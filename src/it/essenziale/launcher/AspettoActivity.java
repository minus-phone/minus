package it.essenziale.launcher;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Impostazioni › Home e giornata › Aspetto (0.10, wireframe 156). */
public class AspettoActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = U.screen(this, "Aspetto");
        LinearLayout.LayoutParams lp = U.full();
        lp.topMargin = U.dp(this, 6);
        root.addView(Aspetto.preview(this), lp);
        Aspetto.controls(this, root, true, null);
        TextView n = U.text(this, "Ogni scelta ha «Annulla». Niente sfondi o icone: la home resta testo, per non tornare a una vetrina di app.", 13, U.MUTED, false);
        n.setPadding(0, U.dp(this, 16), 0, 0);
        root.addView(n);
        setContentView(root);
    }
}
