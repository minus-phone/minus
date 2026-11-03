package it.essenziale.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.PathInterpolator;

/**
 * 0.13: il logo animato del primo avvio (canvas UI «LogoRotazione»). Il ventaglio si chiude in senso orario:
 * il tratto a 45° ruota su quello verticale, insieme su quello a 135°, poi tutti si posano sull'orizzontale
 * e resta un solo segno meno. Ogni tratto lascia una scia che si apre solo mentre ruota. Una volta sola;
 * con le animazioni spente in Android si vede subito il meno.
 */
public class LogoView extends View {
    private static final long CYCLE = 5000;           // come sul sito: 5 s, qui si gioca fino al 58%
    private static final float END = 0.58f;
    private static final float[] TRAIL = {0.42f, 0.28f, 0.18f, 0.10f, 0.05f};
    private static final long TRAIL_STEP = 35;
    private static final float ROTATING = 0.3f;
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PathInterpolator ease = new PathInterpolator(0.6f, 0f, 0.3f, 1f);
    private long start = -1;
    private final boolean still;

    public LogoView(Context c, int square, int stroke) {
        super(c);
        bg.setColor(square);
        bar.setColor(stroke);
        float scale = 1f;
        try { scale = android.provider.Settings.Global.getFloat(c.getContentResolver(), android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f); } catch (Exception ignored) {}
        still = scale == 0f;
        setContentDescription("Logo di −minus: i tratti di un asterisco ruotano uno sull'altro fino a lasciare un segno meno");
    }

    /** Angolo del tratto che parte da «base» gradi, a una frazione p del ciclo. */
    private float angle(int base, float p) {
        float[][] k;
        if (base == 45) k = new float[][]{{0, 45}, {.16f, 45}, {.30f, 90}, {.44f, 135}, {.58f, 180}};
        else if (base == 90) k = new float[][]{{0, 90}, {.30f, 90}, {.44f, 135}, {.58f, 180}};
        else if (base == 135) k = new float[][]{{0, 135}, {.44f, 135}, {.58f, 180}};
        else return 0;
        if (p <= k[0][0]) return k[0][1];
        for (int i = 1; i < k.length; i++) {
            if (p <= k[i][0]) {
                float f = (p - k[i - 1][0]) / (k[i][0] - k[i - 1][0]);
                return k[i - 1][1] + (k[i][1] - k[i - 1][1]) * ease.getInterpolation(f);
            }
        }
        return k[k.length - 1][1];
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h);
        float ox = (w - s) / 2f, oy = (h - s) / 2f;
        cv.drawRoundRect(new RectF(ox, oy, ox + s, oy + s), s * 0.22f, s * 0.22f, bg);
        long now = android.os.SystemClock.uptimeMillis();
        if (start < 0) start = now + 400; // un attimo fermo, poi si chiude
        long t = still ? CYCLE : now - start;
        float cx = ox + s / 2f, cy = oy + s / 2f, hw = s * 0.225f, hh = s * 0.025f;
        RectF r = new RectF(cx - hw, cy - hh, cx + hw, cy + hh);
        int alpha = bar.getAlpha();
        for (int base : new int[]{0, 135, 90, 45}) {
            for (int k = TRAIL.length; k >= 0; k--) {
                long tk = t - k * TRAIL_STEP;
                float p = Math.max(0, Math.min(END, tk / (float) CYCLE));
                // 3/11: pieno solo il tratto orizzontale; quelli che ruotano al 30%, scia in proporzione
                float base0 = base == 0 ? 1f : ROTATING;
                bar.setAlpha(Math.round(alpha * base0 * (k == 0 ? 1f : TRAIL[k - 1])));
                cv.save();
                cv.rotate(angle(base, p), cx, cy);
                cv.drawRoundRect(r, hh, hh, bar);
                cv.restore();
            }
        }
        bar.setAlpha(alpha);
        if (!still && t < CYCLE * END + TRAIL.length * TRAIL_STEP + 50) postInvalidateOnAnimation();
    }
}
