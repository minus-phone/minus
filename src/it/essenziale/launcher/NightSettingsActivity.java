package it.essenziale.launcher;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 0.15: Impostazioni › Meno interferenze sul sonno › App frenate di notte (wireframe 202).
 * Solo l'elenco delle app frenate di notte: le altre scelte della notte (ore, modalità sera,
 * mattino, scala di grigi, rapporto) stanno nella pagina dell'obiettivo. Prima qui c'era la
 * vecchia pagina «Di notte» della 0.10, con alternativa, orari e prova.
 */
public class NightSettingsActivity extends BaseActivity {

    private LinearLayout list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = U.screen(this, "App frenate di notte");
        TextView intro = U.text(this, "Dall'ora di staccare alla sveglia queste app ti chiedono per cosa le apri e per quanto: 2 o 5 minuti. Di giorno restano come le hai scelte.", 14, U.MUTED, false);
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

    private void render() {
        list.removeAllViews();
        Set<String> apps = Night.nightApps(this);
        if (apps.isEmpty()) {
            TextView none = U.text(this, "Nessuna app: di notte le app si aprono come di giorno.", 15, U.MUTED, false);
            none.setPadding(0, U.dp(this, 10), 0, U.dp(this, 10));
            list.addView(none);
        }
        for (final String pkg : apps) {
            final String label = U.appLabel(this, pkg);
            LinearLayout r = U.row(this);
            r.setMinimumHeight(U.dp(this, 48));
            r.addView(U.text(this, label, 15, U.INK, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView x = U.text(this, "✕", 16, U.INK, false);
            x.setGravity(Gravity.CENTER);
            x.setContentDescription("Togli " + label + " dalle app frenate di notte");
            x.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Changes.track(NightSettingsActivity.this, "Di notte: tolta " + label, new String[]{Night.K_APPS},
                            new Runnable() { public void run() { Night.setNightApp(NightSettingsActivity.this, pkg, false); } },
                            new Runnable() { public void run() { render(); } });
                }
            });
            r.addView(x, new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 44)));
            list.addView(r, U.full());
            list.addView(U.divider(this));
        }
        TextView add = U.text(this, "+ Aggiungi un'app", 15, U.INK, false);
        add.setMinHeight(U.dp(this, 48));
        add.setGravity(Gravity.CENTER_VERTICAL);
        add.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pick(); } });
        list.addView(add, U.full());
        String more = Night.scopeOn(this) && !U.pausedPkgs(this).isEmpty()
                ? "Anche le app con la pausa di giorno, di notte ti chiedono per cosa le apri."
                : "Le app con la pausa di giorno si scelgono in Meno doomscrolling.";
        TextView n = U.text(this, more, 12, U.MUTED, false);
        n.setPadding(0, U.dp(this, 14), 0, U.dp(this, 30));
        list.addView(n);
    }

    private void pick() {
        final List<U.AppInfo> all = new ArrayList<U.AppInfo>();
        Set<String> in = Night.nightApps(this), paused = U.pausedPkgs(this);
        for (U.AppInfo a : U.sortedApps(this)) if (!in.contains(a.pkg) && !paused.contains(a.pkg)) all.add(a);
        String[] names = new String[all.size()];
        for (int i = 0; i < names.length; i++) names[i] = all.get(i).label;
        new AlertDialog.Builder(this).setTitle("Frenata di notte").setItems(names, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, final int w) {
                Changes.track(NightSettingsActivity.this, "Di notte: aggiunta " + all.get(w).label, new String[]{Night.K_APPS},
                        new Runnable() { public void run() { Night.setNightApp(NightSettingsActivity.this, all.get(w).pkg, true); } },
                        new Runnable() { public void run() { render(); } });
            }
        }).setNegativeButton("Annulla", null).show();
    }
}
