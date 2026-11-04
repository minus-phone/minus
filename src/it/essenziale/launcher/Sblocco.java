package it.essenziale.launcher;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.media.AudioManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Sblocco senza scopo (0.12, wireframe 173): spento di default. Al terzo sblocco in 10 minuti senza
 * aprire niente la home chiede «Per cosa l'hai preso?». Le regole stanno in {@link Cosa}.
 * Le risposte restano sul telefono (registro locale) e serviranno ai riepiloghi.
 */
final class Sblocco {
    private Sblocco() {}

    static final String K_ON = "unlock_ask_on", K_LAST = "unlock_last", K_EMPTY = "unlock_empty",
            K_ASK_AT = "unlock_ask_at", K_ASK_DAY = "unlock_ask_day", K_ASK_N = "unlock_ask_n",
            K_LOG = "unlock_log", K_SNOOZE = "unlock_snooze";
    static final long SHOW_MS = 3 * 60 * 1000L, SNOOZE_MS = 60 * 60 * 1000L;
    private static final String MAPS = "com.google.android.apps.maps";

    static boolean on(Context c) { return U.prefs(c).getBoolean(K_ON, false) && Goals.on(c, Goals.NOTIF); }

    /** Chiamato dal filtro a ogni sblocco. */
    static void unlocked(Context c) {
        if (!on(c)) return;
        android.content.SharedPreferences p = U.prefs(c);
        long now = System.currentTimeMillis(), prev = p.getLong(K_LAST, 0);
        if (prev > 0 && now - prev < 3000) return; // schermo acceso e sblocco arrivano insieme: uno solo
        List<Long> empties = new ArrayList<Long>();
        for (String s : p.getString(K_EMPTY, "").split(",")) try { if (s.length() > 0) empties.add(Long.parseLong(s)); } catch (Exception ignored) {}
        if (prev > 0 && !openedSince(c, prev, now)) empties.add(prev); else empties.clear();
        empties = Cosa.window(empties, now);
        long today = U.startOfDay(0);
        int asked = p.getLong(K_ASK_DAY, 0) == today ? p.getInt(K_ASK_N, 0) : 0;
        boolean ask = now >= p.getLong(K_SNOOZE, 0) && Cosa.ask(empties, now, asked, Night.active(c), busy(c, prev, now));
        android.content.SharedPreferences.Editor e = p.edit().putLong(K_LAST, now);
        if (ask) {
            e.putLong(K_ASK_AT, now).putLong(K_ASK_DAY, today).putInt(K_ASK_N, asked + 1).putString(K_EMPTY, "");
        } else {
            StringBuilder b = new StringBuilder();
            for (Long t : empties) { if (b.length() > 0) b.append(','); b.append(t); }
            e.putString(K_EMPTY, b.toString());
        }
        e.apply();
    }

    /** La domanda va mostrata in home adesso? */
    static boolean pending(Context c) {
        return on(c) && System.currentTimeMillis() - U.prefs(c).getLong(K_ASK_AT, 0) < SHOW_MS;
    }

    static void answer(Context c, String a) {
        String line = System.currentTimeMillis() + " | " + a + "\n";
        String old = U.prefs(c).getString(K_LOG, "");
        if (old.length() > 4000) old = old.substring(old.indexOf('\n', old.length() - 3000) + 1);
        U.prefs(c).edit().putString(K_LOG, old + line).putLong(K_ASK_AT, 0).apply();
    }

    static void snooze(Context c) {
        U.prefs(c).edit().putLong(K_ASK_AT, 0).putLong(K_SNOOZE, System.currentTimeMillis() + SNOOZE_MS).apply();
    }

    /** Hai aperto qualcosa tra i due sblocchi? Da minus, o (con i dati di utilizzo) da notifiche e recenti. */
    private static boolean openedSince(Context c, long from, long to) {
        if (U.prefs(c).getLong(U.K_LAST_LAUNCH, 0) >= from) return true;
        if (!Pill.usageGranted(c)) return false;
        try {
            UsageStatsManager um = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            UsageEvents ev = um.queryEvents(from, to);
            UsageEvents.Event x = new UsageEvents.Event();
            while (ev.hasNextEvent()) {
                ev.getNextEvent(x);
                if (x.getEventType() != UsageEvents.Event.MOVE_TO_FOREGROUND) continue;
                String pkg = x.getPackageName();
                if (pkg == null || pkg.equals(c.getPackageName()) || pkg.startsWith("com.android.systemui")) continue;
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /** In chiamata o con la navigazione: mai domande. */
    private static boolean busy(Context c, long from, long to) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am != null && am.getMode() != AudioManager.MODE_NORMAL) return true;
        } catch (Exception ignored) {}
        long since = to - 30 * 60 * 1000L;
        return MAPS.equals(U.prefs(c).getString(U.K_LAST_LAUNCH_PKG, "")) && U.prefs(c).getLong(U.K_LAST_LAUNCH, 0) >= since;
    }
}
