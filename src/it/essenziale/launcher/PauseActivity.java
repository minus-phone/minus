package it.essenziale.launcher;

import android.content.Intent;
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
 * «Pausa prima di aprire» (0.10, wireframe 140): per ogni app un livello, nessuna, pausa o pausa e
 * scopo. Non c'è «spegni per qualche giorno»: si può solo scendere di livello, con «Annulla».
 */
public class PauseActivity extends BaseActivity {

    static final String[] LEVELS = {"Nessuna", "Pausa", "Pausa e scopo"};

    private LinearLayout list;
    private List<U.AppInfo> apps;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        apps = U.sortedApps(this);
        LinearLayout root = U.screen(this, "Pausa prima di aprire");
        TextView intro = U.text(this, "Per ogni app scegli quanto attrito vuoi.\n«Pausa e scopo» rallenta l'apertura dell'app perché ti chiede il motivo per cui vuoi aprirla. Meglio non usarla per le cose da aprire in fretta, come banca o mappe.", 12, U.MUTED, false);
        intro.setPadding(0, U.dp(this, 4), 0, U.dp(this, 6));
        root.addView(intro);
        ScrollView sv = new ScrollView(this);
        list = U.col(this);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout prevs = U.row(this);
        prevs.addView(preview("Anteprima pausa", false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        prevs.addView(preview("Anteprima scopo", true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(prevs, U.full());
        setContentView(root);
    }

    private TextView preview(String s, final boolean scope) {
        TextView t = U.text(this, s + " ›", 14, U.INK, true);
        t.setMinHeight(U.dp(this, 48));
        t.setGravity(Gravity.CENTER);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(PauseActivity.this, FrictionActivity.class);
                i.putExtra("scope", scope);
                startActivity(i);
            }
        });
        return t;
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        list.removeAllViews();
        if (!Pill.usageGranted(this)) {
            TextView u = U.text(this, "Vale quando apri le app da −minus. Per fermarle anche da notifiche e app recenti: Dati di utilizzo ›", 13, U.INK, false);
            u.setPadding(U.dp(this, 14), U.dp(this, 10), U.dp(this, 14), U.dp(this, 10));
            u.setBackground(U.box(this, U.SURFACE, U.LINE, 12));
            u.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Pill.openUsageSettings(PauseActivity.this); } });
            LinearLayout.LayoutParams lp = U.full();
            lp.topMargin = U.dp(this, 6);
            list.addView(u, lp);
        } else Scope.startGuard(this);
        Set<String> paused = U.pausedPkgs(this);
        List<U.AppInfo> on = new ArrayList<U.AppInfo>(), off = new ArrayList<U.AppInfo>();
        for (U.AppInfo a : apps) {
            if (paused.contains(a.pkg) || U.isSocial(a.pkg)) on.add(a); else off.add(a);
        }
        list.addView(U.label(this, "Con la pausa o social"));
        for (U.AppInfo a : on) row(a);
        list.addView(U.label(this, "Altre app"));
        for (U.AppInfo a : off) row(a);
        TextView n = U.text(this, "Non c'è «spegni per qualche giorno»: chi sospende tende a ricadere in fretta. Si può solo scendere di livello, app per app, e ogni scelta ha «Annulla». Di notte, con «Di notte» attivo, la pausa diventa pausa e scopo.", 12, U.MUTED, false);
        n.setPadding(0, U.dp(this, 14), 0, U.dp(this, 24));
        list.addView(n);
    }

    private void row(final U.AppInfo a) {
        LinearLayout r = U.col(this);
        r.setPadding(0, U.dp(this, 8), 0, U.dp(this, 8));
        LinearLayout top = U.row(this);
        top.addView(U.text(this, a.label, 16, U.INK, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (U.isSocial(a.pkg)) top.addView(U.text(this, "social", 12, U.MUTED, false));
        r.addView(top, U.full());
        r.addView(segment(a), U.full());
        list.addView(r, U.full());
        list.addView(U.divider(this));
    }

    private View segment(final U.AppInfo a) {
        final int cur = Scope.dayLevel(this, a.pkg);
        LinearLayout seg = U.row(this);
        seg.setBackground(U.box(this, U.SURFACE, U.BORDER, 10));
        seg.setPadding(U.dp(this, 1), U.dp(this, 1), U.dp(this, 1), U.dp(this, 1));
        for (int i = 0; i < LEVELS.length; i++) {
            final int k = i;
            boolean sel = i == cur;
            TextView t = U.text(this, LEVELS[i], 13, sel ? U.BG : U.INK, false);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(U.dp(this, 44));
            if (sel) t.setBackground(U.box(this, U.INK, 0, 9));
            t.setContentDescription(a.label + ": " + LEVELS[i] + (sel ? ", scelto" : ""));
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (k == cur) return;
                    Changes.track(PauseActivity.this, a.label + ": " + LEVELS[k].toLowerCase(java.util.Locale.ITALY),
                            new String[]{U.K_PAUSE, Scope.K_LEVEL},
                            new Runnable() { public void run() { Scope.setLevel(PauseActivity.this, a.pkg, k); } },
                            new Runnable() { public void run() { render(); } });
                }
            });
            seg.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        LinearLayout.LayoutParams lp = U.full();
        lp.topMargin = U.dp(this, 6);
        seg.setLayoutParams(lp);
        return seg;
    }
}
