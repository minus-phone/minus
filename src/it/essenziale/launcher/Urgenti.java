package it.essenziale.launcher;

/**
 * Evolutiva 001 · Task urgenti, logica pura: in che gruppo sta un task, che etichetta ha sopra il titolo,
 * il «!» scritto all'inizio. Ordine in home: urgenti di oggi, oggi, urgenti della settimana, scaduti,
 * senza scadenza. Coperta dal test (sezione E001).
 */
public final class Urgenti {
    private Urgenti() {}

    public static final int URG_TODAY = 0, TODAY = 1, URG_WEEK = 2, OVERDUE = 3, UNDATED = 4, LATER = 5;
    public static final long DAY = 24L * 3600 * 1000;
    /** Oltre questo numero di urgenze nello stesso giorno compare la riga «scegline una». */
    public static final int TOO_MANY = 3;

    /** today: inizio di oggi. «Della settimana» vuol dire nei prossimi 7 giorni. */
    public static int group(long due, boolean urgent, long today) {
        if (due == 0) return UNDATED;
        if (due < today) return OVERDUE;
        if (due < today + DAY) return urgent ? URG_TODAY : TODAY;
        if (urgent && due < today + 7 * DAY) return URG_WEEK;
        return LATER;
    }

    /** I task che stanno sopra la linea corta: quelli di oggi. */
    public static boolean isToday(int g) { return g == URG_TODAY || g == TODAY; }

    /** In home: tutto tranne i task futuri non urgenti (stanno in Settimana). */
    public static boolean inHome(int g) { return g != LATER; }

    /** L'etichetta sopra il titolo; null per i task di oggi (niente «oggi»). day: «gio». */
    public static String label(int g, String day) {
        switch (g) {
            case URG_WEEK: return "entro " + day;
            case OVERDUE: return "Scaduto";
            case UNDATED: return "senza scadenza";
            default: return null;
        }
    }

    /** Urgente vuol dire con una scadenza: senza data diventa oggi. */
    public static long dueWhenUrgent(long due, long today) { return due == 0 ? today : due; }

    /** «! pagare la mensa» → «pagare la mensa»; null se il titolo non comincia con «!». */
    public static String bang(String title) {
        if (title == null) return null;
        String t = title.trim();
        if (!t.startsWith("!")) return null;
        t = t.replaceFirst("^!+\\s*", "").trim();
        return t.length() == 0 ? null : t;
    }

    /** Più di 3 urgenze oggi: una riga grigia sotto la lista. Nessun avviso, nessun blocco. */
    public static String tooMany(int urgentToday) {
        return urgentToday > TOO_MANY ? urgentToday + " urgenze oggi: scegline una per iniziare, le altre aspettano." : null;
    }
}
