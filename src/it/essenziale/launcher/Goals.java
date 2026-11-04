package it.essenziale.launcher;

import android.content.Context;

/**
 * Gli obiettivi (0.14, wireframe 180): Meno notifiche, Meno doomscrolling, Meno interferenze sul
 * sonno. Ogni obiettivo accende o spegne insieme le sue funzioni; spento, le scelte restano salvate.
 */
public final class Goals {
    private Goals() {}

    static final String NOTIF = "notifiche", DOOM = "doom", SLEEP = "sonno";
    static final String[] ALL = {NOTIF, DOOM, SLEEP};
    static final String[] NAMES = {"Meno notifiche", "Meno doomscrolling", "Meno interferenze sul sonno"};
    static final String[] SUBS = {"Ti raggiunge solo chi conta. Il resto aspetta che sia tu a guardarlo.",
            "Le app che ti trattengono ti chiedono prima per cosa le apri.",
            "La sera il telefono si fa da parte, e di notte ti raggiunge solo chi conta."};

    static String name(String g) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i].equals(g)) return NAMES[i];
        return g;
    }

    /** Chi arriva da prima della 0.14 non ha la chiave: gli obiettivi si ricavano da cosa usava già. */
    static void migrate(Context c) {
        android.content.SharedPreferences p = U.prefs(c);
        if (p.contains(Onboarding.K_GOALS)) return;
        if (!p.getBoolean(Onboarding.K_DONE, false)) return; // primo avvio: decide l'onboarding
        StringBuilder b = new StringBuilder(NOTIF); // il filtro era sempre acceso
        if (!U.pausedPkgs(c).isEmpty() || Pill.on(c)) b.append(",").append(DOOM);
        if (Night.on(c)) b.append(",").append(SLEEP);
        p.edit().putString(Onboarding.K_GOALS, b.toString()).apply();
    }

    static boolean on(Context c, String g) {
        android.content.SharedPreferences p = U.prefs(c);
        if (!p.contains(Onboarding.K_GOALS)) return true; // fino a decisione presa, tutto come prima
        return ("," + p.getString(Onboarding.K_GOALS, "") + ",").contains("," + g + ",");
    }

    static void set(Context c, String g, boolean on) {
        StringBuilder b = new StringBuilder();
        for (String x : ALL) {
            boolean has = x.equals(g) ? on : on(c, x);
            if (has) { if (b.length() > 0) b.append(","); b.append(x); }
        }
        android.content.SharedPreferences.Editor e = U.prefs(c).edit().putString(Onboarding.K_GOALS, b.toString());
        if (SLEEP.equals(g)) e.putBoolean(Night.K_ON, on);
        e.commit();
        if (NOTIF.equals(g) && !on && Onboarding.policy(c)) FilterService.applySilence(c, false);
        if (NOTIF.equals(g) && on && U.prefs(c).getBoolean(U.K_SILENCE, false) && Onboarding.policy(c)) FilterService.applySilence(c, true);
    }

    static int count(Context c) {
        int n = 0;
        for (String g : ALL) if (on(c, g)) n++;
        return n;
    }
}
