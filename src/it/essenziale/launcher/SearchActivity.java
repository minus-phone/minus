package it.essenziale.launcher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * La ricerca (0.7): un solo accesso a persone, gruppi, app, impegni, azioni e impostazioni.
 * I risultati sono appoggiati in basso: il gruppo più importante sta vicino al campo e al pollice.
 * 0.12: tastiera chiusa all'apertura, ✕ in alto a destra. 0.13: Impostazioni come prima voce, sopra i recenti.
 */
public class SearchActivity extends BaseActivity {

    static final String K_PORTATA = "portata";     // pacchetto | nome
    static final String K_RECENT = "recent";       // tipo | valore | etichetta
    static final String K_RECENT_ON = "recent_on";
    static final int MAX_PORTATA = 8;
    static final String K_ORDER = "portata_people"; // 0.8: ordine di chi passa sempre (persone e gruppi)
    static final String K_ACTIVE_ON = "active_on";  // 0.8: conversazioni attive nella ricerca
    static final int MAX_ACTIVE = 3;
    private static final int REQ_VOICE = 21;
    private static final String CLAUDE_PKG = "com.anthropic.claude";

    private LinearLayout results;
    private ScrollView scroll;
    private EditText query;
    private TextView clear;
    private List<U.AppInfo> apps;
    private Runnable best;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = U.col(this);
        root.setBackgroundColor(U.BG);
        root.setPadding(U.dp(this, 20), U.dp(this, 12), U.dp(this, 20), U.dp(this, 10));
        // 0.13: in alto solo ✕ per tornare alla home; le Impostazioni sono una voce del menu, sopra i recenti
        LinearLayout top = U.row(this);
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView close = U.text(this, "✕", 18, U.INK, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Chiudi la ricerca");
        close.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        top.addView(close, new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 44)));
        root.addView(top, U.full());

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        results = U.col(this);
        results.setGravity(Gravity.BOTTOM);
        scroll.addView(results, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout field = U.row(this);
        field.setBackground(U.box(this, U.SURFACE, U.INK, 26));
        field.setPadding(U.dp(this, 16), U.dp(this, 2), U.dp(this, 6), U.dp(this, 2));
        query = new EditText(this);
        query.setHint("Cerca persone, app o chiedi");
        query.setHintTextColor(U.MUTED);
        query.setTextColor(U.INK);
        query.setTextSize(16);
        query.setBackground(null);
        query.setSingleLine(true);
        query.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        query.setImeOptions(EditorInfo.IME_ACTION_GO);
        field.addView(query, new LinearLayout.LayoutParams(0, U.dp(this, 48), 1f));
        clear = U.text(this, "✕", 16, U.MUTED, false);
        clear.setGravity(Gravity.CENTER);
        clear.setContentDescription("Cancella");
        clear.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { query.setText(""); } });
        field.addView(clear, new LinearLayout.LayoutParams(U.dp(this, 40), U.dp(this, 44)));
        MicView mic = new MicView(this);
        mic.setContentDescription("Comando vocale");
        mic.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { voice(); } });
        field.addView(mic, new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 44)));
        LinearLayout.LayoutParams flp = U.full();
        flp.topMargin = U.dp(this, 8);
        root.addView(field, flp);
        TextView hint = U.text(this, "Le altre app si aprono scrivendone il nome.", 14, U.MUTED, false);
        hint.setTypeface(android.graphics.Typeface.create(U.SERIF, android.graphics.Typeface.ITALIC)); // 0.13.6: come nella UI
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, U.dp(this, 6), 0, 0);
        root.addView(hint, U.full());
        setContentView(root);

        query.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { render(); }
        });
        query.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int id, KeyEvent e) { if (best != null) best.run(); return true; }
        });
        // 0.12: la tastiera resta chiusa (si vedono recenti, app e persone); si apre toccando il campo.
        // Impostazioni › App › «Apri subito la tastiera» la riporta come prima
        if (U.prefs(this).getBoolean(U.K_KEYBOARD, false)) {
            query.requestFocus();
            getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        } else {
            root.setFocusableInTouchMode(true);
            root.requestFocus();
        }
        if (getIntent().getBooleanExtra("voice", false)) voice();
    }

    @Override
    protected void onResume() {
        super.onResume();
        apps = null;
        render();
    }

    // ---------------------------------------------------------------------

    private void render() {
        results.removeAllViews();
        best = null;
        String q = query.getText().toString().trim();
        clear.setVisibility(q.length() > 0 ? View.VISIBLE : View.GONE);
        if (q.length() == 0) empty(); else found(q);
        scroll.post(new Runnable() { public void run() { scroll.fullScroll(View.FOCUS_DOWN); } });
    }

    // ---------- Al tocco (0.8): recenti, app, persone e gruppi ----------

    private void empty() {
        List<View> blocks = new ArrayList<View>();
        Set<String> paused = U.pausedPkgs(this);
        // dall'alto: recenti, app, persone e gruppi (vicino al pollice). Ogni blocco su una riga che scorre di lato.
        // 0.13.6: l'aspetto della proposta UI (canvas «Ricerca»): voci a riga, griglia per le app, persone come avatar
        blocks.add(settingsRow());
        if (U.prefs(this).getBoolean(K_RECENT_ON, true)) {
            // 0.13.7: di nuovo una riga che scorre di lato
            LinearLayout chips = hrow();
            for (final String[] r : recents(this)) {
                if (r.length < 3 || ("app".equals(r[0]) && paused.contains(r[1]))) continue;
                chips.addView(chip(r[2], new Runnable() { public void run() { openRecent(r); } }), chipLp());
            }
            if (chips.getChildCount() > 0) { blocks.add(sectionLabel("Cercate di recente")); blocks.add(scrollOf(chips)); }
        }
        List<String[]> pt = portata(this);
        if (!pt.isEmpty()) {
            blocks.add(sectionLabel("App a portata"));
            // 0.13.7: tre colonne, al massimo tre righe
            for (int i = 0; i < pt.size() && i < 9; i += 3) {
                LinearLayout line = U.row(this);
                for (int k = i; k < i + 3; k++) {
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    if (k < i + 2) lp.rightMargin = U.dp(this, 14);
                    if (k >= pt.size()) { line.addView(new View(this), lp); continue; }
                    final String[] a = pt.get(k);
                    final boolean pz = paused.contains(a[0]);
                    line.addView(gridCell(a[1] + (pz ? " · pausa" : ""), new Runnable() { public void run() { tapped = true; openApp(a[0], a[1]); } }), lp);
                }
                blocks.add(line);
            }
        }

        // Persone e gruppi: chi passa sempre come avatar; sotto, le conversazioni attive (le 3 più recenti)
        final List<String[]> fixed = fixedPeople(this);
        Set<String> fixedKeys = new java.util.HashSet<String>();
        for (String[] f : fixed) fixedKeys.add(Cerca.norm(f[0]));
        // 0.13.8: la stessa persona si riconosce dal numero («Filippo» in rubrica, «Filippo Ferro» in chat)
        Set<String> fixedNums = new java.util.HashSet<String>();
        for (Rules.Person p : Rules.people(U.cfg(this, U.K_CONTACTS, U.DEF_CONTACTS)))
            if (p.section.equalsIgnoreCase("Sempre")) fixedNums.addAll(Nums.forPerson(this, p));
        List<Rules.Conv> cs = new ArrayList<Rules.Conv>();
        if (U.prefs(this).getBoolean(K_ACTIVE_ON, true)) {
            long now = System.currentTimeMillis(), today = U.startOfDay(0);
            for (Rules.Conv c : Convs.load(this))
                if ((c.isOpen(now) || c.last >= today) && !fixedKeys.contains(Cerca.norm(Rules.groupName(c.name)))
                        && (c.group || java.util.Collections.disjoint(fixedNums, Nums.forConv(this, c.name)))) cs.add(c);
            Collections.sort(cs, new Comparator<Rules.Conv>() {
                public int compare(Rules.Conv a, Rules.Conv b) { return Long.compare(Math.max(b.last, b.until - Rules.OPEN_MS), Math.max(a.last, a.until - Rules.OPEN_MS)); }
            });
        }
        blocks.add(sectionLabel("Persone e gruppi"));
        LinearLayout av = hrow();
        av.setGravity(Gravity.TOP);
        for (final String[] f : fixed) av.addView(personChip(f));
        // «Altri ›» porta all'elenco; senza nessuno che passa sempre, invita a sceglierlo
        av.addView(avatar(fixed.isEmpty() ? "+" : "›", fixed.isEmpty() ? "Scegli chi passa sempre" : "Altri", false, true, new Runnable() {
            public void run() { startActivity(SettingsActivity.peopleIntent(SearchActivity.this, "Ricerca")); }
        }));
        blocks.add(scrollOf(av));
        for (int i = 0; i < cs.size() && i < MAX_ACTIVE; i++) blocks.add(convRow(cs.get(i)));
        if (blocks.isEmpty()) {
            TextView t = U.text(this, "Scrivi il nome di una persona, di un'app o di un impegno. Puoi anche chiedere o dire un comando.", 14, U.MUTED, false);
            t.setPadding(0, 0, 0, U.dp(this, 8));
            blocks.add(t);
        }
        for (View v : blocks) results.addView(v, U.full());
    }

    private LinearLayout hrow() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = U.dp(this, 6);
        return lp;
    }

    /** Una riga che scorre di lato; l'ultimo elemento resta tagliato sul bordo e dice che ce n'è altro. */
    private View scrollOf(LinearLayout row) {
        android.widget.HorizontalScrollView h = new android.widget.HorizontalScrollView(this);
        h.setHorizontalScrollBarEnabled(false);
        h.setOverScrollMode(View.OVER_SCROLL_NEVER);
        h.setPadding(0, 0, 0, U.dp(this, 6));
        h.addView(row);
        return h;
    }

    /** 0.13.6: persona o gruppo che passa sempre, come avatar: iniziale in un cerchio (quadrato per i gruppi) e nome sotto. */
    private View personChip(final String[] f) {
        final boolean group = "g".equals(f[2]);
        String n0 = f[0].trim();
        View c = avatar(n0.length() > 0 ? n0.substring(0, 1).toUpperCase(Locale.ITALY) : "?", shortName(f[0]), group, false, null);
        c.setContentDescription((group ? "Gruppo " : "") + f[0]);
        c.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!group) for (Rules.Person p : Rules.people(U.cfg(SearchActivity.this, U.K_CONTACTS, U.DEF_CONTACTS)))
                    if (Cerca.norm(p.name).equals(Cerca.norm(f[0]))) { openPerson(p); return; }
                Hit h = new Hit();
                h.name = f[0]; h.code = f[1]; h.group = group;
                openHit(h);
            }
        });
        return c;
    }

    private static String shortName(String n) {
        String t = n.trim();
        int sp = t.indexOf(' ');
        return sp > 0 && t.length() > 12 ? t.substring(0, sp) : t;
    }

    private View avatar(String ini, String name, boolean group, boolean more, final Runnable r) {
        LinearLayout c = U.col(this);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(0, U.dp(this, 2), U.dp(this, 18), U.dp(this, 4));
        c.setMinimumWidth(U.dp(this, 64));
        TextView i = U.title(this, ini, more ? 18 : 20, more ? U.MUTED : U.INK);
        i.setTypeface(more ? U.SANS : U.SERIF);
        i.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setCornerRadius(U.dp(this, group ? 14 : 28));
        if (more) d.setStroke(U.dp(this, 1), U.MUTED, U.dp(this, 3), U.dp(this, 3));
        else d.setStroke(Math.max(1, Math.round(U.dp(this, 1.5f))), U.INK);
        i.setBackground(d);
        c.addView(i, new LinearLayout.LayoutParams(U.dp(this, 56), U.dp(this, 56)));
        TextView t = U.text(this, name, 14, more ? U.MUTED : U.INK, false);
        t.setGravity(Gravity.CENTER);
        t.setMaxWidth(U.dp(this, 84));
        t.setMaxLines(2);
        t.setPadding(0, U.dp(this, 8), 0, 0);
        c.addView(t);
        if (r != null) {
            c.setContentDescription(name);
            c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        }
        return c;
    }

    private TextView sectionLabel(String s) {
        TextView t = U.label(this, s);
        t.setLetterSpacing(0.12f);
        t.setPadding(0, U.dp(this, 22), 0, U.dp(this, 6));
        return t;
    }

    private View settingsRow() {
        LinearLayout row = U.row(this);
        row.setMinimumHeight(U.dp(this, 48));
        TextView g = U.text(this, "⚙", 18, U.INK, false);
        g.setPadding(0, 0, U.dp(this, 12), 0);
        row.addView(g);
        row.addView(U.text(this, "Impostazioni", 17, U.INK, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(U.text(this, "›", 17, U.MUTED, false));
        row.setContentDescription("Impostazioni");
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(SearchActivity.this, SettingsActivity.class).putExtra("back", "Ricerca")); }
        });
        LinearLayout wrap = U.col(this);
        wrap.addView(row, U.full());
        wrap.addView(U.divider(this));
        return wrap;
    }

    private View gridCell(String s, final Runnable r) {
        LinearLayout wrap = U.col(this);
        TextView t = U.text(this, s, 15, U.INK, false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setMinHeight(U.dp(this, 46));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        wrap.addView(t, U.full());
        wrap.addView(U.divider(this));
        return wrap;
    }

    /** 0.8: chi passa sempre, persone e gruppi insieme, nell'ordine scelto in A portata: {nome, codice app, "g" se gruppo}. */
    static List<String[]> fixedPeople(Context c) {
        java.util.Map<String, String[]> byName = new java.util.LinkedHashMap<String, String[]>();
        for (Rules.Person p : Rules.people(U.cfg(c, U.K_CONTACTS, U.DEF_CONTACTS)))
            if (p.section.equalsIgnoreCase("Sempre")) byName.put(p.name, new String[]{p.name, p.apps.isEmpty() ? "WA" : p.apps.get(0), ""});
        java.util.Map<String, String> codes = new java.util.HashMap<String, String>();
        for (Rules.Conv cv : Convs.load(c)) if (cv.group) codes.put(Cerca.norm(Rules.groupName(cv.name)), cv.code);
        for (String[] r : U.rows(U.prefs(c).getString(U.K_GROUPS, ""))) {
            if (r.length < 2 || !Rules.groupAlways(r[1].trim())) continue;
            String name = Rules.groupName(r[0]);
            String code = codes.get(Cerca.norm(name));
            byName.put(name, new String[]{name, code == null ? "WA" : code, "g"});
        }
        List<String> order = new ArrayList<String>();
        for (String[] r : U.rows(U.prefs(c).getString(K_ORDER, ""))) if (r.length > 0) order.add(r[0]);
        List<String[]> out = new ArrayList<String[]>();
        for (String n : Cerca.ordered(new ArrayList<String>(byName.keySet()), order)) out.add(byName.get(n));
        return out;
    }

    static void saveOrder(Context c, List<String> names) {
        StringBuilder b = new StringBuilder();
        for (String n : names) b.append(n.replace("|", "/")).append("\n");
        U.prefs(c).edit().putString(K_ORDER, b.toString()).commit();
    }

    // ---------- Mentre scrivi ----------

    private static class Hit {
        int rank;
        String name, sub, code, badges;
        boolean group, strong;
        Rules.Person person;
        long contactId = -1;
    }

    private void found(final String q) {
        List<List<View>> groups = new ArrayList<List<View>>(); // dal più importante (vicino al campo)
        List<String> titles = new ArrayList<String>();

        // Persone e gruppi
        List<Hit> people = peopleHits(q);
        if (!people.isEmpty()) {
            List<View> g = new ArrayList<View>();
            for (int i = 0; i < people.size() && i < 4; i++) g.add(personRow(people.get(i)));
            groups.add(g); titles.add("Persone e gruppi");
            final Hit h = people.get(0);
            if (best == null) best = new Runnable() { public void run() { openHit(h); } };
        }
        // App
        List<U.AppInfo> appHits = appHits(q);
        if (!appHits.isEmpty()) {
            List<View> g = new ArrayList<View>();
            Set<String> paused = U.pausedPkgs(this);
            for (int i = 0; i < appHits.size() && i < 4; i++) {
                final U.AppInfo a = appHits.get(i);
                g.add(row(a.label, paused.contains(a.pkg) ? "si apre dopo " + U.pauseSeconds(this) + " secondi" : null, "›", "◻", new Runnable() { public void run() { openApp(a.pkg, a.label); } }));
            }
            groups.add(g); titles.add("App");
            final U.AppInfo a0 = appHits.get(0);
            if (best == null || Cerca.score(a0.label, q) > (people.isEmpty() ? 0 : Cerca.score(people.get(0).name, q)))
                best = new Runnable() { public void run() { openApp(a0.pkg, a0.label); } };
        }
        // Impegni dei prossimi 30 giorni
        if (U.hasCalendar(this) && q.length() >= 2) {
            List<View> g = new ArrayList<View>();
            for (final U.Event e : U.events(this, System.currentTimeMillis() - 3600000L, U.startOfDay(30))) {
                if (Cerca.score(e.title, q) == 0) continue;
                String when = (e.begin < U.startOfDay(1) ? "oggi" : e.begin < U.startOfDay(2) ? "domani" : U.dayLabel(e.begin).toLowerCase(Locale.ITALY))
                        + (e.allDay ? "" : " · " + U.hhmm(e.begin));
                g.add(row(e.title, when, "›", "▤", new Runnable() { public void run() { U.openEvent(SearchActivity.this, e); } }));
                if (g.size() >= 3) break;
            }
            if (!g.isEmpty()) { groups.add(g); titles.add("Impegni"); }
        }
        boolean anything = !groups.isEmpty();
        // Azioni
        List<View> act = new ArrayList<View>();
        VoiceParser.Cmd c = VoiceParser.parse(q, VoiceActivity.names(this), java.util.Calendar.getInstance());
        if (!VoiceParser.ASK.equals(c.type)) act.add(row("Esegui «" + q + "»", "come se lo dicessi a voce", "›", "▶", new Runnable() { public void run() { command(q); } }));
        final String cap = q.substring(0, 1).toUpperCase(Locale.ITALY) + q.substring(1);
        act.add(row("Aggiungi «" + cap + "»", Quando.addDayOffset(U.hourNow()) == 1 ? "domani" : "oggi", "›", "+", new Runnable() {
            public void run() {
                Intent i = new Intent(SearchActivity.this, AddActivity.class);
                i.putExtra("title", cap);
                i.putExtra("day", U.startOfDay(Quando.addDayOffset(U.hourNow())));
                startActivity(i);
            }
        }));
        if (anything) { groups.add(act); titles.add("Azioni"); }
        // Impostazioni di minus
        List<String[]> st = Cerca.settings(q, 3);
        if (!st.isEmpty()) {
            List<View> g = new ArrayList<View>();
            for (final String[] e : st) g.add(row(e[0], "Impostazioni", "›", null, new Runnable() { public void run() { openSetting(e[2]); } }));
            groups.add(g); titles.add("Impostazioni di −minus");
            anything = true;
        }
        // Chiedi, sempre per ultimo (lontano dal campo)
        List<View> ask = new ArrayList<View>();
        ask.add(row("Chiedi «" + q + "» a Claude", null, "↗", null, new Runnable() { public void run() { ask(q); } }));
        if (!anything) {
            ask.add(row("Cerca sul web", null, "↗", null, new Runnable() { public void run() { web(q); } }));
            ask.add(row("Cerca nel Play Store", null, "↗", null, new Runnable() { public void run() { store(q); } }));
            groups.add(act); titles.add("Azioni");
        }
        groups.add(ask); titles.add("Chiedi");
        if (best == null) best = new Runnable() { public void run() { ask(q); } };

        // dall'alto il meno importante: il primo gruppo finisce attaccato al campo
        if (!anything) {
            TextView t = U.text(this, "Nessuna persona, app o impegno con «" + q + "».", 15, U.INK, false);
            t.setPadding(0, 0, 0, U.dp(this, 6));
            results.addView(t, U.full());
        }
        for (int i = groups.size() - 1; i >= 0; i--) {
            results.addView(U.label(this, titles.get(i)), U.full());
            for (View v : groups.get(i)) results.addView(v, U.full());
        }
    }

    private List<Hit> peopleHits(String q) {
        List<Hit> out = new ArrayList<Hit>();
        Set<String> seen = new java.util.HashSet<String>();
        java.util.Map<String, String> rules = U.groupRules(this);
        java.util.Map<String, Rules.Conv> conv = new java.util.HashMap<String, Rules.Conv>();
        long now = System.currentTimeMillis();
        for (Rules.Conv c : Convs.load(this)) {
            String k = Cerca.norm(Rules.groupName(c.name));
            Rules.Conv o = conv.get(k);
            if (o == null || c.until > o.until || c.last > o.last) conv.put(k, c);
        }
        for (Rules.Person p : Rules.people(U.cfg(this, U.K_CONTACTS, U.DEF_CONTACTS))) {
            int s = Cerca.rank(p.name, q, p.section.equalsIgnoreCase("Sempre"));
            if (s == 0) continue;
            Hit h = new Hit();
            h.rank = s; h.name = p.name; h.person = p; h.strong = p.section.equalsIgnoreCase("Sempre");
            h.sub = status(conv.get(Cerca.norm(p.name)), p, null, now);
            h.badges = TextUtils.join(" ", p.apps);
            h.code = p.apps.isEmpty() ? "WA" : p.apps.get(0);
            out.add(h); seen.add(Cerca.norm(p.name));
        }
        for (Rules.Conv c : conv.values()) {
            String k = Cerca.norm(Rules.groupName(c.name));
            if (seen.contains(k)) continue;
            String rule = c.group ? rules.get(Rules.groupName(c.name).toLowerCase(Locale.ITALY)) : null;
            int s = Cerca.rank(c.name, q, Rules.groupAlways(rule) || c.isOpen(now));
            if (s == 0) continue;
            Hit h = new Hit();
            h.rank = s; h.name = Rules.groupName(c.name); h.code = c.code; h.group = c.group; h.badges = c.code;
            h.sub = status(c, null, rule, now);
            out.add(h); seen.add(k);
        }
        if (Onboarding.hasContacts(this) && Cerca.norm(q).length() >= 2) {
            Cursor cur = null;
            try {
                cur = getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
                        new String[]{ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME},
                        ContactsContract.Contacts.DISPLAY_NAME + " LIKE ?", new String[]{"%" + q + "%"}, ContactsContract.Contacts.DISPLAY_NAME + " ASC");
                int n = 0;
                while (cur != null && cur.moveToNext() && n < 12) {
                    String name = cur.getString(1);
                    if (name == null || seen.contains(Cerca.norm(name))) continue;
                    n++;
                    Hit h = new Hit();
                    h.rank = Math.max(1, Cerca.rank(name, q, false));
                    h.name = name; h.contactId = cur.getLong(0); h.sub = "in rubrica";
                    out.add(h); seen.add(Cerca.norm(name));
                }
            } catch (Exception ignored) {
            } finally {
                if (cur != null) cur.close();
            }
        }
        Collections.sort(out, new Comparator<Hit>() { public int compare(Hit a, Hit b) { return b.rank - a.rank; } });
        return out;
    }

    private static String status(Rules.Conv c, Rules.Person p, String groupRule, long now) {
        if (c != null && c.isOpen(now) && (p == null || !p.section.equalsIgnoreCase("Sempre"))) return "aperta · " + Convs.until(c.until);
        if (p != null) {
            if (p.section.equalsIgnoreCase("Sempre")) return "passa sempre";
            if (p.section.equalsIgnoreCase("Attesa")) return "aspetta in Notifiche";
            return "passa finché vi scrivete";
        }
        if (c != null && c.group) return Rules.groupAlways(groupRule) ? "passa sempre" : "in silenzio";
        if (c != null && c.last >= U.startOfDay(0)) return "ti ha scritto alle " + U.hhmm(c.last);
        return null;
    }

    private List<U.AppInfo> appHits(String q) {
        if (apps == null) apps = U.sortedApps(this);
        Set<String> pref = new java.util.HashSet<String>();
        for (String[] a : portata(this)) pref.add(a[0]);
        final java.util.Map<U.AppInfo, Integer> rank = new java.util.HashMap<U.AppInfo, Integer>();
        List<U.AppInfo> out = new ArrayList<U.AppInfo>();
        for (U.AppInfo a : apps) {
            int s = Cerca.rank(a.label, q, pref.contains(a.pkg));
            if (s > 0) { rank.put(a, s); out.add(a); }
        }
        Collections.sort(out, new Comparator<U.AppInfo>() { public int compare(U.AppInfo a, U.AppInfo b) { return rank.get(b) - rank.get(a); } });
        return out;
    }

    // ---------- Righe ----------

    private TextView chip(String s, final Runnable r) {
        TextView t = U.text(this, s, 14, U.INK, false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setMaxWidth(U.dp(this, 150));
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(U.dp(this, 44));
        t.setPadding(U.dp(this, 14), 0, U.dp(this, 14), 0);
        t.setBackground(U.box(this, U.SURFACE, U.BORDER, 20));
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        return t;
    }

    private View row(String title, String sub, String right, String icon, final Runnable r) {
        LinearLayout row = U.row(this);
        row.setMinimumHeight(U.dp(this, 50));
        row.setPadding(0, U.dp(this, 4), 0, U.dp(this, 4));
        if (icon != null) {
            TextView ic = U.text(this, icon, 14, U.MUTED, false);
            ic.setGravity(Gravity.CENTER);
            ic.setBackground(U.box(this, U.BG, U.LINE, 8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(U.dp(this, 34), U.dp(this, 34));
            lp.rightMargin = U.dp(this, 12);
            row.addView(ic, lp);
        }
        LinearLayout tx = U.col(this);
        TextView t = U.text(this, title, 15, U.INK, false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        tx.addView(t);
        if (sub != null) tx.addView(U.text(this, sub, 12, U.MUTED, false));
        row.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (right != null) row.addView(U.text(this, right, 15, U.MUTED, false));
        row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        LinearLayout wrap = U.col(this);
        wrap.addView(row, U.full());
        wrap.addView(U.divider(this));
        return wrap;
    }

    private View personRow(final Hit h) {
        LinearLayout row = U.row(this);
        row.setMinimumHeight(U.dp(this, 54));
        String n0 = h.name.replace("[", "").trim();
        TextView ini = U.text(this, n0.length() > 0 ? n0.substring(0, 1).toUpperCase(Locale.ITALY) : "?", 13, U.INK, true);
        ini.setGravity(Gravity.CENTER);
        ini.setBackground(U.box(this, U.CHIP, 0, h.group ? 9 : 16));
        row.addView(ini, new LinearLayout.LayoutParams(U.dp(this, 32), U.dp(this, 32)));
        LinearLayout tx = U.col(this);
        tx.setPadding(U.dp(this, 12), 0, U.dp(this, 6), 0);
        TextView t = U.text(this, h.name, 15, U.INK, h.strong);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        tx.addView(t);
        if (h.sub != null) tx.addView(U.text(this, h.sub, 12, U.MUTED, false));
        row.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        // 0.13 (178): al posto delle sigle, l'icona della barra di stato; il nome finché l'icona non c'è
        if (h.badges != null && h.badges.length() > 0)
            for (String code : h.badges.split(" ")) if (code.length() > 0) row.addView(AppIcons.badge(this, AppIcons.pkgOfCode(this, code), Rules.appName(code), U.MUTED));
        if (h.contactId < 0) {
            TextView more = U.text(this, "⋯", 20, U.INK, false);
            more.setGravity(Gravity.CENTER);
            more.setContentDescription("Come passano i messaggi di " + h.name);
            more.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { Pass.sheet(SearchActivity.this, pkgOf(h.code), h.name, new Runnable() { public void run() { render(); } }); }
            });
            row.addView(more, new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 44)));
        }
        row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openHit(h); } });
        LinearLayout wrap = U.col(this);
        wrap.addView(row, U.full());
        wrap.addView(U.divider(this));
        return wrap;
    }

    private View convRow(Rules.Conv c) {
        Hit h = new Hit();
        h.name = Rules.groupName(c.name); h.code = c.code; h.group = c.group; h.badges = c.code;
        Rules.Person p = null;
        for (Rules.Person x : Rules.people(U.cfg(this, U.K_CONTACTS, U.DEF_CONTACTS))) if (Cerca.norm(x.name).equals(Cerca.norm(h.name))) p = x;
        h.person = p;
        h.sub = status(c, p, c.group ? U.groupRules(this).get(h.name.toLowerCase(Locale.ITALY)) : null, System.currentTimeMillis());
        return personRow(h);
    }

    static String pkgOf(String code) {
        if ("TG".equals(code)) return "org.telegram.messenger";
        if ("SMS".equals(code)) return "com.google.android.apps.messaging";
        return "com.whatsapp";
    }

    // ---------- Aprire ----------

    private void openHit(Hit h) {
        if (h.person != null) { openPerson(h.person); return; }
        if (h.contactId >= 0) { openContact(h.name, h.contactId); return; }
        // conversazione o gruppo visti nelle notifiche
        boolean ok = Convs.openChat(this, h.code, h.name);
        if (!ok && !h.group) ok = U.openChatFromContacts(this, h.name, h.code);
        if (!ok) {
            ok = U.launchPkg(this, U.chatPkg(this, h.code));
            if (ok && h.group) U.toast(this, "Cerca «" + h.name + "» nell'app: −minus apre il gruppo direttamente dal prossimo messaggio");
        }
        if (ok) { Convs.open(this, h.code, h.name, h.group); remember("person", h.name, h.name); }
        else U.toast(this, "Non riesco ad aprire questa conversazione");
    }

    private void openPerson(final Rules.Person p) {
        if (p.apps.size() <= 1) { openWith(p.name, p.apps.isEmpty() ? "WA" : p.apps.get(0), p.target); return; }
        final String[] names = new String[p.apps.size()];
        for (int i = 0; i < names.length; i++) names[i] = Rules.appName(p.apps.get(i));
        new AlertDialog.Builder(this).setTitle(p.name).setItems(names, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) { openWith(p.name, p.apps.get(w), p.target); }
        }).show();
    }

    /** Una persona trovata in rubrica: le app con cui è registrata, più la chiamata. */
    private void openContact(final String name, long id) {
        final String num = numberOf(id);
        final List<String> codes = U.detectedChatApps(this, name);
        if (num != null && num.length() > 0) { if (!codes.contains("TEL")) codes.add("TEL"); if (!codes.contains("SMS")) codes.add("SMS"); }
        if (codes.isEmpty()) { U.toast(this, "Nessun numero o app di chat per " + name); return; }
        final String[] labels = new String[codes.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = Rules.appName(codes.get(i));
        new AlertDialog.Builder(this).setTitle(name).setItems(labels, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) { openWith(name, codes.get(w), num); }
        }).show();
    }

    private void openWith(String name, String app, String target) {
        boolean ok = false;
        if ("WA".equals(app) || "TG".equals(app)) ok = Convs.openChat(this, app, name) || U.openChatFromContacts(this, name, app);
        if (!ok) {
            String t = target;
            if (t == null || t.replaceAll("[^0-9]", "").length() < 6 && !"TG".equals(app)) { String n = numberByName(name); if (n != null) t = n; }
            ok = ListActivity.contact(this, app, t, null);
        }
        if (ok) {
            if (!"TEL".equals(app) && !"MAIL".equals(app)) Convs.open(this, app, name, false);
            remember("person", name, name);
        }
    }

    private void openApp(String pkg, String label) {
        U.AppInfo a = null;
        if (apps == null) apps = U.sortedApps(this);
        for (U.AppInfo x : apps) if (x.pkg.equals(pkg)) { a = x; break; }
        if (a == null) { U.toast(this, label + " non è installata"); return; }
        if (U.open(this, a)) { remember("app", a.pkg, a.label); logOpen(tapped); }
        tapped = false;
    }

    /** 0.12: registro locale, solo conteggi: app aperte con un tocco «a portata» o scrivendone il nome. */
    static final String K_OPEN_TAP = "open_tap", K_OPEN_TYPED = "open_typed";
    private boolean tapped;

    private void logOpen(boolean tap) {
        String k = tap ? K_OPEN_TAP : K_OPEN_TYPED;
        U.prefs(this).edit().putInt(k, U.prefs(this).getInt(k, 0) + 1).apply();
    }

    /** 0.12.3: un recente porta dritto alla destinazione (prima, per le persone, riempiva solo il campo). */
    private void openRecent(String[] r) {
        if ("app".equals(r[0])) { openApp(r[1], r[2]); return; }
        if ("person".equals(r[0]))
            for (Hit h : peopleHits(r[1]))
                if (Cerca.norm(h.name).equals(Cerca.norm(r[1]))) { openHit(h); return; }
        // non più tra persone, gruppi e rubrica: si cerca, come prima
        query.setText(r[1]);
        query.setSelection(query.getText().length());
    }

    void openSetting(String dest) {
        Intent i;
        if (dest.startsWith("area:")) {
            i = new Intent(this, SettingsActivity.class);
            i.putExtra("area", Integer.parseInt(dest.substring(5)));
            i.putExtra("back", "Ricerca");
        } else if ("people".equals(dest)) i = SettingsActivity.peopleIntent(this, "Ricerca");
        else if ("groups".equals(dest)) i = new Intent(this, GroupsActivity.class);
        else if ("servizi".equals(dest)) i = new Intent(this, ServiziActivity.class);
        else if ("waiting".equals(dest)) { i = new Intent(this, ContainerActivity.class); i.putExtra("tab", ContainerActivity.TAB_WAITING); }
        else if ("portata".equals(dest)) i = new Intent(this, PortataActivity.class);
        else if ("pause".equals(dest)) i = new Intent(this, PauseActivity.class);
        else if ("session".equals(dest)) i = new Intent(this, SessionActivity.class);
        else if ("calendars".equals(dest)) i = new Intent(this, CalendarsActivity.class);
        else if ("voice".equals(dest)) i = new Intent(this, VoiceHelpActivity.class);
        else if ("changes".equals(dest)) i = new Intent(this, ChangesActivity.class);
        else if ("sticky".equals(dest)) i = new Intent(this, StickyActivity.class);
        else if ("bug".equals(dest)) i = new Intent(this, BugActivity.class);
        else if ("night".equals(dest)) i = new Intent(this, NightSettingsActivity.class);
        else if ("aspetto".equals(dest)) i = new Intent(this, AspettoActivity.class);
        else if ("wellbeing".equals(dest)) { Session.openWellbeing(this); return; }
        else if ("alarms".equals(dest)) { U.openAlarms(this); return; }
        else return;
        if (!i.hasExtra("back")) i.putExtra("back", "Ricerca");
        startActivity(i);
    }

    private void remember(String kind, String value, String label) {
        List<String> old = new ArrayList<String>();
        for (String[] r : recents(this)) old.add(r[0] + " | " + r[1] + " | " + r[2]);
        List<String> now = Cerca.pushRecent(old, kind + " | " + value.replace("|", "/") + " | " + label.replace("|", "/"));
        U.prefs(this).edit().putString(K_RECENT, U.lines(now)).apply();
    }

    static List<String[]> recents(Context c) {
        return U.rows(U.prefs(c).getString(K_RECENT, ""));
    }

    // ---------- A portata ----------

    /** Le app a portata: quelle scelte; la prima volta Maps e le app di Shop e Movimenti installate. */
    static List<String[]> portata(Context c) {
        List<String[]> out = new ArrayList<String[]>();
        String raw = U.prefs(c).getString(K_PORTATA, null);
        List<U.AppInfo> all = U.sortedApps(c);
        if (raw != null) {
            for (String[] r : U.rows(raw)) if (r.length >= 2) for (U.AppInfo a : all) if (a.pkg.equals(r[0])) { out.add(new String[]{a.pkg, a.label}); break; }
            return out;
        }
        List<String> names = new ArrayList<String>();
        names.add("Maps");
        for (String[] r : U.rows(U.cfg(c, U.K_SHOP, U.DEF_SHOP))) names.add(r[r.length - 1].replace("!", "").trim());
        for (String[] r : U.rows(U.cfg(c, U.K_MONEY, U.DEF_MONEY))) names.add(r[r.length - 1].trim());
        Set<String> seen = new java.util.HashSet<String>();
        for (String n : names) {
            if (out.size() >= MAX_PORTATA) break;
            for (U.AppInfo a : all) {
                if (Cerca.score(a.label, n) >= 2 && seen.add(a.pkg)) { out.add(new String[]{a.pkg, a.label}); break; }
            }
        }
        return out;
    }

    static void savePortata(Context c, List<String[]> list) {
        StringBuilder b = new StringBuilder("# pacchetto | nome\n");
        for (String[] a : list) b.append(a[0]).append(" | ").append(a[1].replace("|", "/")).append("\n");
        U.prefs(c).edit().putString(K_PORTATA, b.toString()).commit();
    }

    // ---------- Altro ----------

    private String numberOf(long contactId) {
        Cursor cur = null;
        try {
            cur = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE},
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[]{String.valueOf(contactId)}, null);
            String any = null;
            while (cur != null && cur.moveToNext()) {
                if (cur.getInt(1) == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) return cur.getString(0);
                if (any == null) any = cur.getString(0);
            }
            return any;
        } catch (Exception e) {
            return null;
        } finally {
            if (cur != null) cur.close();
        }
    }

    private String numberByName(String name) {
        if (!Onboarding.hasContacts(this)) return null;
        Cursor cur = null;
        try {
            cur = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " = ?", new String[]{name}, null);
            return cur != null && cur.moveToFirst() ? cur.getString(0) : null;
        } catch (Exception e) {
            return null;
        } finally {
            if (cur != null) cur.close();
        }
    }

    private void ask(String q) {
        if (U.installed(this, CLAUDE_PKG)) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, q);
            i.setPackage(CLAUDE_PKG);
            if (U.start(this, i)) return;
            if (U.launchPkg(this, CLAUDE_PKG)) return;
        }
        web(q);
    }

    private void web(String q) {
        Intent s = new Intent(Intent.ACTION_WEB_SEARCH);
        s.putExtra(android.app.SearchManager.QUERY, q);
        if (!U.start(this, s) && !U.start(this, new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))))
            U.toast(this, "Nessun browser disponibile");
    }

    private void store(String q) {
        if (!U.start(this, new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(q)))))
            U.start(this, new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=" + Uri.encode(q) + "&c=apps")));
    }

    private void command(String said) {
        Intent i = new Intent(this, VoiceActivity.class);
        i.putExtra("said", said);
        startActivity(i);
    }

    private void voice() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Di' un comando, ad esempio «Chiama mamma»");
        try { startActivityForResult(i, REQ_VOICE); } catch (Exception e) { U.toast(this, "Riconoscimento vocale non disponibile"); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_VOICE && res == RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) command(r.get(0));
        }
    }
}
