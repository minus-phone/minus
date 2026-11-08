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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Impostazioni › Tempo e uso › Di notte (0.10, wireframe 151). Non misura il sonno: se ti svegli e
 * prendi il telefono, mostra solo l'essenziale. Poche scelte: l'alternativa, quando è notte, la
 * pausa che diventa scopo, le app solo di notte, la scala di grigi (di Android), la prova.
 */
public class NightSettingsActivity extends BaseActivity {

    private LinearLayout list;
    private EditText alt;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = U.screen(this, "Di notte");
        TextView intro = U.text(this, "Se ti svegli e prendi il telefono, −minus ti mostra solo l'essenziale. Non misura il sonno.", 14, U.MUTED, false);
        intro.setPadding(0, U.dp(this, 4), 0, U.dp(this, 6));
        root.addView(intro);
        ScrollView sv = new ScrollView(this);
        list = U.col(this);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    protected void onPause() {
        saveAlt();
        super.onPause();
    }

    private void saveAlt() {
        if (alt == null) return;
        String s = alt.getText().toString().trim();
        if (s.length() > 0 && !s.equals(Night.alternative(this))) {
            Changes.record(this, "Di notte: la tua alternativa", new String[]{Night.K_ALT});
            U.prefs(this).edit().putString(Night.K_ALT, s).apply();
        }
    }

