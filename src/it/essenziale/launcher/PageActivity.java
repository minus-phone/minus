package it.essenziale.launcher;

import android.content.Intent;
import android.os.Bundle;

/**
 * 0.21 · base delle schermate secondarie (Impostazioni e sotto-pagine, Settimana, Aggiungi, liste, primo avvio): il passaggio
 * da una pagina all'altra, in avanti e indietro, è una dissolvenza in RAPIDO (Motion.quickCut; 80 ms con «Rimuovi
 * animazioni»). Vale solo fra schermate di −minus: verso un'app o le impostazioni di Android resta la transizione di sistema.
 */
public class PageActivity extends BaseActivity {

    @Override
    public void startActivityForResult(Intent i, int code, Bundle options) {
        super.startActivityForResult(i, code, options);
        if (i != null && i.getComponent() != null && getPackageName().equals(i.getComponent().getPackageName())) Motion.quickCut(this);
    }

    @Override
    public void finish() {
        super.finish();
        Motion.quickCut(this);
    }
}
