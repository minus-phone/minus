package it.essenziale.launcher;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Evolutiva 016 · un testo che, quando cambia, scorre in su invece di scattare (la pillola del tempo: «18 min»
 * → «19 min»). Il vecchio sale e svanisce, il nuovo arriva dal basso, in BASE con la curva DECELERA. Con
 * «Rimuovi animazioni», una dissolvenza di 80 ms. Sono due TextView sovrapposti; la vista ritaglia al proprio bordo.
 */
final class RollText extends FrameLayout {
    private TextView cur, nxt;
    private CharSequence text = "";

    RollText(Context c, float sp, int color) {
        super(c);
        cur = make(c, sp, color);
        nxt = make(c, sp, color);
        nxt.setVisibility(INVISIBLE);
        addView(cur);
        addView(nxt);
    }

    private TextView make(Context c, float sp, int color) {
        TextView t = new TextView(c);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setMinHeight(U.dp(c, 32));
        t.setTypeface(Typeface.DEFAULT);
        return t;
    }

    CharSequence text() { return text; }

    /** Cambia il testo; se la vista è già a schermo e il testo è diverso, scorre. */
    void set(CharSequence s, boolean animate) {
        if (s == null) s = "";
        if (s.toString().equals(text.toString())) return;
        text = s;
        cur.animate().cancel();
        nxt.animate().cancel();
        if (!animate || getWidth() == 0 || cur.getText().length() == 0) { cur.setText(s); cur.setAlpha(1f); cur.setTranslationY(0f); nxt.setVisibility(INVISIBLE); return; }
        final TextView old = cur, neu = nxt;
        cur = neu; nxt = old;
        neu.setText(s);
        neu.setVisibility(VISIBLE);
        Context c = getContext();
        boolean red = Motion.reduced(c);
        float travel = red ? 0f : Math.max(getHeight(), U.dp(c, 20));
        int ms = Motion.dur(c, Motion.BASE);
        neu.setAlpha(0f);
        neu.setTranslationY(travel);
        neu.animate().alpha(1f).translationY(0f).setDuration(ms).setInterpolator(Motion.DECELERA).start();
        old.animate().alpha(0f).translationY(-travel).setDuration(ms).setInterpolator(Motion.DECELERA).withEndAction(new Runnable() {
            public void run() { old.setText(""); old.setVisibility(INVISIBLE); }
        }).start();
    }
}
