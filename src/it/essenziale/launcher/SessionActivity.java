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
        LinearLayout root = U.screen(this, "Tempo nelle app che rapiscono");
        TextView intro = U.text(this, "Conta solo il tempo nelle app che rapiscono, sommato da quando sblocchi il telefono. Nelle altre app e in home si ferma.", 14, U.MUTED, false);
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
        r.addView(U.toggle(this, on, "session_on"), new LinearLayout.LayoutParams(U.dp(this, 48), U.dp(this, 28))); // 0.21: l'interruttore con il pallino che scorre (momento 9)
        Press.row(r); // riga con interruttore: si scurisce solo il fondo
        r.setContentDescription(on ? "Tempo nelle app che rapiscono attivo" : "Tempo nelle app che rapiscono spento");
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Changes.track(SessionActivity.this, on ? "Tempo nelle app che rapiscono spento" : "Tempo nelle app che rapiscono attivo", new String[]{Session.K_ON},
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
                if (sel) Press.chipOn(t, 20); else Press.chip(t, U.BG, U.LINE, 20);
                t.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Changes.track(SessionActivity.this, "Tempo nelle app che rapiscono: dopo " + m + " min", new String[]{Session.K_MIN},
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
        TextView n = U.text(this, "Si azzera quando spegni lo schermo. Non compare mentre sei in chiamata o usi la navigazione. Servono il filtro notifiche e l'accesso ai dati di utilizzo.", 12, U.MUTED, false);
        n.setPadding(0, U.dp(this, 14), 0, U.dp(this, 6));
        body.addView(n);
        body.addView(U.divider(this));
        TextView dw = U.text(this, "Pillola «App usata per…» di Benessere digitale  ›", 15, U.INK, false);
        dw.setMinHeight(U.dp(this, 52));
        dw.setGravity(Gravity.CENTER_VERTICAL);
        Press.row(dw); // 0.21: riga senza bordo
        dw.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Session.openWellbeing(SessionActivity.this); }
        });
        body.addView(dw, U.full());
        TextView dn = U.text(this, "Conta il tempo per app, anche la home come se fosse −minus. −minus non può spegnerla: si disattiva da Benessere digitale.", 12, U.MUTED, false);
        body.addView(dn);
    }
}
