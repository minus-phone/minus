package it.essenziale.launcher;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Modifiche recenti (0.6): le ultime 20 scelte degli ultimi 30 giorni, ognuna si ripristina. */
public class ChangesActivity extends BaseActivity {

    private LinearLayout list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getIntent().putExtra("back", "Impostazioni");
        LinearLayout root = U.screen(this, "Modifiche recenti");
        TextView intro = U.text(this, "Le ultime scelte che hai cambiato. Se qualcosa non va come ti aspettavi, torni indietro da qui.", 14, U.MUTED, false);
        intro.setPadding(0, 0, 0, U.dp(this, 8));
        root.addView(intro);
        ScrollView sv = new ScrollView(this);
        list = U.col(this);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView foot = U.text(this, "Tiene le ultime 20 modifiche per 30 giorni. Ripristinare è anch'essa una modifica: si può annullare.", 12, U.MUTED, false);
        foot.setPadding(0, U.dp(this, 8), 0, 0);
        root.addView(foot);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        list.removeAllViews();
        List<Changes.Change> all = Changes.list(this);
        if (all.isEmpty()) {
            TextView t = U.text(this, "Ancora nessuna modifica.", 15, U.MUTED, false);
            t.setPadding(0, U.dp(this, 12), 0, 0);
            list.addView(t);
            return;
        }
        for (final Changes.Change c : all) {
            LinearLayout r = U.row(this);
            r.setMinimumHeight(U.dp(this, 64));
            r.setPadding(0, U.dp(this, 8), 0, U.dp(this, 8));
            LinearLayout tx = U.col(this);
            tx.addView(U.text(this, Changes.when(c.time), 12, U.MUTED, false));
            tx.addView(U.text(this, c.desc, 15, U.INK, false));
            r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView back = U.text(this, "Ripristina", 14, U.INK, false);
            back.setPaintFlags(back.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            back.setGravity(Gravity.CENTER);
            back.setMinHeight(U.dp(this, 44));
            back.setPadding(U.dp(this, 10), 0, 0, 0);
            back.setContentDescription("Ripristina: " + c.desc);
            back.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Changes.restore(ChangesActivity.this, c, new Runnable() { public void run() { render(); } });
                }
            });
            r.addView(back);
            list.addView(r, U.full());
            list.addView(U.divider(this));
        }
    }
}
