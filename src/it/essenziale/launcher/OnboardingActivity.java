package it.essenziale.launcher;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
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
 * Configurazione guidata per obiettivi (0.14, wireframe 180–194). Prima «Cosa vuoi cambiare?»,
 * poi la configurazione (home, giornata, a portata) e un passo per ogni obiettivo scelto. I permessi
 * di Android si chiedono toccando Avanti, solo quando servono; si torna da soli. Ciò che si salta
 * o si nega finisce in «Da completare».
 *
 * Extra: "goal" (dalle Impostazioni: solo i passi di quell'obiettivo, poi lo accende),
 * "only" (un solo passo, per esempio Quando dormi), "again" (rifai tutto).
 */
public class OnboardingActivity extends BaseActivity {

    static final int GOALS = 0, HOME = 1, DAY = 2, PORTATA = 3, NOTIF = 4, UNLOCK = 5, DOOM_APPS = 6, DOOM_HOW = 7,
            SLEEP = 8, TRIAL = 9, ALT = 10, READY = 11;

    private static final int REQ_CAL = 4, REQ_CONTACTS = 3, REQ_PICK = 51;

    private int[] path = {GOALS};
    private int idx = 0;
    private String onlyGoal;        // dalle Impostazioni: «Accendi»
    private boolean onlyStep;       // dalle Impostazioni: un solo passo
    private boolean who = false;    // dentro «Chi conta» (185), sopra «Cosa ti avvisa» (186)
    private boolean restricted = false;
    private boolean splash = false, hadSplash = false; // 0.15 · 179: logo e promessa, solo al primo avvio
    private LinearLayout root, body, foot;
    private final java.util.Set<String> chosenGoals = new java.util.LinkedHashSet<String>();

    // stato dei passi
    private boolean askedContacts = false, askedCal = false;
    private final Map<String, String[]> chosen = new LinkedHashMap<String, String[]>();    // nome minuscolo -> {nome, numero}
    private final Map<String, String[]> candidates = new LinkedHashMap<String, String[]>();
    private boolean peopleLoaded = false;
    private String search = "";
    private LinearLayout searchResults;
    private List<String[]> portataCand;   // {pacchetto, nome, tipo}
    private List<String[]> portataSel;    // {pacchetto, nome}
    private boolean doomPrepared = false, sleepPrepared = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        running = true;
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
        root.setPadding(U.dp(this, 20), U.dp(this, 16), U.dp(this, 20), U.dp(this, 12));
        ScrollView sv = new ScrollView(this);
        body = U.col(this);
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        foot = U.col(this);
        root.addView(foot, U.full());
        setContentView(root);
        prepare(); // dalle Impostazioni si parte già dal passo dell'obiettivo
    }

    @Override
    protected void onResume() {
        super.onResume();
        watching = null; // 0.13.5: sei tornata, non serve più riportarti qui
        if (chain != null) { runChain(); return; } // 0.14: il permesso successivo, o il passo dopo
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
        if (req == REQ_PICK && res == RESULT_OK && data != null && data.getData() != null) picked(data.getData());
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
    private boolean restrictedShown;
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
            if (!ok && asked.contains(p.key) && "notifiche".equals(p.key) && maybeRestricted() && !restrictedShown) {
                // tornata senza permesso e con l'app da APK: l'interruttore era grigio, la guida prima di andare avanti
                restrictedShown = true;
                restricted = true;
                render();
                return;
            }
            if (ok || asked.contains(p.key)) continue;
            asked.add(p.key);
            restricted = false;
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
        peopleLoaded = false;
        if (code == REQ_CAL && step() == HOME) { next(); return; }
        render();
    }

    @Override
    public void onBackPressed() { back(); }

    private int step() { return path[idx]; }

    private void next() {
        who = false;
        if (onlyStep) { finish(); return; }
        if (idx < path.length - 1) { idx++; prepare(); render(); }
        else finish();
    }

    private void back() {
        if (restricted) { restricted = false; if (chain != null) { runChain(); return; } render(); return; }
        if (who) { who = false; render(); return; }
        if (idx == 0 && hadSplash && !splash) { splash = true; render(); return; } // da 180 si torna allo splash
        if (idx == 0) { finish(); return; }
        idx--;
        render();
    }

    // ---------- Percorso ----------

    private int[] goalSteps(String g, boolean alone) {
        if (Goals.NOTIF.equals(g)) return new int[]{NOTIF, UNLOCK};
        if (Goals.DOOM.equals(g)) return new int[]{DOOM_APPS, DOOM_HOW};
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
        if (s == NOTIF || s == UNLOCK) return Goals.NAMES[0];
        if (s == DOOM_APPS || s == DOOM_HOW) return Goals.NAMES[1];
        if (s == SLEEP || s == TRIAL) return Goals.NAMES[2];
        return null;
    }

    /** Prima di mostrare un passo dell'obiettivo, le scelte proposte (mai accese in silenzio). */
    private void prepare() {
        int s = step();
        if (s == DOOM_APPS && !doomPrepared) {
            doomPrepared = true;
            if (!U.prefs(this).contains(Scope.K_LEVEL))
                for (U.AppInfo a : U.sortedApps(this)) if (U.isSocial(a.pkg)) Scope.setLevel(this, a.pkg, Scopo.SCOPE);
        }
        if (s == TRIAL && !sleepPrepared) {
            sleepPrepared = true;
            if (Night.nightApps(this).isEmpty() && !U.prefs(this).contains(Night.K_APPS))
                for (U.AppInfo a : U.sortedApps(this)) if (U.isSocial(a.pkg) || isVideo(a)) Night.setNightApp(this, a.pkg, true);
        }
    }

    static boolean isVideo(U.AppInfo a) {
        String l = a.label.toLowerCase(Locale.ITALY);
        return l.contains("youtube") || l.contains("netflix") || l.contains("prime video") || l.contains("disney") || l.contains("twitch") || l.contains("raiplay");
    }

    // ---------------------------------------------------------------------

    private void render() {
        body.removeAllViews();
        foot.removeAllViews();
        int s = step();
        if (splash) { splash(); return; }
        if (restricted) { header(); restrictedGuide(); return; }
        if (s != GOALS && s != READY && s != ALT) header();
        if (who) { people(); return; } // 185 si apre da 186 e da 192
        switch (s) {
            case GOALS: goals(); break;
            case HOME: home(); break;
            case DAY: day(); break;
            case PORTATA: portata(); break;
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
            TextView skip = U.text(this, "Salta", 13, U.MUTED, false);
            skip.setPaintFlags(skip.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            skip.setGravity(Gravity.CENTER);
            skip.setMinHeight(U.dp(this, 44));
            skip.setPadding(U.dp(this, 12), 0, 0, 0);
            // 0.13.6: «Salta» tiene quello che il passo ha già compilato; il passo va in «Da completare»
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

    private void skip() {
        int s = step();
        if (s == NOTIF && who && peopleLoaded) savePeople();
        if (s == PORTATA) Onboarding.seen(this, "portata_saltata");
        next();
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
        primary(s, r, step() != GOALS && step() != READY ? "‹ Indietro" : null, new Runnable() { public void run() { back(); } });
    }

    private void primary(String s, final Runnable r, String second, final Runnable r2) {
        TextView t = U.text(this, s, 16, U.BG, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(U.dp(this, 52));
        t.setBackground(U.box(this, U.INK, 0, 26));
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        foot.addView(t, U.full());
        if (second != null) {
            TextView b = U.text(this, second, 14, U.MUTED, false);
            b.setGravity(Gravity.CENTER);
            b.setMinHeight(U.dp(this, 44));
            b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r2.run(); } });
            foot.addView(b, U.full());
        }
    }

    /** Scheda selezionabile: radio (tondo) o checkbox (quadrato). */
    private void choice(String title, String subtitle, boolean on, boolean radio, final Runnable r) {
        LinearLayout c = U.row(this);
        c.setMinimumHeight(U.dp(this, 64));
        c.setPadding(U.dp(this, 15), U.dp(this, 10), U.dp(this, 15), U.dp(this, 10));
        c.setBackground(U.box(this, U.SURFACE, on ? U.INK : U.BORDER, 14));
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
        tx.addView(U.text(this, title, 15, U.INK, false));
        if (subtitle != null) tx.addView(U.text(this, subtitle, 12, U.MUTED, false));
        row.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (toggle != null) row.addView(U.toggle(this, toggle), new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 26)));
        else if (right != null) {
            TextView rt = U.text(this, right, 14, U.INK, false);
            rt.setPadding(U.dp(this, 10), 0, 0, 0);
            row.addView(rt);
        }
        if (r != null) row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
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
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        body.addView(t, U.full());
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
            t.setBackground(sel ? U.box(this, U.INK, 0, 18) : U.box(this, U.BG, U.BORDER, 18));
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
        brand.addView(name);
        body.addView(brand, U.full());
        title("Cosa vuoi cambiare?");
        sub("Scegli cosa vuoi in meno: ti mostriamo solo i passi che servono.");
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
        body.addView(name, U.full());
        TextView promise = U.text(this, "", 22, U.INK, true);
        promise.setText(android.text.Html.fromHtml("Il tuo smartphone. Ma <i>" + U.NAME + "</i>."));
        promise.setGravity(Gravity.CENTER);
        promise.setPadding(0, U.dp(this, 10), 0, 0);
        body.addView(promise, U.full());
        TextView free = U.text(this, "Gratis e open source · nessun dato esce dal telefono", 12, U.MUTED, false);
        free.setGravity(Gravity.CENTER);
        free.setPadding(0, 0, 0, U.dp(this, 10));
        foot.addView(free, U.full());
        primary("Inizia", new Runnable() { public void run() { splash = false; render(); } }, null, null);
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
            caption("I nuovi impegni vanno in");
            String def = U.prefs(this).getString("last_cal_name", null);
            line(def != null ? def : "Scegli un calendario", null, null, "Cambia", new Runnable() { public void run() { pickCalendar(); } });
        } else {
            LinearLayout c = card(new String[]{"Non vediamo il tuo calendario", "Senza accesso al calendario la home mostra solo i task e la sveglia."}, true);
            c.setBackground(U.box(this, U.SURFACE, U.BORDER, 14));
            TextView b = U.text(this, "Collega il calendario", 15, U.INK, true);
            b.setGravity(Gravity.CENTER);
            b.setMinHeight(U.dp(this, 48));
            b.setBackground(U.box(this, U.BG, U.INK, 24));
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
        final boolean lock = U.prefs(this).getBoolean(U.K_LOCK, true);
        line("Schermata di blocco −minus", "Ora, prossimi impegni, sveglia. Niente notifiche", lock, null, new Runnable() {
            public void run() { U.prefs(OnboardingActivity.this).edit().putBoolean(U.K_LOCK, !lock).apply(); render(); }
        });
        primary("Avanti", new Runnable() { public void run() { next(); } });
    }

    private void pickCalendar() {
        final List<U.Cal> cals = U.writableCalendars(this);
        if (cals.isEmpty()) { U.toast(this, "Nessun calendario modificabile tra quelli mostrati"); return; }
        List<String> names = new ArrayList<String>();
        for (U.Cal k : cals) names.add(k.name + "\n" + U.calNote(k));
        new AlertDialog.Builder(this).setTitle("I nuovi impegni vanno in").setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                U.prefs(OnboardingActivity.this).edit().putLong("last_cal", cals.get(w).id).putString("last_cal_name", cals.get(w).name).apply();
                render();
            }
        }).show();
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

    // ---------- 186 · Cosa ti avvisa, cosa aspetta ----------

    private void notif() {
        title("Cosa ti avvisa, cosa aspetta");
        sub("Ti avvisa solo ciò che conta, il resto aspetta te. Suoneria o vibrazione restano quelle del telefono.");
        tag("Per te", "ti avvisano subito, e restano qui", true);
        int n = Onboarding.alwaysCount(this);
        String names = alwaysNames(3);
        line("Chi conta", n == 0 ? "Da scegliere: persone e gruppi" : names, null, n == 0 ? "Scegli ›" : "✓ ›", new Runnable() {
            public void run() { openWho(); }
        });
        String sv = ServiziActivity.names(this, 3);
        int svn = ServiziActivity.installed(this).size();
        line("Servizi", (sv.length() > 0 ? sv + " · " : "Banca, SPID, app IO, codici · ") + svn + " riconosciuti", null, "›", new Runnable() {
            public void run() { startActivity(new Intent(OnboardingActivity.this, ServiziActivity.class).putExtra("back", "Configurazione")); }
        });
        line("Chiamate", "Squillano solo chi conta e le chiamate ripetute", null, null, null);
        tag("Per te", "ti aspettano qui", true);
        body.addView(U.text(this, "Gruppi in silenzio, altre persone, app che segui", 14, U.INK, false));
        tag("Altro", "ti aspettano qui, fuori dal conteggio", false);
        body.addView(U.text(this, "Social, shopping, news", 14, U.INK, false));
        if (maybeRestricted() && !U.listenerEnabled(this)) {
            View gap = new View(this);
            body.addView(gap, new LinearLayout.LayoutParams(1, U.dp(this, 10)));
            link("L'interruttore di Android è grigio e non si attiva?", new Runnable() { public void run() { restricted = true; render(); } });
        }
        note("Poi due permessi di Android: accesso alle notifiche e a Non disturbare. Si torna qui da soli.");
        primary("Avanti", new Runnable() {
            public void run() {
                if (Onboarding.alwaysCount(OnboardingActivity.this) == 0 && !asked.contains("chi")) { asked.add("chi"); openWho(); return; }
                List<Perm> p = new ArrayList<Perm>();
                p.add(listenerPerm());
                p.add(policyPerm());
                askThen(p, new Runnable() {
                    public void run() {
                        if (Onboarding.policy(OnboardingActivity.this) && FilterService.applySilence(OnboardingActivity.this, true))
                            U.prefs(OnboardingActivity.this).edit().putBoolean(U.K_SILENCE, true).apply();
                        next();
                    }
                });
            }
        });
    }

    private void tag(String name, String desc, boolean filled) {
        LinearLayout r = U.row(this);
        r.setPadding(0, U.dp(this, 14), 0, U.dp(this, 4));
        TextView t = U.text(this, name, 12, filled ? U.BG : U.INK, true);
        t.setPadding(U.dp(this, 10), U.dp(this, 3), U.dp(this, 10), U.dp(this, 3));
        t.setBackground(filled ? U.box(this, U.INK, 0, 12) : U.box(this, U.BG, U.INK, 12));
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

    /** 185a → 185: il permesso Contatti (lettura e modifica, per la stella) e poi Chi conta. */
    private void openWho() {
        who = true;
        peopleLoaded = false;
        if (!Onboarding.hasContacts(this) && !askedContacts) {
            askedContacts = true;
            requestPermissions(new String[]{android.Manifest.permission.READ_CONTACTS, android.Manifest.permission.WRITE_CONTACTS}, REQ_CONTACTS);
        } else if (Onboarding.hasContacts(this) && !Stars.canWrite(this) && !askedContacts) {
            askedContacts = true;
            requestPermissions(new String[]{android.Manifest.permission.WRITE_CONTACTS}, REQ_CONTACTS);
        }
        render();
    }

    // ---------- 185 · Chi conta (185n senza rubrica) ----------

    private void people() {
        title("Chi conta");
        boolean contacts = Onboarding.hasContacts(this);
        sub(contacts ? "Queste persone ti raggiungono sempre, anche col silenzio: messaggi e chiamate."
                : "Queste persone ti raggiungono sempre: scegline una alla volta.");
        if (!peopleLoaded) loadPeople();
        if (!contacts) {
            card(new String[]{"Non vediamo la tua rubrica", "Senza accesso ai contatti non posso proporti i preferiti né mettere la stella. Puoi aggiungere le persone a mano."}, true);
            TextView pick = U.text(this, "Scegli dalla rubrica", 15, U.INK, true);
            pick.setGravity(Gravity.CENTER);
            pick.setMinHeight(U.dp(this, 48));
            pick.setBackground(U.box(this, U.BG, U.INK, 24));
            pick.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    try { startActivityForResult(new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), REQ_PICK); }
                    catch (Exception e) { U.toast(OnboardingActivity.this, "Non trovo la rubrica"); }
                }
            });
            body.addView(pick, U.full());
        } else {
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
        }
        caption(contacts ? "Persone · dai preferiti" : "Persone");
        LinearLayout list = U.col(this);
        body.addView(list, U.full());
        if (candidates.isEmpty())
            list.addView(U.text(this, contacts ? "Nessun preferito in rubrica: cerca qui sopra le persone da aggiungere." : "Ancora nessuno.", 14, U.MUTED, false));
        for (final String[] p : candidates.values()) personRow(list, p);
        if (contacts && Stars.canWrite(this)) {
            TextView st = U.text(this, "★ Metteremo la stella in rubrica a chi scegli, così squilla anche col silenzio di sistema, anche nelle altre app.", 13, U.INK, false);
            st.setPadding(0, U.dp(this, 8), 0, 0);
            body.addView(st);
        }
        caption("Gruppi, app per app");
        line("Gruppi che passano sempre", "Gli altri restano in silenzio. Compaiono qui quando arrivano le prime notifiche dei gruppi", null, "›", new Runnable() {
            public void run() { startActivity(new Intent(OnboardingActivity.this, GroupsActivity.class).putExtra("back", "Chi conta")); }
        });
        if (!contacts || !Stars.canWrite(this)) {
            card(new String[]{"Senza stella, col silenzio di Android queste persone non squillano. I loro messaggi passano comunque."}, false);
            link("Concedi l'accesso ai contatti", new Runnable() { public void run() { Onboarding.openAppInfo(OnboardingActivity.this); } });
        }
        card(new String[]{"Conversazioni aperte", "Se rispondi a qualcuno che non è in elenco, ti avvisa per 12 ore. Gruppi 1 ora, mai di notte."}, true);
        primary("Fatto", new Runnable() {
            public void run() {
                savePeople();
                int n = Stars.starAlways(OnboardingActivity.this);
                if (n > 0) U.toast(OnboardingActivity.this, "★ " + n + (n == 1 ? " persona" : " persone") + " con la stella in rubrica");
                who = false;
                render();
            }
        }, "‹ Cosa ti avvisa", new Runnable() { public void run() { who = false; render(); } });
    }

    private void picked(Uri u) {
        Cursor cur = null;
        try {
            cur = getContentResolver().query(u, new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null);
            if (cur != null && cur.moveToFirst()) {
                String name = cur.getString(0), num = cur.getString(1);
                if (name == null) name = num;
                if (name != null) {
                    String[] p = {name, num == null ? "" : num};
                    candidates.put(name.toLowerCase(Locale.ITALY), p);
                    chosen.put(name.toLowerCase(Locale.ITALY), p);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
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
        for (String a : U.chatAppsOf(this, p[0], null)) { if (ap.length() > 0) ap.append(", "); ap.append(Rules.appName(a)); }
        if (p[1] != null && p[1].length() > 0) ap.append(ap.length() > 0 ? ", chiamata" : "chiamata");
        tx.addView(U.text(this, ap.toString(), 12, U.MUTED, false));
        r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(U.checkbox(this, on, U.INK), new LinearLayout.LayoutParams(U.dp(this, 22), U.dp(this, 22)));
        r.setContentDescription(p[0] + (on ? ", scelta" : ", non scelta"));
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
        Changes.record(this, "Chi conta: " + chosen.size() + (chosen.size() == 1 ? " persona" : " persone"), new String[]{U.K_CONTACTS});
        U.prefs(this).edit().putString(U.K_CONTACTS, Rules.peopleText(out)).apply();
    }

    /** Android 13+, app installata fuori dal Play Store: i tre tocchi per sbloccare il permesso. */
    private void restrictedGuide() {
        title("Android blocca questo permesso");
        sub("Succede alle app installate fuori dal Play Store. Si sblocca in tre tocchi, poi torni qui.");
        String[] steps = {"Tocca «Apri Info app» qui sotto", "In alto a destra tocca ⋮, poi «Consenti impostazioni con restrizioni»",
                "Conferma con PIN o impronta, torna indietro e tocca Avanti"};
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
        note("Compare solo se Android segnala «Impostazione con restrizioni». Con il Play Store questo passo non c'è.");
        primary("Apri Info app", new Runnable() { public void run() { asked.remove("notifiche"); Onboarding.openAppInfo(OnboardingActivity.this); } });
    }

    // ---------- 187 · Quando lo sblocchi senza motivo ----------

    private void unlock() {
        title("Quando lo sblocchi senza motivo");
        sub("Se lo sblocchi tre volte in 10 minuti senza aprire niente, ti chiediamo per cosa l'hai preso.");
        final boolean on = U.prefs(this).getBoolean(Sblocco.K_ON, true);
        line("Chiedimelo", "Al massimo 3 volte al giorno, mai di notte", on, null, new Runnable() {
            public void run() { U.prefs(OnboardingActivity.this).edit().putBoolean(Sblocco.K_ON, !on).apply(); render(); }
        });
        card(new String[]{"ANTEPRIMA", "Per cosa l'hai preso?", "Controllare l'ora  ·  Mi annoiavo  ·  Niente"}, false);
        primary("Avanti", new Runnable() {
            public void run() {
                if (!U.prefs(OnboardingActivity.this).contains(Sblocco.K_ON)) U.prefs(OnboardingActivity.this).edit().putBoolean(Sblocco.K_ON, true).apply();
                goalDone(Goals.NOTIF);
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
        caption("Trovate sul tuo telefono");
        Set<String> paused = U.pausedPkgs(this);
        int shown = 0;
        for (final U.AppInfo a : U.sortedApps(this)) {
            boolean social = U.isSocial(a.pkg), video = isVideo(a);
            if (!social && !video && !paused.contains(a.pkg)) continue;
            shown++;
            final int lv = Scope.dayLevel(this, a.pkg);
            LinearLayout r = U.row(this);
            r.setMinimumHeight(U.dp(this, 56));
            LinearLayout tx = U.col(this);
            tx.addView(U.text(this, a.label, 15, U.INK, false));
            tx.addView(U.text(this, social ? "social" : video ? "video" : "scelta da te", 12, U.MUTED, false));
            r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            String[] opts = {"Pausa", "Scopo"};
            for (int i = 0; i < 2; i++) {
                final int level = i == 0 ? Scopo.PAUSE : Scopo.SCOPE;
                boolean sel = lv == level;
                TextView t = U.text(this, opts[i], 13, sel ? U.BG : U.INK, false);
                t.setGravity(Gravity.CENTER);
                t.setMinHeight(U.dp(this, 40));
                t.setPadding(U.dp(this, 12), 0, U.dp(this, 12), 0);
                t.setBackground(sel ? U.box(this, U.INK, 0, 18) : U.box(this, U.BG, U.BORDER, 18));
                t.setContentDescription(a.label + ": " + opts[i] + (sel ? ", scelto. Tocca per toglierla" : ""));
                t.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { Scope.setLevel(OnboardingActivity.this, a.pkg, lv == level ? Scopo.NONE : level); render(); }
                });
                LinearLayout.LayoutParams lp = U.wrap();
                lp.leftMargin = U.dp(this, 4);
                r.addView(t, lp);
            }
            body.addView(r, U.full());
            body.addView(U.divider(this));
        }
        if (shown == 0) body.addView(U.text(this, "Nessun social o app video installata.", 14, U.MUTED, false));
        link("Aggiungi un'altra app", new Runnable() { public void run() { addDoomApp(); } });
        note("Se manca l'accesso ai dati di utilizzo, Avanti apre la pagina di Android e torni qui da sola: serve per la pausa anche da notifiche e app recenti.");
        primary("Avanti", new Runnable() {
            public void run() {
                List<Perm> p = new ArrayList<Perm>();
                if (!U.pausedPkgs(OnboardingActivity.this).isEmpty()) p.add(usagePerm());
                askThen(p, new Runnable() { public void run() { next(); } });
            }
        });
    }

    private void addDoomApp() {
        final List<U.AppInfo> apps = new ArrayList<U.AppInfo>();
        Set<String> paused = U.pausedPkgs(this);
        for (U.AppInfo a : U.sortedApps(this)) if (!paused.contains(a.pkg) && !getPackageName().equals(a.pkg)) apps.add(a);
        String[] names = new String[apps.size()];
        for (int i = 0; i < names.length; i++) names[i] = apps.get(i).label;
        new AlertDialog.Builder(this).setTitle("Aggiungi un'app").setItems(names, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) { Scope.setLevel(OnboardingActivity.this, apps.get(w).pkg, Scopo.PAUSE); render(); }
        }).setNegativeButton("Annulla", null).show();
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
        caption("Tempo dall'ultimo sblocco, avvisami dopo");
        final int[] mins = {10, 20, 30};
        int m = U.prefs(this).getInt(Session.K_MIN, 10), ms = 0;
        for (int i = 0; i < mins.length; i++) if (mins[i] == m) ms = i;
        chips(new String[]{"10 min", "20 min", "30 min"}, ms, new ChipPick() {
            public void pick(int i) { U.prefs(OnboardingActivity.this).edit().putBoolean(Session.K_ON, true).putInt(Session.K_MIN, mins[i]).apply(); render(); }
        });
        final boolean pill = U.prefs(this).getBoolean(Pill.K_ON, false);
        line("Anche sopra le altre app", "La pillola «25 min» dentro Instagram", pill, null, new Runnable() {
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
                        goalDone(Goals.DOOM);
                    }
                });
            }
        });
    }

    // ---------- 190 · Quando dormi ----------

    private void sleep() {
        title("Quando dormi");
        sub("Ogni giorno ti avviso quando è il momento di staccare.");
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
                "Da quell'ora −minus va in grigio, mostra domani in home e frena le app scelte per la notte."}, false);
        ((TextView) c.getChildAt(1)).setTextSize(17);
        ((TextView) c.getChildAt(1)).setTextColor(U.INK);
        ((TextView) c.getChildAt(1)).setTypeface(U.SANS_B);
        TextView ch = (TextView) c.getChildAt(2);
        ch.setPaintFlags(ch.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        ch.setMinHeight(U.dp(this, 40));
        ch.setGravity(Gravity.CENTER_VERTICAL);
        ch.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final int[] opts = {15, 30, 45, 60};
                new AlertDialog.Builder(OnboardingActivity.this).setTitle("Quanto prima di dormire?")
                        .setItems(new String[]{"15 minuti", "30 minuti", "45 minuti", "Un'ora"}, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) { p.edit().putInt(Night.K_LEAD, opts[w]).apply(); render(); }
                        }).show();
            }
        });
        ((TextView) c.getChildAt(3)).setText(U.brand("Da quell'ora −minus va in grigio, mostra domani in home e frena le app scelte per la notte."));
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
                null, "Cambia", new Runnable() { public void run() { openWho(); } });
        final boolean morning = Night.morning(this);
        line("Il mattino resta tranquillo", "Fino alle " + Notte.hhmm(Night.wakeMin(this) + Notte.MORNING_MIN) + " niente app frenate", morning, null, new Runnable() {
            public void run() { U.prefs(OnboardingActivity.this).edit().putBoolean(Night.K_MORNING, !morning).apply(); render(); }
        });
        StringBuilder na = new StringBuilder();
        Set<String> night = Night.nightApps(this);
        for (U.AppInfo a : U.sortedApps(this)) if (night.contains(a.pkg)) { if (na.length() > 0) na.append(", "); na.append(a.label); }
        line("App frenate di notte", na.length() > 0 ? "Proposte: " + na + " · trovate sul telefono" : "Nessuna", null, "Scegli", new Runnable() {
            public void run() { pickNightApps(); }
        });
        card(new String[]{"Un consiglio, una volta: metti il telefono a caricare fuori dalla camera. La sveglia suona lo stesso."}, false);
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

    private void pickNightApps() {
        final List<U.AppInfo> apps = new ArrayList<U.AppInfo>();
        for (U.AppInfo a : U.sortedApps(this)) if (!getPackageName().equals(a.pkg)) apps.add(a);
        Set<String> night = Night.nightApps(this);
        String[] names = new String[apps.size()];
        final boolean[] on = new boolean[apps.size()];
        for (int i = 0; i < names.length; i++) { names[i] = apps.get(i).label; on[i] = night.contains(apps.get(i).pkg); }
        new AlertDialog.Builder(this).setTitle("App frenate di notte").setMultiChoiceItems(names, on, new DialogInterface.OnMultiChoiceClickListener() {
            public void onClick(DialogInterface d, int w, boolean c) { on[w] = c; }
        }).setPositiveButton("Fatto", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                for (int i = 0; i < on.length; i++) Night.setNightApp(OnboardingActivity.this, apps.get(i).pkg, on[i]);
                render();
            }
        }).setNegativeButton("Annulla", null).show();
    }

    // ---------- 193 · La tua alternativa ----------

    private EditText altField;

    private void alternative() {
        View top = new View(this);
        body.addView(top, new LinearLayout.LayoutParams(1, U.dp(this, 12)));
        title("Cosa preferisci fare invece di scrollare?");
        sub("Te lo proponiamo quando dici «Mi annoiavo», nella pausa con scopo e la sera.");
        altField = new EditText(this);
        String cur = U.prefs(this).getString(Night.K_ALT, "");
        altField.setText(cur);
        altField.setHint("Leggere qualche pagina");
        altField.setHintTextColor(U.MUTED);
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
            t.setBackground(U.box(this, U.BG, U.BORDER, 18));
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
        }, "Salta", new Runnable() { public void run() { next(); } });
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
