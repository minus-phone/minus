package it.essenziale.launcher;

import android.content.Context;

import java.util.List;
import java.util.Map;

/** Le conversazioni recenti e quelle aperte (0.4), salvate nelle preferenze. La logica è in Rules. */
public final class Convs {
    private Convs() {}

    /** Azione originale dell'ultima notifica di ogni conversazione: apre proprio quella chat. Vale fino al riavvio. */
    private static final Map<String, android.app.PendingIntent> INTENTS = new java.util.HashMap<String, android.app.PendingIntent>();
    private static final Map<String, String> PKGS = new java.util.HashMap<String, String>();

    public static synchronized void remember(String code, String name, String pkg, android.app.PendingIntent pi) {
        String k = Rules.convKey(code, name);
        if (pi != null) INTENTS.put(k, pi);
        if (pkg != null) PKGS.put(k, pkg);
    }

    /** Apre la chat dall'ultima notifica, se Essenziale la conosce ancora. */
    public static boolean openChat(Context c, String code, String name) {
        android.app.PendingIntent pi;
        synchronized (Convs.class) { pi = INTENTS.get(Rules.convKey(code, name)); }
        if (pi == null) return false;
        try { pi.send(); return true; } catch (Exception e) { return false; }
    }

    public static synchronized String pkg(String code, String name) { return PKGS.get(Rules.convKey(code, name)); }

    static final String KEY = "conversations"; // codice | nome | persona/gruppo | ultimo messaggio | aperta fino a

    public static synchronized List<Rules.Conv> load(Context c) {
        return Rules.convs(U.prefs(c).getString(KEY, ""));
    }

    private static void save(Context c, List<Rules.Conv> l) {
        U.prefs(c).edit().putString(KEY, Rules.convsText(l, System.currentTimeMillis())).apply();
    }

    /** È arrivato un messaggio da questa conversazione. */
    public static synchronized void seen(Context c, String code, String name, boolean group, long time) {
        if (code == null || name == null || Rules.groupName(name).length() < 2) return;
        save(c, Rules.touch(load(c), code, name, group, time, false));
    }

    /** Le hai scritto (da Essenziale, dalla notifica o a voce): resta aperta per 12 ore. */
    public static void open(Context c, String code, String name, Boolean group) {
        open(c, code, name, group, System.currentTimeMillis());
    }

    public static synchronized void open(Context c, String code, String name, Boolean group, long time) {
        if (code == null || name == null || Rules.groupName(name).length() < 2) return;
        save(c, Rules.touch(load(c), code, name, group, time, true));
    }

    /** 0.15: l'hai avviata tu da −minus (ricerca, persone, voce): conta come una tua risposta. */
    public static void started(Context c, String code, String name, Boolean group) {
        wrote(c, code, name, group, System.currentTimeMillis());
    }

    /** 0.15: le hai scritto davvero (la tua risposta è nella notifica, o un messaggio a voce). */
    public static synchronized void wrote(Context c, String code, String name, Boolean group, long time) {
        if (code == null || name == null || Rules.groupName(name).length() < 2) return;
        save(c, Rules.wrote(load(c), code, name, group, time));
    }

    public static synchronized void close(Context c, String code, String name) {
        List<Rules.Conv> l = load(c);
        String k = Rules.convKey(code, name);
        for (Rules.Conv x : l) if (x.key().equals(k)) x.until = 0;
        save(c, l);
    }

    /** Toglie la conversazione dalla lista (0.6.1). */
    public static synchronized void forget(Context c, String code, String name) {
        List<Rules.Conv> l = load(c);
        String k = Rules.convKey(code, name);
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).key().equals(k) || (code == null && Rules.groupName(l.get(i).name).equalsIgnoreCase(Rules.groupName(name)))) l.remove(i);
        save(c, l);
    }

    public static Map<String, Long> openMap(Context c) {
        return Rules.openMap(load(c), System.currentTimeMillis());
    }

    /** "fino alle 21:05" oppure "fino a domani 09:40". */
    public static String until(long t) {
        if (t >= U.startOfDay(1) && t < U.startOfDay(2)) return "fino a domani " + U.hhmm(t);
        if (t >= U.startOfDay(2)) return "fino a " + U.dayLabel(t).toLowerCase(java.util.Locale.ITALY) + " " + U.hhmm(t);
        return "fino alle " + U.hhmm(t);
    }
}
