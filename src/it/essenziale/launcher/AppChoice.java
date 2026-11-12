package it.essenziale.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 0.15: dove vanno le notifiche di un'app che non è una chat: passano sempre, restano in Per te, vanno
 * in Altro, oppure come decide −minus. Si sceglie alla prima notifica di un'app nuova, tenendo premuta
 * una notifica, o in Impostazioni › Meno notifiche › Notifiche delle app.
 */
final class AppChoice {
    private AppChoice() {}

    static final String[] CODES = {Rules.APP_ALWAYS, Rules.APP_WAITING, Rules.APP_OTHER, null};
    static final String[] LABELS = {"Passano sempre", "Restano in Per te", "Vanno in Altro", "Come decide −minus"};

    static String label(String code) {
        for (int i = 0; i < CODES.length; i++) if (code == null ? CODES[i] == null : code.equals(CODES[i])) return LABELS[i];
        return LABELS[3];
    }

    static void ask(final Activity a, final String pkg, final String app, final Runnable done) {
        new AlertDialog.Builder(a).setTitle(app).setItems(LABELS, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, final int w) {
                Changes.track(a, app + ": " + LABELS[w].toLowerCase(java.util.Locale.ITALY), new String[]{U.K_APP_CHOICE},
                        new Runnable() { public void run() { U.setAppChoice(a, pkg, CODES[w]); } }, done);
            }
        }).setNegativeButton("Annulla", null).show();
    }

    /** Le app con una scelta, poi «+ Scegli per un'altra app». */
    static void list(final Activity a, final Runnable done) {
        final Map<String, String> m = U.appChoices(a);
        final List<String> pkgs = new ArrayList<String>(m.keySet());
        List<String> rows = new ArrayList<String>();
        for (String p : pkgs) rows.add(U.appLabel(a, p) + " · " + label(m.get(p)).toLowerCase(java.util.Locale.ITALY));
        rows.add("+ Scegli per un'altra app");
        new AlertDialog.Builder(a).setTitle("Notifiche delle app").setItems(rows.toArray(new String[0]), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                if (w < pkgs.size()) { ask(a, pkgs.get(w), U.appLabel(a, pkgs.get(w)), done); return; }
                final List<U.AppInfo> apps = new ArrayList<U.AppInfo>();
                for (U.AppInfo x : U.sortedApps(a)) if (Rules.chatCode(x.pkg) == null && !m.containsKey(x.pkg)) apps.add(x);
                String[] names = new String[apps.size()];
                for (int i = 0; i < names.length; i++) names[i] = apps.get(i).label;
                new AlertDialog.Builder(a).setTitle("Scegli l'app").setItems(names, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d2, int k) { ask(a, apps.get(k).pkg, apps.get(k).label, done); }
                }).setNegativeButton("Annulla", null).show();
            }
        }).setNegativeButton("Chiudi", null).show();
    }
}
