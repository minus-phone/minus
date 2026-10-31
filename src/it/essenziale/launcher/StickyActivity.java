package it.essenziale.launcher;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Impostazioni › Notifiche › Icone fisse nella barra (0.9, wireframe 119). */
public class StickyActivity extends BaseActivity {

    private LinearLayout list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = U.screen(this, "Icone fisse nella barra");
        TextView intro = U.text(this, "Le notifiche che non passano Essenziale le toglie già. Queste restano perché sono fisse: Android non permette di toglierle, ma puoi renderle silenziose e l'icona sparisce.", 14, U.MUTED, false);
        intro.setPadding(0, U.dp(this, 4), 0, U.dp(this, 6));
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
        render();
    }

    private void render() {
        list.removeAllViews();
        List<String[]> rows = Sticky.recent(this);
        list.addView(U.label(this, "Ultimi 7 giorni"));
        if (rows.isEmpty()) {
            TextView t = U.text(this, U.listenerEnabled(this) ? "Nessuna icona fissa negli ultimi 7 giorni." : "Serve il filtro notifiche attivo per vederle.", 15, U.MUTED, false);
            t.setPadding(0, U.dp(this, 8), 0, U.dp(this, 8));
            list.addView(t);
        }
        for (final String[] r : rows) {
            LinearLayout row = U.row(this);
            row.setMinimumHeight(U.dp(this, 62));
            LinearLayout tx = U.col(this);
            tx.addView(U.text(this, U.appLabel(this, r[0]), 16, U.INK, false));
            String when = "ultima volta " + Changes.when(Long.parseLong(r[2])).toLowerCase(java.util.Locale.ITALY);
            tx.addView(U.text(this, (r[3].length() > 0 ? r[3] + " · " : "") + when, 12, U.MUTED, false));
            row.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView keep = action("Lascia", "Lascia l'icona di " + U.appLabel(this, r[0]), new Runnable() {
                public void run() { Sticky.keep(StickyActivity.this, r[0], r[1]); render(); }
            });
            row.addView(keep);
            TextView mute = action("Rendi silenziosa ›", "Rendi silenziosa la notifica fissa di " + U.appLabel(this, r[0]), new Runnable() {
                public void run() { Sticky.openSettings(StickyActivity.this, r[0], r[1]); }
            });
            row.addView(mute);
            list.addView(row, U.full());
            list.addView(U.divider(this));
        }
        TextView note = U.text(this, "«Rendi silenziosa» apre la pagina delle notifiche di quell'app in Android: scegli «Silenziosa». Wi-Fi, batteria e sveglia sono di sistema e restano.", 13, U.MUTED, false);
        note.setPadding(0, U.dp(this, 14), 0, U.dp(this, 60));
        list.addView(note);
    }

    private TextView action(String s, String desc, final Runnable r) {
        TextView t = U.text(this, s, 14, U.INK, false);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(U.dp(this, 44));
        t.setPadding(U.dp(this, 8), 0, U.dp(this, 2), 0);
        t.setContentDescription(desc);
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        return t;
    }
}
