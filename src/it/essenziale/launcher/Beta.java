package it.essenziale.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Registro per la beta (0.15): il collegamento tra Android e le regole pure di Registro.
 * Spento di default. Acceso, conta solo numeri del giorno; non registra nulla prima né dopo.
 * Il registro resta sul telefono e si cancella con un tocco o spegnendolo.
 */
final class Beta {
    private Beta() {}

    static final String K_ON = "beta_on", K_LOG = "beta_log", K_CODE = "beta_code", K_SINCE = "beta_since", K_LAST = "beta_last_unlock";

    static boolean on(Context c) { return U.prefs(c).getBoolean(K_ON, false); }

    static synchronized void setOn(Context c, boolean on) {
        SharedPreferences.Editor e = U.prefs(c).edit().putBoolean(K_ON, on);
        if (on) {
            if (!U.prefs(c).contains(K_CODE)) e.putString(K_CODE, newCode());
            if (!U.prefs(c).contains(K_SINCE)) e.putLong(K_SINCE, System.currentTimeMillis());
        } else {
            e.remove(K_LOG).remove(K_SINCE); // spento: niente resta sul telefono, il codice sì per ripartire
        }
        e.commit();
        if (on) snapshot(c);
    }

    static String code(Context c) {
        String s = U.prefs(c).getString(K_CODE, null);
        if (s == null) { s = newCode(); U.prefs(c).edit().putString(K_CODE, s).apply(); }
        return s;
    }

    static String log(Context c) { return U.prefs(c).getString(K_LOG, ""); }

    static long since(Context c) { return U.prefs(c).getLong(K_SINCE, 0); }

    /** Aggiunge 1 a un conteggio di oggi, se il registro è acceso. */
    static void add(Context c, String key) {
        if (c == null || !on(c)) return;
        synchronized (Beta.class) {
            String log = Registro.add(log(c), today(), key, 1);
            U.prefs(c).edit().putString(K_LOG, Registro.prune(log, oldest())).apply();
        }
    }

    /** Lo stato di oggi: −minus è la home? quali obiettivi sono accesi? */
    static void snapshot(Context c) {
        if (c == null || !on(c)) return;
        synchronized (Beta.class) {
            String d = today(), log = log(c);
            log = Registro.set(log, d, Registro.HOME, Onboarding.isDefaultHome(c) ? "1" : "0");
            log = Registro.set(log, d, Registro.GOALS, Registro.goals(Goals.on(c, Goals.NOTIF), Goals.on(c, Goals.DOOM), Goals.on(c, Goals.SLEEP)));
            U.prefs(c).edit().putString(K_LOG, Registro.prune(log, oldest())).apply();
        }
    }

    /** Dal filtro, a ogni sblocco: lo conta, e conta a parte quelli tra le 23 e le 6. */
    static void unlocked(Context c) {
        if (!on(c)) return;
        long now = System.currentTimeMillis(), prev = U.prefs(c).getLong(K_LAST, 0);
        U.prefs(c).edit().putLong(K_LAST, now).apply();
        if (now - prev < 3000) return; // schermo acceso e sblocco arrivano insieme: uno solo
        add(c, Registro.UNLOCKS);
        if (Notte.fixed(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))) add(c, Registro.NIGHT_UNLOCKS);
        snapshot(c);
    }

    static void clear(Context c) {
        U.prefs(c).edit().remove(K_LOG).putLong(K_SINCE, System.currentTimeMillis()).commit();
        snapshot(c);
    }

    static String export(Context c) {
        snapshot(c);
        String v = "?";
        try { v = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; } catch (Exception ignored) {}
        return Registro.export(log(c), code(c), v);
    }

    static String today() { return day(0); }

    private static String oldest() { return day(-(Registro.KEEP_DAYS - 1)); }

    private static String day(int offset) {
        Calendar k = Calendar.getInstance();
        k.add(Calendar.DAY_OF_YEAR, offset);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(k.getTimeInMillis()));
    }

    private static String newCode() {
        java.security.SecureRandom r = new java.security.SecureRandom();
        int[] n = new int[6];
        for (int i = 0; i < n.length; i++) n[i] = r.nextInt(1 << 20);
        return Registro.code(n);
    }
}
