package it.essenziale.launcher;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;

import java.util.Set;

/**
 * Pausa e scopo (0.10, wireframe 140–146): livello per app, la sessione dichiarata (scopo e tempo),
 * il conto alla rovescia, il preavviso, lo scadere e il controllo delle app aperte fuori da
 * Essenziale con i dati di utilizzo. La logica delle scelte è in Scopo (pura).
 */
final class Scope {
    private Scope() {}

    static final String K_LEVEL = "scope_pkgs";        // pacchetti con «pausa e scopo»
    static final String K_PKG = "scope_pkg", K_LABEL = "scope_label", K_CLS = "scope_cls", K_PURPOSE = "scope_purpose",
            K_START = "scope_start", K_END = "scope_end", K_EXT = "scope_ext", K_WARNED = "scope_warned", K_EXPIRED = "scope_expired",
            K_LOG = "scope_log", K_PASSED = "scope_passed", K_PASSED_AT = "scope_passed_at";
    static final String ACT_MORE = "it.essenziale.launcher.SCOPE_MORE", ACT_OK = "it.essenziale.launcher.SCOPE_OK";
    static final int NOTE_ID = 5, ALERT_ID = 6;

    private static final Handler H = new Handler(Looper.getMainLooper());

    // ---------- Livelli ----------

    static Set<String> scopePkgs(Context c) {
        Set<String> out = new java.util.HashSet<String>();
        for (String[] r : U.rows(U.prefs(c).getString(K_LEVEL, ""))) if (r.length > 0) out.add(r[0]);
        return out;
    }

    static int dayLevel(Context c, String pkg) {
        if (scopePkgs(c).contains(pkg)) return Scopo.SCOPE;
        return U.isPaused(c, pkg) ? Scopo.PAUSE : Scopo.NONE;
    }

    /** Il livello che vale adesso: di notte la pausa diventa «pausa e scopo». */
    static int level(Context c, String pkg) {
        // 0.14: «Meno doomscrolling» spento: di giorno niente pausa; di notte restano le app scelte per la notte
        int day = Goals.on(c, Goals.DOOM) ? dayLevel(c, pkg) : Scopo.NONE;
        return Scopo.level(day, Night.nightApps(c).contains(pkg), Night.active(c), Night.scopeOn(c));
    }

    static void setLevel(Context c, String pkg, int level) {
        U.setPaused(c, pkg, level != Scopo.NONE);
        Set<String> s = scopePkgs(c);
        if (level == Scopo.SCOPE) s.add(pkg); else s.remove(pkg);
        StringBuilder b = new StringBuilder("# pacchetti con «pausa e scopo»\n");
        for (String x : s) b.append(x).append('\n');
        U.prefs(c).edit().putString(K_LEVEL, b.toString()).apply();
    }

    // ---------- Sessione ----------

    static boolean active(Context c) { return U.prefs(c).getString(K_PKG, null) != null; }

    static boolean activeFor(Context c, String pkg) { return pkg != null && pkg.equals(U.prefs(c).getString(K_PKG, null)); }

    /** Dopo la pausa l'app si apre: per qualche secondo il controllo non la ferma di nuovo. */
    static void passed(Context c, String pkg) {
        U.prefs(c).edit().putString(K_PASSED, pkg).putLong(K_PASSED_AT, System.currentTimeMillis()).apply();
    }

    static boolean recentlyPassed(Context c, String pkg) {
        return pkg.equals(U.prefs(c).getString(K_PASSED, "")) && System.currentTimeMillis() - U.prefs(c).getLong(K_PASSED_AT, 0) < 15000L;
    }

    static void start(Context c, String pkg, String cls, String label, String purpose, int minutes) {
        long now = System.currentTimeMillis();
        U.prefs(c).edit().putString(K_PKG, pkg).putString(K_CLS, cls).putString(K_LABEL, label).putString(K_PURPOSE, purpose)
                .putLong(K_START, now).putLong(K_END, now + minutes * 60000L).putInt(K_EXT, 0)
                .putBoolean(K_WARNED, Scopo.warnLead(minutes) == 0).putBoolean(K_EXPIRED, false).apply();
        log(c, pkg, purpose, minutes, "aperta");
        if (Night.active(c)) Night.count(c, Night.K_OPENS, 1);
        passed(c, pkg);
        schedule(c, 1000);
    }

    static void extend(Context c) {
        android.content.SharedPreferences p = U.prefs(c);
        long now = System.currentTimeMillis();
        p.edit().putLong(K_END, Math.max(now, p.getLong(K_END, now)) + Scopo.EXTRA_MIN * 60000L).putInt(K_EXT, p.getInt(K_EXT, 0) + 1)
                .putBoolean(K_EXPIRED, false).putBoolean(K_WARNED, true).apply();
        cancelAlert(c);
        schedule(c, 500);
    }

