package it.essenziale.launcher;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/** Stato dell'onboarding (0.6) e scorciatoie verso le impostazioni di Android. */
public final class Onboarding {
    private Onboarding() {}

    static final String K_DONE = "onb_done", K_PATH = "onb_path", K_SEEN = "onb_seen", K_DISMISS = "onb_dismiss", K_GOALS = "goals",
            K_INTERESTS = "interessi";
    static final String PATH_SHORT = "breve", PATH_ALL = "tutto";

    /**
     * Passi che si possono saltare e ritrovare in «Continua la configurazione». Dalla 0.9 K_PATH
     * contiene quelli presenti nel percorso scelto (es. "silenzio,portata,pausa").
     */
    static final String[] DEFERRED = {"silenzio", "portata", "pausa"};
    static final String[] DEFERRED_NAMES = {"Silenzio", "A portata", "Pausa e tempo"};

    /** Primo avvio vero: chi aggiorna da una versione precedente non rivede l'onboarding. */
    public static boolean firstRun(Context c) {
        android.content.SharedPreferences p = U.prefs(c);
        if (p.contains(K_DONE)) return !p.getBoolean(K_DONE, false);
        String[] old = {U.K_CONTACTS, U.K_SHOP, U.K_MONEY, U.K_GROUPS, U.K_SEEN_GROUPS, U.K_CAL_SEL, U.K_PAUSE, "last_cal", "tasks"};
        for (String k : old) if (p.contains(k)) { p.edit().putBoolean(K_DONE, true).apply(); return false; }
        return true;
    }

    public static void finish(Context c, String path) {
        U.prefs(c).edit().putBoolean(K_DONE, true).putString(K_PATH, path).apply();
    }

    public static void seen(Context c, String step) {
        String s = U.prefs(c).getString(K_SEEN, "");
        if (!("," + s + ",").contains("," + step + ",")) U.prefs(c).edit().putString(K_SEEN, s.length() == 0 ? step : s + "," + step).apply();
    }

    public static boolean wasSeen(Context c, String step) {
        return ("," + U.prefs(c).getString(K_SEEN, "") + ",").contains("," + step + ",");
    }

    /** Nomi dei passi ancora da fare (i rimandati, più il silenzio se manca il permesso). */
    public static List<String> missing(Context c) {
        List<String> out = new ArrayList<String>();
        String path = U.prefs(c).getString(K_PATH, "");
        boolean old = PATH_SHORT.equals(path) || PATH_ALL.equals(path) || path.length() == 0; // fino alla 0.8
        for (int i = 0; i < DEFERRED.length; i++) {
            if (!old && !("," + path + ",").contains("," + DEFERRED[i] + ",")) continue; // non scelto: non manca
            boolean done = wasSeen(c, DEFERRED[i]);
            if ("portata".equals(DEFERRED[i])) done = done || wasSeen(c, "contenitori") || U.prefs(c).contains(SearchActivity.K_PORTATA);
            if ("silenzio".equals(DEFERRED[i])) done = done || policy(c);
            if (!done) out.add(DEFERRED_NAMES[i]);
        }
        return out;
    }

    public static boolean showContinue(Context c) {
        android.content.SharedPreferences p = U.prefs(c);
        String path = p.getString(K_PATH, "");
        // fino alla 0.8 solo il percorso breve rimandava passi; dalla 0.9 tornano quelli saltati
        if (PATH_ALL.equals(path) || path.length() == 0) return false; // vuoto: aggiornata da prima della 0.6
        return p.getBoolean(K_DONE, false) && !p.getBoolean(K_DISMISS, false) && !missing(c).isEmpty();
    }

    public static void dismissContinue(Context c) {
        U.prefs(c).edit().putBoolean(K_DISMISS, true).apply();
    }

    public static Intent continueIntent(Context c) {
        Intent i = new Intent(c, OnboardingActivity.class);
        i.putExtra("continue", true);
        return i;
    }

    // ---------- Stato dei permessi ----------

    public static int alwaysCount(Context c) {
        int n = 0;
        for (Rules.Person p : Rules.people(U.cfg(c, U.K_CONTACTS, U.DEF_CONTACTS))) if (p.section.equalsIgnoreCase("Sempre")) n++;
        return n;
    }

    public static boolean isDefaultHome(Context c) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        android.content.pm.ResolveInfo ri = c.getPackageManager().resolveActivity(i, 0);
        return ri != null && ri.activityInfo != null && c.getPackageName().equals(ri.activityInfo.packageName);
    }

    public static boolean hasContacts(Context c) {
        return c.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean policy(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.isNotificationPolicyAccessGranted();
    }

    public static int missingPermissions(Context c) {
        int n = 0;
        if (!isDefaultHome(c)) n++;
        if (!U.listenerEnabled(c)) n++;
        if (!hasContacts(c)) n++;
        if (!U.canWriteCalendar(c)) n++;
        if (!policy(c)) n++;
        return n;
    }

    public static void openHomeSettings(Context c) {
        if (!U.start(c, new Intent(Settings.ACTION_HOME_SETTINGS))) U.start(c, new Intent(Settings.ACTION_SETTINGS));
    }

    public static void openListenerSettings(Context c) {
        if (!U.start(c, new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))) U.start(c, new Intent(Settings.ACTION_SETTINGS));
    }

    public static void openPolicySettings(Context c) {
        if (!U.start(c, new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))) U.start(c, new Intent(Settings.ACTION_SETTINGS));
    }

    /** Info app di Essenziale: da qui, su Android 13+, si consentono le impostazioni con restrizioni. */
    public static void openAppInfo(Context c) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.getPackageName()));
        if (!U.start(c, i)) U.start(c, new Intent(Settings.ACTION_SETTINGS));
    }
}
