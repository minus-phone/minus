package it.essenziale.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Pulsante tondo con l'icona del microfono, disegnato a tratto. */
public class MicView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final boolean outline;
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG); // 0.20 (Evolutiva 016): alone circolare da premuto
    private float haloP;
    private android.animation.ValueAnimator haloAnim;

    public MicView(Context c) { this(c, false); }

    /** 0.19 (Evolutiva 014): con outline il microfono è a contorno, nero su fondo chiaro (nel tema scuro, il grigio scuro dei riquadri). */
    public MicView(Context c, boolean outline) {
        super(c);
        this.outline = outline;
        fill.setColor(outline ? U.CHIP : U.INK);
        line.setColor(outline ? U.INK : U.BG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeWidth(U.dp(c, 1.8f));
        setClickable(true);
        setFocusable(true);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (haloAnim != null) haloAnim.cancel();
        haloAnim = android.animation.ValueAnimator.ofFloat(haloP, isPressed() ? 1f : 0f);
        haloAnim.setDuration(Motion.dur(getContext(), Motion.RAPIDO));
        haloAnim.setInterpolator(Motion.DECELERA);
        haloAnim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(android.animation.ValueAnimator a) { haloP = (Float) a.getAnimatedValue(); invalidate(); }
        });
        haloAnim.start();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) / 2f;
        cv.drawCircle(cx, cy, r, fill);
        if (haloP > 0.001f) { // premuto: alone INK al 10%, da scala 0,8 a 1
            halo.setColor(Press.alpha(U.INK, 0.10f * haloP));
            cv.drawCircle(cx, cy, Math.min(r, U.dp(getContext(), 20)) * (0.8f + 0.2f * haloP), halo);
        }
        if (outline) { drawOutline(cv, cx, cy, r); return; }
        float u = r / 10f;
        RectF body = new RectF(cx - 2.6f * u, cy - 5.5f * u, cx + 2.6f * u, cy + 1.5f * u);
        cv.drawRoundRect(body, 2.6f * u, 2.6f * u, line);
        RectF arc = new RectF(cx - 4.6f * u, cy - 3.5f * u, cx + 4.6f * u, cy + 3.8f * u);
        cv.drawArc(arc, 10, 160, false, line);
        cv.drawLine(cx, cy + 3.8f * u, cx, cy + 6f * u, line);
    }

    /** Come nelle tavole (44 x 44): corpo a contorno da 1,6, arco e asta da 2, estremi tondi. */
    private void drawOutline(Canvas cv, float cx, float cy, float r) {
        float k = r / 22f;
        Paint body = new Paint(line);
        body.setStrokeWidth(1.6f * k);
        RectF b = new RectF(cx - 5f * k, cy - 12f * k, cx + 5f * k, cy + 3f * k);
        cv.drawRoundRect(b, 5f * k, 5f * k, body);
        line.setStrokeWidth(2f * k);
        RectF arc = new RectF(cx - 9f * k, cy + 0.5f * k - 9f * k, cx + 9f * k, cy + 0.5f * k + 9f * k);
        cv.drawArc(arc, 0, 180, false, line);
        cv.drawLine(cx, cy + 9.5f * k, cx, cy + 13f * k, line);
    }
}
