package it.essenziale.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.ViewGroup;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Evolutiva 016 · gli stati premuti, uno per famiglia di pulsante. Tutti entrano ed escono in RAPIDO con la curva
 * unica (80 ms con «Rimuovi animazioni»), senza onda e senza scala: sulle pillole cambia solo lo sfondo, il testo
 * non si muove né si rimpicciolisce. Nessuna vibrazione qui: lo scatto lo dà chi lancia l'azione (Motion.scatto).
 * Il segno premuto non si annuncia a TalkBack: il tocco è già l'azione.
 *
 * 0.21 · il dito copre il pulsante, quindi oltre allo sfondo c'è un alone esterno: un anello di 8 dp attorno a pillole,
 * riga «Nuovo», barra di ricerca e icone tonde (6 dp attorno a chip e link di testo; chiaro, 18%, sulla pausa scura e 16%
 * nella sera), e un cerchio di 56 dp dietro la casella dei task e le icone senza bordo. Restano visibili almeno 240 ms
 * anche con un tocco veloce. Le righe senza bordo (risultati, interruttori) non hanno alone: si scurisce solo il fondo.
 * L'alone si disegna nell'overlay della radice, fuori dai bordi della vista: nessun genitore lo ritaglia.
 */
final class Press {
    private Press() {}

    /** Il colore con una trasparenza (0..1). */
    static int alpha(int color, float a) { return (color & 0x00FFFFFF) | (Math.round(255 * a) << 24); }

    /** Cambia lo stato premuto: chi vuole reagire (il badge del contatto) si registra qui. */
    interface OnPress { void onPress(boolean pressed); }

    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    /** L'alone disegnato nell'overlay della radice: un anello attorno alla vista, o un cerchio pieno dietro l'icona. */
    static final class Aura extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final boolean disc;
        private final float spread, radius, size;
        private int color;
        private float p, cx, cy, w, h;
        private final boolean grow;

        Aura(boolean disc, float spreadPx, float radiusPx, float sizePx, boolean grow) {
            this.disc = disc; this.spread = spreadPx; this.radius = radiusPx; this.size = sizePx; this.grow = grow;
            if (!disc) { paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(spreadPx); }
        }

        void place(float centerX, float centerY, float width, float height, int c) {
            cx = centerX; cy = centerY; w = width; h = height; color = c;
            setBounds(Math.round(cx - w / 2f - spread - size), Math.round(cy - h / 2f - spread - size), Math.round(cx + w / 2f + spread + size), Math.round(cy + h / 2f + spread + size));
        }

        void progress(float v) { p = v; invalidateSelf(); }

