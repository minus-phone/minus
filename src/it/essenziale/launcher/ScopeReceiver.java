package it.essenziale.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Le azioni degli avvisi di pausa e scopo: «Altri 5 min» e «Ok». «Chiudo» passa dalla home. */
public class ScopeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (Scope.ACT_MORE.equals(a)) Scope.extend(c);
        else if (Scope.ACT_OK.equals(a)) Scope.cancelAlert(c);
        else if (Sera.ACT_OK.equals(a)) Sera.ack(c);       // E002: «Va bene»
        else if (Sera.ACT_MORE.equals(a)) Sera.snooze(c);  // E002: «Ancora 15 min»
    }
}
