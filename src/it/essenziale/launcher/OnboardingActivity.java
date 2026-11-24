package it.essenziale.launcher;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Configurazione guidata per obiettivi (0.14, wireframe 180–194). Prima «Cosa vuoi cambiare?»,
 * poi la configurazione (home, giornata, a portata) e un passo per ogni obiettivo scelto. I permessi
 * di Android si chiedono toccando Avanti, solo quando servono; si torna da soli. Ciò che si salta
 * o si nega finisce in «Da completare».
 *
 * Extra: "goal" (dalle Impostazioni: solo i passi di quell'obiettivo, poi lo accende),
 * "only" (un solo passo, per esempio Quando dormi), "again" (rifai tutto).
 */
public class OnboardingActivity extends PageActivity {

    static final int GOALS = 0, HOME = 1, DAY = 2, PORTATA = 3, NOTIF = 4, UNLOCK = 5, DOOM_APPS = 6, DOOM_HOW = 7,
            SLEEP = 8, TRIAL = 9, ALT = 10, READY = 11,
            // 0.24 (Evolutiva 018): «Meno notifiche» guidato, una scelta per schermata, poi il riepilogo NOTIF
            N_PEOPLE = 12, N_SERV = 13, N_GROUPS = 14;

    private static final int REQ_CAL = 4, REQ_CONTACTS = 3, REQ_PICK = 51;

    private int[] path = {GOALS};
    private int idx = 0;
    private String onlyGoal;        // dalle Impostazioni: «Accendi»
    private boolean onlyStep;       // dalle Impostazioni: un solo passo
    private int sub = -1;           // 0.24: un selettore aperto dal riepilogo o dalla prova (tipo di Scegli), sopra il passo
    private final GuidaPerm guide = new GuidaPerm(); // 0.24: la guida «Android blocca questo permesso»
    private Scegli scegli;          // il selettore sullo schermo, per la rubrica di Android
    private boolean splash = false, hadSplash = false; // 0.15 · 179: logo e promessa, solo al primo avvio
    private LinearLayout root, body, foot;
    private final java.util.Set<String> chosenGoals = new java.util.LinkedHashSet<String>();

    // stato dei passi
    private boolean askedContacts = false, askedCal = false;
    private List<String[]> portataCand;   // {pacchetto, nome, tipo}
    private List<String[]> portataSel;    // {pacchetto, nome}
    private boolean sleepPrepared = false;
    static final String K_CALLS = "onb_chiamate"; // 0.24: «Chiamate» nel passo Gruppi: squillano solo chi conta (acceso di default)

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        running = true;
        Scegli.resetSession();
        onlyGoal = getIntent().getStringExtra("goal");
        int only = getIntent().getIntExtra("only", -1);
        if (onlyGoal != null) {
            chosenGoals.add(onlyGoal);
            path = goalSteps(onlyGoal, true);
        } else if (only >= 0) {
            onlyStep = true;
            path = new int[]{only};
        } else {
            // rifatta: le scelte di prima; la prima volta, come nel wireframe 180
            if (U.prefs(this).contains(Onboarding.K_GOALS)) { for (String g : Goals.ALL) if (Goals.on(this, g)) chosenGoals.add(g); }
            else { chosenGoals.add(Goals.NOTIF); chosenGoals.add(Goals.SLEEP); splash = hadSplash = true; }
        }
        root = U.col(this);
        root.setBackgroundColor(U.BG);
        root.setPadding(0, U.dp(this, 16), 0, 0);
        ScrollView sv = new ScrollView(this);
        scroll = sv;
        body = U.col(this);
        body.setPadding(U.dp(this, 20), 0, U.dp(this, 20), U.dp(this, 12));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        foot = U.col(this);
        // 0.24: il piede ha il fondo della pagina e un filo in alto: ciò che scorre non si vede mai sotto i pulsanti
        foot.setBackgroundColor(U.BG);
        foot.setPadding(U.dp(this, 20), 0, U.dp(this, 20), U.dp(this, 12));
        foot.setClipChildren(false);
        foot.setClipToPadding(false);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.addView(foot, U.full());
        setContentView(root);
        prepare(); // dalle Impostazioni si parte già dal passo dell'obiettivo
    }

    @Override
    protected void onResume() {
        super.onResume();
        watching = null; // 0.13.5: sei tornata, non serve più riportarti qui
        if (guide.visible()) {
            // 0.24 (Evolutiva 018): la guida si accorge da sola del ritorno da Android; la catena NON riparte da qui
            int ev = guide.resume(U.listenerEnabled(this));
            if (ev == GuidaPerm.EV_OK) watcher.postDelayed(advanceGuide, GuidaPerm.ADVANCE_MS); // «Fatto, si continua» e dopo circa 1 s si va avanti
            render();
            return;
        }
        if (chain != null) { runChain(); return; } // 0.14: il permesso successivo, o il passo dopo
        render(); // al ritorno dalle impostazioni di Android il passo mostra lo stato aggiornato
    }

    /** Passato circa un secondo dal via libera: la guida si chiude e la catena prosegue (Non disturbare), o si torna al passo. */
    private final Runnable advanceGuide = new Runnable() {
        public void run() {
            if (!guide.finish()) return;
            if (chain != null) runChain(); else render();
        }
    };

    /** 0.24: «Salta» e «Indietro» chiudono davvero la guida: niente istruzioni sotto il passo dopo, niente catena rimasta viva. */
    private void clearGuide() {
        guide.clear();
        chain = null;
        chainDone = null;
        watching = null;
        watcher.removeCallbacksAndMessages(null);
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
        if (req == REQ_PICK && res == RESULT_OK && data != null && data.getData() != null && scegli != null) scegli.picked(data.getData());
        if (req == Onboarding.REQ_HOME && Onboarding.isDefaultHome(this)) { askCalendar(); return; }
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

    // ---------- 0.14: i permessi chiesti con Avanti, uno dopo l'altro ----------

    static final class Perm {
        final String key; final Check ok; final Runnable open;
        Perm(String key, Check ok, Runnable open) { this.key = key; this.ok = ok; this.open = open; }
    }

    private List<Perm> chain;
    private Runnable chainDone;
    private final Set<String> asked = new java.util.HashSet<String>();

    /** Chiede, uno alla volta, i permessi che mancano; poi va avanti. Chi torna senza concederlo prosegue. */
    private void askThen(List<Perm> perms, Runnable then) {
        chain = perms;
        chainDone = then;
        runChain();
    }

    private void runChain() {
        if (chain == null) return;
        for (final Perm p : chain) {
            boolean ok = false;
            try { ok = p.ok.ok(); } catch (Exception ignored) {}
            // 0.24: tornata senza il permesso delle notifiche, con l'app da APK (interruttore grigio): resta la guida, e la catena
            // NON passa a Non disturbare finché le notifiche non sono accese o la guida non è chiusa («Salta», «Indietro»)
            int what = GuidaPerm.chain(ok, asked.contains(p.key), "notifiche".equals(p.key) && maybeRestricted(), false);
            if (what == GuidaPerm.NEXT) continue;
            if (what == GuidaPerm.GUIDE) {
                if (!guide.visible()) guide.open();
                showStep();
                return;
            }
            asked.add(p.key);
            openAndWatch(p.open, p.ok);
            return;
        }
        Runnable r = chainDone;
        chain = null;
        chainDone = null;
        if (r != null) r.run(); else render();
    }

    private Perm listenerPerm() {
        return new Perm("notifiche", new Check() { public boolean ok() { return U.listenerEnabled(OnboardingActivity.this); } },
                new Runnable() { public void run() { Onboarding.openListenerSettings(OnboardingActivity.this); } });
    }

    private Perm policyPerm() {
        return new Perm("silenzio", new Check() { public boolean ok() { return Onboarding.policy(OnboardingActivity.this); } },
                new Runnable() { public void run() { Onboarding.openPolicySettings(OnboardingActivity.this); } });
    }

    private Perm usagePerm() {
        return new Perm("utilizzo", new Check() { public boolean ok() { return Pill.usageGranted(OnboardingActivity.this); } },
                new Runnable() { public void run() { Pill.openUsageSettings(OnboardingActivity.this); } });
    }

    private Perm overlayPerm() {
        return new Perm("sopra", new Check() { public boolean ok() { return Pill.canDraw(OnboardingActivity.this); } },
                new Runnable() { public void run() { Pill.openOverlaySettings(OnboardingActivity.this); } });
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
        if (code == REQ_CAL && step() == HOME) { next(); return; }
        render();
    }

    @Override
    public void onBackPressed() { back(); }

    private int step() { return path[idx]; }

    private ScrollView scroll;
    private TextView stepTitle; // il titolo del passo mostrato: lì va il focus di TalkBack quando il passo cambia

    /**
     * 0.20 (Evolutiva 016): un passo nuovo (Avanti, Indietro, Salta) parte sempre dall'alto, senza animazione, e il
     * focus di TalkBack va al titolo del passo. Gli aggiornamenti dello stesso passo (render) non spostano niente.
     */
    private void showStep() {
        if (scroll != null) scroll.scrollTo(0, 0);
        render();
        if (scroll != null) scroll.scrollTo(0, 0); // render rifà il contenuto: ancora in cima
        final View f = stepTitle != null ? stepTitle : body;
        f.post(new Runnable() {
            public void run() {
                if (scroll != null) scroll.scrollTo(0, 0);
                f.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);
                android.view.accessibility.AccessibilityManager am = (android.view.accessibility.AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
                if (am != null && am.isEnabled()) f.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
            }
        });
    }

    private void next() {
        sub = -1;
        if (step() == N_PEOPLE) Stars.starAlways(this); // 0.24: chi conta prende la stella in rubrica, come prima con «Fatto»
        if (onlyStep) { finish(); return; }
        if (idx < path.length - 1) { idx++; prepare(); showStep(); }
        else finish();
    }

    private void back() {
        // 0.24: la guida si chiude del tutto (niente catena che riparte e apre Non disturbare)
        if (guide.visible()) { clearGuide(); showStep(); return; }
        if (sub >= 0) { closeSub(); return; }
        if (idx == 0 && hadSplash && !splash) { splash = true; showStep(); return; } // da 180 si torna allo splash
        if (idx == 0) { finish(); return; }
        if (step() == N_PEOPLE) Stars.starAlways(this);
        idx--;
        showStep();
    }

    // ---------- Percorso ----------

    private int[] goalSteps(String g, boolean alone) {
        if (Goals.NOTIF.equals(g)) return new int[]{N_PEOPLE, N_SERV, N_GROUPS, NOTIF}; // 0.24: guidato, poi il riepilogo
        if (Goals.DOOM.equals(g)) return new int[]{DOOM_APPS, DOOM_HOW, UNLOCK}; // 0.18 (Evolutiva 010): 187 dopo 189
        if (Goals.SLEEP.equals(g)) return new int[]{SLEEP, TRIAL};
        return new int[0];
    }

    private int[] buildPath() {
        List<Integer> l = new ArrayList<Integer>();
        l.add(GOALS); l.add(HOME); l.add(DAY); l.add(PORTATA);
        for (String g : Goals.ALL) if (chosenGoals.contains(g)) for (int s : goalSteps(g, false)) l.add(s);
        if (!chosenGoals.isEmpty()) l.add(ALT); // 193: una volta, alla fine dell'ultimo obiettivo
        l.add(READY);
        int[] out = new int[l.size()];
        for (int i = 0; i < out.length; i++) out[i] = l.get(i);
        return out;
    }

    /** Il gruppo del passo: 0 configurazione, poi un numero per obiettivo. */
    private String groupOf(int s) {
        if (s == HOME || s == DAY || s == PORTATA) return "Configurazione";
        if (s == NOTIF || s == N_PEOPLE || s == N_SERV || s == N_GROUPS) return Goals.NAMES[0];
        if (s == DOOM_APPS || s == DOOM_HOW || s == UNLOCK) return Goals.NAMES[1];
        if (s == SLEEP || s == TRIAL) return Goals.NAMES[2];
        return null;
    }

    /**
     * Prima di mostrare un passo, ciò che serve. Le scelte proposte non sono più accese qui in silenzio: le propone il selettore
     * ({@link Scegli}), con il motivo sotto ogni nome, già scelte e con un tocco per toglierle.
     */
    private void prepare() {
        int s = step();
        if (s == N_PEOPLE) askContactsOnce(); // il permesso Contatti si chiede quando si apre «Chi conta»
        if (s == TRIAL && !sleepPrepared) {
            sleepPrepared = true;
            new Scegli(this, Scegli.NIGHT, true, null); // le proposte per la notte (social e video) si preparano qui, e si possono cambiare
        }
    }

    /** Il permesso Contatti (lettura e modifica, per la stella), una volta, aprendo «Chi conta». */
    private void askContactsOnce() {
        if (askedContacts) return;
        if (!Onboarding.hasContacts(this)) {
            askedContacts = true;
            requestPermissions(new String[]{android.Manifest.permission.READ_CONTACTS, android.Manifest.permission.WRITE_CONTACTS}, REQ_CONTACTS);
        } else if (!Stars.canWrite(this)) {
            askedContacts = true;
            requestPermissions(new String[]{android.Manifest.permission.WRITE_CONTACTS}, REQ_CONTACTS);
        }
    }

    static boolean isVideo(U.AppInfo a) {
        return Proposte.isVideo(a.label, a.pkg, a.category);
    }

    // ---------------------------------------------------------------------

    private void render() {
        stepTitle = null;
        body.removeAllViews();
        foot.removeAllViews();
        scegli = null;
        footDivider();
        int s = step();
        if (splash) { splash(); return; }
        if (guide.visible()) { header(); restrictedGuide(); return; }
        if (s != GOALS && s != READY && s != ALT) header();
        if (sub >= 0) { subPicker(); return; } // un selettore aperto dal riepilogo o dalla prova
        switch (s) {
            case GOALS: goals(); break;
            case HOME: home(); break;
            case DAY: day(); break;
            case PORTATA: portata(); break;
            case N_PEOPLE: guided(Scegli.PEOPLE, 1, "Chi conta", "Passano sempre, anche col silenzio."); break;
            case N_SERV: guided(Scegli.SERVICES, 2, "Servizi", "Banca, SPID, app IO: codici e accessi passano sempre."); break;
            case N_GROUPS: guided(Scegli.GROUPS, 3, "Gruppi che passano sempre", "Quelli che hai visto scrivere."); break;
            case NOTIF: notif(); break;
            case UNLOCK: unlock(); break;
            case DOOM_APPS: doomApps(); break;
            case DOOM_HOW: doomHow(); break;
            case SLEEP: sleep(); break;
            case TRIAL: trial(); break;
            case ALT: alternative(); break;
            default: ready();
        }
    }

    /** «Salta»: sempre in alto a destra, su ogni schermata del primo avvio (0.24). */
    private TextView skipLink() {
        TextView skip = U.text(this, "Salta", 13, U.MUTED, false);
        skip.setPaintFlags(skip.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        skip.setGravity(Gravity.CENTER);
        skip.setMinHeight(U.dp(this, 48));
        skip.setMinWidth(U.dp(this, 64));
        skip.setPadding(U.dp(this, 12), U.dp(this, 8), U.dp(this, 12), U.dp(this, 8));
        skip.setContentDescription("Salta");
        PressB.link(skip); // 0.21: «Salta» è un link: anello di 6 dp
        return skip;
    }

    private void header() {
        String g = groupOf(step());
        List<String> groups = new ArrayList<String>();
        for (int st : path) { String x = groupOf(st); if (x != null && !groups.contains(x)) groups.add(x); }
        int pos = Math.max(1, groups.indexOf(g) + 1), total = Math.max(1, groups.size());
        LinearLayout h = U.row(this);
        h.setMinimumHeight(U.dp(this, 40));
        h.addView(smallLogo(), new LinearLayout.LayoutParams(U.dp(this, 18), U.dp(this, 18)));
        TextView t = U.text(this, "", 13, U.MUTED, false);
        t.setPadding(U.dp(this, 8), 0, 0, 0);
        // 0.15 · 181: «Passo 1 di 3 · Configurazione», con il logo piccolo a sinistra per tutto l'onboarding
        t.setText(android.text.Html.fromHtml((onlyStep ? "" : "Passo " + pos + " di " + total + " · ") + "<b>" + (g == null ? "" : g) + "</b>"));
        h.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!onlyStep) {
            TextView skip = skipLink();
            // 0.13.6: «Salta» tiene quello che il passo ha già compilato (ora si salva al tocco); il passo va in «Da completare»
            skip.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { skip(); } });
            h.addView(skip);
        }
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

    /** 0.24: i tre passi guidati di «Meno notifiche»: tre trattini e «2 di 3». */
    private void dots(int k) {
        LinearLayout r = U.row(this);
        r.setPadding(0, 0, 0, U.dp(this, 8));
        for (int i = 1; i <= 3; i++) {
            View d = new View(this);
            d.setBackground(U.box(this, i <= k ? U.INK : U.TRACK_OFF, 0, 2));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(U.dp(this, 14), U.dp(this, 3));
            lp.rightMargin = U.dp(this, 4);
            r.addView(d, lp);
        }
        TextView t = U.text(this, k + " di 3", 13, U.MUTED, false);
        t.setPadding(U.dp(this, 8), 0, 0, 0);
        r.addView(t);
        r.setContentDescription(k + " di 3");
        body.addView(r, U.full());
    }

    private void skip() {
        if (sub >= 0) { closeSub(); return; }
        clearGuide(); // 0.24: «Salta» azzera la guida e la catena dei permessi, sempre
        int s = step();
        if (s == PORTATA) Onboarding.seen(this, "portata_saltata");
        next();
    }

    private void title(String s) {
        TextView t = U.title(this, s, 28, U.INK);
        t.setPadding(0, U.dp(this, 4), 0, U.dp(this, 8));
        if (stepTitle == null) stepTitle = t;
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

    /** Il filo sottile in cima al piede (0.24). */
    private void footDivider() {
        View d = new View(this);
        d.setBackgroundColor(U.LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, U.dp(this, 1)));
        lp.leftMargin = -U.dp(this, 20);
        lp.rightMargin = -U.dp(this, 20);
        lp.bottomMargin = U.dp(this, 10);
        foot.addView(d, lp);
    }

    private void primary(String s, final Runnable r) { primary(s, r, true); }

    /**
     * 0.24: il piede di tutte le schermate, una riga: «‹» (circa un quarto, «Indietro») a sinistra e il pulsante pieno
     * (circa tre quarti) a destra. Altezze di 52 dp; il piede ha il fondo della pagina e un filo in alto.
     */
    private void primary(String s, final Runnable r, boolean withBack) {
        LinearLayout row = U.row(this);
        if (withBack) {
            TextView b = U.text(this, "‹", 26, U.INK, false);
            b.setGravity(Gravity.CENTER);
            b.setMinHeight(U.dp(this, 52));
            b.setIncludeFontPadding(false);
            b.setContentDescription("Indietro");
            Press.outlined(b, U.BG, U.BORDER, 26);
            b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { back(); } });
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, U.dp(this, 52), 1f);
            blp.rightMargin = U.dp(this, 10);
            row.addView(b, blp);
        }
        TextView t = U.text(this, s, 16, U.BG, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(U.dp(this, 52));
        Press.primary(t, U.INK, 26);
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        row.addView(t, new LinearLayout.LayoutParams(0, U.dp(this, 52), withBack ? 3f : 4f));
        foot.addView(row, U.full());
    }

    /** Scheda selezionabile: radio (tondo) o checkbox (quadrato). */
    private void choice(String title, String subtitle, boolean on, boolean radio, final Runnable r) {
        LinearLayout c = U.row(this);
        c.setMinimumHeight(U.dp(this, 64));
        c.setPadding(U.dp(this, 15), U.dp(this, 10), U.dp(this, 15), U.dp(this, 10));
        PressB.card(c, U.SURFACE, on ? U.INK : U.BORDER, 14); // 0.21: scheda con il bordo: premuta si scurisce un po', anello di 8 dp
        LinearLayout tx = U.col(this);
        tx.addView(U.text(this, title, 16, U.INK, true));
        if (subtitle != null) tx.addView(U.text(this, subtitle, 13, U.MUTED, false));
        c.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView mark = U.text(this, on ? "✓" : "", 12, U.BG, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(U.box(this, on ? U.INK : U.SURFACE, U.INK, radio ? 10 : 4));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(U.dp(this, 22), U.dp(this, 22));
        mlp.leftMargin = U.dp(this, 10);
        c.addView(mark, mlp);
        c.setContentDescription(title + (on ? ", scelto" : ""));
        c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        LinearLayout.LayoutParams lp = U.full();
        lp.bottomMargin = U.dp(this, 10);
        body.addView(c, lp);
    }

    /** Riga con titolo, sottotitolo e, a destra, un interruttore o un testo. */
    private void line(String title, String subtitle, Boolean toggle, String right, final Runnable r) {
        LinearLayout row = U.row(this);
        row.setMinimumHeight(U.dp(this, 56));
        row.setPadding(0, U.dp(this, 6), 0, U.dp(this, 6));
        LinearLayout tx = U.col(this);
        tx.addView(U.text(this, title, 15, U.INK, Boolean.TRUE.equals(toggle))); // 0.16: grassetto se acceso
        if (subtitle != null) tx.addView(U.text(this, subtitle, 12, U.MUTED, false));
        row.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (toggle != null) row.addView(U.toggle(this, toggle, "onb:" + title), new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 26))); // 0.21: il pallino scorre
        else if (right != null) {
            TextView rt = U.text(this, right, 14, U.INK, false);
            rt.setPadding(U.dp(this, 10), 0, 0, 0);
            row.addView(rt);
        }
        final boolean isToggle = toggle != null;
        if (r != null) {
            PressB.row(row); // 0.21: riga senza bordo: si scurisce il fondo (le righe senza azione non si premono)
            row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (isToggle) Motion.scatto(v); r.run(); } });
        }
        if (toggle != null) row.setContentDescription(title + (toggle ? ", acceso" : ", spento"));
        body.addView(row, U.full());
        body.addView(U.divider(this));
    }

    /** Riquadro di testo, pieno o tratteggiato. */
    private LinearLayout card(String[] lines, boolean firstBold) {
        LinearLayout c = U.col(this);
        c.setPadding(U.dp(this, 14), U.dp(this, 12), U.dp(this, 14), U.dp(this, 12));
        c.setBackground(U.box(this, U.SURFACE, U.LINE, 14));
        for (int i = 0; i < lines.length; i++) {
            TextView t = U.text(this, lines[i], i == 0 && firstBold ? 15 : 13, i == 0 && firstBold ? U.INK : U.MUTED, i == 0 && firstBold);
            t.setPadding(0, i == 0 ? 0 : U.dp(this, 4), 0, 0);
            c.addView(t);
        }
        LinearLayout.LayoutParams lp = U.full();
        lp.topMargin = U.dp(this, 6);
        lp.bottomMargin = U.dp(this, 10);
        body.addView(c, lp);
        return c;
    }

    private void link(String s, final Runnable r) {
        TextView t = U.text(this, s, 15, U.INK, false);
        t.setPaintFlags(t.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        t.setMinHeight(U.dp(this, 48));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(U.dp(this, 4), U.dp(this, 6), U.dp(this, 12), U.dp(this, 6));
        PressB.link(t); // 0.21: link di testo: anello di 6 dp, solo attorno al testo
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        body.addView(t, U.wrap());
    }

    /** Fila di chip: una sola scelta. */
    private void chips(String[] labels, int selected, final ChipPick pick) {
        LinearLayout row = U.row(this);
        for (int i = 0; i < labels.length; i++) {
            final int k = i;
            boolean sel = i == selected;
            TextView t = U.text(this, labels[i], 13, sel ? U.BG : U.INK, false);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(U.dp(this, 44));
            t.setPadding(U.dp(this, 14), 0, U.dp(this, 14), 0);
            if (sel) Press.chipOn(t, 18); else Press.chip(t, U.BG, U.BORDER, 18);
            t.setContentDescription(labels[i] + (sel ? ", scelto" : ""));
            t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pick.pick(k); } });
            LinearLayout.LayoutParams lp = U.wrap();
            lp.rightMargin = U.dp(this, 6);
            row.addView(t, lp);
        }
        LinearLayout.LayoutParams lp = U.full();
        lp.bottomMargin = U.dp(this, 8);
        body.addView(row, lp);
    }

    interface ChipPick { void pick(int i); }

    // ---------- 180 · Cosa vuoi cambiare? ----------

    private LogoView logo; // 0.13: una sola istanza, così l'animazione non riparte a ogni scelta

    private void goals() {
        // 0.15 · 180: il logo resta piccolo in alto a sinistra, nella riga del passo
        LinearLayout brand = U.row(this);
        brand.setMinimumHeight(U.dp(this, 36));
        brand.addView(smallLogo(), new LinearLayout.LayoutParams(U.dp(this, 18), U.dp(this, 18)));
        TextView name = U.text(this, U.NAME, 14, U.INK, true);
        name.setTypeface(U.SERIF_I);
        name.setPadding(U.dp(this, 8), 0, 0, 0);
        brand.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView skipGoals = skipLink(); // 0.24: «Salta» sempre in alto a destra: si parte con le scelte di adesso
        skipGoals.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { start(); } });
        brand.addView(skipGoals);
        body.addView(brand, U.full());
        title("Cosa vuoi cambiare?");
        sub("Scegli cosa vuoi in meno: vedrai solo i passi che servono.");
        for (int i = 0; i < Goals.ALL.length; i++) {
            final String g = Goals.ALL[i];
            final boolean on = chosenGoals.contains(g);
            choice(Goals.NAMES[i], Goals.SUBS[i], on, false, new Runnable() {
                public void run() { if (on) chosenGoals.remove(g); else chosenGoals.add(g); render(); }
            });
        }
        link("Per ora voglio solo un telefono più semplice", new Runnable() {
            public void run() { chosenGoals.clear(); start(); }
        });
        path = buildPath();
        note("Puoi sceglierne più di uno, o nessuno. Qui non si accende niente: ogni obiettivo ha il suo passo. Le risposte restano sul telefono.");
        int steps = 1 + chosenGoals.size();
        primary("Inizia · " + steps + (steps == 1 ? " passo" : " passi"), new Runnable() { public void run() { start(); } });
    }

    private LogoView smallLogo() {
        LogoView l = new LogoView(this, U.INK, false);
        l.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return l;
    }

    // ---------- 179 · Splash: logo e promessa ----------

    private void splash() {
        View top = new View(this);
        body.addView(top, new LinearLayout.LayoutParams(1, getResources().getDisplayMetrics().heightPixels / 5));
        if (logo == null) logo = new LogoView(this, U.INK, true); // una sola istanza: l'animazione non riparte
        if (logo.getParent() != null) ((ViewGroup) logo.getParent()).removeView(logo);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(U.dp(this, 96), U.dp(this, 96));
        llp.gravity = Gravity.CENTER_HORIZONTAL;
        llp.bottomMargin = U.dp(this, 10);
        body.addView(logo, llp);
        TextView name = U.title(this, U.NAME, 44, U.INK);
        name.setTypeface(U.SERIF_I);
        name.setGravity(Gravity.CENTER);
        lineHeight(name, 48); // 0.26: interlinea 48 (era 52)
        body.addView(name, U.full());
        TextView promise = U.text(this, "", 20, U.INK, true); // 0.26: 20 px (era 22), interlinea 24, 4 px sopra
        promise.setText(android.text.Html.fromHtml("Il tuo smartphone. Ma <i>" + U.NAME + "</i>."));
        promise.setGravity(Gravity.CENTER);
        lineHeight(promise, 24);
        promise.setPadding(0, U.dp(this, 4), 0, 0);
        body.addView(promise, U.full());
        LinearLayout.LayoutParams mlp = U.full();
        mlp.topMargin = U.dp(this, 12);
        body.addView(menoLoop(), mlp);
        TextView free = U.text(this, "Gratis e open source · nessun dato esce dal telefono", 12, U.MUTED, false);
        free.setGravity(Gravity.CENTER);
        free.setPadding(0, 0, 0, U.dp(this, 10));
        foot.addView(free, U.full());
        primary("Inizia", new Runnable() { public void run() { splash = false; showStep(); } }, false);
    }

    /** 0.26: interlinea esatta in dp (il jar di compilazione è l'API 23, niente setLineHeight). */
    private void lineHeight(TextView t, int dp) {
        t.setIncludeFontPadding(false);
        int natural = t.getPaint().getFontMetricsInt(null);
        t.setLineSpacing(U.dp(this, dp) - natural, 1f);
    }

    private static final String[] MENO = {"Meno interruzioni", "Meno doomscrolling", "Meno interferenze col tuo sonno"};
    private static final int MENO_HOLD = 3000, MENO_FADE = 540;

    /**
     * 0.26 (C46): sotto la promessa i tre «Meno…», uno per volta, in loop. Literata corsivo 500 a 20, #5f5f5a.
     * Ogni frase resta 3 s; il cambio è una dissolvenza incrociata di 540 ms (ease in-out) con 8 dp di spostamento,
     * così lo spazio non resta mai vuoto. Con «Rimuovi animazioni» resta ferma la prima frase.
     */
    private View menoLoop() {
        final android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        final TextView[] tv = new TextView[2];
        for (int i = 0; i < 2; i++) {
            TextView t = U.text(this, MENO[0], 20, 0xFF5F5F5A, false);
            t.setTypeface(U.SERIF_I);
            t.setGravity(Gravity.CENTER);
            t.setIncludeFontPadding(false);
            t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            if (i == 1) t.setAlpha(0f);
            tv[i] = t;
            box.addView(t, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        box.setContentDescription(MENO[0] + ", " + MENO[1] + ", " + MENO[2]);
        if (Motion.reduced(this)) return box;
        final float dy = U.dp(this, 8);
        final int[] at = {0};
        final Runnable[] next = new Runnable[1];
        next[0] = new Runnable() {
            public void run() {
                if (!box.isAttachedToWindow() || isFinishing()) return;
                TextView out = tv[at[0] % 2], in = tv[(at[0] + 1) % 2];
                at[0]++;
                in.setText(MENO[at[0] % MENO.length]);
                in.setTranslationY(dy);
                in.setAlpha(0f);
                out.animate().alpha(0f).translationY(-dy).setDuration(MENO_FADE).setInterpolator(Motion.DECELERA).start();
                in.animate().alpha(1f).translationY(0f).setDuration(MENO_FADE).setInterpolator(Motion.DECELERA).start();
                box.postDelayed(next[0], MENO_HOLD + MENO_FADE);
            }
        };
        box.postDelayed(next[0], MENO_HOLD);
        return box;
    }

    private void start() {
        StringBuilder b = new StringBuilder();
        for (String g : Goals.ALL) if (chosenGoals.contains(g)) { if (b.length() > 0) b.append(","); b.append(g); }
        U.prefs(this).edit().putString(Onboarding.K_GOALS, b.toString()).putBoolean(Night.K_ON, false).apply();
        path = buildPath();
        idx = 1;
        prepare();
        render();
    }

    // ---------- 182 · La tua −minus home (con tema e testo) ----------

    private void home() {
        title("La tua −minus home");
        body.addView(Aspetto.preview(this), U.full());
        sub("Al posto delle icone la tua giornata, e una ricerca per trovare app e persone. Le app restano tutte installate.");
        Aspetto.controls(this, body, false, new Runnable() {
            public void run() {
                root.setBackgroundColor(U.BG);
                getWindow().setStatusBarColor(U.BG);
                getWindow().setNavigationBarColor(U.BG);
                U.immersive(OnboardingActivity.this);
                render();
            }
        });
        card(new String[]{"Puoi tornare alla home di prima quando vuoi dalle Impostazioni."}, false);
        // 0.24: la schermata di blocco è parte della home: qui, non in «La tua giornata»
        final boolean lock = U.prefs(this).getBoolean(U.K_LOCK, true);
        line("Schermata di blocco −minus", "Ora, prossimi impegni, sveglia. Niente notifiche", lock, null, new Runnable() {
            public void run() { U.prefs(OnboardingActivity.this).edit().putBoolean(U.K_LOCK, !lock).apply(); render(); }
        });
        if (Onboarding.isDefaultHome(this)) {
            body.addView(U.text(this, "✓ −minus è la tua home.", 15, U.INK, true));
            primary("Avanti", new Runnable() { public void run() { askCalendar(); } });
        } else {
            primary("Imposta −minus come home", new Runnable() { public void run() {
                openAndWatch(new Runnable() { public void run() { Onboarding.openHomeSettings(OnboardingActivity.this); } },
                        new Check() { public boolean ok() { return Onboarding.isDefaultHome(OnboardingActivity.this); } });
            } });
        }
    }

    /** 183b: il permesso Calendario arriva toccando Avanti, prima della giornata. */
    private void askCalendar() {
        if (U.canWriteCalendar(this) || askedCal) { if (step() == HOME) next(); return; }
        askedCal = true;
        requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR}, REQ_CAL);
    }

    // ---------- 183 · La tua giornata (183d senza permesso) ----------

    private void day() {
        title("La tua giornata");
        sub("Impegni e task sempre a vista: in home e sulla schermata di blocco, senza sbloccare.");
        if (U.hasCalendar(this)) {
            caption("Calendari da mostrare");
            final Set<Long> shown = U.shownCalendars(this);
            for (final U.CalRow r : U.allCalendars(this)) {
                final boolean on = shown.isEmpty() || shown.contains(r.id);
                String note = "com.google".equals(r.type) ? "Google" : android.provider.CalendarContract.ACCOUNT_TYPE_LOCAL.equals(r.type) ? "Solo su questo telefono" : r.type;
                if (r.access < android.provider.CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) note += " · sola lettura";
                line(r.name, note, on, null, new Runnable() {
                    public void run() {
                        Set<Long> s = shown;
                        if (s.isEmpty()) for (U.CalRow x : U.allCalendars(OnboardingActivity.this)) s.add(x.id);
                        if (on) s.remove(r.id); else s.add(r.id);
                        U.setShownCalendars(OnboardingActivity.this, s);
                        render();
                    }
                });
            }
            // 0.15: la scelta sta nel passo, non in un popup: uno dei calendari accesi, oppure solo in −minus
            caption("I nuovi impegni vanno in");
            final long cur = U.prefs(this).getLong("last_cal", -1);
            for (final U.Cal k : U.writableCalendars(this)) {
                if (U.isMinusCal(k)) continue;
                choice(k.name, k.google() ? "Google · si vede anche in Google Calendar" : U.calNote(k), cur == k.id, true, new Runnable() {
                    public void run() { U.setDefaultCalendar(OnboardingActivity.this, k.id, k.name); render(); }
                });
            }
            final long mine = U.minusCalendar(this, false);
            choice("Nessuno: restano in −minus", "Su questo telefono, senza andare in Google Calendar", cur >= 0 && cur == mine, true, new Runnable() {
                public void run() {
                    long id = U.minusCalendar(OnboardingActivity.this, true);
                    if (id < 0) { U.toast(OnboardingActivity.this, "Non riesco a creare il calendario di −minus: scegline uno dei tuoi"); return; }
                    U.setDefaultCalendar(OnboardingActivity.this, id, U.NAME);
                    render();
                }
            });
        } else {
            LinearLayout c = card(new String[]{"Il calendario non è collegato", "Senza accesso al calendario la home mostra solo i task e la sveglia."}, true);
            c.setBackground(U.box(this, U.SURFACE, U.BORDER, 14));
            TextView b = U.text(this, "Collega il calendario", 15, U.INK, true);
            b.setGravity(Gravity.CENTER);
            b.setMinHeight(U.dp(this, 48));
            Press.outlined(b, U.BG, U.INK, 24);
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (!askedCal || shouldShowRequestPermissionRationale(android.Manifest.permission.READ_CALENDAR)) {
                        askedCal = true;
                        requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR}, REQ_CAL);
                    } else Onboarding.openAppInfo(OnboardingActivity.this);
                }
            });
            body.addView(b, U.full());
            View gap = new View(this);
            body.addView(gap, new LinearLayout.LayoutParams(1, U.dp(this, 10)));
        }
        primary("Avanti", new Runnable() { public void run() { next(); } });
    }

    // ---------- 184 · A portata ----------

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

    static final String[] DAILY_HINTS = {"maps", "trenitalia", "italo", "atm", "moovit", "trainline", "flixbus", "uber", "free now", "bolt"};

    /** Tipo per la proposta: pagamenti e banca, spesa, viaggi; null = non proposta. Mai i social. */
    static String kind(U.AppInfo a) {
        if (U.isSocial(a.pkg)) return null;
        String l = " " + a.label.toLowerCase(Locale.ITALY) + " ";
        if (suggest(a.label) == 1) return l.contains("satispay") || l.contains("paypal") ? "pagamenti" : "banca";
        for (String h : new String[]{"esselunga", "carrefour", "coop", "lidl", "glovo", "deliveroo", "just eat", "too good"}) if (l.contains(h)) return "spesa";
        for (String h : DAILY_HINTS) if (l.contains(" " + h)) return h.equals("maps") ? "" : "viaggi";
        for (String h : new String[]{"whatsapp", "fotocamera", "camera", "spotify"}) if (l.contains(h)) return "";
        return null;
    }

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
        sub("Scegli fino a " + SearchActivity.MAX_PORTATA + " app che vuoi vedere senza cercare. Proposte tra le app installate: messaggi, mappe, fotocamera, banca. Social esclusi.");
        if (portataCand == null) loadPortata();
        PortataPicker pk = new PortataPicker(this, portataSel, portataCand, false, new PortataPicker.Host() {
            public void changed(String desc, List<String[]> selected, Runnable after) { portataSel = selected; }
        });
        body.addView(pk.root, U.full());
        primary("Avanti", new Runnable() {
            public void run() {
                Changes.record(OnboardingActivity.this, "A portata scelte nella configurazione guidata", new String[]{SearchActivity.K_PORTATA});
                SearchActivity.savePortata(OnboardingActivity.this, portataSel);
                Onboarding.seen(OnboardingActivity.this, "portata");
                next();
            }
        });
    }

    // ---------- 0.24 · «Meno notifiche» guidato: una scelta per schermata, poi il riepilogo ----------

    private final Scegli.Host scegliHost = new Scegli.Host() {
        public void grantContacts() { OnboardingActivity.this.grantContacts(); }
        public void pickFromAndroid() {
            try { startActivityForResult(new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), REQ_PICK); }
            catch (Exception e) { U.toast(OnboardingActivity.this, "Non trovo la rubrica"); }
        }
    };

    /** Un selettore ({@link Scegli}) a tutta schermata: Chi conta, Servizi, Gruppi, nei tre passi guidati; «Avanti» per continuare. */
    private void guided(int kind, int k, String title, String subtitle) {
        dots(k);
        title(title);
        sub(subtitle);
        scegli = new Scegli(this, kind, true, scegliHost);
        body.addView(scegli.root, U.full());
        if (kind == Scegli.GROUPS) {
            // 0.24: le chiamate si scelgono qui, in fondo ai gruppi
            View gap = new View(this);
            body.addView(gap, new LinearLayout.LayoutParams(1, U.dp(this, 14)));
            body.addView(U.divider(this));
            final boolean calls = U.prefs(this).getBoolean(K_CALLS, true);
            line("Chiamate", "Squillano solo chi conta e le chiamate ripetute", calls, null, new Runnable() {
                public void run() { U.prefs(OnboardingActivity.this).edit().putBoolean(K_CALLS, !calls).apply(); render(); }
            });
        }
        primary("Avanti", new Runnable() { public void run() { next(); } });
    }

    /** Un selettore riaperto dal riepilogo (o dalla prova): sopra il passo, con «Avanti» per tornarci. */
    private void openSub(int kind) {
        sub = kind;
        showStep();
    }

    private void closeSub() {
        if (sub == Scegli.PEOPLE) Stars.starAlways(this);
        sub = -1;
        showStep();
    }

    private void subPicker() {
        title(Scegli.title(sub));
        scegli = new Scegli(this, sub, true, scegliHost);
        body.addView(scegli.root, U.full());
        primary("Avanti", new Runnable() { public void run() { closeSub(); } });
    }

    // ---------- 186 · Cosa ti avvisa (il riepilogo) ----------

    private void notif() {
        title("Cosa ti avvisa");
        sub("Scegli chi e cosa passa sempre. Il resto aspetta.");
        int n = Scegli.count(this, Scegli.PEOPLE);
        summaryRow("Chi conta", "Passano sempre, anche col silenzio", n == 0 ? "Scegli ›" : Scegli.countText(this, Scegli.PEOPLE) + " ›", Scegli.PEOPLE);
        summaryRow("Servizi", "Banca, SPID, app IO: codici e accessi", Scegli.countText(this, Scegli.SERVICES) + " ›", Scegli.SERVICES);
        summaryRow("Gruppi", "Quelli che passano sempre", Scegli.countText(this, Scegli.GROUPS) + " ›", Scegli.GROUPS);
        final boolean calls = U.prefs(this).getBoolean(K_CALLS, true);
        line("Chiamate", "Squillano solo chi conta e le chiamate ripetute", null, calls ? "acceso" : "spento", new Runnable() {
            public void run() { U.prefs(OnboardingActivity.this).edit().putBoolean(K_CALLS, !calls).apply(); render(); }
        });
        tag("Per te", "ti aspettano qui", true);
        body.addView(U.text(this, "Gruppi che aspettano, altre persone, app che segui", 14, U.INK, false));
        tag("Altro", "ti aspettano qui, fuori dal conteggio", false);
        body.addView(U.text(this, "Social, shopping, news", 14, U.INK, false));
        if (maybeRestricted() && !U.listenerEnabled(this)) {
            View gap = new View(this);
            body.addView(gap, new LinearLayout.LayoutParams(1, U.dp(this, 10)));
            link("L'interruttore di Android è grigio e non si attiva?", new Runnable() { public void run() { guide.open(); showStep(); } });
        }
        note("Poi due permessi di Android: accesso alle notifiche e a Non disturbare. Si torna qui da soli.");
        primary("Avanti", new Runnable() {
            public void run() {
                List<Perm> p = new ArrayList<Perm>();
                p.add(listenerPerm());
                p.add(policyPerm());
                askThen(p, new Runnable() {
                    public void run() {
                        boolean calls = U.prefs(OnboardingActivity.this).getBoolean(K_CALLS, true);
                        if (calls && Onboarding.policy(OnboardingActivity.this) && FilterService.applySilence(OnboardingActivity.this, true))
                            U.prefs(OnboardingActivity.this).edit().putBoolean(U.K_SILENCE, true).apply();
                        goalDone(Goals.NOTIF);
                    }
                });
            }
        });
    }

    /** Una riga del riepilogo: stesso «3 scelti ›» delle Impostazioni; si tocca per riaprire il selettore. */
    private void summaryRow(String title, String subtitle, String right, final int kind) {
        line(title, subtitle, null, right, new Runnable() { public void run() { openSub(kind); } });
    }

    private void tag(String name, String desc, boolean filled) {
        LinearLayout r = U.row(this);
        r.setPadding(0, U.dp(this, 14), 0, U.dp(this, 4));
        TextView t = U.text(this, name, 12, filled ? U.BG : U.INK, true);
        t.setPadding(U.dp(this, 10), U.dp(this, 3), U.dp(this, 10), U.dp(this, 3));
        if (filled) Press.primary(t, U.INK, 12); else Press.outlined(t, U.BG, U.INK, 12);
        r.addView(t);
        TextView d = U.text(this, desc, 12, U.MUTED, false);
        d.setPadding(U.dp(this, 8), 0, 0, 0);
        r.addView(d);
        body.addView(r, U.full());
    }

    private String alwaysNames(int max) {
        StringBuilder b = new StringBuilder();
        int n = 0, total = 0;
        for (Rules.Person p : Rules.people(U.cfg(this, U.K_CONTACTS, U.DEF_CONTACTS))) {
            if (!p.section.equalsIgnoreCase("Sempre")) continue;
            total++;
            if (n++ < max) { if (b.length() > 0) b.append(", "); b.append(p.name); }
        }
        if (total > max) b.append(" e altri ").append(total - max);
        return b.toString();
    }

    /** Il permesso Contatti: la finestra di Android se si può ancora chiedere, altrimenti Info app. */
    private void grantContacts() {
        if (!askedContacts && !Onboarding.hasContacts(this)) {
            askedContacts = true;
            requestPermissions(new String[]{android.Manifest.permission.READ_CONTACTS, android.Manifest.permission.WRITE_CONTACTS}, REQ_CONTACTS);
        } else Onboarding.openAppInfo(this);
    }

    /**
     * 0.24 (Evolutiva 018): Android 13+, app installata fuori dal Play Store: i tre tocchi per sbloccare l'accesso alle notifiche,
     * su una schermata sola. Al ritorno da Android si accorge da sola: acceso, «Fatto, si continua» e dopo circa 1 s si va avanti;
     * ancora bloccato, «Non è ancora acceso» e «Riprova». «Salta» e ‹ chiudono la guida del tutto.
     */
    private void restrictedGuide() {
        final int ph = guide.phase;
        title(ph == GuidaPerm.OK ? "Android ha dato il via libera" : "Android blocca questo permesso");
        sub(ph == GuidaPerm.OK ? "L'accesso alle notifiche è acceso."
                : ph == GuidaPerm.STILL ? "Succede alle app installate fuori dal Play Store, e riguarda l'accesso alle notifiche."
                : "Succede alle app installate fuori dal Play Store, e riguarda l'accesso alle notifiche. Si sblocca una volta sola, poi torni qui.");
        if (ph == GuidaPerm.STILL) {
            LinearLayout info = U.row(this);
            info.setPadding(0, 0, 0, U.dp(this, 10));
            info.addView(U.text(this, "ⓘ", 18, U.INK, false));
            TextView m = U.text(this, "Non è ancora acceso. Manca il passo " + guide.missing() + ".", 15, U.INK, true);
            m.setPadding(U.dp(this, 10), 0, 0, 0);
            info.addView(m, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            info.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            body.addView(info, U.full());
        }
        // 0.16: il menu ⋮ compare solo dopo aver provato ad accendere l'interruttore grigio almeno una volta da questa installazione
        final String[] steps = {
                "Tocca «Prova ad accenderlo». Nell'elenco tocca −minus anche se è grigio, poi «OK».",
                "Apri «Info app». In alto a destra tocca ⋮, poi «Consenti impostazioni con restrizioni». Su alcuni telefoni la voce è in fondo alla pagina.",
                "Conferma con PIN o impronta e torna qui: l'interruttore si accende."};
        for (int i = 0; i < steps.length; i++) {
            boolean done = ph == GuidaPerm.OK || i < guide.step;
            boolean cur = ph != GuidaPerm.OK && i == guide.step;
            LinearLayout r = U.row(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(U.dp(this, 10), U.dp(this, 12), U.dp(this, 10), U.dp(this, 12));
            if (cur) r.setBackground(U.box(this, U.BG, U.INK, 14));
            TextView n = U.text(this, done ? "✓" : String.valueOf(i + 1), 14, done ? U.BG : U.INK, true);
            n.setGravity(Gravity.CENTER);
            n.setBackground(U.box(this, done || cur ? U.INK : U.BG, U.INK, 14));
            if (cur) n.setTextColor(U.BG);
            r.addView(n, new LinearLayout.LayoutParams(U.dp(this, 28), U.dp(this, 28)));
            TextView t = U.text(this, steps[i], cur ? 16 : 15, cur ? U.INK : U.MUTED, false);
            t.setPadding(U.dp(this, 14), 0, 0, 0);
            r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            r.setContentDescription("Passo " + (i + 1) + ": " + steps[i] + (done ? ". Fatto" : cur ? ". Tocca a te" : ""));
            LinearLayout.LayoutParams lp = U.full();
            lp.bottomMargin = U.dp(this, 6);
            body.addView(r, lp);
        }
        if (ph == GuidaPerm.OK) {
            // il via libera: il segno disegnato, «Fatto, si continua», e dopo circa 1 s si va avanti da soli
            final View check = new View(this) {
                @Override protected void onDraw(android.graphics.Canvas c) {
                    float w = getWidth(), h = getHeight(), r = Math.min(w, h) / 2f - 2f;
                    android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    p.setStyle(android.graphics.Paint.Style.STROKE);
                    p.setStrokeWidth(U.dp(OnboardingActivity.this, 2));
                    p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                    p.setStrokeJoin(android.graphics.Paint.Join.ROUND);
                    p.setColor(U.INK);
                    c.drawCircle(w / 2f, h / 2f, r, p);
                    android.graphics.Path path = new android.graphics.Path();
                    path.moveTo(w * 0.30f, h * 0.52f);
                    path.lineTo(w * 0.45f, h * 0.66f);
                    path.lineTo(w * 0.72f, h * 0.36f);
                    c.drawPath(path, p);
                }
            };
            check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(U.dp(this, 68), U.dp(this, 68));
            clp.gravity = Gravity.CENTER_HORIZONTAL;
            clp.topMargin = U.dp(this, 28);
            body.addView(check, clp);
            TextView fatto = U.title(this, "Fatto, si continua", 20, U.INK);
            fatto.setGravity(Gravity.CENTER);
            fatto.setPadding(0, U.dp(this, 14), 0, 0);
            fatto.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            body.addView(fatto, U.full());
            return; // niente piede: si prosegue da soli
        }
        final boolean[] open = {ph == GuidaPerm.STILL};
        final LinearLayout help = U.col(this);
        help.setPadding(U.dp(this, 14), U.dp(this, 12), U.dp(this, 14), U.dp(this, 12));
        help.setBackground(U.box(this, U.SURFACE, U.LINE, 14));
        help.addView(U.text(this, "Il ⋮ non c'è? Ripeti il passo 1 e chiudi il messaggio di Android toccando «OK», non con il gesto indietro del telefono. "
                + "Se ancora manca, riavvia il telefono e riprova. Con il Play Store questo passaggio non serve.", 13, U.INK, false));
        TextView gesto = U.text(this, "Per tornare in −minus da Android usa il gesto «indietro» del telefono. Il tasto ‹ qui sotto torna al passo prima.", 13, U.MUTED, false);
        gesto.setPadding(0, U.dp(this, 8), 0, 0);
        help.addView(gesto);
        help.setVisibility(open[0] ? View.VISIBLE : View.GONE);
        final TextView nf = U.text(this, "Non funziona?", 15, U.INK, false);
        nf.setPaintFlags(nf.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        nf.setMinHeight(U.dp(this, 48));
        nf.setGravity(Gravity.CENTER_VERTICAL);
        PressB.link(nf);
        nf.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { open[0] = !open[0]; help.setVisibility(open[0] ? View.VISIBLE : View.GONE); }
        });
        body.addView(nf, U.wrap());
        body.addView(help, U.full());
        primary(guide.button(), new Runnable() {
            public void run() {
                final int k = guide.step;
                if (k == 1) { guide.leave(); Onboarding.openAppInfo(OnboardingActivity.this); return; }
                guide.leave();
                openAndWatch(new Runnable() { public void run() { Onboarding.openListenerSettings(OnboardingActivity.this); } },
                        new Check() { public boolean ok() { return U.listenerEnabled(OnboardingActivity.this); } });
            }
        });
    }

    // ---------- 187 · Quando lo sblocchi senza motivo ----------

    private void unlock() {
        title("Quando lo sblocchi senza motivo");
        sub("Se lo sblocchi tre volte in 10 minuti senza aprire niente, una domanda ti chiede per cosa.");
        final boolean on = U.prefs(this).getBoolean(Sblocco.K_ON, true);
        line("Chiedimelo", "Al massimo 3 volte al giorno, mai di notte", on, null, new Runnable() {
            public void run() { U.prefs(OnboardingActivity.this).edit().putBoolean(Sblocco.K_ON, !on).apply(); render(); }
        });
        card(new String[]{"ANTEPRIMA", "Per cosa l'hai preso?", "Controllare l'ora  ·  Mi annoiavo  ·  Niente"}, false);
        note("Usa l'accesso ai dati di utilizzo che hai appena dato: nessun permesso in più.");
        primary("Avanti", new Runnable() {
            public void run() {
                if (!U.prefs(OnboardingActivity.this).contains(Sblocco.K_ON)) U.prefs(OnboardingActivity.this).edit().putBoolean(Sblocco.K_ON, true).apply();
                goalDone(Goals.DOOM);
            }
        });
    }

    /** Fine dei passi di un obiettivo: dalle Impostazioni si accende e si torna lì. */
    private void goalDone(String g) {
        if (onlyGoal != null && onlyGoal.equals(g)) {
            Goals.set(this, g, true);
            Changes.record(this, Goals.name(g) + " acceso", new String[]{Onboarding.K_GOALS});
            finish();
            return;
        }
        next();
    }

    // ---------- 188 · App che rapiscono ----------

    private void doomApps() {
        title("App che rapiscono");
        sub("Si aprono dopo qualche secondo di pausa. E se scegli «scopo» ti chiedono anche per cosa e per quanto.");
        scegli = new Scegli(this, Scegli.DOOM, true, scegliHost); // 0.24: lo stesso selettore, con cerca e proposte
        body.addView(scegli.root, U.full());
        note("Se manca l'accesso ai dati di utilizzo, Avanti apre la pagina di Android e torni qui in automatico: serve per la pausa anche da notifiche e app recenti.");
        primary("Avanti", new Runnable() {
            public void run() {
                List<Perm> p = new ArrayList<Perm>();
                if (!U.pausedPkgs(OnboardingActivity.this).isEmpty()) p.add(usagePerm());
                askThen(p, new Runnable() { public void run() { next(); } });
            }
        });
    }

    // ---------- 189 · Scegli come fermarle ----------

    private void doomHow() {
        title("Scegli come fermarle");
        caption("Pausa prima che si aprano");
        final int[] secs = {4, 6, 8};
        int cur = U.pauseSeconds(this), sel = 2;
        for (int i = 0; i < secs.length; i++) if (secs[i] == cur) sel = i;
        chips(new String[]{"4 s", "6 s", "8 s"}, sel, new ChipPick() {
            public void pick(int i) { U.prefs(OnboardingActivity.this).edit().putInt(U.K_PAUSE_SEC, secs[i]).apply(); render(); }
        });
        caption("Tempo nelle app che rapiscono, avvisami dopo"); // 0.18 (Evolutiva 012)
        final int[] mins = {10, 20, 30};
        int m = U.prefs(this).getInt(Session.K_MIN, 10), ms = 0;
        for (int i = 0; i < mins.length; i++) if (mins[i] == m) ms = i;
        chips(new String[]{"10 min", "20 min", "30 min"}, ms, new ChipPick() {
            public void pick(int i) { U.prefs(OnboardingActivity.this).edit().putBoolean(Session.K_ON, true).putInt(Session.K_MIN, mins[i]).apply(); render(); }
        });
        final boolean pill = U.prefs(this).getBoolean(Pill.K_ON, false);
        line("Anche sopra le app che rapiscono", "La pillola «18 min» dentro Instagram", pill, null, new Runnable() {
            public void run() { U.prefs(OnboardingActivity.this).edit().putBoolean(Pill.K_ON, !pill).apply(); render(); }
        });
        if (pill && !Pill.canDraw(this))
            body.addView(U.text(this, "Con l'interruttore acceso, Avanti ti porta un attimo nelle impostazioni di Android per dare il permesso.", 12, U.MUTED, false));
        primary("Avanti", new Runnable() {
            public void run() {
                List<Perm> p = new ArrayList<Perm>();
                if (U.prefs(OnboardingActivity.this).getBoolean(Pill.K_ON, false)) p.add(overlayPerm());
                askThen(p, new Runnable() {
                    public void run() {
                        if (!Pill.canDraw(OnboardingActivity.this)) U.prefs(OnboardingActivity.this).edit().putBoolean(Pill.K_ON, false).apply();
                        next();
                    }
                });
            }
        });
    }

    // ---------- 190 · Quando dormi ----------

    private void sleep() {
        title("Quando dormi");
        sub("Ogni sera un avviso ti dice quando è il momento di staccare.");
        final android.content.SharedPreferences p = U.prefs(this);
        if (!p.contains(Night.K_WAKE)) {
            int a = Night.alarmMin(this);
            if (a >= 0) p.edit().putInt(Night.K_WAKE, a).apply();
        }
        final int wake = Night.wakeMin(this);
        caption("Sveglia");
        line(Notte.hhmm(wake), Night.alarmMin(this) == wake ? "dalla sveglia del telefono" : "scelta da te", null, "Cambia", new Runnable() {
            public void run() {
                new android.app.TimePickerDialog(OnboardingActivity.this, new android.app.TimePickerDialog.OnTimeSetListener() {
                    public void onTimeSet(android.widget.TimePicker v, int h, int m) { p.edit().putInt(Night.K_WAKE, h * 60 + m).apply(); render(); }
                }, wake / 60, wake % 60, true).show();
            }
        });
        caption("Ore di sonno che vuoi");
        final int[] hrs = {420, 450, 480};
        int s = Night.sleepMin(this), si = 1;
        for (int i = 0; i < hrs.length; i++) if (hrs[i] == s) si = i;
        chips(new String[]{"7", "7½", "8"}, si, new ChipPick() {
            public void pick(int i) { p.edit().putInt(Night.K_SLEEP, hrs[i]).apply(); render(); }
        });
        final int lead = Night.leadMin(this);
        LinearLayout c = card(new String[]{"Vai a dormire alle " + Notte.hhmm(Notte.bedMin(wake, Night.sleepMin(this))),
                "Ora di staccare: " + Notte.hhmm(Night.offMin(this)), lead + " minuti prima · cambiala",
                "Da quell'ora −minus va in grigio e mostra domani in home. Te lo dice con un avviso, insieme al consiglio della settimana."}, false);
        ((TextView) c.getChildAt(1)).setTextSize(17);
        ((TextView) c.getChildAt(1)).setTextColor(U.INK);
        ((TextView) c.getChildAt(1)).setTypeface(U.SANS_B);
        TextView ch = (TextView) c.getChildAt(2);
        ch.setPaintFlags(ch.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        ch.setMinHeight(U.dp(this, 48));
        ch.setGravity(Gravity.CENTER_VERTICAL);
        ch.setLayoutParams(U.wrap());
        ch.setPadding(0, U.dp(this, 6), U.dp(this, 12), U.dp(this, 6));
        PressB.link(ch); // 0.21: «· cambiala» è un link: anello di 6 dp
        ch.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final int[] opts = {15, 30, 45, 60};
                U.dlg(OnboardingActivity.this).setTitle("Quanto prima di dormire?")
                        .setItems(new String[]{"15 minuti", "30 minuti", "45 minuti", "Un'ora"}, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) { p.edit().putInt(Night.K_LEAD, opts[w]).apply(); render(); }
                        }).show();
            }
        });
        ((TextView) c.getChildAt(3)).setText(U.brand("Da quell'ora −minus va in grigio e mostra domani in home. Te lo dice con un avviso, insieme al consiglio della settimana."));
        // E002: il preavviso, acceso di default; nessun passo in più
        final boolean pre = Sera.prewarnOn(this);
        line("Avvisami 10 minuti prima", "Una riga, senza suoni", pre, null, new Runnable() {
            public void run() { p.edit().putBoolean(Sera.K_PREWARN, !pre).apply(); render(); }
        });
        line("Facoltativo: tutto il telefono in grigio", "In Android, Modalità Riposo, stessi orari: " + Notte.hhmm(Night.offMin(this)) + " – " + Notte.hhmm(wake), null, "Apri", new Runnable() {
            public void run() { Session.openWellbeing(OnboardingActivity.this); }
        });
        primary("Avanti", new Runnable() {
            public void run() {
                p.edit().putString(Night.K_MODE, Notte.SCHED).apply();
                Changes.record(OnboardingActivity.this, "Ora di staccare: " + Notte.hhmm(Night.offMin(OnboardingActivity.this)), new String[]{Night.K_WAKE, Night.K_SLEEP, Night.K_LEAD, Night.K_MODE});
                next();
            }
        });
    }

    // ---------- 192 · Una settimana di prova ----------

    private void trial() {
        title("Una settimana di prova");
        sub("Per sette notti −minus fa così. Poi ti mostra com'è andata e decidi tu.");
        String names = alwaysNames(2);
        line("Di notte, solo l'essenziale", (names.length() > 0 ? "Ti raggiungono solo " + names : "Ti raggiunge solo chi conta") + " · Uno sblocco mostra l'ora e quanto manca alla sveglia",
                null, "Cambia", new Runnable() { public void run() { openSub(Scegli.PEOPLE); } });
        final boolean morning = Night.morning(this);
        line("Il mattino resta tranquillo", "Fino alle " + Notte.hhmm(Night.wakeMin(this) + Notte.MORNING_MIN) + " niente app frenate", morning, null, new Runnable() {
            public void run() { U.prefs(OnboardingActivity.this).edit().putBoolean(Night.K_MORNING, !morning).apply(); render(); }
        });
        StringBuilder na = new StringBuilder();
        Set<String> night = Night.nightApps(this);
        int shown = 0;
        for (U.AppInfo a : U.sortedApps(this)) if (night.contains(a.pkg) && shown++ < 3) { if (na.length() > 0) na.append(", "); na.append(a.label); }
        if (night.size() > 3) na.append(" e altre ").append(night.size() - 3);
        line("App frenate di notte", na.length() > 0 ? na.toString() : "Nessuna", null, night.isEmpty() ? "Scegli ›" : Scegli.countText(this, Scegli.NIGHT) + " ›", new Runnable() {
            public void run() { openSub(Scegli.NIGHT); }
        });
        // 0.16 (192): il consiglio una tantum è tolto: i consigli arrivano la sera, uno a settimana
        primary("Avanti", new Runnable() {
            public void run() {
                U.prefs(OnboardingActivity.this).edit().putString(Night.K_MODE, Notte.SCHED).apply();
                Night.startTrial(OnboardingActivity.this);
                List<Perm> p = new ArrayList<Perm>();
                if (!chosenGoals.contains(Goals.NOTIF) && !Goals.on(OnboardingActivity.this, Goals.NOTIF)) p.add(listenerPerm());
                if (!Night.nightApps(OnboardingActivity.this).isEmpty()) p.add(usagePerm());
                askThen(p, new Runnable() { public void run() { goalDone(Goals.SLEEP); } });
            }
        });
    }

    // ---------- 193 · La tua alternativa ----------

    private EditText altField;

    private void alternative() {
        LinearLayout topRow = U.row(this); // 0.24: «Salta» sempre in alto a destra
        topRow.setMinimumHeight(U.dp(this, 40));
        topRow.addView(smallLogo(), new LinearLayout.LayoutParams(U.dp(this, 18), U.dp(this, 18)));
        topRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView skipAlt = skipLink();
        skipAlt.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { skip(); } });
        topRow.addView(skipAlt);
        body.addView(topRow, U.full());
        title("Cosa preferisci fare invece di scorrere?");
        sub("Viene proposta quando dici «Mi annoiavo», nella pausa con scopo e nello sblocco di notte.");
        altField = new EditText(this);
        String cur = U.prefs(this).getString(Night.K_ALT, "");
        altField.setText(cur);
        altField.setHint("Esempio: Leggere qualche pagina");
        altField.setHintTextColor(U.HINT);
        altField.setTextColor(U.INK);
        altField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        altField.setBackground(U.box(this, U.SURFACE, U.INK, 12));
        altField.setPadding(U.dp(this, 14), U.dp(this, 12), U.dp(this, 14), U.dp(this, 12));
        LinearLayout.LayoutParams alp = U.full();
        alp.bottomMargin = U.dp(this, 10);
        body.addView(altField, alp);
        String[] ideas = {"Leggere il libro sul comodino", "Uscire a camminare", "Chiamare qualcuno", "Stendermi senza telefono"};
        LinearLayout wrap = U.col(this);
        for (final String s : ideas) {
            TextView t = U.text(this, s, 14, U.INK, false);
            t.setMinHeight(U.dp(this, 44));
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(U.dp(this, 14), 0, U.dp(this, 14), 0);
            Press.chip(t, U.BG, U.BORDER, 18); // 0.21: chip di idee: un po' più scuro da premuto, anello di 6 dp
            t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { altField.setText(s); altField.setSelection(s.length()); } });
            LinearLayout.LayoutParams lp = U.wrap();
            lp.bottomMargin = U.dp(this, 6);
            wrap.addView(t, lp);
        }
        body.addView(wrap, U.full());
        primary("Avanti", new Runnable() {
            public void run() {
                String s = altField.getText().toString().trim();
                if (s.length() > 0) U.prefs(OnboardingActivity.this).edit().putString(Night.K_ALT, s).apply();
                next();
            }
        });
    }

    // ---------- 194 · Pronto ----------

    private void ready() {
        View top = new View(this);
        body.addView(top, new LinearLayout.LayoutParams(1, U.dp(this, 12)));
        title("Pronto");
        sub("Ecco cosa fa −minus da adesso.");
        caption("Configurazione");
        String cals = U.hasCalendar(this) ? (U.shownCalendars(this).isEmpty() ? "tutti i calendari" : U.shownCalendars(this).size() + " calendari") : "senza calendario";
        done("Home e giornata", cals + (U.prefs(this).getBoolean(U.K_LOCK, true) ? " · blocco acceso" : ""), Onboarding.isDefaultHome(this) && U.hasCalendar(this));
        done("A portata", SearchActivity.portata(this).size() + " app", true);
        List<String[]> todo = Onboarding.todo(this);
        for (int i = 0; i < Goals.ALL.length; i++) {
            if (!chosenGoals.contains(Goals.ALL[i])) continue;
            caption(Goals.NAMES[i]);
            if (Goals.NOTIF.equals(Goals.ALL[i])) {
                int n = Onboarding.alwaysCount(this);
                done("Chi conta", n + (n == 1 ? " persona" : " persone") + (Stars.mineCount(this) > 0 || Stars.canWrite(this) ? " · ★ in rubrica" : ""), n > 0);
            }
            if (Goals.DOOM.equals(Goals.ALL[i])) done("Pausa", U.pausedPkgs(this).size() + " app · " + U.pauseSeconds(this) + " secondi", true);
            if (Goals.DOOM.equals(Goals.ALL[i]) && U.prefs(this).getBoolean(Sblocco.K_ON, false))
                done("Sblocchi senza scopo", "una domanda, al massimo 3 al giorno", true); // 0.18 (Evolutiva 010)
            if (Goals.SLEEP.equals(Goals.ALL[i]) && Night.on(this))
                done("Ora di staccare " + Notte.hhmm(Night.offMin(this)), "prova fino al " + new java.text.SimpleDateFormat("d MMMM", Locale.ITALY).format(new java.util.Date(U.prefs(this).getLong(Night.K_TRIAL, System.currentTimeMillis()))), true);
            for (final String[] t : todo) if (Goals.ALL[i].equals(t[2])) fixRow(t);
        }
        boolean header = false;
        for (final String[] t : todo) if (t[2] == null) {
            if (!header) { caption("Da completare"); header = true; }
            fixRow(t);
        }
        for (int i = 0; i < Goals.ALL.length; i++)
            if (!chosenGoals.contains(Goals.ALL[i])) {
                TextView t = U.text(this, "«" + Goals.NAMES[i] + "» si può accendere quando vuoi da Impostazioni.", 13, U.MUTED, false);
                t.setPadding(0, U.dp(this, 10), 0, 0);
                body.addView(t);
            }
        note("Annulla resta disponibile dopo ogni scelta, e in Impostazioni › Modifiche recenti.");
        primary("Vai alla home", new Runnable() {
            public void run() {
                Onboarding.finish(OnboardingActivity.this, "obiettivi");
                finish();
            }
        });
    }

    private void done(String title, String sub, boolean ok) {
        line(title, sub, null, ok ? "✓" : null, null);
    }

    private void fixRow(final String[] t) {
        line(t[1], "da concedere", null, "Fai ora", new Runnable() { public void run() { Onboarding.fix(OnboardingActivity.this, t[0]); } });
    }
}
