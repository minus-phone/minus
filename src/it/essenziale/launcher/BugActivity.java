package it.essenziale.launcher;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Segnala un problema (0.10, wireframe 160). Si invia con la condivisione di Android (mail, chat…):
 * vedi tutto il testo prima di mandarlo. Il registro di «Cosa vede Essenziale» è facoltativo e non
 * contiene mai il testo dei messaggi.
 */
public class BugActivity extends BaseActivity {

    static final String[] KINDS = {"Una notifica non arriva", "Un'app non si apre", "Qualcosa è lento", "Altro"};
    private String kind = KINDS[0];
    private boolean attach = false;
    private LinearLayout box;
    private EditText text;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = U.screen(this, "Segnala un problema");
        ScrollView sv = new ScrollView(this);
        box = U.col(this);
        sv.addView(box);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView send = U.text(this, "Invia", 16, U.BG, true);
        send.setGravity(Gravity.CENTER);
        send.setMinHeight(U.dp(this, 52));
        send.setBackground(U.box(this, U.INK, 0, 26));
        send.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { send(); } });
        LinearLayout.LayoutParams lp = U.full();
        lp.bottomMargin = U.dp(this, 16);
        root.addView(send, lp);
        text = new EditText(this);
        text.setHint("Racconta cosa è successo e cosa ti aspettavi…");
        text.setMinLines(4);
        text.setGravity(Gravity.TOP);
        text.setTextColor(U.INK);
        text.setHintTextColor(U.HINT);
        text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        text.setBackground(U.box(this, U.SURFACE, U.BORDER, 12));
        text.setPadding(U.dp(this, 14), U.dp(this, 12), U.dp(this, 14), U.dp(this, 12));
        String pre = getIntent().getStringExtra("text");
        if (pre != null) text.setText(pre);
        String k = getIntent().getStringExtra("kind");
        if (k != null) for (String x : KINDS) if (x.equals(k)) kind = x;
        setContentView(root);
        render();
    }

    private void render() {
        box.removeAllViews();
        box.addView(U.label(this, "Cosa succede?"));
        LinearLayout row = null;
        int used = 0;
        for (final String k : KINDS) {
            if (row == null || used + k.length() > 30) {
                row = U.row(this);
                LinearLayout.LayoutParams lp = U.full();
                lp.topMargin = U.dp(this, 6);
                box.addView(row, lp);
                used = 0;
            }
            used += k.length();
            boolean on = k.equals(kind);
            TextView t = U.text(this, k, 13, on ? U.BG : U.INK, false);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(U.dp(this, 44));
            t.setPadding(U.dp(this, 12), 0, U.dp(this, 12), 0);
            t.setBackground(on ? U.box(this, U.INK, 0, 19) : U.box(this, U.BG, U.BORDER, 19));
            t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { kind = k; render(); } });
            LinearLayout.LayoutParams lp = U.wrap();
            lp.rightMargin = U.dp(this, 6);
            row.addView(t, lp);
        }
        if (text.getParent() != null) ((ViewGroup) text.getParent()).removeView(text);
        LinearLayout.LayoutParams tlp = U.full();
        tlp.topMargin = U.dp(this, 12);
        box.addView(text, tlp);
        LinearLayout r = U.row(this);
        r.setMinimumHeight(U.dp(this, 64));
        LinearLayout tx = U.col(this);
        tx.addView(U.text(this, "Allega «Cosa vede −minus»", 15, U.INK, false));
        tx.addView(U.text(this, "Ultimi 2 giorni: app, nomi e livello. Mai il testo dei messaggi", 12, U.MUTED, false));
        r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(U.toggle(this, attach), new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 26)));
        r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { attach = !attach; render(); } });
        box.addView(r, U.full());
        box.addView(U.text(this, "Si aggiungono da soli: " + info(), 12, U.MUTED, false));
    }

    private String info() {
        String v = "?";
        try { v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
        android.hardware.SensorManager sm = (android.hardware.SensorManager) getSystemService(SENSOR_SERVICE);
        boolean baro = sm != null && sm.getDefaultSensor(android.hardware.Sensor.TYPE_PRESSURE) != null;
        return "versione " + v + " · Android " + Build.VERSION.RELEASE + " · " + Build.MANUFACTURER + " " + Build.MODEL + " · barometro " + (baro ? "sì" : "no");
    }

    private void send() {
        StringBuilder b = new StringBuilder();
        b.append("Problema: ").append(kind).append("\n\n").append(text.getText().toString().trim()).append("\n\n").append(info()).append("\n");
        if (attach) {
            String log = U.prefs(this).getString("seen_log", "");
            long since = System.currentTimeMillis() - 2L * 24 * 3600000L;
            b.append("\n--- Cosa vede −minus (ultimi 2 giorni) ---\n");
            for (String line : log.split("\n")) {
                int bar = line.indexOf('|');
                try { if (bar > 0 && Long.parseLong(line.substring(0, bar).trim()) < since) continue; } catch (Exception ignored) {}
                if (line.trim().length() > 0) b.append(line).append("\n");
            }
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "−minus · " + kind);
        i.putExtra(Intent.EXTRA_TEXT, b.toString());
        startActivity(Intent.createChooser(i, "Invia la segnalazione con…"));
    }
}
