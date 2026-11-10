package it.essenziale.launcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Regole pure (senza Android): lettura delle liste di configurazione e decisione del livello
 * di una notifica. Separate da FilterService per poterle verificare con i test di non regressione.
 */
public final class Rules {
    private Rules() {}

    public static final int IGNORE = -1, DROP = 0, PASS = 1, WAITING = 2, OTHER = 3;

    // Costanti di android.app.Notification (copiate per restare indipendenti da Android)
    public static final int FLAG_ONGOING_EVENT = 0x2, FLAG_NO_CLEAR = 0x20, FLAG_FOREGROUND_SERVICE = 0x40, FLAG_GROUP_SUMMARY = 0x200;

    static final Pattern URGENT = Pattern.compile(
            "(?i)(codice|code|otp|password monouso|verific|verification|autorizz|authoriz|approv|" +
            "conferma (il |l'|un )?(pagamento|acquisto|accesso|operazione)|3d ?secure|securecode|" +
            "pagamento (riuscito|effettuato|confermato)|sei in arrivo|il tuo autista)");

    /** 0.12.2: un codice di 4–8 cifre (non un orario, un importo, una data o un numero di telefono). */
    static final Pattern CODE = Pattern.compile("(?<![\\d+.,:/€$\\-])\\d{4,8}(?![\\d.,:/%€\\-])");
    static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d ]{8,}\\d");
    /** Parole di accesso e sicurezza: nelle app di servizio fanno passare la notifica. */
    static final Pattern ACCESS = Pattern.compile(
            "(?i)(accesso|accedi|login|\\bpin\\b|password|chiave|token|sicurezza|temporane|scade|identit|\\bspid\\b|bonifico|addebit|accredit|pagamento|transazion)");

    public static boolean looksLikeCode(String s) {
        if (s == null) return false;
        return CODE.matcher(PHONE.matcher(s).replaceAll(" ")).find();
    }

    /** App di servizio (banca, pagamenti, identità digitale, IO): voci «pkg:frammento» o nomi di app. */
    public static boolean isService(String pkg, String label, List<String> services) {
        if (services == null) return false;
        String l = label == null ? "" : label.toLowerCase(Locale.ITALY).trim();
        // 0.13: «!pkg:pacchetto» = tolta a mano da Impostazioni › Notifiche › Servizi
        if (pkg != null) for (String s0 : services) if (s0.trim().equalsIgnoreCase("!pkg:" + pkg)) return false;
        for (String s0 : services) {
            String s = s0.toLowerCase(Locale.ITALY).trim();
            if (s.length() == 0 || s.startsWith("!")) continue;
            if (s.startsWith("pkg:")) { if (pkg != null && pkg.contains(s.substring(4).trim())) return true; }
            else if (s.length() <= 3 ? l.equals(s) : (l.equals(s) || l.contains(s))) return true;
        }
        return false;
    }

    public static final String[] DIALERS = {
            "com.google.android.dialer", "com.android.dialer", "com.android.server.telecom", "com.android.phone",
            "com.samsung.android.dialer", "com.samsung.android.incallui"};

    static final String[] ALWAYS_PASS_PKGS = {
            "com.google.android.apps.walletnfcrel", "com.google.android.apps.maps",
            "com.google.android.deskclock", "com.android.deskclock"};

    /**
     * 0.15: Servizi che passano sempre per intero, non solo codici e accessi: wallet, sveglie e timer,
     * app di autenticazione, calendari. Si tolgono da Impostazioni › Notifiche › Servizi.
     */
    public static final String[] ALWAYS_SERVICES = {
            "walletnfcrel", "com.samsung.android.spay",
            "deskclock", "clockpackage", "alarmclock", "fossify.clock", "simplemobiletools.clock",
            "authenticator", "com.authy", "twofasapp", "aegis", "freeotp", "duosecurity",
            "com.google.android.calendar", "com.android.calendar", "com.samsung.android.calendar",
            "proton.android.calendar", "fossify.calendar", "xsoh.etar", "simplemobiletools.calendar"};

    public static boolean alwaysService(String pkg) {
        if (pkg == null) return false;
        for (String f : ALWAYS_SERVICES) if (pkg.contains(f)) return true;
        return false;
    }

    /** Tolta a mano dai Servizi («!pkg:pacchetto»). */
    static boolean optedOut(List<String> services, String pkg) {
        if (services == null || pkg == null) return false;
        for (String s : services) if (s.trim().equalsIgnoreCase("!pkg:" + pkg)) return true;
        return false;
    }

    /** App di calendario: i loro promemoria passano sempre (0.4). */
    public static final String[] CALENDAR_PKGS = {
            "com.google.android.calendar", "com.android.calendar", "com.samsung.android.calendar",
            "ws.xsoh.etar", "me.proton.android.calendar", "com.simplemobiletools.calendar.pro", "org.fossify.calendar"};

    /** Quanto resta aperta una conversazione dopo che le hai scritto (0.4). */
    public static final long OPEN_MS = 12L * 3600 * 1000;
    /** 0.15: una conversazione solo aperta da una notifica resta nell'elenco della Ricerca 2 ore. */
    public static final long SEEN_MS = 2L * 3600 * 1000;

    static final String[] IGNORED_CATEGORIES = {"call", "alarm", "navigation", "transport", "progress", "service", "sys"};

    // ---------- Liste di configurazione ----------

    /** Righe non vuote e non commentate, divise sul carattere |. */
    public static List<String[]> rows(String raw) {
        List<String[]> out = new ArrayList<String[]>();
        if (raw == null) return out;
        for (String line : raw.split("\n")) {
            String l = line.trim();
            if (l.length() == 0 || l.startsWith("#")) continue;
            String[] parts = l.split("\\|", -1);
            for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
            out.add(parts);
        }
        return out;
    }

    /** Una persona in Conversazioni. apps: codici WA, TG, SMS, TEL, MAIL (anche più d'uno). */
    public static class Person {
        public String section = "", name = "", target = "";
        public List<String> apps = new ArrayList<String>();

        public String line() {
            StringBuilder a = new StringBuilder();
            for (String s : apps) { if (a.length() > 0) a.append(","); a.append(s); }
            return section + " | " + name + " | " + a + " | " + target;
        }
    }

    public static List<Person> people(String raw) {
        List<Person> out = new ArrayList<Person>();
        for (String[] r : rows(raw)) {
            if (r.length < 2) continue;
            Person p = new Person();
            p.section = r[0];
            p.name = r[1];
            String apps = r.length > 2 ? r[2] : "WA";
            for (String a : apps.split(",")) {
                String c = a.trim().toUpperCase(Locale.ITALY);
                if (c.length() > 0 && !p.apps.contains(c)) p.apps.add(c);
            }
            if (p.apps.isEmpty()) p.apps.add("WA");
            p.target = r.length > 3 ? r[3] : "";
            out.add(p);
        }
        return out;
    }

    /**
     * 0.6.1: le righe di esempio della prima versione (Partner, Mamma, Sorella con «+39...», Contatto,
     * Gruppo famiglia, Posta) non sono persone vere: spariscono dalla lista e dal filtro.
     */
    public static String stripSamples(String raw) {
        if (raw == null) return "";
        StringBuilder b = new StringBuilder();
        for (String line : raw.split("\n")) {
            String l = line.trim();
            if (l.length() > 0 && !l.startsWith("#")) {
                String[] p = l.split("\\|", -1);
                for (int i = 0; i < p.length; i++) p[i] = p[i].trim();
                String target = p.length > 3 ? p[3] : "";
                String name = p.length > 1 ? p[1] : "";
                boolean sample = target.equals("+39...") || target.equals("username")
                        || (target.length() == 0 && (l.equals("Gruppi | Gruppo famiglia | WA |") || l.equals("Email | Posta | MAIL |")))
                        || name.endsWith("...");
                if (sample) continue;
            }
            b.append(line).append("\n");
        }
        return b.toString();
    }

    /**
     * 0.6.1: alcune versioni di WhatsApp e Telegram non segnano i gruppi: titolo = gruppo,
     * testo = «Mittente: messaggio». Lo riconosco solo per le app di chat e con un mittente breve.
     */
    /** 0.15: titoli delle notifiche di chiamata nelle app di chat («1 videochiamata persa», «Missed voice call»). */
    public static boolean isCallTitle(String title) {
        if (title == null) return false;
        String t = title.toLowerCase(java.util.Locale.ROOT);
        return t.matches(".*(chiamat[ae] pers[ae]|videochiamat[ae]|chiamata in (arrivo|corso)|chiamata vocale|missed (voice |video )?calls?|incoming (voice |video )?call|ongoing (voice |video )?call).*");
    }

    public static boolean looksLikeGroup(String pkg, String title, String text) {
        String code = chatCode(pkg);
        if (!"WA".equals(code) && !"TG".equals(code) || title == null || text == null) return false;
        if (title.contains(" @ ")) return true;
        int i = text.indexOf(": ");
        if (i <= 0 || i > 30) return false;
        String sender = text.substring(0, i).trim();
        if (sender.length() == 0 || sender.equalsIgnoreCase(groupName(title))) return false;
        if (sender.matches(".*[0-9]{3,}.*") && !sender.startsWith("+")) return false;
        if (text.substring(i + 2).trim().matches("[0-9 -]{4,}")) return false; // «Codice: 1234»
        return !sender.contains("http") && sender.split(" ").length <= 4;
    }

    /** Nome del gruppo dal titolo «Mittente @ Gruppo» (formato vecchio di WhatsApp). */
    public static String groupTitle(String title) {
        if (title == null) return "";
        int i = title.lastIndexOf(" @ ");
        return i > 0 ? title.substring(i + 3).trim() : title;
    }

    public static String peopleText(List<Person> list) {
        StringBuilder b = new StringBuilder("# Sezione | Nome | App (WA, TG, SMS, TEL, MAIL, anche separate da virgola) | Numero o username\n");
        for (Person p : list) b.append(p.line()).append("\n");
        return b.toString();
    }

    public static String appName(String code) {
        if ("WA".equals(code)) return "WhatsApp";
        if ("TG".equals(code)) return "Telegram";
        if ("SMS".equals(code)) return "SMS";
        if ("TEL".equals(code)) return "Chiamata";
        if ("MAIL".equals(code)) return "Email";
        return code;
    }

    /** Codice app di messaggistica dal nome detto o scritto. */
    public static String appCode(String spoken) {
        String s = spoken.toLowerCase(Locale.ITALY).trim();
        if (s.startsWith("whatsapp") || s.startsWith("whats app") || s.equals("wa")) return "WA";
        if (s.startsWith("telegram")) return "TG";
        if (s.startsWith("sms") || s.startsWith("messaggio") || s.startsWith("messaggi")) return "SMS";
        if (s.startsWith("mail") || s.startsWith("email") || s.startsWith("e-mail") || s.startsWith("posta")) return "MAIL";
        return null;
    }

    /** Il pacchetto appartiene a una delle app indicate da quel codice? */
    public static boolean pkgMatches(String code, String pkg) {
        if (pkg == null) return false;
        if ("WA".equals(code)) return pkg.startsWith("com.whatsapp");
        if ("TG".equals(code)) return pkg.startsWith("org.telegram") || pkg.equals("org.thunderdog.challegram");
        if ("SMS".equals(code)) return pkg.equals("com.google.android.apps.messaging") || pkg.equals("com.samsung.android.messaging")
                || pkg.equals("com.android.mms") || pkg.equals("com.android.messaging");
        if ("TEL".equals(code)) { for (String d : DIALERS) if (d.equals(pkg)) return true; return false; }
        if ("MAIL".equals(code)) return pkg.equals("com.google.android.gm");
        return false;
    }

    /** Toglie dal titolo di un gruppo il contatore aggiunto dalle app, es. "Condominio (3 messaggi)". */
    public static String groupName(String title) {
        if (title == null) return "";
        // WhatsApp e Telegram aggiungono a volte segni invisibili di direzione del testo: due nomi
        // uguali a vista risultavano diversi (gruppi doppi in Impostazioni, 0.4.1)
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < title.length(); i++) {
            char ch = title.charAt(i);
            if (Character.getType(ch) == Character.FORMAT || ch == '\uFEFF') continue;
            b.append(Character.isSpaceChar(ch) ? ' ' : ch);
        }
        String s = b.toString().replaceAll("\\s+", " ").trim();
        return s.replaceAll("\\s*\\(\\d+[^)]*\\)\\s*$", "")
                .replaceAll("(?i)\\s*[:·]\\s*\\d+ (nuovi |new )?(messaggi|messages?)\\s*$", "").trim();
    }

    // ---------- Decisione sulla notifica ----------

    public static class Note {
        public String pkg = "", ownPkg = "", category, title = "", text = "", appLabel = "";
        public int flags;
        public boolean group;
    }

    public static class Config {
        public List<Person> people = new ArrayList<Person>();
        public List<String> waitingApps = new ArrayList<String>();
        public String myName = ""; // 0.6: non più usato (niente menzioni)
        /** nome gruppo in minuscolo -> "sempre" | "silenzio" (0.5: "menzioni", "attesa", letti come silenzio) */
        public Map<String, String> groups;
        /** Conversazioni aperte (0.4): chiave convKey(codice app, nome) -> scadenza in ms. */
        public Map<String, Long> open;
        public long now;
        /** 0.6: dove vanno i gruppi in silenzio, per app: codice (WA, TG) -> "notifiche" | "altro" */
        public Map<String, String> groupDest;
        /** 0.12.2: app di servizio (banca, pagamenti, identità digitale) */
        public List<String> services = new ArrayList<String>();
    }

    public static final String DEST_NOTIF = "notifiche", DEST_OTHER = "altro";

    /** 0.6: le regole dei gruppi sono due. Quelle della 0.5 (menzioni, attesa) diventano «In silenzio». */
    public static boolean groupAlways(String rule) {
        return "sempre".equals(rule);
    }

    /** Codice dell'app di messaggistica (WA, TG, SMS) o null. */
    public static String chatCode(String pkg) {
        if (pkgMatches("WA", pkg)) return "WA";
        if (pkgMatches("TG", pkg)) return "TG";
        if (pkgMatches("SMS", pkg)) return "SMS";
        return null;
    }

    public static String convKey(String code, String name) {
        return code + "|" + groupName(name).toLowerCase(Locale.ITALY);
    }

    public static boolean isCalendar(String pkg) {
        for (String p : CALENDAR_PKGS) if (p.equals(pkg)) return true;
        return false;
    }

    /** La conversazione con quel nome su quell'app è aperta adesso? */
    public static boolean isOpen(Config cfg, String pkg, String title) {
        if (cfg.open == null || cfg.open.isEmpty()) return false;
        String code = chatCode(pkg);
        if (code == null) return false;
        Long until = cfg.open.get(convKey(code, title));
        return until != null && until > cfg.now;
    }

    public static int decide(Note n, Config cfg) {
        String pkg = n.pkg;
        if (pkg.equals(n.ownPkg)) return IGNORE;
        if (pkg.equals("android") || pkg.equals("com.android.systemui") || pkg.startsWith("com.android.providers")) return IGNORE;
        if ((n.flags & (FLAG_ONGOING_EVENT | FLAG_FOREGROUND_SERVICE | FLAG_NO_CLEAR)) != 0) return IGNORE;
        if (n.category != null) for (String c : IGNORED_CATEGORIES) if (c.equals(n.category)) return IGNORE;
        boolean out = optedOut(cfg.services, pkg);
        for (String p : ALWAYS_PASS_PKGS) if (p.equals(pkg) && !out) return IGNORE;

        if ((n.flags & FLAG_GROUP_SUMMARY) != 0) return DROP;

        // 0.4: i promemoria del calendario passano sempre
        if (isCalendar(pkg) && !out) return PASS;
        // 0.15: wallet, sveglie, autenticazione e calendari tra i Servizi passano per intero
        if (alwaysService(pkg) && isService(pkg, n.appLabel, cfg.services)) return PASS;

        String title = n.title == null ? "" : n.title;
        String all = (title + " " + (n.text == null ? "" : n.text)).toLowerCase(Locale.ITALY);

        // 1. Ciò che ho provocato io: codici, autorizzazioni, conferme
        if (URGENT.matcher(all).find()) return PASS;

        // 0.12.2: SMS e app di servizio (banca, pagamenti, SPID, IO). Un codice passa subito anche senza
        // parole chiave («482913 è il tuo…»); se Android nasconde il testo (codici, da Android 15) passa lo stesso
        boolean sms = !n.group && pkgMatches("SMS", pkg), service = isService(pkg, n.appLabel, cfg.services);
        if (sms || service) {
            String body = n.text == null ? "" : n.text.trim();
            if (looksLikeCode(all)) return PASS;
            if (body.length() == 0) return PASS;
            if (service && ACCESS.matcher(all).find()) return PASS;
        }

        // 1. Persone del gruppo "Sempre", solo nei messaggi diretti e solo dalle app scelte per loro
        if (!n.group) {
            String t = title.toLowerCase(Locale.ITALY);
            for (Person p : cfg.people) {
                if (!p.section.equalsIgnoreCase("Sempre")) continue;
                String name = p.name.toLowerCase(Locale.ITALY);
                if (name.length() <= 1 || !(t.equals(name) || t.startsWith(name + " ") || t.contains(name))) continue;
                if (fromChosenApp(p, pkg)) return PASS;
            }
        }

        // 0.4: conversazioni aperte, cioè a cui hai scritto nelle ultime 12 ore.
        // 0.6: un gruppo in cui scrivi passa anche se è in silenzio (il silenzio è il default dei gruppi)
        if (isOpen(cfg, pkg, title)) {
            boolean waits = false;
            if (!n.group) {
                String t = groupName(title).toLowerCase(Locale.ITALY);
                for (Person p : cfg.people) if (p.section.equalsIgnoreCase("Attesa") && p.name.toLowerCase(Locale.ITALY).equals(t)) waits = true;
            }
            if (!waits) return PASS;
        }

        // Chiamate perse
        for (String d : DIALERS) if (d.equals(pkg)) return WAITING;
        // 0.12.2: SMS e app di servizio non finiscono mai in Altro
        if (sms || service) return WAITING;

        String label = n.appLabel == null ? "" : n.appLabel.toLowerCase(Locale.ITALY);
        boolean waitingApp = false;
        for (String w0 : cfg.waitingApps) {
            String w = w0.toLowerCase(Locale.ITALY);
            if (w.length() > 0 && (label.equals(w) || label.contains(w))) { waitingApp = true; break; }
        }

        if (n.group) {
            // 0.6: gruppi scelti app per app, senza menzioni: «Passa sempre» o «In silenzio».
            // In silenzio va in Notifiche (contato) o in Altro (non contato), a scelta per ogni app.
            String rule = cfg.groups == null ? null : cfg.groups.get(groupName(title).toLowerCase(Locale.ITALY));
            if ("sempre".equals(rule)) return PASS;
            String code = chatCode(pkg);
            String dest = cfg.groupDest == null || code == null ? null : cfg.groupDest.get(code);
            if (DEST_NOTIF.equals(dest)) return WAITING;
            if (DEST_OTHER.equals(dest)) return OTHER;
            return "attesa".equals(rule) ? WAITING : OTHER; // scelta della 0.5 per quel gruppo, finché non scegli per l'app
        }
        return waitingApp ? WAITING : OTHER;
    }

    /** Senza app indicate (o con un codice sconosciuto) vale come nella v0.2: qualsiasi app. */
    static boolean fromChosenApp(Person p, String pkg) {
        boolean known = false;
        for (String code : p.apps) {
            if (code.equals("WA") || code.equals("TG") || code.equals("SMS") || code.equals("TEL") || code.equals("MAIL")) {
                known = true;
                if (pkgMatches(code, pkg)) return true;
            }
        }
        return !known;
    }

    // ---------- Conversazioni (0.4) ----------

    /** Una conversazione vista nelle notifiche o aperta da Essenziale. */
    public static class Conv {
        public String code = "", name = "";
        public boolean group;
        public long last, until;
        /** 0.15: l'ultima volta che hai scritto tu (risposta vista nella notifica o messaggio a voce); 0 = mai. */
        public long mine;

        public String key() { return convKey(code, name); }
        public boolean isOpen(long now) { return until > now; }
        /** 0.15: aperta e con una tua risposta nelle ultime 12 ore: è una conversazione in corso, non solo una notifica. */
        public boolean replied(long now) { return mine > 0 && mine + OPEN_MS > now && isOpen(now); }
        /**
         * 0.15: nell'elenco della Ricerca: avviata da te o con una tua risposta nelle ultime 12 ore, oppure
         * aperta da una notifica nelle ultime 2 ore. Mai solo perché è arrivato un messaggio.
         */
        public boolean listed(long now) {
            if (replied(now)) return true;
            long opened = until > 0 ? until - OPEN_MS : 0;
            return opened > 0 && now - opened >= 0 && now - opened < SEEN_MS;
        }
        public String line() {
            return code + " | " + name.replace("|", "/") + " | " + (group ? "gruppo" : "persona") + " | " + last + " | " + until + " | " + mine;
        }
    }

    public static List<Conv> convs(String raw) {
        List<Conv> out = new ArrayList<Conv>();
        for (String[] r : rows(raw)) {
            if (r.length < 5 || r[1].length() == 0) continue;
            Conv c = new Conv();
            c.code = r[0]; c.name = r[1]; c.group = "gruppo".equals(r[2]);
            try { c.last = Long.parseLong(r[3]); c.until = Long.parseLong(r[4]); } catch (Exception e) { continue; }
            if (r.length > 5) try { c.mine = Long.parseLong(r[5]); } catch (Exception ignored) {}
            out.add(c);
        }
        return out;
    }

    /** Più recenti prima; si dimenticano dopo 30 giorni senza messaggi, al massimo 100. */
    public static String convsText(List<Conv> list, long now) {
        List<Conv> sorted = new ArrayList<Conv>(list);
        java.util.Collections.sort(sorted, new java.util.Comparator<Conv>() {
            public int compare(Conv a, Conv b) { return Long.compare(Math.max(b.last, b.until - OPEN_MS), Math.max(a.last, a.until - OPEN_MS)); }
        });
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (Conv c : sorted) {
            if (now - Math.max(c.last, c.until - OPEN_MS) > 30L * 24 * 3600 * 1000 || n++ >= 100) continue;
            b.append(c.line()).append("\n");
        }
        return b.toString();
    }

    /** Aggiorna (o crea) la conversazione; open = le hai appena scritto, quindi si apre per 12 ore. */
    public static List<Conv> touch(List<Conv> list, String code, String name, Boolean group, long now, boolean open) {
        String k = convKey(code, name);
        Conv c = null;
        for (Conv x : list) if (x.key().equals(k)) { c = x; break; }
        if (c == null) { c = new Conv(); c.code = code; c.name = groupName(name); list.add(c); }
        if (group != null) c.group = group;
        c.last = Math.max(c.last, now);
        if (open) c.until = Math.max(c.until, now + OPEN_MS);
        return list;
    }

    /** 0.15: hai scritto tu: la conversazione si apre per 12 ore e risulta in corso. */
    public static List<Conv> wrote(List<Conv> list, String code, String name, Boolean group, long time) {
        touch(list, code, name, group, time, true);
        String k = convKey(code, name);
        for (Conv x : list) if (x.key().equals(k)) x.mine = Math.max(x.mine, time);
        return list;
    }

    /** 0.15: ultima interazione con una conversazione: un tuo messaggio, o quando l'hai aperta. */
    public static long touched(Conv c) {
        return Math.max(c.mine, c.until > 0 ? c.until - OPEN_MS : 0);
    }

    public static Map<String, Long> openMap(List<Conv> list, long now) {
        Map<String, Long> m = new java.util.HashMap<String, Long>();
        for (Conv c : list) if (c.isOpen(now)) m.put(c.key(), c.until);
        return m;
    }

    // ---------- Tempo dall'ultimo sblocco (0.5) ----------

    /** Minuti di sessione da mostrare, o -1 se sotto la soglia o senza sessione. */
    public static int sessionMinutes(long start, long now, int thresholdMin) {
        if (start <= 0 || now < start) return -1;
        int m = (int) ((now - start) / 60000L);
        return m >= thresholdMin ? m : -1;
    }

    public static String sessionLabel(int minutes) {
        if (minutes < 0) return null;
        if (minutes < 60) return "Acceso da " + minutes + " min";
        int h = minutes / 60, m = minutes % 60;
        return "Acceso da " + h + " h" + (m > 0 ? " " + m + " min" : "");
    }

    /** Una chiamata in corso o la navigazione: il tempo non viene fatto notare. */
    public static boolean pausesSession(String pkg, String category, int flags) {
        boolean ongoing = (flags & (FLAG_ONGOING_EVENT | FLAG_FOREGROUND_SERVICE)) != 0;
        if ("navigation".equals(category)) return true;
        if ("call".equals(category) && ongoing) return true;
        return ongoing && ("com.google.android.apps.maps".equals(pkg) || "com.waze".equals(pkg));
    }
}
