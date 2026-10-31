package it.essenziale.launcher;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Contenitori (Shop, Movimenti, Conversazioni) e "App In attesa", scelti dalla lista delle app installate. */
public class ContainerActivity extends BaseActivity {

    public static final int TAB_SHOP = 0, TAB_MONEY = 1, TAB_RW = 2, TAB_WAITING = 3;
    private static final String[] TABS = {"Shop", "Movimenti", "Conversazioni"};

    private int tab = TAB_SHOP;
    private String filter = "";
    private List<U.AppInfo> apps;
    private LinearLayout tabsRow, list;
    private TextView intro;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        tab = getIntent().getIntExtra("tab", TAB_SHOP);
        U.setPaused(this, "", false); // fissa la pausa attuale (anche quella con "!" della v0.2) prima di cambiare le liste
        apps = U.sortedApps(this);
        LinearLayout root = U.screen(this, tab == TAB_WAITING ? "App che vanno In attesa" : "Contenitori");

        tabsRow = U.row(this);
        tabsRow.setBackground(U.box(this, U.CHIP, 0, 24));
        tabsRow.setPadding(U.dp(this, 4), U.dp(this, 4), U.dp(this, 4), U.dp(this, 4));
        if (tab != TAB_WAITING) root.addView(tabsRow, U.full());

        intro = U.text(this, "", 14, U.MUTED, false);
        intro.setPadding(0, U.dp(this, 8), 0, U.dp(this, 8));
        root.addView(intro);

