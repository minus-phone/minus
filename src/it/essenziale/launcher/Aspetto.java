package it.essenziale.launcher;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Aspetto della home (0.10, wireframe 155–157): anteprima, tema e testo. Usato da Impostazioni e onboarding. */
final class Aspetto {
    private Aspetto() {}

    static final String[][] THEMES = {{U.THEME_LIGHT, "Chiaro"}, {U.THEME_DARK, "Scuro"}, {U.THEME_AUTO, "Automatico"}};

    /** Anteprima della home con i colori attuali. */
    static View preview(Activity a) {
        LinearLayout c = U.col(a);
        c.setPadding(U.dp(a, 14), U.dp(a, 12), U.dp(a, 14), U.dp(a, 12));
        c.setBackground(U.box(a, U.BG, U.LINE, 14));
        LinearLayout h = U.row(a);
        h.addView(U.text(a, "Oggi", 19, U.INK, true));
        TextView d = U.text(a, "  " + new java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.ITALY).format(new java.util.Date()), 12, U.MUTED, false);
        h.addView(d, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        h.addView(U.text(a, "Notifiche ›", 12, U.INK, false));
        c.addView(h, U.full());
        String[][] rows = {{"11:00", "Revisione design"}, {"16:30", "Pediatra"}};
        for (String[] r : rows) {
            LinearLayout l = U.row(a);
            l.setPadding(0, U.dp(a, 4), 0, 0);
            TextView t = U.text(a, r[0] + "  ", 14, U.MUTED, false);
            t.setTypeface(android.graphics.Typeface.MONOSPACE);
            l.addView(t);
            l.addView(U.text(a, r[1], 14, U.INK, false));
            c.addView(l, U.full());
        }
        TextView s = U.text(a, "Cerca persone, app o chiedi", 12, U.MUTED, false);
        s.setMinHeight(U.dp(a, 34));
        s.setGravity(Gravity.CENTER_VERTICAL);
        s.setPadding(U.dp(a, 12), 0, 0, 0);
        s.setBackground(U.box(a, U.BG, U.INK, 17));
        LinearLayout.LayoutParams lp = U.full();
        lp.topMargin = U.dp(a, 16);
        c.addView(s, lp);
        return c;
    }

    /** I due controlli. Ogni scelta si applica subito (la pagina si ridisegna) e ha «Annulla» se track è vero. */
    static void controls(final Activity a, LinearLayout into, final boolean track, final Runnable refresh) {
        into.addView(U.label(a, "Tema"));
        final String cur = U.prefs(a).getString(U.K_THEME, U.THEME_LIGHT);
        LinearLayout seg = seg(a);
        for (final String[] t : THEMES) {
            seg.addView(item(a, t[1], t[0].equals(cur), new Runnable() {
                public void run() { set(a, "Tema: " + t[1].toLowerCase(java.util.Locale.ITALY), U.K_THEME, t[0], null, track, refresh); }
            }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        into.addView(seg, U.full());
        if (U.THEME_AUTO.equals(cur)) into.addView(U.text(a, "Segue Android; con «Di notte» attivo, scuro di notte.", 12, U.MUTED, false));
        into.addView(U.label(a, "Testo"));
        final boolean big = U.prefs(a).getBoolean(U.K_TEXT_BIG, false);
        LinearLayout s2 = seg(a);
        s2.addView(item(a, "Normale", !big, new Runnable() {
            public void run() { set(a, "Testo normale", U.K_TEXT_BIG, null, false, track, refresh); }
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        s2.addView(item(a, "Grande", big, new Runnable() {
            public void run() { set(a, "Testo grande", U.K_TEXT_BIG, null, true, track, refresh); }
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        into.addView(s2, U.full());
    }

    private static void set(final Activity a, String desc, final String key, final String str, final Boolean bool, boolean track, final Runnable refresh) {
        Runnable change = new Runnable() {
            public void run() {
                if (str != null) U.prefs(a).edit().putString(key, str).commit();
                else U.prefs(a).edit().putBoolean(key, bool).commit();
            }
        };
        Runnable after = new Runnable() {
            public void run() {
                U.applyTheme(a);
                if (a instanceof BaseActivity) ((BaseActivity) a).syncTheme();
                if (refresh != null) refresh.run(); else a.recreate();
            }
        };
        // la pagina si ridisegna con il nuovo tema: «Annulla» resta in Impostazioni › Modifiche recenti
        if (track) Changes.record(a, desc, new String[]{key});
        change.run();
        after.run();
    }

    private static LinearLayout seg(Activity a) {
        LinearLayout s = U.row(a);
        s.setBackground(U.box(a, U.SURFACE, U.BORDER, 10));
        s.setPadding(U.dp(a, 1), U.dp(a, 1), U.dp(a, 1), U.dp(a, 1));
        return s;
    }

    private static TextView item(Activity a, String s, boolean sel, final Runnable r) {
        TextView t = U.text(a, s, 13, sel ? U.BG : U.INK, false);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(U.dp(a, 44));
        if (sel) t.setBackground(U.box(a, U.INK, 0, 9));
        t.setContentDescription(s + (sel ? ", scelto" : ""));
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        return t;
    }
}
