package it.essenziale.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

/**
 * Evolutiva 016 · gli stati premuti, uno per famiglia di pulsante. Tutti entrano ed escono in RAPIDO con la curva
 * DECELERA (80 ms con «Rimuovi animazioni»), senza onda e senza scala: sulle pillole cambia solo lo sfondo, il testo
 * non si muove né si rimpicciolisce. Nessuna vibrazione qui: lo scatto lo dà chi lancia l'azione (Motion.scatto).
 * Il segno premuto non si annuncia a TalkBack: il tocco è già l'azione.
 */
final class Press {
    private Press() {}

    /** Il colore con una trasparenza (0..1). */
    static int alpha(int color, float a) { return (color & 0x00FFFFFF) | (Math.round(255 * a) << 24); }

    /** Cambia lo stato premuto: chi vuole reagire (il badge del contatto) si registra qui. */
    interface OnPress { void onPress(boolean pressed); }

    /**
     * Sfondo con due facce, normale e premuta, che si sciolgono una nell'altra. La faccia premuta si disegna sopra
     * con trasparenza p (0..1); opzionalmente è un alone più piccolo che cresce da 0,8 a 1 (icone).
     */
    static final class Fade extends Drawable {
        private final Context ctx;
        private final Drawable normal, pressed;
        private float p;
        private boolean down;
        private ValueAnimator va;
        private float haloPx = -1, haloCx = -1, padPx;
        private int nAlpha = 255;
        private ValueAnimator appear;
        private boolean grow;
        OnPress listener;

        Fade(Context c, Drawable normal, Drawable pressed) {
            this.ctx = c; this.normal = normal; this.pressed = pressed;
        }

        /** Alone quadrato di lato halo (px), centrato in cx (px, -1 = al centro); cresce da 0,8 a 1. */
        Fade halo(float sizePx, float centerXPx) { haloPx = sizePx; haloCx = centerXPx; grow = true; return this; }

        /** Allarga la faccia premuta (px) a destra e a sinistra: un alone di testo più largo del testo. */
        Fade pad(float px) { padPx = px; return this; }

        @Override protected void onBoundsChange(Rect b) {
            if (normal != null) normal.setBounds(b);
            placePressed(1f);
        }

        private void placePressed(float scale) {
            if (pressed == null) return;
            Rect b = getBounds();
            if (haloPx > 0) {
                float cx = haloCx >= 0 ? haloCx : b.exactCenterX(), cy = b.exactCenterY(), r = haloPx / 2f * scale;
                pressed.setBounds(Math.round(cx - r), Math.round(cy - r), Math.round(cx + r), Math.round(cy + r));
            } else pressed.setBounds(b.left - Math.round(padPx), b.top, b.right + Math.round(padPx), b.bottom);
        }

        @Override public void draw(Canvas cv) {
            if (normal != null) { normal.setAlpha(nAlpha); normal.draw(cv); }
            if (pressed != null && p > 0.001f) {
                placePressed(grow ? 0.8f + 0.2f * p : 1f);
                pressed.setAlpha(Math.round(255 * p));
                pressed.draw(cv);
            }
        }

