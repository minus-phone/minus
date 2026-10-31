package it.essenziale.launcher;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Capisce i comandi detti (o scritti) dalla home. Il riconoscimento vocale è quello di Android:
 * qui arriva solo il testo. Niente rete, niente modelli: regole semplici e prevedibili.
 * Ciò che non è un comando di Essenziale diventa ASK (da girare a Gemini).
 */
public final class VoiceParser {
    private VoiceParser() {}

    public static final String WRITE = "write", CALL = "call", OPEN = "open", NAV = "nav", MAPS = "maps",
            TASK_ADD = "task_add", TASK_DONE = "task_done", EVENT_ADD = "event_add", EVENT_MOVE = "event_move",
            EVENT_DELETE = "event_delete", HELP = "help", ASK = "ask";

    public static class Cmd {
        public String type = ASK, raw = "";
        public String person, app, text, target;
        public boolean personKnown;
        /** giorni da oggi (-1 = non detto) */
        public int day = -1, fromDay = -1;
        /** minuti dalla mezzanotte (-1 = non detto) */
        public int minutes = -1;

        public String toString() {
            return type + "{person=" + person + ", app=" + app + ", text=" + text + ", target=" + target
                    + ", day=" + day + ", fromDay=" + fromDay + ", minutes=" + minutes + "}";
        }
    }

    // ---------------------------------------------------------------------

    public static Cmd parse(String said, List<String> knownNames, Calendar now) {
        Cmd c = new Cmd();
        String orig = norm(said == null ? "" : said);
        c.raw = orig;
        String s = orig.toLowerCase(Locale.ITALY);
        if (s.length() == 0) return c;

        if (s.equals("aiuto") || s.contains("cosa posso dire") || s.contains("quali comandi") || s.equals("comandi")) {
            c.type = HELP; return c;
        }
        String rest;
        if ((rest = after(s, "chiedi a gemini ", "chiedi a gemini: ", "ehi gemini ", "gemini ")) != null) {
            c.type = ASK; c.text = orig.substring(orig.length() - rest.length()).trim(); return c;
        }

        // Messaggi
        String[] writeApp = {"scrivi su ", "manda un messaggio su ", "scrivi un messaggio su "};
        for (String p : writeApp) {
            if (s.startsWith(p)) {
                String r = s.substring(p.length());
                String[] appAndRest = takeApp(r);
                if (appAndRest != null) {
                    String r2 = appAndRest[1].trim();
                    String r3 = after(r2, "a ", "ad ");
                    if (r3 != null) return write(c, orig, r3, appAndRest[0], knownNames);
                }
            }
        }
        if ((rest = after(s, "manda un whatsapp a ", "manda un whatsapp ad ", "scrivi un whatsapp a ", "whatsapp a ")) != null)
            return write(c, orig, rest, "WA", knownNames);
        if ((rest = after(s, "manda un sms a ", "manda un sms ad ", "scrivi un sms a ", "sms a ")) != null)
            return write(c, orig, rest, "SMS", knownNames);
        for (String verb : new String[]{"scrivi un messaggio", "manda un messaggio", "invia un messaggio", "scrivi", "messaggio"}) {
            for (String prep : new String[]{" a ", " ad ", " al ", " alla ", " allo ", " all'"}) {
                if ((rest = after(s, verb + prep)) != null) return write(c, orig, rest, null, knownNames);
            }
        }

        // Chiamate
        if ((rest = after(s, "chiama ", "telefona a ", "telefona ad ", "fai una chiamata a ", "chiamata a ")) != null) {
            c.type = CALL;
            rest = rest.replaceAll(" (su|con) (whatsapp|whats app|telegram)$", "");
            String known = matchName(rest, knownNames);
            c.person = known != null ? known : cap(rest.trim());
            c.personKnown = known != null;
            return c;
        }

        // Mappe
        if ((rest = after(s, "cerca ", "trova ")) != null && rest.matches(".+ (su|sulle|nelle|con) (google )?(maps|mappe|mappa)$")) {
            c.type = MAPS;
            c.target = origTail(orig, rest).replaceAll("(?i) (su|sulle|nelle|con) (google )?(maps|mappe|mappa)$", "").trim();
            return c;
        }
        if ((rest = after(s, "dov'è ", "dove si trova ", "dove è ")) != null) {
            c.type = MAPS; c.target = origTail(orig, rest).trim(); return c;
        }
        if ((rest = after(s, "portami ", "accompagnami ", "naviga ", "navigazione ", "come arrivo ", "come si arriva ",
                "indicazioni stradali ", "indicazioni ", "andiamo ")) != null) {
            String t = stripLead(origTail(orig, rest), new String[]{"fino a ", "fino al ", "fino alla ", "verso ", "per ", "all'", "al ", "alla ", "allo ",
                    "ai ", "alle ", "agli ", "a ", "ad ", "in ", "dal ", "dalla ", "da "});
            if (t.length() > 0) { c.type = NAV; c.target = t; return c; }
        }

        // App
        if ((rest = after(s, "apri ", "avvia ", "lancia ")) != null) {
            c.type = OPEN;
            c.target = stripLead(origTail(orig, rest), new String[]{"l'applicazione ", "l'app ", "applicazione ", "app "});
            return c;
        }

        // Task
        if ((rest = after(s, "segna come fatto ", "segna come fatta ", "segna fatto ", "segna fatta ", "segna come completato ",
                "segna completato ", "ho fatto ", "completa il task ", "completa ", "fatto ")) != null) {
            c.type = TASK_DONE;
            c.target = cleanTitle(origTail(orig, rest), true);
            return c;
        }
        if ((rest = after(s, "aggiungi un task ", "aggiungi task ", "nuovo task ", "crea un task ", "crea task ", "aggiungi alla lista ",
                "aggiungi da fare ", "da fare ", "ricordami di ", "aggiungi un promemoria ", "promemoria ", "task ")) != null) {
            c.type = TASK_ADD;
            fillTitleAndWhen(c, orig, rest, now, false);
            return c;
        }

        // Impegni
        if ((rest = after(s, "sposta ", "rimanda ", "anticipa ", "posticipa ")) != null) {
            c.type = EVENT_MOVE;
            fillTitleAndWhen(c, orig, rest, now, true);
            return c;
        }
        if ((rest = after(s, "cancella ", "elimina ", "annulla ", "togli ")) != null) {
            c.type = EVENT_DELETE;
            fillTitleAndWhen(c, orig, rest, now, false);
            c.fromDay = c.day; c.day = -1;
            return c;
        }
        boolean explicit = false;
        if ((rest = after(s, "nuovo impegno ", "nuovo appuntamento ", "nuovo evento ", "crea un impegno ", "crea un appuntamento ",
                "crea un evento ", "aggiungi un impegno ", "aggiungi impegno ", "aggiungi un appuntamento ", "aggiungi appuntamento ",
                "aggiungi un evento ", "aggiungi evento ", "fissa un appuntamento ", "fissa ")) != null) explicit = true;
        else rest = after(s, "aggiungi ", "metti ", "segna ", "crea ");
        if (rest != null) {
            fillTitleAndWhen(c, orig, rest, now, false);
            c.type = (explicit || c.minutes >= 0) ? EVENT_ADD : TASK_ADD;
            if (c.text == null || c.text.length() == 0) { c.type = ASK; c.text = orig; }
            return c;
        }

        c.type = ASK;
        c.text = orig;
        return c;
    }

