package it.essenziale.launcher;

import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Impostazioni (0.6): cinque aree invece di nove voci, più Modifiche recenti.
 * La stessa schermata mostra l'elenco delle aree o, con l'extra "area", una sola area.
 */
public class SettingsActivity extends BaseActivity {

    static final int MAIN = -1, NOTIF = 0, APPS = 1, TIME = 2, DAY = 3, SETUP = 4;
    static final String[] TITLES = {"Notifiche", "App", "Tempo e uso", "Home e giornata", "Attivazione"};

    private int area = MAIN;
    private LinearLayout body;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        area = getIntent().getIntExtra("area", MAIN);
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
            case NOTIF: notif(); break;
            case APPS: apps(); break;
            case TIME: time(); break;
            case DAY: day(); break;
            case SETUP: setup(); break;
            default: main();
        }
    }

    // ---------- Elenco delle aree ----------

    private void main() {
        int people = Onboarding.alwaysCount(this);
        open("Notifiche", "Persone e gruppi (" + people + (people == 1 ? " passa" : " passano") + " sempre), cosa aspetta in Notifiche", NOTIF);
        open("App", "A portata, pausa prima di aprire, contenitori", APPS);
        open("Tempo e uso", "Tempo dall'ultimo sblocco, pillola, durata della pausa, sblocchi senza scopo, Di notte", TIME);
        open("Home e giornata", "Aspetto, calendari, sveglia, comandi vocali, schermata di blocco", DAY);
        int todo = Onboarding.missingPermissions(this);
        open("Attivazione", todo == 0 ? "Permessi · tutto attivo" : "Permessi · " + todo + " da completare", SETUP);
        View gap = new View(this);
        body.addView(gap, new LinearLayout.LayoutParams(1, U.dp(this, 20)));
        nav("Modifiche recenti", "Le ultime scelte, per tornare indietro", new Intent(this, ChangesActivity.class), "›");
        nav("Segnala un problema", "Qualcosa non va? Raccontacelo", new Intent(this, BugActivity.class), "›");
        note("Cinque aree: ogni riga dice cosa c'è dentro. Dopo ogni modifica c'è «Annulla».");
    }

    private void open(String title, String sub, int a) {
        Intent i = new Intent(this, SettingsActivity.class);
        i.putExtra("area", a);
        i.putExtra("back", "Impostazioni");
        nav(title, sub, i, "›");
    }

    // ---------- Notifiche ----------

    private void notif() {
        intro("Cosa ti raggiunge subito e cosa aspetta in Notifiche.");
        body.addView(U.label(this, "Chi passa"));
        int people = Onboarding.alwaysCount(this);
        // 0.7: era Conversazioni. Tocchi per scrivere, ⋯ per cambiare come passa
        nav("Persone e gruppi", "Chi passa sempre, chi aspetta, chi ti ha scritto · tocca per scrivere", peopleIntent(this, "Notifiche"), "›");
        nav("Scegli dalla rubrica chi passa sempre", "Anche con il silenzio · " + people + (people == 1 ? " persona" : " persone"), to(PeopleActivity.class), "›");
        nav("Gruppi", "App per app: quali passano sempre", to(GroupsActivity.class), "›");
        final boolean open = U.prefs(this).getBoolean(U.K_OPEN_ON, true);
        toggle("Conversazioni aperte", "Passano per 12 ore dopo la tua risposta", open, new Runnable() {
            public void run() {
                Changes.track(SettingsActivity.this, open ? "Conversazioni aperte spente" : "Conversazioni aperte attive", new String[]{U.K_OPEN_ON},
                        new Runnable() { public void run() { U.prefs(SettingsActivity.this).edit().putBoolean(U.K_OPEN_ON, !open).commit(); } },
                        new Runnable() { public void run() { render(); } });
            }
        });
        body.addView(U.label(this, "Cosa aspetta"));
        int waiting = U.rows(U.cfg(this, U.K_WAITING_APPS, U.DEF_WAITING_APPS)).size();
        Intent w = to(ContainerActivity.class);
        w.putExtra("tab", ContainerActivity.TAB_WAITING);
        nav("App che aspettano in Notifiche", "Contate in «Per te» · " + waiting + " app", w, "›");
        body.addView(U.label(this, "Barra di stato"));
        int sticky = Sticky.recent(this).size();
        nav("Icone fisse nella barra", sticky == 0 ? "Nessuna negli ultimi 7 giorni" : sticky + (sticky == 1 ? " app" : " app") + " negli ultimi 7 giorni · rendile silenziose", to(StickyActivity.class), "›");
        note("Tutto il resto finisce in Altro, senza suoni, e sparisce dalla barra di stato. Persone e gruppi si cambiano anche da Notifiche, tenendo premuto.");
    }

    // ---------- App ----------

    private void apps() {
        intro("Come ritrovi le app e quanto attrito c'è prima di aprirle.");
        int shop = U.rows(U.cfg(this, U.K_SHOP, U.DEF_SHOP)).size(), money = U.rows(U.cfg(this, U.K_MONEY, U.DEF_MONEY)).size(),
                conv = U.rows(U.prefs(this).getString(U.K_RW_APPS, "")).size();
        nav("A portata", "Persone e gruppi, " + SearchActivity.portata(this).size() + " app · cosa vedi appena tocchi la ricerca", to(PortataActivity.class), "›");
        nav("Pausa prima di aprire", U.pausedPkgs(this).size() + " app · " + Scope.scopePkgs(this).size() + " con pausa e scopo", to(PauseActivity.class), "›");
        prefToggle("Apri subito la tastiera", "Nella ricerca. Spento: vedi prima recenti, app a portata e persone", U.K_KEYBOARD, false,
                "Tastiera aperta subito nella ricerca", "Tastiera chiusa all'apertura della ricerca");
        // 0.7: i contenitori non sono più in home; restano qui, in prova, per aprire e per modificare
        body.addView(U.label(this, "Contenitori · in prova"));
        Intent sh = to(ListActivity.class); sh.putExtra(ListActivity.EXTRA_MODE, ListActivity.MODE_SHOP);
        nav("Shop", shop + " app · tocca per aprirle", sh, "›");
        Intent mv = to(ListActivity.class); mv.putExtra(ListActivity.EXTRA_MODE, ListActivity.MODE_MONEY);
        nav("Movimenti", money + " app · tocca per aprirle", mv, "›");
        nav("Modifica i contenitori", "Shop " + shop + " · Movimenti " + money + " · Conversazioni " + conv + " app", to(ContainerActivity.class), "›");
        note("Tutte le app si aprono anche scrivendone il nome nella ricerca in home.");
    }

    // ---------- Tempo e uso ----------

    private void time() {
        intro("Segnali per accorgerti di come usi il telefono. Niente blocchi.");
        nav("Tempo dall'ultimo sblocco", Session.on(this) ? "Te lo fa notare dopo " + Session.threshold(this) + " min" : "Spento", to(SessionActivity.class), "›");
        // 0.9: la pillola sopra le altre app, spenta finché non la accendi
        final boolean pill = Pill.on(this);
        final boolean draw = Pill.canDraw(this), usage = Pill.usageGranted(this);
        toggle("Sopra le altre app", pill && !draw ? "Manca il permesso qui sotto" : "La pillola «25 min», solo oltre la soglia · mai sopra minus", pill, new Runnable() {
            public void run() {
                Changes.track(SettingsActivity.this, pill ? "Pillola del tempo spenta" : "Pillola del tempo accesa", new String[]{Pill.K_ON},
                        new Runnable() { public void run() { U.prefs(SettingsActivity.this).edit().putBoolean(Pill.K_ON, !pill).commit(); } },
                        new Runnable() { public void run() { render(); if (!pill && !Pill.canDraw(SettingsActivity.this)) Pill.openOverlaySettings(SettingsActivity.this); } });
            }
        });
        row("Mostra sopra altre app", draw ? "Permesso attivo · serve alla pillola" : "Permesso di Android · serve alla pillola", draw ? "✓" : "Attiva ›", new Runnable() {
            public void run() { Pill.openOverlaySettings(SettingsActivity.this); }
        });
        row("Dati di utilizzo", usage ? "Attivo · la pillola dice quale app pesa di più" : "Facoltativo · per dire quale app pesa di più", usage ? "✓" : "Attiva ›", new Runnable() {
            public void run() { Pill.openUsageSettings(SettingsActivity.this); }
        });
        pauseLength();
        prefToggle("Sblocchi senza scopo", "Al terzo sblocco in 10 minuti senza aprire niente, la home ti chiede per cosa l'hai preso. Al massimo 3 volte al giorno, mai di notte",
                Sblocco.K_ON, false, "Domanda sugli sblocchi senza scopo accesa", "Domanda sugli sblocchi senza scopo spenta");
        nav("Di notte", Night.on(this) ? (Night.active(this) ? "Attivo · adesso è notte" : "Attivo") + " · " + Night.alternative(this) : "Spento · se ti svegli, solo l'essenziale", to(NightSettingsActivity.class), "›");
        body.addView(U.label(this, "Benessere digitale"));
        row("Hai un timer su minus?", "Android conta anche la home: toglilo da lì", "↗", new Runnable() {
            public void run() { Session.openWellbeing(SettingsActivity.this); }
        });
        note("minus vede quanto tempo passi in ogni app, mai cosa fai dentro. Nessun dato d'uso esce dal telefono.");
    }

    // ---------- Home e giornata ----------

    private void day() {
        String th = U.prefs(this).getString(U.K_THEME, U.THEME_LIGHT);
        nav("Aspetto", "Tema " + th + " · testo " + (U.prefs(this).getBoolean(U.K_TEXT_BIG, false) ? "grande" : "normale"), to(AspettoActivity.class), "›");
        int shown = U.shownCalendars(this).size();
        String def = U.prefs(this).getString("last_cal_name", null);
        nav("Calendari", (shown > 0 ? shown + " mostrati" : "Tutti quelli sincronizzati") + (def != null ? " · nuovi impegni in " + def : ""), to(CalendarsActivity.class), "›");
        String al = U.nextAlarm(this);
        row("Sveglia", (al != null ? "Prossima: " + al + " · " : "") + "si imposta nell'Orologio", "↗", new Runnable() {
            public void run() { if (!U.openAlarms(SettingsActivity.this)) U.toast(SettingsActivity.this, "Non trovo l'app Orologio"); }
        });
        nav("Comandi vocali", "Cosa puoi dire dalla home", to(VoiceHelpActivity.class), "›");
        final boolean lockOn = U.prefs(this).getBoolean(U.K_LOCK, true);
        toggle("Schermata di blocco minus", "Ora, prossimi impegni e da fare senza sbloccare", lockOn, new Runnable() {
            public void run() {
                Changes.track(SettingsActivity.this, lockOn ? "Schermata di blocco minus spenta" : "Schermata di blocco minus attiva", new String[]{U.K_LOCK},
                        new Runnable() { public void run() { U.prefs(SettingsActivity.this).edit().putBoolean(U.K_LOCK, !lockOn).commit(); } },
                        new Runnable() { public void run() { render(); } });
            }
        });
        row("App Home predefinita", Onboarding.isDefaultHome(this) ? "minus" : "Non è minus", "↗", new Runnable() {
            public void run() { Onboarding.openHomeSettings(SettingsActivity.this); }
        });
        note("Cosa compare in home e quando (domani dalle 17, la sveglia dalle 19) non si imposta: è parte di minus.");
    }

    // ---------- Attivazione ----------

    private void setup() {
        intro("Cosa serve a minus e a cosa. Ogni permesso si può togliere da Android.");
        perm("App Home predefinita", null, Onboarding.isDefaultHome(this), new Runnable() {
            public void run() { Onboarding.openHomeSettings(SettingsActivity.this); }
        });
        perm("Accesso alle notifiche", "Per dividere Per te e Altro", U.listenerEnabled(this), new Runnable() {
            public void run() { Onboarding.openListenerSettings(SettingsActivity.this); }
        });
        perm("Contatti", "Per scegliere chi passa sempre", Onboarding.hasContacts(this), new Runnable() {
            public void run() { requestPermissions(new String[]{android.Manifest.permission.READ_CONTACTS}, 2); }
        });
        perm("Calendario", "Per mostrare e aggiungere impegni", U.canWriteCalendar(this), new Runnable() {
            public void run() { requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR}, 1); }
        });
        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        final boolean policy = nm.isNotificationPolicyAccessGranted();
        final boolean silenceOn = U.prefs(this).getBoolean(U.K_SILENCE, false) && policy;
        row("Silenzio (Non disturbare)", !policy ? "Per far passare le persone scelte col silenzio" : silenceOn ? "Attivo · tocca per spegnerlo" : "Spento · tocca per attivarlo",
                !policy ? "Attiva ›" : silenceOn ? "✓" : "Spento", new Runnable() {
            public void run() {
                if (!policy) { Onboarding.openPolicySettings(SettingsActivity.this); return; }
                boolean next = !silenceOn;
                if (FilterService.applySilence(SettingsActivity.this, next)) U.prefs(SettingsActivity.this).edit().putBoolean(U.K_SILENCE, next).apply();
                render();
            }
        });
        note("Con il silenzio attivo squillano solo le chiamate dei contatti preferiti (stella in rubrica) e le chiamate ripetute.");

        row("Cosa vede minus", "Le ultime notifiche di chat arrivate e dove sono finite", "›", new Runnable() {
            public void run() { showLog(); }
        });

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
        body.addView(again, lp);

        TextView reset = button("Ripristina le liste di esempio");
        reset.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Changes.track(SettingsActivity.this, "Liste di esempio ripristinate",
                        new String[]{U.K_CONTACTS, U.K_SHOP, U.K_MONEY, U.K_WAITING_APPS, U.K_GROUPS, U.K_RW_APPS, U.K_PAUSE, U.K_GROUP_DEST},
                        new Runnable() { public void run() {
                            U.prefs(SettingsActivity.this).edit().remove(U.K_CONTACTS).remove(U.K_SHOP).remove(U.K_MONEY)
                                    .remove(U.K_WAITING_APPS).remove(U.K_GROUPS).remove(U.K_RW_APPS).remove(U.K_PAUSE).remove(U.K_GROUP_DEST).commit();
                        } },
                        new Runnable() { public void run() { render(); } });
            }
        });
        LinearLayout.LayoutParams lp2 = U.full();
        lp2.topMargin = U.dp(this, 10);
        lp2.bottomMargin = U.dp(this, 90);
        body.addView(reset, lp2);
    }

    /** 0.6.2: per capire se una chat arriva a Essenziale (se non c'è qui, Android o l'app non la notificano). */
    private void showLog() {
        StringBuilder b = new StringBuilder();
        for (String[] r : U.rows(U.prefs(this).getString(FilterService.K_LOG, ""))) {
            if (r.length < 5) continue;
            long t = 0;
            try { t = Long.parseLong(r[0]); } catch (Exception ignored) {}
            b.append(Changes.when(t)).append("  ·  ").append(r[1]).append("\n").append(r[2]).append(" (").append(r[3]).append(") → ").append(r[4]).append("\n\n");
        }
        if (b.length() == 0) b.append(U.listenerEnabled(this) ? "Ancora nessuna notifica di chat da quando hai aggiornato." : "L'accesso alle notifiche è spento: minus non vede niente.");
        b.append("Se una chat non compare qui, la notifica non arriva proprio: è disattivata o silenziata in WhatsApp o Telegram, o nelle impostazioni di Android.");
        new android.app.AlertDialog.Builder(this).setTitle("Cosa vede minus").setMessage(b.toString()).setPositiveButton("Ok", null).show();
    }

    /** 0.7: la lista di persone e gruppi (era Conversazioni), aperta da Impostazioni o dalla ricerca. */
    static Intent peopleIntent(android.content.Context c, String back) {
        Intent i = new Intent(c, ListActivity.class);
        i.putExtra(ListActivity.EXTRA_MODE, ListActivity.MODE_CONTACTS);
        i.putExtra("title", "Persone e gruppi");
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
        t.setBackground(U.box(this, U.BG, U.LINE, 24));
        return t;
    }

    private void nav(String title, String sub, final Intent target, String right) {
        row(title, sub, right, new Runnable() { public void run() { startActivity(target); } });
    }

    private void row(String title, String sub, String right, final Runnable action) {
        LinearLayout r = U.row(this);
        r.setMinimumHeight(U.dp(this, 64));
        r.setPadding(0, U.dp(this, 8), 0, U.dp(this, 8));
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
        toggle(title, sub, on, new Runnable() {
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
        r.addView(U.text(this, "Prima di aprire un'app con la pausa. Pausa e scopo resta di 4 secondi", 13, U.MUTED, false));
        final int cur = U.pauseSeconds(this);
        LinearLayout seg = U.row(this);
        seg.setBackground(U.box(this, U.SURFACE, U.BORDER, 10));
        seg.setPadding(U.dp(this, 1), U.dp(this, 1), U.dp(this, 1), U.dp(this, 1));
        for (final int sec : new int[]{4, 6, 8}) {
            boolean sel = sec == cur;
            TextView t = U.text(this, sec + " secondi", 13, sel ? U.BG : U.INK, false);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(U.dp(this, 44));
            if (sel) t.setBackground(U.box(this, U.INK, 0, 9));
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

    private void toggle(String title, String sub, boolean on, final Runnable action) {
        LinearLayout r = U.row(this);
        r.setMinimumHeight(U.dp(this, 64));
        r.setPadding(0, U.dp(this, 8), 0, U.dp(this, 8));
        LinearLayout tx = U.col(this);
        tx.addView(U.text(this, title, 16, U.INK, false));
        if (sub != null) tx.addView(U.text(this, sub, 13, U.MUTED, false));
        r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(U.toggle(this, on), new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 26)));
        r.setContentDescription(title + (on ? ", attivo" : ", spento"));
        r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { action.run(); } });
        body.addView(r, U.full());
        body.addView(U.divider(this));
    }

    private void perm(String title, String sub, boolean done, final Runnable action) {
        row(title, sub, done ? "✓" : "Attiva ›", done ? new Runnable() { public void run() {} } : action);
    }
}
