package it.essenziale.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/**
 * Evolutiva 016 · il linguaggio del movimento, in un posto solo.
 * Durate: RAPIDO 120 (tocchi, scatti, cambi di testo), BASE 240 (spunta, righe che si chiudono, pillola),
 * LENTO 480 (pausa, sera). Curve: DECELERA per tutto, MOLLA solo per la conferma.
 * Vibrazioni: «conferma» (hai finito qualcosa) e «scatto» (hai scelto); mai lunghe né ripetute, nessun permesso
 * VIBRATE (si usa performHapticFeedback, che rispetta le impostazioni di chi ha spento le vibrazioni).
 * «Rimuovi animazioni» di Android: ogni movimento diventa una dissolvenza di 80 ms o un cambio diretto.
 */
public final class Motion {
    private Motion() {}

    public static final int RAPIDO = MotionLogic.RAPIDO, BASE = MotionLogic.BASE, LENTO = MotionLogic.LENTO;

    public static final Interpolator DECELERA = new PathInterpolator(0.2f, 0f, 0f, 1f);
    public static final Interpolator MOLLA = new PathInterpolator(0.34f, 1.4f, 0.64f, 1f);

    // ---------- «Rimuovi animazioni» ----------

    /** Vero se in Android le animazioni sono tolte (scala della durata degli animatori = 0). */
    public static boolean reduced(Context c) {
        try {
            return MotionLogic.reducedFromScale(Settings.Global.getFloat(c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f));
        } catch (Exception e) {
            return false;
        }
    }

    /** La durata da usare: quella del token, o 80 ms con le animazioni ridotte. */
    public static int dur(Context c, int ms) { return MotionLogic.dur(reduced(c), ms); }

    // ---------- Vibrazioni ----------

    private static final int CONFIRM = 16; // HapticFeedbackConstants.CONFIRM (API 30), non nel jar di compilazione