        /** La faccia normale compare in dissolvenza (RAPIDO) invece di comparire di colpo: il riquadro del primo risultato. */
        Fade appear() {
            nAlpha = 0;
            if (appear != null) appear.cancel();
            appear = ValueAnimator.ofInt(0, 255);
            appear.setDuration(Motion.dur(ctx, Motion.RAPIDO));
            appear.setInterpolator(Motion.DECELERA);
            appear.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) { nAlpha = (Integer) a.getAnimatedValue(); invalidateSelf(); }
            });
            appear.start();
            return this;
        }

        @Override public boolean isStateful() { return true; }

        @Override protected boolean onStateChange(int[] state) {
            boolean now = false;
            for (int s : state) if (s == android.R.attr.state_pressed) now = true;
            if (now == down) return false;
            down = now;
            if (listener != null) listener.onPress(now);
            if (va != null) va.cancel();
            va = ValueAnimator.ofFloat(p, now ? 1f : 0f);
            va.setDuration(Motion.dur(ctx, Motion.RAPIDO));
            va.setInterpolator(Motion.DECELERA);
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) { p = (Float) a.getAnimatedValue(); invalidateSelf(); }
            });
            va.start();
            return true;
        }

        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter f) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static GradientDrawable shape(Context c, int fill, int stroke, float radiusDp) {
        return U.box(c, fill, stroke, radiusDp);
    }

    /** Applica due facce a una vista. */
    static Fade on(View v, Drawable normal, Drawable pressed) {
        Fade f = new Fade(v.getContext(), normal, pressed);
        v.setBackground(f);
        return f;
    }

    /** Riquadro (pillola, chip) con la faccia premuta a tinta unita; il bordo resta lo stesso. */
    static Fade box(View v, int fill, int stroke, float radiusDp, int pressedFill) {
        Context c = v.getContext();
        return on(v, shape(c, fill, stroke, radiusDp), shape(c, pressedFill, stroke, radiusDp));
    }

    /** Pillola piena: lo sfondo si schiarisce verso il fondo. */
    static Fade primary(View v, int fill, float radiusDp) {
        return box(v, fill, 0, radiusDp, MotionLogic.mix(fill, U.BG, 0.2f));
    }

    /** Pillola piena con fondo e testo dati (per i fondi scuri della pausa). */
    static Fade primary(View v, int fill, int pressedFill, float radiusDp) { return box(v, fill, 0, radiusDp, pressedFill); }

    /** Pillola con bordo: riempimento al 10% di inchiostro, il bordo non cambia. */
    static Fade outlined(View v, int bg, int stroke, float radiusDp) {
        return box(v, bg, stroke, radiusDp, MotionLogic.mix(bg, stroke, 0.10f));
    }

    /** Chip non scelto: premuto, un po' più scuro. */
    static Fade chip(View v, int fill, int stroke, float radiusDp) {
        return box(v, fill, stroke, radiusDp, MotionLogic.mix(fill, U.INK, 0.08f));
    }

    /** Chip scelto (inchiostro pieno): premuto, si schiarisce appena. */
    static Fade chipOn(View v, float radiusDp) { return primary(v, U.INK, radiusDp); }

    /** Righe e voci di lista: sfondo CHIP al 60%, raggio 12, nessuna scala. */
    static Fade row(View v) {
        Context c = v.getContext();
        return on(v, null, shape(c, alpha(U.CHIP, 0.6f), 0, 12));
    }

    /** Riga già evidenziata (primo risultato): premuta, il riquadro scurisce. */
    static Fade rowBest(View v) {
        Context c = v.getContext();
        return on(v, shape(c, U.CHIP, 0, 12), shape(c, MotionLogic.mix(U.CHIP, U.INK, 0.06f), 0, 12));
    }

    /** Link di testo: il colore passa da MUTED a INK e compare un alone INK all'8% dietro la riga. Il testo non si muove. */
    static Fade link(TextView t, int normalColor) {
        Context c = t.getContext();
        t.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_pressed}, {}}, new int[]{U.INK, normalColor}));
        return on(t, null, shape(c, alpha(U.INK, 0.08f), 0, 10));
    }

    /** Icona (microfono, telefono, ✕): alone circolare di 40 dp, INK al 10%, da scala 0,8 a 1. */
    static Fade icon(View v, Drawable normal) {
        Context c = v.getContext();
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(alpha(U.INK, 0.10f));
        return on(v, normal, g).halo(U.dp(c, 40), -1);
    }

    /**
     * La casella dei task: lo stesso alone (36 dp, INK al 10%, da scala 0,8 a 1, RAPIDO), centrato sulla casella.
     * La casella sta sul bordo sinistro della pagina, quindi l'alone esce dai bordi della sua vista: si disegna
     * nell'overlay della radice, che non ritaglia. Il tocco non si consuma (il click funziona come prima).
     */
    static void checkHalo(final View target, final float sizeDp, final float centerXDp) {
        final Context c = target.getContext();
        final GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(alpha(U.INK, 0.10f));
        target.setOnTouchListener(new View.OnTouchListener() {
            private ValueAnimator a;
            private float p;
            private View root;

            public boolean onTouch(View v, android.view.MotionEvent e) {
                int act = e.getActionMasked();
                if (act == android.view.MotionEvent.ACTION_DOWN) show(v);
                else if (act == android.view.MotionEvent.ACTION_UP || act == android.view.MotionEvent.ACTION_CANCEL) hide();
                return false;
            }

            private void show(View v) {
                root = v.getRootView();
                if (!(root instanceof android.view.ViewGroup)) return;
                int[] t = new int[2], r = new int[2];
                v.getLocationInWindow(t);
                root.getLocationInWindow(r);
                float cx = t[0] - r[0] + U.dp(c, centerXDp), cy = t[1] - r[1] + v.getHeight() / 2f, h = U.dp(c, sizeDp) / 2f;
                g.setBounds(Math.round(cx - h), Math.round(cy - h), Math.round(cx + h), Math.round(cy + h));
                ((android.view.ViewGroup) root).getOverlay().add(g);
                run(1f);
            }

            private void hide() {
                if (root == null) return;
                run(0f);
            }

            private void run(final float to) {
                final ValueAnimator old = a, me = ValueAnimator.ofFloat(p, to);
                a = me;
                if (old != null) old.cancel();
                me.setDuration(Motion.dur(c, Motion.RAPIDO));
                me.setInterpolator(Motion.DECELERA);
                me.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    public void onAnimationUpdate(ValueAnimator v) {
                        p = (Float) v.getAnimatedValue();
                        g.setAlpha(Math.round(255 * p));
                        Rect b = g.getBounds();
                        float k = 0.8f + 0.2f * p, cx = b.exactCenterX(), cy = b.exactCenterY(), h = U.dp(c, sizeDp) / 2f * k;
                        g.setBounds(Math.round(cx - h), Math.round(cy - h), Math.round(cx + h), Math.round(cy + h));
                        g.invalidateSelf();
                    }
                });
                me.addListener(new android.animation.AnimatorListenerAdapter() {
                    public void onAnimationEnd(android.animation.Animator an) {
                        if (a == me && to == 0f && root instanceof android.view.ViewGroup) ((android.view.ViewGroup) root).getOverlay().remove(g);
                    }
                });
                me.start();
            }
        });
    }

    /** Riga «Nuovo» tratteggiata: premuta, il bordo scurisce (il tratteggio non si muove) e lo sfondo prende CHIP al 50%. */
    static Fade dashed(View v, float radiusDp, float widthDp, float dash, float gap, int stroke) {
        Context c = v.getContext();
        GradientDrawable n = new GradientDrawable(), p = new GradientDrawable();
        n.setColor(0); p.setColor(alpha(U.CHIP, 0.5f));
        n.setCornerRadius(U.dp(c, radiusDp)); p.setCornerRadius(U.dp(c, radiusDp));
        n.setStroke(U.dp(c, widthDp), stroke, U.dp(c, dash), U.dp(c, gap));
        p.setStroke(U.dp(c, widthDp), U.INK, U.dp(c, dash), U.dp(c, gap));
        return on(v, n, p);
    }

    /** Riga con badge dell'iniziale (contatti): premuta, il cerchio si riempie d'inchiostro e la lettera diventa chiara. */
    static Fade rowWithBadge(View row, TextView badge) {
        Fade f = row(row);
        f.listener = new BadgeFx(badge);
        return f;
    }

    private static final class BadgeFx implements OnPress {
        private final TextView badge;
        private final GradientDrawable bg;
        private final int chip = U.CHIP, ink = U.INK, light = U.BG;
        private ValueAnimator a;
        private float k;

        BadgeFx(TextView badge) {
            this.badge = badge;
            this.bg = badge.getBackground() instanceof GradientDrawable ? (GradientDrawable) badge.getBackground() : null;
        }

        public void onPress(boolean pressed) {
            if (a != null) a.cancel();
            a = ValueAnimator.ofFloat(k, pressed ? 1f : 0f);
            a.setDuration(Motion.dur(badge.getContext(), Motion.RAPIDO));
            a.setInterpolator(Motion.DECELERA);
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator v) {
                    k = (Float) v.getAnimatedValue();
                    if (bg != null) bg.setColor(MotionLogic.mix(chip, ink, k));
                    badge.setTextColor(MotionLogic.mix(ink, light, k));
                }
            });
            a.start();
        }
    }
}
