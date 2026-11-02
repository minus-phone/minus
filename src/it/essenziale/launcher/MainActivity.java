package it.essenziale.launcher;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Home: Oggi (impegni + task), Domani, Settimana, contenitori, Notifiche, Cerca o chiedi, Maps. */
public class MainActivity extends BaseActivity implements Store.Listener {

    private static final int REQ_VOICE = 10;
    private static final int REQ_CAL = 11;
    private static final String CLAUDE_PKG = "com.anthropic.claude";
    private static final String MAPS_PKG = "com.google.android.apps.maps";

    private LinearLayout root, today, tomorrow, results;
    private TextView todayAlarm, dateView, notifBtn, notifCount, sessionView, filterOff;
    private android.widget.FrameLayout sessionWrap;
    private View notifBox, tomorrowLine, setupLine;
    private LinearLayout askBox;
    private boolean bored;
    private EditText query;
    private List<U.AppInfo> appsCache;
    private android.database.ContentObserver observer;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (getIntent() != null && getIntent().getBooleanExtra("scope_done", false)) Scope.end(this, "chiusa");

        root = U.col(this);
        root.setBackgroundColor(U.BG);
        root.setPadding(U.dp(this, 20), U.dp(this, 8), U.dp(this, 20), U.dp(this, 16)); // 0.13.2: la barra di stato non c'è, la home parte dall'alto
        // Il focus iniziale va alla pagina, non al campo di ricerca: niente tastiera automatica
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout content = U.col(this);
        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 0.13: la pillola sta in alto al centro, nello stesso punto in cui compare sopra le altre app.
        // Lo spazio resta anche quando non c'è, così la home non salta
        android.widget.FrameLayout pillRow = new android.widget.FrameLayout(this);
        root.addView(pillRow, 0, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, U.dp(this, 36)));

        // Intestazione (0.13): in alto decidi cosa vedere, «Oggi / Settimana»; sotto la data. A destra Notifiche
        LinearLayout head = U.row(this);
        head.setGravity(Gravity.TOP);
        LinearLayout headL = U.col(this);
        LinearLayout nav = U.row(this);
        nav.setGravity(Gravity.BOTTOM);
        TextView oggi = U.title(this, "Oggi", 32, U.INK);
        oggi.setSingleLine(true);
        nav.addView(oggi);
        TextView slash = U.title(this, "/", 22, U.MUTED);
        slash.setTypeface(U.SERIF);
        slash.setPadding(U.dp(this, 8), 0, U.dp(this, 8), U.dp(this, 3));
        slash.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        nav.addView(slash);
        TextView week = U.title(this, "Settimana", 17, U.MUTED);
        week.setTypeface(U.SERIF);
        week.setSingleLine(true);
        week.setEllipsize(android.text.TextUtils.TruncateAt.END);
        week.setMinHeight(U.dp(this, 44));
        week.setGravity(Gravity.BOTTOM);
        week.setPadding(0, 0, U.dp(this, 6), U.dp(this, 6));
        week.setContentDescription("Settimana, apri la vista della settimana");
        week.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(MainActivity.this, WeekActivity.class)); }
        });
        nav.addView(week, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        headL.addView(nav, U.full());
        head.addView(headL, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        sessionView = U.text(this, "", 13, U.MUTED, false); // tempo dall'ultimo sblocco, solo oltre la soglia
        // Notifiche ›: un accesso, senza contare; sotto, «N per te» solo se ce ne sono. Altro non si conta mai
        LinearLayout nb = U.col(this);
        nb.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        nb.setMinimumHeight(U.dp(this, 56));
        nb.setPadding(U.dp(this, 8), 0, 0, 0);
        notifBtn = U.text(this, "Notifiche", 15, U.INK, false); // 0.13.1: senza ›, per stare accanto a «Oggi / Settimana»
        notifBtn.setGravity(Gravity.END);
        nb.addView(notifBtn);
        notifCount = U.text(this, "", 12, U.MUTED, false);
        notifCount.setGravity(Gravity.END);
        nb.addView(notifCount);
        nb.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(MainActivity.this, InboxActivity.class)); }
        });
        notifBox = nb;
        head.addView(nb);
        // 0.12: niente ⋯ in home. Le impostazioni stanno in alto a sinistra nella ricerca;
        // per chi le cerca qui, tenere premuto su uno spazio vuoto della home le apre
        View.OnLongClickListener settings = new View.OnLongClickListener() {
            public boolean onLongClick(View v) { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); return true; }
        };
        head.setOnLongClickListener(settings);
        content.setOnLongClickListener(settings);
        content.addView(head);
        // 0.13.1: sotto, la data e, a destra, la sveglia di oggi finché non ha suonato (anche dopo mezzanotte)
        LinearLayout dateRow = U.row(this);
        dateView = U.text(this, "", 15, U.MUTED, false);
        dateRow.addView(dateView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        todayAlarm = U.text(this, "", 15, U.INK, false);
        todayAlarm.setMinHeight(U.dp(this, 44));
        todayAlarm.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        todayAlarm.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { if (!U.openAlarms(MainActivity.this)) U.toast(MainActivity.this, "Non trovo l'app Orologio"); }
        });
        dateRow.addView(todayAlarm);
        content.addView(dateRow, U.full());
        // 0.11: il tempo dall'ultimo sblocco ha l'aspetto della pillola, solo oltre la soglia
        sessionView.setPadding(U.dp(this, 14), 0, U.dp(this, 14), 0);
        sessionView.setMinHeight(U.dp(this, 32));
        sessionView.setGravity(Gravity.CENTER_VERTICAL);
        sessionView.setSingleLine(true);
        sessionView.setVisibility(View.GONE);
        // area di tocco di 48 dp attorno alla pillola da 32 (accessibilità)
        sessionWrap = new android.widget.FrameLayout(this);
        sessionWrap.setMinimumHeight(U.dp(this, 48));
        sessionWrap.setVisibility(View.GONE);
        sessionWrap.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(MainActivity.this, SessionActivity.class)); }
        });
        sessionView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        sessionView.setVisibility(View.VISIBLE);
        sessionWrap.addView(sessionView, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        pillRow.addView(sessionWrap, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, U.dp(this, 48), Gravity.CENTER_HORIZONTAL | Gravity.TOP));

        // 0.6: dopo l'onboarding breve, una riga sola finché non completi o tocchi ✕
        setupLine = setupRow();
        content.addView(setupLine, U.full());

        // 0.12 (173, 173b): la domanda allo sblocco senza scopo, e dopo «Mi annoiavo» una cosa da fare
        askBox = U.col(this);
        content.addView(askBox, U.full());

        today = U.col(this);
        today.setPadding(0, U.dp(this, 16), 0, U.dp(this, 10));
        content.addView(today);

        // 0.6: Domani compare dalle 17, o prima se oggi non resta niente
        tomorrowLine = U.divider(this);
        // 0.13: niente riga sopra Domani: basta lo spazio
        tomorrow = U.col(this);
        tomorrow.setPadding(0, U.dp(this, 2), 0, U.dp(this, 8));
        content.addView(tomorrow);

        // Contenitori
        // 0.7: niente contenitori in home. Tutto il resto passa dalla ricerca in basso;
        // Shop e Movimenti restano in Impostazioni › App, in prova

        // Filtro spento: un avviso sotto i contenitori (il pulsante grande non c'è più)
        filterOff = U.text(this, "Il filtro notifiche è spento  · attivalo ›", 14, U.INK, false);
        filterOff.setMinHeight(U.dp(this, 44));
        filterOff.setGravity(Gravity.CENTER_VERTICAL);
        filterOff.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, SettingsActivity.class);
                i.putExtra("area", SettingsActivity.SETUP); // 0.6: dritto in Attivazione
                startActivity(i);
            }
        });
        content.addView(filterOff, U.full());

        results = U.col(this);
        root.addView(results, U.full());

        // 0.13: in basso le azioni valide sempre: aggiungere, poi cercare o chiedere
        TextView add = U.text(this, "+ Aggiungi al calendario", 15, U.INK, false);
        add.setGravity(Gravity.CENTER);
        add.setPadding(U.dp(this, 18), 0, U.dp(this, 18), 0);
        add.setBackground(U.box(this, U.BG, U.INK, 22));
        add.setContentDescription("Aggiungi al calendario un impegno o una cosa da fare");
        add.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, AddActivity.class);
                i.putExtra("day", U.startOfDay(Quando.addDayOffset(U.hourNow())));
                startActivity(i);
            }
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, U.dp(this, 44));
        alp.topMargin = U.dp(this, 8);
        root.addView(add, alp);

        // Barra in basso
        LinearLayout bar = U.row(this);
        bar.setPadding(0, U.dp(this, 12), 0, 0);
        LinearLayout field = U.row(this);
        field.setBackground(U.box(this, U.BG, U.INK, 28));
        field.setPadding(U.dp(this, 22), U.dp(this, 6), U.dp(this, 6), U.dp(this, 6));
        query = new EditText(this);
        query.setHint("Cerca, chiedi o di' un comando");
        query.setHintTextColor(U.MUTED);
        query.setTextColor(U.INK);
        query.setTextSize(17);
        query.setTypeface(U.SANS);
        query.setBackground(null);
        query.setSingleLine(true);
        query.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        query.setImeOptions(EditorInfo.IME_ACTION_GO);
        // 0.7: il campo apre la ricerca a tutto schermo (risultati in basso, vicino al pollice)
        query.setHint("Cerca o chiedi");
        query.setFocusable(false);
        query.setCursorVisible(false);
        query.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(MainActivity.this, SearchActivity.class)); }
        });
        field.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(MainActivity.this, SearchActivity.class)); }
        });
        field.addView(query, new LinearLayout.LayoutParams(0, U.dp(this, 48), 1f));
        MicView mic = new MicView(this);
        mic.setContentDescription("Comando vocale");
        mic.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, SearchActivity.class);
                i.putExtra("voice", true);
                startActivity(i);
            }
        });
        field.addView(mic, new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 44)));
        bar.addView(field, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        // 0.7: Maps è tra le app a portata della ricerca
        root.addView(bar, U.full());

        query.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { showResults(s.toString()); }
        });
        query.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int id, KeyEvent e) { submit(query.getText().toString()); return true; }
        });

        setContentView(root);
        root.requestFocus();
        observer = U.calendarObserver(new Runnable() { public void run() { buildToday(); buildTomorrow(); } });
        // 0.6: al primo avvio l'onboarding chiede i permessi nel passo che li usa
        // 0.13.5: se la configurazione è già aperta (sei diventata home a metà), torna quella, non una nuova da capo
        if (Onboarding.firstRun(this)) OnboardingActivity.bringBack(this);
        else if (!U.hasCalendar(this)) requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR}, REQ_CAL);
    }

    private void addContainer(LinearLayout grid, String title, final int mode, boolean gap) {
        TextView t = U.text(this, title, 16, U.INK, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(U.dp(this, 96)); // 0.4.4: contenitori più alti, più facili da toccare
        t.setPadding(U.dp(this, 4), 0, U.dp(this, 4), 0);
        t.setBackground(U.box(this, U.BG, U.INK, 14));
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, ListActivity.class);
                i.putExtra(ListActivity.EXTRA_MODE, mode);
                startActivity(i);
            }
        });
        // larghezza in proporzione al nome, così "Conversazioni" sta su una riga
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, Math.max(1f, title.length() / 7f));
        t.setSingleLine(true);
        if (gap) lp.rightMargin = U.dp(this, 8);
        grid.addView(t, lp);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 0.13.5: la configurazione vive in un suo task; se era aperta (sei diventata home a metà), torna lei
        if (OnboardingActivity.running) OnboardingActivity.bringBack(this);
        Store.addListener(this);
        U.watchCalendar(this, observer, true);
        registerReceiver(tick, new android.content.IntentFilter(Intent.ACTION_TIME_TICK));
        appsCache = null;
        query.clearFocus();
        root.requestFocus();
        refresh();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) renderAsk(); // lo sblocco può arrivare subito dopo il ritorno in home
    }

    private void renderAsk() {
        askBox.removeAllViews();
        if (bored) {
            View card = Adesso.card(this, false, new Runnable() {
                public void run() { bored = false; U.toast(MainActivity.this, "Buona pausa"); renderAsk(); }
            }, new Runnable() { public void run() { refresh(); } });
            if (card == null) { bored = false; return; }
            LinearLayout.LayoutParams lp = U.full();
            lp.topMargin = U.dp(this, 8);
            askBox.addView(card, lp);
            TextView ok = U.text(this, "Annoiarsi va bene. Questa è solo un'idea.", 13, U.MUTED, false);
            ok.setPadding(0, U.dp(this, 8), 0, 0);
            askBox.addView(ok);
            TextView need = U.text(this, "Mi serve il telefono adesso ›", 15, U.INK, false);
            need.setMinHeight(U.dp(this, 44));
            need.setGravity(Gravity.CENTER_VERTICAL);
            need.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { bored = false; renderAsk(); } });
            askBox.addView(need);
            return;
        }
        if (!Sblocco.pending(this)) return;
        LinearLayout box = U.col(this);
        box.setPadding(U.dp(this, 16), U.dp(this, 14), U.dp(this, 16), U.dp(this, 8));
        box.setBackground(U.box(this, U.SURFACE, U.BORDER, 16));
        box.addView(U.text(this, "Terza volta in 10 minuti", 13, U.MUTED, false));
        TextView q = U.text(this, "Per cosa l'hai preso?", 18, U.INK, true);
        q.setPadding(0, U.dp(this, 4), 0, U.dp(this, 4));
        box.addView(q);
        LinearLayout row = null;
        int used = 0;
        for (final String a : Cosa.ANSWERS) {
            if (row == null || used + a.length() > 28) {
                row = U.row(this);
                LinearLayout.LayoutParams lp = U.full();
                lp.topMargin = U.dp(this, 6);
                box.addView(row, lp);
                used = 0;
            }
            used += a.length();
            TextView t = U.text(this, a, 14, U.INK, false);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(U.dp(this, 44));
            t.setPadding(U.dp(this, 12), 0, U.dp(this, 12), 0);
            t.setBackground(U.box(this, U.BG, U.BORDER, 20));
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Sblocco.answer(MainActivity.this, a);
                    bored = Cosa.proposeAfter(a);
                    if (!bored) U.toast(MainActivity.this, "Grazie. Resta solo sul telefono");
                    renderAsk();
                }
            });
            LinearLayout.LayoutParams lp = U.wrap();
            lp.rightMargin = U.dp(this, 6);
            row.addView(t, lp);
        }
        TextView later = U.text(this, "Non chiedermelo adesso", 14, U.MUTED, false);
        later.setMinHeight(U.dp(this, 44));
        later.setGravity(Gravity.CENTER_VERTICAL);
        later.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Sblocco.snooze(MainActivity.this); renderAsk(); } });
        box.addView(later);
        LinearLayout.LayoutParams lp = U.full();
        lp.topMargin = U.dp(this, 8);
        askBox.addView(box, lp);
    }

    @Override
    protected void onPause() {
        try { unregisterReceiver(tick); } catch (Exception ignored) {}
        Store.removeListener(this);
        U.watchCalendar(this, observer, false);
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra("scope_done", false)) Scope.end(this, "chiusa"); // «Chiudo» / «Ho finito»
        query.setText("");
        hideKeyboard();
        refresh();
    }

    @Override
    public void onBackPressed() {
        if (query.getText().length() > 0) { query.setText(""); hideKeyboard(); }
    }

    @Override
    public void onChanged() {
        runOnUiThread(new Runnable() { public void run() { refreshNotif(); } });
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        refresh();
    }

    // ---------------------------------------------------------------------

    private void refresh() {
        refreshDate();
        renderAsk();
        buildToday();
        buildTomorrow();
        refreshNotif();
    }

    /** Ogni minuto: toglie gli impegni appena finiti; alle 17 e alle 19 cambia cosa si vede. */
    private final android.content.BroadcastReceiver tick = new android.content.BroadcastReceiver() {
        public void onReceive(android.content.Context c, Intent i) { refreshDate(); buildToday(); buildTomorrow(); }
    };

    private void refreshDate() {
        dateView.setText(new java.text.SimpleDateFormat("EEE d MMM", Locale.ITALY).format(new java.util.Date()));
        String al = Quando.todayAlarm(System.currentTimeMillis(), U.nextAlarmTime(this), U.startOfDay(1));
        todayAlarm.setText(al == null ? "" : al);
        todayAlarm.setVisibility(al == null ? View.GONE : View.VISIBLE);
        todayAlarm.setContentDescription(al == null ? null : al + " oggi. Apri le sveglie");
    }

    private boolean todayEmpty;

    private void buildToday() {
        // 0.6: sotto la data solo il tempo dall'ultimo sblocco; la sveglia sta in Domani
        String sl = Pill.homeText(this);
        sessionView.setText(sl == null ? "" : sl);
        sessionView.setTextColor(U.BG);
        sessionView.setBackground(U.box(this, U.INK, 0, 16));
        sessionWrap.setContentDescription(sl == null ? null : "Al telefono: " + sl.replace("●", "").replace("◷", "").trim() + ". Tocca per le impostazioni del tempo.");
        sessionWrap.setVisibility(sl == null ? View.INVISIBLE : View.VISIBLE);
        today.removeAllViews();
        if (!U.hasCalendar(this)) {
            TextView t = U.text(this, "Tocca qui per permettere la lettura del calendario", 15, U.MUTED, false);
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR}, REQ_CAL); }
            });
            today.addView(t);
        }
        List<U.Event> all = U.events(this, U.startOfDay(0), U.startOfDay(1));
        long now = System.currentTimeMillis();
        // 0.4.3: gli impegni già finiti spariscono dalla home (restano in Settimana)
        List<U.Event> ev = new java.util.ArrayList<U.Event>();
        for (U.Event e : all) if (e.allDay || e.end >= now) ev.add(e);
        boolean nextMarked = false;
        for (U.Event e : ev) {
            boolean isNext = !nextMarked && !e.allDay;
            if (isNext) nextMarked = true;
            today.addView(eventRow(e, isNext, false, 16));
        }
        List<Tasks.Task> tasks = Tasks.forToday(this, true, true);
        for (Tasks.Task t : tasks) today.addView(taskRow(t, 16, true));
        todayEmpty = ev.isEmpty() && tasks.isEmpty();
        if (todayEmpty && U.hasCalendar(this))
            today.addView(U.text(this, all.isEmpty() ? "Niente in programma oggi" : "Niente più in programma oggi", 16, U.MUTED, false));
    }

    private void buildTomorrow() {
        tomorrow.removeAllViews();
        int hour = U.hourNow();
        boolean show = Quando.showTomorrow(hour, todayEmpty);
        tomorrow.setVisibility(show ? View.VISIBLE : View.GONE);
        tomorrowLine.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        long d = U.startOfDay(1);
        // 0.13: la sveglia sta sulla riga del giorno, a destra; dalle 19, se non c'è, lo dice
        String al = Quando.homeAlarm(hour, System.currentTimeMillis(), U.nextAlarmTime(this), d, U.startOfDay(2));
        tomorrow.addView(dayRow(this, "Domani · " + U.dayLabel(d), al, U.MUTED, U.INK), U.full());
        List<U.Event> ev = U.events(this, d, U.startOfDay(2));
        for (U.Event e : ev) tomorrow.addView(eventRow(e, false, false, 15));
        List<Tasks.Task> tasks = Tasks.forDay(this, d);
        for (Tasks.Task t : tasks) tomorrow.addView(taskRow(t, 15, false));
        if (ev.isEmpty() && tasks.isEmpty()) tomorrow.addView(U.text(this, "Niente in programma", 15, U.MUTED, false));
    }

    /** 0.13: «DOMANI · SAB 31» a sinistra e la sveglia a destra, sulla stessa riga (home e blocco). */
    static View dayRow(final android.app.Activity a, String day, String alarm, int muted, int ink) {
        LinearLayout r = U.row(a);
        r.setMinimumHeight(U.dp(a, 48));
        r.setPadding(0, U.dp(a, 10), 0, 0);
        TextView l = U.label(a, day);
        l.setTextColor(muted);
        l.setPadding(0, 0, 0, 0);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (alarm != null) {
            boolean none = alarm.startsWith("Nessuna");
            TextView t = U.text(a, none ? "Nessuna sveglia ›" : alarm, 15, ink, false);
            t.setMinHeight(U.dp(a, 44));
            t.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
            t.setPadding(U.dp(a, 12), 0, 0, 0);
            t.setContentDescription(none ? "Nessuna sveglia per domani. Apri le sveglie" : alarm + ". Apri le sveglie");
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { if (!U.openAlarms(a)) U.toast(a, "Non trovo l'app Orologio"); }
            });
            r.addView(t);
        }
        return r;
    }

    private View eventRow(final U.Event e, boolean strong, boolean past, float sp) {
        LinearLayout r = U.row(this);
        r.setMinimumHeight(U.dp(this, 40));
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { U.openEvent(MainActivity.this, e); }
        });
        int color = past ? U.MUTED : U.INK;
        TextView time = U.text(this, e.allDay ? "giorno" : U.hhmm(e.begin), sp - 1, strong ? U.INK : U.MUTED, strong);
        time.setTypeface(strong ? U.SERIF_M : U.SERIF);
        time.setMinWidth(U.dp(this, 64));
        r.addView(time);
        TextView title = U.text(this, e.title, sp, color, strong);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (e.repeats()) r.addView(U.text(this, "↻", 13, U.MUTED, false));
        return r;
    }

    private View taskRow(final Tasks.Task t, float sp, boolean showDue) {
        LinearLayout r = U.row(this);
        r.setMinimumHeight(U.dp(this, 40));
        LinearLayout boxWrap = U.row(this);
        boxWrap.setMinimumWidth(U.dp(this, 64));
        boxWrap.addView(U.checkbox(this, t.done, U.INK), new LinearLayout.LayoutParams(U.dp(this, 18), U.dp(this, 18)));
        r.addView(boxWrap);
        TextView title = U.text(this, t.title, sp, t.done ? U.MUTED : U.INK, false);
        if (t.done) title.setPaintFlags(title.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (showDue) {
            String due = Tasks.dueLabel(t);
            if (due.length() > 0) r.addView(U.text(this, due, 12, Tasks.overdue(t) ? U.ALERT : U.MUTED, Tasks.overdue(t)));
        }
        View.OnClickListener toggle = new View.OnClickListener() {
            public void onClick(View v) { Tasks.setDone(MainActivity.this, t.id, !t.done); refresh(); }
        };
        if (t.app != null && !t.done && U.installed(this, t.app)) {
            // 0.13: il task apre la sua app; la casella resta per segnarlo fatto
            final String label = U.appLabel(this, t.app);
            TextView open = U.text(this, label + " ›", 13, U.MUTED, false);
            open.setPadding(U.dp(this, 10), 0, 0, 0);
            r.addView(open);
            boxWrap.setMinimumHeight(U.dp(this, 44));
            boxWrap.setOnClickListener(toggle);
            boxWrap.setContentDescription("Segna fatto: " + t.title);
            r.setContentDescription(t.title + ", apri " + label);
            r.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { if (!U.launchPkg(MainActivity.this, t.app)) U.toast(MainActivity.this, "Non riesco ad aprire " + label); }
            });
        } else r.setOnClickListener(toggle);
        r.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { confirmDelete(t); return true; }
        });
        return r;
    }

    private void confirmDelete(final Tasks.Task t) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminare il task?")
                .setMessage(t.title)
                .setPositiveButton("Elimina", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { Tasks.delete(MainActivity.this, t.id); refresh(); }
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void refreshNotif() {
        boolean on = U.listenerEnabled(this);
        filterOff.setVisibility(on ? View.GONE : View.VISIBLE);
        int n = on ? Store.count(this, Store.WAITING) : 0;
        notifCount.setText(n == 0 ? "" : n + " per te");
        notifCount.setVisibility(n == 0 ? View.GONE : View.VISIBLE);
        notifBox.setContentDescription(n == 0 ? "Notifiche" : "Notifiche, " + n + " per te");
        // 0.10: una riga sola alla volta. Prima la fine della prova di «Di notte», poi la configurazione
        boolean night = Night.trialOver(this);
        setupLine.setVisibility(night || Onboarding.showContinue(this) ? View.VISIBLE : View.GONE);
        if (setupLine.getVisibility() == View.VISIBLE)
            ((TextView) setupLine.findViewWithTag("setup")).setText(night ? "Una settimana di «Di notte» ›"
                    : "Continua la configurazione · " + Onboarding.missing(this).size() + (Onboarding.missing(this).size() == 1 ? " passo ›" : " passi ›"));
    }

    private View setupRow() {
        LinearLayout r = U.row(this);
        r.setMinimumHeight(U.dp(this, 46));
        r.setPadding(U.dp(this, 14), 0, U.dp(this, 4), 0);
        r.setBackground(U.box(this, U.SURFACE, U.LINE, 12));
        TextView t = U.text(this, "Continua la configurazione", 14, U.INK, false);
        t.setTag("setup");
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (Night.trialOver(MainActivity.this)) startActivity(new Intent(MainActivity.this, NightReportActivity.class));
                else startActivity(Onboarding.continueIntent(MainActivity.this));
            }
        });
        TextView x = U.text(this, "✕", 14, U.MUTED, false);
        x.setGravity(Gravity.CENTER);
        x.setContentDescription("Non mostrare più");
        x.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (Night.trialOver(MainActivity.this)) U.prefs(MainActivity.this).edit().putBoolean(Night.K_REPORT_SEEN, true).putLong(Night.K_TRIAL, 0).apply();
                else Onboarding.dismissContinue(MainActivity.this);
                refreshNotif();
            }
        });
        r.addView(x, new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 44)));
        r.setVisibility(View.GONE);
        return r;
    }

    // ---------- Cerca o chiedi ----------

    private List<U.AppInfo> matches(String q) {
        if (appsCache == null) appsCache = U.launchableApps(this);
        String s = q.trim().toLowerCase(Locale.ITALY);
        List<U.AppInfo> starts = new ArrayList<U.AppInfo>(), contains = new ArrayList<U.AppInfo>();
        if (s.length() == 0) return starts;
        for (U.AppInfo a : appsCache) {
            String l = a.label.toLowerCase(Locale.ITALY);
            if (l.startsWith(s)) starts.add(a); else if (l.contains(s)) contains.add(a);
        }
        starts.addAll(contains);
        return starts;
    }

    private void showResults(final String q) {
        results.removeAllViews();
        if (q.trim().length() == 0) return;
        List<U.AppInfo> m = matches(q);
        int shown = 0;
        for (final U.AppInfo a : m) {
            if (shown++ >= 4) break;
            TextView t = U.text(this, a.label, 17, U.INK, false);
            t.setMinHeight(U.dp(this, 48));
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { open(a); }
            });
            results.addView(t, U.full());
            results.addView(U.divider(this));
        }
        TextView ask = U.text(this, "Chiedi: “" + q.trim() + "”", 16, U.MUTED, false);
        ask.setMinHeight(U.dp(this, 48));
        ask.setGravity(Gravity.CENTER_VERTICAL);
        ask.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { ask(q.trim()); }
        });
        results.addView(ask, U.full());
    }

    private void submit(String q) {
        q = q.trim();
        if (q.length() == 0) return;
        List<U.AppInfo> m = matches(q);
        if (!m.isEmpty() && (m.size() == 1 || m.get(0).label.equalsIgnoreCase(q))) { open(m.get(0)); return; }
        if (m.isEmpty()) {
            // Un comando scritto ("chiama mamma", "portami in via Tortona 27") vale come detto
            VoiceParser.Cmd c = VoiceParser.parse(q, VoiceActivity.names(this), java.util.Calendar.getInstance());
            if (!VoiceParser.ASK.equals(c.type)) { command(q); return; }
            ask(q);
        }
    }

    private void open(U.AppInfo a) {
        hideKeyboard();
        query.setText("");
        if (!U.open(this, a)) U.toast(this, "Impossibile aprire " + a.label);
    }

    private void ask(String q) {
        hideKeyboard();
        query.setText("");
        if (U.installed(this, CLAUDE_PKG)) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, q);
            i.setPackage(CLAUDE_PKG);
            if (U.start(this, i)) return;
            if (U.launchPkg(this, CLAUDE_PKG)) return;
        }
        Intent s = new Intent(Intent.ACTION_WEB_SEARCH);
        s.putExtra(android.app.SearchManager.QUERY, q);
        if (!U.start(this, s)) U.toast(this, "Nessun assistente disponibile");
    }

    private void voice() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Di' un comando, ad esempio “Chiama mamma”");
        try {
            startActivityForResult(i, REQ_VOICE);
        } catch (Exception e) {
            U.toast(this, "Riconoscimento vocale non disponibile");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_VOICE && res == RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) command(r.get(0));
        }
    }

    /** Tutti i comandi passano dalla schermata di conferma (Voce). */
    private void command(String said) {
        hideKeyboard();
        query.setText("");
        Intent i = new Intent(this, VoiceActivity.class);
        i.putExtra("said", said);
        startActivity(i);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(query.getWindowToken(), 0);
        query.clearFocus();
        root.requestFocus();
        results.removeAllViews();
    }
}
