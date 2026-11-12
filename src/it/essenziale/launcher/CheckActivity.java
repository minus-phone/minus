package it.essenziale.launcher;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
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
 * Un controllo (0.15, wireframe 212–216): i numeri degli obiettivi accesi, «Ti sta aiutando?»,
 * un campo facoltativo, la data del prossimo controllo e «Non mostrarmeli più».
 * «No» o un testo scritto aprono «Segnala un problema» già compilato, da inviare se vuoi.
 * Non propone mai di spegnere una funzione.
 */
public class CheckActivity extends BaseActivity {

    static final String[] ANSWERS = {"Sì", "In parte", "No"};
    private int n;
    private String answer = null;
    private LinearLayout box, chipRow;
    private EditText text;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        n = getIntent().getIntExtra("n", Checks.current(this));
        if (n < 1 || n > Controlli.COUNT) { finish(); return; }
        String[] when = {"due settimane", "un mese", "tre mesi"};
        String[] titles = {"Le tue prime due settimane", "Il tuo primo mese", "Tre mesi con −minus"};
        java.util.List<String[]> nums = Checks.numbers(this, n);
        boolean none = nums.isEmpty();
        String[] plain = {"Due settimane con −minus", "Un mese con −minus", "Tre mesi con −minus"};
        LinearLayout root = U.screen(this, none ? plain[n - 1] : titles[n - 1]);
        ScrollView sv = new ScrollView(this);
        box = U.col(this);
        sv.addView(box);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        box.addView(U.text(this, "Controllo " + n + " di " + Controlli.COUNT + " · " + when[n - 1], 12, U.MUTED, false));
        if (n == Controlli.COUNT) box.addView(lead("È l'ultimo controllo: dopo questo, −minus non te ne mostrerà altri."));
        else if (none) box.addView(lead("Senza obiettivi accesi non ci sono numeri da mostrarti: ti chiediamo solo come va."));
        else box.addView(lead("Solo i numeri degli obiettivi che hai acceso. Nessun voto."));
        for (String[] r : nums) box.addView(card(r), gap(8));

        TextView q = U.text(this, none ? "Ti trovi bene?" : "Ti sta aiutando?", 16, U.INK, true);
        box.addView(q, gap(16));
        chipRow = U.row(this);
        box.addView(chipRow, gap(8));
        renderChips();
        box.addView(U.label(this, "Cosa cambieresti? (facoltativo)"), gap(12));
        text = new EditText(this);
        text.setMinLines(2);
        text.setGravity(Gravity.TOP);
        text.setTextColor(U.INK);
        text.setHintTextColor(U.HINT);
        text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        text.setBackground(U.box(this, U.SURFACE, U.BORDER, 12));
        text.setPadding(U.dp(this, 14), U.dp(this, 12), U.dp(this, 14), U.dp(this, 12));
        text.setContentDescription("Cosa cambieresti, facoltativo");
        box.addView(text, gap(6));

