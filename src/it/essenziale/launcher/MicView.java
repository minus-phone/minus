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

    public MicView(Context c) {
        super(c);
        fill.setColor(U.INK);
        line.setColor(U.BG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeWidth(U.dp(c, 1.8f));
        setClickable(true);
        setFocusable(true);
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) / 2f;
        cv.drawCircle(cx, cy, r, fill);
        float u = r / 10f;
        RectF body = new RectF(cx - 2.6f * u, cy - 5.5f * u, cx + 2.6f * u, cy + 1.5f * u);
        cv.drawRoundRect(body, 2.6f * u, 2.6f * u, line);
        RectF arc = new RectF(cx - 4.6f * u, cy - 3.5f * u, cx + 4.6f * u, cy + 3.8f * u);
        cv.drawArc(arc, 10, 160, false, line);
        cv.drawLine(cx, cy + 3.8f * u, cx, cy + 6f * u, line);
    }
}
