package it.essenziale.launcher;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gruppi, app per app (0.6): ognuno «Passa sempre» o «In silenzio». Niente menzioni.
 * I gruppi in silenzio vanno in Notifiche (contati) o in Altro (non contati), a scelta per ogni app.
 */
public class GroupsActivity extends BaseActivity {

    private static final String[] CODES = {"WA", "TG"};
    private LinearLayout tabs, list;
    private String code = "WA";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = U.screen(this, "Gruppi");
        tabs = U.row(this);
        tabs.setPadding(0, U.dp(this, 6), 0, U.dp(this, 6));
        root.addView(tabs, U.full());
        ScrollView sv = new ScrollView(this);
        list = U.col(this);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        render();
    }

    private static class G {
        String name, code;
        long last;
    }

    /** Gruppi visti nelle notifiche negli ultimi 30 giorni, più quelli con una scelta salvata. */
    private Map<String, G> groups() {
        Map<String, G> out = new LinkedHashMap<String, G>();
        for (String[] r : U.rows(U.prefs(this).getString(U.K_SEEN_GROUPS, ""))) {
            if (r.length < 3) continue;
            G g = new G();
            g.name = Rules.groupName(r[0]);
            g.code = codeOf(r[1]);
            try { g.last = Long.parseLong(r[2]); } catch (Exception ignored) {}
            String k = g.name.toLowerCase(Locale.ITALY);
            G old = out.get(k);
            if (old == null || g.last > old.last) out.put(k, g);
        }
        for (String[] r : U.rows(U.prefs(this).getString(U.K_GROUPS, ""))) {
            String name = Rules.groupName(r[0]), k = name.toLowerCase(Locale.ITALY);
            if (out.containsKey(k)) continue;
            G g = new G();
            g.name = name;
            g.code = null;
            out.put(k, g);
        }
        return out;
    }

    static String codeOf(String label) {
        String l = label == null ? "" : label.toLowerCase(Locale.ITALY);
        if (l.contains("telegram")) return "TG";
        if (l.contains("whatsapp")) return "WA";
        return null;
    }

    private void render() {
        tabs.removeAllViews();
        for (int i = 0; i < CODES.length; i++) {
            final String c = CODES[i];
            boolean on = c.equals(code);
            TextView t = U.text(this, Rules.appName(c), 14, on ? U.BG : U.INK, false);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(U.dp(this, 44));
            t.setPadding(U.dp(this, 16), 0, U.dp(this, 16), 0);
            t.setBackground(on ? U.box(this, U.INK, 0, 20) : U.box(this, U.BG, U.LINE, 20));
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { code = c; render(); }
            });
            LinearLayout.LayoutParams lp = U.wrap();
            lp.rightMargin = U.dp(this, 6);
            tabs.addView(t, lp);
        }

        list.removeAllViews();
        Map<String, String> rules = U.groupRules(this);
        List<G> always = new ArrayList<G>(), silent = new ArrayList<G>();
        for (G g : groups().values()) {
            if (g.code != null && !g.code.equals(code)) continue;
            if (g.code == null && !"WA".equals(code)) continue; // gruppi senza app nota: sotto WhatsApp
            (Rules.groupAlways(rules.get(g.name.toLowerCase(Locale.ITALY))) ? always : silent).add(g);
        }
        if (always.isEmpty() && silent.isEmpty()) {
            TextView t = U.text(this, U.listenerEnabled(this)
                    ? "Ancora nessun gruppo di " + Rules.appName(code) + ": compariranno qui appena arriva una notifica da un gruppo."
                    : "Attiva l'accesso alle notifiche (Impostazioni › Attivazione) per vedere qui i tuoi gruppi.", 15, U.INK, false);
            t.setPadding(0, U.dp(this, 14), 0, 0);
            list.addView(t);
        } else {
            list.addView(U.label(this, "Passano sempre"));
            if (always.isEmpty()) list.addView(U.text(this, "Nessuno. Tocca un gruppo qui sotto per farlo passare sempre.", 14, U.MUTED, false));
            for (G g : always) row(g, true);
            list.addView(U.label(this, "In silenzio"));
            if (silent.isEmpty()) list.addView(U.text(this, "Nessuno.", 14, U.MUTED, false));
            for (G g : silent) row(g, false);
        }

        // Dove vanno i gruppi in silenzio di questa app
        TextView q = U.text(this, "I gruppi in silenzio di " + Rules.appName(code) + " vanno in:", 15, U.INK, false);
        q.setPadding(0, U.dp(this, 22), 0, U.dp(this, 8));
        list.addView(q);
        String dest = U.groupDest(this).get(code);
        if (dest == null) dest = Rules.DEST_OTHER;
        LinearLayout chips = U.row(this);
        destChip(chips, "Notifiche", "contati", Rules.DEST_NOTIF, dest);
        destChip(chips, "Altro", "non contati", Rules.DEST_OTHER, dest);
        list.addView(chips, U.full());
        TextView foot = U.text(this, "I gruppi nuovi partono in silenzio. Un gruppo in cui scrivi passa per 12 ore, come le conversazioni aperte.", 13, U.MUTED, false);
        foot.setPadding(0, U.dp(this, 14), 0, U.dp(this, 24));
        list.addView(foot);
    }

    private void row(final G g, final boolean always) {
        LinearLayout r = U.row(this);
        r.setMinimumHeight(U.dp(this, 58));
        LinearLayout tx = U.col(this);
        tx.addView(U.text(this, g.name, 16, U.INK, false));
        if (g.last > 0) tx.addView(U.text(this, "ultimo messaggio " + ago(g.last), 12, U.MUTED, false));
        r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView act = U.text(this, always ? "Silenzia" : "Fai passare", 13, U.INK, false);
        act.setGravity(Gravity.CENTER);
        act.setMinHeight(U.dp(this, 44));
        act.setPadding(U.dp(this, 12), 0, U.dp(this, 12), 0);
        act.setBackground(U.box(this, U.BG, U.LINE, 18));
        r.addView(act);
        View.OnClickListener l = new View.OnClickListener() {
            public void onClick(View v) {
                Changes.track(GroupsActivity.this, g.name + (always ? ": in silenzio" : ": passa sempre"), new String[]{U.K_GROUPS},
                        new Runnable() { public void run() { U.setGroupRule(GroupsActivity.this, g.name, always ? "silenzio" : "sempre"); } },
                        new Runnable() { public void run() { render(); } });
            }
        };
        r.setOnClickListener(l);
        act.setOnClickListener(l);
        r.setContentDescription(g.name + (always ? ", passa sempre" : ", in silenzio"));
        list.addView(r, U.full());
        list.addView(U.divider(this));
    }

    private void destChip(LinearLayout chips, String title, String sub, final String value, String cur) {
        boolean on = value.equals(cur);
        LinearLayout c = U.col(this);
        c.setGravity(Gravity.CENTER);
        c.setMinimumHeight(U.dp(this, 52));
        c.setBackground(on ? U.box(this, U.INK, 0, 14) : U.box(this, U.BG, U.LINE, 14));
        TextView t = U.text(this, title, 15, on ? U.BG : U.INK, true);
        t.setGravity(Gravity.CENTER);
        c.addView(t);
        TextView s = U.text(this, sub, 12, on ? U.BG : U.MUTED, false);
        s.setGravity(Gravity.CENTER);
        c.addView(s);
        c.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Changes.track(GroupsActivity.this, "Gruppi in silenzio di " + Rules.appName(code) + ": in " + (Rules.DEST_NOTIF.equals(value) ? "Notifiche" : "Altro"),
                        new String[]{U.K_GROUP_DEST},
                        new Runnable() { public void run() { U.setGroupDest(GroupsActivity.this, code, value); } },
                        new Runnable() { public void run() { render(); } });
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (Rules.DEST_NOTIF.equals(value)) lp.rightMargin = U.dp(this, 8);
        chips.addView(c, lp);
    }

    static String ago(long t) {
        long d0 = U.startOfDay(0);
        if (t >= d0) return "oggi";
        if (t >= U.startOfDay(-1)) return "ieri";
        long days = (d0 - t) / (24L * 3600 * 1000) + 1;
        return days + " giorni fa";
    }
}
