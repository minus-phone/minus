package it.essenziale.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** 0.17 (Evolutiva 005): il segno di −minus senza quadrato: il meno pieno e gli altri tre tratti dell'asterisco in trasparenza. */
final class Mark extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    Mark(Context c, int color) {
        super(c);
        p.setColor(color);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h), cx = w / 2f, cy = h / 2f;
        float len = s * 0.75f, th = s * 0.105f;
        r.set(cx - len / 2f, cy - th / 2f, cx + len / 2f, cy + th / 2f);
        int alpha = p.getAlpha();
        for (int a : new int[]{45, 90, 135}) {
            p.setAlpha((int) (alpha * 0.22f));
            c.save();
            c.rotate(a, cx, cy);
            c.drawRoundRect(r, th / 2f, th / 2f, p);
            c.restore();
        }
        p.setAlpha(alpha);
        c.drawRoundRect(r, th / 2f, th / 2f, p);
    }
}
