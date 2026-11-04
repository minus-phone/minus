package it.essenziale.launcher;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Test di non regressione (NRT) di Essenziale 0.3, eseguibile su qualsiasi JVM.
 *  A. Filtro notifiche: confronto differenziale con la logica v0.2 su ~100.000 combinazioni.
 *  B. Comportamenti nuovi del filtro (persone per app, regole dei gruppi), dichiarati come cambi voluti.
 *  C. Ricorrenze (RRULE) stile calendario di Windows.
 *  D. Comandi vocali dagli esempi dei wireframe.
 */
public class NrtTest {

    static int pass = 0, fail = 0;
    static final List<String> report = new ArrayList<String>();
    static String section = "";

    static void sec(String s) { section = s; report.add("\n## " + s); System.out.println("\n== " + s); }

    static void check(String name, boolean ok, String detail) {
        if (ok) pass++; else fail++;
        String line = (ok ? "PASS " : "FAIL ") + name + (ok || detail == null ? "" : "  →  " + detail);
        System.out.println(line);
        report.add("- " + (ok ? "✅ " : "❌ ") + name + (ok || detail == null ? "" : " — " + detail));
    }

    static void eq(String name, Object got, Object exp) {
        boolean ok = got == null ? exp == null : got.equals(exp);
        check(name, ok, "atteso «" + exp + "», ottenuto «" + got + "»");
    }

    // Default della v0.2 (identici a U.DEF_*)
    static final String DEF_CONTACTS =
            "# Sezione | Nome | App (WA, TG, SMS, TEL, MAIL) | Numero con prefisso o username Telegram\n" +
            "Sempre | Partner | WA | +39...\n" +
            "Sempre | Mamma | WA | +39...\n" +
            "Sempre | Sorella | WA | +39...\n" +
            "Gruppi | Gruppo famiglia | WA |\n" +
            "Frequenti | Contatto | TG | username\n" +
            "Email | Posta | MAIL |\n";
    static final String DEF_WAITING_APPS =
            "# App le cui notifiche vanno In attesa (il resto va in Altro)\n" +
            "WhatsApp\nTelegram\nMessaggi\nGmail\nTelefono\nCalendar\nCalendario\nToo Good To Go\nVinted\nMioPediatra\nPagineMediche\n";

