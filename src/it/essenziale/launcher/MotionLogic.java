package it.essenziale.launcher;

/**
 * Evolutiva 016 · le regole del movimento che non toccano Android (verificate dai test).
 * I valori che si vedono nel codice stanno in Motion, che rimanda qui.
 */
public final class MotionLogic {
    private MotionLogic() {}

    public static final int RAPIDO = 120, BASE = 240, LENTO = 480;
    /** «Rimuovi animazioni»: ogni movimento diventa una dissolvenza così breve (o un cambio diretto). */
    public static final int RIDOTTO = 80;
    /** Quanto resta spuntata una casella prima che la riga si chiuda; la seconda pressione la toglie. */
    public static final int SPUNTA_RESTA = 1000;
    /** Dopo la risposta alla domanda dello sblocco, prima che la scheda si chiuda. */
    public static final int SBLOCCO_ATTESA = 300;
    /** Il numero del rapporto che scende: durata totale. */
    public static final int CONTO_MS = 600;
    /** Le righe del rapporto entrano una alla volta, a distanza di… */
    public static final int RIGHE_SFALSAMENTO = 80;

    /** «Rimuovi animazioni» di Android = scala delle durate degli animatori a 0. */
    public static boolean reducedFromScale(float animatorScale) { return animatorScale == 0f; }

    /** La durata da usare: 80 ms con le animazioni ridotte, altrimenti quella del token. */
    public static int dur(boolean reduced, int ms) { return reduced ? RIDOTTO : ms; }

    /** Con le animazioni ridotte niente traslazioni né scale: restano solo dissolvenze. */
    public static boolean allowsTravel(boolean reduced) { return !reduced; }

    /**
     * Il rapporto conta alla rovescia solo se la settimana scorsa è già salvata ({@code previous >= 0})
     * e il numero di adesso è più basso. Se è salito o uguale, c'è già. Senza dato, niente conto.
     */
    public static boolean countDown(int previous, int now) { return previous >= 0 && now >= 0 && now < previous; }

    /** Il numero a un istante del conto: da {@code from} a {@code to}, rallentando (cubica); t in 0..1. */
    public static int countValue(int from, int to, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        float e = 1f - (1f - k) * (1f - k) * (1f - k);
        return Math.round(from + (to - from) * e);
    }

    /** Il ritardo dell'ingresso della riga i del rapporto (0 con le animazioni ridotte). */
    public static int rowDelay(boolean reduced, int i) { return reduced ? 0 : i * RIGHE_SFALSAMENTO; }

    /** L'avanzamento dell'anello della pausa dopo k secondi di seconds (0..1). */
    public static float ring(int secondsPassed, int seconds) {
        if (seconds <= 0) return 1f;
        return Math.max(0f, Math.min(1f, secondsPassed / (float) seconds));
    }

    /** L'«ultimo urgente»: spuntando questo task non resta nessun altro urgente da fare. */
    public static boolean lastUrgent(boolean thisUrgent, int otherUrgentsStillToDo) { return thisUrgent && otherUrgentsStillToDo <= 0; }

    /** Il colore a metà strada tra a e b (canali ARGB), t in 0..1. */
    public static int mix(int a, int b, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int out = 0;
        for (int s = 24; s >= 0; s -= 8) {
            int x = (a >>> s) & 0xFF, y = (b >>> s) & 0xFF;
            out |= (Math.round(x + (y - x) * k) & 0xFF) << s;
        }
        return out;
    }

    // ---------- Evolutiva 016, fase 2 ----------

    /** «Togliere diventa un meno»: la riga si comprime in una linea (BASE), la linea si ritira a sinistra (RAPIDO). */
    public static final int LEAVE_COMPRIME = BASE, LEAVE_RITIRA = RAPIDO;
    public static int leaveTotal(boolean reduced) { return reduced ? RIDOTTO : LEAVE_COMPRIME + LEAVE_RITIRA; }

    /** «Il meno che si disegna»: il meno (LENTO), i tratti trasparenti (RAPIDO), la home (RAPIDO, parte 60 ms prima della fine dei tratti). */
    public static final int MARCA_DISEGNO = LENTO, MARCA_TRATTI = RAPIDO, MARCA_HOME = RAPIDO, MARCA_SOVRAPPOSTA = 60;
    /** Il momento non blocca mai i tocchi oltre questo (ms). */
    public static final int MARCA_MAX_BLOCCO = 700;
    /** Durata totale: disegno + tratti + home, meno la sovrapposizione. Con le animazioni ridotte, due dissolvenze da 80 ms. */
    public static int marcaTotale(boolean reduced) {
        return reduced ? 2 * RIDOTTO : MARCA_DISEGNO + MARCA_TRATTI + MARCA_HOME - MARCA_SOVRAPPOSTA;
    }

    /** Quanto del meno è disegnato a t ms dall'inizio (0..1, lineare: la curva la mette chi disegna). */
    public static float marcaDisegno(long t) { return Math.max(0f, Math.min(1f, t / (float) MARCA_DISEGNO)); }

    /** L'opacità dei tratti trasparenti a t ms (0..1): compaiono dopo il disegno del meno. */
    public static float marcaTratti(long t) { return Math.max(0f, Math.min(1f, (t - MARCA_DISEGNO) / (float) MARCA_TRATTI)); }

    /** L'opacità del logo sopra la home a t ms: 1 finché la home non comincia ad apparire, poi scende a 0. */
    public static float marcaLogo(long t) {
        long from = MARCA_DISEGNO + MARCA_TRATTI - MARCA_SOVRAPPOSTA;
        return 1f - Math.max(0f, Math.min(1f, (t - from) / (float) MARCA_HOME));
    }

    /**
     * Il primo sblocco del mattino: adesso è dopo la sveglia di oggi ({@code wakeToday}) e l'ultima volta che la home
     * è stata vista era prima di quell'ora (la notte, o ieri). Mai prima della sveglia.
     */
    public static boolean morningFirst(long lastSeen, long wakeToday, long now) { return now >= wakeToday && lastSeen < wakeToday; }

    /** Il momento di marca: all'avvio a freddo o al primo sblocco del mattino, una volta sola al giorno. */
    public static boolean brandMoment(boolean coldStart, boolean morningFirst, long today, long lastShownDay) {
        return (coldStart || morningFirst) && today != lastShownDay;
    }

    /** «La sera»: la tavolozza scivola (e la luna sale) solo se la sera comincia mentre la home è a schermo. */
    public static final int SERA_MS = 3000, SERA_LUNA_DP = 6;
    public static boolean seraAnimates(boolean wasEvening, boolean nowEvening, boolean homeVisible, boolean reduced) {
        return !wasEvening && nowEvening && homeVisible && !reduced;
    }

    /**
     * Il rapporto della settimana: i numeri della settimana appena chiusa si tengono per il confronto della prossima.
     * Si salvano solo se c'era una prova già conclusa (rapporto visto); altrimenti -1 = nessun dato.
     */
    public static int prevWeek(boolean hadTrial, boolean reportSeen, int current) {
        return hadTrial && reportSeen && current >= 0 ? current : -1;
    }
}