    private void render() {
        saveAlt();
        list.removeAllViews();
        final boolean on = Night.on(this);
        toggleRow("Di notte", on ? (Night.active(this) ? "Attivo · adesso è notte" : "Attivo") : "Spento", on, new Runnable() {
            public void run() {
                Changes.track(NightSettingsActivity.this, on ? "«Di notte» spento" : "«Di notte» acceso", new String[]{Night.K_ON},
                        new Runnable() { public void run() { U.prefs(NightSettingsActivity.this).edit().putBoolean(Night.K_ON, !on).commit(); } },
                        new Runnable() { public void run() { render(); } });
            }
        });
        list.addView(U.label(this, "La tua alternativa"));
        alt = new EditText(this);
        alt.setText(Night.alternative(this));
        alt.setTextSize(15 * U.textScale);
        alt.setTextColor(U.INK);
        alt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        alt.setBackground(U.box(this, U.SURFACE, U.BORDER, 12));
        alt.setPadding(U.dp(this, 14), U.dp(this, 12), U.dp(this, 14), U.dp(this, 12));
        list.addView(alt, U.full());
        list.addView(U.text(this, "Scrivila con parole tue: è quello che vuoi fare invece di scorrere.", 12, U.MUTED, false));

        list.addView(U.label(this, "Quando è notte"));
        final String mode = Night.mode(this);
        LinearLayout seg = U.row(this);
        seg.setBackground(U.box(this, U.SURFACE, U.BORDER, 10));
        seg.setPadding(U.dp(this, 1), U.dp(this, 1), U.dp(this, 1), U.dp(this, 1));
        String[][] modes = {{Notte.AUTO, "In automatico"}, {Notte.FIXED, "Dalle 23 alle 6"}};
        for (final String[] m : modes) {
            boolean sel = m[0].equals(mode);
            TextView t = U.text(this, m[1], 13, sel ? U.BG : U.INK, false);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(U.dp(this, 44));
            if (sel) t.setBackground(U.box(this, U.INK, 0, 9));
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (m[0].equals(mode)) return;
                    Changes.track(NightSettingsActivity.this, "Di notte: " + m[1].toLowerCase(java.util.Locale.ITALY), new String[]{Night.K_MODE},
                            new Runnable() { public void run() { U.prefs(NightSettingsActivity.this).edit().putString(Night.K_MODE, m[0]).commit(); } },
                            new Runnable() { public void run() { render(); } });
                }
            });
            seg.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        list.addView(seg, U.full());
        list.addView(U.text(this, Notte.AUTO.equals(mode) ? "Dalla prima lunga pausa della sera fino alla sveglia." : "Ogni notte, alla stessa ora.", 12, U.MUTED, false));

        list.addView(U.label(this, "App"));
        final boolean sc = Night.scopeOn(this);
        int paused = U.pausedPkgs(this).size();
        toggleRow("Le app con la pausa diventano «pausa e scopo»", paused + " app · tempi brevi, 2 o 5 min · Vedi ›", sc, new Runnable() {
            public void run() {
                Changes.track(NightSettingsActivity.this, sc ? "Di notte: pausa senza scopo" : "Di notte: pausa e scopo", new String[]{Night.K_SCOPE},
                        new Runnable() { public void run() { U.prefs(NightSettingsActivity.this).edit().putBoolean(Night.K_SCOPE, !sc).commit(); } },
                        new Runnable() { public void run() { render(); } });
            }
        });
        TextView see = U.text(this, "Vedi le app con la pausa ›", 14, U.INK, false);
        see.setMinHeight(U.dp(this, 44));
        see.setGravity(Gravity.CENTER_VERTICAL);
        see.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { startActivity(new Intent(NightSettingsActivity.this, PauseActivity.class)); } });
        list.addView(see, U.full());
        for (final String pkg : Night.nightApps(this)) {
            LinearLayout r = U.row(this);
            r.setMinimumHeight(U.dp(this, 46));
            LinearLayout tx = U.col(this);
            tx.addView(U.text(this, U.appLabel(this, pkg), 15, U.INK, false));
            tx.addView(U.text(this, "solo di notte", 12, U.MUTED, false));
            r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView x = U.text(this, "✕", 16, U.INK, false);
            x.setGravity(Gravity.CENTER);
            x.setContentDescription("Togli " + U.appLabel(this, pkg) + " dalle app solo di notte");
            x.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Changes.track(NightSettingsActivity.this, "Di notte: tolta " + U.appLabel(NightSettingsActivity.this, pkg), new String[]{Night.K_APPS},
                            new Runnable() { public void run() { Night.setNightApp(NightSettingsActivity.this, pkg, false); } },
                            new Runnable() { public void run() { render(); } });
                }
            });
            r.addView(x, new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 44)));
            list.addView(r, U.full());
            list.addView(U.divider(this));
        }
        TextView add = U.text(this, "+ Aggiungi un'app solo di notte", 15, U.INK, false);
        add.setMinHeight(U.dp(this, 46));
        add.setGravity(Gravity.CENTER_VERTICAL);
        add.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pick(); } });
        list.addView(add, U.full());

        list.addView(U.label(this, "Altro"));
        row("Scala di grigi", "Con la modalità Riposo di Android, in Benessere digitale", "Attiva ›", new Runnable() {
            public void run() { Session.openWellbeing(NightSettingsActivity.this); }
        });
        final boolean trial = U.prefs(this).getLong(Night.K_TRIAL, 0) > System.currentTimeMillis();
        toggleRow("Prova di una settimana", trial ? "Finisce " + Changes.when(U.prefs(this).getLong(Night.K_TRIAL, 0)).toLowerCase(java.util.Locale.ITALY) + " · poi decidi tu" : "Una settimana, poi ti mostro i numeri", trial, new Runnable() {
            public void run() {
                if (trial) U.prefs(NightSettingsActivity.this).edit().putLong(Night.K_TRIAL, 0).apply();
                else Night.startTrial(NightSettingsActivity.this);
                render();
            }
        });
        TextView n = U.text(this, "La scala di grigi la fa Android: −minus è già in bianco e nero. Di notte il tempo dall'ultimo sblocco scende a 5 minuti.", 12, U.MUTED, false);
        n.setPadding(0, U.dp(this, 14), 0, U.dp(this, 30));
        list.addView(n);
    }

    private void pick() {
        final List<U.AppInfo> all = new ArrayList<U.AppInfo>();
        Set<String> in = Night.nightApps(this), paused = U.pausedPkgs(this);
        for (U.AppInfo a : U.sortedApps(this)) if (!in.contains(a.pkg) && !paused.contains(a.pkg)) all.add(a);
        String[] names = new String[all.size()];
        for (int i = 0; i < names.length; i++) names[i] = all.get(i).label;
        new AlertDialog.Builder(this).setTitle("Solo di notte").setItems(names, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, final int w) {
                Changes.track(NightSettingsActivity.this, "Di notte: aggiunta " + all.get(w).label, new String[]{Night.K_APPS},
                        new Runnable() { public void run() { Night.setNightApp(NightSettingsActivity.this, all.get(w).pkg, true); } },
                        new Runnable() { public void run() { render(); } });
            }
        }).setNegativeButton("Annulla", null).show();
    }

    private void toggleRow(String title, String sub, boolean on, final Runnable r) {
        LinearLayout row = U.row(this);
        row.setMinimumHeight(U.dp(this, 60));
        LinearLayout tx = U.col(this);
        tx.addView(U.text(this, title, 15, U.INK, false));
        tx.addView(U.text(this, sub, 12, U.MUTED, false));
        row.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(U.toggle(this, on), new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 26)));
        row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        list.addView(row, U.full());
        list.addView(U.divider(this));
    }

    private void row(String title, String sub, String right, final Runnable r) {
        LinearLayout row = U.row(this);
        row.setMinimumHeight(U.dp(this, 56));
        LinearLayout tx = U.col(this);
        tx.addView(U.text(this, title, 15, U.INK, false));
        tx.addView(U.text(this, sub, 12, U.MUTED, false));
        row.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(U.text(this, right, 14, U.INK, false));
        row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        list.addView(row, U.full());
        list.addView(U.divider(this));
    }
}
