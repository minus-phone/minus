package it.essenziale.launcher;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * La tua alternativa, di notte (0.10): lo schermo diventa nero e al minimo della luminosità,
 * con la tua frase che sfuma. Un tocco torna indietro; altrimenti lo schermo si spegne da solo.
 */
public class DimActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 0.01f;
        getWindow().setAttributes(lp);
        final TextView t = U.text(this, Night.alternative(this), 18, 0xFF757571, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(U.dp(this, 40), 0, U.dp(this, 40), 0);
        t.setBackgroundColor(Color.BLACK);
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        setContentView(t);
        new Handler().postDelayed(new Runnable() {
            public void run() { t.animate().alpha(0f).setDuration(4000).start(); }
        }, 3000);
    }

    @Override
    protected void onResume() { super.onResume(); Pill.resumed(this); }

    @Override
    protected void onPause() { super.onPause(); Pill.pausedActivity(this); }
}
