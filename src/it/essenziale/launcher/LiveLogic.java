package it.essenziale.launcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Evolutiva 019 · «Attività in corso sul blocco»: quali notifiche sono attività avviate da te (e quindi si vedono sul blocco)
 * e quali no. Logica pura, senza Android, coperta dai test. Il lavoro con Android (leggere le notifiche, disegnarle) è in Live.
 *
 * Sì: Live Update promossi di Android 16 (FLAG_PROMOTED_ONGOING, ProgressStyle), e le notifiche in corso con categoria
 * navigazione, chiamata, avanzamento, cronometro, allenamento, timer (sveglia con conto alla rovescia), musica (MediaStyle).
 * No: servizi in primo piano e di sistema, promozioni, riepiloghi di gruppo, VPN, batteria, «in esecuzione», le nostre.
 */
public final class LiveLogic {
    private LiveLogic() {}

    public static final int FLAG_ONGOING_EVENT = 0x2, FLAG_GROUP_SUMMARY = 0x200, FLAG_PROMOTED_ONGOING = 0x40000;
    public static final int MAX = 2;
    /** La musica in pausa da più di 5 minuti non è più «in corso». */
    public static final long MEDIA_PAUSA_MS = 5 * 60 * 1000L;

    /** La notifica ridotta a ciò che serve per decidere. */
    public static final class Note {
        public String key, pkg, ownPkg, category, title, text;
        public int flags;
        /** Ora della notifica (o dell'ultimo aggiornamento): serve a scegliere la più recente. */
        public long when;
        /** Android 16: FLAG_PROMOTED_ONGOING, o stile ProgressStyle. */
        public boolean promoted, progressStyle;
        /** MediaStyle con sessione; {@code playing}: in riproduzione ora; {@code since}: da quando è nello stato attuale (ms). */
        public boolean media, playing;
        public long since;
        /** Cronometro mostrato e, se sì, a conto alla rovescia (un timer). */
        public boolean chrono, countdown;
        /** Barra di avanzamento: max > 0 o indeterminata. */
        public boolean hasProgress;
    }

    private static final String[] NO_CATEGORIES = {"service", "status", "sys", "recommendation", "promo", "err", "msg", "email", "social"};
    private static final String[] CATEGORIES = {"navigation", "transport", "call", "progress", "stopwatch", "workout"};
    private static final String[] NO_PACKAGES = {"android", "com.android.systemui", "com.android.settings", "com.android.providers.downloads.ui"};

    private static boolean in(String[] a, String s) {
        if (s == null) return false;
        for (String x : a) if (x.equals(s)) return true;
        return false;
    }

    /** Testi di servizi, non di attività: VPN, batteria, «in esecuzione», «running in the background». */
    public static boolean serviceText(String title, String text) {
        String t = ((title == null ? "" : title) + " " + (text == null ? "" : text)).toLowerCase(Locale.ITALY);
        return t.contains("in esecuzione") || t.contains("running in the background") || t.contains("is running") || t.contains("vpn")
                || t.contains("batteria") || t.contains("battery") || t.contains("in background") || t.contains("ottimizzazione");
    }

    /** La musica si mostra se suona, o se è in pausa da meno di 5 minuti. */
    public static boolean mediaVisible(boolean playing, long since, long now) {
        if (playing) return true;
        return since > 0 && now - since <= MEDIA_PAUSA_MS;
    }

    public static boolean isLive(Note n, long now) {
        if (n == null || n.pkg == null) return false;
        if (n.pkg.equals(n.ownPkg)) return false;
        if (in(NO_PACKAGES, n.pkg)) return false;
        if ((n.flags & FLAG_GROUP_SUMMARY) != 0) return false;
        boolean noTitle = n.title == null || n.title.trim().length() == 0, noText = n.text == null || n.text.trim().length() == 0;
        if (noTitle && noText) return false;
        if (in(NO_CATEGORIES, n.category)) return false;
        if (n.media) return mediaVisible(n.playing, n.since, now);
        boolean ongoing = (n.flags & FLAG_ONGOING_EVENT) != 0;
        boolean call = "call".equals(n.category);
        if (!call && serviceText(n.title, n.text)) return false;
        // Android 16: un Live Update promosso, o uno stile a tappe, è già un'attività avviata da te
        if (n.promoted || (n.progressStyle && ongoing)) return ongoing || n.promoted;
        if (!ongoing) return false;
        if (in(CATEGORIES, n.category)) return !"progress".equals(n.category) || n.hasProgress;
        if ("alarm".equals(n.category)) return n.chrono && n.countdown; // solo i timer, non le sveglie
        return false;
    }

    /** Le attività da mostrare: al massimo {@code max}, le più recenti prima. */
    public static List<Note> pick(List<Note> all, long now, int max) {
        List<Note> out = new ArrayList<Note>();
        if (all == null) return out;
        for (Note n : all) if (isLive(n, now)) out.add(n);
        Collections.sort(out, new Comparator<Note>() {
            public int compare(Note a, Note b) {
                if (a.when != b.when) return a.when > b.when ? -1 : 1;
                return a.promoted == b.promoted ? 0 : a.promoted ? -1 : 1;
            }
        });
        return out.size() > max ? new ArrayList<Note>(out.subList(0, max)) : out;
    }

    /** Il testo che dice TalkBack sulla scheda: app, titolo, testo. */
    public static String describe(String app, String title, String text) {
        StringBuilder b = new StringBuilder();
        for (String s : new String[]{app, title, text}) {
            if (s == null || s.trim().length() == 0) continue;
            if (b.length() > 0) b.append(". ");
            b.append(s.trim());
        }
        return b.toString();
    }
}
