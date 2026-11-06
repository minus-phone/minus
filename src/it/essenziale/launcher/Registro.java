package it.essenziale.launcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registro per la beta (0.15). Regole pure, senza Android, verificate dai test.
 *
 * Una riga per giorno, solo conteggi: «2026-11-06;sbl=34;pausa_no=5». Mai nomi di persone,
 * di app o testi. Il registro vive solo sul telefono; esce soltanto se la persona lo invia.
 */
public final class Registro {
    private Registro() {}

    /** Quanti giorni si tengono: la beta ne dura 28, si lascia margine. */
    public static final int KEEP_DAYS = 60;

    // Le chiavi, nell'ordine delle colonne esportate.
    public static final String HOME = "home", GOALS = "obiettivi", UNLOCKS = "sblocchi", NIGHT_UNLOCKS = "sblocchi_notte",
            PAUSE_OPEN = "pausa_aperte", PAUSE_NOT_NOW = "pausa_non_ora", SCOPE = "scopo_aperte",
            NOTIF_PASS = "notifiche_passate", NOTIF_WAIT = "notifiche_trattenute", ASK_UNLOCK = "domande_sblocco";

    public static final String[] KEYS = {HOME, GOALS, UNLOCKS, NIGHT_UNLOCKS, PAUSE_OPEN, PAUSE_NOT_NOW, SCOPE,
            NOTIF_PASS, NOTIF_WAIT, ASK_UNLOCK};

    /** Come si leggono le colonne, per la schermata e per chi riceve il file. */
    public static final String[] LABELS = {
            "−minus era la home (1 sì, 0 no)",
            "Obiettivi accesi (n notifiche, d doomscrolling, s sonno)",
            "Sblocchi del telefono",
            "Sblocchi tra le 23 e le 6",
            "Pause concluse aprendo l'app",
            "Pause concluse con «Non ora»",
            "Aperture con uno scopo dichiarato",
            "Notifiche passate subito",
            "Notifiche trattenute in Notifiche",
            "Domande «Per cosa l'hai preso?» mostrate"};

    /** Le chiavi che si sommano; le altre (home, obiettivi) sono lo stato dell'ultima volta nel giorno. */
    static boolean counter(String key) { return !HOME.equals(key) && !GOALS.equals(key); }

    static boolean known(String key) {
        for (String k : KEYS) if (k.equals(key)) return true;
        return false;
    }

    /** Il registro come mappa giorno → valori, nell'ordine in cui è scritto (dal più vecchio). */
    static LinkedHashMap<String, LinkedHashMap<String, String>> parse(String log) {
        LinkedHashMap<String, LinkedHashMap<String, String>> out = new LinkedHashMap<String, LinkedHashMap<String, String>>();
        if (log == null) return out;
        for (String line : log.split("\n")) {
            String[] f = line.trim().split(";");
            if (f.length == 0 || !isDay(f[0])) continue;
            LinkedHashMap<String, String> v = out.get(f[0]);
            if (v == null) { v = new LinkedHashMap<String, String>(); out.put(f[0], v); }
            for (int i = 1; i < f.length; i++) {
                int eq = f[i].indexOf('=');
                if (eq <= 0) continue;
                String k = f[i].substring(0, eq);
                if (known(k)) v.put(k, clean(f[i].substring(eq + 1)));
            }
        }
        return out;
    }

