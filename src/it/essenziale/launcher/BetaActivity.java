package it.essenziale.launcher;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Registro per la beta (0.15). Spento di default: si accende solo per chi partecipa alla beta.
 * Mostra cosa conta e cosa non conta mai, gli ultimi giorni registrati, e permette di inviarlo
 * (condivisione di Android: si vede tutto il testo prima di mandarlo) o di cancellarlo.
 */
public class BetaActivity extends BaseActivity {

    private LinearLayout box;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = U.screen(this, "Registro per la beta");
        ScrollView sv = new ScrollView(this);
        box = U.col(this);
        sv.addView(box);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        box.removeAllViews();
        final boolean on = Beta.on(this);
        box.addView(U.text(this, "Serve solo a chi partecipa alla beta di −minus. Conta ogni giorno alcuni numeri, per capire cosa funziona e cosa no. "
                + "Resta sul telefono finché non lo invii tu.", 15, U.INK, false));

        LinearLayout r = U.row(this);
        r.setMinimumHeight(U.dp(this, 64));
        LinearLayout tx = U.col(this);
        tx.addView(U.text(this, "Registro per la beta", 16, U.INK, false));
        tx.addView(U.text(this, on ? sinceText() : "Spento · non conta niente", 13, U.MUTED, false));
        r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(U.toggle(this, on), new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 26)));
        r.setContentDescription("Registro per la beta, " + (on ? "acceso" : "spento"));
        r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (on) askOff(); else { Beta.setOn(BetaActivity.this, true); render(); } } });
        LinearLayout.LayoutParams rlp = U.full();
        rlp.topMargin = U.dp(this, 8);
        box.addView(r, rlp);
        box.addView(U.divider(this));

        if (on) {
            label("Il tuo codice");
            box.addView(U.text(this, Beta.code(this), 22, U.INK, true));
            box.addView(U.text(this, "Ti riconosce nella beta senza il tuo nome. Usalo nel diario e nelle interviste.", 13, U.MUTED, false));
        }

        label("Cosa conta, ogni giorno");
        for (int i = 0; i < Registro.LABELS.length; i++) bullet(Registro.LABELS[i]);
        label("Cosa non conta mai");
        bullet("Nomi di persone, di gruppi e di app");
        bullet("Il testo di messaggi e notifiche");
        bullet("Dove sei, e cosa scrivi nella ricerca");
        bullet("Per cosa apri un'app: conta solo quante volte");

        if (on) {
            label("Ultimi giorni");
            java.util.List<String[]> rows = Registro.recent(Beta.log(this), 7);
            if (rows.isEmpty()) box.addView(U.text(this, "Ancora niente: i numeri arrivano usando il telefono.", 14, U.MUTED, false));
            for (String[] d : rows) {
                LinearLayout dr = U.col(this);
                dr.setPadding(0, U.dp(this, 6), 0, U.dp(this, 6));
                dr.addView(U.text(this, d[0], 15, U.INK, true));
                dr.addView(U.text(this, d[1], 13, U.MUTED, false));
                box.addView(dr, U.full());
            }
            TextView send = button("Invia il registro", true);
            send.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { send(); } });
            LinearLayout.LayoutParams lp = U.full();
            lp.topMargin = U.dp(this, 18);
            box.addView(send, lp);
            TextView del = button("Cancella il registro", false);
            del.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { askClear(); } });
            LinearLayout.LayoutParams lp2 = U.full();
            lp2.topMargin = U.dp(this, 10);
            box.addView(del, lp2);
            box.addView(U.text(this, "Si tengono al massimo gli ultimi " + Registro.KEEP_DAYS + " giorni. Spegnendo il registro, si cancella.", 12, U.MUTED, false), padTop());
        }
        View end = new View(this);
        box.addView(end, new LinearLayout.LayoutParams(1, U.dp(this, 60)));
    }

    private String sinceText() {
        int n = Registro.days(Beta.log(this));
        long s = Beta.since(this);
        String from = s > 0 ? " dal " + new java.text.SimpleDateFormat("d MMMM", java.util.Locale.ITALY).format(new java.util.Date(s)) : "";
        return "Acceso" + from + " · " + n + (n == 1 ? " giorno" : " giorni");
    }

    private void send() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "−minus · registro per la beta · " + Beta.code(this));
        i.putExtra(Intent.EXTRA_TEXT, Beta.export(this));
        startActivity(Intent.createChooser(i, "Invia il registro con…"));
    }

    private void askOff() {
        new AlertDialog.Builder(this).setTitle("Spegnere il registro?")
                .setMessage("I numeri registrati finora si cancellano dal telefono. Quelli che hai già inviato restano a chi li ha ricevuti.")
                .setPositiveButton("Spegni e cancella", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { Beta.setOn(BetaActivity.this, false); render(); }
                }).setNegativeButton("Annulla", null).show();
    }

    private void askClear() {
        new AlertDialog.Builder(this).setTitle("Cancellare il registro?")
                .setMessage("Il registro riparte da oggi e resta acceso. Quello che hai già inviato non cambia.")
                .setPositiveButton("Cancella", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { Beta.clear(BetaActivity.this); render(); }
                }).setNegativeButton("Annulla", null).show();
    }

    private void label(String s) {
        TextView t = U.label(this, s);
        box.addView(t, padTop());
    }

    private void bullet(String s) {
        TextView t = U.text(this, "·  " + s, 14, U.INK, false);
        t.setPadding(0, U.dp(this, 3), 0, U.dp(this, 3));
        box.addView(t, U.full());
    }

    private LinearLayout.LayoutParams padTop() {
        LinearLayout.LayoutParams lp = U.full();
        lp.topMargin = U.dp(this, 18);
        return lp;
    }

    private TextView button(String s, boolean primary) {
        TextView t = U.text(this, s, 16, primary ? U.BG : U.INK, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(U.dp(this, 52));
        t.setBackground(primary ? U.box(this, U.INK, 0, 26) : U.box(this, U.BG, U.LINE, 26));
        return t;
    }
}
