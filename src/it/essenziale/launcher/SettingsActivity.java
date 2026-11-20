package it.essenziale.launcher;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Impostazioni (0.14, wireframe 198–203): la tua home e la tua giornata, poi un'area per ogni
 * obiettivo con il suo interruttore, la tua alternativa e i permessi. Stessi nomi dell'onboarding.
 * Spariscono Attivazione, App, Tempo e uso, e i contenitori.
 * La stessa schermata mostra la radice o, con l'extra "area", una sola area.
 */
public class SettingsActivity extends PageActivity {

    static final int MAIN = -1, HOME = 0, DAY = 1, NOTIF = 2, DOOM = 3, SLEEP = 4, PERMS = 5, TODO = 6, MORE = 7;
    static final int SETUP = PERMS; // chi apriva Attivazione ora apre Permessi
    static final String[] TITLES = {"La tua home", "La tua giornata", "Meno notifiche", "Meno doomscrolling", "Meno interferenze sul sonno",
            "Permessi", "Da completare", "Altro"};

    private int area = MAIN;
    private LinearLayout body;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Goals.migrate(this);
        area = getIntent().getIntExtra("area", MAIN);
        if (area >= TITLES.length) area = MAIN;
        if (area == DAY) area = HOME; // 0.18 (Evolutiva 011): La tua giornata è dentro La tua home
        if (area == PERMS) area = TODO; // 0.18: Permessi tolta, i permessi mancanti sono in Da completare
        LinearLayout root = U.screen(this, area == MAIN ? "Impostazioni" : TITLES[area]);
        ScrollView sv = new ScrollView(this);
        body = U.col(this);
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) { render(); }

    private void render() {
        body.removeAllViews();
        switch (area) {
            case HOME: home(); break;
            case DAY: day(); break;
            case NOTIF: notif(); break;
            case DOOM: doom(); break;
            case SLEEP: sleep(); break;
            case TODO: todo(); break;
            case MORE: more(); break;
            default: main();
        }
    }

    // ---------- 198 · Radice ----------

    private void main() {
        open("La tua home", "Aspetto, calendario, a portata, tastiera, comandi vocali", HOME); // 0.18 (Evolutiva 011)
        body.addView(U.label(this, "I tuoi obiettivi"));
        int people = Onboarding.alwaysCount(this);
        open(Goals.NAMES[0], Goals.on(this, Goals.NOTIF) ? "Acceso · " + people + (people == 1 ? " persona" : " persone") + " che contano" : "Spento", NOTIF);
        open(Goals.NAMES[1], Goals.on(this, Goals.DOOM) ? "Acceso · pausa su " + U.pausedPkgs(this).size() + " app" : "Spento", DOOM);
        open(Goals.NAMES[2], Goals.on(this, Goals.SLEEP) && Night.on(this) ? "Acceso · staccare alle " + Notte.hhmm(Night.offMin(this)) : "Spento", SLEEP);
        body.addView(U.label(this, "−minus"));
        row("La tua alternativa", U.prefs(this).getString(Night.K_ALT, "Da scrivere"), "›", new Runnable() { public void run() { editAlternative(); } });
        // 0.18 (Evolutiva 011): «Permessi» non sta più nella radice: quelli che mancano sono in Da completare
        int todo = Onboarding.todo(this).size();
        open("Da completare · Modifiche recenti · Segnala un problema", todo == 0 ? "Niente da completare" : todo + " da completare", MORE);
        // 0.18 (Evolutiva 011): in fondo versione, aggiornamenti e informativa
        String v = "?";
        try { v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
        LinearLayout foot = U.row(this);
        foot.setPadding(0, U.dp(this, 28), 0, 0);
        TextView ver = U.text(this, "", 13, U.MUTED, false);
        android.text.SpannableString vs = new android.text.SpannableString("−minus · versione " + v);
        vs.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.ITALIC), 0, 6, 0);
        ver.setText(vs);
        foot.addView(ver, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        foot.addView(link("Controlla aggiornamenti", RELEASES));
        body.addView(foot, U.full());
        LinearLayout lp = U.row(this);
        lp.setPadding(0, 0, 0, U.dp(this, 24));
        lp.addView(link("Informativa sulla privacy ↗", PRIVACY));
        body.addView(lp, U.full());
    }

    static final String RELEASES = "https://github.com/minus-phone/minus/releases/latest",
            PRIVACY = "https://github.com/minus-phone/minus/blob/main/PRIVACY.md";

    private TextView link(String text, final String url) {
        TextView t = U.text(this, text, 14, U.INK, false);
        t.setPaintFlags(t.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        t.setMinHeight(U.dp(this, 44));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setContentDescription(text.replace(" ↗", "") + ", link");
        PressB.link(t); // 0.21: link di testo, anello di 6 dp
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { web(url); } });
        return t;
    }

    private void web(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))); }
        catch (Exception e) { U.toast(this, "Non trovo un browser"); }
    }

    private void open(String title, String sub, int a) {
        Intent i = new Intent(this, SettingsActivity.class);
        i.putExtra("area", a);
        i.putExtra("back", area == MAIN ? "Impostazioni" : TITLES[area]);
        nav(title, sub, i, "›");
    }

    private void editAlternative() {
        final android.widget.EditText e = new android.widget.EditText(this);
        e.setText(U.prefs(this).getString(Night.K_ALT, ""));
        e.setHint("Esempio: Leggere qualche pagina");
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        android.widget.FrameLayout f = new android.widget.FrameLayout(this);
        f.setPadding(U.dp(this, 20), U.dp(this, 8), U.dp(this, 20), 0);
        f.addView(e);
        U.dlg(this).setTitle("Cosa preferisci fare invece di scorrere?").setView(f)
                .setPositiveButton("Salva", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        final String v = e.getText().toString().trim();
                        if (v.length() == 0) return;
                        Changes.track(SettingsActivity.this, "La tua alternativa: " + v, new String[]{Night.K_ALT},
                                new Runnable() { public void run() { U.prefs(SettingsActivity.this).edit().putString(Night.K_ALT, v).commit(); } },
                                new Runnable() { public void run() { render(); } });
                    }
                }).setNegativeButton("Annulla", null).show();
    }

    // ---------- La tua home ----------

    private void home() {
        // 0.18 (Evolutiva 011): La tua home unisce La tua giornata. Tolti App Home predefinita
        // (si sceglie nell'onboarding e in Permessi), Sveglia (è nell'Orologio) e le due note
        String th = U.prefs(this).getString(U.K_THEME, U.THEME_LIGHT);
        nav("Aspetto", "Tema " + th + " · testo " + (U.prefs(this).getBoolean(U.K_TEXT_BIG, false) ? "grande" : "normale"), to(AspettoActivity.class), "›");
        if (!U.hasCalendar(this)) row("Calendario", "Non collegato · senza, la home mostra solo i task e la sveglia", "Concedi ›", new Runnable() {
            public void run() { Onboarding.fix(SettingsActivity.this, "calendario"); }
        });
        else {
            int shown = U.shownCalendars(this).size();
            String def = U.prefs(this).getString("last_cal_name", null);
            nav("Calendario", (shown > 0 ? shown + " mostrati" : "Tutti quelli sincronizzati") + (def != null ? " · nuovi impegni in " + def : ""), to(CalendarsActivity.class), "›");
        }
        nav("A portata nella ricerca", "Contatti e gruppi, " + SearchActivity.portata(this).size() + " app", to(PortataActivity.class), "›");
        prefToggle("Apri subito la tastiera", "Nella ricerca. Spento: vedi prima recenti, app a portata e persone", U.K_KEYBOARD, false,
                "Tastiera aperta subito nella ricerca", "Tastiera chiusa all'apertura della ricerca");
        nav("Comandi vocali", "Cosa puoi dire dalla home", to(VoiceHelpActivity.class), "›");
        final boolean lockOn = U.prefs(this).getBoolean(U.K_LOCK, true);
        toggle("Impegni e task nella schermata di blocco", "Si vedono senza sbloccare il telefono", lockOn, new Runnable() {
            public void run() {
                Changes.track(SettingsActivity.this, lockOn ? "Impegni e task nella schermata di blocco: spenti" : "Impegni e task nella schermata di blocco: accesi", new String[]{U.K_LOCK},
                        new Runnable() { public void run() { U.prefs(SettingsActivity.this).edit().putBoolean(U.K_LOCK, !lockOn).commit(); } },
                        new Runnable() { public void run() { render(); } });
            }
        });
    }

    private void day() { home(); }

    // ---------- Interruttore dell'obiettivo ----------

    /** In cima a ogni obiettivo: l'interruttore che accende o spegne tutto insieme, con Annulla. */
    private boolean goalHeader(final String g, String desc) {
        final boolean on = Goals.on(this, g) && (!Goals.SLEEP.equals(g) || Night.on(this));
        toggle(on ? "Acceso" : "Spento", desc, on, "goal:" + g, new Runnable() {
            public void run() {
                if (!on && !configured(g)) { accendi(g); return; }
                Changes.track(SettingsActivity.this, Goals.name(g) + (on ? " spento" : " acceso"), new String[]{Onboarding.K_GOALS, Night.K_ON},
                        new Runnable() { public void run() { Goals.set(SettingsActivity.this, g, !on); } },
                        new Runnable() { public void run() { render(); if (on && Goals.NOTIF.equals(g)) offerUnstar(); } });
            }
        });
        return on;
    }

    /** Già passato dai suoi passi? Altrimenti «Accendi» li apre. */
    private boolean configured(String g) {
        if (Goals.DOOM.equals(g)) return !U.pausedPkgs(this).isEmpty();
        if (Goals.SLEEP.equals(g)) return Notte.SCHED.equals(Night.mode(this)) || U.prefs(this).contains(Night.K_TRIAL);
        return true;
    }

    private void accendi(String g) {
        startActivity(new Intent(this, OnboardingActivity.class).putExtra("goal", g));
    }

    /** Spento «Meno notifiche»: la stella resta, con la proposta di toglierla (200). */
    private void offerUnstar() {
        final int n = Stars.mineCount(this);
        if (n == 0) return;
        U.dlg(this).setTitle("Togliere anche le stelle?")
                .setMessage("−minus aveva messo la stella in rubrica a " + n + (n == 1 ? " persona" : " persone") + ". Restano preferite anche per Android, finché non le togli.")
                .setPositiveButton("Toglile", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { U.toast(SettingsActivity.this, "Stelle tolte: " + Stars.unstarMine(SettingsActivity.this)); }
                }).setNegativeButton("Lasciale", null).show();
    }

    /** 201: spento, l'obiettivo si presenta invece di mostrare interruttori vuoti. */
    private void intro(final String g, String what, String time) {
        intro(Goals.SUBS[java.util.Arrays.asList(Goals.ALL).indexOf(g)]);
        LinearLayout c = U.col(this);
        c.setPadding(U.dp(this, 16), U.dp(this, 12), U.dp(this, 16), U.dp(this, 12));
        c.setBackground(U.box(this, U.SURFACE, U.LINE, 14));
        c.addView(U.text(this, "Cosa fa, se lo accendi", 15, U.INK, false));
        c.addView(U.text(this, what, 13, U.MUTED, false));
        c.addView(U.text(this, time, 13, U.MUTED, false));
        LinearLayout.LayoutParams lp = U.full();
        lp.topMargin = U.dp(this, 8);
        lp.bottomMargin = U.dp(this, 16);
        body.addView(c, lp);
        TextView b = button("Accendi");
        b.setTextColor(U.BG);
        Press.primary(b, U.INK, 26);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (configured(g)) {
                    Changes.track(SettingsActivity.this, Goals.name(g) + " acceso", new String[]{Onboarding.K_GOALS, Night.K_ON},
                            new Runnable() { public void run() { Goals.set(SettingsActivity.this, g, true); } },
                            new Runnable() { public void run() { render(); } });
                } else accendi(g);
            }
        });
        body.addView(b, U.full());
    }

    // ---------- 200 · Meno notifiche ----------

    private void notif() {
        if (!Goals.on(this, Goals.NOTIF)) {
            intro(Goals.NOTIF, "Ti avvisano solo chi conta, i servizi e le chiamate ripetute. Il resto aspetta in Notifiche, senza suoni.", "Due schermate e due permessi di Android.");
            return;
        }
        goalHeader(Goals.NOTIF, "Ti raggiunge solo chi conta. Spegnendolo, le notifiche tornano quelle di Android");
        // 0.18 (Evolutiva 009): COME TI AVVISA con «Contatti e gruppi», poi APP e BARRA DI STATO.
        // Evolutiva 010: «Sblocchi senza scopo» passa in Meno doomscrolling; qui resta un rimando
        body.addView(U.label(this, "Come ti avvisa"));
        final boolean open = U.prefs(this).getBoolean(U.K_OPEN_ON, true);
        toggle("Conversazioni aperte", "Se rispondi a qualcuno, ti avvisa per 12 ore. Gruppi 1 ora, mai di notte", open, new Runnable() {
            public void run() {
                Changes.track(SettingsActivity.this, open ? "Conversazioni aperte spente" : "Conversazioni aperte attive", new String[]{U.K_OPEN_ON},
                        new Runnable() { public void run() { U.prefs(SettingsActivity.this).edit().putBoolean(U.K_OPEN_ON, !open).commit(); } },
                        new Runnable() { public void run() { render(); } });
            }
        });
        final boolean policy = Onboarding.policy(this);
        final boolean silenceOn = U.prefs(this).getBoolean(U.K_SILENCE, false) && policy;
        row("Chiamate", !policy ? "Squillano solo chi conta · Non disturbare da concedere" : silenceOn ? "Squillano solo chi conta e le chiamate ripetute · tocca per far squillare tutti"
                : "Squillano tutti · tocca per far squillare solo chi conta", !policy ? "Concedi ›" : silenceOn ? "✓" : "Spento", new Runnable() {
            public void run() {
                if (!policy) { Onboarding.openPolicySettings(SettingsActivity.this); return; }
                boolean next = !silenceOn;
                if (FilterService.applySilence(SettingsActivity.this, next)) U.prefs(SettingsActivity.this).edit().putBoolean(U.K_SILENCE, next).apply();
                render();
            }
        });
        nav("Contatti e gruppi", contactsSummary(), peopleIntent(this, TITLES[NOTIF]), "›");
        body.addView(U.label(this, "App"));
        int sv = ServiziActivity.installed(this).size();
        nav("Servizi", sv + " riconosciuti · banca, SPID, IO, salute, scuola: codici e accessi passano sempre", to(ServiziActivity.class), "›");
        int ac = U.appChoices(this).size();
        row("Notifiche delle app", "Per ogni app: passa sempre, in Per te o in Altro" + (ac > 0 ? " · " + ac + " scelte" : ""), "›", new Runnable() {
            public void run() { AppChoice.list(SettingsActivity.this, new Runnable() { public void run() { render(); } }); }
        });
        body.addView(U.label(this, "Barra di stato"));
        int sticky = Sticky.recent(this).size();
        nav("Icone fisse nella barra", sticky == 0 ? "Nessuna negli ultimi 7 giorni" : sticky + " app negli ultimi 7 giorni · rendile silenziose", to(StickyActivity.class), "›");
        // per qualche versione, per chi cerca qui la domanda sugli sblocchi
        Intent dm = new Intent(this, SettingsActivity.class).putExtra("area", DOOM).putExtra("back", TITLES[NOTIF]);
        nav("Sblocchi senza scopo", "Ora è in Meno doomscrolling", dm, "›");
        note("Contatti e gruppi si cambiano anche da Notifiche, tenendo premuto.");
    }

    // ---------- 201 · Meno doomscrolling ----------

    private void doom() {
        if (!Goals.on(this, Goals.DOOM)) {
            intro(Goals.DOOM, "Una pausa prima di Instagram, TikTok e le altre app che scegli. Il tempo nelle app che rapiscono in home e, se vuoi, sopra di loro. E una domanda quando lo sblocchi senza motivo.",
                    "Tre schermate, circa un minuto.");
            return;
        }
        goalHeader(Goals.DOOM, "Spegnendolo, le app si aprono senza pausa e non ti chiede più niente allo sblocco; le scelte restano");
        // 0.18 (Evolutiva 010): PRIMA DI APRIRE e QUANDO LO SBLOCCHI
        body.addView(U.label(this, "Prima di aprire"));
        nav("App che rapiscono", U.pausedPkgs(this).size() + " app · " + Scope.scopePkgs(this).size() + " con pausa e scopo", to(PauseActivity.class), "›");
        pauseLength();
        body.addView(U.label(this, "Quando lo sblocchi"));
        // 0.18 (Evolutiva 012): conta solo il tempo nelle app frenate; senza accesso ai dati di utilizzo non conta
        if (Session.on(this) && !Pill.usageGranted(this))
            row("Tempo nelle app che rapiscono", "Serve l'accesso ai dati di utilizzo per sapere quale app hai davanti", "Concedi ›", new Runnable() { public void run() { Pill.openUsageSettings(SettingsActivity.this); } });
        else nav("Tempo nelle app che rapiscono", Session.on(this) ? "Avvisami dopo " + Session.threshold(this) + " min · si ferma nelle altre app" : "Spento", to(SessionActivity.class), "›");
        final boolean pill = U.prefs(this).getBoolean(Pill.K_ON, false);
        final boolean draw = Pill.canDraw(this);
        toggle("Anche sopra le app che rapiscono", pill && !draw ? "Manca il permesso «Mostra sopra altre app» · tocca per darlo" : !Pill.usageGranted(this) ? "Spenta finché manca l'accesso ai dati di utilizzo" : "La pillola «18 min» dentro Instagram, solo oltre la soglia", pill, new Runnable() {
            public void run() {
                if (pill && !draw) { Pill.openOverlaySettings(SettingsActivity.this); return; }
                Changes.track(SettingsActivity.this, pill ? "Pillola del tempo spenta" : "Pillola del tempo accesa", new String[]{Pill.K_ON},
                        new Runnable() { public void run() { U.prefs(SettingsActivity.this).edit().putBoolean(Pill.K_ON, !pill).commit(); } },
                        new Runnable() { public void run() { render(); if (!pill && !Pill.canDraw(SettingsActivity.this)) Pill.openOverlaySettings(SettingsActivity.this); } });
            }
        });
        final boolean unlock = U.prefs(this).getBoolean(Sblocco.K_ON, false);
        toggle("Sblocchi senza scopo", "Al terzo sblocco in 10 minuti senza aprire niente ti chiede per cosa l'hai preso. Al massimo 3 volte al giorno, mai di notte", unlock, new Runnable() {
            public void run() {
                Changes.track(SettingsActivity.this, unlock ? "Domanda sugli sblocchi senza scopo spenta" : "Domanda sugli sblocchi senza scopo accesa", new String[]{Sblocco.K_ON},
                        new Runnable() { public void run() { U.prefs(SettingsActivity.this).edit().putBoolean(Sblocco.K_ON, !unlock).commit(); } },
                        new Runnable() { public void run() { render(); } });
            }
        });
        body.addView(U.label(this, "Altro"));
        row("Hai un timer su −minus in Benessere digitale?", "Android conta anche la home: toglilo da lì", "↗", new Runnable() {
            public void run() { Session.openWellbeing(SettingsActivity.this); }
        });
        note("−minus vede quanto tempo passi in ogni app e quando sblocchi il telefono, mai cosa fai dentro. Nessun dato d'uso esce dal telefono.");
    }

    // ---------- 202 · Meno interferenze sul sonno ----------

    private void sleep() {
        if (!(Goals.on(this, Goals.SLEEP) && Night.on(this))) {
            intro(Goals.SLEEP, "Dall'ora di staccare la home va in grigio e mostra domani; di notte uno sblocco mostra solo l'ora e la sveglia, e le app scelte restano frenate.",
                    "Due schermate, una settimana di prova.");
            return;
        }
        goalHeader(Goals.SLEEP, "Spegnendolo, la sera e la notte tornano come il resto del giorno");
        Intent q = new Intent(this, OnboardingActivity.class).putExtra("only", OnboardingActivity.SLEEP);
        // 0.16: una riga sola: sveglia e ora di staccare si scelgono insieme
        nav("Quando dormi", "Sveglia " + Notte.hhmm(Night.wakeMin(this)) + " · staccare alle " + Notte.hhmm(Night.offMin(this)), q, "›");
        int preMin = ((Night.offMin(this) - Serale.PREWARN_MIN) % 1440 + 1440) % 1440;
        nav("Avviso e consiglio della sera", (Sera.prewarnOn(this) ? "Preavviso alle " + Notte.hhmm(preMin) : "Senza preavviso")
                + (Sera.tipOn(this) ? " · consiglio della settimana" : ""), to(SeraActivity.class), "›");
        prefToggle("Modalità sera", "Grigio e domani in home", Night.K_EVENING, true, "Modalità sera accesa", "Modalità sera spenta");
        nav("Chi ti raggiunge di notte", Onboarding.alwaysCount(this) + " persone che contano", to(PeopleActivity.class), "›");
        StringBuilder na = new StringBuilder();
        for (String pk : Night.nightApps(this)) { if (na.length() > 0) na.append(", "); na.append(U.appLabel(this, pk)); }
        nav("App frenate di notte", na.length() == 0 ? "Nessuna" : na.toString(), to(NightSettingsActivity.class), "›");
        prefToggle("Mattino tranquillo", "Fino a " + Notte.MORNING_MIN + " minuti dopo la sveglia", Night.K_MORNING, true, "Mattino tranquillo acceso", "Mattino tranquillo spento");
        row("Scala di grigi di Android", "Apri Modalità Riposo · orari " + Notte.hhmm(Night.offMin(this)) + " – " + Notte.hhmm(Night.wakeMin(this)), "↗", new Runnable() {
            public void run() { Session.openWellbeing(SettingsActivity.this); }
        });
        nav("Rapporto della settimana", "Com'è andata la prova", to(NightReportActivity.class), "›");
        note("Se cambi la sveglia, ricordati di aggiornare anche gli orari della Modalità Riposo di Android.");
    }

    static String hours(int min) {
        return min % 60 == 0 ? String.valueOf(min / 60) : (min / 60) + "½";
    }

    // 0.18: la pagina «Permessi» non c'è più: quelli che mancano sono in Da completare, gli altri si tolgono da Android

    // ---------- Da completare ----------

    private void todo() {
        List<String[]> t = Onboarding.todo(this);
        if (t.isEmpty()) { intro("Niente da completare: −minus ha tutto quello che serve agli obiettivi accesi."); return; }
        intro("Ogni schermata saltata e ogni permesso mancante, della base e degli obiettivi accesi.");
        for (final String[] x : t)
            row(x[1], x[2] == null ? "Configurazione" : Goals.name(x[2]), "Fai ora ›", new Runnable() { public void run() { Onboarding.fix(SettingsActivity.this, x[0]); } });
    }

    // ---------- Altro ----------

    private void more() {
        int todo = Onboarding.todo(this).size();
        open("Da completare", todo == 0 ? "Niente" : todo + " da completare", TODO);
        nav("Modifiche recenti", "Le ultime scelte, per tornare indietro", new Intent(this, ChangesActivity.class), "›");
        nav("Segnala un problema", "Qualcosa non va? Raccontacelo", new Intent(this, BugActivity.class), "›");
        nav("Registro per la beta", Beta.on(this) ? "Acceso · " + Registro.days(Beta.log(this)) + " giorni · " + Beta.code(this) : "Spento · solo per chi partecipa alla beta",
                new Intent(this, BetaActivity.class), "›");
        row("Cosa vede −minus", "Le ultime notifiche di chat arrivate e dove sono finite", "›", new Runnable() { public void run() { showLog(); } });

        TextView again = button("Rifai la configurazione guidata");
        again.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(SettingsActivity.this, OnboardingActivity.class);
                i.putExtra("again", true);
                startActivity(i);
            }
        });
        LinearLayout.LayoutParams lp = U.full();
        lp.topMargin = U.dp(this, 18);
        lp.bottomMargin = U.dp(this, 90); // 0.16: «Ripristina le liste di esempio» tolto: era per le prove
        body.addView(again, lp);
    }

    /** 0.6.2: per capire se una chat arriva a Essenziale (se non c'è qui, Android o l'app non la notificano). 0.16: senza la riga del barometro. */
    private void showLog() { showLog(""); }

    private void showLog(String head) {
        StringBuilder b = new StringBuilder(head);
        b.append("Icone delle app salvate: ").append(AppIcons.count(this)).append(" (arrivano con la prima notifica di ogni app).\n\n");
        for (String[] r : U.rows(U.prefs(this).getString(FilterService.K_LOG, ""))) {
            if (r.length < 5) continue;
            long t = 0;
            try { t = Long.parseLong(r[0]); } catch (Exception ignored) {}
            b.append(Changes.when(t)).append("  ·  ").append(r[1]).append("\n").append(r[2]).append(" (").append(r[3]).append(") → ").append(r[4]).append("\n\n");
        }
        if (b.length() == 0) b.append(U.listenerEnabled(this) ? "Ancora nessuna notifica di chat da quando hai aggiornato." : "L'accesso alle notifiche è spento: −minus non vede niente.");
        b.append("Se una chat non compare qui, la notifica non arriva proprio: è disattivata o silenziata in WhatsApp o Telegram, o nelle impostazioni di Android.");
        U.dlg(this).setTitle(U.brand("Cosa vede −minus")).setMessage(b.toString()).setPositiveButton("Ok", null).show();
    }

    /** 0.18: «4 contatti e 1 gruppo passano sempre · 2 aperte ora»: lo stato di adesso. */
    private String contactsSummary() {
        int people = Onboarding.alwaysCount(this), groups = 0;
        for (String[] g : U.rows(U.prefs(this).getString(U.K_GROUPS, ""))) if (g.length >= 2 && "sempre".equals(g[1])) groups++;
        int open = Convs.openMap(this).size();
        StringBuilder b = new StringBuilder();
        if (people + groups == 0) b.append("Nessuno passa sempre");
        else {
            if (people > 0) b.append(people).append(people == 1 ? " contatto" : " contatti");
            if (groups > 0) b.append(people > 0 ? " e " : "").append(groups).append(groups == 1 ? " gruppo" : " gruppi");
            b.append(people + groups == 1 ? " passa sempre" : " passano sempre");
        }
        if (open > 0) b.append(" · ").append(open).append(open == 1 ? " aperta ora" : " aperte ora");
        return b.toString();
    }

    /** 0.7: la lista di persone e gruppi (era Conversazioni). 0.18 (Evolutiva 009): «Contatti e gruppi», con i tab. */
    static Intent peopleIntent(android.content.Context c, String back) {
        Intent i = new Intent(c, ListActivity.class);
        i.putExtra(ListActivity.EXTRA_MODE, ListActivity.MODE_CONTACTS);
        i.putExtra("title", "Contatti e gruppi");
        i.putExtra("back", back);
        return i;
    }

    // ---------- Pezzi ----------

    private Intent to(Class<?> c) {
        Intent i = new Intent(this, c);
        if (area >= 0) i.putExtra("back", TITLES[area]);
        return i;
    }

    private void intro(String s) {
        TextView t = U.text(this, s, 14, U.MUTED, false);
        t.setPadding(0, 0, 0, U.dp(this, 6));
        body.addView(t);
    }

    private void note(String s) {
        TextView t = U.text(this, s, 13, U.MUTED, false);
        t.setPadding(0, U.dp(this, 14), 0, U.dp(this, 12));
        body.addView(t);
    }

    private TextView button(String s) {
        TextView t = U.text(this, s, 15, U.INK, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(U.dp(this, 48));
        PressB.pill(t, U.BG, U.LINE, 24); // 0.21: pillola con il bordo tenue, anello di 8 dp
        return t;
    }

    private void nav(String title, String sub, final Intent target, String right) {
        row(title, sub, right, new Runnable() { public void run() { startActivity(target); } });
    }

    private void row(String title, String sub, String right, final Runnable action) {
        LinearLayout r = U.row(this);
        r.setMinimumHeight(U.dp(this, 64));
        r.setPadding(0, U.dp(this, 8), 0, U.dp(this, 8));
        PressB.row(r); // 0.21: fondo più scuro, almeno 240 ms (nel tema scuro, il colore pieno dei riquadri)
        LinearLayout tx = U.col(this);
        tx.addView(U.text(this, title, 16, U.INK, false));
        if (sub != null) tx.addView(U.text(this, sub, 13, U.MUTED, false));
        r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (right != null) {
            TextView rt = U.text(this, right, "›".equals(right) ? 20 : 15, "›".equals(right) || "Attiva ›".equals(right) ? U.INK : U.MUTED, "Attiva ›".equals(right));
            rt.setPadding(U.dp(this, 8), 0, 0, 0);
            r.addView(rt);
        }
        r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { action.run(); } });
        body.addView(r, U.full());
        body.addView(U.divider(this));
    }

    /** 0.12: interruttore legato a una preferenza, con «Annulla». */
    private void prefToggle(String title, String sub, final String key, boolean def, final String onMsg, final String offMsg) {
        final boolean on = U.prefs(this).getBoolean(key, def);
        toggle(title, sub, on, "pref:" + key, new Runnable() {
            public void run() {
                Changes.track(SettingsActivity.this, on ? offMsg : onMsg, new String[]{key},
                        new Runnable() { public void run() { U.prefs(SettingsActivity.this).edit().putBoolean(key, !on).commit(); } },
                        new Runnable() { public void run() { render(); } });
            }
        });
    }

    /** 0.12: durata della pausa, 4, 6 o 8 secondi. */
    private void pauseLength() {
        LinearLayout r = U.col(this);
        r.setPadding(0, U.dp(this, 10), 0, U.dp(this, 12));
        r.addView(U.text(this, "Durata della pausa", 16, U.INK, false));
        r.addView(U.text(this, "Prima di aprire un'app con la pausa. Pausa e scopo non ha attesa: chiede solo per cosa", 12, U.MUTED, false));
        final int cur = U.pauseSeconds(this);
        LinearLayout seg = U.row(this);
        seg.setBackground(U.box(this, U.SURFACE, U.BORDER, 10));
        seg.setPadding(U.dp(this, 1), U.dp(this, 1), U.dp(this, 1), U.dp(this, 1));
        for (final int sec : new int[]{4, 6, 8}) {
            boolean sel = sec == cur;
            TextView t = U.text(this, sec + " secondi", 13, sel ? U.BG : U.INK, false);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(U.dp(this, 44));
            if (sel) Press.chipOn(t, 9); else PressB.tabOff(t, U.SURFACE, 9); // 0.21: la scheda non scelta resta leggibile (testo INK) anche sullo scuro
            t.setContentDescription("Pausa di " + sec + " secondi" + (sel ? ", scelta" : ""));
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (sec == cur) return;
                    Changes.track(SettingsActivity.this, "Pausa di " + sec + " secondi", new String[]{U.K_PAUSE_SEC},
                            new Runnable() { public void run() { U.prefs(SettingsActivity.this).edit().putInt(U.K_PAUSE_SEC, sec).commit(); } },
                            new Runnable() { public void run() { render(); } });
                }
            });
            seg.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        LinearLayout.LayoutParams lp = U.full();
        lp.topMargin = U.dp(this, 8);
        r.addView(seg, lp);
        body.addView(r, U.full());
        body.addView(U.divider(this));
    }

    private void toggle(String title, String sub, boolean on, final Runnable action) { toggle(title, sub, on, title, action); }

    /** key: chi è questo interruttore, uguale prima e dopo il cambio (serve a far scorrere il pallino quando la pagina si ridisegna). */
    private void toggle(String title, String sub, boolean on, String key, final Runnable action) {
        LinearLayout r = U.row(this);
        r.setMinimumHeight(U.dp(this, 64));
        r.setPadding(0, U.dp(this, 8), 0, U.dp(this, 8));
        LinearLayout tx = U.col(this);
        tx.addView(U.text(this, title, 16, U.INK, on)); // 0.16: grassetto se acceso
        if (sub != null) tx.addView(U.text(this, sub, 13, U.MUTED, false));
        r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(U.toggle(this, on, "settings:" + key), new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 26)));
        r.setContentDescription(title + ", interruttore, " + (on ? "acceso" : "spento"));
        PressB.row(r); // 0.20: tutta la riga prende lo stato premuto; 0.21: almeno 240 ms
        r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Motion.scatto(v); action.run(); } }); // lo stesso scatto, acceso o spento
        body.addView(r, U.full());
        body.addView(U.divider(this));
    }

    private void perm(String title, String sub, boolean done, final Runnable action) {
        row(title, sub, done ? "✓" : "Attiva ›", done ? new Runnable() { public void run() {} } : action);
    }
}
