package it.essenziale.launcher;

import java.util.HashMap;
import java.util.Map;

/**
 * Tempo sopra le app (0.9), logica pura: quale app pesa di più nella sessione, cosa dice la pillola
 * e quando si vede. Coperta dal test di non regressione (sezione G).
 */
public final class Tempo {
    private Tempo() {}

    public static final int HIDE = 0, DOT = 1, PILL = 2;
    public static final int FG = 1, BG = 2; // UsageEvents: MOVE_TO_FOREGROUND, MOVE_TO_BACKGROUND
    public static final long DOT_MS = 10 * 60000L;

    /**
     * L'app rimasta più a lungo in primo piano tra start e now. Gli eventi sono in ordine di tempo;
     * quelli prima di start servono solo a sapere quale app era già aperta. ignore = Essenziale.
     * Restituisce {pacchetto, millisecondi} oppure null.
     */
    public static String[] top(long[] t, String[] p, int[] type, long start, long now, String ignore) {
        Map<String, Long> ms = new HashMap<String, Long>();
        String cur = null;
        long since = 0;
        for (int i = 0; i < t.length; i++) {
            if (t[i] > now) break;
            if (type[i] == FG) {
                if (cur != null) add(ms, cur, since, t[i], start);
                cur = p[i];
                since = t[i];
            } else if (type[i] == BG && p[i] != null && p[i].equals(cur)) {
                add(ms, cur, since, t[i], start);
                cur = null;
            }
        }
        if (cur != null) add(ms, cur, since, now, start);
        String best = null;
        long bestMs = 0;
        for (Map.Entry<String, Long> e : ms.entrySet()) {
            if (e.getKey().equals(ignore)) continue;
            if (e.getValue() > bestMs) { best = e.getKey(); bestMs = e.getValue(); }
        }
        return best == null ? null : new String[]{best, String.valueOf(bestMs)};
    }

    private static void add(Map<String, Long> ms, String pkg, long from, long to, long start) {
        long d = to - Math.max(from, start);
        if (d <= 0) return;
        Long o = ms.get(pkg);
        ms.put(pkg, (o == null ? 0 : o) + d);
    }

    /** "25 min", oppure "25 min · 18 su Instagram" se un'app pesa più di metà della sessione. */
    public static String pillText(int minutes, String appLabel, int appMinutes) {
        String tot = minutes < 60 ? minutes + " min" : (minutes / 60) + " h" + (minutes % 60 > 0 ? " " + (minutes % 60) : "");
        if (appLabel == null || appLabel.length() == 0 || appMinutes <= 0 || appMinutes * 2 <= minutes) return tot;
        return tot + " · " + appMinutes + " su " + appLabel;
    }

    /**
     * Cosa mostrare: niente, il puntino o la pillola. Mai sopra Essenziale (in home c'è già la riga),
     * mai sotto la soglia (sessionMinutes = -1), mai senza permesso; il puntino dura 10 minuti dal tocco.
     */
    /** 0.15: finito uno scopo, la pillola del tempo tace per 10 minuti: un segnale alla volta. */
    public static final long QUIET_AFTER_SCOPE_MS = 10 * 60000L;

    public static boolean quietAfterScope(long scopeEndedAt, long now) {
        return scopeEndedAt > 0 && now - scopeEndedAt >= 0 && now - scopeEndedAt < QUIET_AFTER_SCOPE_MS;
    }

    public static int state(boolean pillOn, boolean canDraw, boolean sessionOn, int sessionMinutes,
                            boolean essenzialeVisible, boolean paused, long dotUntil, long now) {
        if (!pillOn || !canDraw || !sessionOn || sessionMinutes < 0 || essenzialeVisible || paused) return HIDE;
        return now < dotUntil ? DOT : PILL;
    }
}
