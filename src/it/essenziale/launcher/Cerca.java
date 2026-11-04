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

    /** Pagine di Impostazioni cercabili: titolo | parole chiave | destinazione. */
    public static final String[][] SETTINGS = {
            {"Meno notifiche", "notifiche filtro per te altro avvisi chi conta silenzio", "area:2"},
            {"Persone e gruppi", "conversazioni chat contatti chi passa sempre chi conta whatsapp telegram stella preferiti", "people"},
            {"Gruppi", "gruppi silenzio whatsapp telegram", "groups"},
            {"Servizi", "banca spid io otp codici accesso pagamenti poste identità digitale sms", "servizi"},
            {"Conversazioni aperte", "12 ore aperte risposta", "area:2"},
            {"App che aspettano in Notifiche", "in attesa contate", "waiting"},
            {"Sblocchi senza scopo", "sblocco sblocchi noia annoiavo domanda una cosa adesso", "area:2"},
            {"La tua home", "home app applicazioni tastiera", "area:0"},
            {"A portata", "preferite app persone gruppi ordine conversazioni attive ricerca recenti banca", "portata"},
            {"Apri subito la tastiera", "tastiera ricerca", "area:0"},
            {"Meno doomscrolling", "doomscrolling social uso tempo", "area:3"},
            {"Pausa prima di aprire", "pausa attesa social secondi scopo per cosa tempo app che rapiscono", "pause"},
            {"Durata della pausa", "secondi pausa 4 6 8 durata timer", "area:3"},
            {"Tempo dall'ultimo sblocco", "sessione acceso sblocco", "session"},
            {"Benessere digitale", "tempo per app wellbeing timer", "wellbeing"},
            {"Pillola del tempo", "pillola sopra le altre app tempo minuti overlay", "area:3"},
            {"Icone fisse nella barra", "barra di stato icone fisse silenziosa notifiche fisse", "sticky"},
            {"La tua giornata", "giornata cose da fare", "area:1"},
            {"Calendari", "calendario impegni google icloud", "calendars"},
            {"Sveglia", "sveglia orologio allarme", "alarms"},
            {"Comandi vocali", "voce microfono parla", "voice"},
            {"Schermata di blocco", "blocco lock", "area:1"},
            {"Meno interferenze sul sonno", "notte sonno sveglia buio letto insonnia sera staccare dormire", "area:4"},
            {"La tua alternativa", "alternativa invece di scrollare leggere", "area:-1"},
            {"Permessi", "permessi silenzio non disturbare home predefinita attivazione", "area:5"},
            {"Da completare", "da completare configurazione mancano", "area:6"},
            {"Modifiche recenti", "annulla ripristina storico", "changes"},
            {"Segnala un problema", "bug problema errore non funziona segnala aiuto", "bug"},
            {"App frenate di notte", "notte app chiuse frenate", "night"},
            {"Aspetto", "tema scuro chiaro testo grande dimensione caratteri colori", "aspetto"},
            {"Cosa vede −minus", "registro notifiche diagnosi", "area:5"},
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

    /** Recenti: la voce più nuova in cima, niente doppioni, al massimo 3. */
    public static List<String> pushRecent(List<String> old, String item) {
        List<String> out = new ArrayList<String>();
        out.add(item);
        for (String s : old) if (!s.equals(item) && out.size() < 3) out.add(s);
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
}
