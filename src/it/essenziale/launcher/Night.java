package it.essenziale.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

/** Di notte (0.10): stato e preferenze. La decisione è in Notte (pura). */
final class Night {
    private Night() {}

    static final String K_ON = "night_on", K_MODE = "night_mode", K_ALT = "night_alt", K_SCOPE = "night_scope",
            K_APPS = "night_apps", K_TRIAL = "night_trial_until", K_START = "night_start", K_END = "night_end",
            K_OFF_AT = "screen_off_at", K_UNLOCKS = "night_unlocks", K_ALTS = "night_alts", K_OPENS = "night_opens",
            K_OPEN_MIN = "night_open_min", K_REPORT_SEEN = "night_report_seen";
    static final String DEF_ALT = "Resto al buio e ascolto il respiro";
    static final long WEEK = 7L * 24 * 3600000L;

    static SharedPreferences p(Context c) { return U.prefs(c); }
    static boolean on(Context c) { return p(c).getBoolean(K_ON, false); }
    static String mode(Context c) { return p(c).getString(K_MODE, Notte.AUTO); }
    static String alternative(Context c) { return p(c).getString(K_ALT, DEF_ALT); }
    static boolean scopeOn(Context c) { return p(c).getBoolean(K_SCOPE, true); }

    static java.util.Set<String> nightApps(Context c) {
        java.util.Set<String> out = new java.util.HashSet<String>();
        for (String[] r : U.rows(p(c).getString(K_APPS, ""))) if (r.length > 0) out.add(r[0]);
        return out;
    }

    static void setNightApp(Context c, String pkg, boolean on) {
        java.util.Set<String> s = nightApps(c);
        if (on) s.add(pkg); else s.remove(pkg);
        StringBuilder b = new StringBuilder();
        for (String x : s) b.append(x).append('\n');
        p(c).edit().putString(K_APPS, b.toString()).apply();
    }

    static boolean active(Context c) {
        long now = System.currentTimeMillis();
        return Notte.active(on(c), mode(c), Calendar.getInstance().get(Calendar.HOUR_OF_DAY), p(c).getLong(K_END, 0), now);
    }

    /** Prova di una settimana: accende Di notte e azzera i conteggi. */
    static void startTrial(Context c) {
        p(c).edit().putBoolean(K_ON, true).putLong(K_TRIAL, System.currentTimeMillis() + WEEK).putBoolean(K_REPORT_SEEN, false)
                .putInt(K_UNLOCKS, 0).putInt(K_ALTS, 0).putInt(K_OPENS, 0).putInt(K_OPEN_MIN, 0).apply();
    }

    static boolean trialOver(Context c) {
        long t = p(c).getLong(K_TRIAL, 0);
        return on(c) && t > 0 && System.currentTimeMillis() > t && !p(c).getBoolean(K_REPORT_SEEN, false);
    }

    static void count(Context c, String key, int add) {
        p(c).edit().putInt(key, p(c).getInt(key, 0) + add).apply();
    }

    static void screenOff(Context c) {
        p(c).edit().putLong(K_OFF_AT, System.currentTimeMillis()).apply();
    }

    /**
     * Allo sblocco: in automatico una pausa lunga della sera apre la notte; se è notte e la pausa
     * è stata lunga, restituisce true (va mostrata la schermata della notte).
     */
    static boolean onUnlock(Context c) {
        if (!on(c)) return false;
        long now = System.currentTimeMillis(), off = p(c).getLong(K_OFF_AT, 0);
        if (off <= 0) return false;
        long gapMin = (now - off) / 60000L;
        p(c).edit().putLong(K_OFF_AT, 0).apply(); // un solo sblocco per pausa (SCREEN_ON e USER_PRESENT)
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(off);
        if (Notte.AUTO.equals(mode(c)) && !active(c) && Notte.opensNight(k.get(Calendar.HOUR_OF_DAY), gapMin)) {
            Calendar seven = Calendar.getInstance();
            seven.setTimeInMillis(now);
            if (seven.get(Calendar.HOUR_OF_DAY) >= 7) seven.add(Calendar.DAY_OF_YEAR, 1);
            seven.set(Calendar.HOUR_OF_DAY, 7); seven.set(Calendar.MINUTE, 0); seven.set(Calendar.SECOND, 0);
            long end = Notte.end(now, U.nextAlarmTime(c), seven.getTimeInMillis());
            p(c).edit().putLong(K_START, off).putLong(K_END, end).apply();
        }
        boolean show = Notte.nightUnlock(active(c), gapMin);
        if (show) count(c, K_UNLOCKS, 1);
        return show;
    }
}
