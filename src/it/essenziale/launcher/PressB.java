package it.essenziale.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

/**
 * 0.21 · gli stati premuti per le schermate secondarie (Impostazioni e sotto-pagine, Settimana, Aggiungi, liste, primo
 * avvio), in un file solo. Le famiglie sono quelle di {@link Press} (anello di 8 dp per pillole e schede con bordo,
 * 6 dp per chip e link, cerchio di 56 dp per icone e caselle); qui in più c'è la riga senza bordo che resta scura
 * almeno 240 ms.
 *
 * Perché serve: in una pagina che scorre Android segna «premuto» solo dopo 100 ms, e un tocco veloce lo toglie subito:
 * il fondo delle righe (che non ha alone) non si vedeva quasi mai. {@link Face} tiene la faccia premuta per 240 ms dal
 * momento in cui compare, come l'alone esterno. Nel tema scuro la riga prende il colore pieno dei riquadri (CHIP,
 * #2A2927): il 60% non si distingueva dal fondo #121211.
 */
final class PressB {
    private PressB() {}

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // ---------- Righe senza bordo ----------

    /**
     * Sfondo con una faccia premuta che compare in RAPIDO e resta almeno 240 ms (MotionLogic.aloneRestante).
     * {@code normal} è lo sfondo a riposo, se c'è (una scheda). Nessuna scala, nessun alone: si scurisce solo il fondo.
     */
    static final class Face extends Drawable {
        private final Context ctx;
        private final Drawable normal, pressed;
        private float p;
        private boolean down;
        private long downAt;
        private ValueAnimator va;
        private Runnable hide;
        Press.OnPress listener;

        Face(Context c, Drawable normal, Drawable pressed) { this.ctx = c; this.normal = normal; this.pressed = pressed; }

        @Override protected void onBoundsChange(Rect b) {
            if (normal != null) normal.setBounds(b);
            if (pressed != null) pressed.setBounds(b);
        }

        @Override public void draw(Canvas cv) {
            if (normal != null) normal.draw(cv);
            if (pressed != null && p > 0.001f) { pressed.setAlpha(Math.round(255 * p)); pressed.draw(cv); }
        }

        @Override public boolean isStateful() { return true; }

        @Override protected boolean onStateChange(int[] state) {
            boolean now = false;
            for (int s : state) if (s == android.R.attr.state_pressed) now = true;
            if (now == down) return false;
            down = now;
            if (hide != null) { MAIN.removeCallbacks(hide); hide = null; }
            if (now) {
                downAt = SystemClock.uptimeMillis();
                run(1f);
            } else {
                long wait = MotionLogic.aloneRestante(SystemClock.uptimeMillis() - downAt);
                if (wait <= 0) run(0f);
                else {
                    hide = new Runnable() { public void run() { hide = null; Face.this.run(0f); } };
                    MAIN.postDelayed(hide, wait);
                }
            }
            return true;
        }

        private void run(float to) {
            if (listener != null) listener.onPress(to > 0f);
            if (va != null) va.cancel();
            va = ValueAnimator.ofFloat(p, to);
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

    /** Il fondo premuto di una riga: #E7E4DB al 60% sul chiaro, il colore pieno dei riquadri (CHIP) sullo scuro. Raggio 12. */
    private static GradientDrawable rowShape(Context c) {
        return U.box(c, U.dark ? U.CHIP : Press.alpha(U.CHIP, 0.6f), 0, 12);
    }

    /** Riga o voce di lista senza bordo: tutta la riga si scurisce, resta visibile almeno 240 ms. */
    static Face row(View v) {
        Face f = new Face(v.getContext(), null, rowShape(v.getContext()));
        v.setBackground(f);
        return f;
    }

    /** Come {@link #row}, su una scheda che ha già il suo fondo (la tendina dei suggerimenti): il fondo resta, sopra si scurisce. */
    static Face rowOn(View v, Drawable normal) {
        Face f = new Face(v.getContext(), normal, rowShape(v.getContext()));
        v.setBackground(f);
        return f;
    }

    /** Riga con il cerchio dell'iniziale (contatti, persone): premuta, il cerchio si riempie d'inchiostro e la lettera diventa chiara. */
    static Face rowWithBadge(View row, View badge) {
        Face f = row(row);
        f.listener = new BadgeFx(badge);
        return f;
    }

    private static final class BadgeFx implements Press.OnPress {
        private final View badge;
        private final android.widget.ImageView icon; // 0.26 (Evolutiva 021): il badge dei gruppi ha un'icona al posto della lettera
        private final GradientDrawable bg;
        private final int chip = U.CHIP, ink = U.INK, light = U.BG;
        private ValueAnimator a;
        private float k;

        BadgeFx(View badge) {
            this.badge = badge;
            this.icon = badge instanceof android.view.ViewGroup && ((android.view.ViewGroup) badge).getChildCount() > 0
                    && ((android.view.ViewGroup) badge).getChildAt(0) instanceof android.widget.ImageView
                    ? (android.widget.ImageView) ((android.view.ViewGroup) badge).getChildAt(0) : null;
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

    // ---------- Le altre famiglie, con le stesse misure di Press ----------

    /** Link di testo (e «‹ Indietro»): anello di 6 dp, il colore passa a INK; il colore a riposo è quello che ha ora. */
    /**
     * 0.22: titolo che porta altrove («Settimana» in home): niente riquadro né anello, che su un titolo grande
     * sembrano un difetto. Premuto, il testo passa dal suo colore all'inchiostro pieno.
     */
    static void title(TextView t, int rest) {
        t.setBackground(null);
        t.setTextColor(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_pressed}, {}}, new int[]{U.INK, rest}));
    }

    static void link(TextView t) { Press.link(t, t.getCurrentTextColor()); }

