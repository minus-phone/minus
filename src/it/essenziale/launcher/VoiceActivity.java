package it.essenziale.launcher;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Conferma di un comando vocale: cosa ho capito, cosa succederà, un pulsante per farlo.
 * Niente parte da sola: messaggi e chiamate si aprono pronti, ma l'ultimo tocco è tuo.
 */
public class VoiceActivity extends BaseActivity {

    private static final int REQ_VOICE = 50;
    static final String GEMINI_PKG = "com.google.android.apps.bard";

    private LinearLayout card;
    private VoiceParser.Cmd cmd;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = U.col(this);
        root.setBackgroundColor(U.BG);
        root.setPadding(U.dp(this, 20), U.dp(this, 16), U.dp(this, 20), U.dp(this, 16));
        LinearLayout head = U.row(this);
        TextView back = U.text(this, "‹  Home", 16, U.INK, false);
        back.setMinHeight(U.dp(this, 44));
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        head.addView(back, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView help = U.text(this, "Cosa posso dire", 14, U.INK, false);
        help.setPaintFlags(help.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        help.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(VoiceActivity.this, VoiceHelpActivity.class)); }
        });
        head.addView(help);
        root.addView(head, U.full());
        ScrollView sv = new ScrollView(this);
        card = U.col(this);
        sv.addView(card);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout again = U.row(this);
        again.setGravity(Gravity.CENTER);
        MicView mic = new MicView(this);
        mic.setContentDescription("Ripeti il comando");
        mic.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { listen(); } });
        again.addView(mic, new LinearLayout.LayoutParams(U.dp(this, 52), U.dp(this, 52)));
        TextView al = U.text(this, "Ripeti", 14, U.MUTED, false);
        al.setPadding(U.dp(this, 10), 0, 0, 0);
        again.addView(al);
        root.addView(again, U.full());
        setContentView(root);

        String said = getIntent().getStringExtra("said");
        if (said == null) listen(); else handle(said);
    }

    /** Nomi delle persone scelte (tutte quelle in Conversazioni). */
    static List<String> names(Context c) {
        List<String> out = new ArrayList<String>();
        for (Rules.Person p : Rules.people(U.cfg(c, U.K_CONTACTS, U.DEF_CONTACTS))) out.add(p.name);
        return out;
    }

    static Rules.Person person(Context c, String name) {
        if (name == null) return null;
        for (Rules.Person p : Rules.people(U.cfg(c, U.K_CONTACTS, U.DEF_CONTACTS))) if (p.name.equalsIgnoreCase(name.trim())) return p;
        return null;
    }

    private void listen() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Di' un comando");
        try { startActivityForResult(i, REQ_VOICE); } catch (Exception e) { U.toast(this, "Riconoscimento vocale non disponibile"); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_VOICE && res == RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) handle(r.get(0));
        } else if (req == REQ_VOICE && cmd == null) finish();
    }

    // ---------------------------------------------------------------------

    private void handle(String said) {
        cmd = VoiceParser.parse(said, names(this), Calendar.getInstance());
        card.removeAllViews();
        card.addView(U.label(this, "Hai detto"));
        TextView q = U.text(this, "“" + cmd.raw + "”", 18, U.INK, false);
        q.setTypeface(Typeface.create(U.SERIF, Typeface.ITALIC));
        card.addView(q);
        String t = cmd.type;
        if (VoiceParser.HELP.equals(t)) { startActivity(new Intent(this, VoiceHelpActivity.class)); finish(); return; }
        if (VoiceParser.WRITE.equals(t)) write();
        else if (VoiceParser.CALL.equals(t)) call();
        else if (VoiceParser.OPEN.equals(t)) openApp();
        else if (VoiceParser.NAV.equals(t)) nav();
        else if (VoiceParser.MAPS.equals(t)) maps();
        else if (VoiceParser.TASK_ADD.equals(t)) taskAdd();
        else if (VoiceParser.TASK_DONE.equals(t)) taskDone();
        else if (VoiceParser.EVENT_ADD.equals(t)) eventAdd();
        else if (VoiceParser.EVENT_MOVE.equals(t)) eventMove();
        else if (VoiceParser.EVENT_DELETE.equals(t)) eventDelete();
        else ask();
    }

    // ---------- Scheda ----------

    private void head(String tag, String action) {
        LinearLayout r = U.row(this);
        r.setPadding(0, U.dp(this, 20), 0, U.dp(this, 6));
        TextView tg = U.text(this, tag, 11, U.INK, true);
        tg.setTypeface(U.SERIF_M);
        tg.setPadding(U.dp(this, 8), U.dp(this, 3), U.dp(this, 8), U.dp(this, 3));
        tg.setBackground(U.box(this, U.BG, U.INK, 6));
        r.addView(tg);
        TextView a = U.text(this, action, 20, U.INK, true);
        a.setPadding(U.dp(this, 10), 0, 0, 0);
        r.addView(a);
        card.addView(r, U.full());
    }

    private void line(String k, String v) {
        LinearLayout r = U.row(this);
        r.setMinimumHeight(U.dp(this, 40));
        TextView kk = U.text(this, k, 14, U.MUTED, false);
        kk.setMinWidth(U.dp(this, 90));
        r.addView(kk);
        r.addView(U.text(this, v, 16, U.INK, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(r, U.full());
        card.addView(U.divider(this));
    }

    private void hint(String s) {
        TextView h = U.text(this, s, 13, U.MUTED, false);
        h.setPadding(0, U.dp(this, 10), 0, 0);
        card.addView(h);
    }

    private void cta(String label, final Runnable r) {
        TextView b = U.text(this, label, 16, U.BG, true);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(U.dp(this, 52));
        b.setBackground(U.box(this, U.INK, 0, 26));
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        LinearLayout.LayoutParams lp = U.full();
        lp.topMargin = U.dp(this, 20);
        card.addView(b, lp);
        TextView c = U.text(this, "Annulla", 15, U.INK, false);
        c.setGravity(Gravity.CENTER);
        c.setMinHeight(U.dp(this, 48));
        c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        card.addView(c, U.full());
    }

    private void notFound(String what, String action, String cta, final Class<?> target) {
        head("?", action);
        hint(what);
        if (cta != null) cta(cta, new Runnable() {
            public void run() { startActivity(new Intent(VoiceActivity.this, target)); finish(); }
        });
    }

    // ---------- Comandi ----------

    private void write() {
        final Rules.Person p = person(this, cmd.person);
        if (p == null) {
            notFound("Non trovo “" + cmd.person + "” tra le persone che hai scelto. I messaggi a voce vanno solo a loro.",
                    "Scrivi un messaggio", "Scegli le persone", PeopleActivity.class);
            return;
        }
        final String app = cmd.app != null ? cmd.app : (p.apps.isEmpty() ? "WA" : p.apps.get(0));
        final String text = cmd.text == null || cmd.text.length() == 0 ? null : cmd.text;
        head("MSG", "Messaggio su " + Rules.appName(app));
        line("A", p.name);
        line("Testo", text == null ? "(da scrivere)" : text);
        hint(app.equals("TG") ? "Si apre Telegram con il testo pronto: scegli tu la chat e tocchi Invia."
                : "Si apre " + Rules.appName(app) + " con il messaggio pronto: tocchi tu Invia.");
        cta(text == null ? "Apri la chat" : "Apri e invia", new Runnable() {
            public void run() {
                if (ListActivity.contact(VoiceActivity.this, app, p.target, text)) {
                    Convs.open(VoiceActivity.this, app, p.name, false);
                    finish();
                }
            }
        });
    }

    private void call() {
        final Rules.Person p = person(this, cmd.person);
        if (p == null || p.target.replaceAll("[^0-9]", "").length() < 6) {
            notFound(p == null ? "Non trovo “" + cmd.person + "” tra le persone che hai scelto."
                            : "Per " + p.name + " non c'è un numero di telefono.", "Chiamata", "Scegli le persone", PeopleActivity.class);
            return;
        }
        head("TEL", "Chiamata");
        line("Chiama", p.name);
        line("Numero", p.target);
        hint("Si apre il telefono con il numero: tocchi tu per chiamare.");
        cta("Chiama", new Runnable() {
            public void run() { if (ListActivity.contact(VoiceActivity.this, "TEL", p.target, null)) finish(); }
        });
    }

    private void openApp() {
        final U.AppInfo a = U.findApp(this, cmd.target);
        if (a == null) { notFound("Non trovo l'app “" + cmd.target + "” sul telefono.", "Apri app", null, null); return; }
        boolean paused = U.isPaused(this, a.pkg);
        head("APP", "Apri app");
        line("App", a.label);
        line("Pausa", paused ? U.pauseSeconds(this) + " secondi prima di aprire" : "nessuna");
        if (paused) hint("La pausa vale anche per i comandi vocali.");
        cta("Apri", new Runnable() { public void run() { U.open(VoiceActivity.this, a); finish(); } });
    }

    private String enc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20"); } catch (Exception e) { return s; }
    }

    private void nav() {
        head("MAPS", "Navigazione");
        line("Verso", cmd.target);
        line("Con", "Google Maps");
        hint("Si apre Maps con il percorso.");
        cta("Avvia", new Runnable() {
            public void run() {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + enc(cmd.target)));
                i.setPackage("com.google.android.apps.maps");
                if (!U.start(VoiceActivity.this, i)) U.start(VoiceActivity.this, new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + enc(cmd.target))));
                finish();
            }
        });
    }

    private void maps() {
        head("MAPS", "Cerca su Maps");
        line("Cerca", cmd.target);
        cta("Cerca su Maps", new Runnable() {
            public void run() {
                if (!U.start(VoiceActivity.this, new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + enc(cmd.target)))))
                    U.toast(VoiceActivity.this, "Google Maps non è installata");
                finish();
            }
        });
    }

    private String dayText(int off) {
        if (off < 0) return "Nessuna";
        if (off == 0) return "Oggi";
        if (off == 1) return "Domani";
        return new java.text.SimpleDateFormat("EEE d MMMM", Locale.ITALY).format(new java.util.Date(U.startOfDay(off)));
    }

    private void taskAdd() {
        head("TASK", "Nuovo task");
        line("Task", cmd.text);
        line("Scadenza", dayText(cmd.day));
        hint("Comparirà in Da fare.");
        cta("Aggiungi", new Runnable() {
            public void run() {
                Tasks.add(VoiceActivity.this, cmd.text, cmd.day < 0 ? 0 : U.startOfDay(cmd.day));
                U.toast(VoiceActivity.this, "Task aggiunto");
                finish();
            }
        });
    }

    private void taskDone() {
        Tasks.Task best = null;
        int bestScore = 0;
        for (Tasks.Task k : Tasks.all(this)) {
            if (k.done) continue;
            int s = VoiceParser.score(cmd.target, k.title);
            if (s > bestScore) { bestScore = s; best = k; }
        }
        if (best == null || bestScore < 50) { notFound("Non trovo un task come “" + cmd.target + "” tra quelli da fare.", "Segna fatto", null, null); return; }
        final Tasks.Task k = best;
        head("TASK", "Segna fatto");
        line("Task", k.title);
        String due = Tasks.dueLabel(k);
        if (due.length() > 0) line("Scadenza", due);
        cta("Segna fatto", new Runnable() {
            public void run() { Tasks.setDone(VoiceActivity.this, k.id, true); U.toast(VoiceActivity.this, "Fatto"); finish(); }
        });
    }

    private void eventAdd() {
        head("CAL", "Nuovo impegno");
        line("Impegno", cmd.text);
        int day = cmd.day < 0 ? 0 : cmd.day;
        line("Quando", dayText(day) + (cmd.minutes >= 0 ? " · " + String.format(Locale.ITALY, "%02d:%02d", cmd.minutes / 60, cmd.minutes % 60) : ""));
        hint("Controlla calendario e orario prima di salvare.");
        final int d = day;
        cta("Continua", new Runnable() {
            public void run() {
                Intent i = new Intent(VoiceActivity.this, AddActivity.class);
                i.putExtra("title", cmd.text);
                i.putExtra("day", U.startOfDay(d));
                if (cmd.minutes >= 0) i.putExtra("from", cmd.minutes);
                startActivity(i);
                finish();
            }
        });
    }

    /** Il primo impegno (da oggi in poi) col titolo più simile; nel giorno detto, se detto. */
    private U.Event findEvent(String what, int day) {
        U.Event best = null;
        int bestScore = 0;
        long from = day >= 0 ? U.startOfDay(day) : U.startOfDay(0), to = day >= 0 ? U.startOfDay(day + 1) : U.startOfDay(31);
        for (U.Event e : U.events(this, from, to)) {
            int s = VoiceParser.score(what, e.title);
            if (s > bestScore) { bestScore = s; best = e; }
        }
        if (bestScore >= 60) return best;
        if (day >= 0) return findEvent(what, -1);
        return null;
    }

    private String when(U.Event e) {
        return new java.text.SimpleDateFormat("EEE d", Locale.ITALY).format(new java.util.Date(e.begin))
                + (e.allDay ? " · tutto il giorno" : " · " + U.hhmm(e.begin));
    }

    private void eventMove() {
        if (!U.hasCalendar(this)) { notFound("Serve il permesso di leggere il calendario.", "Sposta impegno", null, null); return; }
        final U.Event e = findEvent(cmd.target, cmd.fromDay);
        if (e == null) { notFound("Non trovo “" + cmd.target + "” nei prossimi 30 giorni.", "Sposta impegno", null, null); return; }
        if (cmd.day < 0 && cmd.minutes < 0) { notFound("Ho capito l'impegno, ma non quando spostarlo. Prova: “Sposta " + e.title + " a domani alle 17”.", "Sposta impegno", null, null); return; }
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(e.begin);
        long dayStart = cmd.day >= 0 ? U.startOfDay(cmd.day) : AddActivity.dayStart(e.begin);
        final boolean allDay = e.allDay && cmd.minutes < 0;
        final long start = allDay ? dayStart : dayStart + (cmd.minutes >= 0 ? cmd.minutes : k.get(Calendar.HOUR_OF_DAY) * 60 + k.get(Calendar.MINUTE)) * 60000L;
        final long end = allDay ? start + Math.max(86400000L, e.end - e.begin) : start + (e.allDay ? 3600000L : Math.max(5 * 60000L, e.end - e.begin));
        head("CAL", "Modifica impegno");
        line("Impegno", e.title);
        line("Prima", when(e));
        line("Dopo", new java.text.SimpleDateFormat("EEE d", Locale.ITALY).format(new java.util.Date(start)) + (allDay ? " · tutto il giorno" : " · " + U.hhmm(start)));
        hint((e.calName != null ? "Calendario " + e.calName + ". " : "") + (e.repeats() ? "Si ripete: sposto solo questa volta." : ""));
        if (!e.editable()) { hint("Questo calendario è di sola lettura."); return; }
        cta("Conferma", new Runnable() {
            public void run() {
                if (!U.canWriteCalendar(VoiceActivity.this)) { askWrite(); return; }
                U.EventData d = U.loadEvent(VoiceActivity.this, e.eventId);
                if (d == null) { U.toast(VoiceActivity.this, "Impegno non trovato"); return; }
                boolean ok = e.repeats()
                        ? U.exception(VoiceActivity.this, e.eventId, e.instBegin, false, d.title, start, end, allDay, d.desc)
                        : U.updateEvent(VoiceActivity.this, e.eventId, d.title, start, end, allDay, null, d.desc);
                U.toast(VoiceActivity.this, ok ? "Impegno spostato" : "Non sono riuscita a spostarlo");
                if (ok) finish();
            }
        });
    }

    private void eventDelete() {
        if (!U.hasCalendar(this)) { notFound("Serve il permesso di leggere il calendario.", "Elimina impegno", null, null); return; }
        final U.Event e = findEvent(cmd.target, cmd.fromDay);
        if (e == null) { notFound("Non trovo “" + cmd.target + "” nei prossimi 30 giorni.", "Elimina impegno", null, null); return; }
        head("CAL", "Elimina impegno");
        line("Impegno", e.title);
        line("Quando", when(e));
        hint((e.repeats() ? "Si ripete: elimino solo questa volta. " : "")
                + "Sparisce anche dal calendario" + (e.calName != null ? " " + e.calName : "") + ", anche per chi lo condivide.");
        if (!e.editable()) { hint("Questo calendario è di sola lettura."); return; }
        cta("Elimina", new Runnable() {
            public void run() {
                if (!U.canWriteCalendar(VoiceActivity.this)) { askWrite(); return; }
                boolean ok = e.repeats()
                        ? U.exception(VoiceActivity.this, e.eventId, e.instBegin, true, null, 0, 0, false, null)
                        : U.deleteEvent(VoiceActivity.this, e.eventId);
                U.toast(VoiceActivity.this, ok ? "Impegno eliminato" : "Non sono riuscita a eliminarlo");
                if (ok) finish();
            }
        });
    }

    private void askWrite() {
        requestPermissions(new String[]{android.Manifest.permission.WRITE_CALENDAR, android.Manifest.permission.READ_CALENDAR}, 51);
    }

    private void ask() {
        final String q = cmd.text == null ? cmd.raw : cmd.text;
        head("?", "Non è un comando di −minus");
        line("Puoi", "Chiederlo a Gemini");
        hint("Si apre fuori da −minus.");
        cta("Chiedi a Gemini", new Runnable() { public void run() { gemini(VoiceActivity.this, q); finish(); } });
    }

    /** Gira la domanda a Gemini; se non c'è, all'assistente predefinito o alla ricerca web. */
    static void gemini(Context c, String q) {
        if (U.installed(c, GEMINI_PKG)) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, q);
            i.setPackage(GEMINI_PKG);
            if (U.start(c, i)) return;
            copy(c, q);
            if (U.launchPkg(c, GEMINI_PKG)) { U.toast(c, "Domanda copiata: incollala in Gemini"); return; }
        }
        Intent assist = new Intent(Intent.ACTION_ASSIST);
        assist.putExtra(android.app.SearchManager.QUERY, q);
        copy(c, q);
        if (U.start(c, assist)) return;
        Intent s = new Intent(Intent.ACTION_WEB_SEARCH);
        s.putExtra(android.app.SearchManager.QUERY, q);
        if (!U.start(c, s)) U.toast(c, "Nessun assistente disponibile");
    }

    static void copy(Context c, String s) {
        try {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Domanda", s));
        } catch (Exception ignored) {}
    }
}