        @Override public void draw(Canvas cv) {
            if (p <= 0.001f) return;
            paint.setColor((color & 0x00FFFFFF) | (Math.round(((color >>> 24) & 0xFF) * p) << 24));
            if (disc) {
                cv.drawCircle(cx, cy, size / 2f * (grow ? 0.8f + 0.2f * p : 1f), paint);
                return;
            }
            // anello: da bordo vista a bordo vista + spread (il tratto è centrato a metà dello spessore)
            float k = spread / 2f;
            r.set(cx - w / 2f - k, cy - h / 2f - k, cx + w / 2f + k, cy + h / 2f + k);
            float rad = radius < 0 ? Math.min(w, h) / 2f + k : radius + k;
            if (radius == 0f) { paint.setStrokeJoin(Paint.Join.MITER); cv.drawRect(r, paint); }
            else cv.drawRoundRect(r, rad, rad, paint);
        }

        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter f) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /**
     * Chi accende e spegne un'alone per una vista. {@code down} lo mostra (RAPIDO, ease-in-out); {@code up} lo fa svanire,
     * ma non prima che siano passati 240 ms dal tocco (MotionLogic.aloneRestante).
     */
    static final class Halo {
        private final View v;
        private final boolean ring, grow, onDark;
        private final float spreadDp, radiusDp, sizeDp, centerXDp;
        private Aura aura;
        private ValueAnimator anim;
        private float p;
        private long downAt;
        private boolean shown;
        private Runnable hide;

        /** Anello di {@code spreadDp} attorno alla vista; {@code radiusDp} < 0 = tondo quanto la vista (pillola, cerchio). */
        static Halo ring(View v, float radiusDp, float spreadDp, boolean onDark) { return new Halo(v, true, radiusDp, spreadDp, 0, -1, false, onDark); }

        /** Cerchio pieno di {@code sizeDp} (INK al 10%), centrato sulla vista o a {@code centerXDp} dal suo bordo sinistro. */
        static Halo disc(View v, float sizeDp, float centerXDp, boolean grow) { return new Halo(v, false, 0, 0, sizeDp, centerXDp, grow, false); }

        /** Come {@link #disc}, ma chiaro (ON_DARK al 14%) per i fondi scuri fissi (il blocco). */
        static Halo discOnDark(View v, float sizeDp, float centerXDp, boolean grow) { return new Halo(v, false, 0, 0, sizeDp, centerXDp, grow, true); }

        private Halo(View v, boolean ring, float radiusDp, float spreadDp, float sizeDp, float centerXDp, boolean grow, boolean onDark) {
            this.v = v; this.ring = ring; this.radiusDp = radiusDp; this.spreadDp = spreadDp; this.sizeDp = sizeDp; this.centerXDp = centerXDp; this.grow = grow; this.onDark = onDark;
        }

        private int color() {
            if (!ring) return onDark ? alpha(U.ON_DARK, 0.14f) : alpha(U.INK, 0.10f);
            if (onDark) return alpha(U.ON_DARK, 0.18f);
            return U.dark ? alpha(U.INK, 0.16f) : alpha(U.INK, 0.10f);
        }

        /** Rete di sicurezza (0.21): se la vista non è più premuta, o è uscita di scena, l'alone si toglie comunque. */
        private final Runnable guard = new Runnable() {
            public void run() {
                if (!shown) return;
                boolean away = v.getWindowVisibility() != View.VISIBLE || !v.isShown();
                if (away) { drop(); return; }
                if (!v.isPressed() && hide == null) up();
                MAIN.postDelayed(this, 400);
            }
        };

        /** Toglie l'alone subito, senza animazione. */
        private void drop() {
            if (hide != null) { MAIN.removeCallbacks(hide); hide = null; }
            MAIN.removeCallbacks(guard);
            if (anim != null) anim.cancel();
            View root = v.getRootView();
            if (aura != null && root instanceof ViewGroup) ((ViewGroup) root).getOverlay().remove(aura);
            aura = null; shown = false; p = 0f;
        }

        void down() {
            if (hide != null) { MAIN.removeCallbacks(hide); hide = null; }
            if (!shown) {
                View root = v.getRootView();
                if (!(root instanceof ViewGroup) || v.getWidth() <= 0) return;
                Context c = v.getContext();
                int[] t = new int[2], q = new int[2];
                v.getLocationInWindow(t);
                root.getLocationInWindow(q);
                float cx = t[0] - q[0] + (centerXDp >= 0 ? U.dp(c, centerXDp) : v.getWidth() / 2f), cy = t[1] - q[1] + v.getHeight() / 2f;
                aura = new Aura(!ring, U.dp(c, spreadDp), radiusDp < 0 ? -1 : U.dp(c, radiusDp), U.dp(c, sizeDp), grow);
                aura.place(cx, cy, v.getWidth(), v.getHeight(), color());
                ((ViewGroup) root).getOverlay().add(aura);
                shown = true;
                downAt = SystemClock.uptimeMillis();
                MAIN.removeCallbacks(guard);
                MAIN.postDelayed(guard, 700);
            }
            run(1f);
        }

        void up() {
            if (!shown) return;
            long wait = MotionLogic.aloneRestante(SystemClock.uptimeMillis() - downAt);
            if (hide != null) MAIN.removeCallbacks(hide);
            hide = new Runnable() {
                public void run() {
                    hide = null;
                    Halo.this.run(0f);
                    // se l'animazione non finisce (schermata in pausa), l'alone si toglie comunque
                    MAIN.postDelayed(new Runnable() { public void run() { if (shown && hide == null && !v.isPressed()) drop(); } }, Motion.dur(v.getContext(), Motion.RAPIDO) + 120);
                }
            };
            if (wait <= 0) hide.run(); else MAIN.postDelayed(hide, wait);
        }

        void set(boolean pressed) { if (pressed) down(); else up(); }

        private void run(final float to) {
            if (anim != null) anim.cancel();
            final ValueAnimator me = ValueAnimator.ofFloat(p, to);
            anim = me;
            me.setDuration(Motion.dur(v.getContext(), Motion.RAPIDO));
            me.setInterpolator(Motion.DECELERA);
            me.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) { p = (Float) a.getAnimatedValue(); if (aura != null) aura.progress(p); }
            });
            me.addListener(new android.animation.AnimatorListenerAdapter() {
                private boolean cancelled;
                public void onAnimationCancel(android.animation.Animator an) { cancelled = true; }
                public void onAnimationEnd(android.animation.Animator an) {
                    if (cancelled || anim != me || to > 0f) return;
                    View root = v.getRootView();
                    if (aura != null && root instanceof ViewGroup) ((ViewGroup) root).getOverlay().remove(aura);
                    aura = null;
                    shown = false;
                }
            });
            me.start();
        }
    }

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
        private float ringDp = -1, ringSpreadDp;
        private boolean ringDark;
        private Halo ring;
        private boolean held;
        private Drawable pressedBest;   // 0.24: la faccia premuta quando il riquadro del primo risultato è acceso
        private boolean bestOn;

        /** Tiene la faccia premuta (il pulsante che ha appena fatto il suo lavoro, mentre il check si disegna). */
        void hold() {
            held = true;
            if (va != null) va.cancel();
            va = ValueAnimator.ofFloat(p, 1f);
            va.setDuration(Motion.dur(ctx, Motion.RAPIDO));
            va.setInterpolator(Motion.DECELERA);
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) { p = (Float) a.getAnimatedValue(); invalidateSelf(); }
            });
            va.start();
        }

        /** Alone esterno: anello di {@code spreadDp} attorno alla vista, con angoli di {@code radiusDp} (-1 = tondo quanto la vista). */
        Fade ring(float radiusDp, float spreadDp) { ringDp = radiusDp; ringSpreadDp = spreadDp; return this; }

        /** Sulla pausa scura l'anello è chiaro (18%) e di 8 dp per tutti, chip compresi. */
        Fade dark(boolean on) { if (on) { ringDark = true; if (ringSpreadDp > 0) ringSpreadDp = MotionLogic.ALONE_DP; } return this; }

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

        private Drawable pressedNow() { return bestOn && pressedBest != null ? pressedBest : pressed; }

        private void placePressed(float scale) {
            Drawable pressed = pressedNow();
            if (pressed == null) return;
            Rect b = getBounds();
            if (haloPx > 0) {
                float cx = haloCx >= 0 ? haloCx : b.exactCenterX(), cy = b.exactCenterY(), r = haloPx / 2f * scale;
                pressed.setBounds(Math.round(cx - r), Math.round(cy - r), Math.round(cx + r), Math.round(cy + r));
            } else pressed.setBounds(b.left - Math.round(padPx), b.top, b.right + Math.round(padPx), b.bottom);
        }

        @Override public void draw(Canvas cv) {
            if (normal != null) { normal.setAlpha(nAlpha); normal.draw(cv); }
            Drawable pressed = pressedNow();
            if (pressed != null && p > 0.001f) {
                placePressed(grow ? 0.8f + 0.2f * p : 1f);
                pressed.setAlpha(Math.round(255 * p));
                pressed.draw(cv);
            }
        }

        /**
         * 0.24: il riquadro del primo risultato si accende o si spegne in dissolvenza sul posto (RAPIDO), senza spostarsi.
         * Acceso, premuto scurisce (#DCD8CD); spento, premuto è il fondo delle righe normali.
         */
        Fade setBest(boolean on, boolean animate) {
            int to = on ? 255 : 0;
            if (on == bestOn && (nAlpha == to || (appear != null && appear.isRunning()))) return this;
            bestOn = on;
            if (appear != null) appear.cancel();
            if (!animate) { nAlpha = to; invalidateSelf(); return this; }
            appear = ValueAnimator.ofInt(nAlpha, to);
            appear.setDuration(Motion.dur(ctx, Motion.RAPIDO));
            appear.setInterpolator(Motion.DECELERA);
            appear.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) { nAlpha = (Integer) a.getAnimatedValue(); invalidateSelf(); }
            });
            appear.start();
            return this;
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

        /** Il fondo premuto (e chi si registra in {@code listener}) resta almeno 240 ms anche con un tocco veloce. */
        Fade minHold() { minHold = true; return this; }

        private boolean minHold;
        private long downAt;
        private Runnable pendingRelease;

        @Override protected boolean onStateChange(int[] state) {
            boolean now = false;
            for (int s : state) if (s == android.R.attr.state_pressed) now = true;
            if (now == down) return false;
            down = now;
            if (pendingRelease != null) { MAIN.removeCallbacks(pendingRelease); pendingRelease = null; }
            if (now) downAt = SystemClock.uptimeMillis();
            else if (minHold) {
                long wait = MotionLogic.aloneRestante(SystemClock.uptimeMillis() - downAt);
                if (wait > 0) {
                    pendingRelease = new Runnable() { public void run() { pendingRelease = null; apply(false); } };
                    MAIN.postDelayed(pendingRelease, wait);
                    return true;
                }
            }
            apply(now);
            return true;
        }

        private void apply(boolean now) {
            if (listener != null) listener.onPress(now);
            if (ringSpreadDp > 0 && getCallback() instanceof View) {
                if (ring == null) ring = Halo.ring((View) getCallback(), ringDp, ringSpreadDp, ringDark);
                ring.set(now);
            }
            if (va != null) va.cancel();
            va = ValueAnimator.ofFloat(p, now || held ? 1f : 0f);
            va.setDuration(Motion.dur(ctx, Motion.RAPIDO));
            va.setInterpolator(Motion.DECELERA);
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) { p = (Float) a.getAnimatedValue(); invalidateSelf(); }
            });
            va.start();
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

    /** Pillola piena: lo sfondo si schiarisce verso il fondo; alone esterno di 8 dp. */
    static Fade primary(View v, int fill, float radiusDp) {
        return box(v, fill, 0, radiusDp, MotionLogic.mix(fill, U.BG, 0.2f)).ring(radiusDp, MotionLogic.ALONE_DP);
    }

    /** Pillola piena con fondo e testo dati (per i fondi scuri della pausa). */
    static Fade primary(View v, int fill, int pressedFill, float radiusDp) { return box(v, fill, 0, radiusDp, pressedFill).ring(radiusDp, MotionLogic.ALONE_DP); }

    /** Pillola con bordo: riempimento al 10% di inchiostro, il bordo non cambia; alone esterno di 8 dp. */
    static Fade outlined(View v, int bg, int stroke, float radiusDp) {
        return box(v, bg, stroke, radiusDp, MotionLogic.mix(bg, stroke, 0.10f)).ring(radiusDp, MotionLogic.ALONE_DP);
    }

    /** Chip non scelto: premuto, un po' più scuro; alone esterno di 6 dp. */
    static Fade chip(View v, int fill, int stroke, float radiusDp) {
        return box(v, fill, stroke, radiusDp, MotionLogic.mix(fill, U.INK, 0.08f)).ring(radiusDp, MotionLogic.ALONE_PICCOLO_DP);
    }

    /** Chip scelto (inchiostro pieno): premuto, si schiarisce appena; alone esterno di 6 dp. */
    static Fade chipOn(View v, float radiusDp) { return primary(v, U.INK, radiusDp).ring(radiusDp, MotionLogic.ALONE_PICCOLO_DP); }

    /** Righe e voci di lista: sfondo CHIP al 60%, raggio 12, nessuna scala. Senza bordo: niente alone, si scurisce solo il fondo. */
    static Fade row(View v) {
        Context c = v.getContext();
        return on(v, null, shape(c, alpha(U.CHIP, 0.6f), 0, 12));
    }

    /**
     * Tessera con l'iniziale e il nome sotto (la fila «Contatti e gruppi»): premuta, il fondo scurisce, la tessera si
     * riempie d'inchiostro e la lettera diventa chiara (RAPIDO, almeno 240 ms). {@code tile} è il TextView con l'iniziale.
     */
    static Fade tile(View v, TextView tile) { return rowWithBadge(v, tile); }

    /**
     * 0.24: riga della ricerca che può diventare il primo risultato: il riquadro (CHIP) parte spento, si accende con
     * {@link Fade#setBest}; {@code badge} è il cerchio con l'iniziale (null per le app e le azioni).
     */
    static Fade rowSwitch(View v, View badge) {
        Context c = v.getContext();
        Fade f = on(v, shape(c, U.CHIP, 0, 12), shape(c, alpha(U.CHIP, 0.6f), 0, 12)).minHold();
        f.pressedBest = shape(c, U.dark ? MotionLogic.mix(U.CHIP, U.INK, 0.06f) : 0xFFDCD8CD, 0, 12);
        f.nAlpha = 0;
        if (badge != null) f.listener = new BadgeFx(badge);
        return f;
    }

    /** Riga già evidenziata (primo risultato): premuta, il riquadro scurisce (#DCD8CD sul chiaro). */
    static Fade rowBest(View v) {
        Context c = v.getContext();
        Drawable old = v.getBackground();
        Fade f = on(v, shape(c, U.CHIP, 0, 12), shape(c, U.dark ? MotionLogic.mix(U.CHIP, U.INK, 0.06f) : 0xFFDCD8CD, 0, 12));
        if (old instanceof Fade) { f.listener = ((Fade) old).listener; f.minHold = ((Fade) old).minHold; } // il badge con l'iniziale continua a reagire
        return f;
    }

    /** Solo l'alone esterno (nessun cambio di fondo): il «Cambia impostazioni» della ricerca. {@code radiusDp} 0 = angoli vivi. */
    static Fade ringOnly(View v, float radiusDp, float spreadDp) { return on(v, null, null).ring(radiusDp, spreadDp); }

    /**
     * Link di testo: il colore passa da MUTED a INK e compare un fondo INK all'8% dietro la riga, più un alone esterno
     * di 6 dp. Il testo non si muove.
     */
    static Fade link(TextView t, int normalColor) {
        Context c = t.getContext();
        t.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_pressed}, {}}, new int[]{U.INK, normalColor}));
        return on(t, null, shape(c, alpha(U.INK, 0.08f), 0, 10)).ring(10, MotionLogic.ALONE_PICCOLO_DP);
    }

    /**
     * Icona senza bordo (✕, microfono nella barra): alone circolare di 56 dp (più grande del dito, INK al 10%), da
     * scala 0,8 a 1. Sporge dalla vista: si disegna nell'overlay della radice. {@code normal} è lo sfondo, se c'è.
     */
    static Fade icon(final View v, Drawable normal) {
        Fade f = on(v, normal, null);
        f.listener = new OnPress() {
            private Halo h;
            public void onPress(boolean pressed) { if (h == null) h = Halo.disc(v, MotionLogic.ALONE_ICONA_DP, -1, true); h.set(pressed); }
        };
        return f;
    }

    /** Icona senza bordo su fondo scuro fisso (blocco, pausa): alone di 56 dp chiaro. */
    static Fade iconOnDark(final View v, Drawable normal) {
        Fade f = on(v, normal, null);
        f.listener = new OnPress() {
            private Halo h;
            public void onPress(boolean pressed) { if (h == null) h = Halo.discOnDark(v, MotionLogic.ALONE_ICONA_DP, -1, true); h.set(pressed); }
        };
        return f;
    }

    /** Link di testo su fondo scuro fisso: fondo ON_DARK all'8% dietro la riga e alone di 8 dp chiaro; il testo non si muove. */
    static Fade linkOnDark(View v) {
        Context c = v.getContext();
        return on(v, null, shape(c, alpha(U.ON_DARK, 0.08f), 0, 10)).ring(10, MotionLogic.ALONE_PICCOLO_DP).dark(true);
    }

    /** Riga senza bordo su fondo scuro fisso: premuta, si schiarisce appena (ON_DARK all'8%), nessun alone. */
    static Fade rowOnDark(View v) {
        Context c = v.getContext();
        return on(v, null, shape(c, alpha(U.ON_DARK, 0.08f), 0, 12));
    }

    /** Pillola con bordo su fondo scuro fisso: riempimento ON_DARK al 10%, alone chiaro di 8 dp. */
    static Fade outlinedOnDark(View v, int bg, int stroke, float radiusDp) {
        return box(v, bg, stroke, radiusDp, MotionLogic.mix(bg, U.ON_DARK, 0.10f)).ring(radiusDp, MotionLogic.ALONE_DP).dark(true);
    }

    /** Icona tonda con il bordo (il telefono): nessun movimento dell'icona, alone esterno di 8 dp attorno al cerchio. */
    static Fade circle(View v, Drawable normal) { return on(v, normal, null).ring(-1, MotionLogic.ALONE_DP); }

    /**
     * La casella dei task: lo stesso alone (56 dp, INK al 10%, RAPIDO, senza scala), centrato sulla casella. La casella
     * sta sul bordo sinistro della pagina, quindi l'alone esce dai bordi della sua vista: si disegna nell'overlay della
     * radice, che non ritaglia. Il tocco non si consuma (il click funziona come prima). Resta almeno 240 ms.
     */
    static void checkHalo(final View target, final float sizeDp, final float centerXDp) {
        final Halo h = Halo.disc(target, sizeDp, centerXDp, false);
        target.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, android.view.MotionEvent e) {
                int act = e.getActionMasked();
                if (act == android.view.MotionEvent.ACTION_DOWN) h.down();
                else if (act == android.view.MotionEvent.ACTION_UP || act == android.view.MotionEvent.ACTION_CANCEL) h.up();
                return false;
            }
        });
    }

    /** Riga «Nuovo» tratteggiata: premuta, il bordo scurisce (il tratteggio non si muove), lo sfondo prende CHIP al 50% e compare l'alone esterno di 8 dp. */
    static Fade dashed(View v, float radiusDp, float widthDp, float dash, float gap, int stroke) {
        Context c = v.getContext();
        GradientDrawable n = new GradientDrawable(), p = new GradientDrawable();
        n.setColor(0); p.setColor(alpha(U.CHIP, 0.5f));
        n.setCornerRadius(U.dp(c, radiusDp)); p.setCornerRadius(U.dp(c, radiusDp));
        n.setStroke(U.dp(c, widthDp), stroke, U.dp(c, dash), U.dp(c, gap));
        p.setStroke(U.dp(c, widthDp), U.INK, U.dp(c, dash), U.dp(c, gap));
        return on(v, n, p).ring(radiusDp, MotionLogic.ALONE_DP);
    }

    /** Riga con badge dell'iniziale (contatti): premuta, il cerchio si riempie d'inchiostro e la lettera diventa chiara. */
    static Fade rowWithBadge(View row, View badge) {
        Fade f = row(row).minHold();
        f.listener = new BadgeFx(badge);
        return f;
    }

    private static final class BadgeFx implements OnPress {
        private final View badge;
        private final ImageView icon; // 0.26 (Evolutiva 021): il badge dei gruppi ha un'icona al posto della lettera
        private final GradientDrawable bg;
        private final int chip = U.CHIP, ink = U.INK, light = U.BG;
        private ValueAnimator a;
        private float k;

        BadgeFx(View badge) {
            this.badge = badge;
            this.icon = badge instanceof android.view.ViewGroup && ((android.view.ViewGroup) badge).getChildCount() > 0
                    && ((android.view.ViewGroup) badge).getChildAt(0) instanceof ImageView
                    ? (ImageView) ((android.view.ViewGroup) badge).getChildAt(0) : null;
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
                    int fg = MotionLogic.mix(ink, light, k);
                    if (badge instanceof TextView) ((TextView) badge).setTextColor(fg);
                    else if (icon != null) icon.setColorFilter(fg, android.graphics.PorterDuff.Mode.SRC_IN);
                }
            });
            a.start();
        }
    }
}
