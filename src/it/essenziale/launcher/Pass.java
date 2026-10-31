package it.essenziale.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

import java.util.List;
import java.util.Locale;

/** 0.6.1: dalla notifica di una chat, scegliere al volo se quella persona o quel gruppo passa sempre. */
public final class Pass {
    private Pass() {}

    /** Il nome è un gruppo già visto nelle notifiche? */
    public static boolean isGroup(Activity a, String title) {
        if (title != null && title.contains(" @ ")) return true;
        String k = Rules.groupName(title).toLowerCase(Locale.ITALY);
        for (String[] r : U.rows(U.prefs(a).getString(U.K_SEEN_GROUPS, ""))) if (Rules.groupName(r[0]).toLowerCase(Locale.ITALY).equals(k)) return true;
        return false;
    }

    public static void sheet(final Activity a, String pkg, String rawTitle, final Runnable refresh) {
        final String code = Rules.chatCode(pkg);
        final boolean group = isGroup(a, rawTitle);
        final String name = group ? Rules.groupName(Rules.groupTitle(rawTitle)) : Rules.groupName(rawTitle);
        final boolean always = group ? Rules.groupAlways(U.groupRules(a).get(name.toLowerCase(Locale.ITALY))) : isAlways(a, name);
        String[] items = group
                ? new String[]{always ? "Passa sempre ✓" : "Fai passare sempre", always ? "Metti in silenzio" : "Lascia in silenzio"}
                : new String[]{always ? "Passa sempre ✓" : "Fai passare sempre", always ? "Fai aspettare in Notifiche" : "Lascia in Notifiche"};
        new AlertDialog.Builder(a)
                .setTitle(name + (group ? " · gruppo" : ""))
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        final boolean on = w == 0;
                        if (on == always) return;
                        Changes.track(a, name + (on ? ": passa sempre" : group ? ": in silenzio" : ": aspetta in Notifiche"),
                                new String[]{group ? U.K_GROUPS : U.K_CONTACTS}, new Runnable() {
                                    public void run() {
                                        if (group) U.setGroupRule(a, name, on ? "sempre" : "silenzio");
                                        else setAlways(a, name, code, on);
                                    }
                                }, refresh);
                    }
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    static boolean isAlways(Activity a, String name) {
        for (Rules.Person p : Rules.people(U.cfg(a, U.K_CONTACTS, U.DEF_CONTACTS)))
            if (p.section.equalsIgnoreCase("Sempre") && p.name.equalsIgnoreCase(name)) return true;
        return false;
    }

    static void setAlways(Activity a, String name, String code, boolean on) {
        List<Rules.Person> people = Rules.people(U.cfg(a, U.K_CONTACTS, U.DEF_CONTACTS));
        Rules.Person p = null;
        for (Rules.Person x : people) if (x.name.equalsIgnoreCase(name)) p = x;
        if (p == null) {
            if (!on) return;
            p = new Rules.Person();
            p.name = name.replace("|", "/");
            p.apps.addAll(U.chatAppsOf(a, name, code));
            people.add(0, p);
        } else if (code != null && !p.apps.contains(code)) p.apps.add(code);
        p.section = on ? "Sempre" : "Attesa";
        U.prefs(a).edit().putString(U.K_CONTACTS, Rules.peopleText(people)).commit();
    }
}
