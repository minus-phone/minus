package it.essenziale.launcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Task in home e in settimana (0.15). Regole pure, senza Android, verificate dai test.
 * - In home i task completati oggi si vedono sbiaditi fino allo sblocco successivo, poi spariscono.
 * - Da 3 completati in su vanno tutti in un elenco chiuso «n task completati», sotto quelli da fare,
 *   e lì non spariscono.
 * - Con un tocco lungo un task si sposta su o giù nella giornata.
 */
public final class Ordine {
    private Ordine() {}

    public static final int ACCORDION_FROM = 3;

    public static boolean accordion(int doneToday) { return doneToday >= ACCORDION_FROM; }

    /** Un task completato si vede in home? doneAt e lastUnlock in millisecondi, todayStart = mezzanotte. */
    public static boolean doneVisibleInHome(long doneAt, long lastUnlock, long todayStart, int doneToday) {
        if (doneAt < todayStart) return false;
        return accordion(doneToday) || doneAt > lastUnlock;
    }

    /** Sposta l'elemento i di delta posizioni (−1 su, +1 giù); fuori dai bordi resta dov'è. */
    public static List<String> move(List<String> ids, int i, int delta) {
        List<String> out = new ArrayList<String>(ids);
        int j = i + delta;
        if (i < 0 || i >= out.size() || j < 0 || j >= out.size()) return out;
        String x = out.remove(i);
        out.add(j, x);
        return out;
    }

    /** Il titolo dell'elenco chiuso. */
    public static String accordionTitle(int n) { return n + (n == 1 ? " task completato" : " task completati"); }
}
