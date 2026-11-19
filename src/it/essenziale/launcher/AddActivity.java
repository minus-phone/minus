package it.essenziale.launcher;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Aggiungi un impegno (nel calendario) o un task (in Essenziale).
 * Con l'extra "event_id" diventa "Modifica impegno": salva, ripeti, elimina (con la scelta per le serie).
 */
public class AddActivity extends BaseActivity {

    private static final int REQ_VOICE = 20, REQ_WRITE = 21, REQ_REPEAT = 22;
    private static final int SCOPE_ONE = 0, SCOPE_NEXT = 1, SCOPE_ALL = 2;

    private boolean isEvent = true, allDay = false;
    private long day = U.startOfDay(0);   // 0 = nessuna scadenza (solo task)
    private int fromMin = -1, toMin = -1;
    private long calId = -1;
    private String calName;
    private TextView calNote;
    private Rec rec = new Rec();

    // Modifica
    private U.EventData orig;
    private long instBegin, instEnd;

    private EditText title, note;
    private TextView tabEvent, tabTask, whenLabel, fromBtn, toBtn, calBtn, repeatBtn;
    private LinearLayout allDayWrap, urgentWrap;
    private boolean urgent;
    private Tasks.Task editTask;
    private TextView titleLabel, bangView;
    private LinearLayout chips, eventOnly, times, tabs, calBlock, taskOnly;
    private android.widget.EditText appField; // 0.17: «Si apre con» è una casella che filtra mentre scrivi
    private TextView appClear;
    private LinearLayout appSugg;
    private boolean appSetting;
    private ScrollView scrollView;
    private View rootView, comboWrap;
    private final List<View> bottom = new ArrayList<View>(); // 0.18: «Elimina task» e «Salva», nascosti a tendina aperta
    private String taskApp;
    private boolean appChosen;
    private static final String KEEP = "com.google.android.keep";

    private boolean editing() { return orig != null; }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent in = getIntent();
        isEvent = !"task".equals(in.getStringExtra("kind"));
        Calendar now = Calendar.getInstance();
        fromMin = Math.min(23 * 60, (now.get(Calendar.HOUR_OF_DAY) + 1) * 60);
        toMin = Math.min(23 * 60 + 59, fromMin + 60);
        calId = U.prefs(this).getLong("last_cal", -1);
        calName = U.prefs(this).getString("last_cal_name", null);

        // 0.16: «Modifica task» apre Aggiungi già compilato
        String tid = in.getStringExtra("task_id");
        if (tid != null) for (Tasks.Task t : Tasks.all(this)) if (t.id.equals(tid)) editTask = t;
        if (editTask != null) {
            isEvent = false;
            day = editTask.due;
            urgent = editTask.urgent;
            taskApp = editTask.app;
            appChosen = true;
        }
        long eventId = in.getLongExtra("event_id", -1);
        if (eventId >= 0) {
            orig = U.loadEvent(this, eventId);
            if (orig == null) { U.toast(this, "Impegno non trovato: forse è stato eliminato"); finish(); return; }
            instBegin = in.getLongExtra("inst_begin", orig.start);
            instEnd = in.getLongExtra("inst_end", orig.end);
            isEvent = true;
            allDay = orig.allDay;
            if (allDay) {
                day = localDayOfUtc(instBegin);
            } else {
                Calendar k = Calendar.getInstance();
                k.setTimeInMillis(instBegin);
                day = dayStart(instBegin);
                fromMin = k.get(Calendar.HOUR_OF_DAY) * 60 + k.get(Calendar.MINUTE);
                long dur = Math.max(5 * 60000L, instEnd - instBegin);
                toMin = Math.min(23 * 60 + 59, fromMin + (int) (dur / 60000L));
            }
            calId = orig.calId;
            calName = orig.calName;
            rec = Rec.parse(orig.rrule, instBegin);
        } else {
            if (in.hasExtra("day")) day = in.getLongExtra("day", day);
            if (in.hasExtra("from")) {
                fromMin = in.getIntExtra("from", fromMin);
                toMin = Math.min(23 * 60 + 59, fromMin + 60);
            }
        }

        LinearLayout root = U.col(this);
        root.setBackgroundColor(U.BG);
        root.setPadding(U.dp(this, 20), U.dp(this, 16), U.dp(this, 20), U.dp(this, 16));
        TextView cancel = U.text(this, "Annulla", 16, U.INK, false);
        cancel.setMinHeight(U.dp(this, 44));
        cancel.setGravity(Gravity.CENTER_VERTICAL);
        cancel.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        root.addView(cancel);

