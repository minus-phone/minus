package it.essenziale.launcher;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** A portata (0.8): cosa vedi appena tocchi la ricerca, in due blocchi: persone e gruppi, app. */
public class PortataActivity extends BaseActivity {

    private LinearLayout list;
    private PortataPicker picker;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = U.screen(this, "A portata");
        TextView intro = U.text(this, "Cosa vedi appena tocchi la ricerca, in due blocchi.", 14, U.MUTED, false);
        intro.setPadding(0, 0, 0, U.dp(this, 6));
        root.addView(intro);
        ScrollView sv = new ScrollView(this);
        list = U.col(this);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        picker = null; // rilegge la scelta: può essere cambiata da «Modifiche recenti»
        render();
    }

    private void render() {
        list.removeAllViews();
        // Persone e gruppi: sono quelli che passano sempre; qui se ne sceglie solo l'ordine
        final List<String[]> fixed = SearchActivity.fixedPeople(this);
        final List<String> names = new ArrayList<String>();
        for (String[] f : fixed) names.add(f[0]);
        list.addView(U.label(this, "Persone e gruppi · passano sempre"));
        // 0.15: in Ricerca l'ordine segue le conversazioni; quello scelto qui vale per chi non senti da un po'
        TextView how = U.text(this, "In Ricerca vengono prima le conversazioni in corso, poi chi hai sentito di recente. Quest'ordine vale per gli altri.", 12, U.MUTED, false);
        how.setPadding(0, 0, 0, U.dp(this, 8));
        list.addView(how, U.full());
        for (int i = 0; i < fixed.size(); i++) {
            final int k = i;
            final String[] f = fixed.get(i);
            LinearLayout r = U.row(this);
            r.setMinimumHeight(U.dp(this, 50));
            LinearLayout tx = U.col(this);
            tx.addView(U.text(this, f[0], 16, U.INK, false));
            if ("g".equals(f[2])) tx.addView(U.text(this, "gruppo", 12, U.MUTED, false));
            r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (i > 0) r.addView(small("↑", "Sposta su " + f[0], new Runnable() {
                public void run() {
                    Changes.track(PortataActivity.this, "A portata: " + f[0] + " più in alto", new String[]{SearchActivity.K_ORDER}, new Runnable() {
                        public void run() { SearchActivity.saveOrder(PortataActivity.this, Cerca.moveUp(names, k)); }
                    }, new Runnable() { public void run() { render(); } });
                }
            }));
            list.addView(r, U.full());
            list.addView(U.divider(this));
        }
        TextView link = U.text(this, fixed.isEmpty() ? "Nessuno passa sempre. Si sceglie in Notifiche › Persone e gruppi ›" : "Si cambiano in Notifiche › Persone e gruppi ›", 14, U.MUTED, false);
        link.setMinHeight(U.dp(this, 44));
        link.setGravity(Gravity.CENTER_VERTICAL);
        link.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(SettingsActivity.peopleIntent(PortataActivity.this, "A portata")); }
        });
        list.addView(link, U.full());
        toggleRow("Conversazioni attive", "Quelle a cui hai risposto nelle ultime 12 ore: le 3 più recenti", SearchActivity.K_ACTIVE_ON, "Conversazioni attive");

        // 0.11: le app in una sola vista, con la ricerca tra tutte (wireframe 161)
        if (picker == null) {
            List<String[]> props = new ArrayList<String[]>();
            for (U.AppInfo x : U.sortedApps(this)) {
                String k = OnboardingActivity.kind(x);
                if (k != null) props.add(new String[]{x.pkg, x.label, k});
            }
            picker = new PortataPicker(this, SearchActivity.portata(this), props, true, new PortataPicker.Host() {
                public void changed(final String desc, final List<String[]> selected, Runnable after) {
                    Changes.record(PortataActivity.this, desc, new String[]{SearchActivity.K_PORTATA});
                    SearchActivity.savePortata(PortataActivity.this, selected);
                }
            });
        } else picker.render();
        if (picker.root.getParent() != null) ((ViewGroup) picker.root.getParent()).removeView(picker.root);
        list.addView(U.label(this, "App"));
        list.addView(picker.root, U.full());
        list.addView(U.divider(this));
        toggleRow("Ricerche recenti", "Solo ciò che hai scritto o chiesto a voce, nelle ultime 2 ore; mai le app con la pausa", SearchActivity.K_RECENT_ON, "Ricerche recenti");
        TextView note = U.text(this, "Le app che non sono a portata si aprono scrivendone il nome nella ricerca.", 13, U.MUTED, false);
        note.setPadding(0, U.dp(this, 14), 0, U.dp(this, 90));
        list.addView(note);
    }

    private void toggleRow(String title, String sub, final String key, final String what) {
        final boolean on = U.prefs(this).getBoolean(key, true);
        LinearLayout r = U.row(this);
        r.setMinimumHeight(U.dp(this, 60));
        LinearLayout tx = U.col(this);
        tx.addView(U.text(this, title, 16, U.INK, false));
        tx.addView(U.text(this, sub, 13, U.MUTED, false));
        r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(U.toggle(this, on), new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 26)));
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Changes.track(PortataActivity.this, what + (on ? " nascoste" : " visibili"), new String[]{key},
                        new Runnable() { public void run() { U.prefs(PortataActivity.this).edit().putBoolean(key, !on).commit(); } },
                        new Runnable() { public void run() { render(); } });
            }
        });
        list.addView(r, U.full());
        list.addView(U.divider(this));
    }

    private TextView small(String s, String desc, final Runnable r) {
        TextView t = U.text(this, s, 18, U.INK, false);
        t.setGravity(Gravity.CENTER);
        t.setContentDescription(desc);
        t.setLayoutParams(new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 44)));
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        return t;
    }

}
