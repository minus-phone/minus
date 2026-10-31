package it.essenziale.launcher;

import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;

/**
 * Sblocco di notte (0.10, wireframe 152): dopo una lunga pausa e prima della sveglia, solo
 * l'essenziale. La tua alternativa, torcia, chiamata, le notifiche per te; la home resta a un tocco.
 */
public class NightActivity extends BaseActivity {

    private boolean torch = false;
    private TextView torchBtn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = U.col(this);
        root.setBackgroundColor(U.BG);
        root.setPadding(U.dp(this, 22), U.dp(this, 40), U.dp(this, 22), U.dp(this, 20));
        Calendar k = Calendar.getInstance();
        TextView clock = U.text(this, String.format(java.util.Locale.ITALY, "%d:%02d", k.get(Calendar.HOUR_OF_DAY), k.get(Calendar.MINUTE)), 48, U.INK, false);
        clock.setTypeface(android.graphics.Typeface.MONOSPACE);
        clock.setGravity(Gravity.CENTER);
        root.addView(clock, U.full());
        String al = U.nextAlarm(this);
        TextView alarm = U.text(this, al != null ? "Sveglia " + al : "Nessuna sveglia", 14, U.MUTED, false);
        alarm.setGravity(Gravity.CENTER);
        root.addView(alarm, U.full());
        TextView q = U.text(this, "Cosa ti serve adesso?", 20, U.INK, true);
        q.setPadding(0, U.dp(this, 30), 0, U.dp(this, 4));
        root.addView(q);
        root.addView(U.label(this, "La tua alternativa"));
        TextView alt = U.text(this, Night.alternative(this), 16, U.INK, false);
        alt.setMinHeight(U.dp(this, 58));
        alt.setGravity(Gravity.CENTER_VERTICAL);
        alt.setPadding(U.dp(this, 16), 0, U.dp(this, 16), 0);
        alt.setBackground(U.box(this, U.SURFACE, U.INK, 14));
        alt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Night.count(NightActivity.this, Night.K_ALTS, 1);
                startActivity(new Intent(NightActivity.this, DimActivity.class));
                finish();
            }
        });
        root.addView(alt, U.full());
        LinearLayout two = U.row(this);
        torchBtn = btn("Torcia", new Runnable() { public void run() { toggleTorch(); } });
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        a.rightMargin = U.dp(this, 10);
        two.addView(torchBtn, a);
        two.addView(btn("Chiamata", new Runnable() {
            public void run() { U.start(NightActivity.this, new Intent(Intent.ACTION_DIAL)); }
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams tlp = U.full();
        tlp.topMargin = U.dp(this, 10);
        root.addView(two, tlp);
        int n = U.listenerEnabled(this) ? Store.count(this, Store.WAITING) : 0;
        if (n > 0) root.addView(link(n + " per te in Notifiche ›", new Runnable() {
            public void run() { startActivity(new Intent(NightActivity.this, InboxActivity.class)); finish(); }
        }));
        root.addView(link("Apri la home ›", new Runnable() { public void run() { finish(); } }));
        View sp = new View(this);
        root.addView(sp, new LinearLayout.LayoutParams(1, 0, 1f));
        setContentView(root);
    }

    private TextView btn(String s, final Runnable r) {
        TextView t = U.text(this, s, 15, U.INK, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(U.dp(this, 52));
        t.setBackground(U.box(this, U.BG, U.INK, 26));
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        return t;
    }

    private TextView link(String s, final Runnable r) {
        TextView t = U.text(this, s, 15, U.INK, false);
        t.setMinHeight(U.dp(this, 48));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        return t;
    }

    private void toggleTorch() {
        try {
            CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                Boolean f = cm.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (f != null && f) { torch = !torch; cm.setTorchMode(id, torch); break; }
            }
            torchBtn.setText(torch ? "Spegni la torcia" : "Torcia");
        } catch (Exception e) {
            U.toast(this, "Torcia non disponibile");
        }
    }

    @Override
    protected void onDestroy() {
        if (torch) toggleTorch();
        super.onDestroy();
    }
}
