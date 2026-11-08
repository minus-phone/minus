package it.essenziale.launcher;

/**
 * Pausa e scopo (0.10), logica pura: il livello di attrito di un'app, di giorno e di notte,
 * i tempi tra cui scegliere, il preavviso e i testi degli avvisi. Coperta dal test (sezione H).
 */
public final class Scopo {
    private Scopo() {}

    public static final int NONE = 0, PAUSE = 1, SCOPE = 2;
    public static final int[] DAY_MIN = {5, 10, 15, 30};
    public static final int[] NIGHT_MIN = {2, 5};
    public static final int EXTRA_MIN = 5;
    /**
     * 0.15: niente motivazioni già pronte, lo scopo si scrive sempre. Dopo 2 lettere compaiono al
     * massimo 3 motivazioni già scritte dalla persona, le più recenti prima, mai quelle suggerite da −minus.
     * past: dalla più recente; exclude: le vecchie motivazioni preimpostate, che non sono parole sue.
     */
    public static java.util.List<String> suggest(java.util.List<String> past, String typed, String[] exclude) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        String q = typed == null ? "" : typed.trim().toLowerCase(java.util.Locale.ITALY);
        if (q.length() < 2) return out;
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (String e : exclude) seen.add(e.toLowerCase(java.util.Locale.ITALY));
        for (String p : past) {
            if (p == null) continue;
            String t = p.trim(), low = t.toLowerCase(java.util.Locale.ITALY);
            if (t.length() == 0 || seen.contains(low) || low.equals(q) || !low.contains(q)) continue;
            seen.add(low);
            out.add(t);
            if (out.size() == 3) break;
        }
        return out;
    }

    public static final String[] DAY_PURPOSES = {"Rispondere a un messaggio", "Pubblicare", "Cercare qualcosa", "Solo guardare"};
    public static final String[] NIGHT_PURPOSES = {"Rispondere a un messaggio", "Cercare una cosa", "Non lo so"};

    /**
     * Il livello effettivo. Di notte ogni app con la pausa (o scelta solo per la notte) diventa
     * «pausa e scopo»; le app senza pausa restano libere.
     */
    public static int level(int dayLevel, boolean nightOnly, boolean night, boolean nightScopeOn) {
        if (night && nightScopeOn && (dayLevel != NONE || nightOnly)) return SCOPE;
        return dayLevel;
    }

    /** Minuti di preavviso prima della fine: 5 → 1, 10–15 → 2, 30 → 5; nessuno sotto i 3 minuti. */
    public static int warnLead(int minutes) {
        if (minutes < 3) return 0;
        if (minutes <= 5) return 1;
        if (minutes <= 15) return 2;
        return 5;
    }

    /**
     * Cosa dire allo scadere, in base a quante volte si è già prorogato: {titolo, testo, azione no, azione sì}.
     * Dalla terza volta nessun avviso: resta solo la pillola. null = nessun avviso.
     */
    public static String[] expired(int extensions, String label, int totalMin, String purpose) {
        String p = purpose == null || purpose.length() == 0 ? "" : " Volevi: " + lower(purpose) + ".";
        if (extensions == 0) return new String[]{totalMin + " minuti passati", p.trim(), "Chiudo", "Altri " + EXTRA_MIN + " min"};
        if (extensions == 1) return new String[]{"Ti serve ancora?", ("Sei su " + label + " da " + totalMin + " minuti." + p).trim(), "No, chiudo", "Sì, altri " + EXTRA_MIN + " min"};
        return null;
    }

    /** Testo della pillola durante uno scopo: «Instagram · 6 min», «tempo scaduto», «+3 min». */
    public static String pill(String label, long msLeft) {
        if (msLeft > 0) {
            long m = (msLeft + 59999) / 60000;
            return "◷  " + label + " · " + m + " min";
        }
        long over = -msLeft / 60000;
        return "◷  " + label + (over < 1 ? " · tempo scaduto" : " · +" + over + " min");
    }

    static String lower(String s) {
        return s.length() == 0 ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
