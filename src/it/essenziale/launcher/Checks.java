package it.essenziale.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * I tre controlli (0.15) e i conteggi giornalieri che li alimentano: il collegamento tra Android
 * e le regole pure di Controlli. I conteggi restano sempre e solo sul telefono, in un registro
 * separato da quello della beta (che invece è spento di default e si invia).
 */
final class Checks {
    private Checks() {}

    static final String K_LOG = "checks_log", K_START = "checks_start", K_OFF = "checks_off",
            K_DONE = "checks_done_", K_ANSWER = "checks_answer_";

    /** Aggiunge 1 a un conteggio di oggi. Sempre attivo, mai inviato. */
    static void add(Context c, String key) {
        if (c == null) return;
        synchronized (Checks.class) {
            String log = Registro.add(U.prefs(c).getString(K_LOG, ""), today(), key, 1);
            U.prefs(c).edit().putString(K_LOG, Registro.prune(log, day(-(Controlli.KEEP_DAYS - 1)))).apply();
        }
    }

    /** Allo sblocco: conta quelli tra l'ora di dormire e la sveglia (o, senza orari, tra le 23 e le 6). */
    static void unlocked(Context c) {
        boolean night = Night.on(c) ? (Night.sleeping(c) || Night.active(c))
                : Notte.fixed(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
        if (night) add(c, Registro.NIGHT_UNLOCKS);
    }

    /** Il primo giorno di −minus per i controlli: fissato la prima volta che la configurazione è fatta. */
    static String start(Context c) {
        SharedPreferences p = U.prefs(c);
        String s = p.getString(K_START, null);
        if (s == null && p.getBoolean(Onboarding.K_DONE, false)) {
            s = today();
            p.edit().putString(K_START, s).apply();
        }
        return s;
    }

    static boolean done(Context c, int n) { return U.prefs(c).getBoolean(K_DONE + n, false); }

    /** Il controllo da mostrare in home oggi, 0 se nessuno. */
    static int current(Context c) {
        String s = start(c);
        if (s == null) return 0;
        boolean[] d = new boolean[Controlli.COUNT];
        for (int i = 0; i < d.length; i++) d[i] = done(c, i + 1);
        return Controlli.current(s, today(), d, U.prefs(c).getBoolean(K_OFF, false));
    }

    static java.util.List<String[]> numbers(Context c, int n) {
        return Controlli.numbers(U.prefs(c).getString(K_LOG, ""), start(c), n,
                Goals.on(c, Goals.NOTIF), Goals.on(c, Goals.DOOM), Goals.on(c, Goals.SLEEP));
    }

    static void finish(Context c, int n, String answer) {
        SharedPreferences.Editor e = U.prefs(c).edit().putBoolean(K_DONE + n, true);
        if (answer != null) e.putString(K_ANSWER + n, answer);
        e.apply();
    }

    static String dueLabel(Context c, int n) { return Controlli.label(Controlli.due(start(c), n)); }

    static String today() { return day(0); }

    private static String day(int offset) {
        Calendar k = Calendar.getInstance();
        k.add(Calendar.DAY_OF_YEAR, offset);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(k.getTimeInMillis()));
    }
}
