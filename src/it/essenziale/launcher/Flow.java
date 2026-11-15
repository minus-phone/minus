package it.essenziale.launcher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** 0.17 (Evolutiva 007): i riquadri degli eventi di tutto il giorno, uno dopo l'altro, a capo quando non ci stanno. */
final class Flow extends ViewGroup {
    private final int gap;

    Flow(Context c, int gapPx) { super(c); gap = gapPx; }

    @Override
    protected void onMeasure(int ws, int hs) {
        int max = MeasureSpec.getSize(ws), x = 0, y = 0, rowH = 0, used = 0;
        boolean bounded = MeasureSpec.getMode(ws) != MeasureSpec.UNSPECIFIED;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            v.measure(MeasureSpec.makeMeasureSpec(bounded ? max : 0, bounded ? MeasureSpec.AT_MOST : MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int w = v.getMeasuredWidth(), h = v.getMeasuredHeight();
            if (bounded && x > 0 && x + w > max) { x = 0; y += rowH + gap; rowH = 0; }
            x += w + gap;
            used = Math.max(used, x - gap);
            rowH = Math.max(rowH, h);
        }
        setMeasuredDimension(bounded && MeasureSpec.getMode(ws) == MeasureSpec.EXACTLY ? max : used, y + rowH);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int max = r - l, x = 0, y = 0, rowH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            int w = v.getMeasuredWidth(), h = v.getMeasuredHeight();
            if (x > 0 && x + w > max) { x = 0; y += rowH + gap; rowH = 0; }
            v.layout(x, y, x + w, y + h);
            x += w + gap;
            rowH = Math.max(rowH, h);
        }
    }
}
