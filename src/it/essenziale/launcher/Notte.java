package it.essenziale.launcher;

/**
 * Di notte (0.10), logica pura: quando è notte e quando uno sblocco è «di notte».
 * In automatico la notte comincia con la prima lunga pausa della sera e finisce alla sveglia
 * (o alle 7 se non c'è). Coperta dal test (sezione H).
 */
public final class Notte {
    private Notte() {}

    public static final String AUTO = "auto", FIXED = "fisso", SCHED = "orario";
    public static final int LEAD_MIN = 30;      // 0.14: l'ora di staccare viene 30 minuti prima di andare a dormire
    public static final int MORNING_MIN = 30;   // 0.14: mattino tranquillo, 30 minuti dopo la sveglia

    /** 0.14: l'ora di staccare in minuti dalla mezzanotte: sveglia meno le ore di sonno, meno 30 minuti. */
    public static int offMin(int wakeMin, int sleepMin, int leadMin) {
        return ((wakeMin - sleepMin - leadMin) % 1440 + 1440) % 1440;
    }

    /** L'ora in cui andare a dormire (sveglia meno le ore di sonno). */
    public static int bedMin(int wakeMin, int sleepMin) {
        return offMin(wakeMin, sleepMin, 0);
    }

    /** Dentro la finestra [start, end), anche a cavallo della mezzanotte. */
    /**
     * 0.21: la sveglia del telefono vale come ora del risveglio solo se è un'ora da risveglio (dalle 4 alle 11).
     * Un allarme di giorno (un promemoria alle 11:30, un timer) spostava la «sera» in pieno giorno.
     */
    public static int plausibleWake(int alarmMin) {
        return alarmMin >= 4 * 60 && alarmMin <= 11 * 60 ? alarmMin : -1;
    }

    public static final int DEFAULT_WAKE_MIN = 7 * 60, NOON_MIN = 12 * 60;

    /**
     * 0.25: la notte ha una sola fine. La chiave è la data del mattino in cui finisce: prima di mezzogiorno è oggi,
     * dopo è domani. (Le sveglie plausibili sono tra le 4 e le 11, quindi mezzogiorno separa bene le notti.)
     */
    public static String wakeKey(String today, String tomorrow, int nowMin) {
        return nowMin < NOON_MIN ? today : tomorrow;
    }

    /**
     * 0.25: l'ora del risveglio di questa notte. Finché la sveglia non è passata segue la prossima sveglia del telefono
     * (se la cambi di sera o di notte la notte la segue); dal momento in cui è passata si ferma lì: una sveglia
     * dopo, il «Posticipa» o una seconda sveglia, non allungano più la notte.
     * cachedKey/cachedWake: l'ultimo valore tenuto (cachedWake &lt; 0 = nessuno); alarmMin: la prossima sveglia, -1 se non c'è.
     */
    public static int wakeFor(String cachedKey, int cachedWake, String key, int nowMin, int alarmMin) {
        if (key != null && key.equals(cachedKey) && cachedWake >= 0 && nowMin < NOON_MIN && nowMin >= cachedWake) return cachedWake;
        int a = plausibleWake(alarmMin);
        return a >= 0 ? a : DEFAULT_WAKE_MIN;
    }

    public static boolean inWindow(int nowMin, int startMin, int endMin) {
        if (startMin == endMin) return false;
        if (startMin < endMin) return nowMin >= startMin && nowMin < endMin;
        return nowMin >= startMin || nowMin < endMin;
    }

    /** «22:45» da minuti dalla mezzanotte. */
    public static String hhmm(int min) {
        min = ((min % 1440) + 1440) % 1440;
        int m = min % 60;
        return (min / 60) + ":" + (m < 10 ? "0" : "") + m;
    }
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
        if (SCHED.equals(mode)) return false; // 0.14: con l'ora di staccare decide Night.active, che conosce i minuti
        return nightEnd > now;
    }

    /** Lo sblocco mostra la schermata della notte solo dopo una pausa lunga, non a ogni accensione. */
    public static boolean nightUnlock(boolean night, long gapMin) {
        return night && gapMin >= NIGHT_UNLOCK_MIN;
    }
}
