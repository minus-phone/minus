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
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = U.screen(this, "Una settimana di «Di notte»");
        android.content.SharedPreferences p = U.prefs(this);
        int unlocks = p.getInt(Night.K_UNLOCKS, 0), alts = p.getInt(Night.K_ALTS, 0), opens = p.getInt(Night.K_OPENS, 0), min = p.getInt(Night.K_OPEN_MIN, 0);
        LinearLayout c = U.col(this);
        c.setPadding(U.dp(this, 16), U.dp(this, 12), U.dp(this, 16), U.dp(this, 12));
        c.setBackground(U.box(this, U.SURFACE, U.LINE, 14));
        c.addView(U.text(this, "Di notte hai preso il telefono " + unlocks + (unlocks == 1 ? " volta" : " volte"), 15, U.INK, false));
        c.addView(U.text(this, alts + (alts == 1 ? " volta" : " volte") + " hai scelto la tua alternativa", 15, U.INK, false));
        c.addView(U.text(this, opens == 0 ? "Non hai aperto app con la pausa" : opens + (opens == 1 ? " volta" : " volte") + " hai aperto un'app con la pausa, in tutto " + min + " min", 15, U.INK, false));
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
            public void run() { close(true); startActivity(new Intent(NightReportActivity.this, NightSettingsActivity.class)); finish(); }
        }), a);
        two.addView(big("Spegnilo", false, new Runnable() {
            public void run() {
                Changes.track(NightReportActivity.this, "«Di notte» spento", new String[]{Night.K_ON, Night.K_TRIAL, Night.K_REPORT_SEEN},
                        new Runnable() { public void run() { close(false); } },
                        new Runnable() { public void run() { finish(); } });
            }
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams tlp = U.full();
        tlp.topMargin = U.dp(this, 10);
        tlp.bottomMargin = U.dp(this, 20);
        root.addView(two, tlp);
        setContentView(root);
    }

    private void close(boolean keep) {
        U.prefs(this).edit().putBoolean(Night.K_REPORT_SEEN, true).putLong(Night.K_TRIAL, 0).putBoolean(Night.K_ON, keep).commit();
    }

    private TextView big(String s, boolean filled, final Runnable r) {
        TextView t = U.text(this, s, 15, filled ? U.BG : U.INK, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(U.dp(this, 52));
        t.setBackground(filled ? U.box(this, U.INK, 0, 26) : U.box(this, U.BG, U.INK, 26));
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        return t;
    }
}
