package it.essenziale.launcher;

/**
 * Evolutiva 002 · Avviso e consiglio della sera, logica pura: il preavviso di 10 minuti, dove arriva
 * l'avviso all'ora di staccare, il consiglio della settimana (26, uno nuovo ogni lunedì).
 * Coperta dal test (sezione E002).
 */
public final class Serale {
    private Serale() {}

    public static final int PREWARN_MIN = 10;
    public static final long SNOOZE_MS = 15L * 60000L;

    /** Dove arriva l'avviso: in home (−minus davanti o allo sblocco), sulla pillola, con una notifica silenziosa. */
    public static final int HOME = 0, PILL = 1, NOTIF = 2;

    public static int where(boolean screenOn, boolean minusFront, boolean braked, boolean canDraw) {
        if (!screenOn || minusFront) return HOME;
        if (braked && canDraw) return PILL;
        return NOTIF;
    }

    /** Nei 10 minuti prima dell'ora di staccare. */
    public static boolean prewarn(int nowMin, int offMin) {
        return Notte.inWindow(nowMin, ((offMin - PREWARN_MIN) % 1440 + 1440) % 1440, offMin);
    }

    /** Minuti che mancano all'ora di staccare (1..10 nel preavviso). */
    public static int minutesTo(int nowMin, int offMin) {
        return ((offMin - nowMin) % 1440 + 1440) % 1440;
    }

    public static String prewarnText(int min) {
        return "Tra " + (min <= 1 ? "un minuto" : min + " minuti") + " −minus passa alla sera";
    }

    /** Numero della settimana, da lunedì: giorni dal 5 gennaio 1970 (un lunedì) diviso 7. */
    public static long week(long localDayNumber) {
        long d = localDayNumber - 4;
        return d >= 0 ? d / 7 : -((-d + 6) / 7);
    }

    public static int tipIndex(long week, long startWeek) {
        return (int) (((week - startWeek) % TIPS.length + TIPS.length) % TIPS.length);
    }

    /** Fonti nel piano (He 2020, Kroese 2014, Rod 2018, Hughes e Burke 2018, linee guida di igiene del sonno). */
    public static final String[] TIPS = {
            "Stanotte metti il telefono a caricare fuori dalla camera.",
            "Usa una sveglia vera: così il telefono può dormire in un'altra stanza.",
            "Abbassa le luci di casa adesso.",
            "Apri la finestra cinque minuti: la camera fresca aiuta.",
            "Se il telefono resta in camera, lascialo a faccia in giù e lontano dal letto.",
            "Domani sveglia alla stessa ora di oggi, anche se è sabato.",
            "Il letto è per dormire: il lavoro e le serie restano sul divano.",
            "Se non prendi sonno, alzati e fai qualcosa di tranquillo con la luce bassa, poi torna a letto.",
            "Domattina, appena sveglia, prendi qualche minuto di luce del giorno.",
            "Scrivi i task di domani: −minus li tiene per te, la tua testa può lasciarli.",
            "Prepara i vestiti e la borsa per domani.",
            "C'è un pensiero che gira? Scrivilo e lascialo sul foglio.",
            "Leggi qualche pagina di un libro di carta.",
            "Una doccia calda adesso: dopo, il corpo si raffredda e il sonno arriva più facilmente.",
            "Respira lento, con l'espirazione più lunga dell'inspirazione, per qualche minuto.",
            "Allunga collo e spalle per due minuti.",
            "Musica tranquilla o un podcast, a schermo spento.",
            "Domani niente caffè dopo pranzo.",
            "Stasera, se puoi, niente alcol prima di dormire.",
            "Domani muoviti un po': anche una camminata conta.",
            "Cena leggera e non troppo tardi.",
            "Fare tardi la sera spesso vuol dire voler tempo per sé: domani prova a trovarne un po' di giorno.",
            "Decidi adesso: «Quando −minus diventa grigio, vado a lavarmi i denti».",
            "Pensa a com'è domattina se vai a letto adesso, e a cosa ti trattiene. Poi scegli.",
            "Lega il dormire agli stessi gesti ogni sera: luce spenta, telefono fuori, libro.",
            "Ripensa alla settimana: qual è stata la sera migliore, e cosa l'ha resa così?"
    };
}
