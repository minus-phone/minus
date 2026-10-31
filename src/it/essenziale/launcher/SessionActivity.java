package it.essenziale.launcher;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Impostazioni del tempo dall'ultimo sblocco: un interruttore e la soglia. */
public class SessionActivity extends BaseActivity {

    private LinearLayout body;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = U.screen(this, "Tempo dall'ultimo sblocco");
        TextView intro = U.text(this, "Quando il telefono resta acceso a lungo senza pause, minus te lo fa notare. Non conta il tempo per app, ma la sessione intera.", 14, U.MUTED, false);
        intro.setPadding(0, U.dp(this, 4), 0, U.dp(this, 6));
        root.addView(intro);
        body = U.col(this);
        root.addView(body, U.full());
        setContentView(root);
        render();
    }

    private void render() {
        body.removeAllViews();
        final boolean on = Session.on(this);
        LinearLayout r = U.row(this);
        r.setMinimumHeight(U.dp(this, 54));
        r.addView(U.text(this, "Attivo", 16, U.INK, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(toggle(on), new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 26)));
        r.setContentDescription(on ? "Tempo dall'ultimo sblocco attivo" : "Tempo dall'ultimo sblocco spento");
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Changes.track(SessionActivity.this, on ? "Tempo dall'ultimo sblocco spento" : "Tempo dall'ultimo sblocco attivo", new String[]{Session.K_ON},
                        new Runnable() { public void run() { U.prefs(SessionActivity.this).edit().putBoolean(Session.K_ON, !on).commit(); } },
                        new Runnable() { public void run() { render(); } });
            }
        });
        body.addView(r, U.full());
        body.addView(U.divider(this));
        if (on) {
            body.addView(U.label(this, "Fammelo notare dopo"));
            LinearLayout chips = U.row(this);
            int cur = U.prefs(this).getInt(Session.K_MIN, 10);
            for (final int m : Session.CHOICES) {
                boolean sel = m == cur;
                TextView t = U.text(this, m + " min", 14, sel ? U.BG : U.INK, false);
                t.setGravity(Gravity.CENTER);
                t.setMinHeight(U.dp(this, 44));
                t.setPadding(U.dp(this, 14), 0, U.dp(this, 14), 0);
                t.setBackground(sel ? U.box(this, U.INK, 0, 20) : U.box(this, U.BG, U.LINE, 20));
                t.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Changes.track(SessionActivity.this, "Tempo dall'ultimo sblocco: dopo " + m + " min", new String[]{Session.K_MIN},
                                new Runnable() { public void run() { U.prefs(SessionActivity.this).edit().putInt(Session.K_MIN, m).commit(); } },
                                new Runnable() { public void run() { render(); } });
                    }
                });
                LinearLayout.LayoutParams lp = U.wrap();
                lp.rightMargin = U.dp(this, 6);
                chips.addView(t, lp);
            }
            body.addView(chips, U.full());
        }
        TextView n = U.text(this, "Si azzera quando spegni lo schermo. Non compare mentre sei in chiamata o usi la navigazione. Serve il filtro notifiche attivo.", 13, U.MUTED, false);
        n.setPadding(0, U.dp(this, 14), 0, U.dp(this, 6));
        body.addView(n);
        body.addView(U.divider(this));
        TextView dw = U.text(this, "Pillola «App usata per…» di Benessere digitale  ›", 15, U.INK, false);
        dw.setMinHeight(U.dp(this, 52));
        dw.setGravity(Gravity.CENTER_VERTICAL);
        dw.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Session.openWellbeing(SessionActivity.this); }
        });
        body.addView(dw, U.full());
        TextView dn = U.text(this, "Conta il tempo per app, anche la home come se fosse minus. minus non può spegnerla: si disattiva da Benessere digitale.", 13, U.MUTED, false);
        body.addView(dn);
    }

    View toggle(boolean on) {
        LinearLayout sw = U.row(this);
        sw.setGravity(on ? Gravity.END | Gravity.CENTER_VERTICAL : Gravity.START | Gravity.CENTER_VERTICAL);
        sw.setPadding(U.dp(this, 3), U.dp(this, 3), U.dp(this, 3), U.dp(this, 3));
        sw.setBackground(U.box(this, on ? U.INK : U.BORDER, 0, 13));
        View knob = new View(this);
        knob.setBackground(U.box(this, U.SURFACE, 0, 10));
        sw.addView(knob, new LinearLayout.LayoutParams(U.dp(this, 20), U.dp(this, 20)));
        return sw;
    }
}
