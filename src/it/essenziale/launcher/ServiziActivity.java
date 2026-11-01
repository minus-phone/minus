package it.essenziale.launcher;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Impostazioni › Notifiche › Servizi (0.13): banca, pagamenti, identità digitale, app IO.
 * I loro codici e accessi passano sempre; il resto aspetta in Notifiche, mai in Altro.
 * −minus li riconosce da solo; qui si tolgono o si aggiungono a mano («!pkg:» e «pkg:» in K_SERVICES).
 */
public class ServiziActivity extends BaseActivity {
    private LinearLayout list;
    private EditText q;
    private List<U.AppInfo> all;

    /** Le app installate che oggi sono Servizi. */
    static List<U.AppInfo> installed(android.content.Context c) {
        List<String> sv = U.ruleConfig(c).services;
        List<U.AppInfo> out = new ArrayList<U.AppInfo>();
        for (U.AppInfo a : U.sortedApps(c)) if (Rules.isService(a.pkg, a.label, sv)) out.add(a);
        return out;
    }

    static String names(android.content.Context c, int max) {
        List<U.AppInfo> l = installed(c);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < l.size() && i < max; i++) { if (b.length() > 0) b.append(", "); b.append(l.get(i).label); }
        if (l.size() > max) b.append(" e altre ").append(l.size() - max);
        return b.toString();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = U.screen(this, "Servizi");
        TextView intro = U.text(this, "Banca, pagamenti, SPID, app IO. I loro codici e accessi passano sempre, anche con il silenzio. Il resto aspetta in Notifiche, mai in Altro.", 14, U.MUTED, false);
        intro.setPadding(0, 0, 0, U.dp(this, 8));
        root.addView(intro);
        q = new EditText(this);
        q.setHint("Cerca un'app da aggiungere");
        q.setTextSize(15 * U.textScale);
        q.setTypeface(U.SANS);
        q.setSingleLine(true);
        q.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        q.setPadding(U.dp(this, 16), U.dp(this, 10), U.dp(this, 16), U.dp(this, 10));
        q.setMinHeight(U.dp(this, 48));
        q.setTextColor(U.INK);
        q.setHintTextColor(U.MUTED);
        q.setBackground(U.box(this, U.SURFACE, U.BORDER, 24));
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable e) { render(); }
        });
        root.addView(q, U.full());
        ScrollView sv = new ScrollView(this);
        list = U.col(this);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        render();
    }

    private void render() {
        list.removeAllViews();
        String query = q.getText().toString().trim();
        List<String> sv = U.ruleConfig(this).services;
        if (query.length() == 0) {
            List<U.AppInfo> l = installed(this);
            list.addView(U.label(this, "Riconosciuti · " + l.size()));
            if (l.isEmpty()) list.addView(U.text(this, "Nessuno: cerca la tua banca o l'app IO qui sopra.", 14, U.MUTED, false));
            for (U.AppInfo a : l) row(a, true);
            TextView n = U.text(this, "Gli SMS con un codice passano sempre, anche senza scegliere niente qui.", 13, U.MUTED, false);
            n.setPadding(0, U.dp(this, 14), 0, U.dp(this, 24));
            list.addView(n);
        } else {
            if (all == null) all = U.sortedApps(this);
            list.addView(U.label(this, "Risultati"));
            int n = 0;
            for (U.AppInfo a : all) {
                if (Cerca.score(a.label, query) <= 0) continue;
                if (n++ >= 30) break;
                row(a, Rules.isService(a.pkg, a.label, sv));
            }
            if (n == 0) list.addView(U.text(this, "Nessuna app con questo nome.", 14, U.MUTED, false));
        }
    }

    private void row(final U.AppInfo a, final boolean on) {
        LinearLayout r = U.row(this);
        r.setMinimumHeight(U.dp(this, 52));
        r.addView(AppIcons.view(this, a.pkg, 24), AppIcons.lp(this, 24));
        TextView t = U.text(this, a.label, 15, U.INK, false);
        t.setSingleLine(true);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(U.checkbox(this, on, U.INK), new LinearLayout.LayoutParams(U.dp(this, 22), U.dp(this, 22)));
        r.setContentDescription(a.label + (on ? ", servizio" : ", non è un servizio"));
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Changes.track(ServiziActivity.this, (on ? "Servizi: tolta " : "Servizi: aggiunta ") + a.label, new String[]{U.K_SERVICES},
                        new Runnable() { public void run() { set(a.pkg, !on); } },
                        new Runnable() { public void run() { render(); } });
            }
        });
        list.addView(r, U.full());
        list.addView(U.divider(this));
    }

    private void set(String pkg, boolean on) {
        List<String> out = new ArrayList<String>();
        for (String line : U.cfg(this, U.K_SERVICES, U.DEF_SERVICES).split("\n")) {
            String l = line.trim();
            if (l.equalsIgnoreCase("pkg:" + pkg) || l.equalsIgnoreCase("!pkg:" + pkg)) continue;
            out.add(line);
        }
        StringBuilder b = new StringBuilder();
        for (String l : out) if (l.length() > 0) b.append(l).append('\n');
        b.append(on ? "pkg:" : "!pkg:").append(pkg).append('\n');
        U.prefs(this).edit().putString(U.K_SERVICES, b.toString()).commit();
    }
}
