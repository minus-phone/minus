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

    // ---------- 0.14 · Da completare (wireframe 197) ----------

    /**
     * Ciò che manca, della base e degli obiettivi accesi: {chiave, nome, obiettivo o null}.
     * Sostituisce «Continua la configurazione»: copre ogni schermata saltata e ogni permesso mancante.
     */
    public static List<String[]> todo(Context c) {
        List<String[]> out = new ArrayList<String[]>();
        if (!isDefaultHome(c)) out.add(new String[]{"home", "−minus come home", null});
        if (!U.hasCalendar(c)) out.add(new String[]{"calendario", "Accesso al calendario", null});
        if (wasSeen(c, "portata_saltata") && !U.prefs(c).contains(SearchActivity.K_PORTATA)) out.add(new String[]{"portata", "App a portata", null});
        boolean notif = Goals.on(c, Goals.NOTIF), doom = Goals.on(c, Goals.DOOM), sleep = Goals.on(c, Goals.SLEEP) && Night.on(c);
        if (notif || sleep) {
            String g = notif ? Goals.NOTIF : Goals.SLEEP;
            if (!U.listenerEnabled(c)) out.add(new String[]{"notifiche", "Accesso alle notifiche", g});
        }
        if (notif) {
            if (!hasContacts(c) || !Stars.canWrite(c)) out.add(new String[]{"contatti", "Accesso ai contatti", Goals.NOTIF});
            if (!policy(c)) out.add(new String[]{"silenzio", "Accesso a Non disturbare", Goals.NOTIF});
        }
        if ((doom && !U.pausedPkgs(c).isEmpty()) || (sleep && !Night.nightApps(c).isEmpty()))
            if (!Pill.usageGranted(c)) out.add(new String[]{"utilizzo", "Accesso ai dati di utilizzo", doom ? Goals.DOOM : Goals.SLEEP});
        if (doom && U.prefs(c).getBoolean(Pill.K_ON, false) && !Pill.canDraw(c)) out.add(new String[]{"sopra", "Mostra sopra altre app", Goals.DOOM});
        return out;
    }

    /** Fa ora la cosa che manca: apre la pagina giusta di Android o di −minus. */
    public static void fix(android.app.Activity a, String key) {
        if ("home".equals(key)) openHomeSettings(a);
        else if ("calendario".equals(key)) askOrInfo(a, new String[]{android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR}, 1);
        else if ("contatti".equals(key)) askOrInfo(a, new String[]{android.Manifest.permission.READ_CONTACTS, android.Manifest.permission.WRITE_CONTACTS}, 2);
        else if ("notifiche".equals(key)) openListenerSettings(a);
        else if ("silenzio".equals(key)) openPolicySettings(a);
        else if ("utilizzo".equals(key)) Pill.openUsageSettings(a);
        else if ("sopra".equals(key)) Pill.openOverlaySettings(a);
        else if ("portata".equals(key)) a.startActivity(new Intent(a, PortataActivity.class).putExtra("back", "Da completare"));
    }

    /** Il dialogo di Android si propone una volta: se è già stato negato, si va alle info dell'app. */
    static void askOrInfo(android.app.Activity a, String[] perms, int code) {
        String k = "asked_" + perms[0];
        boolean before = U.prefs(a).getBoolean(k, false);
        if (before && !a.shouldShowRequestPermissionRationale(perms[0])) { openAppInfo(a); return; }
        U.prefs(a).edit().putBoolean(k, true).apply();
        a.requestPermissions(perms, code);
    }

    /** La riga in home: visibile finché c'è qualcosa e non l'hai chiusa (torna se cambia cosa manca). */
    public static boolean showTodo(Context c) {
        if (!U.prefs(c).getBoolean(K_DONE, false)) return false;
        List<String[]> t = todo(c);
        if (t.isEmpty()) return false;
        return !todoKey(t).equals(U.prefs(c).getString(K_DISMISS + "_todo", ""));
    }

    public static void dismissTodo(Context c) {
        U.prefs(c).edit().putString(K_DISMISS + "_todo", todoKey(todo(c))).apply();
    }

    static String todoKey(List<String[]> t) {
        StringBuilder b = new StringBuilder();
        for (String[] x : t) b.append(x[0]).append(',');
        return b.toString();
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

    public static final int REQ_HOME = 41;

    /**
     * 0.13.5: da Android 10 c'è la finestra «Imposta come app Home» (RoleManager): si sceglie e si torna
     * subito alla configurazione. Prima di Android 10, le impostazioni Home di Android.
     */
    public static void openHomeSettings(android.app.Activity a) {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            try {
                Object rm = a.getSystemService("role");
                Class<?> k = Class.forName("android.app.role.RoleManager");
                boolean avail = (Boolean) k.getMethod("isRoleAvailable", String.class).invoke(rm, "android.app.role.HOME");
                if (avail) {
                    Intent i = (Intent) k.getMethod("createRequestRoleIntent", String.class).invoke(rm, "android.app.role.HOME");
                    a.startActivityForResult(i, REQ_HOME);
                    return;
                }
            } catch (Throwable ignored) {}
        }
        if (!U.start(a, new Intent(Settings.ACTION_HOME_SETTINGS))) U.start(a, new Intent(Settings.ACTION_SETTINGS));
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
