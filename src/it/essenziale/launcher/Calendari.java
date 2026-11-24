package it.essenziale.launcher;

/**
 * 0.27 (Evolutiva 024): come si descrive il tipo di un calendario (sotto il nome, nella scelta del calendario e sotto
 * la riga «Calendario» del modulo): solo il tipo, mai l'e-mail dell'account. Logica pura, coperta dal test.
 */
public final class Calendari {
    private Calendari() {}

    public static final String GOOGLE_TYPE = "com.google", LOCAL_TYPE = "LOCAL";

    /**
     * «Google Calendar» per gli account Google, «Sul telefono» per quelli locali, altrimenti il nome dell'app
     * dell'account ({@code appLabel}, se si è trovato), altrimenti niente (stringa vuota).
     */
    public static String typeLabel(String type, String appLabel) {
        if (GOOGLE_TYPE.equals(type)) return "Google Calendar";
        if (LOCAL_TYPE.equals(type)) return "Sul telefono";
        return appLabel == null ? "" : appLabel.trim();
    }

    /** Nome e, se c'è, il tipo su una seconda riga: «Famiglia\nGoogle Calendar». */
    public static String twoLines(String name, String type) {
        return type == null || type.length() == 0 ? name : name + "\n" + type;
    }
}