        final ScrollView sv = new ScrollView(this);
        scrollView = sv;
        LinearLayout c = U.col(this);
        sv.addView(c);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (editing()) {
            if (calName != null) c.addView(U.text(this, calName, 13, U.MUTED, false));
            c.addView(U.text(this, "Modifica impegno", 24, U.INK, true));
        } else if (editTask != null) c.addView(U.text(this, "Modifica task", 24, U.INK, true));

        tabs = U.row(this);
        tabs.setBackground(U.box(this, U.CHIP, 0, 24));
        tabs.setPadding(U.dp(this, 4), U.dp(this, 4), U.dp(this, 4), U.dp(this, 4));
        tabEvent = tab("Impegno", true);
        tabTask = tab("Task", false);
        tabs.addView(tabEvent, new LinearLayout.LayoutParams(0, U.dp(this, 42), 1f));
        tabs.addView(tabTask, new LinearLayout.LayoutParams(0, U.dp(this, 42), 1f));
        c.addView(tabs, U.full());
        if (editing() || editTask != null) tabs.setVisibility(View.GONE);

        titleLabel = U.label(this, "Titolo");
        c.addView(titleLabel);
        LinearLayout tf = U.row(this);
        tf.setBackground(U.box(this, U.SURFACE, U.BORDER, 12));
        tf.setPadding(U.dp(this, 14), U.dp(this, 2), U.dp(this, 6), U.dp(this, 2));
        // 0.16: con Urgente acceso un «!» rosso fisso davanti al titolo: non si cancella da tastiera
        bangView = U.text(this, "!", 16, U.URGENT, true);
        bangView.setPadding(0, 0, U.dp(this, 8), 0);
        bangView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tf.addView(bangView);
        title = new EditText(this);
        title.setBackground(null);
        title.setTextSize(16);
        title.setTextColor(U.INK);
        title.setHintTextColor(U.HINT);
        title.setSingleLine(true);
        title.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        tf.addView(title, new LinearLayout.LayoutParams(0, U.dp(this, 50), 1f));
        MicView mic = new MicView(this);
        mic.setContentDescription("Detta il titolo");
        mic.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { voice(); } });
        tf.addView(mic, new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 44)));
        c.addView(tf, U.full());
        if (editing()) title.setText(orig.title);
        else if (editTask != null) title.setText(editTask.title);
        else if (in.getStringExtra("title") != null) title.setText(in.getStringExtra("title"));

        whenLabel = U.label(this, "Giorno");
        c.addView(whenLabel);
        chips = U.row(this);
        c.addView(chips, U.full());

        // E001: «! Urgente» sotto la scadenza, solo per i task
        urgentWrap = U.col(this);
        c.addView(urgentWrap, U.full());

        eventOnly = U.col(this);

        // 0.16: «Tutto il giorno» sopra l'orario; acceso, la riga Orario sparisce
        allDayWrap = U.col(this);
        eventOnly.addView(allDayWrap, U.full());
        // 0.16: orario, ripetizione e calendario sono righe compatte da 48 dp, il valore sottolineato a destra
        fromBtn = field();
        toBtn = field();
        times = compact("Orario", fromBtn);
        TextView dash = U.text(this, "–", 16, U.MUTED, false);
        dash.setPadding(U.dp(this, 6), 0, U.dp(this, 6), 0);
        times.addView(dash);
        times.addView(toBtn);
        eventOnly.addView(times, U.full());

        repeatBtn = field();
        eventOnly.addView(compact("Ripeti", repeatBtn), U.full());

        calBlock = U.col(this);
        calBtn = field();
        calBlock.addView(compact("Calendario", calBtn), U.full());
        calNote = U.text(this, "", 12, U.MUTED, false);
        calNote.setPadding(U.dp(this, 4), U.dp(this, 4), 0, 0);
        calBlock.addView(calNote);
        eventOnly.addView(calBlock, U.full());

        eventOnly.addView(U.label(this, "Note"));
        note = new EditText(this);
        note.setTextSize(15);
        note.setTextColor(U.INK);
        note.setHint("Facoltative");
        note.setHintTextColor(U.HINT);
        note.setBackground(U.box(this, U.SURFACE, U.BORDER, 12));
        note.setPadding(U.dp(this, 14), U.dp(this, 10), U.dp(this, 14), U.dp(this, 10));
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        note.setMinLines(2);
        note.setGravity(Gravity.TOP);
        if (editing()) note.setText(orig.desc);
        eventOnly.addView(note, U.full());
        c.addView(eventOnly, U.full());

        // 0.13: un task può aprire un'app («Fare la spesa» → Keep, per le liste condivise)
        taskOnly = U.col(this);
        taskOnly.addView(U.label(this, "Si apre con"));
        taskOnly.addView(appCombo(), U.full());
        TextView an = U.text(this, "Toccando il task si apre l'app; la casella lo segna come fatto.", 12, U.MUTED, false);
        an.setPadding(0, U.dp(this, 6), 0, 0);
        taskOnly.addView(an);
        c.addView(taskOnly, U.full());
        title.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            public void onTextChanged(CharSequence s, int a, int b, int d) {}
            public void afterTextChanged(android.text.Editable e) {
                // la spesa propone Keep, se c'è e non hai scelto altro
                if (taskApp == null && !appChosen && e.toString().toLowerCase(java.util.Locale.ITALY).matches(".*\\b(spesa|lista)\\b.*")
                        && U.installed(AddActivity.this, KEEP)) { taskApp = KEEP; render(); }
            }
        });

        fromBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickTime(true); } });
        toBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickTime(false); } });
        calBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickCalendar(); } });
        repeatBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickRepeat(); } });

        if (editing()) {
            // Inviti, promemoria, fusi orari, colori: restano all'app di calendario.
            TextView cal = U.text(this, "Apri nel calendario  ›", 15, U.INK, false);
            cal.setGravity(Gravity.CENTER);
            cal.setMinHeight(U.dp(this, 44));
            cal.setPaintFlags(cal.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            cal.setContentDescription("Apri l'impegno nell'app di calendario, per inviti e promemoria");
            cal.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    U.Cal info = U.calInfo(AddActivity.this, orig.calId);
                    if (info != null && !info.google() && U.installed(AddActivity.this, "com.google.android.calendar"))
                        U.toast(AddActivity.this, "Se Google Calendar non lo trova: mostra solo i calendari Google. Per " + info.name + " serve un'app come Fossify Calendar");
                    Intent i = new Intent(Intent.ACTION_VIEW, android.content.ContentUris.withAppendedId(android.provider.CalendarContract.Events.CONTENT_URI, orig.id));
                    i.putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, instBegin);
                    i.putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, instEnd);
                    U.start(AddActivity.this, i);
                }
            });
            LinearLayout.LayoutParams clp = U.full();
            clp.topMargin = U.dp(this, 4);
            root.addView(cal, clp);
            TextView del = U.text(this, "Elimina impegno", 16, U.ALERT, true);
            del.setGravity(Gravity.CENTER);
            del.setMinHeight(U.dp(this, 48));
            del.setBackground(U.box(this, U.BG, U.ALERT, 24));
            del.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { askDelete(); } });
            LinearLayout.LayoutParams dlp = U.full();
            dlp.topMargin = U.dp(this, 8);
            root.addView(del, dlp);
        }

        if (editTask != null) {
            // 0.16: elimina subito; «Annulla» compare in home
            TextView del = U.text(this, "Elimina task", 16, U.URGENT, true);
            del.setGravity(Gravity.CENTER);
            del.setMinHeight(U.dp(this, 48));
            del.setBackground(U.box(this, U.BG, U.URGENT, 24));
            del.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Changes.record(AddActivity.this, "Task eliminato: " + editTask.title, new String[]{"tasks"});
                    Tasks.delete(AddActivity.this, editTask.id);
                    startActivity(new Intent(AddActivity.this, MainActivity.class).putExtra("undo", true)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    finish();
                }
            });
            LinearLayout.LayoutParams dlp = U.full();
            dlp.topMargin = U.dp(this, 8);
            root.addView(del, dlp);
            bottom.add(del);
        }
        TextView save = U.text(this, editing() || editTask != null ? "Salva modifiche" : "Salva", 16, U.BG, true);
        save.setGravity(Gravity.CENTER);
        save.setMinHeight(U.dp(this, 52));
        Press.primary(save, U.INK, 26);
        save.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { save(); } });
        LinearLayout.LayoutParams slp = U.full();
        slp.topMargin = U.dp(this, 10);
        root.addView(save, slp);
        bottom.add(save);
        root.setFocusableInTouchMode(true); // 0.18: chiudendo la tendina il cursore non salta in un altro campo
        rootView = root;

        setContentView(root);
        render();
        if (editing() && in.getBooleanExtra("ask_delete", false)) askDelete(); // 0.15: dal tocco lungo «Elimina impegno»
    }

    static long dayStart(long t) {
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(t);
        k.set(Calendar.HOUR_OF_DAY, 0); k.set(Calendar.MINUTE, 0); k.set(Calendar.SECOND, 0); k.set(Calendar.MILLISECOND, 0);
        return k.getTimeInMillis();
    }

    /** Gli impegni "tutto il giorno" sono a mezzanotte UTC: il giorno locale corrispondente. */
    static long localDayOfUtc(long utc) {
        Calendar u = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        u.setTimeInMillis(utc);
        Calendar l = Calendar.getInstance();
        l.clear();
        l.set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH));
        return l.getTimeInMillis();
    }

    private long startMillis() { return allDay ? day : day + fromMin * 60000L; }

    private long endMillis() { return allDay ? day + 86400000L : day + toMin * 60000L; }

    private TextView tab(String s, final boolean event) {
        TextView t = U.text(this, s, 15, U.INK, true);
        t.setGravity(Gravity.CENTER);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                isEvent = event;
                if (isEvent && day == 0) day = U.startOfDay(0);
                render();
            }
        });
        return t;
    }

    private TextView field() {
        TextView t = U.text(this, "", 16, U.INK, false);
        t.setMinHeight(U.dp(this, 48));
        t.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        t.setPaintFlags(t.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        return t;
    }

    /** 0.16: una riga da 48 dp, l'etichetta a sinistra e il valore a destra. */
    private LinearLayout compact(String label, TextView value) {
        LinearLayout r = U.row(this);
        r.setMinimumHeight(U.dp(this, 48));
        r.addView(U.text(this, label, 16, U.INK, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(value);
        return r;
    }

    private void render() {
        if (isEvent) Press.chipOn(tabEvent, 20);
        tabEvent.setTextColor(isEvent ? U.BG : U.INK);
        tabTask.setBackground(!isEvent ? U.box(this, U.INK, 0, 20) : null);
        tabTask.setTextColor(!isEvent ? U.BG : U.INK);
        titleLabel.setText(isEvent ? "TITOLO" : "COSA DEVI FARE?");
        bangView.setVisibility(!isEvent && urgent ? View.VISIBLE : View.GONE);
        title.setHint(isEvent ? "Esempio: Pediatra" : "Esempio: Pagare la retta del nido");
        whenLabel.setText(isEvent ? "GIORNO" : "SCADENZA");
        eventOnly.setVisibility(isEvent ? View.VISIBLE : View.GONE);
        if (taskOnly != null) {
            taskOnly.setVisibility(isEvent ? View.GONE : View.VISIBLE);
            if (!appField.hasFocus()) setAppText(taskApp == null ? "" : U.appLabel(this, taskApp));
            appClear.setVisibility(appField.getText().length() > 0 ? View.VISIBLE : View.GONE);
        }
        times.setVisibility(allDay ? View.GONE : View.VISIBLE);
        allDayWrap.removeAllViews();
        allDayWrap.addView(U.switchRow(this, "Tutto il giorno", "Tutto il giorno", allDay, new View.OnClickListener() {
            public void onClick(View v) { allDay = !allDay; render(); }
        }), U.full());
        urgentWrap.removeAllViews();
        urgentWrap.setVisibility(isEvent ? View.GONE : View.VISIBLE);
        urgentWrap.addView(U.switchRow(this, U.bang("Urgente"), "Urgente", urgent, new View.OnClickListener() {
            public void onClick(View v) {
                urgent = !urgent;
                if (urgent && day == 0) day = U.startOfDay(0); // urgente vuol dire con una scadenza
                render();
            }
        }), U.full());

        chips.removeAllViews();
        boolean custom = day != 0 && day != U.startOfDay(0) && day != U.startOfDay(1);
        chip("Oggi", day == U.startOfDay(0), U.startOfDay(0));
        chip("Domani", day == U.startOfDay(1), U.startOfDay(1));
        if (!isEvent) chip("Nessuna", day == 0, 0);
        chip(custom ? U.dayLabel(day) : "Scegli…", custom, -1);

        fromBtn.setText(fmt(fromMin));
        toBtn.setText(fmt(toMin));
        fromBtn.setTypeface(U.SERIF);
        toBtn.setTypeface(U.SERIF);
        calBtn.setText(calName != null ? calName : "Scegli");
        U.Cal info = U.calInfo(this, calId);
        calNote.setText(info == null ? "" : U.calNote(info));
        calNote.setVisibility(info == null ? View.GONE : View.VISIBLE);
        repeatBtn.setText(rec.shortLabel(startMillis()));
    }

    private void chip(String label, boolean on, final long value) {
        TextView t = U.text(this, label, 14, on ? U.BG : U.INK, false);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(U.dp(this, 44));
        t.setPadding(U.dp(this, 14), 0, U.dp(this, 14), 0);
        if (on) Press.chipOn(t, 21); else Press.chip(t, U.BG, U.LINE, 21);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (value == -1) pickDate();
                else { day = value; if (day == 0) urgent = false; render(); }
            }
        });
        LinearLayout.LayoutParams lp = U.wrap();
        lp.rightMargin = U.dp(this, 6);
        chips.addView(t, lp);
    }

    private static String fmt(int min) {
        return String.format(Locale.ITALY, "%02d:%02d", min / 60, min % 60);
    }

    private void pickDate() {
        Calendar k = Calendar.getInstance();
        if (day > 0) k.setTimeInMillis(day);
        new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            public void onDateSet(DatePicker v, int y, int m, int d) {
                Calendar s = Calendar.getInstance();
                s.clear();
                s.set(y, m, d);
                day = s.getTimeInMillis();
                render();
            }
        }, k.get(Calendar.YEAR), k.get(Calendar.MONTH), k.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void pickTime(final boolean from) {
        int m = from ? fromMin : toMin;
        new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            public void onTimeSet(TimePicker v, int h, int mi) {
                int val = h * 60 + mi;
                if (from) {
                    int dur = Math.max(15, toMin - fromMin);
                    fromMin = val;
                    toMin = Math.min(23 * 60 + 59, val + dur);
                } else toMin = Math.max(val, fromMin + 5);
                render();
            }
        }, m / 60, m % 60, true).show();
    }

    private void pickCalendar() {
        if (!U.canWriteCalendar(this)) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_CALENDAR, android.Manifest.permission.READ_CALENDAR}, REQ_WRITE);
            return;
        }
        if (editing() && orig.rrule != null && orig.rrule.length() > 0) {
            U.toast(this, "Una serie si sposta in un altro calendario dall'app di calendario: usa «Apri nel calendario»");
            return;
        }
        final List<U.Cal> cals = U.writableCalendars(this);
        if (cals.isEmpty()) { U.toast(this, "Nessun calendario modificabile sul telefono"); return; }
        List<CharSequence> names = new ArrayList<CharSequence>();
        for (U.Cal k : cals) {
            android.text.SpannableString s = new android.text.SpannableString(k.name + "\n" + U.calNote(k));
            s.setSpan(new android.text.style.RelativeSizeSpan(0.8f), k.name.length() + 1, s.length(), 0);
            s.setSpan(new android.text.style.ForegroundColorSpan(U.MUTED), k.name.length() + 1, s.length(), 0);
            names.add(s);
        }
        U.show(U.dlg(this)
                .setTitle(editing() ? "Sposta in" : "Calendario")
                .setItems(names.toArray(new CharSequence[0]), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        U.Cal k = cals.get(w);
                        if (!k.sync && !k.local() && U.setSync(AddActivity.this, k.id, true))
                            U.toast(AddActivity.this, "Ho riacceso la sincronizzazione di " + k.name);
                        calId = k.id;
                        calName = k.name;
                        // il calendario scelto per un impegno nuovo diventa quello di partenza per i prossimi
                        if (!editing()) U.prefs(AddActivity.this).edit().putLong("last_cal", calId).putString("last_cal_name", calName).apply();
                        render();
                    }
                }));
    }

    // ---------- 0.17 · «Si apre con»: combo box ----------

    private View appCombo() {
        LinearLayout wrap = U.col(this);
        LinearLayout box = U.row(this);
        box.setBackground(U.box(this, U.SURFACE, U.BORDER, 12));
        box.setPadding(U.dp(this, 14), 0, U.dp(this, 4), 0);
        appField = new android.widget.EditText(this) {
            // 0.18: Indietro chiude insieme tendina e tastiera (prima chiudeva solo la tastiera)
            @Override public boolean onKeyPreIme(int code, android.view.KeyEvent e) {
                if (code == android.view.KeyEvent.KEYCODE_BACK && comboOpen()) {
                    if (e.getAction() == android.view.KeyEvent.ACTION_UP) closeCombo();
                    return true;
                }
                return super.onKeyPreIme(code, e);
            }
        };
        comboWrap = wrap;
        appField.setBackground(null);
        appField.setTextSize(15 * U.textScale);
        appField.setTextColor(U.INK);
        appField.setHintTextColor(U.HINT);
        appField.setHint("Scrivi il nome dell'app");
        appField.setSingleLine(true);
        appField.setMinHeight(U.dp(this, 48));
        appField.setPadding(0, 0, 0, 0);
        appField.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        appField.setContentDescription("Si apre con");
        box.addView(appField, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        appClear = U.text(this, "✕", 16, U.INK, false);
        appClear.setGravity(Gravity.CENTER);
        appClear.setContentDescription("Togli l'app");
        appClear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { appChosen = true; taskApp = null; setAppText(""); showSugg(""); appField.requestFocus(); }
        });
        box.addView(appClear, new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 44)));
        wrap.addView(box, U.full());
        appSugg = U.col(this);
        appSugg.setVisibility(View.GONE);
        appSugg.setBackground(U.box(this, U.SURFACE, U.BORDER, 12));
        LinearLayout.LayoutParams lp = U.full();
        lp.topMargin = U.dp(this, 4);
        wrap.addView(appSugg, lp);
        appField.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                if (has) { showSugg(appField.getText().toString().equals(taskApp == null ? "" : U.appLabel(AddActivity.this, taskApp)) ? "" : appField.getText().toString()); return; }
                if (comboOpen()) closeCombo();
            }
        });
        appField.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            public void onTextChanged(CharSequence s, int a, int b, int d) {}
            public void afterTextChanged(android.text.Editable e) {
                appClear.setVisibility(e.length() > 0 ? View.VISIBLE : View.GONE);
                if (!appSetting && appField.hasFocus()) showSugg(e.toString());
            }
        });
        appField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int id, android.view.KeyEvent ev) {
                // «Fatto» sulla tastiera sceglie il primo suggerimento
                List<U.AppInfo> m = matches(appField.getText().toString());
                if (!m.isEmpty() && appField.getText().length() > 0) chooseApp(m.get(0));
                else closeCombo(); // 0.18: a campo vuoto «Fatto» chiude senza scegliere
                return true;
            }
        });
        return wrap;
    }

    private void setAppText(String t) {
        appSetting = true;
        appField.setText(t);
        appField.setSelection(t.length());
        appSetting = false;
    }

    /** Vuota: le app già usate nei task e quelle a portata (5 al massimo), poi «Tutte le app ›». Scritta: chi corrisponde. */
    private List<U.AppInfo> matches(String q) {
        List<U.AppInfo> all = U.sortedApps(this), out = new ArrayList<U.AppInfo>();
        if (Cerca.norm(q).length() == 0) {
            java.util.LinkedHashSet<String> pk = new java.util.LinkedHashSet<String>();
            for (Tasks.Task t : Tasks.all(this)) if (t.app != null) pk.add(t.app);
            for (String[] r : SearchActivity.portata(this)) pk.add(r[0]);
            for (String p : pk) {
                for (U.AppInfo a : all) if (a.pkg.equals(p)) { out.add(a); break; }
                if (out.size() == 5) break;
            }
            return out;
        }
        for (int sc = 3; sc >= 1; sc--) for (U.AppInfo a : all) if (Cerca.score(a.label, q) == sc) out.add(a);
        return out.size() > 6 ? out.subList(0, 6) : out;
    }

    private void showSugg(String q) {
        appSugg.removeAllViews();
        List<U.AppInfo> m = matches(q);
        for (final U.AppInfo a : m) {
            TextView t = U.text(this, a.label, 15, U.INK, a.pkg.equals(taskApp));
            t.setMinHeight(U.dp(this, 48));
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(U.dp(this, 14), 0, U.dp(this, 14), 0);
            t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { chooseApp(a); } });
            appSugg.addView(t, U.full());
        }
        boolean empty = Cerca.norm(q).length() == 0;
        if (!empty && m.isEmpty()) {
            TextView none = U.text(this, "Nessuna app con questo nome", 14, U.MUTED, false);
            none.setMinHeight(U.dp(this, 48));
            none.setGravity(Gravity.CENTER_VERTICAL);
            none.setPadding(U.dp(this, 14), 0, U.dp(this, 14), 0);
            appSugg.addView(none, U.full());
        }
        if (empty) {
            TextView more = U.text(this, "Tutte le app  ›", 15, U.INK, false);
            more.setMinHeight(U.dp(this, 48));
            more.setGravity(Gravity.CENTER_VERTICAL);
            more.setPadding(U.dp(this, 14), 0, U.dp(this, 14), 0);
            more.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickApp(); } });
            appSugg.addView(more, U.full());
        }
        appSugg.setVisibility(View.VISIBLE);
        for (View b : bottom) b.setVisibility(View.GONE); // 0.18: la tendina ha tutto lo spazio sopra la tastiera
        // 0.17 (Evolutiva 004): etichetta, campo e tendina sopra la tastiera (la pagina si ridimensiona: adjustResize)
        if (scrollView != null) scrollView.postDelayed(new Runnable() {
            public void run() {
                View label = (View) appField.getParent().getParent().getParent();
                int[] a = new int[2], b = new int[2];
                label.getLocationInWindow(a);
                scrollView.getLocationInWindow(b);
                int top = a[1] - b[1] + scrollView.getScrollY() - U.dp(AddActivity.this, 24);
                scrollView.smoothScrollTo(0, Math.max(0, top));
            }
        }, 250);
        appSugg.announceForAccessibility(empty ? m.size() + " app suggerite" : m.size() == 1 ? "1 app trovata" : m.size() + " app trovate");
    }

    private void chooseApp(U.AppInfo a) {
        appChosen = true;
        taskApp = a == null ? null : a.pkg;
        closeCombo();
    }

    private boolean comboOpen() { return appSugg != null && appSugg.getVisibility() == View.VISIBLE; }

    /** 0.18: chiude tendina e tastiera senza scegliere: resta l'app già scelta; a campo svuotato, nessuna app. */
    private void closeCombo() {
        if (appField.getText().length() == 0 && taskApp != null) { appChosen = true; taskApp = null; }
        appSugg.setVisibility(View.GONE);
        setAppText(taskApp == null ? "" : U.appLabel(this, taskApp));
        for (View b : bottom) b.setVisibility(View.VISIBLE);
        try {
            ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(appField.getWindowToken(), 0);
        } catch (Exception ignored) {}
        if (rootView != null) rootView.requestFocus();
        render();
    }

    /** 0.18: un tocco fuori dal campo e dalla tendina li chiude. */
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (ev.getAction() == android.view.MotionEvent.ACTION_DOWN && comboOpen() && comboWrap != null) {
            int[] xy = new int[2];
            comboWrap.getLocationOnScreen(xy);
            float x = ev.getRawX(), y = ev.getRawY();
            if (x < xy[0] || x > xy[0] + comboWrap.getWidth() || y < xy[1] || y > xy[1] + comboWrap.getHeight()) { closeCombo(); return true; }
        }
        return super.dispatchTouchEvent(ev);
    }

    /** «Tutte le app ›»: l'elenco completo, come prima. */
    private void pickApp() {
        final List<U.AppInfo> apps = U.sortedApps(this);
        List<CharSequence> names = new ArrayList<CharSequence>();
        for (U.AppInfo a : apps) names.add(a.label);
        U.show(U.dlg(this)
                .setTitle("Si apre con")
                .setItems(names.toArray(new CharSequence[0]), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { chooseApp(apps.get(w)); }
                }).setNegativeButton("Annulla", null));
    }

    /** 0.15: «Ripeti» è una selezione di sistema, al posto della pagina. */
    private void pickRepeat() {
        final String[] names = {"Mai", "Ogni giorno", "Ogni settimana", "Ogni mese", "Ogni anno", "Personalizza…"};
        final int[] freqs = {Rec.NONE, Rec.DAILY, Rec.WEEKLY, Rec.MONTHLY, Rec.YEARLY};
        int sel = rec.repeats() ? 5 : -1;
        if (rec.custom == null && rec.interval == 1 && rec.end == Rec.END_NEVER
                && (rec.freq != Rec.WEEKLY || rec.days == 0 || Integer.bitCount(rec.days) == 1))
            for (int i = 0; i < freqs.length; i++) if (freqs[i] == rec.freq) sel = i;
        U.dlg(this).setTitle("Ripeti")
                .setSingleChoiceItems(names, sel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        d.dismiss();
                        if (w == 5) {
                            RepeatDialog.show(AddActivity.this, rec, startMillis(), new RepeatDialog.Done() {
                                public void done(Rec r) { rec = r; render(); }
                            });
                            return;
                        }
                        Rec r = new Rec();
                        r.freq = freqs[w];
                        rec = r;
                        d.dismiss();
                        render();
                    }
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    // ---------- Salva ----------

    private void save() {
        final String t = title.getText().toString().trim();
        if (t.length() == 0) { U.toast(this, "Scrivi un titolo"); return; }
        if (!isEvent) {
            // E001: un «!» all'inizio rende il task urgente
            String b = Urgenti.bang(t);
            if (editTask != null) {
                Changes.record(this, "Task modificato: " + t, new String[]{"tasks"});
                Tasks.update(this, editTask.id, b != null ? b : t, day, taskApp, urgent || b != null);
                U.toast(this, "Task modificato");
                finish();
                return;
            }
            Tasks.add(this, b != null ? b : t, day, taskApp, urgent || b != null);
            U.toast(this, "Task aggiunto");
            finish();
            return;
        }
        if (!U.canWriteCalendar(this)) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_CALENDAR, android.Manifest.permission.READ_CALENDAR}, REQ_WRITE);
            return;
        }
        final String desc = note.getText().toString();
        if (!editing()) {
            if (calId < 0) { pickCalendar(); return; }
            long s = startMillis();
            if (U.insertEvent(this, calId, t, s, endMillis(), allDay, rec.toRule(s), desc) >= 0) {
                U.toast(this, "Impegno aggiunto in " + calName);
                finish();
            } else U.toast(this, "Impossibile salvare l'impegno");
            return;
        }
        if (orig.rrule == null || orig.rrule.length() == 0) {
            long s = startMillis();
            if (calId != orig.calId && calId >= 0) {
                // Android non sposta un impegno tra calendari: lo ricreo nel nuovo e tolgo il vecchio
                boolean ok = U.insertEvent(this, calId, t, s, endMillis(), allDay, rec.toRule(s), desc) >= 0;
                if (ok) U.deleteEvent(this, orig.id);
                done(ok, "Spostato in " + calName);
                return;
            }
            done(U.updateEvent(this, orig.id, t, s, endMillis(), allDay, rec.toRule(s), desc), "Modifiche salvate");
            return;
        }
        askScope("Salva", new Scope() {
            public void apply(int scope) { saveSeries(scope, t, desc); }
        });
    }

    private void saveSeries(int scope, String t, String desc) {
        long s = startMillis(), e = endMillis();
        if (scope == SCOPE_ONE) {
            done(U.exception(this, orig.id, instBegin, false, t, s, e, allDay, desc), "Modificato solo questo");
            return;
        }
        // Se cambio il giorno della settimana di una serie settimanale "semplice", la serie segue
        Rec r = rec.copy();
        if (r.freq == Rec.WEEKLY && Integer.bitCount(r.days) == 1 && r.days == (1 << Rec.dow(instStartLocal())))
            r.days = 1 << Rec.dow(s);
        if (scope == SCOPE_NEXT && !isFirstInstance()) {
            boolean ok = U.endSeriesBefore(this, orig.id, orig.rrule, instBegin)
                    && U.insertEvent(this, orig.calId, t, s, e, allDay, r.toRule(s), desc) >= 0;
            done(ok, "Modificati questo e i successivi");
            return;
        }
        // Tutta la serie: sposto l'inizio della serie dello stesso scarto dell'istanza
        long delta = s - instStartLocal();
        long masterLocal = (orig.allDay ? localDayOfUtc(orig.start) : orig.start) + delta;
        if (allDay) masterLocal = dayStart(masterLocal + 12 * 3600000L);
        long dur = e - s;
        done(U.updateEvent(this, orig.id, t, masterLocal, masterLocal + dur, allDay, r.toRule(masterLocal), desc), "Modificata tutta la serie");
    }

    /** Inizio dell'istanza in ora locale (gli impegni "tutto il giorno" sono in UTC). */
    private long instStartLocal() { return orig.allDay ? localDayOfUtc(instBegin) : instBegin; }

    private boolean isFirstInstance() { return instBegin <= orig.start; }

    private void done(boolean ok, String msg) {
        if (ok) { U.toast(this, msg); finish(); }
        else U.toast(this, "Non salvato: il calendario non lo permette");
    }

    // ---------- Elimina ----------

    private void askDelete() {
        final String t = orig.title;
        if (orig.rrule == null || orig.rrule.length() == 0) {
            U.dlg(this)
                    .setTitle("Eliminare “" + t + "”?")
                    .setMessage("Sparisce anche dal calendario" + (orig.calName != null ? " " + orig.calName : "") + ", anche per chi lo condivide.")
                    .setNegativeButton("No", null)
                    .setPositiveButton("Sì, elimina", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) { done(U.deleteEvent(AddActivity.this, orig.id), "Impegno eliminato"); }
                    }).show();
            return;
        }
        askScope("Elimina", new Scope() {
            public void apply(int scope) {
                boolean ok;
                if (scope == SCOPE_ONE) ok = U.exception(AddActivity.this, orig.id, instBegin, true, null, 0, 0, false, null);
                else if (scope == SCOPE_NEXT && !isFirstInstance()) ok = U.endSeriesBefore(AddActivity.this, orig.id, orig.rrule, instBegin);
                else ok = U.deleteEvent(AddActivity.this, orig.id);
                done(ok, "Eliminato");
            }
        });
    }

    interface Scope { void apply(int scope); }

    /** "A quali impegni applico le modifiche?" — come nei wireframe (Serie). */
    private void askScope(String action, final Scope then) {
        String when = new java.text.SimpleDateFormat("EEE d MMMM", Locale.ITALY).format(new java.util.Date(instStartLocal()));
        String[] items = {"Solo questo · " + when, "Questo e i successivi · da " + when + " in poi", "Tutta la serie · anche quelli passati"};
        final int[] sel = {SCOPE_ONE};
        U.dlg(this)
                .setTitle("“" + orig.title + "” si ripete. A quali impegni?")
                .setSingleChoiceItems(items, 0, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { sel[0] = w; }
                })
                .setNegativeButton("Annulla", null)
                .setPositiveButton(action, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { then.apply(sel[0]); }
                }).show();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        if (code == REQ_WRITE && U.canWriteCalendar(this) && calId < 0 && !editing()) pickCalendar();
    }

    private void voice() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT");
        try { startActivityForResult(i, REQ_VOICE); } catch (Exception e) { U.toast(this, "Riconoscimento vocale non disponibile"); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_VOICE && res == RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) {
                String s = r.get(0);
                title.setText(s.substring(0, 1).toUpperCase(Locale.ITALY) + s.substring(1));
                title.setSelection(title.getText().length());
            }
        } else if (req == REQ_REPEAT && res == RESULT_OK && data != null) {
            rec = Rec.parse(data.getStringExtra("rule"), startMillis());
            render();
        }
    }
}