    // ---------- Messaggi ----------

    private static Cmd write(Cmd c, String orig, String rest, String app, List<String> names) {
        c.type = WRITE;
        String known = matchName(rest, names);
        String r2;
        if (known != null) {
            c.person = known; c.personKnown = true;
            int at = rest.toLowerCase(Locale.ITALY).indexOf(known.toLowerCase(Locale.ITALY).trim());
            r2 = rest.substring(Math.max(0, at) + known.trim().length());
        } else {
            int cut = rest.length();
            for (String sep : new String[]{" su ", " con ", " via ", " che ", " dicendo", ":", ","}) {
                int i = rest.indexOf(sep);
                if (i > 0 && i < cut) cut = i;
            }
            if (cut == rest.length()) { int sp = rest.indexOf(' '); if (sp > 0) cut = sp; }
            c.person = cap(rest.substring(0, cut).trim());
            r2 = rest.substring(cut);
        }
        String tail = origTail(orig, r2);
        String low = tail.toLowerCase(Locale.ITALY).trim();
        tail = tail.trim();
        low = low.replaceAll("^[,:]\\s*", ""); tail = tail.replaceAll("^[,:]\\s*", "");
        for (String p : new String[]{"su ", "con ", "via ", "tramite "}) {
            if (low.startsWith(p)) {
                String[] a = takeApp(low.substring(p.length()));
                if (a != null) {
                    app = a[0];
                    int consumed = low.length() - a[1].length();
                    tail = tail.substring(consumed);
                    low = a[1];
                }
                break;
            }
        }
        c.app = app;
        boolean changed = true;
        while (changed) {
            changed = false;
            low = low.trim(); tail = tail.trim();
            for (String p : new String[]{":", ",", "che ", "dicendo ", "dicendole ", "dicendogli ", "dicendo che ", "di "}) {
                if (low.startsWith(p)) { low = low.substring(p.length()); tail = tail.substring(p.length()); changed = true; break; }
            }
        }
        c.text = cap(tail.trim());
        return c;
    }

