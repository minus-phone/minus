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
