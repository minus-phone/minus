package it.essenziale.launcher;

import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * «Una cosa che puoi fare adesso» (0.12, wireframe 172 e 173b): la prima attività di oggi non fatta,
 * con «Fatto» e «Più tardi», e accanto l'alternativa scelta per «Di notte».
 * Le regole stanno in {@link Cosa}; qui solo la scheda e la memoria della giornata.
 */
final class Adesso {
    private Adesso() {}

    static final String K_DAY = "cosa_day", K_LATER = "cosa_later", K_SKIPPED = "cosa_skipped";

    private static void roll(Context c) {
        long today = U.startOfDay(0);
        if (U.prefs(c).getLong(K_DAY, 0) != today)
            U.prefs(c).edit().putLong(K_DAY, today).putInt(K_LATER, 0).putString(K_SKIPPED, "").apply();
    }

    static int later(Context c) { roll(c); return U.prefs(c).getInt(K_LATER, 0); }

    private static List<String> skipped(Context c) {
        roll(c);
        String s = U.prefs(c).getString(K_SKIPPED, "");
        return s.length() == 0 ? new ArrayList<String>() : new ArrayList<String>(Arrays.asList(s.split(",")));
    }

    /** L'attività da proporre adesso, o null (niente da fare oggi, o due «Più tardi» di fila). */
    static Tasks.Task pick(Context c) {
        List<Tasks.Task> ts = Tasks.forToday(c, false, false);
        List<String> ids = new ArrayList<String>();
        for (Tasks.Task t : ts) ids.add(t.id);
        int i = Cosa.pick(ids, skipped(c), later(c));
        return i < 0 ? null : ts.get(i);
    }

    static void later(Context c, Tasks.Task t) {
        List<String> s = skipped(c);
        if (!s.contains(t.id)) s.add(t.id);
        StringBuilder b = new StringBuilder();
        for (String x : s) { if (b.length() > 0) b.append(','); b.append(x); }
        U.prefs(c).edit().putInt(K_LATER, later(c) + 1).putString(K_SKIPPED, b.toString()).apply();
    }

    static void done(Context c, Tasks.Task t) {
        Tasks.setDone(c, t.id, true);
        U.prefs(c).edit().putInt(K_LATER, 0).apply(); // non più «di fila»
    }

    /**
     * La scheda. dark: nella pausa (sempre scura). onAlt: tocco sull'alternativa. onChange: dopo «Fatto»
     * o «Più tardi», per ridisegnare. Restituisce null se oggi non c'è niente da proporre e l'alternativa
     * non basta (due «Più tardi» di fila).
     */
    static View card(final Context c, final boolean dark, final Runnable onAlt, final Runnable onChange) {
        if (Cosa.offToday(later(c))) return null;
        final Tasks.Task t = pick(c);
        final int ink = dark ? U.ON_DARK : U.INK, muted = dark ? U.ON_DARK_MUTED : U.MUTED, bg = dark ? U.DARK : U.BG;
        final int line = dark ? 0xFF62625C : U.BORDER;
        LinearLayout box = U.col(c);
        box.setPadding(U.dp(c, 16), U.dp(c, 14), U.dp(c, 16), U.dp(c, 14));
        box.setBackground(U.box(c, bg, line, 16));
        TextView lab = U.text(c, t != null ? "COMPLETA UN TASK" : "LA TUA ALTERNATIVA", 12, muted, false); // 0.15: era «Una cosa che puoi fare adesso»
        lab.setLetterSpacing(0.06f);
        box.addView(lab);
        if (t != null) {
            TextView title = U.text(c, t.title, 18, ink, true);
            if (t.urgent) title.setText(U.bang(t.title)); // E001: propone prima un urgente
            title.setPadding(0, U.dp(c, 6), 0, 0);
            box.addView(title, U.full());
            String due = t.due == U.startOfDay(0) ? "da oggi" : "da " + U.dayLabel(t.due).toLowerCase(Locale.ITALY);
            box.addView(U.text(c, due, 13, muted, false));
            LinearLayout btns = U.row(c);
            TextView done = pill(c, "Fatto", true, dark);
            done.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    done(c, t);
                    U.toast(c, "Fatto. Grazie per esserti preso un momento");
                    if (onChange != null) onChange.run();
                }
            });
            TextView later = pill(c, "Più tardi", false, dark);
            later.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { later(c, t); if (onChange != null) onChange.run(); }
            });
            LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            a.rightMargin = U.dp(c, 8);
            btns.addView(done, a);
            btns.addView(later, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams blp = U.full();
            blp.topMargin = U.dp(c, 12);
            box.addView(btns, blp);
        }
        // l'alternativa, quella scritta dalla persona come in «Di notte»: riposare vale quanto fare
        LinearLayout alt = U.row(c);
        alt.setMinimumHeight(U.dp(c, 44));
        alt.setPadding(0, U.dp(c, t != null ? 10 : 6), 0, 0);
        alt.addView(U.text(c, t != null ? "Oppure:  " : "", 15, muted, false));
        alt.addView(U.text(c, Night.alternative(c), 15, ink, t == null));
        alt.setContentDescription("Oppure: " + Night.alternative(c));
        alt.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (onAlt != null) onAlt.run(); } });
        box.addView(alt, U.full());
        if (t == null) {
            // senza attività: solo l'alternativa, e il modo di scriverne una
            TextView add = U.text(c, "+ Aggiungi un task", 15, ink, false);
            add.setMinHeight(U.dp(c, 44));
            add.setGravity(Gravity.CENTER_VERTICAL);
            add.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Intent i = new Intent(c, AddActivity.class);
                    i.putExtra("day", U.startOfDay(0));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    U.start(c, i);
                }
            });
            box.addView(add, U.full());
        }
        return box;
    }

    private static TextView pill(Context c, String s, boolean primary, boolean dark) {
        int ink = dark ? U.ON_DARK : U.INK, bg = dark ? U.DARK : U.BG;
        TextView t = U.text(c, s, 15, primary ? bg : ink, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(U.dp(c, 48));
        t.setBackground(primary ? U.box(c, ink, 0, 24) : U.box(c, bg, ink, 24));
        return t;
    }
}