    /** Se la stringa inizia con il nome di un'app di messaggi: {codice, resto}. */
    private static String[] takeApp(String r) {
        String[][] names = {{"whatsapp", "WA"}, {"whats app", "WA"}, {"telegram", "TG"}, {"sms", "SMS"}, {"messaggio", "SMS"}, {"email", "MAIL"}, {"mail", "MAIL"}};
        for (String[] n : names) {
            if (r.startsWith(n[0]) && (r.length() == n[0].length() || !Character.isLetter(r.charAt(n[0].length()))))
                return new String[]{n[1], r.substring(n[0].length())};
        }
        return null;
    }

    /** Il nome conosciuto più lungo con cui inizia il testo (senza distinguere maiuscole). */
    static String matchName(String rest, List<String> names) {
        if (names == null) return null;
        List<String> sorted = new ArrayList<String>(names);
        Collections.sort(sorted, new Comparator<String>() {
            public int compare(String a, String b) { return b.length() - a.length(); }
        });
        String r = rest.toLowerCase(Locale.ITALY);
        for (String n : sorted) {
            String ln = n.toLowerCase(Locale.ITALY).trim();
            if (ln.length() == 0) continue;
            if (r.startsWith(ln) && (r.length() == ln.length() || !Character.isLetterOrDigit(r.charAt(ln.length())))) return n;
        }
        // "mia mamma", "la mamma"
        for (String art : new String[]{"mia ", "mio ", "la ", "il ", "lo "}) {
            if (r.startsWith(art)) {
                String m = matchName(rest.substring(art.length()), names);
                if (m != null) return m;
            }
        }
        return null;
    }

    // ---------- Titolo e quando ----------

    private static void fillTitleAndWhen(Cmd c, String orig, String rest, Calendar now, boolean move) {
        String tail = origTail(orig, rest);
        String[] tok = tail.split(" ");
        String[] low = tail.toLowerCase(Locale.ITALY).split(" ");
        int cut = tok.length;
        When w = null;
        for (int i = 1; i < tok.length; i++) {
            When x = when(low, i, now);
            if (x != null) { cut = i; w = x; break; }
        }
        StringBuilder t = new StringBuilder();
        for (int i = 0; i < cut; i++) { if (i > 0) t.append(' '); t.append(tok[i]); }
        String title = cleanTitle(t.toString(), true);
        c.text = title;
        c.target = title;
        if (w != null) {
            c.minutes = w.minutes;
            if (w.days.size() >= 2 && move) { c.fromDay = w.days.get(0); c.day = w.days.get(w.days.size() - 1); }
            else if (w.days.size() >= 1) c.day = w.days.get(w.days.size() - 1);
        }
    }

    static class When {
        List<Integer> days = new ArrayList<Integer>();
        int minutes = -1;
    }

    static final String[] CONNECTORS = {"a", "al", "alle", "ad", "per", "entro", "di", "del", "della", "il", "lo", "la", "le",
            "ore", "dalle", "verso", "prossimo", "prossima", "questo", "questa", "giorno", "e", "fra", "tra", "in", "ma", "invece"};
    static final String[] WEEKDAYS = {"lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato", "domenica"};
    static final String[] WEEKDAYS_PLAIN = {"lunedi", "martedi", "mercoledi", "giovedi", "venerdi", "sabato", "domenica"};
    static final String[] MONTHS = {"gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto",
            "settembre", "ottobre", "novembre", "dicembre"};
    static final String[] NUM_WORDS = {"zero", "una", "due", "tre", "quattro", "cinque", "sei", "sette", "otto", "nove", "dieci",
            "undici", "dodici"};

    static boolean in(String t, String[] list) { for (String s : list) if (s.equals(t)) return true; return false; }

    static int idx(String t, String[] list) { for (int i = 0; i < list.length; i++) if (list[i].equals(t)) return i; return -1; }

