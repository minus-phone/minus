package it.essenziale.launcher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

/**
 * Evolutiva 016 · il check che prende il posto della label nei pulsanti a pillola (piena o con bordo). Alla pressione
 * la label svanisce (RAPIDO) e al centro si disegna un segno di spunta di 22 dp (BASE, ease-in-out, dopo mezzo RAPIDO), nel colore
 * del testo; l'azione parte 250 ms dopo la fine del disegno, il tempo di vederlo (tutti i tempi in MotionLogic).
 * Con «Rimuovi animazioni» la label svanisce e il check c'è subito. Il pulsante non si riusa: dopo il tocco l'azione
 * cambia la schermata. Il check non si annuncia: lo dice l'azione.
 */
final class Tick {
    private Tick() {}

    /** Il click del pulsante: il check, poi l'azione. Un secondo tocco, nell'attesa, non fa niente. */
    static void onClick(final TextView b, final Runnable action) {
        final boolean[] used = {false};
        b.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) {
                if (used[0]) return;
                used[0] = true;
                // 0.24: l'azione parte quando il disegno ha finito davvero (ultimo fotogramma già disegnato), non a tempo
                play(b, action);
            }
        });
    }

    /** Solo il movimento: label che svanisce e check che si disegna. */
    static void play(final TextView b) { play(b, null); }

    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    /**
     * Il movimento, poi {@code done} (se c'è) dopo l'ultimo fotogramma. 0.24: il tempo si legge dall'orologio, non da un
     * ValueAnimator (che con la scala delle animazioni di Android diversa da 1 finiva dopo, o prima, dell'azione e il check si
     * fermava a metà), e il segno ha i limiti del pulsante: con limiti vuoti invalidateSelf() non ridisegnava niente, il
     * check si vedeva solo finché la label cambiava colore (180 ms).
     */
    static void play(final TextView b, final Runnable done) {
        final boolean red = Motion.reduced(b.getContext());
        final int color = b.getCurrentTextColor();
        final Mark mark = new Mark(b, color);
        b.getOverlay().add(mark);
        final int total = MotionLogic.tickAttesa(red);
        final long t0 = android.os.SystemClock.uptimeMillis();
        final Runnable step = new Runnable() {
            public void run() {
                long t = Math.min(total, android.os.SystemClock.uptimeMillis() - t0);
                boolean last = t >= total;
                float fade = red ? 1f : Math.min(1f, t / (float) Motion.RAPIDO);
                float f = MotionLogic.io(fade);
                b.setTextColor(Press.alpha(color, ((color >>> 24) & 0xFF) / 255f * (1f - f)));
                mark.progress = last ? 1f : MotionLogic.tickCheck(t, red);
                mark.setBounds(0, 0, b.getWidth(), b.getHeight());
                mark.invalidateSelf();
                b.invalidate();
                if (!last && b.isAttachedToWindow()) { b.postOnAnimation(this); return; }
                if (done != null) MAIN.post(done); // il fotogramma finale si disegna prima: l'azione va al giro dopo
            }
        };
        step.run();
    }

    /** Il segno di spunta, M5 11.5 l4 4 8 -8.5 su una griglia di 22, tratto di 2,2, estremi tondi. */
    private static final class Mark extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path(), part = new Path();
        private final PathMeasure pm = new PathMeasure();
        private final TextView host;
        float progress;

        Mark(TextView host, int color) {
            this.host = host;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(color);
        }

        @Override public void draw(Canvas cv) {
            if (progress <= 0f) return;
            float size = U.dp(host.getContext(), 22), s = size / 22f;
            float ox = (host.getWidth() - size) / 2f, oy = (host.getHeight() - size) / 2f;
            path.reset();
            path.moveTo(ox + 5f * s, oy + 11.5f * s);
            path.rLineTo(4f * s, 4f * s);
            path.rLineTo(8f * s, -8.5f * s);
            pm.setPath(path, false);
            part.reset();
            pm.getSegment(0f, pm.getLength() * Math.min(1f, progress), part, true);
            paint.setStrokeWidth(2.2f * s);
            cv.drawPath(part, paint);
        }

        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter f) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
