package it.essenziale.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Evolutiva 016 · l'anello attorno al conto alla rovescia della pausa: si riempie piano, in senso orario, al ritmo
 * dei secondi (un secondo di sweep lineare per ogni secondo). Con «Rimuovi animazioni» avanza a scatti, un passo al secondo.
 */
final class RingView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float progress;
    private ValueAnimator anim;

    RingView(Context c) {
        super(c);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(U.dp(c, 2));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(U.ON_DARK);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Subito a questo avanzamento (0..1). */
    void set(float to) {
        stop();
        progress = to;
        invalidate();
    }

    /** Verso questo avanzamento in ms, a velocità costante. */
    void to(float to, int ms) {
        stop();
        if (ms <= 0 || Motion.reduced(getContext())) { set(to); return; }
        anim = ValueAnimator.ofFloat(progress, to);
        anim.setDuration(ms);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) { progress = (Float) a.getAnimatedValue(); invalidate(); }
        });
        anim.start();
    }

    void stop() { if (anim != null) { anim.cancel(); anim = null; } }

    @Override
    protected void onDraw(Canvas cv) {
        if (progress <= 0f) return;
        float h = p.getStrokeWidth() / 2f + 1f;
        r.set(h, h, getWidth() - h, getHeight() - h);
        cv.drawArc(r, -90f, 360f * progress, false, p);
    }
}
