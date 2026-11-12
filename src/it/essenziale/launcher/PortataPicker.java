package it.essenziale.launcher;

import android.app.Activity;
import android.content.Intent;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A portata in una sola schermata (0.11, wireframe 161–161b): in alto la ricerca tra tutte le app;
 * a campo vuoto le scelte (con il contatore) e le proposte; mentre scrivi, i risultati. I social si
 * possono scegliere: sotto la riga una nota, senza popup. Usata dall'onboarding e da Impostazioni.
 */
final class PortataPicker {

    interface Host {
        /** La scelta è cambiata: {pacchetto, nome} in ordine. */
        void changed(String desc, List<String[]> selected, Runnable after);
    }

    private final Activity a;
    private final Host host;
    private final boolean reorder;
    private final List<String[]> sel;        // {pacchetto, nome}
    private final List<String[]> proposals;  // {pacchetto, nome, tipo}
    private List<U.AppInfo> all;
    final LinearLayout root;
    private final LinearLayout list;
    private final EditText q;

    PortataPicker(Activity a, List<String[]> selected, List<String[]> proposals, boolean reorder, Host host) {
        this.a = a;
        this.host = host;
        this.reorder = reorder;
        this.sel = new ArrayList<String[]>(selected);
        this.proposals = proposals;
        root = U.col(a);
        q = new EditText(a);
        q.setHint("Cerca tra tutte le app");
        q.setTextSize(15 * U.textScale);
        q.setSingleLine(true);
        q.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        q.setPadding(U.dp(a, 16), U.dp(a, 10), U.dp(a, 16), U.dp(a, 10));
        q.setMinHeight(U.dp(a, 48));
        q.setContentDescription("Cerca tra tutte le app da mettere a portata");
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable e) { render(); }
        });
        LinearLayout.LayoutParams lp = U.full();
        lp.topMargin = U.dp(a, 8);
        root.addView(q, lp);
        list = U.col(a);
        root.addView(list, U.full());
        render();
    }

    List<String[]> selected() { return new ArrayList<String[]>(sel); }

    private int indexOf(String pkg) {
        for (int i = 0; i < sel.size(); i++) if (sel.get(i)[0].equals(pkg)) return i;
        return -1;
    }

    private boolean full() { return sel.size() >= SearchActivity.MAX_PORTATA; }

    void render() {
        q.setTextColor(U.INK);
        q.setHintTextColor(U.HINT);
        String query = q.getText().toString().trim();
        q.setBackground(U.box(a, U.SURFACE, query.length() > 0 ? U.INK : U.BORDER, 24));
        list.removeAllViews();
        String count = sel.size() + " di " + SearchActivity.MAX_PORTATA;
        if (query.length() == 0) {
            list.addView(U.label(a, "Scelte · " + count));
            if (full()) hintFull();
            if (sel.isEmpty()) list.addView(U.text(a, "Nessuna: cerca un'app o scegli tra le proposte.", 14, U.MUTED, false));
            for (int i = 0; i < sel.size(); i++) row(sel.get(i)[0], sel.get(i)[1], kindOf(sel.get(i)[0]), i);
            boolean head = false;
            for (String[] p : proposals) {
                if (indexOf(p[0]) >= 0) continue;
                if (!head) { list.addView(U.label(a, "Proposte")); head = true; }
                row(p[0], p[1], U.isSocial(p[0]) ? "social" : p[2], -1);
            }
        } else {
            if (all == null) all = U.sortedApps(a);
            final String qq = query;
            List<U.AppInfo> hits = new ArrayList<U.AppInfo>();
            for (U.AppInfo x : all) if (Cerca.score(x.label, qq) > 0) hits.add(x);
            Collections.sort(hits, new Comparator<U.AppInfo>() {
                public int compare(U.AppInfo x, U.AppInfo y) { return Cerca.score(y.label, qq) - Cerca.score(x.label, qq); }
            });
            list.addView(U.label(a, "Risultati · " + count));
            if (full()) hintFull();
            if (hits.isEmpty()) list.addView(U.text(a, "Nessuna app con questo nome.", 14, U.MUTED, false));
            int n = 0;
            for (U.AppInfo x : hits) {
                if (n++ >= 30) break;
                row(x.pkg, x.label, kindOf(x.pkg), -1);
            }
        }
    }

    private String kindOf(String pkg) {
        if (U.isSocial(pkg)) return "social";
        for (String[] p : proposals) if (p[0].equals(pkg)) return p[2];
        return "";
    }

    private void hintFull() {
        TextView t = U.text(a, "Togline una per aggiungerne un'altra.", 13, U.INK, false);
        t.setPadding(0, U.dp(a, 2), 0, U.dp(a, 4));
        list.addView(t);
    }

    /** pos ≥ 0: riga nelle «Scelte» (con ↑ se si può riordinare). */
    private void row(final String pkg, final String label, String kind, final int pos) {
        final boolean on = indexOf(pkg) >= 0;
        final boolean disabled = !on && full();
        LinearLayout r = U.row(a);
        r.setMinimumHeight(U.dp(a, 52));
        TextView n = U.text(a, label, 15, disabled ? U.MUTED : U.INK, false);
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        r.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (kind != null && kind.length() > 0) {
            TextView k = U.text(a, kind, 12, U.MUTED, false);
            k.setPadding(0, 0, U.dp(a, 12), 0);
            r.addView(k);
        }
        if (reorder && pos > 0) {
            TextView up = U.text(a, "↑", 18, U.INK, false);
            up.setGravity(Gravity.CENTER);
            up.setContentDescription("Sposta su " + label);
            up.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    String[] x = sel.remove(pos);
                    sel.add(pos - 1, x);
                    host.changed("A portata: " + label + " più in alto", selected(), null);
                    render();
                }
            });
            r.addView(up, new LinearLayout.LayoutParams(U.dp(a, 44), U.dp(a, 44)));
        }
        r.addView(U.checkbox(a, on, disabled ? U.MUTED : U.INK), new LinearLayout.LayoutParams(U.dp(a, 22), U.dp(a, 22)));
        r.setContentDescription(label + (kind != null && kind.length() > 0 ? ", " + kind : "") + (on ? ", a portata" : disabled ? ", non disponibile: sono già 8" : ", non a portata"));
        if (!disabled) r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int i = indexOf(pkg);
                if (i >= 0) sel.remove(i); else sel.add(new String[]{pkg, label});
                host.changed(i >= 0 ? "A portata: tolta " + label : "A portata: aggiunta " + label, selected(), null);
                render();
            }
        });
        list.addView(r, U.full());
        if (on && U.isSocial(pkg)) socialNote(pkg, label);
        list.addView(U.divider(a));
    }

    private void socialNote(final String pkg, String label) {
        boolean scoped = Scope.dayLevel(a, pkg) == Scopo.SCOPE;
        TextView t = U.text(a, "", 13, U.INK, false);
        t.setText(Html.fromHtml("<b>" + Html.escapeHtml(label) + " è un social.</b> " + (scoped
                ? "Ha «pausa e scopo»: prima di aprirla ti chiede per cosa e per quanto."
                : "A portata la apri con un tocco, senza scriverne il nome. Se vuoi tenerla, puoi darle <u>pausa e scopo ›</u>")));
        t.setPadding(U.dp(a, 12), U.dp(a, 10), U.dp(a, 12), U.dp(a, 10));
        t.setBackground(U.box(a, U.CHIP, 0, 12));
        if (!scoped) {
            t.setMinHeight(U.dp(a, 48));
            t.setContentDescription(label + " è un social. Tocca per darle pausa e scopo.");
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Changes.record(a, "Pausa e scopo per " + U.appLabel(a, pkg), new String[]{U.K_PAUSE, Scope.K_LEVEL});
                    Scope.setLevel(a, pkg, Scopo.SCOPE);
                    render();
                }
            });
        }
        LinearLayout.LayoutParams lp = U.full();
        lp.bottomMargin = U.dp(a, 8);
        list.addView(t, lp);
    }

    static void open(Activity a) { a.startActivity(new Intent(a, PortataActivity.class)); }
}
