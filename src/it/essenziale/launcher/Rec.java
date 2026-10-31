package it.essenziale.launcher;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Ricorrenza di un impegno, come nel calendario di Windows: frequenza, "ogni n", giorni della
 * settimana, mese per giorno o per posizione, fine (mai, data, dopo n volte).
 * Si traduce in una RRULE standard (RFC 5545), che Google Calendar capisce.
 */
public class Rec {
    public static final int NONE = 0, DAILY = 1, WEEKLY = 2, MONTHLY = 3, YEARLY = 4;
    public static final int END_NEVER = 0, END_UNTIL = 1, END_COUNT = 2;

    static final String[] CODES = {"MO", "TU", "WE", "TH", "FR", "SA", "SU"};
    static final String[] DAY_NAMES = {"lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato", "domenica"};
    static final String[] SHORT = {"L", "M", "M", "G", "V", "S", "D"};
    static final String[] ORD = {"", "primo", "secondo", "terzo", "quarto", "quinto"};

    public int freq = NONE;
    public int interval = 1;
    /** bit 0 = lunedì … bit 6 = domenica */
    public int days = 0;
    /** Mese: true = "il terzo lunedì", false = "il giorno 19" */
    public boolean byPos = false;
    public int end = END_NEVER;
    public long until = 0;    // inizio del giorno di fine (locale)
    public int count = 10;
    /** Regola letta dal calendario che non so rappresentare: la conservo così com'è. */
    public String custom = null;

    public Rec copy() {
        Rec r = new Rec();
        r.freq = freq; r.interval = interval; r.days = days; r.byPos = byPos;
        r.end = end; r.until = until; r.count = count; r.custom = custom;
        return r;
    }

    /** Indice 0..6 (lunedì..domenica) del giorno di start. */
    public static int dow(long start) {
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(start);
        return (k.get(Calendar.DAY_OF_WEEK) + 5) % 7;
    }

    /** Settimana del mese (1..4) o -1 se è l'ultima di quel giorno nel mese. */
    public static int weekPos(long start) {
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(start);
        int dom = k.get(Calendar.DAY_OF_MONTH);
        int pos = (dom - 1) / 7 + 1;
        if (dom + 7 > k.getActualMaximum(Calendar.DAY_OF_MONTH)) return pos == 5 ? -1 : pos;
        return pos;
    }

    public boolean repeats() { return custom != null || freq != NONE; }

    /** Regola RRULE senza prefisso "RRULE:", oppure null se non si ripete. */
    public String toRule(long start) {
        if (custom != null) return custom;
        if (freq == NONE) return null;
        StringBuilder b = new StringBuilder("FREQ=");
        b.append(freq == DAILY ? "DAILY" : freq == WEEKLY ? "WEEKLY" : freq == MONTHLY ? "MONTHLY" : "YEARLY");
        if (interval > 1) b.append(";INTERVAL=").append(interval);
        if (freq == WEEKLY) {
            int d = days == 0 ? (1 << dow(start)) : days;
            b.append(";BYDAY=");
            boolean first = true;
            for (int i = 0; i < 7; i++) if ((d & (1 << i)) != 0) { if (!first) b.append(","); b.append(CODES[i]); first = false; }
            b.append(";WKST=MO");
        } else if (freq == MONTHLY) {
            Calendar k = Calendar.getInstance();
            k.setTimeInMillis(start);
            if (byPos) b.append(";BYDAY=").append(weekPos(start)).append(CODES[dow(start)]);
            else b.append(";BYMONTHDAY=").append(k.get(Calendar.DAY_OF_MONTH));
        }
        if (end == END_COUNT) b.append(";COUNT=").append(Math.max(1, count));
        else if (end == END_UNTIL && until > 0) b.append(";UNTIL=").append(utc(endOfDay(until)));
        return b.toString();
    }

    static long endOfDay(long dayStart) {
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(dayStart);
        k.set(Calendar.HOUR_OF_DAY, 23); k.set(Calendar.MINUTE, 59); k.set(Calendar.SECOND, 59); k.set(Calendar.MILLISECOND, 0);
        return k.getTimeInMillis();
    }

