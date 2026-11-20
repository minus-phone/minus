package it.essenziale.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Evolutiva 016 · la casella del task (18 dp, come prima), che si riempie in senso orario, poi la spunta
 * con un rimbalzo di molla. Il riempimento dura RAPIDO (ease-in-out), la spunta e il rimbalzo BASE. Senza animazioni:
 * tutto diretto. Disegnata a mano, nessuna risorsa.
 */
final class TaskBox extends View {
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF(), inner = new RectF();
    private final Path clip = new Path(), check = new Path();
    private final float radius, sw;
    private float progress;      // 0..1, il riempimento
    private float tickAlpha;     // 0..1, la spunta
    private ValueAnimator fillAnim;

    TaskBox(Context c, boolean done) {
        super(c);
        sw = U.dp(c, 1.5f);
        radius = U.dp(c, 4);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(sw);
        stroke.setColor(U.INK);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(U.INK);
        tick.setStyle(Paint.Style.STROKE);
        tick.setStrokeCap(Paint.Cap.ROUND);
        tick.setStrokeJoin(Paint.Join.ROUND);
        tick.setColor(U.BG);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        set(done);
    }

    /** Subito, senza movimento. */
    void set(boolean done) {
        if (fillAnim != null) fillAnim.cancel();
        setScaleX(1f); setScaleY(1f);
        progress = done ? 1f : 0f;
        tickAlpha = done ? 1f : 0f;
        invalidate();
    }

    /** Il riempimento verso 0 o 1; senza animazioni resta una dissolvenza così breve che si vede solo il cambio. */
    void fillTo(float to, int ms) {
        if (fillAnim != null) fillAnim.cancel();
        final float from = progress;
        if (ms <= 0 || from == to) { progress = to; if (to < 1f) tickAlpha = 0f; invalidate(); return; }
        if (to < 1f) tickAlpha = 0f;
        fillAnim = ValueAnimator.ofFloat(0f, 1f);
        fillAnim.setDuration(ms);
        fillAnim.setInterpolator(new android.view.animation.LinearInterpolator()); // la curva (ease-in-out) la mette io(), come nel prototipo
        fillAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) { progress = from + (to - from) * MotionLogic.io(a.getAnimatedFraction()); invalidate(); }
        });
        fillAnim.start();
    }

    /** La spunta compare e la casella fa un piccolo rimbalzo (1 → 1,12 → 1, un solo rimbalzo, stessa curva). Con le animazioni ridotte, nessun rimbalzo. */
    void confirm(boolean bounce) {
        if (fillAnim != null) fillAnim.cancel();
        progress = 1f;
        tickAlpha = 1f;
        invalidate();
        if (!bounce) return;
        ObjectAnimator o = ObjectAnimator.ofPropertyValuesHolder(this, pop("scaleX"), pop("scaleY"));
        o.setDuration(Motion.BASE);
        o.start();
    }

    /** 1 → 1,12, poi → 1 (la curva è la stessa). */
    private static PropertyValuesHolder pop(String prop) {
        Keyframe a = Keyframe.ofFloat(0f, 1f), b = Keyframe.ofFloat(0.5f, 1.12f), c = Keyframe.ofFloat(1f, 1f);
        b.setInterpolator(Motion.MOLLA);
        c.setInterpolator(Motion.DECELERA);
        return PropertyValuesHolder.ofKeyframe(prop, a, b, c);
    }

    @Override
    protected void onDraw(Canvas cv) {
        float h = sw / 2f;
        rect.set(h, h, getWidth() - h, getHeight() - h);
        if (progress > 0f) {
            clip.reset();
            clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
            cv.save();
            cv.clipPath(clip);
            if (progress >= 1f) cv.drawRect(0, 0, getWidth(), getHeight(), fill);
            else {
                float big = Math.max(getWidth(), getHeight());
                inner.set(getWidth() / 2f - big, getHeight() / 2f - big, getWidth() / 2f + big, getHeight() / 2f + big);
                cv.drawArc(inner, -90f, 360f * progress, true, fill);
            }
            cv.restore();
        }
        cv.drawRoundRect(rect, radius, radius, stroke);
        if (tickAlpha > 0f) {
            float u = getWidth() / 18f, o = getWidth() / 6f; // la spunta è di 12 su 18, al centro
            tick.setStrokeWidth(1.8f * u);
            check.reset();
            check.moveTo(o + 2.2f * u, o + 6.3f * u);
            check.lineTo(o + 4.6f * u, o + 8.7f * u);
            check.lineTo(o + 9.8f * u, o + 3.3f * u);
            tick.setAlpha(Math.round(255 * tickAlpha));
            cv.drawPath(check, tick);
        }
    }
}
