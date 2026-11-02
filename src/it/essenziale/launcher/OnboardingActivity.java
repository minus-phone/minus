package it.essenziale.launcher;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Onboarding al primo avvio (0.9, wireframe 120–132). Una prima schermata chiede cosa ti interessa
 * e il percorso contiene solo i passi che servono: sempre home, persone, notifiche, silenzio e a
 * portata; giornata e pausa e tempo solo se scelti. Ogni passo arriva già compilato e si può
 * saltare; i saltati tornano in home come «Continua la configurazione».
 */
public class OnboardingActivity extends BaseActivity {

    static final int WELCOME = 0, HOME = 2, PEOPLE = 3, NOTIF = 4, SILENCE = 5, DAY = 6, PORTATA = 7, PAUSE = 8, READY = 9, ASPETTO = 10, NIGHT = 11;
    /** Cosa ti interessa (0.9): ognuno aggiunge passi. «Raggiungibile solo da chi conta» è data per scontata. */
    static final String[] INTERESTS = {"Prendere il telefono meno spesso", "Stare meno dentro le app", "Avere la giornata sotto controllo", "Usarlo meno di notte"};
    static final String[] INTEREST_SUBS = {"Pausa prima di aprire, tempo dall'ultimo sblocco", "Pausa e scopo, con il tempo sopra le app", "Calendari e sveglia in home", "Di notte: solo l'essenziale e la tua alternativa"};

    private int[] path = {WELCOME};
    private int idx = 0;
    private boolean cont = false, pillStage = false;
    private LinearLayout root, body, foot;