    public static String utc(long t) {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(t));
    }

    static long parseUntil(String v) {
        try {
            if (v.length() == 8) {
                SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd", Locale.US);
                return f.parse(v).getTime();
            }
            SimpleDateFormat f = new SimpleDateFormat(v.endsWith("Z") ? "yyyyMMdd'T'HHmmss'Z'" : "yyyyMMdd'T'HHmmss", Locale.US);
            if (v.endsWith("Z")) f.setTimeZone(TimeZone.getTimeZone("UTC"));
            long t = f.parse(v).getTime();
            Calendar k = Calendar.getInstance();
            k.setTimeInMillis(t);
            k.set(Calendar.HOUR_OF_DAY, 0); k.set(Calendar.MINUTE, 0); k.set(Calendar.SECOND, 0); k.set(Calendar.MILLISECOND, 0);
            return k.getTimeInMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Legge una RRULE. Se contiene parti che non gestisco, la tiene intatta in custom. */
    public static Rec parse(String rule, long start) {
        Rec r = new Rec();
        if (rule == null || rule.trim().length() == 0) return r;
        String s = rule.trim();
        if (s.toUpperCase(Locale.US).startsWith("RRULE:")) s = s.substring(6);
        boolean ok = true;
        for (String part : s.split(";")) {
            int eq = part.indexOf('=');
            if (eq < 0) continue;
            String k = part.substring(0, eq).trim().toUpperCase(Locale.US), v = part.substring(eq + 1).trim().toUpperCase(Locale.US);
            if (k.equals("FREQ")) {
                if (v.equals("DAILY")) r.freq = DAILY;
                else if (v.equals("WEEKLY")) r.freq = WEEKLY;
                else if (v.equals("MONTHLY")) r.freq = MONTHLY;
                else if (v.equals("YEARLY")) r.freq = YEARLY;
                else ok = false;
            } else if (k.equals("INTERVAL")) {
                try { r.interval = Math.max(1, Integer.parseInt(v)); } catch (Exception e) { ok = false; }
            } else if (k.equals("COUNT")) {
                try { r.count = Integer.parseInt(v); r.end = END_COUNT; } catch (Exception e) { ok = false; }
            } else if (k.equals("UNTIL")) {
                r.until = parseUntil(v); r.end = END_UNTIL;
                if (r.until == 0) ok = false;
            } else if (k.equals("BYDAY")) {
                for (String d : v.split(",")) {
                    String code = d.replaceAll("[^A-Z]", "");
                    String num = d.replaceAll("[A-Z]", "");
                    int idx = -1;
                    for (int i = 0; i < 7; i++) if (CODES[i].equals(code)) idx = i;
                    if (idx < 0) { ok = false; continue; }
                    if (num.length() > 0) r.byPos = true;
                    r.days |= 1 << idx;
                }
            } else if (k.equals("BYMONTHDAY")) {
                r.byPos = false;
            } else if (k.equals("WKST")) {
                // indifferente
            } else ok = false;
        }
        if (r.freq == NONE) ok = false;
        if (r.freq == MONTHLY && r.byPos && Integer.bitCount(r.days) != 1) ok = false;
        if (r.freq == DAILY && r.days != 0) ok = false;
        if ((r.freq == WEEKLY || r.freq == YEARLY) && r.byPos) ok = false;
        if (r.freq == YEARLY && r.days != 0) ok = false;
        if (!ok) { Rec c = new Rec(); c.custom = s; return c; }
        if (r.freq == MONTHLY && !r.byPos) r.days = 0;
        return r;
    }

    // ---------- Testi ----------

    static String unit(int freq, int n) {
        boolean one = n == 1;
        switch (freq) {
            case DAILY: return one ? "giorno" : "giorni";
            case WEEKLY: return one ? "settimana" : "settimane";
            case MONTHLY: return one ? "mese" : "mesi";
            case YEARLY: return one ? "anno" : "anni";
        }
        return "";
    }

    public static String unitLabel(int freq, int n) { return unit(freq, n); }

    /** Es. "Ogni 2 settimane il lunedì e il mercoledì, per 10 volte". */
    public String summary(long start) {
        if (custom != null) return "Ripetizione personalizzata";
        if (freq == NONE) return "Mai";
        StringBuilder b = new StringBuilder("Ogni ");
        if (interval > 1) b.append(interval).append(" ");
        b.append(unit(freq, interval));
        if (freq == WEEKLY) {
            int d = days == 0 ? (1 << dow(start)) : days;
            if (d == 0x1F && interval == 1) { b.setLength(0); b.append("Dal lunedì al venerdì"); }
            else {
                java.util.List<String> names = new java.util.ArrayList<String>();
                for (int i = 0; i < 7; i++) if ((d & (1 << i)) != 0) names.add("il " + DAY_NAMES[i]);
                if (names.size() > 0) {
                    b.append(" ");
                    for (int i = 0; i < names.size(); i++) {
                        if (i > 0) b.append(i == names.size() - 1 ? " e " : ", ");
                        b.append(names.get(i));
                    }
                }
            }
        } else if (freq == MONTHLY) {
            Calendar k = Calendar.getInstance();
            k.setTimeInMillis(start);
            if (byPos) {
                int p = weekPos(start);
                b.append(" il ").append(p == -1 ? "ultimo" : ORD[p]).append(" ").append(DAY_NAMES[dow(start)]);
            } else b.append(" il giorno ").append(k.get(Calendar.DAY_OF_MONTH));
        } else if (freq == YEARLY) {
            b.append(" il ").append(new SimpleDateFormat("d MMMM", Locale.ITALY).format(new Date(start)));
        }
        if (end == END_COUNT) b.append(", per ").append(count).append(count == 1 ? " volta" : " volte");
        else if (end == END_UNTIL && until > 0)
            b.append(", fino al ").append(new SimpleDateFormat("d MMM yyyy", Locale.ITALY).format(new Date(until)));
        return b.toString();
    }

    /** Versione breve per la riga "Ripeti" (es. "Ogni lunedì"). */
    public String shortLabel(long start) {
        if (custom != null) return "Personalizzata";
        if (freq == NONE) return "Mai";
        if (freq == WEEKLY && interval == 1 && end == END_NEVER) {
            int d = days == 0 ? (1 << dow(start)) : days;
            if (Integer.bitCount(d) == 1) for (int i = 0; i < 7; i++) if (d == (1 << i)) return "Ogni " + DAY_NAMES[i];
        }
        if (interval == 1 && end == END_NEVER && freq != WEEKLY) {
            return freq == DAILY ? "Ogni giorno" : freq == MONTHLY ? "Ogni mese" : "Ogni anno";
        }
        return summary(start);
    }

    /** Sostituisce la fine di una regola esistente con UNTIL (per "questo e i successivi"). */
    public static String withUntil(String rule, long untilMillis) {
        StringBuilder b = new StringBuilder();
        for (String part : rule.split(";")) {
            String k = part.contains("=") ? part.substring(0, part.indexOf('=')).trim().toUpperCase(Locale.US) : part;
            if (k.equals("COUNT") || k.equals("UNTIL") || part.trim().length() == 0) continue;
            if (b.length() > 0) b.append(";");
            b.append(part.trim());
        }
        b.append(";UNTIL=").append(utc(untilMillis));
        return b.toString();
    }
}
