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
import android.view.WindowManager;
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
    private static TextView view;
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
        long start = Session.start(a);
        int min = Rules.sessionMinutes(start, now, Session.threshold(a));
        int st = Tempo.state(on(a), canDraw(a), Session.on(a), min, resumed > 0, paused, U.prefs(a).getLong(K_DOT, 0), now);
        if (st == Tempo.HIDE) { hide(); return; }
        if (st == Tempo.DOT) { show(a, null); return; }
        String label = null;
        int appMin = 0;
        if (usageGranted(a)) {
            String[] top = topApp(a, start, now);
            if (top != null) {
                label = U.appLabel(a, top[0]);
                appMin = (int) (Long.parseLong(top[1]) / 60000L);
            }
        }
        show(a, Tempo.pillText(min, label, appMin));
    }

    /** 0.11: il testo della pillola in home: lo scopo se c'è, altrimenti il tempo oltre la soglia. */
    static String homeText(Context c) {
        String scope = Scope.pillText(c);
        if (scope != null) return scope;
        if (!Session.on(c) || !U.listenerEnabled(c)) return null;
        long start = Session.start(c), now = System.currentTimeMillis();
        int min = Rules.sessionMinutes(start, now, Session.threshold(c));
        if (min < 0) return null;
        String label = null;
        int appMin = 0;
        if (usageGranted(c)) {
            String[] top = topApp(c, start, now);
            if (top != null) {
                label = U.appLabel(c, top[0]);
                appMin = (int) (Long.parseLong(top[1]) / 60000L);
            }
        }
        return "●  " + Tempo.pillText(min, label, appMin);
    }

    private static String[] topApp(Context c, long start, long now) {
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
            return Tempo.top(ta, p.toArray(new String[0]), tya, start, now, c.getPackageName());
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- La finestra ----------

    private static void show(final Context a, String text) {
        try {
            WindowManager wm = (WindowManager) a.getSystemService(Context.WINDOW_SERVICE);
            boolean dot = text == null;
            if (view != null && viewDot != dot) hide(); // puntino e pillola hanno misure diverse: si ricrea
            if (view == null) {
                viewDot = dot;
                view = new TextView(a);
                view.setTextColor(U.BG);
                view.setTextSize(13);
                view.setGravity(Gravity.CENTER);
                view.setSingleLine(true);
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
                view.setText("");
                view.setPadding(0, 0, 0, 0);
                view.setWidth(U.dp(a, 12));
                view.setHeight(U.dp(a, 12));
                view.setBackground(U.box(a, U.INK, 0, 6));
                view.setContentDescription("Tempo al telefono, ridotto");
            } else {
                view.setMaxWidth(U.dp(a, 300));
                view.setMinHeight(U.dp(a, 32));
                view.setPadding(U.dp(a, 14), 0, U.dp(a, 14), 0);
                view.setText(text.startsWith("◷") ? text : "●  " + text);
                view.setBackground(U.box(a, U.INK, 0, 16));
                view.setContentDescription("Al telefono da " + text + ". Tocca per ridurlo per 10 minuti.");
            }
            view.requestLayout();
        } catch (Exception ignored) {
            view = null;
        }
    }

    static void hide() {
        ScopePill.hide();
        hideOwn();
    }

    private static void hideOwn() {
        if (view == null) return;
        try {
            WindowManager wm = (WindowManager) view.getContext().getSystemService(Context.WINDOW_SERVICE);
            wm.removeView(view);
        } catch (Exception ignored) {}
        view = null;
    }
}