    static void end(Context c, String how) {
        android.content.SharedPreferences p = U.prefs(c);
        String pkg = p.getString(K_PKG, null);
        if (pkg == null) return;
        int real = (int) ((System.currentTimeMillis() - p.getLong(K_START, 0)) / 60000L);
        log(c, pkg, p.getString(K_PURPOSE, ""), real, how);
        if (Night.active(c)) Night.count(c, Night.K_OPEN_MIN, real);
        p.edit().remove(K_PKG).remove(K_CLS).remove(K_LABEL).remove(K_PURPOSE).remove(K_START).remove(K_END).remove(K_EXT).apply();
        H.removeCallbacks(TICK);
        ScopePill.reset();
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(NOTE_ID);
            nm.cancel(ALERT_ID);
        } catch (Exception ignored) {}
        Pill.update(c);
    }

    /** Registro sul telefono per i riepiloghi: ora | app | scopo | minuti | esito. Ultime 200 righe. */
    static void log(Context c, String pkg, String purpose, int minutes, String how) {
        String old = U.prefs(c).getString(K_LOG, "");
        String line = System.currentTimeMillis() + "|" + pkg + "|" + (purpose == null ? "" : purpose.replace("|", "/").replace("\n", " ")) + "|" + minutes + "|" + how + "\n";
        String all = line + old;
        int n = 0, cut = all.length();
        for (int i = 0; i < all.length(); i++) if (all.charAt(i) == '\n' && ++n == 200) { cut = i + 1; break; }
        U.prefs(c).edit().putString(K_LOG, all.substring(0, cut)).apply();
    }

    static void notNow(Context c, String pkg) {
        log(c, pkg, "", 0, "non ora");
    }

    // ---------- Conto alla rovescia, preavviso, scadere ----------

    private static Context app;
    private static final Runnable TICK = new Runnable() {
        public void run() { if (app != null) tick(app); }
    };

    static void schedule(Context c, long delay) {
        app = c.getApplicationContext();
        H.removeCallbacks(TICK);
        H.postDelayed(TICK, delay);
    }

    static void tick(Context c) {
        android.content.SharedPreferences p = U.prefs(c);
        String pkg = p.getString(K_PKG, null);
        if (pkg == null) return;
        long now = System.currentTimeMillis(), end = p.getLong(K_END, now), start = p.getLong(K_START, now);
        int total = (int) Math.round((end - start) / 60000.0);
        String label = p.getString(K_LABEL, pkg), purpose = p.getString(K_PURPOSE, "");
        int ext = p.getInt(K_EXT, 0);
        int declared = total - ext * Scopo.EXTRA_MIN;
        if (!p.getBoolean(K_WARNED, false) && end - now <= Scopo.warnLead(declared) * 60000L) {
            p.edit().putBoolean(K_WARNED, true).apply();
            String[] w = Scopo.warn(Scopo.warnLead(declared), purpose);
            // 0.15: con «Mostra sopra altre app» la pillola si apre da sola (206); senza, l'avviso di Android (210)
            if (ScopePill.usable(c)) ScopePill.warn(c, w);
            else alert(c, w[0], w[1], w[2], w[3], ACT_OK, false);
        }
        if (!p.getBoolean(K_EXPIRED, false) && now >= end) {
            p.edit().putBoolean(K_EXPIRED, true).apply();
            int total0 = (int) ((now - start + 30000) / 60000L);
            if (ScopePill.usable(c)) {
                String[] t = Scopo.sheet(ext, label, total0, purpose);
                if (t != null) ScopePill.expired(c, t); // 207, 208; dalla terza volta solo la pillola aperta (209)
            } else {
                String[] t = Scopo.expired(ext, label, total0, purpose);
                if (t != null) alert(c, t[0], t[1], t[2], t[3], ACT_MORE, true);
            }
        }
        ScopePill.tick(c);
        ongoing(c, label, purpose, end - now);
        Pill.update(c);
        H.removeCallbacks(TICK);
        H.postDelayed(TICK, 15000);
    }

    /** Testo per la pillola durante uno scopo, oppure null. */
    static String pillText(Context c) {
        android.content.SharedPreferences p = U.prefs(c);
        if (p.getString(K_PKG, null) == null) return null;
        return Scopo.pill(p.getString(K_LABEL, ""), p.getLong(K_END, 0) - System.currentTimeMillis());
    }

    private static PendingIntent closeIntent(Context c) {
        Intent home = new Intent(c, MainActivity.class);
        home.putExtra("scope_done", true);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 31, home, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent broadcast(Context c, String action, int code) {
        Intent i = new Intent(c, ScopeReceiver.class);
        i.setAction(action);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Avviso in primo piano con una sola vibrazione breve. */
    private static void alert(Context c, String title, String text, String close, String other, String otherAction, boolean twice) {
        try {
            Notification.Builder b = new Notification.Builder(c)
                    .setSmallIcon(Icon.createWithResource("android", android.R.drawable.ic_lock_idle_alarm))
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setVibrate(new long[]{0})
                    .setCategory(Notification.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setContentIntent(closeIntent(c))
                    .addAction(new Notification.Action.Builder(null, close, closeIntent(c)).build())
                    .addAction(new Notification.Action.Builder(null, other, broadcast(c, otherAction, 32)).build());
            ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(ALERT_ID, b.build());
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            // 0.15 (210): allo scadere due tocchi, come con la pillola; al preavviso uno solo
            if (v != null) { if (twice) v.vibrate(new long[]{0, 60, 120, 60}, -1); else v.vibrate(40); }
        } catch (Exception ignored) {}
    }

    static void cancelAlert(Context c) {
        try { ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(ALERT_ID); } catch (Exception ignored) {}
    }

    /**
     * Nella tendina, in silenzio: app, scopo e tempo che resta, con «Ho finito». Da Android 16 chiede
     * di diventare la pillola nella barra di stato (Live Update), con il tempo che resta.
     */
    private static void ongoing(Context c, String label, String purpose, long msLeft) {
        try {
            long min = msLeft > 0 ? (msLeft + 59999) / 60000 : 0;
            String title = label + (msLeft > 0 ? " · " + min + " min" : " · tempo scaduto");
            Notification.Builder b = new Notification.Builder(c)
                    .setSmallIcon(Icon.createWithResource("android", android.R.drawable.ic_lock_idle_alarm))
                    .setContentTitle(title)
                    .setContentText(purpose.length() > 0 ? purpose : "Tempo che hai scelto")
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(false)
                    .setPriority(Notification.PRIORITY_LOW)
                    .setDefaults(0)
                    .setCategory(Notification.CATEGORY_PROGRESS)
                    .setContentIntent(closeIntent(c))
                    .addAction(new Notification.Action.Builder(null, "Ho finito", closeIntent(c)).build());
            if (android.os.Build.VERSION.SDK_INT >= 36) {
                // Live Update (Android 16): metodi recenti, chiamati per nome perché compiliamo con API vecchie
                try { b.getClass().getMethod("setRequestPromotedOngoing", boolean.class).invoke(b, true); } catch (Exception ignored) {}
                try { b.getClass().getMethod("setShortCriticalText", String.class).invoke(b, msLeft > 0 ? min + " min" : "fine"); } catch (Exception ignored) {}
            }
            ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTE_ID, b.build());
        } catch (Exception ignored) {}
    }

    // ---------- Controllo delle app aperte fuori da Essenziale (dati di utilizzo) ----------

    private static String lastFg = null;
    private static boolean guarding = false;
    private static final Runnable GUARD = new Runnable() {
        public void run() { if (app != null) guard(app); }
    };

    static void startGuard(Context c) {
        app = c.getApplicationContext();
        if (!Pill.usageGranted(app)) return;
        guarding = true;
        H.removeCallbacks(GUARD);
        H.postDelayed(GUARD, 1500);
    }

    static void stopGuard() {
        guarding = false;
        H.removeCallbacks(GUARD);
    }

    private static void guard(Context c) {
        if (!guarding) return;
        try {
            String fg = foreground(c);
            if (fg != null && !fg.equals(lastFg)) {
                String prev = lastFg;
                lastFg = fg;
                boolean own = fg.equals(c.getPackageName());
                // uno scopo scaduto finisce quando lasci l'app
                if (prev != null && activeFor(c, prev) && !own && U.prefs(c).getBoolean(K_EXPIRED, false)) end(c, "chiusa");
                if (!own && !activeFor(c, fg) && !recentlyPassed(c, fg) && level(c, fg) != Scopo.NONE) {
                    Intent i = new Intent(c, FrictionActivity.class);
                    i.putExtra("pkg", fg);
                    i.putExtra("label", U.appLabel(c, fg));
                    i.putExtra("outside", true);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    U.start(c, i);
                }
            }
        } catch (Exception ignored) {}
        H.postDelayed(GUARD, 1500);
    }

    /** L'app in primo piano secondo gli ultimi eventi d'uso (ultimi 10 secondi), o null. */
    static String foreground(Context c) {
        UsageStatsManager um = (UsageStatsManager) c.getSystemService("usagestats");
        long now = System.currentTimeMillis();
        UsageEvents ev = um.queryEvents(now - 10000, now);
        UsageEvents.Event e = new UsageEvents.Event();
        String last = null;
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e);
            if (e.getEventType() == Tempo.FG) last = e.getPackageName();
        }
        return last;
    }
}
