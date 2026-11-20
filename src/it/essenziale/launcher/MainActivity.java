package it.essenziale.launcher;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
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

    private LinearLayout root, today, tomorrow;
    private LinearLayout dateChips;
    private TextView todayAlarm, dateView, notifBtn, sessionView, filterOff;
    private View notifBadge;     // 0.20 (Evolutiva 016, momento 12): il pallino con il numero; il numero scorre quando cambia
    private RollText notifCount;
    private android.widget.ImageView moon; // 0.20 (momento 13): la luna accanto alla data, solo in modalità sera
    private static boolean seraBack;     // 0.21: la bitmap è la sera che finisce (il contrario)
    private LinearLayout dateRowView;
    private static android.graphics.Bitmap seraFrom; // la home chiara com'era, per la dissolvenza verso la sera
    private android.widget.FrameLayout page;   // 0.21: il fondo della home; la festa dei task si disegna dietro i contenuti
    private FestaView festa;
    private android.widget.FrameLayout sessionWrap;
    private View notifBox, tomorrowLine, setupLine;
    private LinearLayout askBox;
    private boolean bored;
    // 0.20 (Evolutiva 016): righe che si chiudono e scheda dello sblocco che si chiude: finché si muovono, la pagina non si rifà
    private int busyRows;
    private boolean dirty, askAnimating;
    private final java.util.Set<String> hiddenDone = new java.util.HashSet<String>();
    private TextView query; // 0.20.1: solo testo, il tocco lo riceve tutta la barra
    private android.database.ContentObserver observer;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        scrollToTasks = getIntent().getBooleanExtra("tasks", false);
        if (getIntent() != null && getIntent().getBooleanExtra("scope_done", false)) Scope.end(this, "chiusa");
        Goals.migrate(this); // 0.14: chi aggiorna trova gli obiettivi ricavati da cosa usava già
        evening = Night.evening(this);

        root = U.col(this);
        root.setPadding(0, U.dp(this, 8), 0, U.dp(this, 16)); // 0.21: la pagina è larga il 100%; i 20 dp sono padding dei blocchi (contenuto, «Nuovo», barra)
        root.setClipChildren(false);
        root.setClipToPadding(false); // 0.13.2: la barra di stato non c'è, la home parte dall'alto
        // Il focus iniziale va alla pagina, non al campo di ricerca: niente tastiera automatica
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);

        final ScrollView sv = new ScrollView(this);
        homeScroll = sv;
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout content = U.col(this);
        content.setPadding(U.dp(this, 20), 0, U.dp(this, 20), 0);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
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
        Press.link(week, U.MUTED);
        week.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(MainActivity.this, WeekActivity.class)); }
        });
        nav.addView(week, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        headL.addView(nav, U.full());
        head.addView(headL, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        sessionView = U.text(this, "", 13, U.MUTED, false); // tempo dall'ultimo sblocco, solo oltre la soglia
        // Notifiche ›: un accesso, senza contare; sotto, «N per te» solo se ce ne sono. Altro non si conta mai
        // 0.15: «Notifiche» e il pallino con il numero sulla stessa riga
        LinearLayout nb = U.row(this);
        nb.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        nb.setMinimumHeight(U.dp(this, 56));
        nb.setPadding(U.dp(this, 8), 0, 0, 0);
        notifBtn = U.text(this, "Notifiche", 15, U.INK, false); // 0.13.1: senza ›, per stare accanto a «Oggi / Settimana»
        notifBtn.setGravity(Gravity.END);
        nb.addView(notifBtn);
        // 0.20 (momento 12): come U.badge, ma il numero scorre in su quando cambia (RollText)
        android.widget.FrameLayout badge = new android.widget.FrameLayout(this);
        badge.setMinimumWidth(U.dp(this, 22));
        badge.setPadding(U.dp(this, 6), 0, U.dp(this, 6), 0);
        badge.setBackground(U.box(this, U.BADGE, 0, 11));
        notifCount = new RollText(this, 12, U.INK);
        notifCount.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); // il numero lo dice la riga intera
        badge.addView(notifCount, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        notifBadge = badge;
        LinearLayout.LayoutParams nclp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, U.dp(this, 22));
        nclp.leftMargin = U.dp(this, 8);
        nb.addView(badge, nclp);
        nb.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(MainActivity.this, InboxActivity.class)); }
        });
        Press.row(nb);
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
        if (evening) { // 0.20 (momento 13): la luna (direzione A) accanto alla data, solo di sera; 0.21: prima della data
            moon = Ic.view(this, "ic_notte", U.MUTED, 20);
            if (moon != null) {
                moon.setContentDescription(null);
                moon.setPadding(U.dp(this, 1), U.dp(this, 1), U.dp(this, 1), U.dp(this, 1)); // 20 dp di luna nei 22 dp del suo spazio
                LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(U.dp(this, MotionLogic.SERA_LUNA_BOX_DP), U.dp(this, MotionLogic.SERA_LUNA_BOX_DP));
                mlp.rightMargin = U.dp(this, MotionLogic.SERA_DATA_DP - MotionLogic.SERA_LUNA_BOX_DP);
                mlp.gravity = Gravity.CENTER_VERTICAL;
                dateRow.addView(moon, mlp);
            }
        }
        dateRow.addView(dateView);
        // 0.17 (Evolutiva 007): gli eventi di tutto il giorno accanto alla data
        dateChips = new LinearLayout(this);
        dateChips.setPadding(U.dp(this, 10), 0, 0, 0);
        dateRow.addView(dateChips, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        todayAlarm = U.text(this, "", 15, U.INK, false);
        todayAlarm.setMinHeight(U.dp(this, 44));
        todayAlarm.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        Press.link(todayAlarm, U.INK);
        todayAlarm.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { if (!U.openAlarms(MainActivity.this)) U.toast(MainActivity.this, "Non trovo l'app Orologio"); }
        });
        dateRow.addView(todayAlarm);
        dateRowView = dateRow;
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
        Press.link(filterOff, U.INK);
        filterOff.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Onboarding.fix(MainActivity.this, "notifiche"); // 0.18: dritto nelle impostazioni di Android
            }
        });
        content.addView(filterOff, U.full());

        // 0.19 (Evolutiva 014): «Nuovo» è una riga-scheletro larga come i task, al posto del pulsante «+ Aggiungi»
        LinearLayout add = U.row(this);
        add.setMinimumHeight(U.dp(this, 48));
        add.setPadding(U.dp(this, 10), 0, U.dp(this, 10), 0);
        Press.dashed(add, 8, 1.5f, 5, 4, U.BORDER); // 0.20: premuto, il bordo scurisce (il tratteggio non si muove)
        LinearLayout addBoxWrap = U.row(this);
        addBoxWrap.setMinimumWidth(U.dp(this, 30));
        TextView addBox = U.text(this, "+", 14, U.MUTED, false);
        addBox.setGravity(Gravity.CENTER);
        addBox.setIncludeFontPadding(false);
        android.graphics.drawable.GradientDrawable addBoxBg = new android.graphics.drawable.GradientDrawable();
        addBoxBg.setColor(0);
        addBoxBg.setCornerRadius(U.dp(this, 4));
        addBoxBg.setStroke(U.dp(this, 1.5f), U.MUTED, U.dp(this, 3), U.dp(this, 2));
        addBox.setBackground(addBoxBg);
        addBoxWrap.addView(addBox, new LinearLayout.LayoutParams(U.dp(this, 18), U.dp(this, 18)));
        add.addView(addBoxWrap);
        add.addView(U.text(this, "Nuovo", 16, U.INK, false));
        add.setContentDescription("Nuovo task o impegno");
        add.setClickable(true);
        add.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, AddActivity.class);
                i.putExtra("day", U.startOfDay(Quando.addDayOffset(U.hourNow()))); // dalle 18 propone domani
                startActivity(i);
            }
        });
        LinearLayout.LayoutParams alp = U.full();
        alp.topMargin = U.dp(this, 8);
        alp.leftMargin = U.dp(this, 10); // la casella «+» sta in colonna con le caselle dei task (20 dp di pagina - 10 dp)
        alp.rightMargin = U.dp(this, 10);
        root.addView(add, alp);

        // Barra in basso
        LinearLayout bar = U.row(this);
        bar.setPadding(0, U.dp(this, 12), 0, 0);
        bar.addView(Telefono.button(this), Telefono.buttonLp(this)); // 0.19 (Evolutiva 014): il telefono a sinistra della ricerca
        final LinearLayout field = U.row(this);
        Press.box(field, U.BG, U.INK, 28, U.CHIP).ring(28, MotionLogic.ALONE_DP); // 0.20: premuta, la barra prende il colore dei chip; 0.21: e un alone esterno di 8 dp
        field.setPadding(U.dp(this, 22), U.dp(this, 6), U.dp(this, 6), U.dp(this, 6));
        query = new TextView(this);
        query.setHint("Cerca o chiedi");
        query.setHintTextColor(U.HINT);
        query.setTextColor(U.INK);
        query.setTextSize(17);
        query.setTypeface(U.SANS);
        query.setBackground(null);
        query.setSingleLine(true);
        query.setGravity(Gravity.CENTER_VERTICAL);
        // 0.7: la barra apre la ricerca a tutto schermo (risultati in basso, vicino al pollice).
        // 0.20.1: il testo non è un campo e non prende il tocco: lo riceve la barra intera, con il suo stato premuto
        query.setClickable(false);
        query.setFocusable(false);
        query.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        field.setContentDescription("Cerca o chiedi");
        field.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openSearch(); }
        });
        field.addView(query, new LinearLayout.LayoutParams(0, U.dp(this, 48), 1f));
        MicView mic = new MicView(this, true); // 0.19: microfono a contorno
        mic.setContentDescription("Comando vocale");
        // 0.15: il microfono avvia subito i comandi vocali, senza passare dalla ricerca
        mic.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { voice(); }
        });
        field.addView(mic, new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 44)));
        bar.addView(field, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        // 0.7: Maps è tra le app a portata della ricerca
        LinearLayout.LayoutParams barLp = U.full();
        barLp.leftMargin = U.dp(this, 20);
        barLp.rightMargin = U.dp(this, 20);
        root.addView(bar, barLp);

        // 0.21: la festa («Tutto fatto per oggi») sta sullo sfondo: sotto la pagina, che è trasparente
        page = new android.widget.FrameLayout(this);
        page.setBackgroundColor(U.BG);
        festa = new FestaView(this);
        page.addView(festa, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        page.addView(root, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(page);
        root.requestFocus();
        resumeSera();
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

    /** La home com'è adesso, in una bitmap (per la dissolvenza verso la sera); null se non si può. */
    private android.graphics.Bitmap snapshot() {
        try {
            if (page.getWidth() <= 0 || page.getHeight() <= 0) return null;
            android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(page.getWidth(), page.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
            page.draw(new android.graphics.Canvas(b));
            return b;
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 0.20 (Evolutiva 016, momento 13 · «La sera»): ridisegnata con la tavolozza della sera, la home tiene sopra la
     * vecchia in un'immagine che svanisce in 3 s (dissolvenza di colori, senza vibrazione) e la luna sale di 6 dp mentre appare.
     * Se la home si apre già di sera, o con le animazioni ridotte, niente di tutto questo: la sera è già lì.
     */
    private void resumeSera() {
        final android.graphics.Bitmap from = seraFrom;
        seraFrom = null;
        final boolean back = seraBack;
        seraBack = false;
        if (from == null || evening == back || Motion.reduced(this)) return; // sera che comincia: evening; sera che finisce: giorno
        final android.widget.ImageView ov = new android.widget.ImageView(this);
        ov.setImageBitmap(from);
        ov.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
        ov.setClickable(false); // i tocchi passano alla home: la sera non trattiene nessuno
        ov.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addContentView(ov, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ov.animate().alpha(0f).setDuration(MotionLogic.SERA_MS).setInterpolator(Motion.DECELERA).withEndAction(new Runnable() {
            public void run() { if (ov.getParent() instanceof ViewGroup) ((ViewGroup) ov.getParent()).removeView(ov); }
        }).start();
        if (back) { // il contrario: la luna svanisce scendendo di 6 dp, la data torna a sinistra, i colori tornano chiari
            final android.widget.ImageView m = Ic.view(this, "ic_notte", U.MUTED, 20);
            if (m != null && dateRowView != null) {
                m.setPadding(U.dp(this, 1), U.dp(this, 1), U.dp(this, 1), U.dp(this, 1));
                LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(U.dp(this, MotionLogic.SERA_LUNA_BOX_DP), U.dp(this, MotionLogic.SERA_LUNA_BOX_DP));
                mlp.rightMargin = U.dp(this, MotionLogic.SERA_DATA_DP - MotionLogic.SERA_LUNA_BOX_DP);
                mlp.gravity = Gravity.CENTER_VERTICAL;
                dateRowView.addView(m, 0, mlp);
                m.animate().alpha(0f).translationY(U.dp(this, MotionLogic.SERA_LUNA_DP)).setDuration(Motion.LENTO).setInterpolator(Motion.DECELERA).start();
                dateView.animate().translationX(-U.dp(this, MotionLogic.SERA_DATA_DP)).setDuration(Motion.LENTO).setInterpolator(Motion.DECELERA).withEndAction(new Runnable() {
                    public void run() { dateRowView.removeView(m); dateView.setTranslationX(0f); }
                }).start();
            }
            Motion.say(root, "Giorno");
            return;
        }
        if (moon != null) {
            moon.setAlpha(0f);
            moon.setTranslationY(U.dp(this, MotionLogic.SERA_LUNA_DP));
            moon.animate().alpha(1f).translationY(0f).setDuration(Motion.LENTO).setInterpolator(Motion.DECELERA).start();
            // la data scorre a destra di quanto occupa la luna, nello stesso tempo
            dateView.setTranslationX(-U.dp(this, MotionLogic.SERA_DATA_DP));
            dateView.animate().translationX(0f).setDuration(Motion.LENTO).setInterpolator(Motion.DECELERA).start();
        }
        Motion.say(root, "Ora di staccare");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 0.13.5: la configurazione vive in un suo task; se era aperta (sei diventata home a metà), torna lei
        if (OnboardingActivity.running) OnboardingActivity.bringBack(this);
        Store.addListener(this);
        U.watchCalendar(this, observer, true);
        registerReceiver(tick, new android.content.IntentFilter(Intent.ACTION_TIME_TICK));
        query.clearFocus();
        // 0.15: una stella nuova in rubrica fa passare sempre quella persona
        java.util.List<String> starred = Stars.adoptNewStars(this);
        if (!starred.isEmpty()) U.toast(this, android.text.TextUtils.join(", ", starred) + ": stella in rubrica, ora passa sempre");
        root.requestFocus();
        refresh();
        maybeScrollToTasks();
    }

    // ---------- E003 · gesti ----------

    private ScrollView homeScroll;
    private View taskHead;
    private boolean scrollToTasks;
    private final boolean[] upOk = {true};
    private Swipe swipe;

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        if (swipe == null) swipe = new Swipe(this, upOk, new Swipe.Act() {
            public void up() { openSearch(); } // E013: tastiera chiusa
            public void left() { startActivity(new Intent(MainActivity.this, WeekActivity.class)); overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out); }
            public void right() { Telefono.dial(MainActivity.this, null); } // 0.19 (Evolutiva 014): da sinistra a destra, il tastierino
        });
        // su apre la ricerca solo quando la home è già in fondo (altrimenti si scorre la giornata)
        if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) upOk[0] = homeScroll == null || !homeScroll.canScrollVertically(1);
        if (swipe.detector.onTouchEvent(e)) {
            android.view.MotionEvent cancel = android.view.MotionEvent.obtain(e);
            cancel.setAction(android.view.MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
            return true;
        }
        return super.dispatchTouchEvent(e);
    }

    /** 0.16: dal blocco, «TASK» apre la home sui task. */
    private void maybeScrollToTasks() {
        if (!scrollToTasks || taskHead == null || homeScroll == null) return;
        scrollToTasks = false;
        homeScroll.post(new Runnable() {
            public void run() {
                android.graphics.Rect r = new android.graphics.Rect();
                taskHead.getDrawingRect(r);
                try { ((ViewGroup) homeScroll.getChildAt(0)).offsetDescendantRectToMyCoords(taskHead, r); } catch (Exception e) { return; }
                homeScroll.smoothScrollTo(0, Math.max(0, r.top - U.dp(MainActivity.this, 8)));
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) renderAsk(); // lo sblocco può arrivare subito dopo il ritorno in home
    }

    private void renderAsk() {
        if (askAnimating) return; // la scheda si sta chiudendo
        askBox.removeAllViews();
        if (bored) {
            View card = Adesso.card(this, false, new Runnable() {
                public void run() { bored = false; U.toast(MainActivity.this, "Buona pausa"); renderAsk(); }
            }, new Runnable() { public void run() { refresh(); } });
            if (card == null) { bored = false; return; }
            LinearLayout.LayoutParams lp = U.full();
            lp.topMargin = U.dp(this, 8);
            askBox.addView(card, lp);
            TextView ok = U.text(this, "Annoiarsi va bene. Questa è solo un'idea.", 12, U.MUTED, false);
            ok.setPadding(0, U.dp(this, 8), 0, 0);
            askBox.addView(ok);
            TextView need = U.text(this, "Mi serve il telefono adesso ›", 15, U.INK, false);
            need.setMinHeight(U.dp(this, 44));
            need.setGravity(Gravity.CENTER_VERTICAL);
            Press.link(need, U.INK);
            need.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { bored = false; renderAsk(); } });
            askBox.addView(need);
            return;
        }
        if (!Sblocco.pending(this)) return;
        LinearLayout box = U.col(this);
        box.setPadding(U.dp(this, 16), U.dp(this, 14), U.dp(this, 16), U.dp(this, 8));
        box.setBackground(U.box(this, U.SURFACE, U.BORDER, 16));
        box.addView(U.text(this, "Terza volta in 10 minuti", 12, U.MUTED, false));
        TextView q = U.text(this, "Per cosa l'hai preso?", 18, U.INK, true);
        q.setPadding(0, U.dp(this, 4), 0, U.dp(this, 4));
        box.addView(q);
        LinearLayout row = null;
        int used = 0;
        final java.util.List<View> chips = new java.util.ArrayList<View>();
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
            Press.chip(t, U.BG, U.BORDER, 20); // 0.20: premuto, un po' più scuro
            chips.add(t);
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { answerAsk((TextView) v, a, chips); }
            });
            LinearLayout.LayoutParams lp = U.wrap();
            lp.rightMargin = U.dp(this, 6);
            row.addView(t, lp);
        }
        TextView later = U.text(this, "Non chiedermelo adesso", 14, U.MUTED, false);
        later.setMinHeight(U.dp(this, 44));
        later.setGravity(Gravity.CENTER_VERTICAL);
        Press.link(later, U.MUTED);
        later.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // 0.20 (Evolutiva 016): si chiude come dopo una risposta, senza vibrazione
                if (askAnimating) return;
                askAnimating = true;
                Sblocco.snooze(MainActivity.this);
                Motion.say(v, "Domanda chiusa");
                closeAsk(false);
            }
        });
        box.addView(later);
        LinearLayout.LayoutParams lp = U.full();
        lp.topMargin = U.dp(this, 8);
        askBox.addView(box, lp);
    }

    @Override
    protected void onPause() {
        if (festa != null) festa.stop();
        try { unregisterReceiver(tick); } catch (Exception ignored) {}
        Store.removeListener(this);
        U.watchCalendar(this, observer, false);
        super.onPause();
    }

    /**
     * 0.20 (Evolutiva 016), momento 5: il chip scelto si riempie d'inchiostro (RAPIDO) con lo scatto; dopo 300 ms la
     * scheda si chiude (BASE) e la giornata sale. Niente «grazie»: si torna a quello che stavi facendo.
     */
    private void answerAsk(TextView chip, String answer, java.util.List<View> chips) {
        if (askAnimating) return;
        askAnimating = true;
        for (View c : chips) c.setClickable(false);
        Motion.scatto(chip);
        final android.graphics.drawable.GradientDrawable g = U.box(this, U.BG, U.BORDER, 20);
        chip.setBackground(g);
        final TextView tv = chip;
        Motion.color(Motion.dur(this, Motion.RAPIDO), U.BG, U.INK, new Motion.ColorSink() {
            public void set(int c) { g.setColor(c); }
        });
        Motion.color(Motion.dur(this, Motion.RAPIDO), U.INK, U.BG, new Motion.ColorSink() {
            public void set(int c) { tv.setTextColor(c); }
        });
        Motion.say(chip, answer + ", scelto");
        Sblocco.answer(this, answer);
        final boolean next = Cosa.proposeAfter(answer);
        askBox.postDelayed(new Runnable() { public void run() { bored = next; closeAsk(true); } }, MotionLogic.SBLOCCO_ATTESA);
    }

    /** La scheda si chiude (altezza e dissolvenza in BASE); sotto, la giornata sale. */
    private void closeAsk(final boolean answered) {
        Motion.collapse(askBox, true, new Runnable() {
            public void run() {
                askBox.removeAllViews();
                askAnimating = false;
                renderAsk(); // dopo «Mi annoiavo» arriva la scheda «Adesso»; altrimenti niente
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra("scope_done", false)) Scope.end(this, "chiusa"); // «Chiudo» / «Ho finito»
        if (intent.getBooleanExtra("undo", false)) { // 0.16: da Modifica task › Elimina task
            final java.util.List<Changes.Change> ch = Changes.list(this);
            if (!ch.isEmpty()) Changes.snackbar(this, ch.get(0).desc, ch.get(0), new Runnable() { public void run() { refresh(); } });
        }
        if (intent.getBooleanExtra("tasks", false)) { scrollToTasks = true; maybeScrollToTasks(); } // 0.16: dal blocco, «TASK»
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
        public void onReceive(android.content.Context c, Intent i) {
            // 0.14: all'ora di staccare (e alla sveglia) la home cambia colori: si ridisegna
            boolean nowEvening = Night.evening(MainActivity.this);
            if (nowEvening != evening) {
                // 0.20 (momento 13): se la sera comincia mentre guardi la home, i colori scivolano via in 3 s
                boolean go = MotionLogic.seraAnimates(evening, nowEvening, hasWindowFocus(), Motion.reduced(MainActivity.this));
                boolean back = MotionLogic.giornoAnimates(evening, nowEvening, hasWindowFocus(), Motion.reduced(MainActivity.this));
                if (go || back) { seraFrom = snapshot(); seraBack = back; }
                recreate();
                return;
            }
            refreshDate(); buildToday(); buildTomorrow();
        }
    };

    private void refreshDate() {
        dateView.setText(new java.text.SimpleDateFormat("EEE d MMM", Locale.ITALY).format(new java.util.Date()));
        String al = Quando.todayAlarm(System.currentTimeMillis(), U.nextAlarmTime(this), U.startOfDay(1));
        todayAlarm.setText(al == null ? "" : al);
        todayAlarm.setVisibility(al == null ? View.GONE : View.VISIBLE);
        todayAlarm.setContentDescription(al == null ? null : al + " oggi. Apri le sveglie");
    }

    private boolean todayEmpty;
    private boolean doneOpen; // 0.15: l'elenco «n task completati» aperto
    private boolean evening; // 0.14 (195): modalità sera, dall'ora di staccare alla sveglia

    private void buildToday() {
        if (busyRows > 0) { dirty = true; return; } // una riga si sta chiudendo: si rifà alla fine
        // 0.6: sotto la data solo il tempo dall'ultimo sblocco; la sveglia sta in Domani
        String sl = Pill.homeText(this);
        sessionView.setText(sl == null ? "" : U.brand(sl));
        sessionView.setTextColor(U.BG);
        sessionView.setBackground(U.box(this, U.INK, 0, 16));
        sessionWrap.setContentDescription(sl == null ? null : sl.startsWith("Tra ") ? sl
                : "Al telefono: " + sl.replace("●", "").replace("◷", "").trim() + ". Tocca per le impostazioni del tempo.");
        sessionWrap.setVisibility(sl == null ? View.INVISIBLE : View.VISIBLE);
        today.removeAllViews();
        taskHead = null;
        dateChips.removeAllViews();
        View chips = U.allDayChips(this, U.events(this, U.startOfDay(0), U.startOfDay(1)));
        if (chips != null) dateChips.addView(chips, U.full());
        evening = Night.evening(this);
        if (evening && U.hourNow() >= 12) {
            // 195: la sera domani prende il posto di oggi. E002: al posto di «Invece di scorrere» l'avviso
            // dell'ora di staccare (se il telefono era bloccato) e il consiglio della settimana
            todayEmpty = true;
            View card = seraCard();
            if (card != null) today.addView(card, U.full());
            // 0.16: sotto, i task urgenti di oggi non ancora fatti: la sera non li nasconde
            List<Tasks.Task> urg = new java.util.ArrayList<Tasks.Task>();
            for (Tasks.Task t : Tasks.forToday(this, false, false)) if (t.urgent && t.due != 0) urg.add(t);
            if (!urg.isEmpty()) {
                TextView h = U.label(this, "Urgente, da oggi");
                h.setPadding(0, U.dp(this, 18), 0, U.dp(this, 4));
                today.addView(h);
                for (Tasks.Task t : urg) today.addView(taskRow(t, 16, false, urg));
            }
            return;
        }
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
        for (U.Event e : all) if (!e.allDay && e.end >= now) ev.add(e); // 0.17: tutto il giorno sta accanto alla data
        boolean nextMarked = false;
        for (U.Event e : ev) {
            boolean isNext = !nextMarked && !e.allDay;
            if (isNext) nextMarked = true;
            today.addView(eventRow(e, isNext, false, 16));
        }
        // 0.15: prima i task da fare, nell'ordine scelto; i completati sbiaditi fino allo sblocco successivo,
        // oppure, da 3 in su, tutti in un elenco chiuso «n task completati» che resta
        // E001: «TASK» in ordine di scadenza: urgenti di oggi, oggi, urgenti della settimana, scaduti, senza scadenza
        List<Tasks.Task> tasks = Tasks.forToday(this, true, true);
        final List<Tasks.Task> todo = Tasks.home(this);
        List<Tasks.Task> doneToday = new java.util.ArrayList<Tasks.Task>();
        for (Tasks.Task t : tasks) if (t.done) doneToday.add(t);
        // 0.20: un task appena spuntato si è chiuso e non ricompare tra i da fare; da 3 completati in su restano nell'elenco chiuso
        if (!Ordine.accordion(doneToday.size())) for (int i = doneToday.size() - 1; i >= 0; i--) if (hiddenDone.contains(doneToday.get(i).id)) doneToday.remove(i);
        if (!todo.isEmpty() || !doneToday.isEmpty()) {
            if (!ev.isEmpty()) {
                View line = new View(this); // una linea piena separa gli eventi dai task
                line.setBackgroundColor(U.LINE);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, U.dp(this, 1)));
                llp.topMargin = U.dp(this, 12);
                today.addView(line, llp);
            }
            TextView th = U.label(this, "Task");
            taskHead = th;
            th.setTextSize(12 * U.textScale);
            th.setPadding(0, U.dp(this, 12), 0, U.dp(this, 2));
            today.addView(th);
        }
        boolean anyToday = false, split = false;
        int urgentToday = 0;
        String lastHead = null;
        for (Tasks.Task t : todo) {
            int g = Tasks.group(t);
            // 0.17 (Evolutiva 006): la scadenza non si ripete su ogni task, è un titoletto sopra il gruppo
            String hd = Urgenti.heading(g, new java.text.SimpleDateFormat("EEE", Locale.ITALY).format(new java.util.Date(t.due)));
            if (hd != null && !hd.equals(lastHead)) {
                if (anyToday && !split) {
                    split = true;
                    View line = new View(this);
                    line.setBackgroundColor(U.LINE);
                    LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(U.dp(this, 64), Math.max(1, U.dp(this, 1)));
                    llp.setMargins(U.dp(this, 30), U.dp(this, 4), 0, 0);
                    today.addView(line, llp);
                }
                TextView h = U.text(this, hd, 12, U.MUTED, false);
                int st = g == Urgenti.URG_WEEK ? android.graphics.Typeface.BOLD : g == Urgenti.OVERDUE ? android.graphics.Typeface.BOLD_ITALIC : android.graphics.Typeface.NORMAL;
                h.setTypeface(android.graphics.Typeface.create(h.getTypeface(), st));
                h.setPadding(0, U.dp(this, 10), 0, 0);
                U.heading(h);
                today.addView(h);
            }
            lastHead = hd;
            if (Urgenti.isToday(g)) anyToday = true;
            else if (anyToday && !split) {
                split = true; // una linea più corta, rientrata, separa i task di oggi dagli altri
                View line = new View(this);
                line.setBackgroundColor(U.LINE);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, U.dp(this, 1)));
                llp.setMargins(U.dp(this, 30), U.dp(this, 4), 0, U.dp(this, 4));
                today.addView(line, llp);
            }
            if (g == Urgenti.URG_TODAY) urgentToday++;
            List<Tasks.Task> same = new java.util.ArrayList<Tasks.Task>();
            for (Tasks.Task x : todo) if (Tasks.group(x) == g) same.add(x);
            today.addView(taskRow(t, 16, false, same));
        }
        String many = Urgenti.tooMany(urgentToday);
        if (many != null) {
            TextView m = U.text(this, many, 13, U.MUTED, false);
            m.setPadding(0, U.dp(this, 6), 0, U.dp(this, 6));
            today.addView(m);
        }
        long lastUnlock = Tasks.lastUnlock(this), midnight = U.startOfDay(0);
        boolean acc = Ordine.accordion(doneToday.size());
        int doneShown = 0;
        if (acc) {
            doneShown = doneToday.size();
            final TextView head = U.text(this, Ordine.accordionTitle(doneToday.size()) + (doneOpen ? "  ▴" : "  ▾"), 14, U.MUTED, false);
            head.setMinHeight(U.dp(this, 44));
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setContentDescription(Ordine.accordionTitle(doneToday.size()) + (doneOpen ? ", aperto. Tocca per chiudere" : ", chiuso. Tocca per aprire"));
            head.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { doneOpen = !doneOpen; buildToday(); } });
            today.addView(head, U.full());
            if (doneOpen) for (Tasks.Task t : doneToday) today.addView(taskRow(t, 15, false, null));
        } else for (Tasks.Task t : doneToday)
            if (Ordine.doneVisibleInHome(t.doneAt, lastUnlock, midnight, doneToday.size())) { today.addView(taskRow(t, 16, false, null)); doneShown++; }
        todayEmpty = ev.isEmpty() && todo.isEmpty() && doneShown == 0;
        if (todayEmpty && U.hasCalendar(this))
            today.addView(U.text(this, all.isEmpty() ? "Niente in programma oggi" : "Niente più in programma oggi", 16, U.MUTED, false));
    }

    /** E002: «È l'ora di staccare» con «Va bene» e «Ancora 15 min»; dopo «Va bene» resta il solo consiglio. */
    private View seraCard() {
        boolean pending = Sera.pending(this);
        String tip = Sera.tip(this);
        if (!pending && tip == null) return null;
        LinearLayout c = U.col(this);
        c.setPadding(U.dp(this, 16), U.dp(this, 14), U.dp(this, 16), U.dp(this, 14));
        c.setBackground(U.box(this, U.SURFACE, U.LINE, 14));
        if (pending) {
            TextView t = U.text(this, "È l'ora di staccare", 20, U.INK, false);
            t.setTypeface(U.SERIF_M);
            c.addView(t);
            String al = Sera.join(Sera.alarmLine(this), Sera.canSnooze(this) ? null : Sera.SNOOZE_USED);
            if (al != null) {
                TextView l = U.text(this, al, 15, U.MUTED, false);
                l.setPadding(0, U.dp(this, 4), 0, 0);
                c.addView(l);
            }
        }
        if (tip != null) {
            TextView tl = U.label(this, "Consiglio della settimana");
            tl.setTextSize(12 * U.textScale);
            if (!pending) tl.setPadding(0, 0, 0, U.dp(this, 4));
            c.addView(tl);
            c.addView(U.text(this, tip, 16, U.INK, false));
        }
        if (pending) {
            LinearLayout btns = U.row(this);
            btns.setPadding(0, U.dp(this, 12), 0, 0);
            TextView ok = U.text(this, "Va bene", 15, U.BG, true);
            ok.setGravity(Gravity.CENTER);
            Press.primary(ok, U.INK, 22);
            Tick.onClick(ok, new Runnable() { public void run() { Sera.ack(MainActivity.this); buildToday(); } }); // 0.21: check al posto della label
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, U.dp(this, 44), 1f);
            blp.rightMargin = U.dp(this, 8);
            btns.addView(ok, blp);
            if (Sera.canSnooze(this)) {
                TextView more = U.text(this, "Ancora 15 min", 15, U.INK, true);
                more.setGravity(Gravity.CENTER);
                Press.outlined(more, U.BG, U.INK, 22);
                more.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Sera.snooze(MainActivity.this); recreate(); } });
                btns.addView(more, new LinearLayout.LayoutParams(0, U.dp(this, 44), 1f));
            }
            c.addView(btns, U.full());
        }
        return c;
    }

    private void buildTomorrow() {
        if (busyRows > 0) { dirty = true; return; }
        tomorrow.removeAllViews();
        int hour = U.hourNow();
        boolean show = Quando.showTomorrow(hour, todayEmpty) || evening && hour >= 12;
        tomorrow.setVisibility(show ? View.VISIBLE : View.GONE);
        tomorrowLine.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        long d = U.startOfDay(1);
        // 0.13: la sveglia sta sulla riga del giorno, a destra; dalle 19, se non c'è, lo dice
        String al = Quando.homeAlarm(hour, System.currentTimeMillis(), U.nextAlarmTime(this), d, U.startOfDay(2));
        List<U.Event> all = U.events(this, d, U.startOfDay(2)), ev = new java.util.ArrayList<U.Event>();
        for (U.Event e : all) if (!e.allDay) ev.add(e);
        // 0.19 (Evolutiva 014): la sveglia occupa già la riga del giorno: il «tutto il giorno» va sotto, a tutta larghezza
        tomorrow.addView(dayRow(this, "Domani · " + U.dayLabel(d), al, U.MUTED, U.INK), U.full());
        View chipsBelow = U.allDayChips(this, all);
        if (chipsBelow != null) {
            LinearLayout.LayoutParams clp = U.full();
            clp.topMargin = U.dp(this, 4);
            tomorrow.addView(chipsBelow, clp);
        }
        for (U.Event e : ev) tomorrow.addView(eventRow(e, false, false, 15));
        // 0.17: gli urgenti di domani stanno già in cima (TASK); in modalità sera la cima non c'è e restano qui
        List<Tasks.Task> tasks = new java.util.ArrayList<Tasks.Task>();
        for (Tasks.Task t : Tasks.forDay(this, d)) if (!(t.urgent && !evening)) tasks.add(t);
        for (Tasks.Task t : tasks) tomorrow.addView(taskRow(t, 15, false, tasks));
        if (all.isEmpty() && tasks.isEmpty()) tomorrow.addView(U.text(this, "Niente in programma", 15, U.MUTED, false));
    }

    /** 0.13: «DOMANI · SAB 31» a sinistra e la sveglia a destra, sulla stessa riga (home e blocco). */
    static View dayRow(final android.app.Activity a, String day, String alarm, int muted, int ink) { return dayRow(a, day, alarm, muted, ink, null); }

    static View dayRow(final android.app.Activity a, String day, String alarm, int muted, int ink, View chips) {
        LinearLayout r = U.row(a);
        r.setMinimumHeight(U.dp(a, 48));
        r.setPadding(0, U.dp(a, 10), 0, 0);
        TextView l = U.label(a, day);
        l.setTextColor(muted);
        l.setPadding(0, 0, 0, 0);
        if (chips == null) r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        else {
            r.addView(l);
            LinearLayout cw = new LinearLayout(a);
            cw.setPadding(U.dp(a, 10), 0, 0, 0);
            cw.addView(chips, U.full());
            r.addView(cw, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
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
        r.setOnLongClickListener(new View.OnLongClickListener() { // 0.15: Modifica impegno, Elimina impegno
            public boolean onLongClick(View v) { Menu.event(MainActivity.this, e); return true; }
        });
        int color = past ? U.MUTED : U.INK;
        TextView time = U.text(this, U.hhmm(e.begin), sp - 1, strong ? U.INK : U.MUTED, strong);
        time.setTypeface(strong ? U.SERIF_M : U.SERIF);
        time.setMinWidth(U.dp(this, 64));
        r.addView(time);
        TextView title = U.text(this, e.title, sp, color, strong);
        title.setTypeface(strong ? U.SERIF_M : U.SERIF); // 0.18 (Evolutiva 008): titoli degli impegni in Literata
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (e.repeats()) r.addView(U.text(this, "↻", 13, U.MUTED, false));
        return r;
    }

    private View taskRow(final Tasks.Task t, float sp, boolean showDue, final List<Tasks.Task> scope) {
        LinearLayout r = U.row(this);
        r.setMinimumHeight(U.dp(this, 48)); // 0.21: come nel prototipo, ogni riga di task è alta 48 dp
        r.setClipChildren(false); // l'alone (e il fondo premuto) non vengono ritagliati dalla riga
        r.setClipToPadding(false);
        LinearLayout boxWrap = U.row(this);
        boxWrap.setMinimumWidth(U.dp(this, 30)); // E001: casella più vicina al titolo
        final TaskBox tbox = new TaskBox(this, t.done); // 0.20 (Evolutiva 016): si riempie in senso orario, poi la spunta con la molla
        boxWrap.addView(tbox, new LinearLayout.LayoutParams(U.dp(this, 18), U.dp(this, 18)));
        r.addView(boxWrap);
        int g = Tasks.group(t);
        boolean late = !t.done && g == Urgenti.OVERDUE;
        LinearLayout col = U.col(this);
        col.setPadding(0, 0, U.dp(this, 8), 0);
        String lab = t.done || !showDue ? null
                : Urgenti.label(g, new java.text.SimpleDateFormat("EEE", Locale.ITALY).format(new java.util.Date(t.due)));
        if (lab != null) {
            // E001: «entro gio» in grassetto, «Scaduto» in grassetto corsivo, «senza scadenza» normale; niente «oggi»
            TextView l = U.text(this, lab, 12, U.MUTED, false);
            int style = g == Urgenti.URG_WEEK ? android.graphics.Typeface.BOLD
                    : late ? android.graphics.Typeface.BOLD_ITALIC : android.graphics.Typeface.NORMAL;
            l.setTypeface(android.graphics.Typeface.create(l.getTypeface(), style));
            l.setIncludeFontPadding(false);
            col.addView(l);
        }
        // 0.15: i completati in un grigio meno contrastato (almeno 3:1 sullo sfondo), barrati
        TextView title = U.text(this, "", sp, t.done ? U.BORDER : U.INK, false);
        final TaskRows.BangSpan bang = t.urgent && !t.done ? new TaskRows.BangSpan(U.URGENT) : null;
        title.setText(bang != null ? TaskRows.bangTitle(t.title, bang) : t.title);
        if (late) title.setTypeface(android.graphics.Typeface.create(title.getTypeface(), android.graphics.Typeface.ITALIC));
        if (t.done) title.setPaintFlags(title.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(title, U.full());
        r.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        String spoken = (t.urgent && !t.done ? "Urgente: " : "") + t.title + (lab != null ? ", " + lab : "");
        r.setContentDescription(t.done ? t.title + ", completato. Tocca per riaprirlo" : spoken);
        View.OnClickListener toggle = new View.OnClickListener() {
            public void onClick(View v) { hiddenDone.remove(t.id); Tasks.setDone(MainActivity.this, t.id, !t.done); refresh(); }
        };
        // 0.16: una regola sola: la casella segna fatto, la riga apre (l'app del task, o «Modifica task»)
        if (t.done) r.setOnClickListener(toggle);
        else {
            boxWrap.setMinimumHeight(U.dp(this, 48));
            final TaskRows.State st = new TaskRows.State();
            st.id = t.id; st.title = t.title; st.urgent = t.urgent; st.box = tbox; st.titleView = title; st.bang = bang; st.row = r;
            r.setTag(U.R.id_task, st);
            boxWrap.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { TaskRows.toggle(st, taskHost); }
            });
            boxWrap.setContentDescription("Segna fatto: " + t.title);
            Press.checkHalo(boxWrap, MotionLogic.ALONE_ICONA_DP, 9); // l'alone (alla casella, 56 dp) si disegna sopra la pagina: la casella è sul bordo
            U.widenLeft(r, boxWrap, 18);
            if (t.app != null && U.installed(this, t.app)) {
                // 0.13: il task apre la sua app. E001: l'app sta a destra
                final String label = U.appLabel(this, t.app);
                TextView open = U.text(this, label + " ›", 13, U.MUTED, false);
                open.setPadding(U.dp(this, 10), 0, 0, 0);
                r.addView(open);
                r.setContentDescription(spoken + ", apri " + label);
                r.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { if (!U.launchPkg(MainActivity.this, t.app)) U.toast(MainActivity.this, label + " non si apre. Controlla che sia installata"); }
                });
            } else {
                r.setContentDescription(spoken + ", modifica");
                r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { editTask(MainActivity.this, t); } });
            }
        }
        r.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { taskMenu(t, scope); return true; }
        });
        return r;
    }

    /** 0.20 (Evolutiva 016): la spunta salva subito; la riga si chiude dopo; la pagina si rifà quando tutte hanno finito. */
    private final TaskRows.Host taskHost = new TaskRows.Host() {
        public void setDone(String id, boolean done) { Tasks.setDone(MainActivity.this, id, done); }
        public void gone(String id) { hiddenDone.add(id); }
        public void reopened(String id) { hiddenDone.remove(id); }
        public void festa(int kind, View row) {
            if (row.getParent() != today || festa == null) return; // vale per i task di oggi, non per quelli di domani
            festa.play(kind);
        }
        public void busy(int delta) {
            busyRows = Math.max(0, busyRows + delta);
            if (busyRows == 0) { dirty = false; buildToday(); buildTomorrow(); refreshNotif(); }
        }
    };

    /** 0.20 (Evolutiva 016): la Ricerca entra dal basso (salita e dissolvenza, BASE), dallo swipe e dal tocco sulla barra. */
    private void openSearch() {
        startActivity(new Intent(this, SearchActivity.class));
        Motion.openSearch(this);
    }

    /** 0.15: con un tocco lungo si sposta il task nella giornata, come in Keep; E001: «! Urgente»; o si elimina. */
    private void taskMenu(final Tasks.Task t, final List<Tasks.Task> scope) {
        int at = -1;
        if (scope != null) for (int i = 0; i < scope.size(); i++) if (scope.get(i).id.equals(t.id)) at = i;
        Menu m = new Menu(this, t.title);
        if (!t.done) m.item("Modifica task", false, new Runnable() { public void run() { editTask(MainActivity.this, t); } });
        if (at > 0) m.item("Sposta su", false, new Runnable() { public void run() { Tasks.move(MainActivity.this, scope, t.id, -1); refresh(); } });
        if (at >= 0 && at < scope.size() - 1) m.item("Sposta giù", false, new Runnable() { public void run() { Tasks.move(MainActivity.this, scope, t.id, 1); refresh(); } });
        if (!t.done) m.toggle(U.bang("Urgente"), "Urgente", t.urgent, new Runnable() {
            public void run() { Tasks.setUrgent(MainActivity.this, t.id, !t.urgent); refresh(); }
        });
        m.item("Elimina task", true, new Runnable() { public void run() { confirmDelete(t); } }).show();
    }

    /** 0.16: elimina subito, con «Annulla» (prima chiedeva conferma, unico punto senza Annulla). */
    private void confirmDelete(final Tasks.Task t) {
        Changes.track(this, "Task eliminato: " + t.title, new String[]{"tasks"},
                new Runnable() { public void run() { Tasks.delete(MainActivity.this, t.id); } },
                new Runnable() { public void run() { refresh(); } });
    }

    /** 0.16: «Modifica task» apre Aggiungi già compilato. */
    static void editTask(android.app.Activity a, Tasks.Task t) {
        a.startActivity(new Intent(a, AddActivity.class).putExtra("kind", "task").putExtra("task_id", t.id));
    }

    private void refreshNotif() {
        boolean on = U.listenerEnabled(this);
        filterOff.setVisibility(on ? View.GONE : View.VISIBLE);
        int n = on ? Store.count(this, Store.WAITING) : 0;
        notifCount.set(n == 0 ? "" : String.valueOf(n), hasWindowFocus()); // momento 12: scorre solo se lo vedi cambiare
        notifBadge.setVisibility(n == 0 ? View.GONE : View.VISIBLE);
        notifBox.setContentDescription(n == 0 ? "Notifiche" : "Notifiche, " + n + " per te");
        // 0.10: una riga sola alla volta. Prima la fine della prova di «Di notte», poi la configurazione
        // 0.15 (211): il controllo viene prima di tutto: è la proposta della settimana
        int check = Checks.current(this);
        boolean night = check == 0 && Night.trialOver(this);
        // 0.14 (197): «Da completare» sostituisce «Continua la configurazione»
        java.util.List<String[]> todo = Onboarding.todo(this);
        setupLine.setVisibility(check > 0 || night || Onboarding.showTodo(this) ? View.VISIBLE : View.GONE);
        View setupX = setupLine.findViewWithTag("setupX");
        if (setupX != null) setupX.setVisibility(check > 0 ? View.GONE : View.VISIBLE);
        if (setupLine.getVisibility() == View.VISIBLE) {
            TextView t = (TextView) setupLine.findViewWithTag("setup");
            String[] when = {"Due settimane", "Un mese", "Tre mesi"};
            if (check > 0) t.setText(U.brand(when[check - 1] + " con −minus\nCom'è andata? · controllo " + check + " di 3  ›"));
            else if (night) t.setText("Una settimana di sonno: com'è andata ›");
            else t.setText(U.brand("Da completare · " + todo.size() + "\n" + todo.get(0)[1] + (todo.size() > 1 ? " e altro" : "") + "  ›"));
        }
    }

    private View setupRow() {
        LinearLayout r = U.row(this);
        r.setMinimumHeight(U.dp(this, 46));
        r.setPadding(U.dp(this, 14), 0, U.dp(this, 4), 0);
        r.setBackground(U.box(this, U.SURFACE, U.LINE, 12));
        TextView t = U.text(this, "Continua la configurazione", 14, U.INK, false);
        t.setTag("setup");
        Press.row(t);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int check = Checks.current(MainActivity.this);
                if (check > 0) startActivity(new Intent(MainActivity.this, CheckActivity.class).putExtra("n", check));
                else if (Night.trialOver(MainActivity.this)) startActivity(new Intent(MainActivity.this, NightReportActivity.class));
                else {
                    java.util.List<String[]> todo = Onboarding.todo(MainActivity.this);
                    if (todo.size() == 1) Onboarding.fix(MainActivity.this, todo.get(0)[0]);
                    else startActivity(new Intent(MainActivity.this, SettingsActivity.class).putExtra("area", SettingsActivity.TODO).putExtra("back", "Home"));
                }
            }
        });
        TextView x = U.text(this, "✕", 14, U.MUTED, false);
        x.setTag("setupX");
        x.setGravity(Gravity.CENTER);
        x.setContentDescription("Non mostrare più");
        Press.icon(x, null);
        x.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (Night.trialOver(MainActivity.this)) { Night.closeWeek(MainActivity.this); U.prefs(MainActivity.this).edit().putBoolean(Night.K_REPORT_SEEN, true).putLong(Night.K_TRIAL, 0).apply(); }
                else Onboarding.dismissTodo(MainActivity.this);
                refreshNotif();
            }
        });
        r.addView(x, new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 44)));
        r.setVisibility(View.GONE);
        return r;
    }

    // ---------- Cerca o chiedi ----------

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
    }
}
