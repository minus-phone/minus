package it.essenziale.launcher;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * I tre controlli (0.15, wireframe 211–216). Regole pure, senza Android, verificate dai test.
 * - A 14 giorni, a 1 mese e a 3 mesi dal primo giorno di −minus; poi basta.
 * - Una riga in home per 7 giorni dalla data; se non la apri sparisce e si passa al successivo.
 * - Al massimo 3 numeri, uno per obiettivo acceso. Il primo controllo dà i totali; dal secondo
 *   la media al giorno delle ultime 2 settimane accanto a quella della prima settimana.
 * - Mai numeri sul sonno: solo sblocchi del telefono di notte.
 * I conteggi giornalieri usano lo stesso formato del registro della beta (Registro), ma restano
 * sempre e solo sul telefono.
 */
public final class Controlli {
    private Controlli() {}

    public static final int COUNT = 3, SHOW_DAYS = 7;
    /** I nomi degli obiettivi, come in Goals.NAMES (qui senza Android, per i test). */
    public static final String[] GOALS = {"Meno notifiche", "Meno doomscrolling", "Meno interferenze sul sonno"};
    /** I conteggi servono fino al terzo controllo (3 mesi) e alla sua settimana in home. */
    public static final int KEEP_DAYS = 100;

    /** Il giorno del controllo n (1, 2, 3), da quello d'inizio (yyyy-mm-dd). */
    public static String due(String start, int n) {
        Calendar k = cal(start);
        if (n == 1) k.add(Calendar.DAY_OF_YEAR, 14);
        else if (n == 2) k.add(Calendar.MONTH, 1);
        else k.add(Calendar.MONTH, 3);
        return fmt(k);
    }

    /**
     * Il controllo da mostrare oggi, 0 se nessuno. done[i] = il controllo i+1 è stato aperto e chiuso;
     * off = «Non mostrarmeli più».
     */
    public static int current(String start, String today, boolean[] done, boolean off) {
        if (off || start == null || !Registro.isDay(start) || !Registro.isDay(today)) return 0;
        for (int n = 1; n <= COUNT; n++) {
            String d = due(start, n);
            if (done[n - 1]) continue;
            if (today.compareTo(d) >= 0 && today.compareTo(plus(d, SHOW_DAYS)) < 0) return n;
        }
        return 0;
    }

    /** Il prossimo controllo dopo n, 0 se n era l'ultimo. */
    public static int next(int n) { return n < COUNT ? n + 1 : 0; }

    /** Le righe dei numeri: {obiettivo, numero grande, riga sotto, prima settimana o null}. */
    public static List<String[]> numbers(String log, String start, int n, boolean notif, boolean doom, boolean sleep) {
        List<String[]> out = new ArrayList<String[]>();
        String d = due(start, n);
        LinkedHashMap<String, LinkedHashMap<String, String>> days = Registro.parse(log);
        if (n == 1) {
            int span = between(start, d);
            if (notif) out.add(new String[]{GOALS[0], String.valueOf(sum(days, Registro.NOTIF_WAIT, start, d)),
                    "notifiche hanno aspettato invece di interromperti", null});
            if (doom) {
                int v = sum(days, Registro.PAUSE_NOT_NOW, start, d);
                out.add(new String[]{GOALS[1], v + (v == 1 ? " volta" : " volte"), "hai scelto «Non ora» davanti a un'app", null});
            }
            if (sleep) {
                double v = sum(days, Registro.NIGHT_UNLOCKS, start, d) / (double) Math.max(1, span);
                out.add(new String[]{GOALS[2], unlocks(v), "a notte, in media, tra l'ora di dormire e la sveglia", null});
            }
        } else {
            String from = plus(d, -14), week = plus(start, 7);
            if (from.compareTo(week) < 0) from = week;
            int span = Math.max(1, between(from, d));
            if (notif) out.add(new String[]{GOALS[0], perDay(sum(days, Registro.NOTIF_WAIT, from, d) / (double) span),
                    "notifiche hanno aspettato", perDay(sum(days, Registro.NOTIF_WAIT, start, week) / 7.0)});
            if (doom) out.add(new String[]{GOALS[1], perDay(sum(days, Registro.PAUSE_NOT_NOW, from, d) / (double) span),
                    "volte «Non ora» davanti a un'app", perDay(sum(days, Registro.PAUSE_NOT_NOW, start, week) / 7.0)});
            if (sleep) out.add(new String[]{GOALS[2], unlocks(sum(days, Registro.NIGHT_UNLOCKS, from, d) / (double) span),
                    "a notte, in media", night(sum(days, Registro.NIGHT_UNLOCKS, start, week) / 7.0)});
        }
        return out;
    }

    /** «9 dicembre». */
    public static String label(String day) {
        String[] m = {"gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre"};
        Calendar k = cal(day);
        return k.get(Calendar.DAY_OF_MONTH) + " " + m[k.get(Calendar.MONTH)];
    }

    public static String plus(String day, int days) {
        Calendar k = cal(day);
        k.add(Calendar.DAY_OF_YEAR, days);
        return fmt(k);
    }

    /** Giorni da a (incluso) a b (escluso). */
    public static int between(String a, String b) {
        long ms = cal(b).getTimeInMillis() - cal(a).getTimeInMillis();
        return (int) Math.round(ms / 86400000.0);
    }

    static String num(double v) {
        if (v >= 10 || v == Math.rint(v)) return String.valueOf(Math.round(v));
        return String.format(Locale.ITALY, "%.1f", v);
    }

    static String perDay(double v) { return num(v) + " al giorno"; }

    static String unlocks(double v) { String s = num(v); return s + ("1".equals(s) ? " sblocco" : " sblocchi"); }

    static String night(double v) { return num(v) + " a notte"; }

    /** Somma di una colonna tra a (incluso) e b (escluso). */
    static int sum(LinkedHashMap<String, LinkedHashMap<String, String>> days, String key, String a, String b) {
        int s = 0;
        for (Map.Entry<String, LinkedHashMap<String, String>> e : days.entrySet()) {
            if (e.getKey().compareTo(a) < 0 || e.getKey().compareTo(b) >= 0) continue;
            try { s += Integer.parseInt(e.getValue().get(key) == null ? "0" : e.getValue().get(key)); } catch (NumberFormatException ignored) {}
        }
        return s;
    }

    private static Calendar cal(String day) {
        Calendar k = Calendar.getInstance();
        k.clear();
        k.set(Integer.parseInt(day.substring(0, 4)), Integer.parseInt(day.substring(5, 7)) - 1, Integer.parseInt(day.substring(8, 10)), 12, 0, 0);
        return k;
    }

    private static String fmt(Calendar k) {
        return String.format(Locale.ROOT, "%04d-%02d-%02d", k.get(Calendar.YEAR), k.get(Calendar.MONTH) + 1, k.get(Calendar.DAY_OF_MONTH));
    }
}
