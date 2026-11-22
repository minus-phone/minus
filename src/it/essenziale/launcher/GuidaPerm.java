package it.essenziale.launcher;

/**
 * 0.24 (Evolutiva 018): la guida «Android blocca questo permesso» come piccola macchina a stati, senza Android.
 * Si apre, il pulsante porta in Android ({@link #leave}), al ritorno ({@link #resume}) si accorge da sola se il permesso
 * è acceso (via libera, poi si continua) o no («Non è ancora acceso»). «Salta» e «Indietro» la azzerano del tutto.
 */
final class GuidaPerm {
    static final int HIDDEN = 0, STEPS = 1, STILL = 2, OK = 3;
    static final int EV_NONE = 0, EV_OK = 1, EV_STILL = 2;
    static final int STEP_COUNT = 3;
    static final long ADVANCE_MS = 1000;

    int phase = HIDDEN;
    int step = 0;          // 0..2: il passo che tocca adesso
    boolean away = false;  // l'utente è andata in Android e non è ancora tornata

    boolean visible() { return phase != HIDDEN; }

    void open() { phase = STEPS; step = 0; away = false; }

    /** Il pulsante del passo apre Android: si ricorda che si è usciti e il passo successivo è quello che tocca. */
    void leave() {
        away = true;
        if (step < STEP_COUNT - 1) step++;
    }

    /** Il ritorno in −minus (onResume) con il permesso com'è adesso. */
    int resume(boolean granted) {
        if (phase == HIDDEN) return EV_NONE;
        if (granted) {
            if (phase == OK) return EV_NONE;
            phase = OK;
            away = false;
            return EV_OK;
        }
        if (away) { phase = STILL; away = false; return EV_STILL; }
        return EV_NONE;
    }

    /** «Salta» e «Indietro»: tutto azzerato, le istruzioni non restano sotto il passo dopo. */
    void clear() { phase = HIDDEN; step = 0; away = false; }

    /** Passato il secondo del «Fatto, si continua»: la guida si chiude. Vero se si può proseguire. */
    boolean finish() {
        boolean ok = phase == OK;
        clear();
        return ok;
    }

    /** «Manca il passo N» (1-3) dopo un ritorno senza permesso. */
    int missing() { return step + 1; }

    /** Il testo del pulsante principale. */
    String button() {
        if (phase == STILL && step == STEP_COUNT - 1) return "Riprova";
        return step == 0 ? "Prova ad accenderlo" : step == 1 ? "Apri Info app" : "Accendilo";
    }

    // ---------- La catena dei permessi ----------

    static final int NEXT = 0, ASK = 1, GUIDE = 2;

    /**
     * Che cosa fa la catena con un permesso: {@code NEXT} se è dato (o chiesto e rifiutato, senza guida) e si passa al
     * successivo; {@code ASK} se va ancora chiesto; {@code GUIDE} se è stato chiesto, manca, il telefono lo blocca
     * (Android 13+ fuori dal Play Store) e la guida non è stata saltata: si resta qui, NON si passa a Non disturbare.
     */
    static int chain(boolean granted, boolean asked, boolean guideable, boolean skipped) {
        if (granted) return NEXT;
        if (!asked) return ASK;
        return guideable && !skipped ? GUIDE : NEXT;
    }
}