    // stato dei passi
    private boolean askedNotif = false, restricted = false, askedContacts = false, askedCal = false;
    private final Map<String, String[]> chosen = new LinkedHashMap<String, String[]>();    // nome minuscolo -> {nome, numero}
    private final Map<String, String[]> candidates = new LinkedHashMap<String, String[]>();
    private boolean peopleLoaded = false;
    private String search = "";
    private LinearLayout searchResults;
    private List<String[]> portataCand;   // {pacchetto, nome, tipo}
    private List<String[]> portataSel;    // {pacchetto, nome}

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        running = true;
        cont = getIntent().getBooleanExtra("continue", false);
        if (cont) {
            List<Integer> steps = new ArrayList<Integer>();
            List<String> miss = Onboarding.missing(this);
            if (miss.contains("Silenzio")) steps.add(SILENCE);
            if (miss.contains("A portata")) steps.add(PORTATA);
            if (miss.contains("Pausa e tempo")) steps.add(PAUSE);
            steps.add(READY);
            path = new int[steps.size()];
            for (int i = 0; i < path.length; i++) path[i] = steps.get(i);
        }
        root = U.col(this);
        root.setBackgroundColor(U.BG);
        root.setPadding(U.dp(this, 20), U.dp(this, 16), U.dp(this, 20), U.dp(this, 12));
        ScrollView sv = new ScrollView(this);
        body = U.col(this);
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        foot = U.col(this);
        root.addView(foot, U.full());
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        watching = null; // 0.13.5: sei tornata, non serve più riportarti qui
        render(); // al ritorno dalle impostazioni di Android il passo mostra lo stato aggiornato
    }

    /** 0.13.5: aperta e non finita. La home la riporta davanti invece di ricominciare da capo. */
    static boolean running;

    static void bringBack(android.app.Activity from) {
        Intent i = new Intent(from, OnboardingActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        from.startActivity(i);
    }

    @Override
    public void finish() {
        running = false; // prima che la home riprenda, sennò la riporterebbe davanti
        super.finish();
    }

    @Override
    protected void onDestroy() {
        running = false;
        watcher.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        render(); // 0.13.5: dalla finestra «app Home» si torna qui
    }

    // ---------- 0.13.5: ritorno automatico dalle impostazioni di Android ----------

    interface Check { boolean ok(); }

    private Check watching;
    private final android.os.Handler watcher = new android.os.Handler();

    /**
     * Apre una pagina di Android e, appena il permesso risulta concesso, riporta alla configurazione:
     * niente più «torna con Indietro». Se Android non lo permette, resta il ritorno con Indietro.
     */
    private void openAndWatch(Runnable open, final Check c) {
        watching = c;
        final long until = System.currentTimeMillis() + 3 * 60 * 1000L;
        open.run();
        watcher.removeCallbacksAndMessages(null);
        watcher.postDelayed(new Runnable() {
            public void run() {
                if (watching != c || System.currentTimeMillis() > until) return;
                boolean ok = false;
                try { ok = c.ok(); } catch (Exception ignored) {}
                if (ok) {
                    watching = null;
                    Intent i = new Intent(OnboardingActivity.this, OnboardingActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    try { startActivity(i); } catch (Exception ignored) {}
                } else watcher.postDelayed(this, 600);
            }
        }, 800);
    }

    /** Su Android 13+ le app installate fuori dal Play Store hanno l'interruttore delle notifiche grigio. */
    private boolean maybeRestricted() {
        if (Build.VERSION.SDK_INT < 33) return false;
        try {
            String inst = getPackageManager().getInstallerPackageName(getPackageName());
            return !"com.android.vending".equals(inst);
        } catch (Exception e) { return true; }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        peopleLoaded = false;
        render();
    }

    @Override
    public void onBackPressed() { back(); }

    private int step() { return path[idx]; }

    private void next() {
        pillStage = false;
        if (idx < path.length - 1) { idx++; render(); }
    }

    private void back() {
        if (pillStage) { pillStage = false; render(); return; }
        if (idx == 0) { finish(); return; }
        idx--;
        render();
    }

    // ---------- Percorso (0.9) ----------

    private boolean interested(int k) {
        return ("," + U.prefs(this).getString(Onboarding.K_INTERESTS, "") + ",").contains("," + k + ",");
    }

    private void setInterest(int k, boolean on) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < INTERESTS.length; i++) {
            boolean has = i == k ? on : interested(i);
            if (has) { if (b.length() > 0) b.append(","); b.append(i); }
        }
        U.prefs(this).edit().putString(Onboarding.K_INTERESTS, b.toString()).apply();
    }

    /** Passi dopo la prima schermata: la base più quelli scelti. */
    private int[] buildPath() {
        List<Integer> l = new ArrayList<Integer>();
        l.add(WELCOME); l.add(HOME); l.add(ASPETTO); l.add(PEOPLE); l.add(NOTIF); l.add(SILENCE);
        if (interested(2)) l.add(DAY);
        l.add(PORTATA);
        if (interested(0) || interested(1)) l.add(PAUSE);
        if (interested(3)) l.add(NIGHT);
        l.add(READY);
        int[] out = new int[l.size()];
        for (int i = 0; i < out.length; i++) out[i] = l.get(i);
        return out;
    }

    /** Quali passi rimandabili sono nel percorso: tornano in «Continua la configurazione» se saltati. */
    private String included() {
        StringBuilder b = new StringBuilder();
        for (int s : path) {
            String k = s == SILENCE ? "silenzio" : s == PORTATA ? "portata" : s == PAUSE ? "pausa" : null;
            if (k != null) { if (b.length() > 0) b.append(","); b.append(k); }
        }
        return b.toString();
    }

    private void done(String deferred) {
        if (deferred != null) Onboarding.seen(this, deferred);
        next();
    }

    // ---------------------------------------------------------------------

    private void render() {
        body.removeAllViews();
        foot.removeAllViews();
        int s = step();
        if (s != WELCOME && s != READY) header();
        switch (s) {
            case WELCOME: welcome(); break;
            case HOME: home(); break;
            case PEOPLE: people(); break;
            case NOTIF: notif(); break;
            case SILENCE: silence(); break;
            case DAY: day(); break;
            case PORTATA: portata(); break;
            case ASPETTO: aspetto(); break;
            case NIGHT: night(); break;
            case PAUSE: if (pillStage) pillPermissions(); else pause(); break;
            default: ready();
        }
    }

    private void header() {
        // la prima schermata e Pronto non contano: «1 di 7» è il primo passo vero
        int first = path[0] == WELCOME ? 1 : 0, total = path.length - first - 1, pos = idx - first + 1;
        LinearLayout h = U.row(this);
        h.setMinimumHeight(U.dp(this, 40));
        h.addView(U.text(this, pos + " di " + total, 13, U.MUTED, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView skip = U.text(this, "Salta", 13, U.MUTED, false);
        skip.setPaintFlags(skip.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        skip.setGravity(Gravity.CENTER);
        skip.setMinHeight(U.dp(this, 44));
        skip.setPadding(U.dp(this, 12), 0, 0, 0);
        // 0.13.6: «Salta» tiene quello che il passo ha già compilato (i preferiti), come promette il benvenuto
        skip.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (step() == PEOPLE && peopleLoaded) savePeople(); next(); } });
        h.addView(skip);
        body.addView(h, U.full());
        LinearLayout bar = U.row(this);
        for (int i = 0; i < total; i++) {
            View seg = new View(this);
            seg.setBackground(U.box(this, i < pos ? U.INK : U.TRACK_OFF, 0, 2));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, U.dp(this, 3), 1f);
            if (i < total - 1) lp.rightMargin = U.dp(this, 3);
            bar.addView(seg, lp);
        }
        LinearLayout.LayoutParams blp = U.full();
        blp.bottomMargin = U.dp(this, 18);
        body.addView(bar, blp);
    }

    private void title(String s) {
        TextView t = U.title(this, s, 28, U.INK);
        t.setPadding(0, U.dp(this, 4), 0, U.dp(this, 8));
        body.addView(t);
    }

    private void sub(String s) {
        TextView t = U.text(this, s, 15, U.MUTED, false);
        t.setPadding(0, 0, 0, U.dp(this, 14));
        body.addView(t);
    }

    private void caption(String s) {
        body.addView(U.label(this, s));
    }

    private void note(String s) {
        TextView t = U.text(this, s, 12, U.MUTED, false);
        t.setPadding(0, U.dp(this, 6), 0, U.dp(this, 10));
        foot.addView(t);
    }

    private void primary(String s, final Runnable r) {
        TextView t = U.text(this, s, 16, U.BG, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(U.dp(this, 52));
        t.setBackground(U.box(this, U.INK, 0, 26));
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        foot.addView(t, U.full());
        if (step() != WELCOME && step() != READY) {
            TextView b = U.text(this, "‹ Indietro", 14, U.MUTED, false);
            b.setGravity(Gravity.CENTER);
            b.setMinHeight(U.dp(this, 44));
            b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { back(); } });
            foot.addView(b, U.full());
        }
    }

    /** Scheda selezionabile: radio (tondo) o checkbox (quadrato). */
    private void choice(String title, String subtitle, boolean on, boolean radio, final Runnable r) {
        LinearLayout c = U.row(this);
        c.setMinimumHeight(U.dp(this, 56));
        c.setPadding(U.dp(this, 15), U.dp(this, 8), U.dp(this, 15), U.dp(this, 8));
        c.setBackground(U.box(this, U.SURFACE, on ? U.INK : U.BORDER, 14));
        LinearLayout tx = U.col(this);
        tx.addView(U.text(this, title, 15, U.INK, false));
        if (subtitle != null) tx.addView(U.text(this, subtitle, 12, U.MUTED, false));
        c.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView mark = U.text(this, on ? "✓" : "", 12, U.BG, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(U.box(this, on ? U.INK : U.SURFACE, U.INK, radio ? 10 : 4));
        c.addView(mark, new LinearLayout.LayoutParams(U.dp(this, 20), U.dp(this, 20)));
        c.setContentDescription(title + (on ? ", scelto" : ""));
        c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        LinearLayout.LayoutParams lp = U.full();
        lp.bottomMargin = U.dp(this, 10);
        body.addView(c, lp);
    }

    /** Riquadro con righe ✓ o ·. */
    private void box(String[] lines, String mark) {
        LinearLayout c = U.col(this);
        c.setPadding(U.dp(this, 16), U.dp(this, 12), U.dp(this, 16), U.dp(this, 12));
        c.setBackground(U.box(this, U.SURFACE, U.LINE, 14));
        for (String l : lines) {
            LinearLayout r = U.row(this);
            r.setPadding(0, U.dp(this, 4), 0, U.dp(this, 4));
            TextView m = U.text(this, mark, 14, U.MUTED, false);
            m.setMinWidth(U.dp(this, 20));
            r.addView(m);
            r.addView(U.text(this, l, 14, U.INK, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            c.addView(r, U.full());
        }
        LinearLayout.LayoutParams lp = U.full();
        lp.bottomMargin = U.dp(this, 12);
        body.addView(c, lp);
    }

    // ---------- 1. Cosa ti interessa (0.9: benvenuto e perché in una schermata) ----------

    private LogoView logo; // 0.13: una sola istanza, così l'animazione non riparte a ogni scelta

    private void welcome() {
        View top = new View(this);
        body.addView(top, new LinearLayout.LayoutParams(1, U.dp(this, 12)));
        if (logo == null) logo = new LogoView(this, U.INK, U.BG);
        if (logo.getParent() != null) ((ViewGroup) logo.getParent()).removeView(logo);
        LinearLayout brand = U.row(this);
        brand.addView(logo, new LinearLayout.LayoutParams(U.dp(this, 64), U.dp(this, 64)));
        TextView name = U.title(this, U.NAME, 40, U.INK);
        name.setTypeface(U.SERIF_I);
        name.setPadding(U.dp(this, 16), 0, 0, 0);
        brand.addView(name);
        LinearLayout.LayoutParams blp = U.full();
        blp.bottomMargin = U.dp(this, 20);
        body.addView(brand, blp);
        title("Cosa ti interessa?");
        sub("−minus diventa la tua home e fa passare solo chi conta. Scegli cosa vuoi in più: ti mostriamo solo i passi che servono.");
        for (int i = 0; i < INTERESTS.length; i++) {
            final int k = i;
            final boolean on = interested(i);
            choice(INTERESTS[i], INTEREST_SUBS[i], on, false, new Runnable() {
                public void run() { setInterest(k, !on); render(); }
            });
        }
        body.addView(U.text(this, "Sempre: home e aspetto, persone, notifiche, silenzio, a portata.", 14, U.MUTED, false));
        TextView all = U.text(this, "Mostrami tutti i passi", 14, U.INK, false);
        all.setPaintFlags(all.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        all.setMinHeight(U.dp(this, 44));
        all.setGravity(Gravity.CENTER_VERTICAL);
        all.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { for (int i = 0; i < INTERESTS.length; i++) setInterest(i, true); render(); }
        });
        body.addView(all);
        path = buildPath();
        final int steps = path.length - 2;
        note("Nessun giudizio: le risposte restano sul telefono. Ogni passo arriva già compilato e si può saltare; dopo ogni scelta c'è «Annulla».");
        primary(steps <= 6 ? "Solo l'essenziale · " + steps + " passi" : "Inizia · " + steps + " passi", new Runnable() {
            public void run() {
                // la pillola arriva accesa solo se hai scelto «stare meno dentro le app»
                if (interested(1)) U.prefs(OnboardingActivity.this).edit().putBoolean(Pill.K_ON, true).apply();
                // 0.10: «stare meno dentro le app» propone pausa e scopo sui social; «meno spesso» la pausa
                if ((interested(0) || interested(1)) && !U.prefs(OnboardingActivity.this).contains(Scope.K_LEVEL))
                    for (U.AppInfo a : U.sortedApps(OnboardingActivity.this))
                        if (U.isSocial(a.pkg)) Scope.setLevel(OnboardingActivity.this, a.pkg, interested(1) ? Scopo.SCOPE : Scopo.PAUSE);
                path = buildPath();
                idx = 1;
                render();
            }
        });
    }

    // ---------- 3. Home ----------

    private void home() {
        title("−minus come home");
        sub("In home trovi solo la giornata. Persone, app e impostazioni sono nella ricerca in basso.");
        boolean ok = Onboarding.isDefaultHome(this);
        box(new String[]{"Le app non vengono cancellate", "Torni indietro quando vuoi, da Impostazioni › App predefinite", "Nessun dato esce dal telefono"}, "✓");
        if (ok) {
            body.addView(U.text(this, "✓ −minus è la tua home.", 15, U.INK, true));
            primary("Avanti", new Runnable() { public void run() { next(); } });
        } else {
            note("Dopo il tocco Android chiede quale app Home usare: scegli −minus. Poi torni qui.");
            primary("Imposta come home", new Runnable() { public void run() {
                openAndWatch(new Runnable() { public void run() { Onboarding.openHomeSettings(OnboardingActivity.this); } },
                        new Check() { public boolean ok() { return Onboarding.isDefaultHome(OnboardingActivity.this); } });
            } });
        }
    }

    // ---------- 4. Persone ----------

    private void people() {
        title("Chi deve poterti raggiungere sempre?");
        sub("Queste persone passano anche con il silenzio. Abbiamo già messo i tuoi preferiti.");
        if (!Onboarding.hasContacts(this) && !askedContacts) {
            askedContacts = true;
            requestPermissions(new String[]{android.Manifest.permission.READ_CONTACTS}, 3);
        }
        if (!peopleLoaded) loadPeople();

        final EditText q = new EditText(this);
        q.setHint("Cerca in rubrica");
        q.setHintTextColor(U.MUTED);
        q.setTextColor(U.INK);
        q.setTextSize(15);
        q.setSingleLine(true);
        q.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        q.setBackground(U.box(this, U.SURFACE, U.BORDER, 22));
        q.setPadding(U.dp(this, 16), 0, U.dp(this, 16), 0);
        q.setText(search);
        body.addView(q, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, U.dp(this, 46)));
        searchResults = U.col(this);
        body.addView(searchResults, U.full());
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { search = s.toString(); renderSearch(); }
        });
        renderSearch();

        LinearLayout list = U.col(this);
        list.setPadding(0, U.dp(this, 10), 0, 0);
        body.addView(list, U.full());
        if (candidates.isEmpty())
            list.addView(U.text(this, Onboarding.hasContacts(this) ? "Nessun preferito in rubrica: cerca qui sopra le persone da aggiungere."
                    : "Senza il permesso Contatti le persone si aggiungono dopo, da Impostazioni › Notifiche.", 14, U.MUTED, false));
        for (final String[] p : candidates.values()) personRow(list, p);

        note("Il permesso Contatti si chiede all'apertura di questo passo. Senza, si aggiungono a mano.");
        primary("Avanti · " + chosen.size() + (chosen.size() == 1 ? " persona" : " persone"), new Runnable() {
            public void run() { savePeople(); next(); }
        });
    }

    private void personRow(LinearLayout list, final String[] p) {
        final String k = p[0].toLowerCase(Locale.ITALY);
        final boolean on = chosen.containsKey(k);
        LinearLayout r = U.row(this);
        r.setMinimumHeight(U.dp(this, 56));
        TextView ini = U.text(this, p[0].trim().length() > 0 ? p[0].trim().substring(0, 1).toUpperCase(Locale.ITALY) : "?", 13, U.INK, true);
        ini.setGravity(Gravity.CENTER);
        ini.setBackground(U.box(this, U.CHIP, 0, 17));
        r.addView(ini, new LinearLayout.LayoutParams(U.dp(this, 34), U.dp(this, 34)));
        LinearLayout tx = U.col(this);
        tx.setPadding(U.dp(this, 12), 0, 0, 0);
        tx.addView(U.text(this, p[0], 15, U.INK, false));
        StringBuilder ap = new StringBuilder();
        for (String a : U.chatAppsOf(this, p[0], null)) { if (ap.length() > 0) ap.append(" · "); ap.append(Rules.appName(a)); }
        if (p[1] != null && p[1].length() > 0) ap.append(" · Chiamate");
        tx.addView(U.text(this, ap.toString(), 12, U.MUTED, false));
        r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(U.checkbox(this, on, U.INK), new LinearLayout.LayoutParams(U.dp(this, 22), U.dp(this, 22)));
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (on) chosen.remove(k); else chosen.put(k, p);
                render();
            }
        });
        list.addView(r, U.full());
        list.addView(U.divider(this));
    }

    private void renderSearch() {
        if (searchResults == null) return;
        searchResults.removeAllViews();
        String s = search.trim();
        if (s.length() < 2 || !Onboarding.hasContacts(this)) return;
        Cursor cur = null;
        try {
            cur = getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
                    new String[]{ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME},
                    ContactsContract.Contacts.DISPLAY_NAME + " LIKE ?", new String[]{"%" + s + "%"},
                    ContactsContract.Contacts.DISPLAY_NAME + " ASC");
            int n = 0;
            while (cur != null && cur.moveToNext() && n++ < 6) {
                final String name = cur.getString(1);
                if (name == null) continue;
                final long id = cur.getLong(0);
                TextView t = U.text(this, "+  " + name, 15, U.INK, false);
                t.setMinHeight(U.dp(this, 44));
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        String[] p = {name, numberOf(id)};
                        candidates.put(name.toLowerCase(Locale.ITALY), p);
                        chosen.put(name.toLowerCase(Locale.ITALY), p);
                        search = "";
                        render();
                    }
                });
                searchResults.addView(t, U.full());
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
    }

    /** Le persone «Sempre» già scelte e i preferiti della rubrica, già selezionati. */
    private void loadPeople() {
        peopleLoaded = true;
        for (Rules.Person p : Rules.people(U.cfg(this, U.K_CONTACTS, U.DEF_CONTACTS))) {
            if (!p.section.equalsIgnoreCase("Sempre") || p.target.contains("...")) continue; // gli esempi non contano
            String[] e = {p.name, p.target};
            candidates.put(p.name.toLowerCase(Locale.ITALY), e);
            chosen.put(p.name.toLowerCase(Locale.ITALY), e);
        }
        if (!Onboarding.hasContacts(this)) return;
        Cursor cur = null;
        try {
            cur = getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
                    new String[]{ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME},
                    ContactsContract.Contacts.STARRED + " = 1", null, ContactsContract.Contacts.DISPLAY_NAME + " ASC");
            while (cur != null && cur.moveToNext()) {
                String name = cur.getString(1);
                if (name == null) continue;
                String k = name.toLowerCase(Locale.ITALY);
                if (candidates.containsKey(k)) continue;
                String[] e = {name, numberOf(cur.getLong(0))};
                candidates.put(k, e);
                chosen.put(k, e);
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
    }

    private String numberOf(long contactId) {
        Cursor cur = null;
        try {
            cur = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE},
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[]{String.valueOf(contactId)}, null);
            String any = "";
            while (cur != null && cur.moveToNext()) {
                if (cur.getInt(1) == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) return cur.getString(0);
                if (any.length() == 0) any = cur.getString(0);
            }
            return any;
        } catch (Exception e) {
            return "";
        } finally {
            if (cur != null) cur.close();
        }
    }

    private void savePeople() {
        List<Rules.Person> out = new ArrayList<Rules.Person>();
        Set<String> names = chosen.keySet();
        Map<String, Rules.Person> before = new java.util.HashMap<String, Rules.Person>();
        for (Rules.Person p : Rules.people(U.cfg(this, U.K_CONTACTS, U.DEF_CONTACTS))) before.put(p.name.toLowerCase(Locale.ITALY), p);
        for (String[] p : chosen.values()) {
            Rules.Person x = new Rules.Person();
            x.section = "Sempre";
            x.name = p[0].replace("|", "/");
            Rules.Person old = before.get(p[0].toLowerCase(Locale.ITALY));
            if (old != null && !old.apps.isEmpty()) x.apps.addAll(old.apps); // 0.6.3: le app scelte restano
            else {
                x.apps.addAll(U.chatAppsOf(this, p[0], null));                // WhatsApp e Telegram dalla rubrica
                if (p[1] != null && p[1].replaceAll("[^0-9]", "").length() >= 6 && !x.apps.contains("TEL")) x.apps.add("TEL");
            }
            x.target = old != null && old.target.length() > 0 ? old.target : p[1] == null ? "" : p[1];
            out.add(x);
        }
        boolean sample = !U.prefs(this).contains(U.K_CONTACTS);
        for (Rules.Person p : Rules.people(U.cfg(this, U.K_CONTACTS, U.DEF_CONTACTS))) {
            if (names.contains(p.name.toLowerCase(Locale.ITALY))) continue;
            if (p.section.equalsIgnoreCase("Sempre")) continue;          // tolta in questo passo
            if (sample && p.target.contains("...") || sample && "Contatto".equals(p.name)) continue; // niente esempi
            out.add(p);
        }
        Changes.record(this, "Persone che passano sempre: " + chosen.size(), new String[]{U.K_CONTACTS});
        U.prefs(this).edit().putString(U.K_CONTACTS, Rules.peopleText(out)).apply();
    }

    // ---------- 5. Notifiche ----------

    private void notif() {
        boolean on = U.listenerEnabled(this);
        if (restricted && !on) { restrictedGuide(); return; }
        if (askedNotif && !on) { notifDenied(); return; }
        title("Notifiche: passa solo chi conta");
        sub("−minus legge le notifiche per decidere quali mostrarti subito. Non le invia a nessuno.");
        LinearLayout c = U.col(this);
        c.setPadding(U.dp(this, 16), U.dp(this, 12), U.dp(this, 16), U.dp(this, 12));
        c.setBackground(U.box(this, U.SURFACE, U.LINE, 14));
        String[][] rows = {{"Per te", "chi hai scelto, chi ti risponde, codici e OTP"}, {"Servizi", "banca, SPID, app IO: accessi e codici passano, il resto aspetta"}, {"Aperte", "quando rispondi o tocchi la notifica, passa per 12 ore"},
                {"Gruppi", "in silenzio; quelli che vuoi li fai passare sempre"}, {"Altro", "il resto, in Notifiche, senza suoni"}};
        for (String[] r : rows) {
            LinearLayout l = U.row(this);
            l.setPadding(0, U.dp(this, 4), 0, U.dp(this, 4));
            TextView a = U.text(this, r[0], 14, U.MUTED, false);
            a.setMinWidth(U.dp(this, 64));
            l.addView(a);
            l.addView(U.text(this, r[1], 14, U.INK, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            c.addView(l, U.full());
        }
        body.addView(c, U.full());
        if (on) {
            TextView ok = U.text(this, "✓ Accesso alle notifiche attivo.", 15, U.INK, true);
            ok.setPadding(0, U.dp(this, 14), 0, 0);
            body.addView(ok);
            // 0.13: i Servizi riconosciuti, da confermare o cambiare
            String sv = ServiziActivity.names(this, 4);
            TextView s = U.text(this, sv.length() > 0 ? "Servizi riconosciuti: " + sv + "  ·  Modifica ›" : "Nessun servizio riconosciuto. Aggiungi la tua banca o l'app IO ›", 14, U.INK, false);
            s.setMinHeight(U.dp(this, 48));
            s.setGravity(Gravity.CENTER_VERTICAL);
            s.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { startActivity(new Intent(OnboardingActivity.this, ServiziActivity.class).putExtra("back", "Configurazione")); }
            });
            body.addView(s, U.full());
            primary("Avanti", new Runnable() { public void run() { next(); } });
            return;
        }
        note("Si apre Android: attiva −minus e torni qui da sola. Per far passare sempre un gruppo: tienilo premuto in Notifiche.");
        if (maybeRestricted()) {
            // 0.13.5: chi installa l'APK lo trova grigio: la guida è subito qui, non solo dopo un rifiuto
            TextView r = U.text(this, "L'interruttore è grigio e non si attiva? ›", 15, U.INK, false);
            r.setMinHeight(U.dp(this, 48));
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { restricted = true; render(); } });
            body.addView(r, U.full());
        }
        primary("Consenti l'accesso alle notifiche", new Runnable() {
            public void run() { askedNotif = true; openListener(); }
        });
    }

    private void openListener() {
        openAndWatch(new Runnable() { public void run() { Onboarding.openListenerSettings(OnboardingActivity.this); } },
                new Check() { public boolean ok() { return U.listenerEnabled(OnboardingActivity.this); } });
    }

    private void notifDenied() {
        title("Va bene, senza filtro per ora");
        sub("−minus funziona lo stesso: home, giornata, ricerca, pausa. Senza questo permesso manca:");
        box(new String[]{"Notifiche divise in Per te e Altro", "Persone e gruppi che passano con il silenzio", "Conversazioni aperte e attive nella ricerca"}, "–");
        if (Build.VERSION.SDK_INT >= 33) {
            TextView r = U.text(this, "Android dice «Impostazione con restrizioni»? ›", 15, U.INK, false);
            r.setMinHeight(U.dp(this, 48));
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { restricted = true; render(); } });
            body.addView(r, U.full());
        }
        TextView again = U.text(this, "Riprova ad attivarlo ›", 15, U.INK, false);
        again.setMinHeight(U.dp(this, 48));
        again.setGravity(Gravity.CENTER_VERTICAL);
        again.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openListener(); } });
        body.addView(again, U.full());
        note("Lo ritrovi in home («Continua la configurazione») e in Impostazioni › Attivazione. Non te lo richiediamo a ogni avvio.");
        primary("Avanti", new Runnable() { public void run() { next(); } });
    }

    /** Android 13+, app installata fuori dal Play Store: i tre tocchi per sbloccare il permesso. */
    private void restrictedGuide() {
        title("Android blocca questo permesso");
        sub("Succede alle app installate fuori dal Play Store. Si sblocca in tre tocchi, poi torni qui.");
        String[] steps = {"Tocca «Apri Info app» qui sotto", "In alto a destra tocca ⋮, poi «Consenti impostazioni con restrizioni»",
                "Conferma con PIN o impronta, torna indietro e riattiva l'accesso alle notifiche"};
        for (int i = 0; i < steps.length; i++) {
            LinearLayout r = U.row(this);
            r.setPadding(0, U.dp(this, 6), 0, U.dp(this, 6));
            TextView n = U.text(this, String.valueOf(i + 1), 13, U.BG, true);
            n.setGravity(Gravity.CENTER);
            n.setBackground(U.box(this, U.INK, 0, 13));
            r.addView(n, new LinearLayout.LayoutParams(U.dp(this, 26), U.dp(this, 26)));
            TextView t = U.text(this, steps[i], 15, U.INK, false);
            t.setPadding(U.dp(this, 12), 0, 0, 0);
            r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            body.addView(r, U.full());
        }
        TextView again = U.text(this, "Fatto: riattiva l'accesso alle notifiche ›", 15, U.INK, false);
        again.setMinHeight(U.dp(this, 48));
        again.setGravity(Gravity.CENTER_VERTICAL);
        again.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { restricted = false; openListener(); }
        });
        body.addView(again, U.full());
        note("Compare solo se Android segnala «Impostazione con restrizioni». Con il Play Store questo passo non c'è.");
        primary("Apri Info app", new Runnable() { public void run() { Onboarding.openAppInfo(OnboardingActivity.this); } });
    }

    // ---------- 6. Silenzio ----------

    private void silence() {
        title("Silenzio, tranne chi conta");
        sub("Con il silenzio attivo il telefono non suona per tutto il resto.");
        box(new String[]{"Squillano le chiamate dei preferiti (stella in rubrica)", "Squillano le chiamate ripetute", "Le sveglie suonano sempre",
                "Codici e OTP, e gli accessi dei Servizi (banca, SPID, app IO), passano sempre"}, "✓");
        boolean policy = Onboarding.policy(this);
        boolean on = U.prefs(this).getBoolean(U.K_SILENCE, false) && policy;
        if (on) {
            body.addView(U.text(this, "✓ Silenzio attivo.", 15, U.INK, true));
            primary("Avanti", new Runnable() { public void run() { done("silenzio"); } });
        } else if (policy) {
            note("Si spegne quando vuoi da Impostazioni › Attivazione.");
            primary("Attiva il silenzio", new Runnable() {
                public void run() {
                    if (FilterService.applySilence(OnboardingActivity.this, true)) U.prefs(OnboardingActivity.this).edit().putBoolean(U.K_SILENCE, true).apply();
                    done("silenzio");
                }
            });
        } else {
            note("Si apre Android: consenti l'accesso a «Non disturbare» per −minus e torna con Indietro.");
            primary("Consenti «Non disturbare»", new Runnable() { public void run() {
                openAndWatch(new Runnable() { public void run() { Onboarding.openPolicySettings(OnboardingActivity.this); } },
                        new Check() { public boolean ok() { return Onboarding.policy(OnboardingActivity.this); } });
            } });
        }
    }

    // ---------- 7. Giornata ----------

    private void day() {
        title("La tua giornata in home");
        sub("−minus mostra e modifica gli impegni del calendario di Android. La sincronizzazione resta al tuo account.");
        if (!U.canWriteCalendar(this) && !askedCal) {
            askedCal = true;
            requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR}, 4);
        }
        if (!U.hasCalendar(this)) {
            body.addView(U.text(this, "Senza il permesso Calendario la home mostra solo i task. Lo ritrovi in Impostazioni › Attivazione.", 14, U.MUTED, false));
            primary("Avanti", new Runnable() { public void run() { next(); } });
            return;
        }
        caption("Calendari da mostrare");
        final Set<Long> shown = U.shownCalendars(this);
        for (final U.CalRow r : U.allCalendars(this)) {
            final boolean on = shown.contains(r.id);
            String note = "com.google".equals(r.type) ? "Google · si vede anche in Google Calendar"
                    : android.provider.CalendarContract.ACCOUNT_TYPE_LOCAL.equals(r.type) ? "Solo su questo telefono"
                    : "Google Calendar non lo mostra";
            if (r.access < android.provider.CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) note += " · sola lettura";
            choice(r.name, note, on, false, new Runnable() {
                public void run() {
                    if (on) shown.remove(r.id); else shown.add(r.id);
                    U.setShownCalendars(OnboardingActivity.this, shown);
                    render();
                }
            });
        }
        caption("Nuovi impegni in");
        String def = U.prefs(this).getString("last_cal_name", null);
        choice(def != null ? def : "Scegli un calendario", "Tocca per cambiare", true, true, new Runnable() {
            public void run() { pickCalendar(); }
        });
        String al = U.nextAlarm(this);
        TextView a = U.text(this, al != null ? "Prossima sveglia: " + al + "  ›" : "Nessuna sveglia impostata  ·  Imposta ›", 14, U.INK, false);
        a.setMinHeight(U.dp(this, 44));
        a.setGravity(Gravity.CENTER_VERTICAL);
        a.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { U.openAlarms(OnboardingActivity.this); } });
        body.addView(a, U.full());
        note("In home vedi gli impegni di oggi non ancora finiti; domani compare dalle 17, la sveglia dalle 19.");
        primary("Avanti", new Runnable() { public void run() { next(); } });
    }

    private void pickCalendar() {
        final List<U.Cal> cals = U.writableCalendars(this);
        if (cals.isEmpty()) { U.toast(this, "Nessun calendario modificabile tra quelli mostrati"); return; }
        List<String> names = new ArrayList<String>();
        for (U.Cal k : cals) names.add(k.name + "\n" + U.calNote(k));
        new AlertDialog.Builder(this).setTitle("Nuovi impegni in").setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                U.prefs(OnboardingActivity.this).edit().putLong("last_cal", cals.get(w).id).putString("last_cal_name", cals.get(w).name).apply();
                render();
            }
        }).show();
    }

    // ---------- Suggerimenti per tipo di app (dai contenitori della 0.6) ----------

    static final String[] SHOP_HINTS = {"amazon", "vinted", "zalando", "ebay", "subito", "too good", "ikea", "shein", "temu", "glovo",
            "deliveroo", "just eat", "esselunga", "carrefour", "coop", "widilo", "decathlon", "lidl"};
    static final String[] MONEY_HINTS = {"bnl", "ing", "intesa", "unicredit", "fineco", "revolut", "n26", "satispay", "paypal", "trade republic",
            "scalable", "hype", "bper", "poste", "banco", "banca", "mediolanum", "widiba", "credem", "illimity", "buddybank", "wise"};
    static final String[] CONV_HINTS = {"gmail", "outlook", "posta", "mail", "signal", "messenger", "slack", "teams"};

    static int suggest(String label) {
        String l = " " + label.toLowerCase(Locale.ITALY) + " ";
        for (String h : MONEY_HINTS) if (l.contains(" " + h)) return 1;
        for (String h : SHOP_HINTS) if (l.contains(h)) return 0;
        for (String h : CONV_HINTS) if (l.contains(h)) return 2;
        return 3;
    }

    // ---------- 8. A portata (0.9: sostituisce i contenitori) ----------

    static final String[] DAILY_HINTS = {"maps", "trenitalia", "italo", "atm", "moovit", "trainline", "flixbus", "uber", "free now", "bolt"};

    /** Tipo per la proposta: pagamenti e banca, spesa, viaggi; null = non proposta. Mai i social. */
    static String kind(U.AppInfo a) {
        if (U.isSocial(a.pkg)) return null;
        String l = " " + a.label.toLowerCase(Locale.ITALY) + " ";
        if (suggest(a.label) == 1) return l.contains("satispay") || l.contains("paypal") ? "pagamenti" : "banca";
        for (String h : new String[]{"esselunga", "carrefour", "coop", "lidl", "glovo", "deliveroo", "just eat", "too good"}) if (l.contains(h)) return "spesa";
        for (String h : DAILY_HINTS) if (l.contains(" " + h)) return h.equals("maps") ? "" : "viaggi";
        return null;
    }

    /** Proposte: Maps e le app di tutti i giorni (pagamenti, banca, spesa, viaggi). Mai i social. */
    private void loadPortata() {
        portataCand = new ArrayList<String[]>();
        portataSel = new ArrayList<String[]>();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (String[] a : SearchActivity.portata(this)) { portataSel.add(new String[]{a[0], a[1]}); seen.add(a[0]); }
        for (U.AppInfo a : U.sortedApps(this)) {
            String k = kind(a);
            if (k == null) continue;
            portataCand.add(new String[]{a.pkg, a.label, k});
            if (!seen.contains(a.pkg) && portataSel.size() < SearchActivity.MAX_PORTATA) { portataSel.add(new String[]{a.pkg, a.label}); seen.add(a.pkg); }
        }
    }

    private void portata() {
        title("A portata");
        sub("Le app che vedi appena tocchi la ricerca, fino a " + SearchActivity.MAX_PORTATA + ". Le altre si aprono scrivendo il nome.");
        if (portataCand == null) loadPortata();
        // 0.11: una sola schermata con la ricerca tra tutte le app (wireframe 161)
        PortataPicker pk = new PortataPicker(this, portataSel, portataCand, false, new PortataPicker.Host() {
            public void changed(String desc, List<String[]> selected, Runnable after) { portataSel = selected; }
        });
        body.addView(pk.root, U.full());
        TextView p = U.text(this, "Le persone e i gruppi a portata sono quelli che passano sempre.", 14, U.MUTED, false);
        p.setPadding(0, U.dp(this, 10), 0, 0);
        body.addView(p);
        note("Già compilato con le app di tutti i giorni: pagamenti, banca, spesa, mappe.");
        primary("Avanti", new Runnable() {
            public void run() {
                Changes.record(OnboardingActivity.this, "A portata scelte nella configurazione guidata", new String[]{SearchActivity.K_PORTATA});
                SearchActivity.savePortata(OnboardingActivity.this, portataSel);
                done("portata");
            }
        });
    }

    // ---------- 9. Pausa e tempo ----------

    private void pause() {
        title("Pausa e tempo");
        sub("Le app che distraggono si aprono dopo qualche secondo. E puoi vedere da quanto sei al telefono.");
        Set<String> paused = U.pausedPkgs(this);
        int shown = 0;
        for (final U.AppInfo a : U.sortedApps(this)) {
            boolean social = U.isSocial(a.pkg);
            if (!social && !paused.contains(a.pkg)) continue;
            shown++;
            final int lv = Scope.dayLevel(this, a.pkg);
            LinearLayout r = U.row(this);
            r.setMinimumHeight(U.dp(this, 56));
            LinearLayout tx = U.col(this);
            tx.addView(U.text(this, a.label, 15, U.INK, false));
            tx.addView(U.text(this, social ? "Social · tocca per cambiare" : "Tocca per cambiare", 12, U.MUTED, false));
            r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            r.addView(U.text(this, PauseActivity.LEVELS[lv] + " ›", 14, lv == Scopo.NONE ? U.MUTED : U.INK, lv != Scopo.NONE));
            r.setContentDescription(a.label + ": " + PauseActivity.LEVELS[lv] + ". Tocca per cambiare");
            r.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { Scope.setLevel(OnboardingActivity.this, a.pkg, (lv + 2) % 3); render(); }
            });
            body.addView(r, U.full());
            body.addView(U.divider(this));
        }
        if (shown == 0) body.addView(U.text(this, "Nessun social installato. La pausa si aggiunge a qualsiasi app da Impostazioni › App.", 14, U.MUTED, false));

        caption("Tempo dall'ultimo sblocco, avvisami dopo");
        LinearLayout chips = U.row(this);
        int cur = Session.on(this) ? Session.threshold(this) : 0;
        int[] vals = {10, 20, 30, 0};
        for (final int m : vals) {
            boolean sel = m == cur;
            TextView t = U.text(this, m == 0 ? "Mai" : m + " min", 13, sel ? U.BG : U.INK, false);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(U.dp(this, 44));
            t.setPadding(U.dp(this, 14), 0, U.dp(this, 14), 0);
            t.setBackground(sel ? U.box(this, U.INK, 0, 18) : U.box(this, U.BG, U.BORDER, 18));
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (m == 0) U.prefs(OnboardingActivity.this).edit().putBoolean(Session.K_ON, false).apply();
                    else U.prefs(OnboardingActivity.this).edit().putBoolean(Session.K_ON, true).putInt(Session.K_MIN, m).apply();
                    render();
                }
            });
            LinearLayout.LayoutParams lp = U.wrap();
            lp.rightMargin = U.dp(this, 6);
            chips.addView(t, lp);
        }
        body.addView(chips, U.full());
        // 0.9: la pillola sopra le altre app
        final boolean pill = Pill.on(this);
        LinearLayout pr = U.row(this);
        pr.setMinimumHeight(U.dp(this, 60));
        LinearLayout ptx = U.col(this);
        ptx.addView(U.text(this, "Anche sopra le altre app", 15, U.INK, false));
        ptx.addView(U.text(this, "La pillola «25 min · 18 su Instagram»", 12, U.MUTED, false));
        pr.addView(ptx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        pr.addView(U.toggle(this, pill), new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 26)));
        pr.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { U.prefs(OnboardingActivity.this).edit().putBoolean(Pill.K_ON, !pill).apply(); render(); }
        });
        LinearLayout.LayoutParams plp = U.full();
        plp.topMargin = U.dp(this, 10);
        body.addView(pr, plp);
        note(interested(1) ? "Passo presente perché hai scelto «stare meno dentro le app»: per questo la pillola arriva accesa."
                : "Passo presente perché hai scelto «prendere il telefono meno spesso». Si cambia quando vuoi da Impostazioni.");
        primary("Avanti", new Runnable() {
            public void run() {
                if (Pill.on(OnboardingActivity.this) && (!Pill.canDraw(OnboardingActivity.this) || !Pill.usageGranted(OnboardingActivity.this))) {
                    pillStage = true;
                    render();
                } else done("pausa");
            }
        });
    }

    /** I due permessi della pillola (wireframe 130): il secondo è facoltativo. */
    private void pillPermissions() {
        title("Per mostrare il tempo sopra le app");
        sub("Servono due permessi di Android. Il secondo è facoltativo.");
        boolean draw = Pill.canDraw(this), usage = Pill.usageGranted(this);
        box(new String[]{(draw ? "✓ " : "1  ") + "Mostra sopra altre app · per la pillola",
                (usage ? "✓ " : "2  ") + "Dati di utilizzo · per dire quale app pesa di più. −minus vede quanto tempo, mai cosa fai dentro"}, "");
        LinearLayout dw = U.col(this);
        dw.setPadding(U.dp(this, 16), U.dp(this, 12), U.dp(this, 16), U.dp(this, 12));
        dw.setBackground(U.box(this, U.SURFACE, U.LINE, 14));
        dw.addView(U.text(this, "Usi i timer di Benessere digitale?", 14, U.INK, true));
        dw.addView(U.text(this, "Android conta anche −minus, perché è la home. Se ci hai messo un timer, toglilo.", 13, U.MUTED, false));
        TextView open = U.text(this, "Apri Benessere digitale ›", 14, U.INK, false);
        open.setMinHeight(U.dp(this, 44));
        open.setGravity(Gravity.CENTER_VERTICAL);
        open.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Session.openWellbeing(OnboardingActivity.this); } });
        dw.addView(open);
        body.addView(dw, U.full());
        if (!draw) {
            note("Si apre Android: attiva −minus e torna con Indietro. Senza: niente pillola, resta la riga in home.");
            primary("Consenti «Mostra sopra altre app»", new Runnable() { public void run() {
                openAndWatch(new Runnable() { public void run() { Pill.openOverlaySettings(OnboardingActivity.this); } },
                        new Check() { public boolean ok() { return Pill.canDraw(OnboardingActivity.this); } });
            } });
        } else if (!usage) {
            TextView skip = U.text(this, "Va bene solo il totale ›", 15, U.INK, false);
            skip.setMinHeight(U.dp(this, 48));
            skip.setGravity(Gravity.CENTER_VERTICAL);
            skip.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { done("pausa"); } });
            body.addView(skip, U.full());
            note("Si apre Android: trova −minus in «Accesso ai dati di utilizzo» e torna con Indietro.");
            primary("Consenti dati di utilizzo · facoltativo", new Runnable() { public void run() {
                openAndWatch(new Runnable() { public void run() { Pill.openUsageSettings(OnboardingActivity.this); } },
                        new Check() { public boolean ok() { return Pill.usageGranted(OnboardingActivity.this); } });
            } });
        } else {
            body.addView(U.text(this, "✓ Pronta: la vedrai sopra le app, oltre la soglia.", 15, U.INK, true));
            primary("Avanti", new Runnable() { public void run() { done("pausa"); } });
        }
    }

    // ---------- Aspetto (0.10) ----------

    private void aspetto() {
        title("Com'è la tua home?");
        sub("−minus sostituisce la home: falla tua. L'anteprima cambia mentre scegli.");
        body.addView(Aspetto.preview(this), U.full());
        Aspetto.controls(this, body, false, new Runnable() {
            public void run() {
                root.setBackgroundColor(U.BG);
                getWindow().setStatusBarColor(U.BG);
                getWindow().setNavigationBarColor(U.BG);
                U.immersive(OnboardingActivity.this);
                render();
            }
        });
        note("Si cambia quando vuoi da Impostazioni › Home e giornata › Aspetto.");
        primary("Avanti", new Runnable() { public void run() { next(); } });
    }

    // ---------- Di notte (0.10) ----------

    private EditText nightAlt;

    private void night() {
        title("Di notte");
        sub("Se ti svegli e prendi il telefono, −minus ti mostra solo l'essenziale. Non misura il sonno.");
        caption("La tua alternativa");
        nightAlt = new EditText(this);
        nightAlt.setText(Night.alternative(this));
        nightAlt.setTextColor(U.INK);
        nightAlt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        nightAlt.setBackground(U.box(this, U.SURFACE, U.BORDER, 12));
        nightAlt.setPadding(U.dp(this, 14), U.dp(this, 12), U.dp(this, 14), U.dp(this, 12));
        body.addView(nightAlt, U.full());
        body.addView(U.text(this, "Scrivila con parole tue: è quello che vuoi fare invece di scorrere.", 12, U.MUTED, false));
        caption("Quando è notte");
        final String mode = Night.mode(this);
        choice("In automatico", "Dalla prima lunga pausa della sera fino alla sveglia", Notte.AUTO.equals(mode), true, new Runnable() {
            public void run() { saveNightAlt(); U.prefs(OnboardingActivity.this).edit().putString(Night.K_MODE, Notte.AUTO).apply(); render(); }
        });
        choice("Dalle 23 alle 6", "Ogni notte, alla stessa ora", Notte.FIXED.equals(mode), true, new Runnable() {
            public void run() { saveNightAlt(); U.prefs(OnboardingActivity.this).edit().putString(Night.K_MODE, Notte.FIXED).apply(); render(); }
        });
        note("Prova di una settimana: di notte le app con la pausa chiedono lo scopo, con tempi brevi. Alla fine ti mostro i numeri e decidi tu.");
        primary("Prova per una settimana", new Runnable() {
            public void run() { saveNightAlt(); Night.startTrial(OnboardingActivity.this); next(); }
        });
    }

    private void saveNightAlt() {
        if (nightAlt == null) return;
        String s = nightAlt.getText().toString().trim();
        if (s.length() > 0) U.prefs(this).edit().putString(Night.K_ALT, s).apply();
    }

    // ---------- Pronto ----------

    private void ready() {
        View top = new View(this);
        body.addView(top, new LinearLayout.LayoutParams(1, U.dp(this, 12)));
        title("Pronto");
        sub("Ecco com'è impostato −minus. Tutto si cambia da Impostazioni: sono la prima voce della ricerca, o tieni premuto su uno spazio vuoto della home.");
        List<String> ok = new ArrayList<String>();
        if (Onboarding.isDefaultHome(this)) ok.add("Home: −minus");
        int p = Onboarding.alwaysCount(this);
        if (p > 0) ok.add(p + (p == 1 ? " persona passa sempre" : " persone passano sempre"));
        if (U.listenerEnabled(this)) ok.add("Filtro notifiche attivo");
        int sv = ServiziActivity.installed(this).size();
        if (sv > 0) ok.add("Servizi: " + sv + " app, i loro codici passano sempre");
        if (U.hasCalendar(this)) {
            String def = U.prefs(this).getString("last_cal_name", null);
            ok.add("Giornata: " + U.shownCalendars(this).size() + " calendari" + (def != null ? ", nuovi in " + def : ""));
        }
        int ap = SearchActivity.portata(this).size();
        if (ap > 0) ok.add("A portata: " + ap + " app");
        if (Pill.active(this)) ok.add("Pillola del tempo sopra le app");
        int sc = Scope.scopePkgs(this).size();
        if (sc > 0) ok.add("Pausa e scopo su " + sc + (sc == 1 ? " app" : " app"));
        if (Night.on(this)) ok.add("Di notte: prova di una settimana");
        if (ok.isEmpty()) ok.add("−minus funziona già: home, giornata, ricerca");
        box(ok.toArray(new String[0]), "✓");
        List<String> todo = new ArrayList<String>();
        if (!Onboarding.isDefaultHome(this)) todo.add("App Home predefinita");
        if (!U.listenerEnabled(this)) todo.add("Accesso alle notifiche");
        for (String m : Onboarding.missing(this)) if (!cont || !"Silenzio".equals(m) || !Onboarding.policy(this)) todo.add(m);
        if (!todo.isEmpty()) {
            caption("Da completare");
            box(todo.toArray(new String[0]), "·");
        }
        List<String> more = new ArrayList<String>();
        boolean inPath = false;
        for (int st : path) if (st == PAUSE) inPath = true;
        if (!inPath && !cont) more.add("Pausa e tempo");
        more.add("Comandi vocali");
        caption("Quando vuoi, da Impostazioni");
        box(more.toArray(new String[0]), "+");
        note("Sbagliato qualcosa? Dopo ogni modifica c'è «Annulla»; in Impostazioni › Modifiche recenti ritrovi le ultime scelte.");
        primary("Vai alla home", new Runnable() {
            public void run() {
                if (!cont) Onboarding.finish(OnboardingActivity.this, included());
                finish();
            }
        });
    }
}
