package it.essenziale.launcher;

/**
 * Di notte (0.10), logica pura: quando è notte e quando uno sblocco è «di notte».
 * In automatico la notte comincia con la prima lunga pausa della sera e finisce alla sveglia
 * (o alle 7 se non c'è). Coperta dal test (sezione H).
 */
public final class Notte {
    private Notte() {}

    public static final String AUTO = "auto", FIXED = "fisso";
    public static final int LONG_PAUSE_MIN = 60;   // pausa che apre la notte, in automatico
    public static final int NIGHT_UNLOCK_MIN = 20; // pausa dopo cui lo sblocco mostra la schermata della notte

    /** Orari fissi: dalle 23 alle 6. */
    public static boolean fixed(int hour) {
        return hour >= 23 || hour < 6;
    }

    /** In automatico: una pausa di almeno un'ora cominciata dalle 21 in poi (o prima delle 4) apre la notte. */
    public static boolean opensNight(int offHour, long gapMin) {
        return gapMin >= LONG_PAUSE_MIN && (offHour >= 21 || offHour < 4);
    }

    /** Fine della notte: la sveglia se arriva entro 14 ore, altrimenti le 7 del mattino dopo. */
    public static long end(long now, long alarmAt, long nextSeven) {
        if (alarmAt > now && alarmAt - now < 14L * 3600000L) return alarmAt;
        return nextSeven;
    }

    /** È notte adesso? */
    public static boolean active(boolean on, String mode, int hour, long nightEnd, long now) {
        if (!on) return false;
        if (FIXED.equals(mode)) return fixed(hour);
        return nightEnd > now;
    }

    /** Lo sblocco mostra la schermata della notte solo dopo una pausa lunga, non a ogni accensione. */
    public static boolean nightUnlock(boolean night, long gapMin) {
        return night && gapMin >= NIGHT_UNLOCK_MIN;
    }
}