    static String write(LinkedHashMap<String, LinkedHashMap<String, String>> days) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, LinkedHashMap<String, String>> d : days.entrySet()) {
            b.append(d.getKey());
            for (String k : KEYS) {
                String v = d.getValue().get(k);
                if (v != null) b.append(';').append(k).append('=').append(v);
            }
            b.append('\n');
        }
        return b.toString();
    }

    /** Aggiunge n a un conteggio del giorno. Chiavi sconosciute o di stato: registro invariato. */
    public static String add(String log, String day, String key, int n) {
        if (!isDay(day) || !known(key) || !counter(key) || n == 0) return log == null ? "" : log;
        LinkedHashMap<String, LinkedHashMap<String, String>> days = parse(log);
        LinkedHashMap<String, String> v = day(days, day);
        int now = 0;
        try { now = Integer.parseInt(v.get(key) == null ? "0" : v.get(key)); } catch (NumberFormatException ignored) {}
        v.put(key, String.valueOf(Math.max(0, now + n)));
        return write(days);
    }

    /** Scrive lo stato del giorno (home, obiettivi): vale l'ultima volta. */
    public static String set(String log, String day, String key, String value) {
        if (!isDay(day) || !known(key) || counter(key)) return log == null ? "" : log;
        LinkedHashMap<String, LinkedHashMap<String, String>> days = parse(log);
        day(days, day).put(key, clean(value));
        return write(days);
    }

    /** Tiene solo gli ultimi KEEP_DAYS giorni rispetto a oggi (le date sono yyyy-mm-dd: l'ordine è alfabetico). */
    public static String prune(String log, String oldestKept) {
        LinkedHashMap<String, LinkedHashMap<String, String>> days = parse(log), out = new LinkedHashMap<String, LinkedHashMap<String, String>>();
        for (Map.Entry<String, LinkedHashMap<String, String>> d : days.entrySet())
            if (d.getKey().compareTo(oldestKept) >= 0) out.put(d.getKey(), d.getValue());
        return write(out);
    }

    public static int days(String log) { return parse(log).size(); }

    /**
     * Il testo che la persona invia: codice partecipante, versione, legenda e un CSV con una riga
     * per giorno. I giorni senza un valore hanno la cella vuota, non zero.
     */
    public static String export(String log, String code, String version) {
        StringBuilder b = new StringBuilder();
        b.append("# −minus · registro per la beta\n");
        b.append("# Partecipante: ").append(clean(code)).append(" · versione ").append(clean(version)).append('\n');
        b.append("# Solo conteggi giornalieri: nessun nome di persone, app o messaggi.\n");
        for (int i = 0; i < KEYS.length; i++) b.append("# ").append(KEYS[i]).append(": ").append(LABELS[i]).append('\n');
        b.append("giorno");
        for (String k : KEYS) b.append(',').append(k);
        b.append('\n');
        for (Map.Entry<String, LinkedHashMap<String, String>> d : parse(log).entrySet()) {
            b.append(d.getKey());
            for (String k : KEYS) {
                String v = d.getValue().get(k);
                b.append(',').append(v == null ? "" : v);
            }
            b.append('\n');
        }
        return b.toString();
    }

    /** Le righe per la schermata, dalla più recente: «6 nov · 34 sblocchi · 5 «Non ora»». */
    public static List<String[]> recent(String log, int max) {
        List<String[]> out = new ArrayList<String[]>();
        List<Map.Entry<String, LinkedHashMap<String, String>>> all =
                new ArrayList<Map.Entry<String, LinkedHashMap<String, String>>>(parse(log).entrySet());
        for (int i = all.size() - 1; i >= 0 && out.size() < max; i--) {
            LinkedHashMap<String, String> v = all.get(i).getValue();
            StringBuilder s = new StringBuilder();
            part(s, v, UNLOCKS, "sblocchi");
            part(s, v, NIGHT_UNLOCKS, "di notte");
            part(s, v, PAUSE_OPEN, "aperte dopo la pausa");
            part(s, v, PAUSE_NOT_NOW, "«Non ora»");
            part(s, v, NOTIF_WAIT, "notifiche trattenute");
            if (s.length() == 0) s.append("Niente da contare");
            out.add(new String[]{label(all.get(i).getKey()), s.toString()});
        }
        return out;
    }

    /** Un codice di 6 caratteri senza lettere che si confondono (0/O, 1/I/L). r: 6 numeri qualsiasi. */
    public static String code(int[] r) {
        String a = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
        StringBuilder b = new StringBuilder("M-");
        for (int i = 0; i < 6; i++) b.append(a.charAt(Math.abs(r[i % r.length] % a.length())));
        return b.toString();
    }

    /** Lettere degli obiettivi accesi, sempre nello stesso ordine: «nds», «s», «» se nessuno. */
    public static String goals(boolean notif, boolean doom, boolean sleep) {
        return (notif ? "n" : "") + (doom ? "d" : "") + (sleep ? "s" : "");
    }

    static String label(String day) {
        String[] m = {"gen", "feb", "mar", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic"};
        try {
            int mm = Integer.parseInt(day.substring(5, 7)), dd = Integer.parseInt(day.substring(8, 10));
            return dd + " " + m[mm - 1];
        } catch (Exception e) { return day; }
    }

    private static void part(StringBuilder s, Map<String, String> v, String k, String what) {
        String n = v.get(k);
        if (n == null || "0".equals(n)) return;
        if (s.length() > 0) s.append(" · ");
        s.append(n).append(' ').append(what);
    }

    private static LinkedHashMap<String, String> day(LinkedHashMap<String, LinkedHashMap<String, String>> days, String day) {
        LinkedHashMap<String, String> v = days.get(day);
        if (v == null) { v = new LinkedHashMap<String, String>(); days.put(day, v); }
        return v;
    }

    static boolean isDay(String s) {
        return s != null && s.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    /** Niente separatori nei valori: il registro resta leggibile riga per riga. */
    private static String clean(String v) {
        return v == null ? "" : v.replace(";", "").replace("=", "").replace(",", "").replace("\n", " ").trim();
    }
}