        EditText search = new EditText(this);
        search.setHint("Cerca tra le app");
        search.setHintTextColor(U.MUTED);
        search.setTextColor(U.INK);
        search.setTextSize(16);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setBackground(U.box(this, U.SURFACE, U.BORDER, 22));
        search.setPadding(U.dp(this, 16), 0, U.dp(this, 16), 0);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { filter = s.toString().trim().toLowerCase(Locale.ITALY); renderList(); }
        });
        root.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, U.dp(this, 46)));

        ScrollView sv = new ScrollView(this);
        list = U.col(this);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        render();
    }

    private void render() {
        tabsRow.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            final int t = i;
            TextView v = U.text(this, TABS[i], 14, tab == i ? U.BG : U.INK, true);
            v.setGravity(Gravity.CENTER);
            v.setBackground(tab == i ? U.box(this, U.INK, 0, 20) : null);
            v.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { tab = t; render(); } });
            tabsRow.addView(v, new LinearLayout.LayoutParams(0, U.dp(this, 40), 1f));
        }
        intro.setText(tab == TAB_WAITING
                ? "Le notifiche di queste app vanno In attesa e vengono contate. Quelle di tutte le altre finiscono in Altro."
                : "Scegli dalle app installate. Con la pausa, l'app si apre dopo 6 secondi.");
        renderList();
    }

    // ---------- Lettura e scrittura delle liste ----------

    private String key() {
        return tab == TAB_SHOP ? U.K_SHOP : tab == TAB_MONEY ? U.K_MONEY : tab == TAB_RW ? U.K_RW_APPS : U.K_WAITING_APPS;
    }

    private String def() {
        return tab == TAB_SHOP ? U.DEF_SHOP : tab == TAB_MONEY ? U.DEF_MONEY : tab == TAB_RW ? "" : U.DEF_WAITING_APPS;
    }

    /** Righe della lista attuale: {sezione, nome} (sezione vuota se la lista non ne ha). */
    private List<String[]> entries() {
        List<String[]> out = new ArrayList<String[]>();
        for (String[] r : U.rows(U.prefs(this).getString(key(), def()))) {
            if (tab == TAB_MONEY && r.length >= 2) out.add(new String[]{r[0], r[1].replace("!", "").trim()});
            else out.add(new String[]{"", r[r.length - 1].replace("!", "").trim()});
        }
        return out;
    }

    private void write(final List<String[]> rows) {
        // 0.6: ogni modifica si annulla subito e si ritrova in Modifiche recenti
        String what = tab == TAB_SHOP ? "Shop" : tab == TAB_MONEY ? "Movimenti" : tab == TAB_RW ? "Conversazioni" : "App che aspettano in Notifiche";
        Changes.track(this, what + ": lista aggiornata", new String[]{key()}, new Runnable() {
            public void run() { writeNow(rows); }
        }, new Runnable() { public void run() { renderList(); } });
    }

    private void writeNow(List<String[]> rows) {
        StringBuilder b = new StringBuilder();
        if (tab == TAB_MONEY) b.append("# Sezione | Nome dell'app\n");
        else if (tab == TAB_WAITING) b.append("# App le cui notifiche vanno In attesa (il resto va in Altro)\n");
        else b.append("# Nome dell'app come appare sul telefono\n");
        for (String[] r : rows) b.append(tab == TAB_MONEY ? r[0] + " | " + r[1] : r[1]).append("\n");
        U.prefs(this).edit().putString(key(), b.toString()).commit();
    }

    /** L'app corrisponde a una voce della lista? (stesso criterio del filtro: uguale o contenuto) */
    private static boolean matches(String entry, String label) {
        String e = entry.toLowerCase(Locale.ITALY), l = label.toLowerCase(Locale.ITALY);
        return e.length() > 0 && (l.equals(e) || l.contains(e) || l.replace(" ", "").contains(e.replace(" ", "")));
    }

    /** Come U.findApp: prima nome uguale, poi "contiene" (così vale lo stesso abbinamento dei contenitori). */
    private U.AppInfo resolve(String entry) {
        String n = entry.toLowerCase(Locale.ITALY).trim();
        for (U.AppInfo a : apps) if (a.label.toLowerCase(Locale.ITALY).equals(n)) return a;
        for (U.AppInfo a : apps) if (a.label.toLowerCase(Locale.ITALY).contains(n)) return a;
        String compact = n.replace(" ", "");
        for (U.AppInfo a : apps) if (a.label.toLowerCase(Locale.ITALY).replace(" ", "").contains(compact)) return a;
        return null;
    }

    private boolean hit(String entry, U.AppInfo a) {
        if (tab == TAB_WAITING) return matches(entry, a.label);
        U.AppInfo f = resolve(entry);
        return f != null && f.pkg.equals(a.pkg) && f.label.equals(a.label);
    }

    private int find(List<String[]> rows, U.AppInfo a) {
        for (int i = 0; i < rows.size(); i++) if (hit(rows.get(i)[1], a)) return i;
        return -1;
    }

    // ---------- Lista ----------

    private void renderList() {
        list.removeAllViews();
        final List<String[]> rows = entries();
        // Voci che non corrispondono a nessuna app installata
        for (final String[] r : rows) {
            boolean installed = false;
            for (U.AppInfo a : apps) if (hit(r[1], a)) { installed = true; break; }
            if (installed || !visible(r[1])) continue;
            LinearLayout row = U.row(this);
            row.setMinimumHeight(U.dp(this, 52));
            row.addView(U.checkbox(this, true, U.MUTED), new LinearLayout.LayoutParams(U.dp(this, 22), U.dp(this, 22)));
            TextView t = U.text(this, r[1] + " · non installata", 16, U.MUTED, false);
            t.setPadding(U.dp(this, 12), 0, 0, 0);
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { rows.remove(r); write(rows); renderList(); }
            });
            list.addView(row, U.full());
            list.addView(U.divider(this));
        }
        Set<String> paused = U.pausedPkgs(this);
        List<U.AppInfo> in = new ArrayList<U.AppInfo>(), out = new ArrayList<U.AppInfo>();
        for (U.AppInfo a : apps) (find(rows, a) >= 0 ? in : out).add(a);
        List<U.AppInfo> ordered = new ArrayList<U.AppInfo>(in);
        ordered.addAll(out);
        for (final U.AppInfo a : ordered) {
            if (!visible(a.label)) continue;
            final boolean inBox = find(rows, a) >= 0;
            LinearLayout row = U.row(this);
            row.setMinimumHeight(U.dp(this, 52));
            row.addView(U.checkbox(this, inBox, U.INK), new LinearLayout.LayoutParams(U.dp(this, 22), U.dp(this, 22)));
            LinearLayout tx = U.col(this);
            tx.addView(U.text(this, a.label, 16, U.INK, false));
            if (tab == TAB_MONEY && inBox) tx.addView(U.text(this, rows.get(find(rows, a))[0], 12, U.MUTED, false));
            tx.setPadding(U.dp(this, 12), 0, 0, 0);
            row.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (tab != TAB_WAITING) {
                final boolean p = paused.contains(a.pkg);
                TextView pz = U.text(this, p ? "Pausa ✓" : "Pausa", 12, p ? U.BG : U.INK, true);
                pz.setGravity(Gravity.CENTER);
                pz.setPadding(U.dp(this, 10), 0, U.dp(this, 10), 0);
                pz.setMinHeight(U.dp(this, 44));
                pz.setContentDescription(p ? "Pausa attiva, tocca per toglierla" : "Aggiungi la pausa di 6 secondi");
                pz.setBackground(p ? U.box(this, U.INK, 0, 17) : U.box(this, U.BG, U.LINE, 17));
                pz.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Changes.track(ContainerActivity.this, a.label + (p ? ": pausa tolta" : ": pausa attiva"), new String[]{U.K_PAUSE},
                                new Runnable() { public void run() { U.setPaused(ContainerActivity.this, a.pkg, !p); } },
                                new Runnable() { public void run() { renderList(); } });
                    }
                });
                row.addView(pz);
            }
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (inBox) {
                        for (int i = rows.size() - 1; i >= 0; i--) if (hit(rows.get(i)[1], a)) rows.remove(i);
                        write(rows);
                        renderList();
                    } else if (tab == TAB_MONEY) askSection(rows, a);
                    else { rows.add(new String[]{"", a.label}); write(rows); renderList(); }
                }
            });
            list.addView(row, U.full());
            list.addView(U.divider(this));
        }
        if (tab != TAB_WAITING) {
            TextView prev = U.text(this, "Anteprima della pausa ›", 15, U.INK, false);
            prev.setMinHeight(U.dp(this, 48));
            prev.setGravity(Gravity.CENTER_VERTICAL);
            prev.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { U.start(ContainerActivity.this, new android.content.Intent(ContainerActivity.this, FrictionActivity.class)); }
            });
            list.addView(prev, U.full());
        }
    }

    private boolean visible(String name) {
        return filter.length() == 0 || name.toLowerCase(Locale.ITALY).contains(filter);
    }

    /** Movimenti ha le sezioni (Conti, Investimenti…): chiedo dove mettere l'app. */
    private void askSection(final List<String[]> rows, final U.AppInfo a) {
        Set<String> s = new LinkedHashSet<String>();
        for (String[] r : rows) if (r[0].length() > 0) s.add(r[0]);
        s.add("Conti"); s.add("Investimenti"); s.add("Altro");
        final String[] items = s.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("In quale sezione?").setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                // la nuova voce va in fondo alla sua sezione
                int at = rows.size();
                for (int i = 0; i < rows.size(); i++) if (rows.get(i)[0].equals(items[w])) at = i + 1;
                rows.add(at, new String[]{items[w], a.label});
                write(rows);
                renderList();
            }
        }).show();
    }
}
