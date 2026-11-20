package it.essenziale.launcher;

/**
 * Evolutiva 016 · le regole del movimento che non toccano Android (verificate dai test).
 * I valori che si vedono nel codice stanno in Motion, che rimanda qui.
 */
public final class MotionLogic {
    private MotionLogic() {}

    public static final int RAPIDO = 180, BASE = 400, LENTO = 720;
    /** «Rimuovi animazioni»: ogni movimento diventa una dissolvenza così breve (o un cambio diretto). */
    public static final int RIDOTTO = 80;
    /** Quanto resta spuntata una casella prima che la riga si chiuda; la seconda pressione la toglie. */
    public static final int SPUNTA_RESTA = 1000;
    /** Dopo la risposta alla domanda dello sblocco, prima che la scheda si chiuda. */
    public static final int SBLOCCO_ATTESA = 300;
    /** Il numero del rapporto che scende: durata totale. */
    public static final int CONTO_MS = 900;
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

    /** La curva «ease-in-out» di tutto ciò che si disegna a mano (riempimento della casella, conto del rapporto): k in 0..1. */
    public static float io(float k) {
        float x = Math.max(0f, Math.min(1f, k));
        return x < 0.5f ? 2f * x * x : 1f - (float) Math.pow(-2f * x + 2f, 2) / 2f;
    }

    /** Il numero a un istante del conto: da {@code from} a {@code to}, ease-in-out; t in 0..1. */
    public static int countValue(int from, int to, float t) {
        return Math.round(from + (to - from) * io(t));
    }

    /** Il ritardo dell'ingresso della riga i del rapporto (0 con le animazioni ridotte). */
    public static int rowDelay(boolean reduced, int i) { return reduced ? 0 : i * RIGHE_SFALSAMENTO; }

    /** L'avanzamento dell'anello della pausa dopo k secondi di seconds (0..1). */
    public static float ring(int secondsPassed, int seconds) {
        if (seconds <= 0) return 1f;
        return Math.max(0f, Math.min(1f, secondsPassed / (float) seconds));
    }

    /** La festa: nessuna, «Tutto fatto per oggi» (l'ultimo task), «Urgenti fatti» (l'ultimo urgente, altri task restano). */
    public static final int FESTA_NESSUNA = 0, FESTA_TUTTO = 1, FESTA_URGENTI = 2;

    /**
     * Spuntando un task: se non resta nulla da fare è «Tutto fatto per oggi»; se questo era urgente e non resta nessun
     * altro urgente è «Urgenti fatti»; altrimenti niente. (Un task non urgente non fa mai «Urgenti fatti».)
     */
    public static int festa(boolean thisUrgent, int urgentsStillToDo, int othersStillToDo) {
        if (othersStillToDo <= 0) return FESTA_TUTTO;
        return thisUrgent && urgentsStillToDo <= 0 ? FESTA_URGENTI : FESTA_NESSUNA;
    }

    /** Il testo che dice TalkBack (la festa non scrive niente sullo schermo), 700 ms dopo che il segno comincia. */
    public static String festaTesto(int festa) {
        return festa == FESTA_TUTTO ? "Tutto fatto per oggi" : festa == FESTA_URGENTI ? "Urgenti fatti" : null;
    }
    public static final int FESTA_ANNUNCIO_MS = 700, FESTA_TIENI_MS = 900, FESTA_RIDOTTO_MS = 1600;
    /** Il segno entra (LENTO), il check si disegna (LENTO, dopo 0,6 LENTO), resta, esce (LENTO): 2,6 LENTO + 900 ms. Ridotto: 1600 ms di sola dissolvenza. */
    public static int festaTotale(boolean reduced) { return reduced ? FESTA_RIDOTTO_MS : Math.round(LENTO * 2.6f) + FESTA_TIENI_MS; }
    /** Il disegno del check: parte dopo 0,6 LENTO e dura LENTO. */
    public static int festaDisegnoDa() { return Math.round(LENTO * 0.6f); }
    /** Opacità del segno a t ms: sale in LENTO, resta, scende negli ultimi LENTO (ease-in-out in entrambi i versi). Ridotto: 0 → 1 a 20%, 1 fino all'80%, → 0. */
    public static float festaOpacita(long t, boolean reduced) {
        int tot = festaTotale(reduced);
        float x = Math.max(0f, Math.min(1f, t / (float) tot));
        if (reduced) return x < 0.2f ? x / 0.2f : x <= 0.8f ? 1f : (1f - x) / 0.2f;
        if (t < LENTO) return io(t / (float) LENTO);
        if (t > tot - LENTO) return 1f - io((t - (tot - LENTO)) / (float) LENTO);
        return 1f;
    }
    /** La scala del segno a t ms: da 0,94 a 1 nell'entrata, poi 1. Ridotto: sempre 1. */
    public static float festaScala(long t, boolean reduced) {
        if (reduced || t >= LENTO) return 1f;
        return 0.94f + 0.06f * io(Math.max(0, t) / (float) LENTO);
    }
    /** Quanto del check è disegnato a t ms (0..1). Ridotto: già tutto. */
    public static float festaCheck(long t, boolean reduced) {
        if (reduced) return 1f;
        return io((t - festaDisegnoDa()) / (float) LENTO);
    }

    /**
     * Il check che sostituisce la label dei pulsanti a pillola: la label svanisce in RAPIDO, il check si disegna in BASE
     * dopo mezzo RAPIDO; l'azione parte 250 ms dopo la fine del disegno (si fa in tempo a vederlo).
     */
    public static final int TICK_EXTRA_MS = 250;
    public static int tickDisegnoDa() { return RAPIDO / 2; }
    public static int tickAttesa() { return tickDisegnoDa() + BASE + TICK_EXTRA_MS; }
    /** Quanto del check del pulsante è disegnato a t ms (0..1), ease-in-out. Ridotto: già tutto. */
    public static float tickCheck(long t, boolean reduced) {
        if (reduced) return 1f;
        return io((t - tickDisegnoDa()) / (float) BASE);
    }
    /** Il ritardo dell'azione dopo il tocco: con le animazioni ridotte il check è subito lì, ma 80 ms bastano a vederlo. */
    public static int tickAttesa(boolean reduced) { return reduced ? RIDOTTO + TICK_EXTRA_MS : tickAttesa(); }

    // ---------- L'alone esterno alla pressione ----------

    /** Sporge di 8 dp attorno a pillole, riga «Nuovo», barra di ricerca, icone tonde; 6 dp attorno a chip e link di testo. */
    public static final float ALONE_DP = 8f, ALONE_PICCOLO_DP = 6f;
    /** L'alone resta visibile almeno così, anche con un tocco veloce (il dito copre il pulsante). */
    public static final int ALONE_MIN_MS = 240;
    /** Quanto aspettare, dopo che il dito si è alzato, prima di far svanire l'alone (0 se è già stato visto abbastanza). */
    public static long aloneRestante(long pressedForMs) { return Math.max(0L, ALONE_MIN_MS - Math.max(0L, pressedForMs)); }
    /** Alone di 56 dp (più grande del dito) dietro la casella dei task e le icone senza bordo. */
    public static final int ALONE_ICONA_DP = 56;

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

    /** «La sera»: la tavolozza scivola (e la luna sale) solo se la sera comincia mentre la home è a schermo. */
    public static final int SERA_MS = 3000, SERA_LUNA_DP = 6;
    /** La data scorre di quanto occupa la luna (22 dp + 8 dp di spazio) mentre la luna compare. */
    public static final int SERA_DATA_DP = 30, SERA_LUNA_BOX_DP = 22;
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
