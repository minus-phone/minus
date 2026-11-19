package it.essenziale.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.HashMap;
import java.util.Map;

/**
 * Evolutiva 016 · l'interruttore delle impostazioni: il pallino scorre in BASE (decelera), il colore della pista
 * si scioglie in RAPIDO. Stesse misure e colori di prima (pallino 20 dp, margine 3 dp). Con «Rimuovi animazioni»
 * il pallino è immediato e la pista cambia in 80 ms. Non ha stato proprio: lo disegna chi lo costruisce; per
 * animare dopo che la pagina si è ridisegnata, si ricorda l'ultimo stato per chiave (la stessa riga, lo stesso interruttore).
 */
final class SwitchView extends View {
    private static final Map<String, Boolean> LAST = new HashMap<String, Boolean>();

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final int off = U.TRACK_OFF, on = U.INK;
    private final float d, m;
    private float knobPos, trackPos; // 0 spento, 1 acceso
    private ValueAnimator ak, at;

    SwitchView(Context c, boolean isOn, String key) {
        super(c);
        d = U.dp(c, 20);
        m = U.dp(c, 3);
        knob.setColor(U.BG);
        knobPos = trackPos = isOn ? 1f : 0f;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (key != null) {
            Boolean was = LAST.get(key);
            LAST.put(key, isOn);
            if (was != null && was != isOn) { // lo stato è cambiato dall'ultima volta che l'ho disegnato: parte da lì
                knobPos = trackPos = isOn ? 0f : 1f;
                final boolean to = isOn;
                post(new Runnable() { public void run() { slideTo(to); } });
            }
        }
    }

    /** Da dove sta a dove va: pallino BASE, pista RAPIDO (entrambi 80 ms con le animazioni ridotte). */
    void slideTo(boolean to) {
        Context c = getContext();
        final float tgt = to ? 1f : 0f;
        if (ak != null) ak.cancel();
        if (at != null) at.cancel();
        ak = ValueAnimator.ofFloat(knobPos, tgt);
        ak.setDuration(Motion.reduced(c) ? 0 : Motion.BASE);
        ak.setInterpolator(Motion.DECELERA);
        ak.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) { knobPos = (Float) a.getAnimatedValue(); invalidate(); }
        });
        at = ValueAnimator.ofFloat(trackPos, tgt);
        at.setDuration(Motion.dur(c, Motion.RAPIDO));
        at.setInterpolator(Motion.DECELERA);
        at.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) { trackPos = (Float) a.getAnimatedValue(); invalidate(); }
        });
        ak.start();
        at.start();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        track.setColor(MotionLogic.mix(off, on, trackPos));
        r.set(0, 0, w, h);
        cv.drawRoundRect(r, h / 2f, h / 2f, track);
        float travel = Math.max(0f, w - 2 * m - d);
        float cx = m + d / 2f + travel * knobPos, cy = h / 2f;
        cv.drawCircle(cx, cy, d / 2f, knob);
    }
}
