package it.essenziale.launcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Regole pure (0.12, wireframe 172, 173, 173b), senza Android, verificate dai test.
 * - «Una cosa adesso»: una sola attività di oggi, mai la lista; «Più tardi» passa alla successiva
 *   la volta dopo; due «Più tardi» di fila spengono la proposta per la giornata.
 * - Sblocco senza scopo: al terzo sblocco in 10 minuti senza aprire niente, al massimo 3 volte al
 *   giorno, mai di notte, mai in chiamata o con la navigazione.
 */
public final class Cosa {
    private Cosa() {}

    public static final int MAX_LATER = 2;
    public static final long WINDOW_MS = 10 * 60 * 1000L;
    public static final int MAX_ASK_DAY = 3, EMPTY_BEFORE = 2;

    /** Risposte allo sblocco senza scopo, nell'ordine del wireframe 173. */
    public static final String[] ANSWERS = {"Cercavo qualcosa", "Aspettavo", "Mi annoiavo", "Per staccare", "Non lo so"};
    public static final String BORED = "Mi annoiavo";

    /**
     * Quale attività proporre. ids: le attività di oggi non fatte, nell'ordine della home.
     * skipped: quelle già rimandate oggi; later: «Più tardi» di fila oggi.
     * Restituisce l'indice in ids, -1 se oggi non si propone (o non c'è niente).
     */
    public static int pick(List<String> ids, List<String> skipped, int later) {
        if (later >= MAX_LATER) return -1;
        for (int i = 0; i < ids.size(); i++) if (!skipped.contains(ids.get(i))) return i;
        return ids.isEmpty() ? -1 : 0; // tutte rimandate una volta: si riparte dalla prima
    }

    /** True se la proposta per oggi è spenta (due «Più tardi» di fila). */
    public static boolean offToday(int later) { return later >= MAX_LATER; }

    /** Aggiorna l'elenco degli sblocchi a vuoto: tiene solo quelli dentro la finestra. */
    public static List<Long> window(List<Long> empties, long now) {
        List<Long> out = new ArrayList<Long>();
        for (Long t : empties) if (t != null && now - t <= WINDOW_MS && t <= now) out.add(t);
        return out;
    }

    /** Chiedere «Per cosa l'hai preso?» a questo sblocco? empties: sblocchi precedenti senza aprire niente. */
    public static boolean ask(List<Long> empties, long now, int askedToday, boolean night, boolean busy) {
        if (night || busy || askedToday >= MAX_ASK_DAY) return false;
        return window(empties, now).size() >= EMPTY_BEFORE;
    }

    /** Dopo la risposta: solo «Mi annoiavo» porta la proposta; le altre chiudono la scheda. */
    public static boolean proposeAfter(String answer) { return BORED.equals(answer); }

    /** Durata della pausa: 4, 6 o 8 secondi (8 di default). */
    public static int pauseSeconds(int saved) { return saved == 4 || saved == 6 || saved == 8 ? saved : 8; }

    public static int nextPauseSeconds(int cur) { return cur == 4 ? 6 : cur == 6 ? 8 : 4; }
}
