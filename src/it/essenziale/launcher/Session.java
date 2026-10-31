package it.essenziale.launcher;

import android.content.Context;

/** Tempo dall'ultimo sblocco (0.5): preferenze e testo per la home. Il conteggio vive in FilterService. */
public final class Session {
    private Session() {}

    static final String K_ON = "session_on", K_MIN = "session_min", K_START = "session_start";
    static final int[] CHOICES = {10, 20, 30};

    public static boolean on(Context c) { return U.prefs(c).getBoolean(K_ON, true); }
    /** Di notte (0.10) la soglia scende a 5 minuti. */
    public static int threshold(Context c) {
        int t = U.prefs(c).getInt(K_MIN, 10);
        return Night.active(c) ? Math.min(t, 5) : t;
    }
    public static long start(Context c) { return U.prefs(c).getLong(K_START, 0); }

    /** "Acceso da 23 min", solo oltre la soglia; altrimenti null. */
    public static String homeLine(Context c) {
        if (!on(c) || !U.listenerEnabled(c)) return null;
        return Rules.sessionLabel(Rules.sessionMinutes(start(c), System.currentTimeMillis(), threshold(c)));
    }

    /** Apre Benessere digitale, se c'è; altrimenti le impostazioni di Android. */
    public static boolean openWellbeing(Context c) {
        String pkg = "com.google.android.apps.wellbeing";
        android.content.Intent i = new android.content.Intent();
        i.setComponent(new android.content.ComponentName(pkg, pkg + ".settings.TopLevelSettingsActivity"));
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        if (U.start(c, i)) return true;
        if (U.launchPkg(c, pkg)) return true;
        android.content.Intent s = new android.content.Intent(android.provider.Settings.ACTION_SETTINGS);
        s.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        U.toast(c, "Cerca «Benessere digitale» nelle impostazioni");
        return U.start(c, s);
    }
}