    public static void main(String[] a) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome"));
        notificationsDifferential();
        notificationsNew();
        notificationsV04();
        groupsV06();
        quando();
        cerca();
        tempo();
        scopoNotte();
        rowsCompat();
        recurrence();
        voice();
        cosa();
        servizi();
        stessaPersona();
        System.out.println("\nTotale: " + pass + " superati, " + fail + " falliti");
        FileWriter w = new FileWriter(a.length > 0 ? a[0] : "nrt-report.md");
        w.write("# Essenziale 0.6 · NRT automatico\n\nSuperati: " + pass + " · Falliti: " + fail + "\n");
        for (String s : report) w.write(s + "\n");
        w.close();
        if (fail > 0) System.exit(1);
    }

    // ---------------------------------------------------------------------

    static final String OWN = "it.essenziale.launcher";
    static final Map<String, String> LABELS = new HashMap<String, String>();
    static {
        LABELS.put(OWN, "Essenziale"); LABELS.put("android", "Sistema Android"); LABELS.put("com.android.systemui", "Interfaccia di sistema");
        LABELS.put("com.android.providers.downloads", "Download"); LABELS.put("com.whatsapp", "WhatsApp");
        LABELS.put("org.telegram.messenger", "Telegram"); LABELS.put("com.google.android.apps.messaging", "Messaggi");
        LABELS.put("com.google.android.dialer", "Telefono"); LABELS.put("com.google.android.apps.maps", "Maps");
        LABELS.put("com.instagram.android", "Instagram"); LABELS.put("fr.vinted", "Vinted"); LABELS.put("com.google.android.gm", "Gmail");
        LABELS.put("it.bnl.apps.banking", "BNL"); LABELS.put("com.google.android.calendar", "Calendar");
    }

    static void notificationsDifferential() {
        sec("A. Filtro notifiche: stesso esito della v0.2");
        String[] cats = {null, "msg", "call", "alarm", "email", "promo", "navigation", "transport", "sys", "social", "event", "progress", "service"};
        int[] flags = {0, 0x2, 0x20, 0x40, 0x200, 0x10, 0x8};
        String[] titles = {"Mamma", "mamma", "Partner", "Marco Rossi", "Gruppo famiglia", "Condominio", "", "Mamma (2 messaggi)", "Ciao Sorella", "BNL"};
        String[] texts = {"Ciao", "Il tuo codice è 123456", "@cristina guarda qui", "Mario ti ha menzionato", "Conferma il pagamento",
                "Offerta speciale -50%", "", "Il tuo autista è arrivato", "Promemoria: Pediatra alle 16:30"};
        String[] myNames = {"Cristina", ""};
        // Config v0.3 equivalente: le persone senza app riconosciute valgono per qualsiasi app, come nella v0.2
        Rules.Config cfg = new Rules.Config();
        cfg.people = Rules.people(DEF_CONTACTS.replace("| WA |", "| * |").replace("| TG |", "| * |").replace("| MAIL |", "| * |"));
        for (String[] r : Rules.rows(DEF_WAITING_APPS)) cfg.waitingApps.add(r[0]);
        int n = 0, diff = 0;
        String firstDiff = null;
        for (String me : myNames) {
            cfg.myName = me;
            for (String pkg : LABELS.keySet())
                if (!Rules.isCalendar(pkg) && !Rules.pkgMatches("SMS", pkg) && !pkg.equals("it.bnl.apps.banking")) // 0.4: calendario (B2); 0.12.2: SMS e app di servizio (sezione 0.12.2)
                for (String c : cats) for (int f : flags) for (String t : titles) for (String x : texts) for (int g = 0; g < 1; g++) { // 0.6: i gruppi cambiano di proposito (sezione B3)
                    boolean group = g == 1;
                    int old = LegacyV02.classify(OWN, pkg, c, f, t, x, group, LABELS.get(pkg), DEF_CONTACTS, DEF_WAITING_APPS, me);
                    Rules.Note note = new Rules.Note();
                    note.pkg = pkg; note.ownPkg = OWN; note.category = c; note.flags = f; note.title = t; note.text = x;
                    note.group = group; note.appLabel = LABELS.get(pkg);
                    int now = Rules.decide(note, cfg);
                    n++;
                    if (old != now) { diff++; if (firstDiff == null) firstDiff = pkg + "/" + c + "/" + f + "/" + t + "/" + x + "/g=" + group + " v0.2=" + old + " v0.3=" + now; }
                }
        }
        check(n + " combinazioni di notifica: livello identico alla v0.2", diff == 0, diff + " differenze, prima: " + firstDiff);

        // Casi chiave della v0.2, scritti per esteso
        Rules.Config def = v03Default("Cristina");
        eq("Codice OTP da app qualsiasi passa subito", lvl(def, "it.bnl.apps.banking", "Il tuo codice è 123456", "BNL", false), Rules.PASS);
        eq("Mamma su WhatsApp passa subito", lvl(def, "com.whatsapp", "Ciao", "Mamma", false), Rules.PASS);
        eq("Chiamata persa: In attesa", lvl(def, "com.google.android.dialer", "Chiamata persa", "Marco Rossi", false), Rules.WAITING);
        eq("Messaggio WhatsApp da altri: In attesa", lvl(def, "com.whatsapp", "Ciao", "Marco Rossi", false), Rules.WAITING);
        eq("Gruppo senza menzione: Altro", lvl(def, "com.whatsapp", "Chi porta la torta?", "Condominio", true), Rules.OTHER);
        eq("Instagram: Altro", lvl(def, "com.instagram.android", "Nuovo follower", "Instagram", false), Rules.OTHER);
        eq("Notifica riassuntiva di gruppo: eliminata", lvlF(def, "com.whatsapp", 0x200), Rules.DROP);
        eq("Notifica fissa (musica, navigazione): ignorata", lvlF(def, "com.spotify.music", 0x2), Rules.IGNORE);
        eq("Maps e sveglia: lasciate stare", lvl(def, "com.google.android.apps.maps", "Gira a destra", "Maps", false), Rules.IGNORE);
    }

    static Rules.Config v03Default(String me) {
        Rules.Config cfg = new Rules.Config();
        cfg.people = Rules.people(DEF_CONTACTS);
        for (String[] r : Rules.rows(DEF_WAITING_APPS)) cfg.waitingApps.add(r[0]);
        cfg.myName = me;
        return cfg;
    }

    static int lvl(Rules.Config cfg, String pkg, String text, String title, boolean group) {
        Rules.Note n = new Rules.Note();
        n.pkg = pkg; n.ownPkg = OWN; n.text = text; n.title = title; n.group = group;
        n.appLabel = LABELS.containsKey(pkg) ? LABELS.get(pkg) : pkg;
        return Rules.decide(n, cfg);
    }

    static int lvlF(Rules.Config cfg, String pkg, int flags) {
        Rules.Note n = new Rules.Note();
        n.pkg = pkg; n.ownPkg = OWN; n.flags = flags; n.title = "x"; n.appLabel = "x";
        return Rules.decide(n, cfg);
    }

    static void notificationsNew() {
        sec("B. Filtro notifiche: comportamenti nuovi della 0.3 (cambi voluti)");
        Rules.Config cfg = v03Default("Cristina");
        cfg.people = Rules.people("Sempre | Mamma | WA,TEL | +39 333 1234567\nSempre | Partner | TG | @partner\n");
        eq("Mamma su WhatsApp (scelto): passa", lvl(cfg, "com.whatsapp", "Ciao", "Mamma", false), Rules.PASS);
        eq("Mamma su Instagram (non scelto): non passa più subito", lvl(cfg, "com.instagram.android", "Ciao", "Mamma", false), Rules.OTHER);
        eq("Chiamata persa di Mamma (Chiamata scelta): passa", lvl(cfg, "com.google.android.dialer", "Chiamata persa", "Mamma", false), Rules.PASS);
        eq("Partner su WhatsApp (scelto solo Telegram): In attesa", lvl(cfg, "com.whatsapp", "Ciao", "Partner", false), Rules.WAITING);
        eq("Partner su Telegram: passa", lvl(cfg, "org.telegram.messenger", "Ciao", "Partner", false), Rules.PASS);
        Map<String, String> g = new HashMap<String, String>();
        g.put("gruppo famiglia", "sempre"); g.put("genitori nido", "menzioni"); g.put("condominio", "attesa");
        cfg.groups = g;
        eq("Gruppo «Sempre»: passa subito", lvl(cfg, "com.whatsapp", "Foto", "Gruppo famiglia", true), Rules.PASS);
        eq("Gruppo «Sempre» col contatore nel titolo", lvl(cfg, "com.whatsapp", "Foto", "Gruppo famiglia (4 messaggi)", true), Rules.PASS);
        eq("Gruppo «Menzioni» della 0.5: in silenzio, in Altro", lvl(cfg, "com.whatsapp", "Domani gita", "Genitori nido", true), Rules.OTHER);
        eq("Gruppo «In attesa» della 0.5: resta in Notifiche finché non scegli per l'app", lvl(cfg, "org.telegram.messenger", "Assemblea", "Condominio", true), Rules.WAITING);
        eq("Codice in un gruppo qualsiasi: passa comunque", lvl(cfg, "com.whatsapp", "codice 1234", "Condominio", true), Rules.PASS);
        eq("Nome gruppo ripulito", Rules.groupName("Condominio (3 messaggi)"), "Condominio");
        eq("Samsung Telefono riconosciuto come chiamata persa", lvl(v03Default(""), "com.samsung.android.dialer", "Chiamata persa", "Marco", false), Rules.WAITING);
    }

    static void notificationsV04() {
        sec("B2. Filtro notifiche: comportamenti nuovi della 0.4 (cambi voluti)");
        Rules.Config cfg = v03Default("Cristina");
        eq("Promemoria di Google Calendar: passa sempre", lvl(cfg, "com.google.android.calendar", "Pediatra alle 16:30", "Pediatra", false), Rules.PASS);
        eq("Promemoria di Samsung Calendar: passa sempre", lvl(cfg, "com.samsung.android.calendar", "Riunione", "Riunione", false), Rules.PASS);
        eq("Notifica fissa del calendario: resta ignorata", lvlF(cfg, "com.google.android.calendar", 0x2), Rules.IGNORE);
        eq("Riassunto di gruppo del calendario: resta eliminato", lvlF(cfg, "com.google.android.calendar", 0x200), Rules.DROP);
        long now = at(2026, 10, 21, 13, 0);
        cfg.now = now;
        cfg.people = Rules.people("Sempre | Mamma | WA | +39 333\nAttesa | Capo | WA | +39 334\nFrequenti | Giulia | TG | @giulia\n");
        Map<String, String> g = new HashMap<String, String>();
        g.put("condominio", "attesa"); g.put("genitori nido", "menzioni");
        cfg.groups = g;
        List<Rules.Conv> cs = new ArrayList<Rules.Conv>();
        Rules.touch(cs, "WA", "Luca", false, now - 3600000L, true);          // gli hai scritto un'ora fa
        Rules.touch(cs, "TG", "Giulia", false, now - 13 * 3600000L, true);   // 13 ore fa: scaduta
        Rules.touch(cs, "WA", "Calcetto mercoledì", true, now - 60000L, true);
        Rules.touch(cs, "WA", "Condominio", true, now - 60000L, true);
        Rules.touch(cs, "WA", "Capo", false, now - 60000L, true);
        Rules.touch(cs, "WA", "Genitori nido", true, now - 60000L, true);
        cfg.open = Rules.openMap(cs, now);
        eq("Senza conversazioni aperte: come la 0.3", lvl(v03Default("Cristina"), "com.whatsapp", "Ciao", "Luca", false), Rules.WAITING);
        eq("Conversazione aperta: passa", lvl(cfg, "com.whatsapp", "Ciao", "Luca", false), Rules.PASS);
        eq("Conversazione aperta col contatore nel titolo: passa", lvl(cfg, "com.whatsapp", "Ciao", "Luca (2 messaggi)", false), Rules.PASS);
        eq("Conversazione aperta su WhatsApp Business: passa", lvl(cfg, "com.whatsapp.w4b", "Ciao", "Luca", false), Rules.PASS);
        eq("Stesso nome ma su Telegram (aperta solo su WhatsApp): non passa", lvl(cfg, "org.telegram.messenger", "Ciao", "Luca", false), Rules.WAITING);
        eq("Stesso nome ma su Instagram: non passa", lvl(cfg, "com.instagram.android", "Ciao", "Luca", false), Rules.OTHER);
        eq("Scaduta dopo 12 ore: torna In attesa", lvl(cfg, "org.telegram.messenger", "Ciao", "Giulia", false), Rules.WAITING);
        eq("Gruppo aperto: passa", lvl(cfg, "com.whatsapp", "Chi viene?", "Calcetto mercoledì", true), Rules.PASS);
        eq("Gruppo aperto in silenzio: passa (0.6)", lvl(cfg, "com.whatsapp", "Assemblea", "Condominio", true), Rules.PASS);
        eq("Persona aperta ma «Aspettano in Notifiche»: In attesa", lvl(cfg, "com.whatsapp", "Mi chiami?", "Capo", false), Rules.WAITING);
        eq("Gruppo «Menzioni» aperto: passa anche senza menzione", lvl(cfg, "com.whatsapp", "Domani gita", "Genitori nido", true), Rules.PASS);
        eq("Persona «Sempre» non cambia", lvl(cfg, "com.whatsapp", "Ciao", "Mamma", false), Rules.PASS);
        eq("Gruppo non aperto: come prima", lvl(cfg, "com.whatsapp", "Foto", "Gita scuola", true), Rules.OTHER);
        cfg.now = now + 10 * 3600000L + 59 * 60000L;
        eq("Ancora aperta 11 ore e 59 minuti dopo la risposta", lvl(cfg, "com.whatsapp", "Ciao", "Luca", false), Rules.PASS);
        cfg.now = now + 11 * 3600000L + 1 * 60000L;
        eq("Chiusa dopo 12 ore dall'ultima risposta", lvl(cfg, "com.whatsapp", "Ciao", "Luca", false), Rules.WAITING);
        // Ogni risposta fa ripartire le 12 ore
        Rules.touch(cs, "WA", "Luca", false, now + 10 * 3600000L, true);
        cfg.open = Rules.openMap(cs, cfg.now);
        eq("Una nuova risposta riapre le 12 ore", lvl(cfg, "com.whatsapp", "Ciao", "Luca", false), Rules.PASS);
        // Un messaggio ricevuto non apre la conversazione
        List<Rules.Conv> cs2 = new ArrayList<Rules.Conv>();
        Rules.touch(cs2, "WA", "Marta", false, now, false);
        eq("Un messaggio ricevuto non apre la conversazione", Rules.openMap(cs2, now).size(), 0);
        // Salvataggio e lettura
        String txt = Rules.convsText(cs, now);
        List<Rules.Conv> back = Rules.convs(txt);
        eq("Conversazioni: salvate e rilette", back.size(), cs.size());
        eq("Conversazioni: la più recente è la prima", back.get(0).name, "Luca");
        eq("Conversazioni: gruppo riconosciuto", Rules.convs("WA | Calcetto | gruppo | 1 | 2\n").get(0).group, true);
        Rules.touch(cs2, "WA", "Vecchia", false, now - 40L * 24 * 3600000L, false);
        eq("Conversazioni ferme da più di 30 giorni: dimenticate", Rules.convs(Rules.convsText(cs2, now)).size(), 1);
        eq("Riga con il carattere | nel nome resta leggibile", Rules.convs(Rules.convsText(Rules.touch(new ArrayList<Rules.Conv>(), "WA", "A|B", false, now, false), now)).get(0).name, "A/B");
        eq("Nome con segni invisibili di WhatsApp: uguale al nome pulito", Rules.groupName("\u200eCalcetto\u202c (3 messaggi)"), "Calcetto");
        eq("Nome con spazio non separabile", Rules.groupName("Genitori\u00a0nido"), "Genitori nido");
        eq("Contatore in inglese", Rules.groupName("Football: 4 new messages"), "Football");
        Map<String, String> g2 = new HashMap<String, String>(); g2.put("calcetto", "sempre");
        Rules.Config c2 = v03Default("Cristina"); c2.groups = g2;
        eq("Gruppo «Sempre» con segni invisibili nel titolo: passa", lvl(c2, "com.whatsapp", "Foto", "\u200eCalcetto", true), Rules.PASS);
        List<Rules.Conv> c3 = new ArrayList<Rules.Conv>();
        Rules.touch(c3, "WA", "Calcetto", true, now, true);
        Rules.touch(c3, "WA", "\u200eCalcetto (2 messaggi)", true, now, false);
        eq("Stesso gruppo con e senza segni invisibili: una sola conversazione", c3.size(), 1);
        // Tempo dall'ultimo sblocco (0.5)
        eq("Sessione sotto la soglia: niente", Rules.sessionMinutes(now - 9 * 60000L, now, 10), -1);
        eq("Sessione alla soglia: 10 minuti", Rules.sessionMinutes(now - 10 * 60000L, now, 10), 10);
        eq("Nessuna sessione (schermo spento): niente", Rules.sessionMinutes(0, now, 10), -1);
        eq("Testo in minuti", Rules.sessionLabel(23), "Acceso da 23 min");
        eq("Testo oltre l'ora", Rules.sessionLabel(75), "Acceso da 1 h 15 min");
        eq("Testo all'ora esatta", Rules.sessionLabel(120), "Acceso da 2 h");
        eq("Chiamata in corso: pausa", Rules.pausesSession("com.google.android.dialer", "call", 0x2), true);
        eq("Navigazione Maps: pausa", Rules.pausesSession("com.google.android.apps.maps", null, 0x2), true);
        eq("Musica in corso: nessuna pausa", Rules.pausesSession("com.spotify.music", "transport", 0x2), false);
        eq("Chiamata persa (non in corso): nessuna pausa", Rules.pausesSession("com.google.android.dialer", "call", 0), false);
        eq("Codice chat da pacchetto", Rules.chatCode("org.telegram.messenger") + Rules.chatCode("com.whatsapp") + Rules.chatCode("com.instagram.android"), "TGWAnull");
    }

    static void groupsV06() {
        sec("B3. Gruppi della 0.6: app per app, senza menzioni (cambi voluti)");
        Rules.Config cfg = v03Default("Cristina");
        Map<String, String> g = new HashMap<String, String>();
        g.put("gruppo famiglia", "sempre"); g.put("genitori nido", "silenzio");
        cfg.groups = g;
        eq("Gruppo «Passa sempre»: passa", lvl(cfg, "com.whatsapp", "Foto", "Gruppo famiglia", true), Rules.PASS);
        eq("Gruppo nuovo: in silenzio, in Altro", lvl(cfg, "com.whatsapp", "Chi porta la torta?", "Condominio", true), Rules.OTHER);
        eq("Menzione col nome: non conta più", lvl(cfg, "com.whatsapp", "@Cristina ci sei?", "Condominio", true), Rules.OTHER);
        eq("«Ti ha menzionato»: non conta più", lvl(cfg, "com.whatsapp", "Mario ti ha menzionato", "Genitori nido", true), Rules.OTHER);
        Map<String, String> d = new HashMap<String, String>();
        d.put("WA", Rules.DEST_NOTIF); d.put("TG", Rules.DEST_OTHER);
        cfg.groupDest = d;
        eq("WhatsApp: gruppi in silenzio in Notifiche (contati)", lvl(cfg, "com.whatsapp", "Assemblea", "Genitori nido", true), Rules.WAITING);
        eq("WhatsApp Business segue WhatsApp", lvl(cfg, "com.whatsapp.w4b", "Assemblea", "Genitori nido", true), Rules.WAITING);
        eq("Telegram: gruppi in silenzio in Altro", lvl(cfg, "org.telegram.messenger", "Assemblea", "Genitori nido", true), Rules.OTHER);
        eq("Gruppo «Passa sempre» non dipende dalla scelta per l'app", lvl(cfg, "org.telegram.messenger", "Foto", "Gruppo famiglia", true), Rules.PASS);
        g.put("vecchio", "attesa");
        eq("Scelta per l'app vince sulla vecchia «In attesa» della 0.5", lvl(cfg, "org.telegram.messenger", "x", "Vecchio", true), Rules.OTHER);
        eq("Codice in un gruppo in silenzio: passa comunque", lvl(cfg, "org.telegram.messenger", "Il tuo codice è 4455", "Genitori nido", true), Rules.PASS);
        eq("Messaggio diretto: le regole delle persone non cambiano", lvl(cfg, "com.whatsapp", "Ciao", "Mamma", false), Rules.PASS);
        long now = at(2026, 10, 23, 18, 0);
        List<Rules.Conv> cs = new ArrayList<Rules.Conv>();
        Rules.touch(cs, "TG", "Genitori nido", true, now - 60000L, true);
        cfg.open = Rules.openMap(cs, now); cfg.now = now;
        eq("Gruppo in silenzio in cui hai scritto: passa per 12 ore", lvl(cfg, "org.telegram.messenger", "Allora domani?", "Genitori nido", true), Rules.PASS);
        cfg.open = null;
        eq("Conversazioni aperte spente: torna in silenzio", lvl(cfg, "org.telegram.messenger", "Allora domani?", "Genitori nido", true), Rules.OTHER);
        eq("Regola «sempre» riconosciuta", Rules.groupAlways("sempre"), true);
        // 0.6.1
        String cleaned = Rules.stripSamples(DEF_CONTACTS);
        eq("Persone di esempio della prima versione: tolte tutte", Rules.people(cleaned).size(), 0);
        eq("Persone vere restano", Rules.people(Rules.stripSamples(DEF_CONTACTS + "Sempre | Mamma | WA,TEL | +39 333 1234567\n")).size(), 1);
        eq("Persona vera chiamata «Partner» con numero vero: resta", Rules.people(Rules.stripSamples("Sempre | Partner | WA | +39 347 0000000\n")).size(), 1);
        eq("Gruppo WhatsApp non segnato (testo «Mittente: …»): riconosciuto", Rules.looksLikeGroup("com.whatsapp", "Calcetto", "Luca: ci sei stasera?"), true);
        eq("Formato vecchio «Mittente @ Gruppo»: riconosciuto", Rules.looksLikeGroup("com.whatsapp", "Luca @ Calcetto", "ci sei?"), true);
        eq("Nome del gruppo dal formato vecchio", Rules.groupTitle("Luca @ Calcetto"), "Calcetto");
        eq("Messaggio diretto normale: non è un gruppo", Rules.looksLikeGroup("com.whatsapp", "Mamma", "Arrivo alle sei"), false);
        eq("Messaggio diretto con orario «Alle 18: ci sei?»: non è un gruppo", Rules.looksLikeGroup("com.whatsapp", "Mamma", "Ore 18:30 ci sei?"), false);
        eq("Codice in un SMS: non è un gruppo", Rules.looksLikeGroup("com.google.android.apps.messaging", "BNL", "Codice: 123456"), false);
        eq("Codice da un account WhatsApp: non è un gruppo", Rules.looksLikeGroup("com.whatsapp", "Banca", "Codice: 123456"), false);
        eq("App non di chat: mai gruppo", Rules.looksLikeGroup("com.instagram.android", "Instagram", "Luca: bella foto"), false);
        eq("Regole della 0.5 lette come silenzio", Rules.groupAlways("menzioni") || Rules.groupAlways("attesa") || Rules.groupAlways(null), false);
    }

    static void quando() {
        sec("E. Quando serve (0.6): cosa si vede e quando");
        long d0 = at(2026, 10, 23, 0, 0), d1 = at(2026, 10, 24, 0, 0), d2 = at(2026, 10, 25, 0, 0);
        eq("Domani alle 16:59 con impegni oggi: nascosto", Quando.showTomorrow(16, false), false);
        eq("Domani alle 17: compare", Quando.showTomorrow(17, false), true);
        eq("Domani di mattina se oggi non resta niente: compare", Quando.showTomorrow(9, true), true);
        long alarm = at(2026, 10, 24, 6, 45);
        eq("Home, sveglia di domani: prima riga di Domani", Quando.homeAlarm(17, at(2026, 10, 23, 17, 0), alarm, d1, d2), "Sveglia 6:45");
        eq("Home, senza sveglia alle 18: niente", Quando.homeAlarm(18, at(2026, 10, 23, 18, 0), -1, d1, d2), null);
        eq("Home, senza sveglia dalle 19: lo dice", Quando.homeAlarm(19, at(2026, 10, 23, 19, 0), -1, d1, d2), "Nessuna sveglia per domani");
        eq("Home, sveglia tra due giorni dalle 19: «Nessuna sveglia per domani»", Quando.homeAlarm(21, at(2026, 10, 23, 21, 0), at(2026, 10, 25, 9, 0), d1, d2), "Nessuna sveglia per domani");
        eq("0.13.1: dopo mezzanotte la sveglia del mattino sta su Oggi", Quando.todayAlarm(at(2026, 10, 23, 0, 37), at(2026, 10, 23, 7, 0), d1), "Sveglia 7:00");
        eq("0.13.1: sveglia di oggi già suonata: niente", Quando.todayAlarm(at(2026, 10, 23, 9, 0), at(2026, 10, 24, 7, 0), d1), null);
        eq("Blocco di giorno: niente sveglia", Quando.lockAlarm(15, at(2026, 10, 23, 15, 0), alarm, d1, d2), null);
        eq("Blocco dalle 19: sveglia di domani", Quando.lockAlarm(21, at(2026, 10, 23, 21, 34), alarm, d1, d2), "Sveglia domani 6:45");
        eq("Blocco dalle 19 senza sveglia: lo dice (anche il venerdì)", Quando.lockAlarm(21, at(2026, 10, 23, 21, 34), -1, d1, d2), "Nessuna sveglia per domani");
        eq("Blocco, sveglia di oggi tra 1 ora e mezza: compare", Quando.lockAlarm(5, at(2026, 10, 23, 5, 15), at(2026, 10, 23, 6, 45), d1, d2), "Sveglia oggi 6:45");
        eq("Blocco, sveglia di oggi tra 3 ore: non ancora", Quando.lockAlarm(3, at(2026, 10, 23, 3, 45), at(2026, 10, 23, 6, 45), d1, d2), null);
        eq("+ Aggiungi di giorno: oggi", Quando.addDayOffset(18), 0);
        eq("+ Aggiungi dalle 19: domani", Quando.addDayOffset(19), 1);
        check("Giorni di prova coerenti", d0 < d1 && d1 < d2, null);
    }

    static void scopoNotte() {
        sec("H. Pausa e scopo, Di notte (0.10)");
        eq("Scopo: di giorno il livello è quello scelto", Scopo.level(Scopo.PAUSE, false, false, true), Scopo.PAUSE);
        eq("Scopo: di notte la pausa diventa pausa e scopo", Scopo.level(Scopo.PAUSE, false, true, true), Scopo.SCOPE);
        eq("Scopo: di notte le app senza pausa restano libere", Scopo.level(Scopo.NONE, false, true, true), Scopo.NONE);
        eq("Scopo: le app scelte solo per la notte hanno lo scopo di notte", Scopo.level(Scopo.NONE, true, true, true), Scopo.SCOPE);
        eq("Scopo: e di giorno restano libere", Scopo.level(Scopo.NONE, true, false, true), Scopo.NONE);
        eq("Scopo: notte senza «pausa e scopo» non cambia nulla", Scopo.level(Scopo.PAUSE, false, true, false), Scopo.PAUSE);
        eq("Preavviso: 5 min → 1 prima", Scopo.warnLead(5), 1);
        eq("Preavviso: 10 min → 2 prima", Scopo.warnLead(10), 2);
        eq("Preavviso: 30 min → 5 prima", Scopo.warnLead(30), 5);
        eq("Preavviso: 2 min di notte → nessuno", Scopo.warnLead(2), 0);
        eq("Scaduto: la prima volta", Scopo.expired(0, "Instagram", 10, "Rispondere a un messaggio")[0], "10 minuti passati");
        eq("Scaduto: lo scopo nel testo", Scopo.expired(0, "Instagram", 10, "Rispondere a un messaggio")[1], "Volevi: rispondere a un messaggio.");
        eq("Scaduto: alla seconda proroga cambia la domanda", Scopo.expired(1, "Instagram", 15, "Solo guardare")[0], "Ti serve ancora?");
        check("Scaduto: dalla terza nessun avviso", Scopo.expired(2, "Instagram", 20, "") == null, null);
        eq("Pillola dello scopo: tempo che resta", Scopo.pill("Instagram", 5 * 60000L + 1000), "◷  Instagram · 6 min");
        eq("Pillola dello scopo: appena scaduto", Scopo.pill("Instagram", -1000), "◷  Instagram · tempo scaduto");
        eq("Pillola dello scopo: oltre", Scopo.pill("Instagram", -3 * 60000L - 5000), "◷  Instagram · +3 min");
        check("Notte fissa: alle 23:30 sì, alle 22 no, alle 5 sì", Notte.fixed(23) && !Notte.fixed(22) && Notte.fixed(5) && !Notte.fixed(6), null);
        check("Notte automatica: pausa lunga dalle 22 la apre", Notte.opensNight(22, 75), null);
        check("Notte automatica: pausa breve no", !Notte.opensNight(22, 30), null);
        check("Notte automatica: pausa lunga del pomeriggio no", !Notte.opensNight(15, 120), null);
        long h = 3600000L, now = 100 * h;
        eq("Notte: finisce alla sveglia", Notte.end(now, now + 6 * h, now + 9 * h), now + 6 * h);
        eq("Notte: senza sveglia vicina finisce alle 7", Notte.end(now, now + 20 * h, now + 9 * h), now + 9 * h);
        check("Notte: spenta non è mai notte", !Notte.active(false, Notte.FIXED, 2, 0, now), null);
        check("Notte automatica attiva fino alla fine", Notte.active(true, Notte.AUTO, 3, now + h, now) && !Notte.active(true, Notte.AUTO, 3, now - 1, now), null);
        check("Sblocco di notte solo dopo una pausa lunga", Notte.nightUnlock(true, 25) && !Notte.nightUnlock(true, 5) && !Notte.nightUnlock(false, 90), null);
        // 0.14: ora di staccare dalla sveglia e dalle ore di sonno
        eq("Staccare: sveglia 6:45, 7½ ore → 22:45", Notte.hhmm(Notte.offMin(405, 450, Notte.LEAD_MIN)), "22:45");
        eq("A dormire: sveglia 6:45, 7½ ore → 23:15", Notte.hhmm(Notte.bedMin(405, 450)), "23:15");
        eq("Staccare: sveglia 8:00, 8 ore → 23:30", Notte.hhmm(Notte.offMin(480, 480, 30)), "23:30");
        eq("Staccare: sveglia 4:00, 7 ore → 20:30", Notte.hhmm(Notte.offMin(240, 420, 30)), "20:30");
        check("Finestra a cavallo della mezzanotte: 23:00 e 3:00 sì, 12:00 no", Notte.inWindow(1380, 1365, 435) && Notte.inWindow(180, 1365, 435) && !Notte.inWindow(720, 1365, 435), null);
        check("Finestra: l'inizio è dentro, la fine no", Notte.inWindow(1365, 1365, 435) && !Notte.inWindow(435, 1365, 435), null);
        check("Finestra nello stesso giorno", Notte.inWindow(60, 30, 120) && !Notte.inWindow(150, 30, 120), null);
        check("Con l'ora di staccare decide l'app, non la logica a ore fisse", !Notte.active(true, Notte.SCHED, 23, 0, now), null);
    }

    static void tempo() {
        sec("G. Tempo sopra le app (0.9)");
        long m = 60000L;
        // Instagram aperta prima dello sblocco, poi WhatsApp, poi di nuovo Instagram fino a ora
        long[] t = {0, 10 * m, 14 * m, 14 * m, 30 * m};
        String[] p = {"ig", "ig", "wa", "wa", "ig"};
        int[] ty = {Tempo.FG, Tempo.BG, Tempo.FG, Tempo.BG, Tempo.FG};
        String[] top = Tempo.top(t, p, ty, 5 * m, 40 * m, "ess");
        eq("Pillola: l'app che pesa di più conta solo dopo lo sblocco", top[0] + " " + Long.parseLong(top[1]) / m, "ig 15");
        long[] t2 = {0, 5 * m};
        String[] p2 = {"ess", "ig"};
        int[] ty2 = {Tempo.FG, Tempo.FG};
        eq("Pillola: Essenziale non conta mai", Tempo.top(t2, p2, ty2, 0, 6 * m, "ess")[0], "ig");
        check("Pillola: senza eventi nessuna app", Tempo.top(new long[0], new String[0], new int[0], 0, m, "ess") == null, null);
        eq("Pillola: l'app si nomina se pesa più di metà", Tempo.pillText(25, "Instagram", 18), "25 min · 18 su Instagram");
        eq("Pillola: altrimenti solo il totale", Tempo.pillText(25, "Instagram", 12), "25 min");
        eq("Pillola: senza dati di utilizzo solo il totale", Tempo.pillText(25, null, 0), "25 min");
        eq("Pillola: oltre l'ora", Tempo.pillText(75, null, 0), "1 h 15");
        eq("Pillola: sotto la soglia non c'è", Tempo.state(true, true, true, -1, false, false, 0, 0), Tempo.HIDE);
        eq("Pillola: mai sopra Essenziale", Tempo.state(true, true, true, 25, true, false, 0, 0), Tempo.HIDE);
        eq("Pillola: spenta finché non la accendi", Tempo.state(false, true, true, 25, false, false, 0, 0), Tempo.HIDE);
        eq("Pillola: senza permesso non c'è", Tempo.state(true, false, true, 25, false, false, 0, 0), Tempo.HIDE);
        eq("Pillola: in chiamata o in navigazione non c'è", Tempo.state(true, true, true, 25, false, true, 0, 0), Tempo.HIDE);
        eq("Pillola: dopo il tocco un puntino per 10 minuti", Tempo.state(true, true, true, 25, false, false, 5 * m + Tempo.DOT_MS, 6 * m), Tempo.DOT);
        eq("Pillola: poi torna", Tempo.state(true, true, true, 25, false, false, 5 * m, 6 * m), Tempo.PILL);
    }

    static void cerca() {
        sec("F. Ricerca (0.7)");
        eq("Inizia così: punteggio pieno", Cerca.score("Mamma", "ma"), 3);
        eq("Una parola inizia così", Cerca.score("Trade Republic", "rep"), 2);
        eq("Contiene", Cerca.score("Calcetto mercoledì", "merc"), 2);
        eq("Senza accenti", Cerca.score("Calcetto mercoledì", "mercoledi"), 2);
        eq("Maiuscole indifferenti", Cerca.score("BNL", "bnl"), 3);
        eq("Niente in comune", Cerca.score("Satispay", "zqx"), 0);
        check("Le persone che passano sempre vengono prima a parità di nome", Cerca.rank("Marta", "ma", true) > Cerca.rank("Mamma", "ma", false), null);
        check("Ma una corrispondenza migliore vince sulla preferenza", Cerca.rank("Mamma", "mam", false) > Cerca.rank("Tommaso", "mam", true) || Cerca.rank("Tommaso", "mam", true) == 0, null);
        eq("Impostazioni: «pausa» trova la pausa", Cerca.settings("pausa", 3).get(0)[0], "Pausa prima di aprire");
        eq("Impostazioni: «gruppi» trova i gruppi", Cerca.settings("grup", 3).get(0)[0], "Gruppi");
        check("Impostazioni: «banca» trova Servizi e A portata dalle parole chiave (0.14: niente contenitori)", hasSetting(Cerca.settings("banca", 3), "Servizi") && !hasSetting(Cerca.settings("banca", 3), "Contenitori"), null);
        eq("Impostazioni: una lettera sola non basta", Cerca.settings("p", 3).size(), 0);
        List<String> r = new ArrayList<String>();
        r = Cerca.pushRecent(r, "a"); r = Cerca.pushRecent(r, "b"); r = Cerca.pushRecent(r, "c"); r = Cerca.pushRecent(r, "d");
        eq("Recenti: al massimo 3, la più nuova in cima", r.toString(), "[d, c, b]");
        eq("Recenti: niente doppioni", Cerca.pushRecent(r, "b").toString(), "[b, d, c]");
        List<String> nm = java.util.Arrays.asList("Mamma", "Andrea", "Calcetto", "Nido");
        eq("A portata 0.8: ordine scelto, i nuovi in fondo", Cerca.ordered(nm, java.util.Arrays.asList("nido", "Mamma", "Sparito")).toString(), "[Nido, Mamma, Andrea, Calcetto]");
        eq("A portata 0.8: senza ordine resta com'è", Cerca.ordered(nm, new ArrayList<String>()).toString(), "[Mamma, Andrea, Calcetto, Nido]");
        eq("A portata 0.8: sposta su", Cerca.moveUp(nm, 2).toString(), "[Mamma, Calcetto, Andrea, Nido]");
        eq("A portata 0.8: il primo non si sposta", Cerca.moveUp(nm, 0).toString(), "[Mamma, Andrea, Calcetto, Nido]");
    }

    static void rowsCompat() {
        sec("A. Liste di configurazione della v0.2: lette allo stesso modo");
        String[] samples = {DEF_CONTACTS, DEF_WAITING_APPS, "# Sezione | Nome dell'app\nConti | BNL\nConti | ING\nInvestimenti | Trade Republic\n",
                "dm\nToo Good To Go\nVinted !\nWidilo\n", "  \n#x\n a | b |  c \n"};
        for (int i = 0; i < samples.length; i++) {
            List<String[]> o = LegacyV02.rows(samples[i]), n = Rules.rows(samples[i]);
            boolean same = o.size() == n.size();
            for (int k = 0; same && k < o.size(); k++) {
                String[] x = o.get(k), y = n.get(k);
                for (int j = 0; j < Math.max(x.length, y.length); j++) {
                    String vx = j < x.length ? x[j] : "", vy = j < y.length ? y[j] : "";
                    if (!vx.equals(vy)) same = false;
                }
            }
            check("Lista di esempio " + (i + 1) + " letta come nella v0.2", same, null);
        }
        List<String> oldNames = LegacyV02.alwaysNames(DEF_CONTACTS);
        List<String> newNames = new ArrayList<String>();
        for (Rules.Person p : Rules.people(DEF_CONTACTS)) if (p.section.equalsIgnoreCase("Sempre")) newNames.add(p.name.toLowerCase());
        eq("Persone «Sempre» della v0.2", newNames.toString(), oldNames.toString());
        Rules.Person p = Rules.people("Sempre | Mamma | WA, tel | +39 333\n").get(0);
        eq("Più app per persona", p.apps.toString(), "[WA, TEL]");
        eq("Riscrittura della riga", Rules.people(Rules.peopleText(Arrays.asList(p))).get(0).line(), p.line());
    }

    // ---------------------------------------------------------------------

    static long at(int y, int m, int d, int h, int mi) {
        Calendar k = Calendar.getInstance();
        k.clear();
        k.set(y, m - 1, d, h, mi);
        return k.getTimeInMillis();
    }

    static void recurrence() {
        sec("C. Ricorrenze (stile calendario di Windows)");
        long mon = at(2026, 10, 19, 16, 30); // lunedì 19 ottobre 2026
        Rec r = new Rec();
        r.freq = Rec.WEEKLY;
        eq("Ogni settimana, giorno preso dalla data", r.toRule(mon), "FREQ=WEEKLY;BYDAY=MO;WKST=MO");
        eq("Etichetta breve", r.shortLabel(mon), "Ogni lunedì");
        r.interval = 2; r.days = 1 | 4; r.end = Rec.END_COUNT; r.count = 10;
        eq("Ogni 2 settimane lun e mer, 10 volte", r.toRule(mon), "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE;WKST=MO;COUNT=10");
        eq("Riepilogo", r.summary(mon), "Ogni 2 settimane il lunedì e il mercoledì, per 10 volte");
        Rec w5 = new Rec(); w5.freq = Rec.WEEKLY; w5.days = 0x1F;
        eq("Dal lunedì al venerdì", w5.summary(mon), "Dal lunedì al venerdì");
        Rec m = new Rec(); m.freq = Rec.MONTHLY;
        eq("Ogni mese il giorno 19", m.toRule(mon), "FREQ=MONTHLY;BYMONTHDAY=19");
        eq("Riepilogo giorno del mese", m.summary(mon), "Ogni mese il giorno 19");
        m.byPos = true;
        eq("Ogni mese il terzo lunedì", m.toRule(mon), "FREQ=MONTHLY;BYDAY=3MO");
        eq("Riepilogo terzo lunedì", m.summary(mon), "Ogni mese il terzo lunedì");
        eq("Ultimo venerdì del mese", Rec.weekPos(at(2026, 10, 30, 9, 0)), -1);
        Rec y = new Rec(); y.freq = Rec.YEARLY;
        eq("Ogni anno", y.summary(mon), "Ogni anno il 19 ottobre");
        Rec d = new Rec(); d.freq = Rec.DAILY; d.end = Rec.END_UNTIL; d.until = at(2026, 12, 31, 0, 0);
        eq("Ogni giorno fino al 31/12 (UTC)", d.toRule(mon), "FREQ=DAILY;UNTIL=20261231T225959Z");
        eq("Riepilogo con data di fine", d.summary(mon), "Ogni giorno, fino al 31 dic 2026");
        eq("Mai: nessuna regola", new Rec().toRule(mon), null);

        String[] rules = {"FREQ=WEEKLY;BYDAY=MO;WKST=MO", "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE;WKST=MO;COUNT=10", "FREQ=MONTHLY;BYMONTHDAY=19",
                "FREQ=MONTHLY;BYDAY=3MO", "FREQ=DAILY;UNTIL=20261231T225959Z", "FREQ=YEARLY"};
        for (String s : rules) eq("Andata e ritorno: " + s, Rec.parse(s, mon).toRule(mon), s);
        Rec g = Rec.parse("RRULE:FREQ=WEEKLY;WKST=SU;BYDAY=TU,TH", mon);
        eq("Regola di Google Calendar letta", g.summary(mon), "Ogni settimana il martedì e il giovedì");
        Rec c = Rec.parse("FREQ=MONTHLY;BYSETPOS=-1;BYDAY=MO,TU,WE,TH,FR", mon);
        eq("Regola sconosciuta conservata intatta", c.toRule(mon), "FREQ=MONTHLY;BYSETPOS=-1;BYDAY=MO,TU,WE,TH,FR");
        eq("…e mostrata come personalizzata", c.shortLabel(mon), "Personalizzata");
        eq("Data di fine senza ora (20261231)", Rec.parse("FREQ=DAILY;UNTIL=20261231", mon).until, at(2026, 12, 31, 0, 0));
        eq("«Questo e i successivi»: chiude la serie prima", Rec.withUntil("FREQ=WEEKLY;BYDAY=MO;COUNT=5", at(2026, 10, 26, 16, 29)),
                "FREQ=WEEKLY;BYDAY=MO;UNTIL=20261026T152900Z");
    }

    // ---------------------------------------------------------------------

    static VoiceParser.Cmd v(String s) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(at(2026, 10, 19, 10, 0)); // lunedì
        return VoiceParser.parse(s, Arrays.asList("Mamma", "Partner", "Sorella", "Nido segreteria"), now);
    }

    static void voice() {
        sec("D. Comandi vocali (esempi dei wireframe)");
        VoiceParser.Cmd c = v("Scrivi a Mamma su WhatsApp: Arrivo alle sei");
        eq("Scrivi a… su WhatsApp: tipo", c.type, VoiceParser.WRITE);
        eq("…persona", c.person, "Mamma");
        eq("…app", c.app, "WA");
        eq("…testo", c.text, "Arrivo alle sei");
        c = v("scrivi a mamma su whatsapp arrivo alle sei");
        eq("Senza i due punti (come lo scrive il riconoscimento)", c.person + "|" + c.app + "|" + c.text, "Mamma|WA|Arrivo alle sei");
        c = v("manda un messaggio alla mia sorella che faccio tardi");
        eq("Manda un messaggio a… che…", c.type + "|" + c.text, VoiceParser.WRITE + "|Faccio tardi");
        c = v("manda un messaggio a Partner che faccio tardi");
        eq("Persona senza app: userà la sua prima app", c.person + "|" + c.app + "|" + c.text, "Partner|null|Faccio tardi");
        c = v("scrivi su telegram a sorella ciao");
        eq("Scrivi su Telegram a…", c.person + "|" + c.app + "|" + c.text, "Sorella|TG|Ciao");
        c = v("scrivi a nido segreteria domani la bimba non viene");
        eq("Nome di due parole", c.person + "|" + c.text, "Nido segreteria|Domani la bimba non viene");
        c = v("Chiama mamma");
        eq("Chiama…", c.type + "|" + c.person + "|" + c.personKnown, VoiceParser.CALL + "|Mamma|true");
        c = v("chiama Luca");
        eq("Chiama una persona non scelta", c.person + "|" + c.personKnown, "Luca|false");

        c = v("Sposta il pediatra a domani alle 17");
        eq("Sposta…", c.type + "|" + c.target + "|" + c.day + "|" + c.minutes, VoiceParser.EVENT_MOVE + "|Pediatra|1|1020");
        c = v("sposta la cena di venerdì a sabato alle 20");
        eq("Sposta da un giorno a un altro", c.target + "|" + c.fromDay + "|" + c.day + "|" + c.minutes, "Cena|4|5|1200");
        c = v("Cancella la cena da mamma di venerdì");
        eq("Cancella…", c.type + "|" + c.target + "|" + c.fromDay, VoiceParser.EVENT_DELETE + "|Cena da mamma|4");
        c = v("Aggiungi dentista giovedì alle 15 e 30");
        eq("Aggiungi impegno con ora", c.type + "|" + c.text + "|" + c.day + "|" + c.minutes, VoiceParser.EVENT_ADD + "|Dentista|3|930");
        c = v("aggiungi pediatra domani alle 4");
        eq("«Alle 4» vuol dire 16:00", c.minutes, 960);
        c = v("aggiungi riunione il 25 alle 9");
        eq("Il 25 alle 9", c.day + "|" + c.minutes, "6|540");
        c = v("nuovo appuntamento parrucchiere 3 novembre");
        eq("Nuovo appuntamento con data", c.type + "|" + c.text + "|" + c.day, VoiceParser.EVENT_ADD + "|Parrucchiere|15");

        c = v("Aggiungi task comprare pannolini entro domani");
        eq("Aggiungi task… entro…", c.type + "|" + c.text + "|" + c.day, VoiceParser.TASK_ADD + "|Comprare pannolini|1");
        c = v("ricordami di chiamare il nido");
        eq("Ricordami di…", c.type + "|" + c.text + "|" + c.day, VoiceParser.TASK_ADD + "|Chiamare il nido|-1");
        c = v("aggiungi latte");
        eq("Aggiungi senza ora: task", c.type + "|" + c.text, VoiceParser.TASK_ADD + "|Latte");
        c = v("segna fatto comprare pannolini");
        eq("Segna fatto…", c.type + "|" + c.target, VoiceParser.TASK_DONE + "|Comprare pannolini");

        c = v("Apri Vinted");
        eq("Apri…", c.type + "|" + c.target, VoiceParser.OPEN + "|Vinted");
        c = v("Portami in via Tortona 27");
        eq("Portami in…", c.type + "|" + c.target, VoiceParser.NAV + "|via Tortona 27");
        c = v("come arrivo al Parco Sempione");
        eq("Come arrivo al…", c.type + "|" + c.target, VoiceParser.NAV + "|Parco Sempione");
        c = v("Cerca farmacia su Maps");
        eq("Cerca… su Maps", c.type + "|" + c.target, VoiceParser.MAPS + "|farmacia");
        c = v("Che tempo fa domani a Milano?");
        eq("Domanda generica: a Gemini", c.type + "|" + c.text, VoiceParser.ASK + "|Che tempo fa domani a Milano");
        eq("Cosa posso dire", v("cosa posso dire").type, VoiceParser.HELP);
        eq("Somiglianza titoli", VoiceParser.score("il pediatra", "Pediatra"), 100);
        check("Somiglianza parziale", VoiceParser.score("cena da mamma", "Cena da mamma e papà") >= 70, null);
    }

    static void cosa() {
        sec("0.12 · Una cosa adesso e sblocchi senza scopo");
        java.util.List<String> ids = java.util.Arrays.asList("a", "b", "c");
        java.util.List<String> none = new java.util.ArrayList<String>();
        eq("Propone la prima attività di oggi", Cosa.pick(ids, none, 0), 0);
        eq("Dopo un «Più tardi» propone la successiva", Cosa.pick(ids, java.util.Arrays.asList("a"), 1), 1);
        eq("Due «Più tardi» di fila: per oggi niente", Cosa.pick(ids, java.util.Arrays.asList("a", "b"), 2), -1);
        check("Due «Più tardi» spengono la giornata", Cosa.offToday(2) && !Cosa.offToday(1), null);
        eq("Senza attività: nessuna proposta (resta l'alternativa)", Cosa.pick(none, none, 0), -1);
        eq("Tutte rimandate una volta: si riparte dalla prima", Cosa.pick(java.util.Arrays.asList("a"), java.util.Arrays.asList("a"), 1), 0);
        long now = 1_000_000_000L;
        java.util.List<Long> two = java.util.Arrays.asList(now - 5 * 60000L, now - 2 * 60000L);
        check("Terzo sblocco in 10 minuti senza aprire niente: chiede", Cosa.ask(two, now, 0, false, false), null);
        check("Solo due sblocchi a vuoto: non chiede", !Cosa.ask(java.util.Arrays.asList(now - 60000L), now, 0, false, false), null);
        check("Sblocchi più vecchi di 10 minuti non contano", !Cosa.ask(java.util.Arrays.asList(now - 15 * 60000L, now - 2 * 60000L), now, 0, false, false), null);
        check("Al massimo 3 volte al giorno", !Cosa.ask(two, now, 3, false, false), null);
        check("Mai di notte", !Cosa.ask(two, now, 0, true, false), null);
        check("Mai in chiamata o con la navigazione", !Cosa.ask(two, now, 0, false, true), null);
        check("Solo «Mi annoiavo» porta la proposta", Cosa.proposeAfter("Mi annoiavo") && !Cosa.proposeAfter("Aspettavo") && !Cosa.proposeAfter("Per staccare"), null);
        eq("Pausa: 8 secondi di default", Cosa.pauseSeconds(0), 8);
        eq("Pausa: 4 secondi se scelti", Cosa.pauseSeconds(4), 4);
        eq("Pausa: valori strani tornano a 8", Cosa.pauseSeconds(5), 8);
    }

    static Rules.Config svc() {
        Rules.Config cfg = new Rules.Config();
        for (String[] r : Rules.rows(DEF_WAITING_APPS)) cfg.waitingApps.add(r[0]);
        cfg.services.add("pkg:bank"); cfg.services.add("pkg:poste"); cfg.services.add("IO"); cfg.services.add("Banca");
        return cfg;
    }

    static int svcLvl(String pkg, String label, String title, String text) {
        Rules.Note n = new Rules.Note();
        n.pkg = pkg; n.ownPkg = OWN; n.title = title; n.text = text; n.appLabel = label;
        return Rules.decide(n, svc());
    }

    static boolean hasSetting(java.util.List<String[]> l, String t) {
        for (String[] x : l) if (t.equals(x[0])) return true;
        return false;
    }

    static void stessaPersona() {
        sec("0.13.8 · la stessa persona si riconosce dal numero");
        eq("+39 333 123 4567 e 3331234567 sono lo stesso numero", Cerca.digits("+39 333 123 4567"), Cerca.digits("3331234567"));
        eq("0039 333-1234567 come sopra", Cerca.digits("0039 333-1234567"), "331234567");
        check("Un nome non è un numero", Cerca.digits("Filippo") == null, null);
    }

    static void servizi() {
        sec("0.12.2 · SMS con codici e app di servizio");
        String sms = "com.google.android.apps.messaging";
        eq("SMS «482913 è il tuo codice» passa", svcLvl(sms, "Messaggi", "PosteInfo", "482913 è la password temporanea per accedere"), Rules.PASS);
        eq("SMS «Usa 5521 per confermare» passa", svcLvl(sms, "Messaggi", "Intesa SP", "Usa 5521 per completare l'operazione"), Rules.PASS);
        eq("SMS senza testo (nascosto da Android) passa", svcLvl(sms, "Messaggi", "INPS", ""), Rules.PASS);
        eq("SMS normale: mai in Altro, in Notifiche", svcLvl(sms, "Messaggi", "Marco Rossi", "Ci vediamo dopo"), Rules.WAITING);
        eq("SMS con un orario non è un codice", svcLvl(sms, "Messaggi", "Marco Rossi", "Ci vediamo alle 16:30"), Rules.WAITING);
        eq("SMS con un numero di telefono non è un codice", svcLvl(sms, "Messaggi", "Marco Rossi", "Chiamami al +39 333 1234567"), Rules.WAITING);
        eq("SMS con un importo non è un codice", svcLvl(sms, "Messaggi", "Negozio", "Totale 1250,00 euro"), Rules.WAITING);
        eq("App IO: avviso di accesso passa", svcLvl("it.pagopa.io.app", "IO", "IO", "Nuovo accesso alla tua identità"), Rules.PASS);
        eq("App IO: messaggio qualsiasi in Notifiche, non in Altro", svcLvl("it.pagopa.io.app", "IO", "IO", "Hai un nuovo messaggio dal Comune"), Rules.WAITING);
        eq("Banca: «Bonifico ricevuto» passa", svcLvl("it.bancaxyz.mobile", "Banca XYZ", "Banca XYZ", "Bonifico ricevuto da Mario"), Rules.PASS);
        eq("Banca: notifica senza parole di accesso in Notifiche", svcLvl("com.mybank.app", "MyBank", "MyBank", "Scopri le novità"), Rules.WAITING);
        eq("PosteID: richiesta di autorizzazione passa", svcLvl("posteitaliane.posteapp.appposteid", "PosteID", "PosteID", "Autorizza l'accesso"), Rules.PASS);
        eq("Un'app qualsiasi resta in Altro", svcLvl("com.instagram.android", "Instagram", "Instagram", "Nuovo follower"), Rules.OTHER);
        check("«IO» vale solo come nome esatto, non dentro altri nomi", !Rules.isService("x.y", "Ricordi", svc().services), null);
        java.util.List<String> spid = java.util.Arrays.asList("pkg:sielte", "MySielte", "ArubaID", "EHT");
        check("0.13.10: MySielte ID è un servizio (pacchetto o nome)", Rules.isService("it.sielte.mysielteid", "MySielte ID", spid) && Rules.isService("x.y", "MySielte ID", spid), null);
        check("0.13.10: «EHT» vale solo come nome esatto", !Rules.isService("x.y", "Beht", spid), null);
        java.util.List<String> sv = new java.util.ArrayList<String>(svc().services);
        sv.add("!pkg:com.mybank.app");
        check("0.13: un servizio tolto a mano («!pkg:») non è più un servizio", !Rules.isService("com.mybank.app", "MyBank", sv) && Rules.isService("it.bancaxyz.mobile", "Banca XYZ", sv), null);
        sv.add("pkg:com.miapp.casa");
        check("0.13: un'app aggiunta a mano è un servizio", Rules.isService("com.miapp.casa", "Casa", sv), null);
        check("Codice: 6 cifre sì, data no", Rules.looksLikeCode("codice 123456") && !Rules.looksLikeCode("il 12/10/2026") && !Rules.looksLikeCode("alle 16:30"), null);
    }
}
