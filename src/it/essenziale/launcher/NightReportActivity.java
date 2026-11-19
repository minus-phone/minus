package it.essenziale.launcher;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Fine della prova di «Di notte» (0.10, wireframe 154): solo numeri, senza voti. Poi decidi tu. */
public class NightReportActivity extends BaseActivity {
    /**
     * Le «volte» della settimana scorsa, per far scendere il numero (E016, momento 6). Oggi nessuno le salva: finché
     * non c'è, il numero compare fermo. TODO (E016, fase 2): salvarle in Night.startTrial (il valore di K_UNLOCKS alla
     * chiusura della prova precedente) e il conto parte da solo.
     */
    static final String K_UNLOCKS_PREV = "night_unlocks_prev";

    private static String unlocksLine(int n) { return "Di notte hai preso il telefono " + n + (n == 1 ? " volta" : " volte"); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = U.screen(this, "Rapporto della settimana");
        android.content.SharedPreferences p = U.prefs(this);
        int unlocks = p.getInt(Night.K_UNLOCKS, 0), alts = p.getInt(Night.K_ALTS, 0), opens = p.getInt(Night.K_OPENS, 0), min = p.getInt(Night.K_OPEN_MIN, 0);
        LinearLayout c = U.col(this);
        c.setPadding(U.dp(this, 16), U.dp(this, 12), U.dp(this, 16), U.dp(this, 12));
        c.setBackground(U.box(this, U.SURFACE, U.LINE, 14));
        final TextView l1 = U.text(this, unlocksLine(unlocks), 15, U.INK, false);
        c.addView(l1);
        c.addView(U.text(this, "Di notte hai scelto la tua alternativa " + alts + (alts == 1 ? " volta" : " volte"), 15, U.INK, false));
        c.addView(U.text(this, opens == 0 ? "Non hai aperto app con la pausa" : opens + (opens == 1 ? " volta" : " volte") + " hai aperto un'app con la pausa, in tutto " + min + " min", 15, U.INK, false));
        // 0.20 (Evolutiva 016): le righe entrano una alla volta (80 ms l'una, BASE, dissolvenza e 8 dp in su);
        // se sono meno della settimana scorsa, il primo numero scende davanti a te (600 ms, rallentando)
        boolean red = Motion.reduced(this);
        for (int i = 0; i < c.getChildCount(); i++) Motion.enter(c.getChildAt(i), MotionLogic.rowDelay(red, i), Motion.BASE, 8);
        final int prev = p.getInt(K_UNLOCKS_PREV, -1);
        if (!red && MotionLogic.countDown(prev, unlocks)) countDown(l1, prev, unlocks);
        LinearLayout.LayoutParams clp = U.full();
        clp.topMargin = U.dp(this, 10);
        root.addView(c, clp);
        TextView q = U.text(this, "Solo numeri, senza voti. Vuoi tenerlo?", 14, U.MUTED, false);
        q.setPadding(0, U.dp(this, 12), 0, 0);
        root.addView(q);
        View sp = new View(this);
        root.addView(sp, new LinearLayout.LayoutParams(1, 0, 1f));
        root.addView(big("Tienilo", true, new Runnable() {
            public void run() { close(true); finish(); }
        }), U.full());
        LinearLayout two = U.row(this);
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        a.rightMargin = U.dp(this, 10);
        two.addView(big("Cambia qualcosa", false, new Runnable() {
            public void run() { close(true); startActivity(new Intent(NightReportActivity.this, SettingsActivity.class).putExtra("area", SettingsActivity.SLEEP).putExtra("back", "Rapporto")); finish(); }
        }), a);
        two.addView(big("Spegnilo", false, new Runnable() {
            public void run() {
                // 0.20: «Annulla» prima nasceva dopo finish() e non si vedeva mai: ora compare nella schermata che si apre
                Changes.later(NightReportActivity.this, "Meno interferenze sul sonno spento", new String[]{Night.K_ON, Night.K_TRIAL, Night.K_REPORT_SEEN},
                        new Runnable() { public void run() { close(false); } });
                finish();
            }
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams tlp = U.full();
        tlp.topMargin = U.dp(this, 10);
        tlp.bottomMargin = U.dp(this, 20);
        root.addView(two, tlp);
        setContentView(root);
    }

    /** Il numero scende da {@code from} a {@code to} in 600 ms, rallentando. TalkBack legge subito il valore finale. */
    private void countDown(final TextView t, int from, final int to) {
        t.setContentDescription(unlocksLine(to));
        t.setText(unlocksLine(from));
        final int f = from;
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(0f, 1f);
        va.setStartDelay(Motion.BASE);
        va.setDuration(MotionLogic.CONTO_MS);
        va.setInterpolator(new android.view.animation.LinearInterpolator()); // la curva è in countValue
        va.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(android.animation.ValueAnimator a) {
                t.setText(unlocksLine(MotionLogic.countValue(f, to, (Float) a.getAnimatedValue())));
            }
        });
        va.start();
    }

    private void close(boolean keep) {
        U.prefs(this).edit().putBoolean(Night.K_REPORT_SEEN, true).putLong(Night.K_TRIAL, 0).putBoolean(Night.K_ON, keep).commit();
    }

    private TextView big(String s, boolean filled, final Runnable r) {
        TextView t = U.text(this, s, 15, filled ? U.BG : U.INK, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(U.dp(this, 52));
        if (filled) Press.primary(t, U.INK, 26); else Press.outlined(t, U.BG, U.INK, 26);
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        return t;
    }
}
