package it.essenziale.launcher;

import android.app.Activity;
import android.os.Bundle;

/** Base comune: colori della barra di stato e barra di navigazione nascosta. */
public class BaseActivity extends Activity {
    private int stamp;

    /** Il tema è stato cambiato da questa pagina, che si ridisegna da sola (0.10). */
    void syncTheme() { stamp = U.themeStamp; }

    @Override
    protected void onCreate(Bundle b) {
        U.applyTheme(this); // 0.10: tema e testo scelti in Aspetto
        stamp = U.themeStamp;
        // campi di testo e finestre di Android seguono il tema scelto
        setTheme(U.dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(b);
        getWindow().setStatusBarColor(U.BG);
        getWindow().setNavigationBarColor(U.BG);
        U.immersive(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Pill.resumed(this); // 0.9: mai sopra minus
        U.applyTheme(this);
        if (stamp != U.themeStamp) { recreate(); return; } // il tema è cambiato altrove
        U.immersive(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Pill.pausedActivity(this);
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        U.smoothInsets(this); // 0.13.4: anche dopo un nuovo setContentView
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) U.immersive(this);
    }
}
