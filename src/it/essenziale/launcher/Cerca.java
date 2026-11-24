package it.essenziale.launcher;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Regole pure della ricerca (0.7), senza Android, verificate dai test. */
public final class Cerca {
    private Cerca() {}

    /** Senza accenti e in minuscolo: «Città» trova «citta». */
    public static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ITALY).trim();
    }

    /** 3 = inizia così, 2 = una parola inizia così, 1 = contiene, 0 = no. */
    public static int score(String label, String q) {
        String l = norm(label), s = norm(q);
        if (s.length() == 0 || l.length() == 0) return 0;
        if (l.startsWith(s)) return 3;
        for (String w : l.split("[\\s\\-_.·]+")) if (w.startsWith(s)) return 2;
        if (l.contains(s)) return 1;
        String compact = s.replace(" ", "");
        return compact.length() > 2 && l.replace(" ", "").contains(compact) ? 1 : 0;
    }

    /** Punteggio con preferenza: ciò che hai scelto (persone che passano sempre, app a portata) sta prima. */
    public static int rank(String label, String q, boolean preferred) {
        int s = score(label, q);
        return s == 0 ? 0 : s * 10 + (preferred ? 5 : 0);
    }

    /** 0.13.8: le ultime 9 cifre di un numero (+39 333 1234567 = 3331234567); null se non è un numero. */
    public static String digits(String s) {
        if (s == null) return null;
        String d = s.replaceAll("[^0-9]", "");
        if (d.length() < 6) return null;
        return d.length() > 9 ? d.substring(d.length() - 9) : d;
    }

    // ---------- 0.19 (Evolutiva 014): numeri di telefono nella ricerca ----------

    /** Vero se la ricerca è fatta solo di caratteri da numero (cifre, spazi, + - ( ) /) e ha almeno una cifra. */
    public static boolean isPhoneQuery(String q) {
        if (q == null) return false;
        boolean digit = false;
        for (int i = 0; i < q.length(); i++) {
            char c = q.charAt(i);
            if (c >= '0' && c <= '9') digit = true;
            else if (" +-()/".indexOf(c) < 0 && !Character.isWhitespace(c)) return false;
        }
        return digit;
    }

    /** Solo le cifre di una ricerca da numero («+39 333-123» = 39333123); null se non è una ricerca da numero. */
    public static String phoneDigits(String q) {
        return isPhoneQuery(q) ? q.replaceAll("[^0-9]", "") : null;
    }

    /** Da 3 cifre la ricerca da numero parte; con 1 o 2 cifre è una ricerca normale. Null se non parte. */
    public static String phoneSearch(String q) {
        String d = phoneDigits(q);
        return d != null && d.length() >= 3 ? d : null;
    }

    /** Il numero di un contatto contiene le cifre cercate (si confrontano solo le cifre). */
    public static boolean numberMatches(String number, String digits) {
        if (number == null || digits == null || digits.length() == 0) return false;
        return number.replaceAll("[^0-9]", "").contains(digits);
    }

    /** Le cifre per wa.me: con + o 00 davanti si toglie il prefisso internazionale (solo cifre); altrimenti le cifre come scritte. */
    public static String waDigits(String q) {
        if (q == null) return "";
        String t = q.trim();
        String d = t.replaceAll("[^0-9]", "");
        if (t.startsWith("00") && d.startsWith("00")) d = d.substring(2);
        return d;
    }

    /** Pagine di Impostazioni cercabili: titolo | parole chiave | destinazione. */
    public static final String[][] SETTINGS = {
            {"Meno notifiche", "notifiche filtro per te altro avvisi chi conta silenzio", "area:2"},
            {"Contatti e gruppi", "persone gruppi conversazioni chat contatti chi passa sempre chi conta whatsapp telegram stella preferiti", "people"},
            {"Gruppi", "gruppi silenzio whatsapp telegram", "groups"},
            {"Servizi", "banca spid io otp codici accesso pagamenti poste identità digitale sms", "servizi"},
            {"Conversazioni aperte", "12 ore aperte risposta", "area:2"},
            {"App che aspettano in Notifiche", "in attesa contate", "waiting"},
            {"Sblocchi senza scopo", "sblocco sblocchi noia annoiavo domanda completa un task una cosa adesso", "area:3"},
            {"La tua home", "home app applicazioni tastiera aspetto calendario giornata", "area:0"},
            {"A portata", "preferite app persone gruppi ordine conversazioni attive ricerca recenti banca", "portata"},
            {"Apri subito la tastiera", "tastiera ricerca", "area:0"},
            {"Meno doomscrolling", "doomscrolling social uso tempo", "area:3"},
            {"Pausa prima di aprire", "pausa attesa social secondi scopo per cosa tempo app che rapiscono", "pause"},
            {"Durata della pausa", "secondi pausa 4 6 8 durata timer", "area:3"},
            {"Tempo nelle app che rapiscono", "tempo dall'ultimo sblocco sessione acceso sblocco minuti instagram tiktok", "session"},
            {"Benessere digitale", "tempo per app wellbeing timer", "wellbeing"},
            {"Pillola del tempo", "pillola sopra le app che rapiscono tempo minuti overlay", "area:3"},
            {"Icone fisse nella barra", "barra di stato icone fisse silenziosa notifiche fisse", "sticky"},
            {"Calendario", "la tua giornata calendari calendario impegni task cose da fare", "area:0"},
            {"Calendari", "calendario impegni google icloud", "calendars"},
            {"Sveglia", "sveglia orologio allarme", "alarms"},
            {"Comandi vocali", "voce microfono parla", "voice"},
            {"Impegni e task nella schermata di blocco", "schermata di blocco lock privacy", "area:0"},
            {"Attività in corso sul blocco", "schermata di blocco lock consegna taxi chiamata timer cronometro musica navigazione live update attività in corso", "area:0"},
            {"Meno interferenze sul sonno", "notte sonno sveglia buio letto insonnia sera staccare dormire", "area:4"},
            {"La tua alternativa", "alternativa invece di scorrere scrollare leggere", "area:-1"},
            {"Permessi che mancano", "permessi silenzio non disturbare home predefinita attivazione concedi", "area:6"},
            {"Da completare", "da completare configurazione mancano", "area:6"},
            {"Modifiche recenti", "annulla ripristina storico", "changes"},
            {"Segnala un problema", "bug problema errore non funziona segnala aiuto", "bug"},
            {"App frenate di notte", "notte app chiuse frenate", "night"},
            {"Aspetto", "tema scuro chiaro testo grande dimensione caratteri colori", "aspetto"},
            {"Cosa vede −minus", "registro notifiche diagnosi", "area:7"},
    };


    /** Le voci di Impostazioni che corrispondono, le migliori prima (al massimo max). */
    public static List<String[]> settings(String q, int max) {
        List<String[]> out = new ArrayList<String[]>();
        List<Integer> sc = new ArrayList<Integer>();
        String s = norm(q);
        if (s.length() < 2) return out;
        for (String[] e : SETTINGS) {
            int v = score(e[0], q) * 10;
            if (v == 0) for (String k : e[1].split(" ")) if (norm(k).startsWith(s)) { v = 5; break; }
            if (v == 0) continue;
            int i = 0;
            while (i < sc.size() && sc.get(i) >= v) i++;
            sc.add(i, v);
            out.add(i, e);
        }
        return out.size() > max ? out.subList(0, max) : out;
    }

    /** Le voci di Impostazioni che corrispondono, come risultati: chiave «s:indice», punteggio del titolo (×10) o della parola chiave (5), ordine dell'elenco. */
    public static List<Cand> settingCands(String q) {
        List<Cand> out = new ArrayList<Cand>();
        String s = norm(q);
        if (s.length() < 2) return out;
        for (int i = 0; i < SETTINGS.length; i++) {
            String[] e = SETTINGS[i];
            int v = score(e[0], q) * 10;
            if (v == 0) for (String k : e[1].split(" ")) if (norm(k).startsWith(s)) { v = 5; break; }
            if (v == 0) continue;
            out.add(new Cand("s:" + i, IMPOSTAZIONE, e[0], v).order(i));
        }
        return out;
    }

    /** Recenti: la voce più nuova in cima, niente doppioni, al massimo 3. */
    /** 0.15: i cercati di recente sono al massimo 9, e si mostrano solo quelli delle ultime 2 ore. */
    public static final int MAX_RECENT = 9;
    public static final long RECENT_MS = 2 * 60 * 60 * 1000L;

    /** «tipo | valore | etichetta | ora»: lo stesso elemento (senza l'ora) non si ripete. */
    public static List<String> pushRecent(List<String> old, String item) {
        List<String> out = new ArrayList<String>();
        out.add(item);
        for (String s : old) if (!recentKey(s).equals(recentKey(item)) && out.size() < MAX_RECENT) out.add(s);
        return out;
    }

    static String recentKey(String s) {
        String[] f = s.split(" \\| ", -1);
        return f.length >= 4 ? f[0] + " | " + f[1] + " | " + f[2] : s;
    }

    /** Il recente è delle ultime 2 ore? Senza ora (prima della 0.15): no. */
    public static boolean recentVisible(String[] r, long now) {
        if (r.length < 4) return false;
        try { long t = Long.parseLong(r[3].trim()); return now - t >= 0 && now - t < RECENT_MS; } catch (NumberFormatException e) { return false; }
    }

    /**
     * 0.15: persone e gruppi in Ricerca: prima le conversazioni in corso, poi per ultima interazione, più recente prima.
     * Chi non senti da tempo (0) resta nell'ordine di partenza, che è quello scelto in A portata.
     */
    public static List<String> byLast(List<String> names, final java.util.Map<String, Long> last, final java.util.Set<String> live) {
        List<String> out = new ArrayList<String>(names);
        java.util.Collections.sort(out, new java.util.Comparator<String>() {
            public int compare(String a, String b) {
                boolean la = live.contains(a), lb = live.contains(b);
                if (la != lb) return la ? -1 : 1;
                Long ta = last.get(a), tb = last.get(b);
                return Long.compare(tb == null ? 0 : tb, ta == null ? 0 : ta);
            }
        });
        return out;
    }

    /** 0.8: ordina i nomi secondo l'ordine scelto; chi non c'è ancora va in fondo, nell'ordine di partenza. */
    public static List<String> ordered(List<String> names, List<String> order) {
        List<String> out = new ArrayList<String>();
        List<String> rest = new ArrayList<String>(names);
        for (String o : order)
            for (int i = 0; i < rest.size(); i++)
                if (norm(rest.get(i)).equals(norm(o))) { out.add(rest.remove(i)); break; }
        out.addAll(rest);
        return out;
    }

    /** 0.8: sposta su di un posto il nome in posizione k. */
    public static List<String> moveUp(List<String> names, int k) {
        List<String> out = new ArrayList<String>(names);
        if (k > 0 && k < out.size()) out.add(k - 1, out.remove(k));
        return out;
    }

    // ---------- 0.24 (Evolutiva 020): una riga sola per ogni risultato, un ordine fisso ----------

    /** I gruppi, nell'ordine fisso in cui compaiono dall'alto (Migliore sta prima, senza titolo). */
    public static final int PERSONA = 0, APP = 1, IMPEGNO = 2, AZIONE = 3, IMPOSTAZIONE = 4, CHIEDI = 5;
    public static final String[] TITOLI = {"Contatti e gruppi", "App", "Impegni", "Azioni", "Impostazioni", "Chiedi"};
    /** Righe al massimo per gruppo (Migliore 1 a parte): Contatti 3, App 3, Impegni 2, Azioni 2, Impostazioni 2, Chiedi 1. */
    public static final int[] LIMITI = {3, 3, 2, 2, 2, 1};
    /** Con zero risultati «Chiedi» ha tre righe: assistente, web, Play Store. */
    public static final int LIMITE_CHIEDI_VUOTO = 3;
    /** In tutto, Migliore e «Chiedi» compresi. */
    public static final int MAX_RIGHE = 8;
    /** Campo vuoto: Aperte ora 2, Recenti 2, Passano sempre 4, A portata (solo app) 5, e 8 dopo «Vedi tutti». */
    public static final int VUOTA_APERTE = 2, VUOTA_RECENTI = 2, VUOTA_SEMPRE = 4, VUOTA_PORTATA = 5, VUOTA_PORTATA_TUTTE = 8;

    /** 0.25: quante app mostra A portata a campo vuoto: 5, o fino a 8 dopo «Vedi tutti ›». */
    public static int portataMax(boolean expanded) { return expanded ? VUOTA_PORTATA_TUTTE : VUOTA_PORTATA; }

    /** Un risultato possibile: chiave stabile (tipo + identificativo), tipo, titolo e quanto corrisponde. */
    public static final class Cand {
        public final String key, label;
        public final int kind, score;
        /** Ultima interazione o ultima apertura (più grande = più recente); per gli impegni, meno inizio. */
        public long recency;
        /** Ordine di partenza (elenco di Impostazioni, ordine delle azioni): più piccolo prima. */
        public int order;
        /** Si mostra solo se c'è almeno un altro risultato (Aggiungi come impegno). */
        public boolean needsHit;
        /** Un'azione che può essere Migliore (solo «Chiama» con un numero scritto). */
        public boolean canBest;

        public Cand(String key, int kind, String label, int score) { this.key = key; this.kind = kind; this.label = label; this.score = score; }
        public Cand recent(long r) { this.recency = r; return this; }
        public Cand order(int o) { this.order = o; return this; }
        public Cand needsHit() { this.needsHit = true; return this; }
        public Cand canBest() { this.canBest = true; return this; }
    }

    /** Chi viene prima: punteggio più alto; a pari punteggio persone, app, impegni, impostazioni; poi il più recente, l'ordine di partenza, l'alfabeto senza accenti. */
    public static int compare(Cand a, Cand b) {
        if (a.score != b.score) return a.score > b.score ? -1 : 1;
        if (a.kind != b.kind) return a.kind < b.kind ? -1 : 1;
        if (a.recency != b.recency) return a.recency > b.recency ? -1 : 1;
        if (a.order != b.order) return a.order < b.order ? -1 : 1;
        int c = norm(a.label).compareTo(norm(b.label));
        return c != 0 ? c : a.key.compareTo(b.key);
    }

    /** Può essere Migliore: persone, app, impegni, impostazioni; le azioni solo se lo dicono («Chiama»); mai «Chiedi». */
    static boolean bestKind(Cand c) {
        return c.kind == PERSONA || c.kind == APP || c.kind == IMPEGNO || c.kind == IMPOSTAZIONE || (c.kind == AZIONE && c.canBest);
    }

    /** Il risultato è davvero qualcosa trovato (non «Chiedi» e non un'azione che dipende dagli altri). */
    static boolean substantive(Cand c) {
        return c.kind == PERSONA || c.kind == APP || c.kind == IMPEGNO || c.kind == IMPOSTAZIONE || (c.kind == AZIONE && !c.needsHit);
    }

    /** Il risultato della scelta: Migliore (preselezionato, tolto dal suo gruppo) e i sei gruppi, già limitati. */
    public static final class Plan {
        public Cand best;
        public final List<List<Cand>> groups = new ArrayList<List<Cand>>();
        /** Quanti risultati di ogni gruppo non si vedono: se > 0 il titolo ha «Vedi tutti ›». */
        public final int[] more = new int[6];
        /** C'è almeno un risultato vero (altrimenti «Chiedi» ha tre righe e niente è preselezionato). */
        public boolean anything;

        public int rows() {
            int n = best == null ? 0 : 1;
            for (List<Cand> g : groups) n += g.size();
            return n;
        }

        /** Le righe che sono risultati (tutte tranne «Chiedi»). */
        public int hits() {
            int n = best == null ? 0 : 1;
            for (int k = 0; k < 5; k++) n += groups.get(k).size();
            return n;
        }
    }

    /**
     * Sceglie e ordina. Migliore: il primo per {@link #compare} fra i tipi che possono esserlo; senza risultati niente.
     * Poi i gruppi nell'ordine fisso, ciascuno con il suo limite; in tutto al massimo 8 righe, «Chiedi» riservata:
     * se i gruppi in alto sono pieni, quelli in basso si accorciano. {@code expanded} (-1 = nessuno) è il gruppo aperto
     * da «Vedi tutti»: mostra fino a 8 righe sue, senza toccare il conto degli altri.
     */
    public static Plan plan(List<Cand> cands, int expanded) {
        Plan p = new Plan();
        boolean any = false;
        for (Cand c : cands) if (substantive(c)) { any = true; break; }
        p.anything = any;
        List<Cand> pool = new ArrayList<Cand>();
        for (Cand c : cands) if (!c.needsHit || any) pool.add(c);
        java.util.Collections.sort(pool, new java.util.Comparator<Cand>() { public int compare(Cand a, Cand b) { return Cerca.compare(a, b); } });
        for (Cand c : pool) if (bestKind(c)) { p.best = c; break; }
        List<List<Cand>> by = new ArrayList<List<Cand>>();
        for (int k = 0; k < 6; k++) by.add(new ArrayList<Cand>());
        for (Cand c : pool) if (c != p.best && c.kind >= 0 && c.kind < 6) by.get(c.kind).add(c);
        // «Chiedi» tiene sempre il suo posto: prima si toglie dal conto
        int askLimit = any ? LIMITI[CHIEDI] : LIMITE_CHIEDI_VUOTO;
        int ask = Math.min(askLimit, by.get(CHIEDI).size());
        int left = MAX_RIGHE - (p.best == null ? 0 : 1) - ask;
        for (int k = 0; k < 5; k++) {
            List<Cand> all = by.get(k);
            int take;
            if (k == expanded) take = Math.min(all.size(), MAX_RIGHE);
            else {
                take = Math.max(0, Math.min(Math.min(LIMITI[k], all.size()), left));
                left -= take;
            }
            p.groups.add(new ArrayList<Cand>(all.subList(0, take)));
            p.more[k] = all.size() - take;
        }
        p.groups.add(new ArrayList<Cand>(by.get(CHIEDI).subList(0, ask)));
        return p;
    }

    /** La scritta a destra della riga preselezionata: «Chiama» per un numero, «Apri» per il resto. */
    public static String hint(Cand best) {
        if (best == null) return null;
        return best.kind == AZIONE ? "Chiama" : "Apri";
    }

    /** L'annuncio per TalkBack, uno solo dopo l'ultima lettera: «Migliore: Mamma. 7 risultati». */
    public static String announcement(String bestLabel, int hits) {
        if (hits <= 0) return "Nessun risultato";
        String n = hits == 1 ? "1 risultato" : hits + " risultati";
        return bestLabel == null ? n : "Migliore: " + bestLabel + ". " + n;
    }

    /** La parte scritta nel titolo, da mettere in grassetto: {inizio, fine} sul titolo com'è (con accenti); null se non c'è. */
    public static int[] match(String label, String q) {
        if (label == null) return null;
        String s = norm(q);
        if (s.length() == 0) return null;
        StringBuilder n = new StringBuilder();
        List<Integer> map = new ArrayList<Integer>();
        for (int i = 0; i < label.length(); i++) {
            String f = Normalizer.normalize(String.valueOf(label.charAt(i)), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ITALY);
            for (int k = 0; k < f.length(); k++) { n.append(f.charAt(k)); map.add(i); }
        }
        String l = n.toString();
        int at = -1;
        if (l.startsWith(s)) at = 0;
        else {
            for (int i = l.indexOf(s); i >= 0; i = l.indexOf(s, i + 1)) {
                if (i > 0 && " -_.·".indexOf(l.charAt(i - 1)) >= 0) { at = i; break; }
            }
            if (at < 0) at = l.indexOf(s);
        }
        if (at < 0) return null;
        int end = at + s.length() - 1;
        return new int[]{map.get(at), map.get(end) + 1};
    }

    /** Campo vuoto: le prime {@code max} voci non ancora viste in un'altra sezione; {@code more} dice se ce n'erano altre. */
    public static final class Piece {
        public final List<Cand> shown = new ArrayList<Cand>();
        public boolean more;
    }

    public static Piece take(List<Cand> in, java.util.Set<String> used, int max) {
        Piece p = new Piece();
        for (Cand c : in) {
            if (used.contains(c.key)) continue;
            if (p.shown.size() >= max) { p.more = true; break; }
            p.shown.add(c);
        }
        for (Cand c : p.shown) used.add(c.key);
        return p;
    }

    /** Persone e gruppi prima, poi le app (ognuno nel suo ordine). */
    public static List<Cand> peopleFirst(List<Cand> in) {
        List<Cand> out = new ArrayList<Cand>();
        for (Cand c : in) if (c.kind == PERSONA) out.add(c);
        for (Cand c : in) if (c.kind != PERSONA) out.add(c);
        return out;
    }

    /** 0.25: solo i candidati di un tipo (A portata a campo vuoto: solo le app; Passano sempre: solo persone e gruppi). */
    public static List<Cand> only(List<Cand> in, int kind) {
        List<Cand> out = new ArrayList<Cand>();
        for (Cand c : in) if (c.kind == kind) out.add(c);
        return out;
    }

    /** Il testo è cambiato abbastanza da azzerare lo scorrimento e chiudere i «Vedi tutti»: vuoto, o prima lettera diversa. */
    public static boolean resetsScroll(String before, String now) {
        String a = norm(before), b = norm(now);
        if (b.length() == 0) return a.length() != 0; // 0.25: da vuoto a vuoto non si azzera nulla (rompeva «Vedi tutti ›» a campo vuoto)
        return a.length() == 0 || a.charAt(0) != b.charAt(0);
    }
}
