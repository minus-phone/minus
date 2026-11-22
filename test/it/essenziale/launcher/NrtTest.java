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
        cerca020();
        tempo();
        scopoNotte();
        rowsCompat();
        recurrence();
        voice();
        cosa();
        servizi();
        registro();
        controlli();
        ordine();
        stessaPersona();
        urgenti();
        serale();
        telefono();
        movimento();
        attivitaInCorso();
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
        Rules.Config sv = v03Default("Cristina");
        for (String l : ("pkg:authenticator\npkg:deskclock\npkg:com.google.android.calendar\npkg:twofasapp\n").split("\n")) sv.services.add(l);
        Rules.Config ap = v03Default("Cristina");
        Rules.Note nn = new Rules.Note();
        nn.pkg = "it.pediatra.app"; nn.ownPkg = OWN; nn.title = "Promemoria visita"; nn.text = "Domani alle 10"; nn.appLabel = "Pediatra";
        nn.fresh = true;
        eq("App nuove 0.15: senza scelta vanno in Per te, mai in Altro", Rules.decide(nn, ap), Rules.WAITING);
        ap.appChoice.put("it.pediatra.app", Rules.APP_ALWAYS);
        eq("App nuove 0.15: «passano sempre»", Rules.decide(nn, ap), Rules.PASS);
        ap.appChoice.put("it.pediatra.app", Rules.APP_OTHER);
        eq("App nuove 0.15: «in Altro»", Rules.decide(nn, ap), Rules.OTHER);
        nn.text = "Il tuo codice è 482913";
        eq("App nuove 0.15: un codice passa comunque", Rules.decide(nn, ap), Rules.PASS);
        Rules.Config gc = v03Default("Cristina");
        gc.now = 1000000;
        gc.groups = new java.util.HashMap<String, String>();
        gc.open = new java.util.HashMap<String, Long>();
        gc.open.put(Rules.convKey("WA", "Genitori 2A"), gc.now + 3600000L);
        gc.replied = new java.util.HashMap<String, Long>();
        gc.groupDest = new java.util.HashMap<String, String>();
        gc.groupDest.put("WA", Rules.DEST_NOTIF);
        Rules.Note gn = new Rules.Note();
        gn.pkg = "com.whatsapp"; gn.ownPkg = OWN; gn.title = "Genitori 2A"; gn.text = "Marta: domani gita"; gn.appLabel = "WhatsApp"; gn.group = true;
        eq("Gruppi 0.15: aperto da una notifica ma senza scrivere, non passa", Rules.decide(gn, gc), Rules.WAITING);
        gc.replied.put(Rules.convKey("WA", "Genitori 2A"), gc.now + 3600000L);
        eq("Gruppi 0.15: se gli hai scritto passa", Rules.decide(gn, gc), Rules.PASS);
        gc.replied.clear();
        gc.groups.put("genitori 2a", "altro");
        eq("Gruppi 0.15: in silenzio, in Altro, anche se per WhatsApp i gruppi vanno in Per te", Rules.decide(gn, gc), Rules.OTHER);
        gc.groups.put("genitori 2a", "notifiche");
        gc.groupDest.put("WA", Rules.DEST_OTHER);
        eq("Gruppi 0.15: in silenzio, in Per te, anche se per WhatsApp vanno in Altro", Rules.decide(gn, gc), Rules.WAITING);
        eq("Servizi 0.15: un'app di autenticazione passa per intero", lvl(sv, "com.twofasapp", "Nuovo dispositivo collegato", "2FAS", false), Rules.PASS);
        eq("Servizi 0.15: il calendario passa", lvl(sv, "com.google.android.calendar", "Pediatra alle 9:30", "Calendar", false), Rules.PASS);
        sv.services.add("!pkg:com.google.android.calendar");
        check("Servizi 0.15: tolto a mano dai Servizi, il calendario non passa più sempre", lvl(sv, "com.google.android.calendar", "Pediatra alle 9:30", "Calendar", false) != Rules.PASS, null);
        check("Servizi 0.15: riconosce wallet, sveglie, autenticazione e calendari", Rules.alwaysService("com.google.android.apps.walletnfcrel") && Rules.alwaysService("com.sec.android.app.clockpackage")
                && Rules.alwaysService("com.azure.authenticator") && Rules.alwaysService("com.samsung.android.calendar") && !Rules.alwaysService("com.instagram.android"), null);
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
        eq("+ Aggiungi di giorno: oggi", Quando.addDayOffset(17), 0);
        eq("+ Aggiungi dalle 18: domani", Quando.addDayOffset(18), 1);
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
        long m1 = 60000L;
        eq("Pillola 0.15 (204): chiusa mostra i minuti che restano", Scopo.closed(5 * m1 + 10), "6'");
        eq("Pillola 0.15 (207): appena scaduta 0'", Scopo.closed(-20000), "0'");
        eq("Pillola 0.15 (209): poi i minuti in più", Scopo.closed(-4 * m1 - 5000), "+4'");
        check("Pillola 0.15: l'anello si svuota", Scopo.ring(3 * m1, 6 * m1) == 0.5f && Scopo.ring(-1, 6 * m1) == 0f && Scopo.ring(9 * m1, 6 * m1) == 1f, null);
        eq("Pillola 0.15 (205): aperta", Scopo.openTitle("Instagram", 6 * m1) + " / " + Scopo.openLine("Rispondere a un messaggio", 6 * m1), "Instagram · 6 min / Volevi: rispondere a un messaggio");
        eq("Pillola 0.15 (209): oltre il tempo", Scopo.openTitle("Instagram", -4 * m1) + " / " + Scopo.openLine("x", -4 * m1), "Instagram · +4 min / Oltre il tempo che avevi scelto");
        eq("Pillola 0.15 (206): preavviso", java.util.Arrays.toString(Scopo.warn(1, "Rispondere a un messaggio")), "[Tra 1 minuto, Rispondere a un messaggio. Hai finito?, Sì, chiudo, Ok]");
        eq("Pillola 0.15 (207): il foglio dice cosa chiudi", Scopo.sheet(0, "Instagram", 6, "Rispondere a un messaggio")[2], "Chiudo Instagram");
        eq("Pillola 0.15 (208): la seconda volta", Scopo.sheet(1, "Instagram", 11, "Rispondere a un messaggio")[1], "Sei su Instagram da 11 minuti. Volevi: rispondere a un messaggio.");
        check("Pillola 0.15 (209): dalla terza volta nessun foglio", Scopo.sheet(2, "Instagram", 16, "") == null, null);
        check("Pillola 0.15 (209): chiusa a mano si riapre dopo un minuto", !Scopo.reopen(1000, 1000 + 30000) && Scopo.reopen(1000, 1000 + 60000), null);
        check("Pausa 0.15: torni all'app entro 3 minuti, niente pausa di nuovo", Scopo.returning("ig", 1000, "ig", 1000 + 2 * m1), null);
        check("Pausa 0.15: dopo 3 minuti la pausa torna", !Scopo.returning("ig", 1000, "ig", 1000 + 3 * m1), null);
        check("Pausa 0.15: vale solo per l'app che hai aperto", !Scopo.returning("ig", 1000, "tiktok", 1000 + m1) && !Scopo.returning("", 1000, "ig", 1000), null);
        check("Pillola 0.15: finito uno scopo, il tempo dall'ultimo sblocco tace 10 minuti", Tempo.quietAfterScope(1000, 1000 + 9 * m1) && !Tempo.quietAfterScope(1000, 1000 + 10 * m1) && !Tempo.quietAfterScope(0, 5000), null);
        check("Pillola 0.15 (209): un tocco ogni 5 minuti", !Scopo.buzz(0, 4 * m1) && Scopo.buzz(0, 5 * m1), null);
        check("Scaduto: dalla terza nessun avviso", Scopo.expired(2, "Instagram", 20, "") == null, null);
        eq("Pillola dello scopo: tempo che resta", Scopo.pill("Instagram", 5 * 60000L + 1000), "◷  Instagram · 6 min");
        eq("Pillola dello scopo: appena scaduto", Scopo.pill("Instagram", -1000), "◷  Instagram · tempo scaduto");
        eq("Pillola dello scopo: oltre", Scopo.pill("Instagram", -3 * 60000L - 5000), "◷  Instagram · +3 min");
        eq("Sveglia delle 6:50: vale come risveglio", Notte.plausibleWake(410), 410);
        eq("Allarme delle 11:30: non è una sveglia", Notte.plausibleWake(690), -1);
        eq("Allarme delle 3: non è una sveglia", Notte.plausibleWake(180), -1);
        check("Con il risveglio alle 7 le 10:33 non sono sera", !Notte.inWindow(633, Notte.offMin(420, 480, 30), 420), null);
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
        // 0.18 (Evolutiva 012): solo il tempo nelle app frenate, fermo nelle altre
        java.util.Set<String> fr = new java.util.HashSet<String>(java.util.Arrays.asList("ig", "tt"));
        long[] t3 = {0, 6 * m, 6 * m, 20 * m, 20 * m};
        String[] p3 = {"tt", "tt", "wa", "wa", "ig"};
        int[] ty3 = {Tempo.FG, Tempo.BG, Tempo.FG, Tempo.BG, Tempo.FG};
        Tempo.Braked bk = Tempo.braked(t3, p3, ty3, 0, 32 * m, fr);
        eq("E012: 6 min di TikTok + 12 di Instagram, WhatsApp non conta", bk.totalMs / m, 18L);
        eq("E012: l'app frenata con più tempo", bk.top + " " + bk.topMs / m, "ig 12");
        check("E012: la pillola c'è dentro Instagram", Tempo.inBraked(bk, fr), null);
        eq("E012: oltre la soglia di 10 min", Tempo.brakedMinutes(bk, 10), 18);
        eq("E012: sotto la soglia di 20 min niente", Tempo.brakedMinutes(bk, 20), -1);
        Tempo.Braked bk2 = Tempo.braked(new long[]{0, 6 * m, 6 * m}, new String[]{"ig", "ig", "wa"}, new int[]{Tempo.FG, Tempo.BG, Tempo.FG}, 0, 40 * m, fr);
        check("E012: in WhatsApp niente pillola", !Tempo.inBraked(bk2, fr), null);
        eq("E012: in WhatsApp il conteggio è fermo", bk2.totalMs / m, 6L);
        eq("E012: senza accesso ai dati di utilizzo non si conta", Tempo.brakedMinutes(null, 10), -1);
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

    // ---- Evolutiva 020 · La ricerca: ordine fisso, limiti, spareggio, Migliore ----
    static Cerca.Cand cd(String key, int kind, String label, int score) { return new Cerca.Cand(key, kind, label, score); }
    static List<String> keys(List<Cerca.Cand> l) { List<String> o = new ArrayList<String>(); for (Cerca.Cand c : l) o.add(c.key); return o; }

    static void cerca020() {
        sec("F2. Ricerca 0.24 (Evolutiva 020): una riga sola, un ordine fisso");
        eq("Gruppi nell'ordine fisso", Arrays.toString(Cerca.TITOLI), "[Contatti e gruppi, App, Impegni, Azioni, Impostazioni, Chiedi]");
        eq("Limiti 3/3/2/2/2/1 (Migliore 1 a parte)", Arrays.toString(Cerca.LIMITI), "[3, 3, 2, 2, 2, 1]");
        eq("In tutto 8 righe", Cerca.MAX_RIGHE, 8);
        // Migliore: il punteggio più alto vince, senza titolo e tolto dal suo gruppo
        List<Cerca.Cand> a = new ArrayList<Cerca.Cand>();
        a.add(cd("p:marco", Cerca.PERSONA, "Marco", 30));
        a.add(cd("a:maps", Cerca.APP, "Maps", 35));
        a.add(cd("x:chiedi", Cerca.CHIEDI, "Chiedi", 0));
        Cerca.Plan p = Cerca.plan(a, -1);
        eq("Migliore: l'app con il punteggio più alto", p.best.key, "a:maps");
        eq("Migliore: tolto dal suo gruppo App", p.groups.get(Cerca.APP).size(), 0);
        eq("Migliore: la persona resta nel suo gruppo", keys(p.groups.get(Cerca.PERSONA)).toString(), "[p:marco]");
        // spareggio: a pari punteggio persone > app > impegni > impostazioni
        a = new ArrayList<Cerca.Cand>();
        a.add(cd("s:1", Cerca.IMPOSTAZIONE, "Mail impostazioni", 30));
        a.add(cd("ev:1", Cerca.IMPEGNO, "Mario", 30));
        a.add(cd("a:mail", Cerca.APP, "Mail", 30));
        a.add(cd("p:mara", Cerca.PERSONA, "Mara", 30));
        eq("Spareggio di tipo: persone prima", Cerca.plan(a, -1).best.key, "p:mara");
        a.remove(3);
        eq("Spareggio di tipo: poi le app", Cerca.plan(a, -1).best.key, "a:mail");
        a.remove(2);
        eq("Spareggio di tipo: poi gli impegni", Cerca.plan(a, -1).best.key, "ev:1");
        a.remove(1);
        eq("Spareggio di tipo: infine le impostazioni", Cerca.plan(a, -1).best.key, "s:1");
        // stesso tipo: il più recente, poi l'alfabeto senza accenti
        a = new ArrayList<Cerca.Cand>();
        a.add(cd("a:b", Cerca.APP, "Banca", 30).recent(100));
        a.add(cd("a:a", Cerca.APP, "Azienda", 30).recent(100));
        a.add(cd("a:c", Cerca.APP, "Chat", 30).recent(500));
        a.add(cd("a:e", Cerca.APP, "Èra", 30).recent(100));
        p = Cerca.plan(a, -1);
        eq("Stesso tipo: il più recente è Migliore", p.best.key, "a:c");
        eq("Stesso tipo: poi alfabetico senza accenti (Azienda, Banca, Èra dopo Banca)", keys(p.groups.get(Cerca.APP)).toString(), "[a:a, a:b, a:e]");
        eq("App: oltre le 3 righe «Vedi tutti» conta quelle nascoste (4 meno Migliore = 3 da mostrare)", p.more[Cerca.APP], 0);
        a.add(cd("a:z", Cerca.APP, "Zeta", 30).recent(100));
        p = Cerca.plan(a, -1);
        eq("App: Migliore + 3 righe, una nascosta", p.groups.get(Cerca.APP).size() + "/" + p.more[Cerca.APP], "3/1");
        eq("Vedi tutti apre il gruppo sul posto (fino a 8)", Cerca.plan(a, Cerca.APP).groups.get(Cerca.APP).size(), 4);
        eq("Vedi tutti: poi niente nascosto", Cerca.plan(a, Cerca.APP).more[Cerca.APP], 0);
        // il punteggio batte il tipo; il +5 conta
        a = new ArrayList<Cerca.Cand>();
        a.add(cd("p:x", Cerca.PERSONA, "Xavier", 10));
        a.add(cd("a:x", Cerca.APP, "Xapp", 20));
        eq("Il punteggio più alto vince anche contro una persona", Cerca.plan(a, -1).best.key, "a:x");
        // Azioni e Chiedi non sono mai Migliore
        a = new ArrayList<Cerca.Cand>();
        a.add(cd("x:esegui", Cerca.AZIONE, "Esegui", 99));
        a.add(cd("q:chiedi", Cerca.CHIEDI, "Chiedi", 99));
        p = Cerca.plan(a, -1);
        check("Azioni e Chiedi non sono mai Migliore", p.best == null, null);
        // zero risultati: nessuna preselezione, Chiedi con tre righe, Aggiungi nascosto
        a = new ArrayList<Cerca.Cand>();
        a.add(cd("x:aggiungi", Cerca.AZIONE, "Aggiungi", 0).needsHit());
        a.add(cd("q:chiedi", Cerca.CHIEDI, "Chiedi", 0).order(0));
        a.add(cd("q:web", Cerca.CHIEDI, "Web", 0).order(1));
        a.add(cd("q:store", Cerca.CHIEDI, "Store", 0).order(2));
        p = Cerca.plan(a, -1);
        check("Zero risultati: nessuna preselezione (Invio non fa niente)", p.best == null && !p.anything, null);
        eq("Zero risultati: Chiedi ha tre righe", keys(p.groups.get(Cerca.CHIEDI)).toString(), "[q:chiedi, q:web, q:store]");
        eq("Zero risultati: «Aggiungi come impegno» non compare", p.groups.get(Cerca.AZIONE).size(), 0);
        eq("Annuncio senza risultati", Cerca.announcement(null, 0), "Nessun risultato");
        // con un risultato Chiedi è una riga sola e Aggiungi compare
        a.add(cd("a:maps", Cerca.APP, "Maps", 30));
        p = Cerca.plan(a, -1);
        eq("Un risultato: Chiedi torna a una riga", keys(p.groups.get(Cerca.CHIEDI)).toString(), "[q:chiedi]");
        eq("Un risultato: Aggiungi compare", keys(p.groups.get(Cerca.AZIONE)).toString(), "[x:aggiungi]");
        eq("Annuncio: Migliore e conto", Cerca.announcement("Maps", 2), "Migliore: Maps. 2 risultati");
        eq("Annuncio: un risultato", Cerca.announcement(null, 1), "1 risultato");
        // massimo 8 righe, Chiedi riservata: i gruppi in basso si accorciano
        a = new ArrayList<Cerca.Cand>();
        for (int i = 0; i < 6; i++) a.add(cd("p:" + i, Cerca.PERSONA, "P" + i, 30 - i));
        for (int i = 0; i < 6; i++) a.add(cd("a:" + i, Cerca.APP, "A" + i, 20 - i));
        for (int i = 0; i < 4; i++) a.add(cd("ev:" + i, Cerca.IMPEGNO, "E" + i, 15 - i));
        for (int i = 0; i < 4; i++) a.add(cd("s:" + i, Cerca.IMPOSTAZIONE, "S" + i, 10 - i).order(i));
        a.add(cd("x:esegui", Cerca.AZIONE, "Esegui", 0).order(0));
        a.add(cd("x:aggiungi", Cerca.AZIONE, "Aggiungi", 0).order(1).needsHit());
        a.add(cd("q:chiedi", Cerca.CHIEDI, "Chiedi", 0));
        p = Cerca.plan(a, -1);
        eq("Al massimo 8 righe in tutto", p.rows(), 8);
        eq("Pieno: Migliore + 3 persone + 3 app", (p.best == null ? 0 : 1) + p.groups.get(Cerca.PERSONA).size() + p.groups.get(Cerca.APP).size(), 7);
        eq("Pieno: gli impegni, le azioni e le impostazioni sono tagliati", p.groups.get(Cerca.IMPEGNO).size() + p.groups.get(Cerca.AZIONE).size() + p.groups.get(Cerca.IMPOSTAZIONE).size(), 0);
        eq("Pieno: Chiedi tiene il suo posto", keys(p.groups.get(Cerca.CHIEDI)).toString(), "[q:chiedi]");
        eq("Pieno: gli impegni nascosti si contano per «Vedi tutti»", p.more[Cerca.IMPEGNO], 4);
        // limiti per gruppo quando c'è posto
        a = new ArrayList<Cerca.Cand>();
        for (int i = 0; i < 4; i++) a.add(cd("ev:" + i, Cerca.IMPEGNO, "E" + i, 15).recent(-i));
        for (int i = 0; i < 4; i++) a.add(cd("s:" + i, Cerca.IMPOSTAZIONE, "S" + i, 10).order(i));
        a.add(cd("q:chiedi", Cerca.CHIEDI, "Chiedi", 0));
        p = Cerca.plan(a, -1);
        eq("Impegni: Migliore il più vicino, poi 2 righe", p.best.key + " " + keys(p.groups.get(Cerca.IMPEGNO)), "ev:0 [ev:1, ev:2]");
        eq("Impostazioni: 2 righe nell'ordine dell'elenco", keys(p.groups.get(Cerca.IMPOSTAZIONE)).toString(), "[s:0, s:1]");
        eq("Impostazioni: 2 nascoste", p.more[Cerca.IMPOSTAZIONE], 2);
        // numero (E014): Chiama è Migliore se non c'è un contatto unico
        a = new ArrayList<Cerca.Cand>();
        a.add(cd("c:1", Cerca.PERSONA, "Giulia", 50));
        a.add(cd("c:2", Cerca.PERSONA, "Paolo", 50));
        a.add(cd("x:chiama", Cerca.AZIONE, "Chiama 346", 100).canBest());
        a.add(cd("x:wa", Cerca.AZIONE, "WhatsApp", 0).order(0));
        a.add(cd("x:addc", Cerca.AZIONE, "Aggiungi ai contatti", 0).order(1));
        p = Cerca.plan(a, -1);
        eq("Numero: due contatti, Migliore è «Chiama»", p.best.key, "x:chiama");
        eq("Numero: poi i contatti", keys(p.groups.get(Cerca.PERSONA)).toString(), "[c:1, c:2]");
        eq("Numero: Azioni WhatsApp e Aggiungi ai contatti", keys(p.groups.get(Cerca.AZIONE)).toString(), "[x:wa, x:addc]");
        eq("Numero: il suggerimento è «Chiama»", Cerca.hint(p.best), "Chiama");
        a.remove(2);
        a.remove(1);
        a.set(0, cd("c:1", Cerca.PERSONA, "Giulia", 100));
        p = Cerca.plan(a, -1);
        eq("Numero: un contatto unico è Migliore", p.best.key, "c:1");
        eq("Numero: il suggerimento del contatto è «Apri»", Cerca.hint(p.best), "Apri");
        check("Senza Migliore niente suggerimento", Cerca.hint(null) == null, null);
        // parte scritta in grassetto
        eq("Grassetto: l'inizio", Arrays.toString(Cerca.match("Mamma", "ma")), "[0, 2]");
        eq("Grassetto: senza accenti sul titolo con accenti", Arrays.toString(Cerca.match("Città del Messico", "citta")), "[0, 5]");
        eq("Grassetto: una parola che inizia così", Arrays.toString(Cerca.match("Trade Republic", "rep")), "[6, 9]");
        eq("Grassetto: contiene", Arrays.toString(Cerca.match("Calcetto mercoledì", "rcol")), "[11, 15]");
        eq("Grassetto: accento nel titolo, scritto senza", Arrays.toString(Cerca.match("Mercoledì", "dì")), "[7, 9]");
        check("Grassetto: niente in comune", Cerca.match("Maps", "zz") == null && Cerca.match("Maps", "") == null, null);
        // scorrimento: si azzera solo con testo vuoto o prima lettera diversa
        check("Scorrimento: una lettera in più resta", !Cerca.resetsScroll("ma", "mar"), null);
        check("Scorrimento: una lettera in meno resta", !Cerca.resetsScroll("mar", "ma"), null);
        check("Scorrimento: la prima lettera cambia, si azzera", Cerca.resetsScroll("ma", "pa"), null);
        check("Scorrimento: testo vuoto, si azzera", Cerca.resetsScroll("ma", ""), null);
        check("Scorrimento: da vuoto a scritto, si azzera", Cerca.resetsScroll("", "m"), null);
        // campo vuoto: sezioni senza doppioni, A portata con persone prima
        List<Cerca.Cand> open = new ArrayList<Cerca.Cand>(), rec = new ArrayList<Cerca.Cand>(), port = new ArrayList<Cerca.Cand>();
        open.add(cd("p:ilde", Cerca.PERSONA, "Ilde", 0));
        rec.add(cd("p:ilde", Cerca.PERSONA, "Ilde", 0));
        rec.add(cd("a:claude", Cerca.APP, "Claude", 0));
        rec.add(cd("p:marta", Cerca.PERSONA, "Marta", 0));
        rec.add(cd("a:mail", Cerca.APP, "Mail", 0));
        port.add(cd("a:maps", Cerca.APP, "Maps", 0));
        port.add(cd("p:fam", Cerca.PERSONA, "Famiglia", 0));
        port.add(cd("a:claude", Cerca.APP, "Claude", 0));
        for (int i = 0; i < 6; i++) port.add(cd("a:x" + i, Cerca.APP, "X" + i, 0));
        java.util.Set<String> used = new java.util.HashSet<String>();
        Cerca.Piece po = Cerca.take(open, used, Cerca.VUOTA_APERTE), pr = Cerca.take(rec, used, Cerca.VUOTA_RECENTI), pp = Cerca.take(Cerca.peopleFirst(port), used, Cerca.VUOTA_PORTATA);
        eq("Aperte ora: max 2", Cerca.VUOTA_APERTE, 2);
        eq("Recenti: niente doppioni con Aperte ora, max 2", keys(pr.shown).toString(), "[a:claude, p:marta]");
        check("Recenti: una voce in più, «more»", pr.more, null);
        eq("A portata: max 5, persone prima, senza doppioni", keys(pp.shown).toString(), "[p:fam, a:maps, a:x0, a:x1, a:x2]");
        check("A portata: «Vedi tutti» perché ce n'è altro", pp.more, null);
        // impostazioni come risultati
        eq("Impostazioni come risultati: «permessi»", Cerca.SETTINGS[Cerca.settingCands("permessi").get(0).order][2], "area:6");
        check("Impostazioni come risultati: una lettera non basta", Cerca.settingCands("p").isEmpty(), null);
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
        // 0.18 (Evolutive 009 e 010)
        check("Cerca «contatti» trova Contatti e gruppi", !Cerca.settings("contatti", 3).isEmpty() && "Contatti e gruppi".equals(Cerca.settings("contatti", 3).get(0)[0]), null);
        String sbl = null; for (String[] e : Cerca.SETTINGS) if ("Sblocchi senza scopo".equals(e[0])) sbl = e[2];
        eq("Sblocchi senza scopo ora sta in Meno doomscrolling", sbl, "area:3");
        boolean oldName = false; for (String[] e : Cerca.SETTINGS) if ("Persone e gruppi".equals(e[0])) oldName = true;
        check("Nessuna voce si chiama più «Persone e gruppi»", !oldName, null);
        boolean noDay = true; for (String[] e : Cerca.SETTINGS) if ("area:1".equals(e[2])) noDay = false;
        check("E011: nessuna voce porta più a La tua giornata", noDay, null);
        boolean noPerms = true; for (String[] e : Cerca.SETTINGS) if ("area:5".equals(e[2])) noPerms = false;
        check("Nessuna voce porta più alla pagina Permessi", noPerms, null);
        eq("«permessi» porta a Da completare", Cerca.settings("permessi", 3).get(0)[2], "area:6");
        eq("E011: «giornata» porta a La tua home", Cerca.settings("giornata", 3).get(0)[2], "area:0");
        String ses = null; for (String[] e : Cerca.SETTINGS) if ("session".equals(e[2])) ses = e[0];
        eq("E012: la voce si chiama Tempo nelle app che rapiscono", ses, "Tempo nelle app che rapiscono");
        check("Ma una corrispondenza migliore vince sulla preferenza", Cerca.rank("Mamma", "mam", false) > Cerca.rank("Tommaso", "mam", true) || Cerca.rank("Tommaso", "mam", true) == 0, null);
        eq("Impostazioni: «pausa» trova la pausa", Cerca.settings("pausa", 3).get(0)[0], "Pausa prima di aprire");
        eq("Impostazioni: «gruppi» trova i gruppi", Cerca.settings("grup", 3).get(0)[0], "Gruppi");
        check("Impostazioni: «banca» trova Servizi e A portata dalle parole chiave (0.14: niente contenitori)", hasSetting(Cerca.settings("banca", 3), "Servizi") && !hasSetting(Cerca.settings("banca", 3), "Contenitori"), null);
        eq("Impostazioni: una lettera sola non basta", Cerca.settings("p", 3).size(), 0);
        List<String> r = new ArrayList<String>();
        for (int i = 1; i <= 10; i++) r = Cerca.pushRecent(r, "app | p" + i + " | App " + i + " | " + (1000 + i));
        eq("Recenti 0.15: al massimo 9, il più nuovo in cima", r.size() + " " + r.get(0), "9 app | p10 | App 10 | 1010");
        eq("Recenti: niente doppioni, anche con un'ora diversa", Cerca.pushRecent(r, "app | p5 | App 5 | 2000").size(), 9);
        long now = 10L * 3600 * 1000;
        check("Recenti: visibile se di meno di 2 ore fa", Cerca.recentVisible(new String[]{"app", "p", "P", String.valueOf(now - 3600 * 1000)}, now), null);
        check("Recenti: non visibile dopo 2 ore", !Cerca.recentVisible(new String[]{"app", "p", "P", String.valueOf(now - 3 * 3600 * 1000)}, now), null);
        check("Recenti: senza ora (prima della 0.15) non visibile", !Cerca.recentVisible(new String[]{"app", "p", "P"}, now), null);
        List<String> nm = java.util.Arrays.asList("Mamma", "Andrea", "Calcetto", "Nido");
        eq("A portata 0.8: ordine scelto, i nuovi in fondo", Cerca.ordered(nm, java.util.Arrays.asList("nido", "Mamma", "Sparito")).toString(), "[Nido, Mamma, Andrea, Calcetto]");
        eq("A portata 0.8: senza ordine resta com'è", Cerca.ordered(nm, new ArrayList<String>()).toString(), "[Mamma, Andrea, Calcetto, Nido]");
        java.util.Map<String, Long> lt = new java.util.HashMap<String, Long>();
        lt.put("Andrea", 500L); lt.put("Nido", 900L); lt.put("Calcetto", 100L);
        java.util.Set<String> lv = new java.util.HashSet<String>(java.util.Arrays.asList("Calcetto"));
        eq("Ricerca 0.15: prima le conversazioni in corso, poi le più recenti, poi l'ordine scelto", Cerca.byLast(nm, lt, lv).toString(), "[Calcetto, Nido, Andrea, Mamma]");
        eq("Ricerca 0.15: senza interazioni resta l'ordine scelto", Cerca.byLast(nm, new java.util.HashMap<String, Long>(), new java.util.HashSet<String>()).toString(), "[Mamma, Andrea, Calcetto, Nido]");
        java.util.Map<Long, String> st = new java.util.LinkedHashMap<Long, String>();
        st.put(1L, "Mamma"); st.put(2L, "Giulia Rossi"); st.put(3L, "Andrea");
        java.util.Set<String> alw = new java.util.HashSet<String>(java.util.Arrays.asList("andrea"));
        eq("Stelle 0.15: la prima volta nessuno viene aggiunto", Rules.newStars(null, st, alw).size(), 0);
        eq("Stelle 0.15: solo le stelle nuove, e non chi passa già sempre", Rules.newStars(new java.util.HashSet<Long>(java.util.Arrays.asList(1L)), st, alw).toString(), "[2]");
        long h = 3600 * 1000L, t0 = 100 * h;
        List<Rules.Conv> cv = Rules.touch(new ArrayList<Rules.Conv>(), "WA", "PM Mama's Living", false, t0, true);
        check("Conversazioni 0.15: aperta dalla notifica ma senza risposta non è in corso", cv.get(0).isOpen(t0 + h) && !cv.get(0).replied(t0 + h), null);
        Rules.wrote(cv, "WA", "PM Mama's Living", false, t0 + h);
        check("Conversazioni 0.15: con una tua risposta è in corso", cv.get(0).replied(t0 + 2 * h), null);
        List<Rules.Conv> cv2 = Rules.touch(new ArrayList<Rules.Conv>(), "WA", "Marta", false, t0, true);
        check("Elenco 0.15: aperta da una notifica, resta 2 ore", cv2.get(0).listed(t0 + h) && !cv2.get(0).listed(t0 + 3 * h), null);
        List<Rules.Conv> cv3 = Rules.touch(new ArrayList<Rules.Conv>(), "WA", "Luca", false, t0, false);
        check("Elenco 0.15: solo un messaggio arrivato, non entra", !cv3.get(0).listed(t0 + 60000), null);
        check("Elenco 0.15: avviata da te o con risposta, resta 12 ore", cv.get(0).listed(t0 + 6 * h), null);
        check("Conversazioni 0.15: dopo 12 ore dalla risposta non più", !cv.get(0).replied(t0 + 14 * h), null);
        eq("Conversazioni 0.15: la risposta si salva e si rilegge", Rules.convs(Rules.convsText(cv, t0 + 2 * h)).get(0).mine, t0 + h);
        eq("Conversazioni 0.15: le righe vecchie (5 campi) si leggono ancora", Rules.convs("WA | Anna | persona | 5 | 6\n").get(0).mine, 0L);
        check("Chiamate 0.15: «1 videochiamate perse» non è una conversazione", Rules.isCallTitle("1 videochiamate perse") && Rules.isCallTitle("Chiamata persa") && Rules.isCallTitle("Missed voice call"), null);
        check("Chiamate 0.15: un nome resta un nome", !Rules.isCallTitle("Filippo") && !Rules.isCallTitle("PM Mama's Living"), null);
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

    static void registro() {
        sec("Registro per la beta (0.15)");
        String log = "";
        log = Registro.add(log, "2026-11-06", Registro.UNLOCKS, 1);
        log = Registro.add(log, "2026-11-06", Registro.UNLOCKS, 1);
        log = Registro.add(log, "2026-11-06", Registro.PAUSE_NOT_NOW, 1);
        eq("Due sblocchi e un «Non ora» nello stesso giorno", log, "2026-11-06;sblocchi=2;pausa_non_ora=1\n");
        log = Registro.add(log, "2026-11-07", Registro.UNLOCKS, 1);
        eq("Un giorno nuovo è una riga nuova", Registro.days(log), 2);
        eq("Chiave sconosciuta: registro invariato", Registro.add(log, "2026-11-07", "app_instagram", 1), log);
        eq("Data non valida: registro invariato", Registro.add(log, "7/11", Registro.UNLOCKS, 1), log);
        eq("Lo stato non si somma con add", Registro.add(log, "2026-11-07", Registro.HOME, 1), log);
        log = Registro.set(log, "2026-11-07", Registro.HOME, "1");
        log = Registro.set(log, "2026-11-07", Registro.HOME, "0");
        check("Lo stato del giorno vale l'ultima volta", log.contains("2026-11-07;home=0;"), log);
        log = Registro.set(log, "2026-11-07", Registro.GOALS, Registro.goals(true, false, true));
        check("Obiettivi come lettere: n e s", log.contains("obiettivi=ns"), log);
        eq("Nessun obiettivo: vuoto", Registro.goals(false, false, false), "");
        check("I valori non possono rompere la riga", !Registro.set(log, "2026-11-07", Registro.GOALS, "a;b=c,d\ne").contains("a;b"), null);
        eq("Si tengono solo i giorni dal più vecchio indicato", Registro.days(Registro.prune(log, "2026-11-07")), 1);
        String ex = Registro.export(log, "M-ABC234", "0.15");
        check("Export: codice partecipante e versione", ex.contains("Partecipante: M-ABC234 · versione 0.15"), null);
        check("Export: intestazione CSV con tutte le colonne", ex.contains("giorno,home,obiettivi,sblocchi,sblocchi_notte,pausa_aperte,pausa_non_ora,scopo_aperte,notifiche_passate,notifiche_trattenute,domande_sblocco\n"), ex);
        check("Export: valore mancante = cella vuota, non zero", ex.contains("2026-11-06,,,2,,,1,,,,\n"), ex);
        eq("Export: una legenda per colonna", Registro.KEYS.length, Registro.LABELS.length);
        java.util.List<String[]> r = Registro.recent(log, 7);
        eq("Schermata: il giorno più recente in alto", r.get(0)[0], "7 nov");
        eq("Schermata: solo i numeri diversi da zero", r.get(1)[1], "2 sblocchi · 1 «Non ora»");
        String code = Registro.code(new int[]{0, 1, 2, 3, 4, 5});
        check("Codice: M- più 6 caratteri, senza 0, O, 1, I, L", code.matches("M-[A-HJKMNP-Z2-9]{6}"), code);
        check("Registro corrotto: si ignora senza errori", Registro.days("ciao\n;;=\n2026-13") == 0, null);
    }

    static void controlli() {
        sec("Controlli a 2 settimane, 1 mese e 3 mesi (0.15)");
        String st = "2026-11-09";
        eq("Controllo 1 a 14 giorni", Controlli.due(st, 1), "2026-11-23");
        eq("Controllo 2 a un mese", Controlli.due(st, 2), "2026-12-09");
        eq("Controllo 3 a tre mesi", Controlli.due(st, 3), "2027-02-09");
        boolean[] none = {false, false, false};
        eq("Prima della data: nessun controllo", Controlli.current(st, "2026-11-22", none, false), 0);
        eq("Il giorno del controllo 1: compare", Controlli.current(st, "2026-11-23", none, false), 1);
        eq("Per 7 giorni resta", Controlli.current(st, "2026-11-29", none, false), 1);
        eq("Dopo 7 giorni sparisce", Controlli.current(st, "2026-11-30", none, false), 0);
        eq("Fatto il primo, il secondo arriva alla sua data", Controlli.current(st, "2026-12-09", new boolean[]{true, false, false}, false), 2);
        eq("Saltato il primo, il secondo arriva lo stesso", Controlli.current(st, "2026-12-10", none, false), 2);
        eq("«Non mostrarmeli più»: nessun controllo", Controlli.current(st, "2026-11-23", none, true), 0);
        eq("Dopo il terzo, basta", Controlli.current(st, "2027-03-01", none, false), 0);
        eq("Senza giorno d'inizio: nessun controllo", Controlli.current(null, "2026-11-23", none, false), 0);
        String log = "";
        for (int d = 0; d < 14; d++) {
            String day = Controlli.plus(st, d);
            for (int k = 0; k < 30; k++) log = Registro.add(log, day, Registro.NOTIF_WAIT, 1);
            if (d % 2 == 0) log = Registro.add(log, day, Registro.PAUSE_NOT_NOW, 3);
            log = Registro.add(log, day, Registro.NIGHT_UNLOCKS, d < 7 ? 3 : 1);
        }
        java.util.List<String[]> n1 = Controlli.numbers(log, st, 1, true, true, true);
        eq("Controllo 1: un numero per obiettivo acceso", n1.size(), 3);
        eq("Controllo 1: totale delle notifiche che hanno aspettato", n1.get(0)[1], "420");
        eq("Controllo 1: volte «Non ora»", n1.get(1)[1], "21 volte");
        eq("Controllo 1: sblocchi di notte in media", n1.get(2)[1], "2 sblocchi");
        check("Controllo 1: nessun confronto", n1.get(0)[3] == null && n1.get(2)[3] == null, null);
        eq("Solo gli obiettivi accesi", Controlli.numbers(log, st, 1, false, true, false).size(), 1);
        check("Solo la base: nessun numero", Controlli.numbers(log, st, 1, false, false, false).isEmpty(), null);
        for (int d = 14; d < 30; d++) log = Registro.add(log, Controlli.plus(st, d), Registro.NIGHT_UNLOCKS, 1);
        java.util.List<String[]> n2 = Controlli.numbers(log, st, 2, false, false, true);
        eq("Controllo 2: media delle ultime 2 settimane", n2.get(0)[1], "1 sblocco");
        eq("Controllo 2: confronto con la prima settimana", n2.get(0)[3], "3 a notte");
        eq("Numeri piccoli con un decimale", Controlli.num(0.4), "0,4");
        eq("Data in italiano", Controlli.label("2026-12-09"), "9 dicembre");
    }

    static void ordine() {
        sec("Task in home, scopo scritto a mano (0.15)");
        long day = 1000L * 86400 * 100, unlock = day + 3600_000L;
        check("Completato prima dello sblocco: sparisce dalla home", !Ordine.doneVisibleInHome(day + 1000, unlock, day, 1), null);
        check("Completato dopo l'ultimo sblocco: ancora visibile", Ordine.doneVisibleInHome(unlock + 1000, unlock, day, 1), null);
        check("Da 3 completati: tutti nell'elenco, non spariscono", Ordine.doneVisibleInHome(day + 1000, unlock, day, 3), null);
        check("Completato ieri: mai in home", !Ordine.doneVisibleInHome(day - 1000, 0, day, 5), null);
        eq("Elenco: titolo", Ordine.accordionTitle(3), "3 task completati");
        java.util.List<String> ids = java.util.Arrays.asList("a", "b", "c");
        eq("Sposta su", Ordine.move(ids, 2, -1).toString(), "[a, c, b]");
        eq("Sposta giù", Ordine.move(ids, 0, 1).toString(), "[b, a, c]");
        eq("Il primo non sale", Ordine.move(ids, 0, -1).toString(), "[a, b, c]");
        java.util.List<String> past = java.util.Arrays.asList("Messaggio a Giulia", "Rispondere a un messaggio", "messaggio a giulia", "Mappa del parco");
        eq("Scopo: con una lettera nessun suggerimento", Scopo.suggest(past, "m", Scopo.DAY_PURPOSES).size(), 0);
        eq("Scopo: solo le parole della persona, senza doppioni", Scopo.suggest(past, "mes", Scopo.DAY_PURPOSES).toString(), "[Messaggio a Giulia]");
        eq("Scopo: al massimo 3", Scopo.suggest(java.util.Arrays.asList("ab1", "ab2", "ab3", "ab4"), "ab", new String[0]).size(), 3);
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

    static void urgenti() {
        sec("E001 · Task urgenti");
        long D = Urgenti.DAY, t0 = 100 * D;
        eq("Urgente oggi in cima", Urgenti.group(t0, true, t0), Urgenti.URG_TODAY);
        eq("Oggi, non urgente", Urgenti.group(t0, false, t0), Urgenti.TODAY);
        eq("Urgente giovedì: urgenti della settimana", Urgenti.group(t0 + 2 * D, true, t0), Urgenti.URG_WEEK);
        eq("Non urgente giovedì: resta in Settimana", Urgenti.group(t0 + 2 * D, false, t0), Urgenti.LATER);
        eq("Urgente tra 8 giorni: fuori dalla settimana", Urgenti.group(t0 + 8 * D, true, t0), Urgenti.LATER);
        eq("Scaduto, anche se urgente", Urgenti.group(t0 - D, true, t0), Urgenti.OVERDUE);
        eq("Senza scadenza", Urgenti.group(0, false, t0), Urgenti.UNDATED);
        check("Ordine: urgenti di oggi < oggi < urgenti settimana < scaduti < senza scadenza",
                Urgenti.URG_TODAY < Urgenti.TODAY && Urgenti.TODAY < Urgenti.URG_WEEK && Urgenti.URG_WEEK < Urgenti.OVERDUE && Urgenti.OVERDUE < Urgenti.UNDATED, null);
        eq("Etichetta «entro gio»", Urgenti.label(Urgenti.URG_WEEK, "gio"), "entro gio");
        eq("Etichetta «Scaduto»", Urgenti.label(Urgenti.OVERDUE, "lun"), "Scaduto");
        eq("Etichetta «senza scadenza»", Urgenti.label(Urgenti.UNDATED, ""), "senza scadenza");
        eq("Niente «oggi»", Urgenti.label(Urgenti.TODAY, "mer"), null);
        eq("0.17: titoletto «entro gio»", Urgenti.heading(Urgenti.URG_WEEK, "gio"), "entro gio");
        eq("0.17: titoletto «scaduti»", Urgenti.heading(Urgenti.OVERDUE, "lun"), "scaduti");
        eq("0.17: titoletto «senza scadenza»", Urgenti.heading(Urgenti.UNDATED, ""), "senza scadenza");
        eq("0.17: nessun titoletto per oggi", Urgenti.heading(Urgenti.TODAY, "mer"), null);
        eq("Urgente senza data diventa oggi", Urgenti.dueWhenUrgent(0, t0), t0);
        eq("Urgente con data la tiene", Urgenti.dueWhenUrgent(t0 + D, t0), t0 + D);
        eq("«! pagare la mensa»", Urgenti.bang("! pagare la mensa"), "pagare la mensa");
        eq("«!!pagare»", Urgenti.bang("!!pagare"), "pagare");
        eq("Senza «!» niente", Urgenti.bang("pagare la mensa"), null);
        eq("Solo «!» niente", Urgenti.bang("!"), null);
        eq("3 urgenze: nessuna riga", Urgenti.tooMany(3), null);
        check("4 urgenze: la riga grigia", "4 urgenze oggi: scegline una per iniziare, le altre aspettano.".equals(Urgenti.tooMany(4)), null);
        java.util.Calendar now = java.util.Calendar.getInstance();
        VoiceParser.Cmd c = VoiceParser.parse("task urgente pagare la mensa entro venerdì", new java.util.ArrayList<String>(), now);
        check("Voce: «task urgente … entro venerdì» è un task urgente con un giorno", VoiceParser.TASK_ADD.equals(c.type) && c.urgent && c.day >= 0 && "pagare la mensa".equalsIgnoreCase(c.text), c.toString());
        c = VoiceParser.parse("! pagare la mensa", new java.util.ArrayList<String>(), now);
        check("Scritto: «! pagare la mensa» è un task urgente", VoiceParser.TASK_ADD.equals(c.type) && c.urgent && "pagare la mensa".equalsIgnoreCase(c.text), c.toString());
        c = VoiceParser.parse("task comprare il latte", new java.util.ArrayList<String>(), now);
        check("Un task normale non è urgente", VoiceParser.TASK_ADD.equals(c.type) && !c.urgent, c.toString());
    }

    static void telefono() {
        sec("E014 · Il telefono in home e i numeri nella ricerca");
        check("«333» è una ricerca da numero", Cerca.isPhoneQuery("333"), null);
        check("Cifre, spazi, +, -, (), / sono caratteri da numero", Cerca.isPhoneQuery("+39 (333) 123-45/6"), null);
        check("Una lettera non è un numero", !Cerca.isPhoneQuery("333a"), null);
        check("Un nome non è un numero", !Cerca.isPhoneQuery("mamma"), null);
        check("Solo simboli senza cifre non è un numero", !Cerca.isPhoneQuery("+ -"), null);
        check("Vuoto o null non è un numero", !Cerca.isPhoneQuery("") && !Cerca.isPhoneQuery(null), null);
        eq("Le cifre di «+39 333-123»", Cerca.phoneDigits("+39 333-123"), "39333123");
        eq("Niente cifre per un testo", Cerca.phoneDigits("calcio 5"), null);
        eq("Con 1 cifra la ricerca è normale", Cerca.phoneSearch("3"), null);
        eq("Con 2 cifre la ricerca è normale", Cerca.phoneSearch("3 3"), null);
        eq("Da 3 cifre parte la ricerca da numero", Cerca.phoneSearch("333"), "333");
        eq("Le cifre contano anche con spazi", Cerca.phoneSearch("3 3 3"), "333");
        eq("Tre simboli e due cifre: ancora normale", Cerca.phoneSearch("(33)"), null);
        check("Il contatto 333 987 1122 corrisponde a 333", Cerca.numberMatches("333 987 1122", "333"), null);
        check("+39 333 987 1122 corrisponde a 3339871", Cerca.numberMatches("+39 333 987 1122", "3339871"), null);
        check("Il contatto 347 111 2233 non corrisponde a 333", !Cerca.numberMatches("347 111 2233", "333"), null);
        check("Numero null: nessuna corrispondenza", !Cerca.numberMatches(null, "333"), null);
        eq("WhatsApp con +: solo le cifre", Cerca.waDigits("+39 333 123 4567"), "393331234567");
        eq("WhatsApp con 00: si toglie il prefisso", Cerca.waDigits("0039 333 123 4567"), "393331234567");
        eq("WhatsApp senza prefisso: le cifre come scritte", Cerca.waDigits("333 123 4567"), "3331234567");
        eq("«Nuovo» alle 17:59 propone oggi", Quando.addDayOffset(17), 0);
        eq("«Nuovo» dalle 18 propone domani", Quando.addDayOffset(18), 1);
        eq("«Nuovo» di sera propone domani", Quando.addDayOffset(22), 1);
    }

    static void serale() {
        sec("E002 · Avviso e consiglio della sera");
        eq("26 consigli", Serale.TIPS.length, 26);
        check("Preavviso alle 22:35 per staccare alle 22:45", Serale.prewarn(22 * 60 + 35, 22 * 60 + 45), null);
        check("Niente preavviso alle 22:34", !Serale.prewarn(22 * 60 + 34, 22 * 60 + 45), null);
        check("Niente preavviso all'ora di staccare", !Serale.prewarn(22 * 60 + 45, 22 * 60 + 45), null);
        check("Preavviso a cavallo della mezzanotte (staccare alle 0:05)", Serale.prewarn(23 * 60 + 58, 5), null);
        eq("Mancano 7 minuti", Serale.minutesTo(22 * 60 + 38, 22 * 60 + 45), 7);
        eq("Testo del preavviso", Serale.prewarnText(10), "Modalità sera tra 10 minuti");
        eq("Testo dell'ultimo minuto", Serale.prewarnText(1), "Modalità sera tra un minuto");
        boolean risveglio = false;
        for (String t : Serale.TIPS) { if (t.contains("appena sveglia")) risveglio = false; if (t.contains("al risveglio")) risveglio = true; }
        eq("0.16: consiglio senza genere, al risveglio", risveglio, true);
        eq("Schermo spento: in home allo sblocco", Serale.where(false, false, true, true), Serale.HOME);
        eq("−minus davanti: in home", Serale.where(true, true, false, true), Serale.HOME);
        eq("App frenata: la pillola", Serale.where(true, false, true, true), Serale.PILL);
        eq("App frenata senza permesso di disegnare: la notifica", Serale.where(true, false, true, false), Serale.NOTIF);
        eq("App non frenata: la notifica silenziosa", Serale.where(true, false, false, true), Serale.NOTIF);
        eq("5 gennaio 1970 è la settimana 0", Serale.week(4), 0L);
        eq("Domenica 11 gennaio 1970 è ancora la settimana 0", Serale.week(10), 0L);
        eq("Lunedì 12 gennaio 1970 è la settimana 1", Serale.week(11), 1L);
        eq("Prima settimana: il consiglio 1", Serale.tipIndex(2900, 2900), 0);
        eq("Dopo 26 settimane il giro ricomincia", Serale.tipIndex(2926, 2900), 0);
        eq("Settimana 27: il consiglio 2", Serale.tipIndex(2927, 2900), 1);
    }

    // ---------- Evolutiva 019 · attività in corso sul blocco ----------

    static LiveLogic.Note ln(String pkg, String cat, int flags, String title, String text) {
        LiveLogic.Note n = new LiveLogic.Note();
        n.pkg = pkg; n.ownPkg = "it.essenziale.launcher"; n.category = cat; n.flags = flags; n.title = title; n.text = text; n.when = 1000;
        return n;
    }

    static void attivitaInCorso() {
        sec("Attività in corso sul blocco (E019)");
        final int ON = LiveLogic.FLAG_ONGOING_EVENT, FG = 0x40;
        long now = 10000000L;
        LiveLogic.Note n = ln("com.maps", "navigation", ON, "Arrivo 10:08", "Esci dalla navigazione");
        check("Navigazione in corso: sì", LiveLogic.isLive(n, now), null);
        n = ln("com.x", null, ON | FG, "Servizio", "Sincronizzazione"); check("Servizio in primo piano senza categoria: no", !LiveLogic.isLive(n, now), null);
        n = ln("com.x", "service", ON, "Titolo", "Testo"); check("Categoria servizio: no", !LiveLogic.isLive(n, now), null);
        n = ln("com.x", "promo", ON, "Offerta", "-50%"); check("Promozione: no", !LiveLogic.isLive(n, now), null);
        n = ln("com.x", "recommendation", ON, "Per te", "Un film"); check("Raccomandazione: no", !LiveLogic.isLive(n, now), null);
        n = ln("com.x", "status", ON, "Stato", "ok"); check("Categoria stato: no", !LiveLogic.isLive(n, now), null);
        n = ln("com.x", "sys", ON, "Sistema", "ok"); check("Categoria sistema: no", !LiveLogic.isLive(n, now), null);
        n = ln("com.x", "progress", ON, "Scarico file", "40%"); n.hasProgress = true; check("Avanzamento con barra: sì", LiveLogic.isLive(n, now), null);
        n = ln("com.x", "progress", ON, "Scarico file", "40%"); check("Avanzamento senza barra: no", !LiveLogic.isLive(n, now), null);
        n = ln("com.x", "call", ON, "Mamma", "Chiamata in corso"); check("Chiamata in corso: sì", LiveLogic.isLive(n, now), null);
        n = ln("com.x", "call", 0, "Mamma", "Chiamata in arrivo"); check("Chiamata non in corso (in arrivo): no", !LiveLogic.isLive(n, now), null);
        n = ln("com.clock", "stopwatch", ON, "Cronometro", "00:12"); check("Cronometro: sì", LiveLogic.isLive(n, now), null);
        n = ln("com.clock", "workout", ON, "Corsa", "5 km"); check("Allenamento: sì", LiveLogic.isLive(n, now), null);
        n = ln("com.clock", "alarm", ON, "Timer", "04:10"); n.chrono = true; n.countdown = true; check("Timer (sveglia con conto alla rovescia): sì", LiveLogic.isLive(n, now), null);
        n = ln("com.clock", "alarm", ON, "Sveglia", "07:00"); check("Sveglia: no", !LiveLogic.isLive(n, now), null);
        n = ln("com.clock", "alarm", ON, "Sveglia", "07:00"); n.chrono = true; check("Sveglia con cronometro che sale: no", !LiveLogic.isLive(n, now), null);
        n = ln("com.rider", null, ON, "Il rider è a 8 minuti", "Ordine 4821"); n.promoted = true; check("Live Update promosso (A16): sì, anche senza categoria", LiveLogic.isLive(n, now), null);
        n = ln("com.rider", null, ON, "Il rider è a 8 minuti", "Ordine 4821"); n.progressStyle = true; check("ProgressStyle in corso: sì", LiveLogic.isLive(n, now), null);
        n = ln("com.rider", null, 0, "Titolo", "Testo"); n.progressStyle = true; check("ProgressStyle non in corso: no", !LiveLogic.isLive(n, now), null);
        n = ln("com.rider", "promo", ON, "Titolo", "Testo"); n.promoted = true; check("Promosso ma categoria promo: no", !LiveLogic.isLive(n, now), null);
        n = ln("com.x", "navigation", ON | LiveLogic.FLAG_GROUP_SUMMARY, "Riepilogo", "3 notifiche"); check("Riepilogo di gruppo: no", !LiveLogic.isLive(n, now), null);
        n = ln("it.essenziale.launcher", "navigation", ON, "Nostra", "Nostra"); check("La nostra: no", !LiveLogic.isLive(n, now), null);
        n = ln("android", "progress", ON, "Sistema", "40%"); n.hasProgress = true; check("Pacchetto di sistema: no", !LiveLogic.isLive(n, now), null);
        n = ln("com.vpn", "progress", ON, "VPN attiva", "Connesso"); n.hasProgress = true; check("VPN: no", !LiveLogic.isLive(n, now), null);
        n = ln("com.x", "progress", ON, "App in esecuzione", "Tocca per i dettagli"); n.hasProgress = true; check("«In esecuzione»: no", !LiveLogic.isLive(n, now), null);
        n = ln("com.x", "progress", ON, "Uso della batteria", "Alto"); n.hasProgress = true; check("Batteria: no", !LiveLogic.isLive(n, now), null);
        n = ln("com.x", "navigation", ON, "", ""); check("Senza titolo né testo: no", !LiveLogic.isLive(n, now), null);
        n = ln("com.x", "transport", 0, "Brano", "Artista"); n.media = true; n.playing = true; n.since = now - 1000;
        check("Musica in riproduzione: sì (anche non «in corso»)", LiveLogic.isLive(n, now), null);
        n.playing = false; n.since = now - 4 * 60000L; check("Musica in pausa da 4 minuti: sì", LiveLogic.isLive(n, now), null);
        n.since = now - 5 * 60000L - 1; check("Musica in pausa da più di 5 minuti: no", !LiveLogic.isLive(n, now), null);
        n.since = 0; check("Musica in pausa, da quando non si sa: no", !LiveLogic.isLive(n, now), null);
        n.playing = true; check("Musica che riparte: torna", LiveLogic.isLive(n, now), null);

        List<LiveLogic.Note> all = new ArrayList<LiveLogic.Note>();
        for (int i = 0; i < 4; i++) { LiveLogic.Note x = ln("com.x" + i, "navigation", ON, "Attività " + i, "t"); x.when = 100 + i; x.key = "k" + i; all.add(x); }
        LiveLogic.Note bad = ln("com.bad", "service", ON, "Servizio", "t"); bad.when = 999; all.add(bad);
        List<LiveLogic.Note> pk = LiveLogic.pick(all, now, LiveLogic.MAX);
        eq("Al massimo 2", pk.size(), 2);
        eq("La più recente per prima", pk.get(0).key, "k3");
        eq("Poi la seguente", pk.get(1).key, "k2");
        check("Il servizio, anche se più recente, non entra", !pk.contains(bad), null);
        eq("Nessuna attività: lista vuota", LiveLogic.pick(new ArrayList<LiveLogic.Note>(), now, 2).size(), 0);
        eq("TalkBack: app, titolo, testo", LiveLogic.describe("Consegne", "Il rider è a 8 minuti", "Ordine 4821"), "Consegne. Il rider è a 8 minuti. Ordine 4821");
        eq("TalkBack: senza testo", LiveLogic.describe("Musica", "Brano", null), "Musica. Brano");
    }

    static void movimento() {
        sec("E016 · Movimento e feedback (parte pura di Motion)");
        eq("Rapido 180 ms", MotionLogic.RAPIDO, 180);
        eq("Base 400 ms", MotionLogic.BASE, 400);
        eq("Lento 720 ms", MotionLogic.LENTO, 720);
        eq("Ridotto: dissolvenza di 80 ms", MotionLogic.RIDOTTO, 80);
        check("Scala degli animatori 0 = «Rimuovi animazioni»", MotionLogic.reducedFromScale(0f), null);
        check("Scala 1 = animazioni normali", !MotionLogic.reducedFromScale(1f), null);
        check("Scala 0,5 = animazioni normali, più veloci", !MotionLogic.reducedFromScale(0.5f), null);
        check("Scala 10 = animazioni normali", !MotionLogic.reducedFromScale(10f), null);
        eq("Ridotto: ogni durata diventa 80 ms (Rapido)", MotionLogic.dur(true, MotionLogic.RAPIDO), 80);
        eq("Ridotto: ogni durata diventa 80 ms (Lento)", MotionLogic.dur(true, MotionLogic.LENTO), 80);
        eq("Normale: la durata resta quella del token", MotionLogic.dur(false, MotionLogic.BASE), 400);
        check("Ridotto: niente traslazioni né scale", !MotionLogic.allowsTravel(true), null);
        check("Normale: le traslazioni sono permesse", MotionLogic.allowsTravel(false), null);
        eq("La riga spuntata resta 1 secondo", MotionLogic.SPUNTA_RESTA, 1000);
        eq("La scheda dello sblocco si chiude dopo 300 ms", MotionLogic.SBLOCCO_ATTESA, 300);
        check("Rapporto: sceso rispetto alla settimana scorsa, il numero scende", MotionLogic.countDown(14, 9), null);
        check("Rapporto: salito, il numero c'è già", !MotionLogic.countDown(9, 14), null);
        check("Rapporto: uguale, il numero c'è già", !MotionLogic.countDown(9, 9), null);
        check("Rapporto: settimana scorsa non salvata, niente conto", !MotionLogic.countDown(-1, 9), null);
        check("Rapporto: sceso fino a zero, il conto parte", MotionLogic.countDown(3, 0), null);
        eq("Il conto parte dal valore di prima", MotionLogic.countValue(14, 9, 0f), 14);
        eq("Il conto finisce sul valore nuovo", MotionLogic.countValue(14, 9, 1f), 9);
        eq("Il conto è ease-in-out: a metà tempo è a metà strada", MotionLogic.countValue(14, 9, 0.5f), 12);
        eq("Il conto parte piano: a un quarto del tempo è sceso di meno di un quarto", MotionLogic.countValue(14, 9, 0.25f), 13);
        eq("Il conto arriva piano: a tre quarti del tempo manca poco", MotionLogic.countValue(14, 9, 0.75f), 10);
        eq("Il conto dura 900 ms", MotionLogic.CONTO_MS, 900);
        eq("Il conto non esce dai limiti (t oltre 1)", MotionLogic.countValue(14, 9, 2f), 9);
        eq("Il conto non esce dai limiti (t sotto 0)", MotionLogic.countValue(14, 9, -1f), 14);
        boolean mono = true;
        int prev = 14;
        for (int k = 0; k <= 20; k++) { int v = MotionLogic.countValue(14, 9, k / 20f); if (v > prev) mono = false; prev = v; }
        check("Il conto non risale mai", mono, null);
        eq("Le righe del rapporto entrano a 80 ms l'una (terza)", MotionLogic.rowDelay(false, 2), 160);
        eq("Ridotto: le righe entrano insieme", MotionLogic.rowDelay(true, 2), 0);
        eq("Anello all'inizio della pausa", MotionLogic.ring(0, 8), 0f);
        eq("Anello a metà della pausa", MotionLogic.ring(4, 8), 0.5f);
        eq("Anello alla fine della pausa", MotionLogic.ring(8, 8), 1f);
        eq("Anello oltre la fine resta pieno", MotionLogic.ring(9, 8), 1f);
        eq("Anello con pausa di 4 secondi", MotionLogic.ring(1, 4), 0.25f);
        eq("Anello senza durata: pieno", MotionLogic.ring(3, 0), 1f);
        eq("Festa: l'ultimo task di oggi è «Tutto fatto»", MotionLogic.festa(false, 0, 0), MotionLogic.FESTA_TUTTO);
        eq("Festa: l'ultimo task, urgente, è «Tutto fatto» (non «Urgenti fatti»)", MotionLogic.festa(true, 0, 0), MotionLogic.FESTA_TUTTO);
        eq("Festa: l'ultimo urgente con altri task da fare è «Urgenti fatti»", MotionLogic.festa(true, 0, 2), MotionLogic.FESTA_URGENTI);
        eq("Festa: un urgente con un altro urgente da fare, niente", MotionLogic.festa(true, 1, 3), MotionLogic.FESTA_NESSUNA);
        eq("Festa: un task non urgente con altri da fare, niente", MotionLogic.festa(false, 0, 1), MotionLogic.FESTA_NESSUNA);
        eq("Festa: un task non urgente non fa mai «Urgenti fatti»", MotionLogic.festa(false, 0, 4), MotionLogic.FESTA_NESSUNA);
        eq("Festa: il testo di TalkBack, tutto", MotionLogic.festaTesto(MotionLogic.FESTA_TUTTO), "Tutto fatto per oggi");
        eq("Festa: il testo di TalkBack, urgenti", MotionLogic.festaTesto(MotionLogic.FESTA_URGENTI), "Urgenti fatti");
        check("Festa: nessuna festa, nessun testo", MotionLogic.festaTesto(MotionLogic.FESTA_NESSUNA) == null, null);
        eq("Festa: TalkBack parla 700 ms dopo l'inizio", MotionLogic.FESTA_ANNUNCIO_MS, 700);
        eq("Festa: dura 2,6 LENTO + 900 ms (2772)", MotionLogic.festaTotale(false), 2772);
        eq("Festa, ridotta: 1600 ms", MotionLogic.festaTotale(true), 1600);
        eq("Festa: il check comincia a 0,6 LENTO (432 ms)", MotionLogic.festaDisegnoDa(), 432);
        eq("Festa: a 0 ms il segno non c'è", MotionLogic.festaOpacita(0, false), 0f);
        eq("Festa: a LENTO il segno è pieno", MotionLogic.festaOpacita(720, false), 1f);
        eq("Festa: resta pieno a metà", MotionLogic.festaOpacita(1400, false), 1f);
        eq("Festa: alla fine non c'è più", MotionLogic.festaOpacita(2772, false), 0f);
        eq("Festa: a metà dell'entrata è a metà (ease-in-out)", MotionLogic.festaOpacita(360, false), 0.5f);
        eq("Festa: entra da scala 0,94", MotionLogic.festaScala(0, false), 0.94f);
        eq("Festa: a LENTO la scala è 1", MotionLogic.festaScala(720, false), 1f);
        eq("Festa, ridotta: mai scala", MotionLogic.festaScala(0, true), 1f);
        eq("Festa: il check non è disegnato prima di 432 ms", MotionLogic.festaCheck(432, false), 0f);
        eq("Festa: il check è a metà a 792 ms", MotionLogic.festaCheck(792, false), 0.5f);
        eq("Festa: il check è completo a 1152 ms", MotionLogic.festaCheck(1152, false), 1f);
        eq("Festa, ridotta: il check è già tutto", MotionLogic.festaCheck(0, true), 1f);
        eq("Festa, ridotta: sale al 20%", MotionLogic.festaOpacita(320, true), 1f);
        eq("Festa, ridotta: a 1600 ms non c'è più", MotionLogic.festaOpacita(1600, true), 0f);
        eq("Tick: il check comincia dopo mezzo RAPIDO (90 ms)", MotionLogic.tickDisegnoDa(), 90);
        eq("Tick: l'azione parte a 740 ms (90 + 400 + 250)", MotionLogic.tickAttesa(false), 740);
        eq("Tick, ridotto: 330 ms", MotionLogic.tickAttesa(true), 330);
        eq("Tick: prima dei 90 ms il check non c'è", MotionLogic.tickCheck(50, false), 0f);
        eq("Tick: a 290 ms il check è a metà", MotionLogic.tickCheck(290, false), 0.5f);
        eq("Tick: a 490 ms il check è finito", MotionLogic.tickCheck(490, false), 1f);
        eq("Tick, ridotto: il check c'è subito", MotionLogic.tickCheck(0, true), 1f);
        eq("Tick 0.24: il disegno finisce a 490 ms", MotionLogic.tickDisegnoFine(), 490);
        check("Tick 0.24: l'azione non parte prima della fine del disegno", !MotionLogic.tickAzionePuoPartire(489, false) && MotionLogic.tickAzionePuoPartire(490, false), null);
        check("Tick 0.24: l'attesa dell'azione è dopo la fine del disegno", MotionLogic.tickAttesa(false) >= MotionLogic.tickDisegnoFine() && MotionLogic.tickAzionePuoPartire(MotionLogic.tickAttesa(false), false), null);
        eq("Tick 0.24: all'attesa il check è intero", MotionLogic.tickCheck(MotionLogic.tickAttesa(false), false), 1f);
        { boolean mn = true; float pv = 0f; for (int t = 0; t <= MotionLogic.tickAttesa(false); t += 16) { float k = MotionLogic.tickCheck(t, false); if (k < pv) mn = false; pv = k; } check("Tick 0.24: il check cresce sempre, a ogni fotogramma", mn && pv == 1f, null); }
        check("Tick 0.24, ridotto: l'azione può partire subito, il check è intero", MotionLogic.tickAzionePuoPartire(0, true) && MotionLogic.tickCheck(0, true) == 1f, null);
        eq("Alone: 8 dp attorno a pillole e icone tonde", MotionLogic.ALONE_DP, 8f);
        eq("Alone: 6 dp attorno a chip e link", MotionLogic.ALONE_PICCOLO_DP, 6f);
        eq("Alone: sulla casella e le icone senza bordo, 56 dp", MotionLogic.ALONE_ICONA_DP, 56);
        eq("Alone: visibile almeno 240 ms", MotionLogic.ALONE_MIN_MS, 240);
        eq("Alone: tocco veloce (60 ms), resta altri 180 ms", MotionLogic.aloneRestante(60), 180L);
        eq("Alone: tocco lungo (500 ms), svanisce subito", MotionLogic.aloneRestante(500), 0L);
        eq("Alone: tocco di 240 ms esatti, svanisce subito", MotionLogic.aloneRestante(240), 0L);
        eq("Alone: ora al contrario (negativo) non rompe", MotionLogic.aloneRestante(-5), 240L);
        eq("io: 0 è 0", MotionLogic.io(0f), 0f);
        eq("io: 1 è 1", MotionLogic.io(1f), 1f);
        eq("io: metà è metà", MotionLogic.io(0.5f), 0.5f);
        eq("io: un quarto è un ottavo", MotionLogic.io(0.25f), 0.125f);
        eq("io: tre quarti è sette ottavi", MotionLogic.io(0.75f), 0.875f);
        boolean mono2 = true;
        float pv = 0f;
        for (int k = 0; k <= 40; k++) { float v = MotionLogic.io(k / 40f); if (v < pv) mono2 = false; pv = v; }
        check("io non torna mai indietro", mono2, null);
        eq("Colore: a metà tra nero e bianco", MotionLogic.mix(0xFF000000, 0xFFFFFFFF, 0.5f), 0xFF808080);
        eq("Colore: il «!» rosso alla fine non c'è più (trasparente)", MotionLogic.mix(0xFFB3261E, 0x00B3261E, 1f), 0x00B3261E);
        eq("Colore: t=0 è il colore di partenza", MotionLogic.mix(0xFF171716, 0xFFF3F1EB, 0f), 0xFF171716);
        // ---- Evolutiva 016, fase 2 ----
        check("Sera: comincia mentre guardi la home, con animazioni", MotionLogic.seraAnimates(false, true, true, false), null);
        check("Sera: la home si apre già di sera, niente", !MotionLogic.seraAnimates(true, true, true, false), null);
        check("Giorno: la sera finisce mentre guardi la home, con animazioni", MotionLogic.giornoAnimates(true, false, true, false), null);
        check("Giorno: la home non si vede, niente", !MotionLogic.giornoAnimates(true, false, false, false), null);
        check("Giorno: animazioni ridotte, subito", !MotionLogic.giornoAnimates(true, false, true, true), null);
        check("Giorno: la sera comincia, non è il ritorno", !MotionLogic.giornoAnimates(false, true, true, false), null);
        check("Sera: la home non si vede, niente", !MotionLogic.seraAnimates(false, true, false, false), null);
        check("Sera: finisce (torna giorno), niente dissolvenza lenta", !MotionLogic.seraAnimates(true, false, true, false), null);
        check("Sera: animazioni ridotte, subito", !MotionLogic.seraAnimates(false, true, true, true), null);
        eq("Sera: 3 secondi", MotionLogic.SERA_MS, 3000);
        eq("Sera: la luna sale di 6 dp", MotionLogic.SERA_LUNA_DP, 6);
        eq("Sera: la data scorre di 30 dp (luna 22 + spazio 8)", MotionLogic.SERA_DATA_DP, 30);
        eq("Sera: lo spazio della luna è 22 dp", MotionLogic.SERA_LUNA_BOX_DP, 22);
        eq("Settimana scorsa: prova finita e dato presente, si tiene", MotionLogic.prevWeek(true, true, 14), 14);
        eq("Settimana scorsa: zero è un dato", MotionLogic.prevWeek(true, true, 0), 0);
        eq("Settimana scorsa: nessuna prova prima, niente", MotionLogic.prevWeek(false, true, 14), -1);
        eq("Settimana scorsa: rapporto non ancora visto, niente", MotionLogic.prevWeek(true, false, 14), -1);
        eq("Settimana scorsa: contatore assente (-1), niente", MotionLogic.prevWeek(true, true, -1), -1);
        // il ciclo di due rapporti: il primo non ha la settimana scorsa; il secondo conta alla rovescia se è sceso
        int prev1 = -1, week1 = 14, week2 = 9;
        check("Due rapporti: il primo non conta (nessuna settimana scorsa)", !MotionLogic.countDown(prev1, week1), null);
        int prev2 = MotionLogic.prevWeek(true, true, week1);
        check("Due rapporti: il secondo conta alla rovescia da 14 a 9", MotionLogic.countDown(prev2, week2), null);
    }
}