    /** Analizza tok[from..] come espressione di tempo completa; null se resta qualcosa che non lo è. */
    static When when(String[] tok, int from, Calendar now) {
        When w = new When();
        boolean any = false, pm = false, am = false;
        int hour = -1, min = 0;
        String prev = "";
        int i = from;
        while (i < tok.length) {
            String t = tok[i].replaceAll("[,.;!?]+$", "");
            if (t.startsWith("all'")) { t = t.substring(4); prev = "alle"; }
            else if (t.startsWith("l'")) { t = t.substring(2); }
            if (t.length() == 0) { i++; continue; }
            if (t.equals("oggi") || t.equals("stasera") || t.equals("stamattina") || t.equals("stanotte")) {
                w.days.add(0); any = true;
                if (t.equals("stasera")) pm = true;
                if (t.equals("stamattina")) am = true;
                i++; prev = t; continue;
            }
            if (t.equals("domani")) {
                if (i + 1 < tok.length && (tok[i + 1].equals("sera") || tok[i + 1].equals("pomeriggio"))) { pm = true; i++; }
                else if (i + 1 < tok.length && tok[i + 1].equals("mattina")) { am = true; i++; }
                w.days.add(1); any = true; i++; prev = t; continue;
            }
            if (t.equals("dopodomani")) { w.days.add(2); any = true; i++; prev = t; continue; }
            int wd = idx(t, WEEKDAYS);
            if (wd < 0) wd = idx(t, WEEKDAYS_PLAIN);
            if (wd >= 0) {
                int today = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7;
                int off = (wd - today + 7) % 7;
                if (off == 0) off = 7;
                w.days.add(off); any = true; i++; prev = t; continue;
            }
            if (t.equals("mezzogiorno")) { hour = 12; min = 0; any = true; i++; prev = t; continue; }
            if (t.equals("mezzanotte")) { hour = 0; min = 0; any = true; am = true; i++; prev = t; continue; }
            if (t.equals("pomeriggio") || t.equals("sera")) { pm = true; i++; prev = t; continue; }
            if (t.equals("mattina") || t.equals("mattino")) { am = true; i++; prev = t; continue; }
            if (t.matches("\\d{1,2}([:.]\\d{2})?") || in(t, NUM_WORDS) || t.equals("un'") || t.equals("l'una")) {
                // numero: giorno del mese o ora
                int n;
                int mm = 0;
                if (t.matches("\\d{1,2}[:.]\\d{2}")) {
                    String[] p = t.split("[:.]");
                    n = Integer.parseInt(p[0]); mm = Integer.parseInt(p[1]);
                } else if (in(t, NUM_WORDS)) n = idx(t, NUM_WORDS);
                else n = Integer.parseInt(t.replaceAll("\\D", ""));
                String next = i + 1 < tok.length ? tok[i + 1].replaceAll("[,.;!?]+$", "") : "";
                int month = idx(next, MONTHS);
                if (month >= 0 && n >= 1 && n <= 31) {
                    w.days.add(dayOffsetForDate(now, n, month)); any = true; i += 2; prev = "mese"; continue;
                }
                if (prev.equals("il") && !t.contains(":") && !t.contains(".") && n >= 1 && n <= 31 && !in(t, NUM_WORDS)) {
                    w.days.add(dayOffsetForDayOfMonth(now, n)); any = true; i++; prev = "giorno"; continue;
                }
                if (n > 23 || mm > 59) return null;
                hour = n; min = mm; any = true; i++;
                // "15 e 30", "3 e mezza", "5 e un quarto"
                if (i + 1 < tok.length && tok[i].equals("e")) {
                    String m2 = tok[i + 1].replaceAll("[,.;!?]+$", "");
                    if (m2.matches("\\d{1,2}")) { min = Integer.parseInt(m2); i += 2; }
                    else if (m2.equals("mezza") || m2.equals("mezzo")) { min = 30; i += 2; }
                    else if (m2.equals("quarto")) { min = 15; i += 2; }
                    else if (m2.equals("un") && i + 2 < tok.length && tok[i + 2].startsWith("quarto")) { min = 15; i += 3; }
                    else if (m2.equals("tre") && i + 2 < tok.length && tok[i + 2].startsWith("quarti")) { min = 45; i += 3; }
                }
                prev = "ora";
                continue;
            }
            if (in(t, CONNECTORS)) { prev = t; i++; continue; }
            return null;
        }
        if (!any) return null;
        if (hour >= 0) {
            if (pm && hour < 12) hour += 12;
            else if (!am && hour >= 1 && hour <= 7) hour += 12;
            w.minutes = hour * 60 + min;
        }
        return w;
    }

