package it.essenziale.launcher;

import java.util.Locale;

/**
 * Regole «quando serve» (0.6), senza Android: cosa mostrare in home e sulla schermata di blocco
 * secondo l'ora. Separate per poterle verificare con i test di non regressione.
 */
public final class Quando {
    private Quando() {}

    public static final int TOMORROW_FROM = 17;   // Domani compare in home dalle 17
    public static final int ALARM_FROM = 19;      // la sveglia di domani (o la sua assenza) dalle 19
    public static final long ALARM_TODAY_BEFORE = 2L * 3600 * 1000; // sveglia di oggi: nelle 2 ore prima

    /** Home: Domani si vede dalle 17, o prima se oggi non resta niente. */
    public static boolean showTomorrow(int hour, boolean todayEmpty) {
        return hour >= TOMORROW_FROM || todayEmpty;
    }

    /** Home, prima riga di Domani: la sveglia se impostata, altrimenti dalle 19 lo dice. */
    public static String homeAlarm(int hour, long now, long alarmAt, long startTomorrow, long startDayAfter) {
        if (alarmAt >= startTomorrow && alarmAt < startDayAfter) return "Sveglia " + hm(alarmAt);
        if (hour >= ALARM_FROM) return "Nessuna sveglia per domani";
        return null;
    }

    /** Blocco: sveglia di oggi nelle 2 ore prima; quella di domani (o la sua assenza) dalle 19. */
    public static String lockAlarm(int hour, long now, long alarmAt, long startTomorrow, long startDayAfter) {
        if (alarmAt >= now && alarmAt < startTomorrow && alarmAt - now <= ALARM_TODAY_BEFORE) return "Sveglia oggi " + hm(alarmAt);
        if (hour >= ALARM_FROM) {
            if (alarmAt >= startTomorrow && alarmAt < startDayAfter) return "Sveglia domani " + hm(alarmAt);
            return "Nessuna sveglia per domani";
        }
        return null;
    }

    /** «+ Aggiungi» dalla home: oggi, dopo le 19 propone domani. */
    public static int addDayOffset(int hour) {
        return hour >= ALARM_FROM ? 1 : 0;
    }

    static String hm(long t) {
        return new java.text.SimpleDateFormat("H:mm", Locale.ITALY).format(new java.util.Date(t));
    }
}
