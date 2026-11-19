package it.essenziale.launcher;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * La pillola del tempo sopra le altre app (0.9, wireframe 113–116). Si vede solo oltre la soglia,
 * mai sopra Essenziale; un tocco la riduce a un puntino per 10 minuti. Spenta finché non la accendi.
 * La decisione è in Tempo (pura); qui solo la finestra e i dati di Android.
 */
final class Pill {
    private Pill() {}

    static final String K_ON = "pill_on", K_DOT = "pill_dot_until";
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static FrameLayout view;    // la pillola (o il puntino) sopra le altre app
    private static RollText roll;        // 0.20 (Evolutiva 016): il testo scorre quando cambia
    private static View dotView;
    private static boolean viewDot;
    private static int resumed = 0;
    private static boolean paused = false;

    // ---------- Permessi ----------

    static boolean canDraw(Context c) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
    }

    static boolean usageGranted(Context c) {
        try {
            AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            return ops.checkOpNoThrow("android:get_usage_stats", Process.myUid(), c.getPackageName()) == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    static void openOverlaySettings(Context c) {
        Intent i = new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse("package:" + c.getPackageName()));
        if (!U.start(c, i)) U.start(c, new Intent(Settings.ACTION_SETTINGS));
    }

    static void openUsageSettings(Context c) {
        if (!U.start(c, new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))) U.start(c, new Intent(Settings.ACTION_SETTINGS));
    }

    static boolean on(Context c) { return U.prefs(c).getBoolean(K_ON, false) && Goals.on(c, Goals.DOOM); }

    /** La pillola è davvero attiva: accesa e con il permesso. Allora l'avviso in tendina non serve. */
    static boolean active(Context c) { return on(c) && canDraw(c); }

    // ---------- Essenziale in primo piano ----------

    static void resumed(Context c) {
        resumed++;
        hide();
    }

    static void pausedActivity(final Context c) {
        resumed = Math.max(0, resumed - 1);
        final Context a = c.getApplicationContext();
        H.postDelayed(new Runnable() { public void run() { update(a); } }, 800);
    }

    /** E002: −minus è davanti (la home mostra l'avviso da sé). */
    static boolean minusFront() { return resumed > 0; }

    /** Chiamata o navigazione in corso (lo decide FilterService). */
    static void setPaused(boolean p) { paused = p; }

    /** 0.15: chiamata o navigazione in corso: niente foglio dello scopo sopra. */
    static boolean isPaused() { return paused; }

    // ---------- Aggiornamento ----------

    static void update(Context c) {
        final Context a = c.getApplicationContext();
        long now = System.currentTimeMillis();
        // 0.10: durante uno scopo la pillola mostra il tempo che resta al posto del totale (un segnale alla volta)
        String scope = Scope.pillText(a);
        if (scope != null) {
            // 0.15: durante uno scopo la pillola è quella dello scopo (wireframe 204–209)
            hideOwn();
            if (canDraw(a) && resumed == 0) ScopePill.show(a); else ScopePill.hide();
            return;
        }
        ScopePill.hide();
        // E002: 10 minuti prima dell'ora di staccare, la stessa riga della home sulla pillola chiusa
        String pre = Sera.prewarn(a);
        if (pre != null && canDraw(a) && resumed == 0 && !paused) { show(a, pre); return; }
        // 0.15: appena finito uno scopo non parte subito la pillola del tempo dall'ultimo sblocco
        if (Tempo.quietAfterScope(U.prefs(a).getLong(Scope.K_ENDED_AT, 0), now)) { hideOwn(); return; }
        // 0.18 (Evolutiva 012): conta solo il tempo nelle app frenate; la pillola solo sopra di loro
        long start = Session.start(a);
        java.util.Set<String> braked = U.pausedPkgs(a);
        Tempo.Braked bk = brakedNow(a, start, now, braked);
        boolean inBraked = Tempo.inBraked(bk, braked);
        if (!inBraked && U.prefs(a).getLong(K_DOT, 0) > 0) U.prefs(a).edit().putLong(K_DOT, 0).apply(); // uscendo il puntino sparisce
        int min = inBraked ? Tempo.brakedMinutes(bk, Session.threshold(a)) : -1;
        int st = Tempo.state(on(a), canDraw(a), Session.on(a), min, resumed > 0, paused, U.prefs(a).getLong(K_DOT, 0), now);
        if (st == Tempo.HIDE) { hide(); return; }
        if (st == Tempo.DOT) { show(a, null); return; }
        String label = bk.top == null ? null : U.appLabel(a, bk.top);
        int appMin = (int) (bk.topMs / 60000L);
        show(a, Tempo.pillText(min, label, appMin));
    }

    /** 0.11: il testo della pillola in home: lo scopo se c'è, altrimenti il tempo oltre la soglia. */
    static String homeText(Context c) {
        String scope = Scope.pillText(c);
        if (scope != null) return scope;
        String pre = Sera.prewarn(c); // E002: il preavviso al posto della pillola del tempo
        if (pre != null) return pre;
        if (!Session.on(c) || !U.listenerEnabled(c)) return null;
        long start = Session.start(c), now = System.currentTimeMillis();
        if (Tempo.quietAfterScope(U.prefs(c).getLong(Scope.K_ENDED_AT, 0), now)) return null;
        Tempo.Braked bk = brakedNow(c, start, now, U.pausedPkgs(c)); // 0.18 (Evolutiva 012)
        int min = Tempo.brakedMinutes(bk, Session.threshold(c));
        if (min < 0) return null;
        String label = bk.top == null ? null : U.appLabel(c, bk.top);
        int appMin = (int) (bk.topMs / 60000L);
        return "●  " + Tempo.pillText(min, label, appMin);
    }

    /** 0.18 (Evolutiva 012): null senza accesso ai dati di utilizzo o senza sessione: allora non si conta. */
    static Tempo.Braked brakedNow(Context c, long start, long now, java.util.Set<String> braked) {
        if (start <= 0 || !usageGranted(c)) return null;
        try {
            UsageStatsManager um = (UsageStatsManager) c.getSystemService("usagestats");
            UsageEvents ev = um.queryEvents(start - 3 * 3600000L, now);
            List<Long> t = new ArrayList<Long>();
            List<String> p = new ArrayList<String>();
            List<Integer> ty = new ArrayList<Integer>();
            UsageEvents.Event e = new UsageEvents.Event();
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                int k = e.getEventType();
                if (k != Tempo.FG && k != Tempo.BG) continue;
                t.add(e.getTimeStamp());
                p.add(e.getPackageName());
                ty.add(k);
            }
            long[] ta = new long[t.size()];
            int[] tya = new int[t.size()];
            for (int i = 0; i < ta.length; i++) { ta[i] = t.get(i); tya[i] = ty.get(i); }
            return Tempo.braked(ta, p.toArray(new String[0]), tya, start, now, braked);
        } catch (Exception ex) {
            return null;
        }
    }

    /** I minuti nelle app frenate oltre la soglia, o -1 (anche senza accesso ai dati di utilizzo). */
    static int brakedMinutes(Context c) {
        return Tempo.brakedMinutes(brakedNow(c, Session.start(c), System.currentTimeMillis(), U.pausedPkgs(c)), Session.threshold(c));
    }

    // ---------- La finestra ----------

    private static void show(final Context a, String text) {
        try {
            WindowManager wm = (WindowManager) a.getSystemService(Context.WINDOW_SERVICE);
            boolean dot = text == null;
            if (view != null && viewDot != dot) hideOwn(false); // puntino e pillola hanno misure diverse: si ricrea
            boolean existed = view != null;
            if (view == null) {
                viewDot = dot;
                view = new FrameLayout(a);
                roll = new RollText(a, 13, U.BG);
                view.addView(roll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
                dotView = new View(a);
                view.addView(dotView, new FrameLayout.LayoutParams(U.dp(a, 12), U.dp(a, 12), Gravity.CENTER));
                view.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        U.prefs(a).edit().putLong(K_DOT, System.currentTimeMillis() + Tempo.DOT_MS).apply();
                        update(a);
                    }
                });
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                        Build.VERSION.SDK_INT >= 26 ? 2038 /* TYPE_APPLICATION_OVERLAY */ : 2002 /* TYPE_PHONE */,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                lp.y = U.dp(a, 34);
                wm.addView(view, lp);
            }
            if (dot) {
                roll.setVisibility(View.GONE);
                dotView.setVisibility(View.VISIBLE);
                view.setPadding(0, 0, 0, 0);
                view.setMinimumHeight(0);
                view.setBackground(U.box(a, U.INK, 0, 6));
                view.setContentDescription("Tempo al telefono, ridotto");
            } else {
                dotView.setVisibility(View.GONE);
                roll.setVisibility(View.VISIBLE);
                view.setMinimumHeight(U.dp(a, 32));
                view.setPadding(U.dp(a, 14), 0, U.dp(a, 14), 0);
                boolean pre = text.startsWith("Tra ");
                // 0.20: se la pillola c'era già, i minuti scorrono in su invece di saltare
                roll.set(text.startsWith("◷") || pre ? U.brand(text) : "●  " + text, existed);
                view.setBackground(U.box(a, U.INK, 0, 16));
                view.setContentDescription(pre ? text : "Al telefono da " + text + ". Tocca per ridurlo per 10 minuti.");
            }
            view.requestLayout();
        } catch (Exception ignored) {
            view = null;
            roll = null;
        }
    }

    static void hide() {
        ScopePill.hide();
        hideOwn(true);
    }

    private static void hideOwn() { hideOwn(true); }

    /**
     * 0.20 (Evolutiva 016): uscendo dall'app frenata la pillola si schiaccia in una linea d'inchiostro che si ritira a sinistra
     * (Motion.leave, BASE + RAPIDO); con «Rimuovi animazioni», una dissolvenza di 80 ms. Il tempo oltre la soglia non cambia colore né vibra.
     */
    private static void hideOwn(boolean animate) {
        if (view == null) return;
        final FrameLayout v = view;
        final RollText r = roll;
        view = null;
        roll = null;
        final Context c = v.getContext();
        final Runnable remove = new Runnable() {
            public void run() {
                try { ((WindowManager) c.getSystemService(Context.WINDOW_SERVICE)).removeView(v); } catch (Exception ignored) {}
            }
        };
        if (!animate || !v.isAttachedToWindow()) { remove.run(); return; }
        // momento 11: la pillola è una finestra sopra le altre app, quindi solo la linea (nient'altro scorre)
        Motion.leave(v, remove);
    }
}
