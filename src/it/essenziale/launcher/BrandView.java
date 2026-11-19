package it.essenziale.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;

/**
 * Evolutiva 016, momento 10 · «Il meno che si disegna». L'unico momento di marca: sul colore della home il meno del
 * logo si disegna da sinistra a destra (LENTO, DECELERA, punta che si assottiglia), compaiono i tratti trasparenti
 * dietro (RAPIDO), poi la home arriva sotto al logo che svanisce (RAPIDO). Dura 660 ms in tutto e non trattiene mai i
 * tocchi oltre i 700; un tocco lo salta. Con «Rimuovi animazioni» il logo intero compare e sparisce in due dissolvenze
 * da 80 ms. Per TalkBack si annuncia con il solo nome, «−minus». Il tempo lo decide MotionLogic.
 */
final class BrandView extends View {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), ink = new Paint(Paint.ANTI_ALIAS_FLAG), ghost = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tip = new Path();
    private final RectF bar = new RectF(), ghostBar = new RectF();
    private final boolean reduced;
    private long t;                 // ms dall'inizio
    private ValueAnimator anim;
    private Runnable done;

    private BrandView(Context c) {
        super(c);
        reduced = Motion.reduced(c);
        bg.setColor(U.BG);
        ink.setColor(U.INK);
        ghost.setColor(U.INK);
        setContentDescription("−minus");
        setClickable(true); // i tocchi non passano alla home sotto: il primo salta il momento
    }

    /** Mette il momento sopra la home di {@code a}; {@code end} quando finisce (o viene saltato). */
    static BrandView play(final Activity a, final Runnable end) {
        final BrandView v = new BrandView(a);
        v.done = end;
        a.addContentView(v, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Motion.say(v, "−minus");
        v.anim = ValueAnimator.ofFloat(0f, 1f);
        final int total = MotionLogic.marcaTotale(v.reduced);
        v.anim.setDuration(total);
        v.anim.setInterpolator(new LinearInterpolator());
        v.anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator x) { v.t = Math.round((Float) x.getAnimatedValue() * total); v.invalidate(); }
        });
        v.anim.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator x) { v.finish(); }
        });
        v.anim.start();
        return v;
    }

    /** Toglie il logo subito (tocco, o la schermata che si ferma). */
    void finish() {
        if (anim != null) { ValueAnimator x = anim; anim = null; x.removeAllListeners(); x.cancel(); }
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
        Runnable d = done;
        done = null;
        if (d != null) d.run();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) finish();
        return true;
    }

    /** L'opacità di tutto il logo (e del fondo) a questo istante. */
    private float logoAlpha() {
        if (!reduced) return MotionLogic.marcaLogo(t);
        int d = MotionLogic.RIDOTTO;
        return t < d ? t / (float) d : Math.max(0f, 1f - (t - d) / (float) d);
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight(), s = U.dp(getContext(), 120);
        float la = logoAlpha();
        // il fondo ha il colore della home e svanisce con il logo: la home arriva da sotto, senza stacco
        bg.setAlpha(Math.round(255 * (reduced ? 1f : la)));
        if (reduced) bg.setAlpha(0); // ridotto: solo il logo compare e sparisce, la home resta dov'è
        cv.drawRect(0, 0, w, h, bg);
        float cx = w / 2f, cy = h / 2f, hw = s * 0.42f, hh = s * 0.04f;
        // i tratti trasparenti (come il logo animato del primo avvio: pieno solo il meno, gli altri al 30%)
        float ga = reduced ? 1f : MotionLogic.marcaTratti(t);
        if (ga > 0f) {
            ghost.setAlpha(Math.round(255 * 0.30f * ga * la));
            ghostBar.set(cx - hw, cy - hh, cx + hw, cy + hh);
            for (int deg : new int[]{45, 90, 135}) {
                cv.save();
                cv.rotate(deg, cx, cy);
                cv.drawRoundRect(ghostBar, hh, hh, ghost);
                cv.restore();
            }
        }
        ink.setAlpha(Math.round(255 * la));
        float p = reduced ? 1f : Motion.DECELERA.getInterpolation(MotionLogic.marcaDisegno(t));
        if (p >= 1f) {
            bar.set(cx - hw, cy - hh, cx + hw, cy + hh);
            cv.drawRoundRect(bar, hh, hh, ink);
        } else if (p > 0f) {
            // da sinistra a destra: la coda è tonda, la punta si assottiglia man mano che il meno si completa
            float x0 = cx - hw, xe = x0 + 2f * hw * p, taper = hh * 2.6f * (1f - p);
            float body = Math.max(x0 + 2f * hh, xe - taper);
            bar.set(x0, cy - hh, body, cy + hh);
            cv.drawRoundRect(bar, hh, hh, ink);
            tip.reset();
            tip.moveTo(body - hh, cy - hh);
            tip.lineTo(xe, cy);
            tip.lineTo(body - hh, cy + hh);
            tip.close();
            cv.drawPath(tip, ink);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (anim != null) { anim.removeAllListeners(); anim.cancel(); anim = null; }
    }
}