        if (none) {
            Intent goals = new Intent(this, SettingsActivity.class).putExtra("back", "Controllo");
            TextView g = navRow("Vedi gli obiettivi", "Meno notifiche, meno doomscrolling, meno interferenze sul sonno");
            final Intent gi = goals;
            g.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { startActivity(gi); } });
            box.addView(g, gap(12));
        }
        int next = Controlli.next(n);
        if (next > 0) {
            String last = next == Controlli.COUNT ? "" : ", l'ultimo il " + Checks.dueLabel(this, Controlli.COUNT);
            box.addView(U.text(this, (next == Controlli.COUNT ? "Prossimo e ultimo controllo il " : "Prossimo controllo il ")
                    + Checks.dueLabel(this, next) + last + ".", 12, U.MUTED, false), gap(16));
        }
        TextView done = U.text(this, "Fatto", 16, U.BG, true);
        done.setGravity(Gravity.CENTER);
        done.setMinHeight(U.dp(this, 52));
        done.setBackground(U.box(this, U.INK, 0, 26));
        done.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finishCheck(); } });
        box.addView(done, gap(12));
        if (next > 0) {
            TextView off = U.text(this, "Non mostrarmeli più", 14, U.INK, false);
            off.setGravity(Gravity.CENTER);
            off.setMinHeight(U.dp(this, 44));
            off.getPaint().setUnderlineText(true);
            off.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { askOff(); } });
            box.addView(off, gap(4));
        }
        box.addView(new View(this), new LinearLayout.LayoutParams(1, U.dp(this, 40)));
    }

    private TextView lead(String s) {
        TextView t = U.text(this, s, 13, U.MUTED, false);
        t.setPadding(0, U.dp(this, 4), 0, U.dp(this, 4));
        return t;
    }

    private View card(String[] r) {
        LinearLayout c = U.col(this);
        c.setBackground(U.box(this, U.SURFACE, U.LINE, 14));
        c.setPadding(U.dp(this, 14), U.dp(this, 10), U.dp(this, 14), U.dp(this, 10));
        c.addView(U.text(this, r[0], 12, U.MUTED, false));
        c.addView(U.text(this, r[1], 24, U.INK, true));
        c.addView(U.text(this, r[2], 13, U.INK, false));
        if (r[3] != null) c.addView(U.text(this, "Prima settimana: " + r[3], 12, U.MUTED, false));
        c.setContentDescription(r[0] + ": " + r[1] + ", " + r[2] + (r[3] != null ? ". Prima settimana: " + r[3] : ""));
        return c;
    }

    private TextView navRow(String title, String sub) {
        TextView t = U.text(this, title + "  ›\n" + sub, 14, U.INK, false);
        t.setMinHeight(U.dp(this, 52));
        t.setPadding(U.dp(this, 14), U.dp(this, 8), U.dp(this, 14), U.dp(this, 8));
        t.setBackground(U.box(this, U.SURFACE, U.LINE, 14));
        return t;
    }

    private void renderChips() {
        chipRow.removeAllViews();
        for (final String a : ANSWERS) {
            boolean on = a.equals(answer);
            TextView t = U.text(this, a, 14, on ? U.BG : U.INK, true);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(U.dp(this, 44));
            t.setBackground(on ? U.box(this, U.INK, 0, 22) : U.box(this, U.BG, U.INK, 22));
            t.setContentDescription(a + (on ? ", scelto" : ""));
            t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { answer = a; renderChips(); } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = U.dp(this, 8);
            chipRow.addView(t, lp);
        }
    }

    private LinearLayout.LayoutParams gap(int dp) {
        LinearLayout.LayoutParams lp = U.full();
        lp.topMargin = U.dp(this, dp);
        return lp;
    }

    private void finishCheck() {
        String note = text.getText().toString().trim();
        Checks.finish(this, n, answer);
        if ("No".equals(answer) || note.length() > 0) {
            String pre = "Controllo " + n + " di " + Controlli.COUNT + (answer != null ? " · ti sta aiutando? " + answer : "")
                    + (note.length() > 0 ? "\n\n" + note : "");
            startActivity(new Intent(this, BugActivity.class).putExtra("text", pre).putExtra("kind", "Altro"));
        }
        finish();
    }

    private void askOff() {
        String next = Checks.dueLabel(this, Controlli.next(n));
        new AlertDialog.Builder(this).setTitle("Niente più controlli?")
                .setMessage("Il prossimo sarebbe il " + next + ". Se cambi idea, lo annulli da Impostazioni › Modifiche recenti.")
                .setPositiveButton("Sì, niente più controlli", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        Changes.track(CheckActivity.this, "Niente più controlli", new String[]{Checks.K_OFF},
                                new Runnable() { public void run() { U.prefs(CheckActivity.this).edit().putBoolean(Checks.K_OFF, true).commit(); } },
                                new Runnable() { public void run() { finish(); } });
                    }
                }).setNegativeButton("Annulla", null).show();
    }
}
