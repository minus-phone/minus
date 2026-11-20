package it.essenziale.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.view.View;

/**
 * Evolutiva 016 · la festa. Quando l'ultimo task di oggi è fatto (o l'ultimo urgente) sullo sfondo della home, al centro,
 * compare un grande check in un cerchio morbido: il segno entra (LENTO, scala da 0,94), il check si disegna (LENTO, dopo
 * 0,6 LENTO), resta, esce (LENTO). Disegnata a mano, 72 dp; non scrive niente: lo dice TalkBack («Tutto fatto per oggi»
 * o «Urgenti fatti», 700 ms dopo l'inizio). Sta dietro i contenuti e non prende i tocchi. Con «Rimuovi animazioni» solo una
 * dissolvenza di 1,6 s, con il check già disegnato. Tutti i tempi sono in MotionLogic.
 */
final class FestaView extends View {
    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path check = new Path(), part = new Path();
    private final PathMeasure pm = new PathMeasure();
    private ValueAnimator anim;
    private Runnable say;
    private long t;
    private boolean reduced, active;

    FestaView(Context c) {
        super(c);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    /** Parte la festa (una nuova al posto di quella che c'è). */
    void play(final int kind) {
        stop();
        final String text = MotionLogic.festaTesto(kind);
        reduced = Motion.reduced(getContext());
        active = true;
        t = 0;
        anim = ValueAnimator.ofInt(0, MotionLogic.festaTotale(reduced));
        anim.setDuration(MotionLogic.festaTotale(reduced));
        anim.setInterpolator(new android.view.animation.LinearInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) { t = (Integer) a.getAnimatedValue(); invalidate(); }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            public void onAnimationCancel(Animator a) { cancelled = true; }
            public void onAnimationEnd(Animator a) { if (!cancelled) { active = false; invalidate(); } }
        });
        anim.start();
        if (text != null) {
            say = new Runnable() { public void run() { if (getParent() instanceof View) Motion.say((View) getParent(), text); } };
            postDelayed(say, MotionLogic.FESTA_ANNUNCIO_MS);
        }
    }

    void stop() {
        if (anim != null) { anim.cancel(); anim = null; }
        if (say != null) { removeCallbacks(say); say = null; }
        active = false;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas cv) {
        if (!active) return;
        float a = MotionLogic.festaOpacita(t, reduced);
        if (a <= 0f) return;
        float size = U.dp(getContext(), 72), s = size / 120f; // il disegno è su una griglia di 120
        float sc = MotionLogic.festaScala(t, reduced);
        cv.save();
        cv.translate(getWidth() / 2f, getHeight() / 2f);
        cv.scale(sc, sc);
        cv.translate(-size / 2f, -size / 2f);
        disc.setColor(Press.alpha(U.INK, 0.07f * a));
        cv.drawCircle(size / 2f, size / 2f, 58f * s, disc);
        float p = MotionLogic.festaCheck(t, reduced);
        if (p > 0f) {
            check.reset();
            check.moveTo(38f * s, 61f * s);
            check.rLineTo(15f * s, 15f * s);
            check.rLineTo(30f * s, -31f * s);
            pm.setPath(check, false);
            part.reset();
            pm.getSegment(0f, pm.getLength() * p, part, true);
            stroke.setStrokeWidth(7f * s);
            stroke.setColor(Press.alpha(0xFF8A877F, a)); // spec E016 §5: segno grigio #8A877F
            cv.drawPath(part, stroke);
        }
        cv.restore();
    }
}
