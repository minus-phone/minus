package it.essenziale.launcher;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 0.24 (Evolutiva 018): le proposte del selettore. Poche (al massimo {@link #MAX}), già scelte, ognuna con il suo
 * MOTIVO scritto sotto il nome. Logica pura, senza Android: i motivi nascono da dati veri (la stella in rubrica,
 * le notifiche ricevute, la categoria riconosciuta, i gruppi visti scrivere) e una proposta senza motivo non esiste.
 */
final class Proposte {
    private Proposte() {}

    static final int MAX = 4;
    static final long DAY = 24L * 3600 * 1000;

    /** Una candidata: chiave, nome, motivo e punteggio (più alto = prima). */
    static final class Cand {
        final String key, label, reason;
        final int score;
        Cand(String key, String label, String reason, int score) { this.key = key; this.label = label; this.reason = reason; this.score = score; }
    }

    /** Le migliori, al massimo {@code max} (mai oltre {@link #MAX}); senza motivo o senza punteggio non entrano. */
    static List<Cand> top(List<Cand> all, int max) {
        List<Cand> l = new ArrayList<Cand>();
        for (Cand c : all) if (c.reason != null && c.reason.length() > 0 && c.score > 0) l.add(c);
        Collections.sort(l, new Comparator<Cand>() {
            public int compare(Cand a, Cand b) {
                if (a.score != b.score) return b.score - a.score;
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        int m = Math.max(0, Math.min(max, MAX));
        return new ArrayList<Cand>(l.subList(0, Math.min(m, l.size())));
    }

    // ---------- Persone ----------

    static final String R_STAR = "Hai la stella in rubrica", R_OFTEN = "Ti scrive spesso", R_RECENT = "Ti ha scritto di recente";

    /**
     * Il motivo per una persona. La stella vale più di tutto. Dalle notifiche: scrive «spesso» chi ti ha scritto negli ultimi
     * 7 giorni e a cui hai anche risposto (si scrivete avanti e indietro); chi ti ha scritto negli ultimi 14 giorni, «di recente».
     * {@code lastMs} = ultimo messaggio ricevuto, {@code mineMs} = ultimo tuo (0 se mai).
     */
    static String reasonPerson(boolean starred, long lastMs, long mineMs, long nowMs) {
        if (starred) return R_STAR;
        if (lastMs > 0 && nowMs - lastMs <= 7 * DAY && mineMs > 0) return R_OFTEN;
        if (lastMs > 0 && nowMs - lastMs <= 14 * DAY) return R_RECENT;
        return null;
    }

    static int scorePerson(boolean starred, long lastMs, long mineMs, long nowMs) {
        String r = reasonPerson(starred, lastMs, mineMs, nowMs);
        if (r == null) return 0;
        if (R_STAR.equals(r)) return 1000;
        // le più recenti prima; «spesso» davanti a «di recente»
        int base = R_OFTEN.equals(r) ? 500 : 100;
        return base + (int) Math.max(0, 14 - (nowMs - lastMs) / DAY);
    }

    // ---------- Gruppi ----------

    static String reasonGroup(long lastMs, long nowMs) {
        if (lastMs <= 0) return null;
        if (nowMs - lastMs <= 7 * DAY) return "Ti ha scritto nel gruppo questa settimana";
        if (nowMs - lastMs <= 30 * DAY) return "Ti ha scritto nel gruppo di recente";
        return null;
    }

    static int scoreGroup(long lastMs, long nowMs) {
        if (reasonGroup(lastMs, nowMs) == null) return 0;
        return 1 + (int) Math.max(0, 30 - (nowMs - lastMs) / DAY);
    }

    // ---------- Servizi ----------

    static final String[] BANK = {"bnl", "ing", "intesa", "unicredit", "fineco", "revolut", "n26", "trade republic", "scalable", "hype", "bper",
            "banco", "banca", "mediolanum", "widiba", "credem", "illimity", "buddybank", "wise", "sanpaolo", "mps", "mediobanca", "findomestic"};
    static final String[] PAY = {"satispay", "paypal", "telepass", "postepay", "nexi", "bancomat", "google pay", "apple pay"};
    static final String[] SPID = {"spid", "posteid", "poste id", "aruba id", "cie id", "cieid", "namirial", "lepida", "infocert", "tim id", "sielte"};

    private static String norm(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return " " + n.toLowerCase(Locale.ITALY).trim() + " ";
    }

    /** La categoria riconosciuta di un servizio («banca», «pagamenti», «accesso SPID», «servizi pubblici»), o "" se nessuna. */
    static String serviceCategory(String label, String pkg) {
        String l = norm(label), p = pkg == null ? "" : pkg.toLowerCase(Locale.ROOT);
        for (String h : SPID) if (l.contains(h)) return "accesso SPID";
        if (l.equals(" io ") || p.equals("it.ipzs.cieid") || p.equals("it.pagopa.io.app") || l.contains(" app io ")) return "servizi pubblici";
        for (String h : PAY) if (l.contains(h)) return "pagamenti";
        for (String h : BANK) if (l.contains(" " + h) || l.contains(h + " ")) return "banca";
        if (l.contains(" poste")) return "pagamenti";
        return "";
    }

    static String reasonService(String category) {
        return category == null || category.length() == 0 ? "Riconosciuta come servizio" : "Riconosciuta: " + category;
    }

    static int scoreService(String category) {
        if ("banca".equals(category)) return 40;
        if ("accesso SPID".equals(category)) return 30;
        if ("servizi pubblici".equals(category)) return 20;
        if ("pagamenti".equals(category)) return 10;
        return 1;
    }

    // ---------- App che rapiscono, frenate di notte ----------

    static String reasonApp(boolean social, boolean video) {
        if (social) return "App social";
        if (video) return "App video";
        return null;
    }

    static int scoreApp(boolean social, boolean video) {
        return social ? 20 : video ? 10 : 0;
    }

    // ---------- Etichette ----------

    /** «3 scelti», «1 scelto», «Nessuno» (maschile) o «3 scelte», «1 scelta», «Nessuna» (femminile). */
    static String count(int n, boolean female) {
        if (n <= 0) return female ? "Nessuna" : "Nessuno";
        return n + (n == 1 ? (female ? " scelta" : " scelto") : (female ? " scelte" : " scelti"));
    }

    static String added(String what, boolean on) {
        return (on ? "Aggiunta: " : "Tolta: ") + what;
    }
}
