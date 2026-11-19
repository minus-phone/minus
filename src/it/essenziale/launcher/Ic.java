package it.essenziale.launcher;

import android.content.Context;
import android.graphics.PorterDuff;
import android.view.View;
import android.widget.ImageView;

/**
 * Evolutiva 016, fase 2 · le icone della direzione A (tratto di 1,5 su una griglia di 24) che vivono in res/drawable.
 * Si risolvono per nome (non c'è R.java) e si tingono con i colori del tema.
 */
final class Ic {
    private Ic() {}

    /** L'icona {@code name} (es. «ic_notte») di {@code sizeDp}, nel colore dato; null se la risorsa non c'è. */
    static ImageView view(Context c, String name, int color, int sizeDp) {
        int id = c.getResources().getIdentifier(name, "drawable", c.getPackageName());
        if (id == 0) return null;
        ImageView v = new ImageView(c);
        v.setImageResource(id);
        v.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); // decorazione: lo stato lo dice il testo
        v.setMinimumWidth(U.dp(c, sizeDp));
        v.setMinimumHeight(U.dp(c, sizeDp));
        return v;
    }
}
