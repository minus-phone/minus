package it.essenziale.launcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Copia fedele della logica di FilterService.classify() e U.rows()/U.alwaysNames() della v0.2
 * (essenziale-0.2-src), resa indipendente da Android. Serve da "oracolo" per il test di non regressione.
 */
final class LegacyV02 {
    static final int IGNORE = -1, DROP = 0, PASS = 1, WAITING = 2, OTHER = 3;

    private static final Pattern URGENT = Pattern.compile(
            "(?i)(codice|code|otp|password monouso|verific|verification|autorizz|authoriz|approv|" +
            "conferma (il |l'|un )?(pagamento|acquisto|accesso|operazione)|3d ?secure|securecode|" +
            "pagamento (riuscito|effettuato|confermato)|sei in arrivo|il tuo autista)");
    private static final String[] DIALERS = {
            "com.google.android.dialer", "com.android.dialer", "com.android.server.telecom", "com.android.phone"};
    private static final String[] ALWAYS_PASS_PKGS = {
            "com.google.android.apps.walletnfcrel", "com.google.android.apps.maps",
            "com.google.android.deskclock", "com.android.deskclock"};

    static List<String[]> rows(String raw) {
        List<String[]> out = new ArrayList<String[]>();
        if (raw == null) return out;
        for (String line : raw.split("\n")) {
            String l = line.trim();
            if (l.length() == 0 || l.startsWith("#")) continue;
            String[] parts = l.split("\\|");
            for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
            out.add(parts);
        }
        return out;
    }

    static List<String> alwaysNames(String contacts) {
        List<String> names = new ArrayList<String>();
        for (String[] r : rows(contacts)) if (r.length >= 2 && r[0].equalsIgnoreCase("Sempre")) names.add(r[1].toLowerCase(Locale.ITALY));
        return names;
    }

    /** category: stringa come in Notification.CATEGORY_*; flags: Notification.flags. */
    static int classify(String ownPkg, String pkg, String cat, int flags, String title0, String text, boolean group,
                        String appLabel, String contacts, String waitingApps, String myName) {
        if (pkg.equals(ownPkg)) return IGNORE;
        if (pkg.equals("android") || pkg.equals("com.android.systemui") || pkg.startsWith("com.android.providers")) return IGNORE;
        if ((flags & (0x2 | 0x40 | 0x20)) != 0) return IGNORE;
        if (cat != null && (cat.equals("call") || cat.equals("alarm") || cat.equals("navigation") || cat.equals("transport")
                || cat.equals("progress") || cat.equals("service") || cat.equals("sys"))) return IGNORE;
        for (String p : ALWAYS_PASS_PKGS) if (p.equals(pkg)) return IGNORE;
        boolean summary = (flags & 0x200) != 0;
        if (summary) return DROP;
        String title = title0 == null ? "" : title0;
        String all = (title + " " + text).toLowerCase(Locale.ITALY);
        if (URGENT.matcher(all).find()) return PASS;
        if (!group) {
            String t = title.toLowerCase(Locale.ITALY);
            for (String name : alwaysNames(contacts)) {
                if (name.length() > 1 && (t.equals(name) || t.startsWith(name + " ") || t.contains(name))) return PASS;
            }
        }
        for (String d : DIALERS) if (d.equals(pkg)) return WAITING;
        String label = appLabel.toLowerCase(Locale.ITALY);
        boolean waitingApp = false;
        for (String[] r : rows(waitingApps)) {
            String w = r[0].toLowerCase(Locale.ITALY);
            if (w.length() > 0 && (label.equals(w) || label.contains(w))) { waitingApp = true; break; }
        }
        if (group) {
            String me = myName.trim().toLowerCase(Locale.ITALY);
            boolean mention = me.length() > 1 && (all.contains("@" + me) || all.contains("@" + me.replace(" ", "")));
            if (all.contains("ti ha menzionat") || all.contains("mentioned you") || all.contains("ha risposto al tuo")) mention = true;
            return (mention && waitingApp) ? WAITING : OTHER;
        }
        return waitingApp ? WAITING : OTHER;
    }
}
