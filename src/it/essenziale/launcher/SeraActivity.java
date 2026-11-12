package it.essenziale.launcher;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Evolutiva 002 · Impostazioni › Meno interferenze sul sonno › Avviso e consiglio della sera:
 * il preavviso di 10 minuti e il consiglio della settimana, due interruttori accesi. Con «all»,
 * l'elenco dei 26 consigli in sola lettura, in grassetto quello di questa settimana.
 */
public class SeraActivity extends BaseActivity {
    private LinearLayout body;
    private boolean all;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        all = getIntent().getBooleanExtra("all", false);
        LinearLayout root = U.screen(this, all ? "I consigli della sera" : "Avviso e consiglio della sera");
        ScrollView sv = new ScrollView(this);
        body = U.col(this);
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        body.removeAllViews();
        int now = Sera.tipIndex(this);
        if (all) {
            body.addView(U.text(this, "Uno a settimana, lo stesso per sette sere. Poi il giro ricomincia.", 14, U.MUTED, false));
            for (int i = 0; i < Serale.TIPS.length; i++) {
                LinearLayout r = U.row(this);
                r.setGravity(Gravity.TOP);
                r.setPadding(0, U.dp(this, 9), 0, U.dp(this, 9));
                TextView n = U.text(this, String.valueOf(i + 1), 14, U.MUTED, false);
                n.setMinWidth(U.dp(this, 28));
                r.addView(n);
                r.addView(U.text(this, Serale.TIPS[i], 14, U.INK, i == now), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                if (i == now) r.setContentDescription("Questa settimana: " + Serale.TIPS[i]);
                body.addView(r, U.full());
                body.addView(U.divider(this));
            }
            body.addView(note("Le fonti sono nel piano di progetto, nella pagina «Meno interferenze sul sonno»."));
            return;
        }
        body.addView(U.text(this, "All'ora di staccare −minus ti avvisa una volta. Se stai scorrendo in un'app frenata si apre la pillola; "
                + "se stai usando un'altra app arriva una notifica silenziosa; a telefono bloccato te lo dice in home allo sblocco.", 14, U.MUTED, false));
        int pre = ((Night.offMin(this) - Serale.PREWARN_MIN) % 1440 + 1440) % 1440;
        toggle("Preavviso di 10 minuti", "Alle " + Notte.hhmm(pre) + ", una riga in home e sulla pillola", Sera.K_PREWARN);
        toggle("Consiglio della settimana", "Uno nuovo ogni lunedì, " + Serale.TIPS.length + " in tutto", Sera.K_TIP);
        if (Sera.tipOn(this)) {
            LinearLayout c = U.col(this);
            c.setPadding(U.dp(this, 14), U.dp(this, 12), U.dp(this, 14), U.dp(this, 12));
            c.setBackground(U.box(this, U.SURFACE, U.LINE, 14));
            TextView l = U.label(this, "Questa settimana · " + (now + 1) + " di " + Serale.TIPS.length);
            l.setPadding(0, 0, 0, U.dp(this, 4));
            c.addView(l);
            c.addView(U.text(this, Serale.TIPS[now], 15, U.INK, false));
            LinearLayout.LayoutParams lp = U.full();
            lp.topMargin = U.dp(this, 10);
            body.addView(c, lp);
        }
        TextView allBtn = U.text(this, "Tutti i consigli  ›", 15, U.INK, false);
        allBtn.setMinHeight(U.dp(this, 48));
        allBtn.setGravity(Gravity.CENTER_VERTICAL);
        allBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new android.content.Intent(SeraActivity.this, SeraActivity.class).putExtra("all", true).putExtra("back", "Avviso e consiglio"));
            }
        });
        body.addView(allBtn, U.full());
        body.addView(note("«Ancora 15 minuti» si può usare una volta a sera. Nessun suono, solo un tocco aptico breve sopra le app frenate."));
    }

    private TextView note(String s) {
        TextView t = U.text(this, s, 12, U.MUTED, false);
        t.setPadding(0, U.dp(this, 12), 0, U.dp(this, 12));
        return t;
    }

    private void toggle(String title, String sub, final String key) {
        final boolean on = U.prefs(this).getBoolean(key, true);
        LinearLayout r = U.row(this);
        r.setMinimumHeight(U.dp(this, 64));
        r.setPadding(0, U.dp(this, 8), 0, U.dp(this, 8));
        LinearLayout tx = U.col(this);
        tx.addView(U.text(this, title, 16, U.INK, false));
        tx.addView(U.text(this, sub, 13, U.MUTED, false));
        r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(U.toggle(this, on), new LinearLayout.LayoutParams(U.dp(this, 48), U.dp(this, 28)));
        r.setContentDescription(title + ", interruttore, " + (on ? "acceso" : "spento"));
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Changes.track(SeraActivity.this, title + (on ? " spento" : " acceso"), new String[]{key},
                        new Runnable() { public void run() { U.prefs(SeraActivity.this).edit().putBoolean(key, !on).commit(); } },
                        new Runnable() { public void run() { render(); } });
            }
        });
        body.addView(r, U.full());
        body.addView(U.divider(this));
    }
}
