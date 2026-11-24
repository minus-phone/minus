package it.essenziale.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.StyleSpan;
import android.text.style.UpdateAppearance;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Evolutiva 016, momenti 1 e 2 · la spunta di un task in home. La casella si riempie in senso orario (RAPIDO, ease-in-out),
 * poi la spunta con il rimbalzo e la vibrazione «conferma» (BASE); in quel momento anche il testo e il «!» rosso (di ogni
 * urgente) passano al grigio dei completati. La riga resta spuntata un secondo (un secondo tocco la riapre, senza
 * vibrazione), poi si dissolve e si chiude (BASE) e le altre salgono. Se non resta più niente da fare compare la festa
 * «Tutto fatto per oggi»; se erano gli ultimi urgenti, «Urgenti fatti» (un grande check in un cerchio, sullo sfondo).
 * TalkBack: «<titolo>, fatto». Il dato si salva subito: se esci a metà, il task è fatto. Senza animazioni: tutto
 * diretto, la riga se ne va con una dissolvenza di 80 ms.
 */
final class TaskRows {
    private TaskRows() {}

    /**
     * 0.27 (Evolutiva 023): un task con una nota ha in fondo alla riga (dopo il link all'app) una piccola icona «nota»
     * (18 dp, U.MUTED, 10 dp a sinistra); TalkBack: «, ha una nota». Senza nota non fa niente.
     */
    static void addNoteIcon(LinearLayout row, Tasks.Task t) {
        if (!hasNote(t)) return;
        Context c = row.getContext();
        android.widget.ImageView ic = Ic.view(c, "ic_nota", U.MUTED, 18);
        if (ic == null) return;
        ic.setPadding(U.dp(c, 10), 0, 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(U.dp(c, 28), U.dp(c, 18));
        lp.gravity = android.view.Gravity.CENTER_VERTICAL;
        row.addView(ic, lp);
        CharSequence d = row.getContentDescription();
        row.setContentDescription((d == null ? t.title : d) + noteSpoken());
    }

    static boolean hasNote(Tasks.Task t) { return t != null && Urgenti.hasNote(t.note); }

    static String noteSpoken() { return ", ha una nota"; }

    static final int IDLE = 0, CHECKED = 1, GONE = 2;

    /** Chi salva i dati e ridisegna la pagina quando tutte le righe hanno finito. */
    interface Host {
        void setDone(String id, boolean done);
        void gone(String id);          // la riga si è chiusa: da qui in poi non si ridisegna fra i da fare
        void reopened(String id);
        void busy(int delta);          // +1 quando una riga inizia, -1 quando finisce
        void festa(int kind, View row); // MotionLogic.FESTA_TUTTO / FESTA_URGENTI: l'ultimo task, o l'ultimo urgente, di quella colonna
    }

    /** Il «!» davanti al titolo: un colore che si può far svanire senza rifare il testo. */
    static final class BangSpan extends CharacterStyle implements UpdateAppearance {
        int color;
        BangSpan(int color) { this.color = color; }
        @Override public void updateDrawState(TextPaint tp) { tp.setColor(color); }
    }

    static final class State {
        String id, title;
        boolean urgent;
        int phase;
        TaskBox box;
        TextView titleView;
        BangSpan bang;
        View row;
        Runnable confirm, collapse;
        ValueAnimator titleAnim, bangAnim;
    }

    /** «!  Titolo» con il segno rosso e in grassetto (come U.bang), ma con il colore animabile. */
    static CharSequence bangTitle(String title, BangSpan b) {
        SpannableString s = new SpannableString("!  " + title);
        s.setSpan(b, 0, 1, 0);
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, 1, 0);
        return s;
    }

    static void toggle(State s, Host h) {
        if (s.phase == GONE) return;
        if (s.phase == CHECKED) uncheck(s, h); else check(s, h);
    }

    /** Quanti altri task della stessa colonna restano da fare (solo gli urgenti se {@code urgentOnly}). */
    private static int othersToDo(State me, boolean urgentOnly) {
        if (!(me.row.getParent() instanceof ViewGroup)) return 0;
        ViewGroup p = (ViewGroup) me.row.getParent();
        int n = 0;
        for (int i = 0; i < p.getChildCount(); i++) {
            Object t = p.getChildAt(i).getTag(U.R.id_task);
            if (t instanceof State && t != me && ((State) t).phase == IDLE && (!urgentOnly || ((State) t).urgent)) n++;
        }
        return n;
    }

    private static void check(final State s, final Host h) {
        final Context c = s.row.getContext();
        final boolean red = Motion.reduced(c);
        s.phase = CHECKED;
        h.setDone(s.id, true);
        h.busy(1);
        s.box.fillTo(1f, red ? 0 : Motion.RAPIDO);
        s.confirm = new Runnable() {
            public void run() {
                s.box.confirm(!red);
                Motion.conferma(s.box);
                Motion.say(s.row, s.title + ", fatto");
                tintTitle(s, U.BORDER);
                if (s.bang != null) fadeBang(s, false); // il «!» di ogni urgente passa al grigio dei completati, insieme al testo
                int kind = MotionLogic.festa(s.urgent, othersToDo(s, true), othersToDo(s, false));
                if (kind != MotionLogic.FESTA_NESSUNA) h.festa(kind, s.row);
                s.collapse = new Runnable() {
                    public void run() {
                        s.phase = GONE;
                        s.row.setClickable(false);
                        s.row.setTag(U.R.id_task, s);
                        h.gone(s.id);
                        Motion.collapse(s.row, false, new Runnable() { public void run() { h.busy(-1); } });
                    }
                };
                s.row.postDelayed(s.collapse, MotionLogic.SPUNTA_RESTA);
            }
        };
        s.row.postDelayed(s.confirm, Motion.dur(c, Motion.BASE));
    }

    private static void uncheck(State s, Host h) {
        s.row.removeCallbacks(s.confirm);
        if (s.collapse != null) s.row.removeCallbacks(s.collapse);
        s.phase = IDLE;
        h.setDone(s.id, false);
        h.reopened(s.id);
        h.busy(-1);
        s.box.fillTo(0f, Motion.reduced(s.row.getContext()) ? 0 : Motion.RAPIDO);
        if (s.bang != null) fadeBang(s, true);
        tintTitle(s, U.INK);
        Motion.say(s.row, s.title + ", da fare"); // niente vibrazione: riaprire è tornare indietro
    }

    private static void tintTitle(final State s, int to) {
        if (s.titleAnim != null) s.titleAnim.cancel();
        if (Motion.reduced(s.row.getContext())) { s.titleView.setTextColor(to); return; }
        s.titleAnim = Motion.color(Motion.BASE, s.titleView.getCurrentTextColor(), to, new Motion.ColorSink() {
            public void set(int c) { s.titleView.setTextColor(c); }
        });
    }

    /** Il «!» rosso diventa grigio come il testo (RAPIDO, lineare, come nel prototipo) o ritorna rosso se la riga si riapre. */
    private static void fadeBang(final State s, boolean back) {
        if (s.bangAnim != null) s.bangAnim.cancel();
        final int to = back ? U.URGENT : U.BORDER;
        if (Motion.reduced(s.row.getContext())) { s.bang.color = to; s.titleView.invalidate(); return; }
        s.bangAnim = Motion.color(Motion.RAPIDO, s.bang.color, to, new android.view.animation.LinearInterpolator(), new Motion.ColorSink() {
            public void set(int c) { s.bang.color = c; s.titleView.invalidate(); }
        });
    }
}