    /** Hai finito qualcosa: spunta, ultimo urgente. Un tocco breve e netto. */
    public static void conferma(View v) {
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= 30) { if (v.performHapticFeedback(CONFIRM)) return; }
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /** Hai scelto: chip, «Non ora», interruttori, aprire un'app. Un tocco leggerissimo. */
    public static void scatto(View v) {
        if (v == null) return;
        if (!v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    /** Lo stato che cambia lo dice anche TalkBack (mai le durate né le animazioni). */
    public static void say(View v, CharSequence s) {
        if (v != null && s != null) v.announceForAccessibility(s);
    }

    // ---------- Una riga che si chiude ----------

    /**
     * Dissolvenza e altezza a 0 in BASE: le righe sotto salgono. Con le animazioni ridotte, solo una dissolvenza
     * di 80 ms. {@code restore}: alla fine la vista torna com'era (per i contenitori che si riusano); altrimenti resta GONE.
     */
    public static void collapse(final View v, final boolean restore, final Runnable end) {
        if (v == null || v.getParent() == null) { if (end != null) end.run(); return; }
        final ViewGroup.LayoutParams lp = v.getLayoutParams();
        final int h0 = Math.max(v.getHeight(), 1), hOld = lp.height;
        final Runnable finish = new Runnable() {
            public void run() {
                if (restore) { lp.height = hOld; v.setLayoutParams(lp); v.setAlpha(1f); }
                else v.setVisibility(View.GONE);
                if (end != null) end.run();
            }
        };
        if (reduced(v.getContext())) {
            v.animate().alpha(0f).setDuration(MotionLogic.RIDOTTO).setListener(new AnimatorListenerAdapter() {
                public void onAnimationEnd(Animator a) { v.animate().setListener(null); finish.run(); }
            }).start();
            return;
        }
        if (v instanceof android.widget.LinearLayout) v.setMinimumHeight(0);
        lp.height = h0;
        v.setLayoutParams(lp);
        ValueAnimator va = ValueAnimator.ofFloat(1f, 0f);
        va.setDuration(BASE);
        va.setInterpolator(DECELERA);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                float k = (Float) a.getAnimatedValue();
                lp.height = Math.max(0, Math.round(h0 * k));
                v.setLayoutParams(lp);
                v.setAlpha(k);
            }
        });
        va.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator a) { finish.run(); }
        });
        va.start();
    }

    // ---------- «Togliere diventa un meno» ----------

    /** Tutto ciò che se ne va: si comprime in una linea d'inchiostro e la linea si ritira. La vista resta GONE. */
    public static void leave(View v, Runnable end) { leave(v, false, end); }

    /**
     * Evolutiva 016, momento 11. La vista si schiaccia in verticale in una linea d'inchiostro di 2 dp (BASE, DECELERA)
     * mentre sotto la giornata sale; poi la linea si ritira verso il suo bordo sinistro e sparisce (RAPIDO). Totale
     * BASE + RAPIDO. In una colonna verticale la linea è una vista in più, tolta alla fine; una vista che non sta in una
     * colonna (la pillola, che è una finestra sopra le altre app) si schiaccia da sola e nient'altro scorre.
     * {@code restore}: alla fine la vista torna com'era (contenitori che si riusano); altrimenti resta GONE.
     * «Rimuovi animazioni»: dissolvenza di 80 ms.
     */
    public static void leave(final View v, final boolean restore, final Runnable end) {
        if (v == null || v.getParent() == null) { if (end != null) end.run(); return; }
        final Context c = v.getContext();
        if (reduced(c) || v.getHeight() <= 0) {
            v.animate().alpha(0f).setDuration(MotionLogic.RIDOTTO).setListener(new AnimatorListenerAdapter() {
                public void onAnimationEnd(Animator a) {
                    v.animate().setListener(null);
                    if (restore) v.setAlpha(1f); else v.setVisibility(View.GONE);
                    if (end != null) end.run();
                }
            }).start();
            return;
        }
        final int h0 = v.getHeight(), line = Math.max(2, U.dp(c, 2));
        final boolean inColumn = v.getParent() instanceof android.widget.LinearLayout
                && ((android.widget.LinearLayout) v.getParent()).getOrientation() == android.widget.LinearLayout.VERTICAL
                && v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams;
        final ViewGroup.MarginLayoutParams lp = inColumn ? (ViewGroup.MarginLayoutParams) v.getLayoutParams() : null;
        final int mbOld = inColumn ? lp.bottomMargin : 0;
        final android.widget.LinearLayout col = inColumn ? (android.widget.LinearLayout) v.getParent() : null;
        final View bar = new View(c);
        if (inColumn) {
            bar.setBackgroundColor(U.INK);
            bar.setAlpha(0f);
            bar.setPivotX(0f);
            android.widget.LinearLayout.LayoutParams blp = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, line);
            blp.leftMargin = lp.leftMargin + v.getPaddingLeft(); // la linea sta nella colonna del contenuto, non nel riquadro
            blp.rightMargin = lp.rightMargin + v.getPaddingRight();
            col.addView(bar, col.indexOfChild(v) + 1, blp);
            v.setPivotX(0f);
            v.setPivotY(0f);
        } else {
            v.setPivotX(0f);
            v.setPivotY(h0 / 2f);
        }
        final int total = MotionLogic.LEAVE_COMPRIME + MotionLogic.LEAVE_RITIRA;
        final float split = MotionLogic.LEAVE_COMPRIME / (float) total;
        final ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(total);
        va.setInterpolator(new android.view.animation.LinearInterpolator());
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                float x = (Float) a.getAnimatedValue();
                float p = DECELERA.getInterpolation(Math.min(1f, x / split));          // 0..1: la riga si comprime
                float q = x <= split ? 0f : DECELERA.getInterpolation((x - split) / (1f - split)); // 0..1: la linea si ritira
                float k = Math.max(line / (float) h0, 1f - p);                          // altezza vista: da 1 a quella di una linea
                v.setScaleY(inColumn ? 1f - p : k);
                float ca = Math.max(0f, 1f - p * 1.6f);                                  // il contenuto svanisce nella prima parte
                if (inColumn) v.setAlpha(ca);
                else if (v instanceof ViewGroup) { // la pillola: il fondo d'inchiostro resta, è lui la linea; svanisce solo il testo
                    ViewGroup g = (ViewGroup) v;
                    for (int i = 0; i < g.getChildCount(); i++) g.getChildAt(i).setAlpha(ca);
                }
                if (inColumn) {
                    lp.bottomMargin = mbOld - Math.round(h0 * p);
                    v.setLayoutParams(lp);
                    bar.setAlpha(Math.min(1f, p * 2f));
                    bar.setScaleX(1f - q);
                    android.widget.LinearLayout.LayoutParams blp = (android.widget.LinearLayout.LayoutParams) bar.getLayoutParams();
                    blp.bottomMargin = -Math.round(line * q);
                    bar.setLayoutParams(blp);
                } else {
                    v.setScaleX(1f - q);
                }
            }
        });
        va.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator a) {
                if (inColumn) {
                    col.removeView(bar);
                    lp.bottomMargin = mbOld;
                    v.setLayoutParams(lp);
                }
                v.setScaleX(1f);
                v.setScaleY(1f);
                if (restore) v.setAlpha(1f); else v.setVisibility(View.GONE);
                if (end != null) end.run();
            }
        });
        va.start();
    }

    /** Una vista che entra: dissolvenza, e con le animazioni normali una piccola salita (dp). */
    public static void enter(View v, int delayMs, int ms, float riseDp) {
        Context c = v.getContext();
        boolean red = reduced(c);
        v.setAlpha(0f);
        if (!red && riseDp != 0) v.setTranslationY(U.dp(c, riseDp));
        v.animate().alpha(1f).translationY(0f).setStartDelay(red ? 0 : delayMs).setDuration(red ? MotionLogic.RIDOTTO : ms).setInterpolator(DECELERA).start();
    }

    /** Colore che passa da a a b (ARGB), per sfondi e testi di chip e piste. */
    public static ValueAnimator color(int ms, final int from, final int to, final ColorSink sink) {
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(ms);
        va.setInterpolator(DECELERA);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) { sink.set(MotionLogic.mix(from, to, (Float) a.getAnimatedValue())); }
        });
        va.start();
        return va;
    }

    public interface ColorSink { void set(int color); }

    // ---------- Transizioni di schermata (res/anim) ----------

    private static int anim(Context c, String name) {
        return c.getResources().getIdentifier(name, "anim", c.getPackageName());
    }

    /** Applica una coppia di animazioni (entra, esce) alla schermata che si sta aprendo o chiudendo. */
    public static void transition(Activity a, String in, String out) {
        a.overridePendingTransition(anim(a, in), anim(a, out));
    }

    /** La Ricerca entra dal basso: salita e dissolvenza in BASE; la home sotto si dissolve in RAPIDO. */
    public static void openSearch(Activity from) {
        if (reduced(from)) transition(from, "ridotto_in", "ridotto_out"); else transition(from, "cerca_in", "home_out");
    }

    /** La Ricerca esce verso il basso, speculare all'apertura. */
    public static void closeSearch(Activity search) {
        if (reduced(search)) transition(search, "ridotto_in", "ridotto_out"); else transition(search, "home_in", "cerca_out");
    }

    /** La Ricerca lascia il posto a un'app: solo dissolvenza in BASE. */
    public static void fadeOutSearch(Activity search) {
        if (reduced(search)) transition(search, "ridotto_in", "ridotto_out"); else transition(search, "rapido_in", "cerca_out");
    }

    /** «Apri» nella pausa: un taglio rapido e neutro. */
    public static void quickCut(Activity a) {
        if (reduced(a)) transition(a, "ridotto_in", "ridotto_out"); else transition(a, "rapido_in", "rapido_out");
    }

    /** «Non ora»: la home arriva morbida in LENTO sopra la pausa che si schiarisce. */
    public static void softHome(Activity a) {
        if (reduced(a)) transition(a, "ridotto_in", "ridotto_out"); else transition(a, "lento_in", "lento_hold");
    }
}
