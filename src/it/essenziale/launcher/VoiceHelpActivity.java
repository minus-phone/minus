package it.essenziale.launcher;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** "Cosa posso dire": gli esempi dei comandi vocali, come nei wireframe. */
public class VoiceHelpActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = U.screen(this, "Cosa posso dire");
        ScrollView sv = new ScrollView(this);
        LinearLayout c = U.col(this);
        sv.addView(c);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        block(c, "Messaggi", "“Scrivi a [persona] su WhatsApp: [testo]”", "Anche Telegram e SMS. Solo le persone che hai scelto. Il messaggio si apre pronto: Invia lo tocchi tu.");
        block(c, "Chiamate", "“Chiama [persona]”", null);
        block(c, "Impegni", "“Aggiungi [cosa] [giorno] alle [ora]”\n“Sposta [impegno] a [giorno/ora]”\n“Cancella [impegno] di [giorno]”",
                "Per gli impegni che si ripetono, a voce cambio solo quella volta.");
        block(c, "Task", "“Aggiungi task [cosa] entro [giorno]”\n“Ricordami di [cosa]”\n“Segna fatto [task]”", null);
        block(c, "App", "“Apri [app]”", "La pausa resta attiva.");
        block(c, "Mappe", "“Portami in [indirizzo]”\n“Cerca [luogo] su Maps”", null);
        TextView rest = U.text(this, "Tutto il resto: “Chiedi a Gemini”. Si apre fuori da minus.", 15, U.INK, false);
        rest.setPadding(0, U.dp(this, 18), 0, U.dp(this, 6));
        c.addView(rest);
        TextView n = U.text(this, "Usa il riconoscimento vocale di Android. Puoi anche scrivere il comando nella barra in basso.", 13, U.MUTED, false);
        n.setPadding(0, 0, 0, U.dp(this, 20));
        c.addView(n);
        setContentView(root);
    }

    private void block(LinearLayout c, String title, String says, String note) {
        c.addView(U.label(this, title));
        c.addView(U.text(this, says, 16, U.INK, false));
        if (note != null) {
            TextView t = U.text(this, note, 13, U.MUTED, false);
            t.setPadding(0, U.dp(this, 4), 0, 0);
            c.addView(t);
        }
    }
}