    /** Icona o simbolo senza bordo (✕, +, ‹, ›, ↑): alone circolare di 56 dp, da scala 0,8 a 1. */
    static void icon(View v) { Press.icon(v, null); }

    /** Chip: scelto (inchiostro pieno) o no; anello di 6 dp. {@code fill}/{@code stroke}: fondo e bordo del chip non scelto. */
    static void chip(View v, boolean on, int fill, int stroke, float radiusDp) {
        if (on) Press.chipOn(v, radiusDp); else Press.chip(v, fill, stroke, radiusDp);
    }

    /**
     * Scheda non scelta di un controllo segmentato che sta su un vassoio colorato (i tab «Impegno | Task», i tre
     * «4 · 6 · 8 secondi»): nessun fondo a riposo, premuta si scurisce un po' e il testo (INK) resta leggibile, chiaro o
     * scuro; anello di 6 dp.
     */
    static void tabOff(View v, int trayColor, float radiusDp) {
        Press.box(v, 0, 0, radiusDp, MotionLogic.mix(trayColor, U.INK, U.dark ? 0.12f : 0.08f)).ring(radiusDp, MotionLogic.ALONE_PICCOLO_DP);
    }

    /** Pulsante a pillola con il bordo di un colore suo (Elimina in rosso): riempimento al 10% del bordo, anello di 8 dp. */
    static void outlined(View v, int bg, int stroke, float radiusDp) { Press.outlined(v, bg, stroke, radiusDp); }

    /**
     * Pulsante a pillola con il bordo tenue (LINE): il bordo non cambia, il fondo prende il 6% (8% sullo scuro) di inchiostro,
     * così si vede anche sul tema scuro dove il bordo quasi si confonde con il fondo; anello di 8 dp.
     */
    static void pill(View v, int bg, int stroke, float radiusDp) {
        Press.box(v, bg, stroke, radiusDp, MotionLogic.mix(bg, U.INK, U.dark ? 0.08f : 0.06f)).ring(radiusDp, MotionLogic.ALONE_DP);
    }

    /** Scheda con il bordo che si sceglie (le card dell'onboarding): premuta, un po' più scura; anello di 8 dp. */
    static void card(View v, int fill, int stroke, float radiusDp) {
        Press.box(v, fill, stroke, radiusDp, MotionLogic.mix(fill, U.INK, U.dark ? 0.10f : 0.06f)).ring(radiusDp, MotionLogic.ALONE_DP);
    }

    // ---------- Interruttore ----------

    /**
     * Il gesto di un interruttore in una riga: scatto subito (lo stesso, acceso o spento), poi l'azione. Se la pagina si
     * ridisegna con lo stato cambiato, l'interruttore con la sua chiave parte da dov'era e scorre (SwitchView).
     */
    static View.OnClickListener toggle(final Runnable action) {
        return new View.OnClickListener() {
            public void onClick(View v) { Motion.scatto(v); action.run(); }
        };
    }

    /**
     * Interruttore in una finestra che si chiude subito dopo (le voci dei menu a tocco lungo): il pallino scorre e la
     * finestra resta ancora un attimo, poi si chiude e parte l'azione. 80 ms con «Rimuovi animazioni».
     */
    static void slideThen(View sw, boolean on, Runnable then) {
        if (sw instanceof SwitchView) ((SwitchView) sw).slideTo(!on);
        MAIN.postDelayed(then, Motion.reduced(sw.getContext()) ? MotionLogic.RIDOTTO : Math.round(Motion.BASE * 0.8f));
    }

    // ---------- Caselle nelle liste ----------

    /** La casella delle liste di scelta (persone, app, servizi): quella dei task (18 dp come in home, qui 20), disegnata a mano. */
    static TaskBox box(Context c, boolean on) { return new TaskBox(c, on); }

    /**
     * Riga con la casella a destra: oltre al fondo scuro della riga, dietro la casella compare l'alone circolare di
     * 56 dp (INK al 10%, nell'overlay della radice, quindi senza tagli), come per la casella dei task.
     */
    static Face rowWithBox(View row, View box) {
        Face f = row(row);
        boxHalo(f, box);
        return f;
    }

    /** Aggiunge l'alone di 56 dp dietro {@code box} a una riga già fatta (per esempio con il cerchio dell'iniziale). */
    static void boxHalo(Face f, View box) {
        final Press.Halo h = Press.Halo.disc(box, MotionLogic.ALONE_ICONA_DP, -1, false);
        final Press.OnPress prev = f.listener;
        f.listener = new Press.OnPress() {
            public void onPress(boolean pressed) { if (prev != null) prev.onPress(pressed); h.set(pressed); }
        };
    }

    /**
     * Il gesto della casella in una lista, lo stesso della home: scatto, la casella si riempie in senso orario (RAPIDO)
     * e fa il suo piccolo rimbalzo con la spunta; subito dopo parte {@code then} (che salva e ridisegna la pagina).
     * Per togliere la spunta, la casella si svuota (RAPIDO) e {@code then} parte quasi subito. La riga non accetta altri
     * tocchi nel frattempo. Con «Rimuovi animazioni» tutto è immediato e {@code then} parte dopo 120 ms.
     */
    static void checkThen(final View row, final TaskBox box, boolean to, final Runnable then) {
        final Context c = row.getContext();
        final boolean red = Motion.reduced(c);
        row.setClickable(false);
        Motion.scatto(row);
        box.fillTo(to ? 1f : 0f, red ? 0 : Motion.RAPIDO);
        int fill = Motion.dur(c, Motion.RAPIDO);
        if (to) row.postDelayed(new Runnable() { public void run() { box.confirm(!red); } }, fill);
        row.postDelayed(then, red ? 120 : to ? fill + 200 : fill + 40);
    }
}
