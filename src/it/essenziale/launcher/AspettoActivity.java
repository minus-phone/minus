package it.essenziale.launcher;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Impostazioni › Home e giornata › Aspetto (0.10, wireframe 156). */
public class AspettoActivity extends BaseActivity {
    private LinearLayout full;

    private void renderFull() {
        full.removeAllViews();
        final boolean on = U.fullscreen(this);
        LinearLayout tx = U.col(this);
        tx.addView(U.text(this, "Schermo intero", 16, U.INK, false));
        tx.addView(U.text(this, "Barre nascoste: scorri dal bordo per vederle, spariscono dopo 3 secondi. Ora e batteria passano in home", 13, U.MUTED, false));
        full.addView(tx, new LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        full.addView(U.toggle(this, on), new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 26)));
        full.setContentDescription("Schermo intero" + (on ? ", attivo" : ", spento"));
        full.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) {
                Changes.track(AspettoActivity.this, on ? "Schermo intero spento" : "Schermo intero acceso", new String[]{U.K_FULL},
                        new Runnable() { public void run() { U.prefs(AspettoActivity.this).edit().putBoolean(U.K_FULL, !on).commit(); } },
                        new Runnable() { public void run() { U.immersive(AspettoActivity.this); renderFull(); } });
            }
        });
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = U.screen(this, "Aspetto");
        LinearLayout.LayoutParams lp = U.full();
        lp.topMargin = U.dp(this, 6);
        root.addView(Aspetto.preview(this), lp);
        Aspetto.controls(this, root, true, null);
        // 0.12: schermo intero
        full = U.row(this);
        full.setMinimumHeight(U.dp(this, 64));
        LinearLayout.LayoutParams flp = U.full();
        flp.topMargin = U.dp(this, 10);
        root.addView(full, flp);
        renderFull();
        TextView n = U.text(this, "Ogni scelta ha «Annulla». Niente sfondi o icone: la home resta testo, per non tornare a una vetrina di app.", 13, U.MUTED, false);
        n.setPadding(0, U.dp(this, 16), 0, 0);
        root.addView(n);
        setContentView(root);
    }
}