    static int dayOffsetForDayOfMonth(Calendar now, int dom) {
        Calendar k = (Calendar) now.clone();
        zero(k);
        Calendar today = (Calendar) k.clone();
        if (dom < k.get(Calendar.DAY_OF_MONTH)) k.add(Calendar.MONTH, 1);
        k.set(Calendar.DAY_OF_MONTH, Math.min(dom, k.getActualMaximum(Calendar.DAY_OF_MONTH)));
        return daysBetween(today, k);
    }

    static int dayOffsetForDate(Calendar now, int dom, int month) {
        Calendar k = (Calendar) now.clone();
        zero(k);
        Calendar today = (Calendar) k.clone();
        k.set(Calendar.DAY_OF_MONTH, 1);
        k.set(Calendar.MONTH, month);
        k.set(Calendar.DAY_OF_MONTH, Math.min(dom, k.getActualMaximum(Calendar.DAY_OF_MONTH)));
        if (k.before(today)) k.add(Calendar.YEAR, 1);
        return daysBetween(today, k);
    }

    static void zero(Calendar k) {
        k.set(Calendar.HOUR_OF_DAY, 0); k.set(Calendar.MINUTE, 0); k.set(Calendar.SECOND, 0); k.set(Calendar.MILLISECOND, 0);
    }

    static int daysBetween(Calendar a, Calendar b) {
        return (int) Math.round((b.getTimeInMillis() - a.getTimeInMillis()) / 86400000.0);
    }

    // ---------- Utilità ----------

    static String norm(String s) {
        return s.replace('’', '\'').replace('‘', '\'').replace('“', '"').replace('”', '"').replaceAll("\\s+", " ").trim()
                .replaceAll("[.!?]+$", "").trim();
    }

    /** Il prefisso che corrisponde e il resto, oppure null. */
    static String after(String s, String... prefixes) {
        for (String p : prefixes) if (s.startsWith(p) && s.length() > p.length()) return s.substring(p.length());
        return null;
    }

    /** La parte finale dell'originale (con maiuscole) lunga quanto il resto in minuscolo. */
    static String origTail(String orig, String lowRest) {
        int n = lowRest.length();
        if (n > orig.length()) return lowRest;
        return orig.substring(orig.length() - n);
    }

    static String stripLead(String s, String[] leads) {
        String t = s.trim();
        String low = t.toLowerCase(Locale.ITALY);
        for (String l : leads) if (low.startsWith(l)) return t.substring(l.length()).trim();
        return t;
    }

    static final String[] ARTICLES = {"l'impegno ", "l'appuntamento ", "l'evento ", "il task ", "impegno ", "appuntamento ", "evento ",
            "task ", "un impegno ", "un appuntamento ", "un evento ", "il ", "lo ", "la ", "l'", "i ", "gli ", "le ", "un ", "una ", "uno ", "un'"};

    static String cleanTitle(String s, boolean capitalize) {
        String t = s.trim().replaceAll("[,.;:]+$", "");
        boolean again = true;
        while (again) {
            again = false;
            String low = t.toLowerCase(Locale.ITALY);
            for (String a : ARTICLES) {
                if (low.startsWith(a) && t.length() > a.length()) { t = t.substring(a.length()).trim(); again = true; break; }
            }
        }
        return capitalize ? cap(t) : t;
    }

    static String cap(String s) {
        if (s == null || s.length() == 0) return s;
        return s.substring(0, 1).toUpperCase(Locale.ITALY) + s.substring(1);
    }

    /** Somiglianza tra un titolo detto e un titolo del calendario o di un task (0 = nessuna). */
    public static int score(String said, String title) {
        if (said == null || title == null) return 0;
        String a = cleanTitle(said.toLowerCase(Locale.ITALY), false), b = title.toLowerCase(Locale.ITALY).trim();
        if (a.length() == 0) return 0;
        if (a.equals(b)) return 100;
        if (b.contains(a)) return 80;
        if (a.contains(b) && b.length() >= 3) return 70;
        int common = 0, total = 0;
        for (String w : a.split(" ")) {
            if (w.length() < 3) continue;
            total++;
            if (b.contains(w)) common++;
        }
        return total == 0 ? 0 : (common * 60) / total;
    }
}
