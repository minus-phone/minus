package it.essenziale.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Le schermate contenitore: Conversazioni, Shop, Movimenti. */
public class ListActivity extends PageActivity {

    public static final String EXTRA_MODE = "mode";
    public static final int MODE_CONTACTS = 1, MODE_SHOP = 2, MODE_MONEY = 3;

    private LinearLayout list;
    private int mode;
    private int tab;            // 0.18: 0 Contatti, 1 Gruppi
    private boolean az;         // 0.18: «Tutti…, dalla A alla Z»
    private TextView tabC, tabG;
    private android.widget.EditText searchBox;
    private String filter = "";

    @Override
    protected void onResume() {
        super.onResume();
        if (mode == MODE_CONTACTS && list != null) { list.removeAllViews(); buildContacts(); }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int mode = getIntent().getIntExtra(EXTRA_MODE, MODE_CONTACTS);
        String title = mode == MODE_CONTACTS ? "Contatti e gruppi" : mode == MODE_SHOP ? "Shop" : "Movimenti";
        if (getIntent().getStringExtra("title") != null) title = getIntent().getStringExtra("title"); // 0.7: da Impostazioni
        tab = getIntent().getIntExtra("tab", 0);
        az = getIntent().getBooleanExtra("az", false);
        if (mode == MODE_CONTACTS) title = az ? (tab == 1 ? "Tutti i gruppi" : "Tutti i contatti") : "Contatti e gruppi";
        LinearLayout root = U.screen(this, title);
        this.mode = mode;
        if (mode == MODE_CONTACTS) {
            if (!az) root.addView(tabs(), U.full()); // 0.18 (Evolutiva 009): Contatti | Gruppi
            root.addView(searchField(), searchParams());
        }
        ScrollView sv = new ScrollView(this);
        list = U.col(this);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        if (mode == MODE_CONTACTS) { /* costruita in onResume */ }
        else if (mode == MODE_SHOP) buildApps(U.rows(U.cfg(this, U.K_SHOP, U.DEF_SHOP)), false);
        else buildApps(U.rows(U.cfg(this, U.K_MONEY, U.DEF_MONEY)), true);
        if (mode != MODE_CONTACTS) {
            TextView hint = U.text(this, "Per pagare: sblocca e avvicina il telefono al POS.", 12, U.MUTED, false);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, U.dp(this, 10), 0, 0);
            root.addView(hint, U.full());
        }
        setContentView(root);
    }

    private TextView row(String name, String badge, boolean dim) {
        LinearLayout r = U.row(this);
        r.setMinimumHeight(U.dp(this, 54));
        PressB.row(r); // 0.21: riga senza bordo (Shop, Movimenti): si scurisce il fondo
        TextView t = U.text(this, name, 17, dim ? U.MUTED : U.INK, false);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (badge != null) {
            TextView bd = U.text(this, badge, 12, U.MUTED, false);
            bd.setTypeface(U.SERIF);
            bd.setPadding(U.dp(this, 6), U.dp(this, 2), U.dp(this, 6), U.dp(this, 2));
            bd.setBackground(U.box(this, U.BG, U.LINE, 6));
            r.addView(bd);
        }
        list.addView(r, U.full());
        list.addView(U.divider(this));
        // il TextView restituito serve solo come "maniglia" per il click: usiamo la riga
        r.setTag(t);
        return t;
    }

    private void onRowClick(TextView anchor, View.OnClickListener l) {
        ((View) anchor.getParent()).setOnClickListener(l);
    }

    // ---------- Conversazioni (0.4) ----------
    // Una lista sola, come WhatsApp: le persone scelte, i gruppi che passano sempre e le conversazioni
    // aperte (a cui hai scritto nelle ultime 12 ore), in ordine di ultimo messaggio.

    /** Una riga della lista: una persona scelta, un gruppo o una conversazione aperta. */
    private static class Entry {
        String name, code;          // code: WA, TG, SMS (null per le persone con più app)
        Rules.Person person;        // se è tra le persone scelte
        boolean group;
        String groupRule;           // sempre / silenzio (0.5: menzioni / attesa) / null
        long last, until;
    }

    private android.widget.EditText searchField() {
        android.widget.EditText search = new android.widget.EditText(this);
        searchBox = search;
        search.setHint(tab == 1 ? "Cerca un gruppo" : "Cerca un contatto");
        search.setHintTextColor(U.HINT);
        search.setTextColor(U.INK);
        search.setTextSize(16);
        search.setSingleLine(true);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        search.setBackground(U.box(this, U.SURFACE, U.BORDER, 22));
        search.setPadding(U.dp(this, 16), 0, U.dp(this, 16), 0);
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {
                filter = s.toString().trim().toLowerCase(java.util.Locale.ITALY);
                list.removeAllViews();
                buildContacts();
            }
        });
        return search;
    }

    private LinearLayout.LayoutParams searchParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, U.dp(this, 46));
        lp.topMargin = U.dp(this, 6);
        lp.bottomMargin = U.dp(this, 6);
        return lp;
    }

    private static String low(String s) { return s.trim().toLowerCase(java.util.Locale.ITALY); }

    private List<Entry> entries() {
        long now = System.currentTimeMillis();
        java.util.Map<String, Entry> byName = new java.util.LinkedHashMap<String, Entry>();
        List<Rules.Conv> convs = Convs.load(this);
        // persone scelte, nell'ordine della lista
        for (Rules.Person p : Rules.people(U.cfg(this, U.K_CONTACTS, U.DEF_CONTACTS))) {
            if (p.name.length() == 0 || p.name.endsWith("...")) continue;
            Entry e = new Entry();
            e.name = p.name; e.person = p;
            e.code = p.apps.size() == 1 ? p.apps.get(0) : null;
            byName.put(low(p.name), e);
        }
        // gruppi che passano sempre
        java.util.Map<String, String> seenApp = new java.util.HashMap<String, String>();
        for (String[] r : U.rows(U.prefs(this).getString(U.K_SEEN_GROUPS, ""))) if (r.length >= 2) seenApp.put(low(Rules.groupName(r[0])), codeOfLabel(r[1]));
        for (String[] g : U.rows(U.prefs(this).getString(U.K_GROUPS, ""))) {
            String gn = Rules.groupName(g[0]);
            if (g.length < 2 || !"sempre".equals(g[1]) || byName.containsKey(low(gn))) continue;
            Entry e = new Entry();
            e.name = gn; e.group = true; e.groupRule = "sempre"; e.code = seenApp.get(low(gn));
            byName.put(low(gn), e);
        }
        // attività recente: ordina e aggiunge le conversazioni aperte
        for (Rules.Conv c : convs) {
            String k = low(Rules.groupName(c.name));
            Entry e = byName.get(k);
            if (e == null) {
                // 0.6.1: anche chi ti ha scritto negli ultimi 14 giorni, così puoi scegliere come passa
                if (!c.isOpen(now) && now - c.last > 14L * 24 * 3600 * 1000) continue;
                e = new Entry();
                e.name = c.name; e.code = c.code; e.group = c.group;
                byName.put(k, e);
            }
            if (e.person != null && !e.person.apps.contains(c.code) && e.person.apps.size() > 0) continue;
            if (e.person == null && (e.code == null || c.last >= e.last)) e.code = c.code;
            if (c.group) e.group = true;
            e.last = Math.max(e.last, Math.max(c.last, c.until - Rules.OPEN_MS));
            if (c.isOpen(now) && c.until > e.until) { e.until = c.until; if (e.person == null) e.code = c.code; }
        }
        java.util.Map<String, String> rules = U.groupRules(this);
        List<Entry> out = new java.util.ArrayList<Entry>(byName.values());
        for (Entry e : out) if (e.group && e.groupRule == null) e.groupRule = rules.get(low(e.name));
        // le più recenti prima; a parità (mai scritte) resta l'ordine della lista
        final java.util.Map<Entry, Integer> pos = new java.util.HashMap<Entry, Integer>();
        for (int i = 0; i < out.size(); i++) pos.put(out.get(i), i);
        java.util.Collections.sort(out, new java.util.Comparator<Entry>() {
            public int compare(Entry a, Entry b) {
                if (a.last != b.last) return Long.compare(b.last, a.last);
                return pos.get(a) - pos.get(b);
            }
        });
        return out;
    }

    /** Il modo in cui passano i suoi messaggi: 0 sempre, 1 finché vi scrivete, 2 aspettano in Notifiche. */
    private static int modeOf(android.content.Context c, Entry e) {
        if (e.group) { // 0.16: gruppi «Passa sempre», «In Per te», «In Altro»
            if (Rules.groupAlways(e.groupRule)) return 0;
            return "in Altro".equals(Pass.state(c, true, e.groupRule, e.code, null, 0, 1)) ? 2 : 1;
        }
        if (e.person == null) return 1;
        if (e.person.section.equalsIgnoreCase("Sempre")) return 0;
        if (e.person.section.equalsIgnoreCase("Attesa")) return 2;
        return 1;
    }

    /** 0.18 (Evolutiva 009): i due tab, con le stesse sezioni. */
    private View tabs() {
        LinearLayout r = U.row(this);
        tabC = tabItem("Contatti", 0);
        tabG = tabItem("Gruppi", 1);
        r.addView(tabC, new LinearLayout.LayoutParams(0, U.dp(this, 48), 1f));
        r.addView(tabG, new LinearLayout.LayoutParams(0, U.dp(this, 48), 1f));
        LinearLayout wrap = U.col(this);
        wrap.addView(r, U.full());
        wrap.addView(U.divider(this));
        styleTabs();
        return wrap;
    }

    private TextView tabItem(String label, final int t) {
        TextView v = U.text(this, label, 15, U.INK, false);
        v.setGravity(Gravity.CENTER);
        v.setOnClickListener(new View.OnClickListener() {
            public void onClick(View x) {
                if (tab == t) return;
                tab = t;
                styleTabs();
                if (searchBox != null) { searchBox.setText(""); searchBox.setHint(tab == 1 ? "Cerca un gruppo" : "Cerca un contatto"); }
                list.removeAllViews();
                buildContacts();
            }
        });
        return v;
    }

    private void styleTabs() {
        if (tabC == null) return;
        TextView[] ts = {tabC, tabG};
        for (int i = 0; i < 2; i++) {
            boolean on = i == tab;
            ts[i].setTextColor(on ? U.INK : U.MUTED);
            ts[i].setTypeface(on ? U.SANS_B : U.SANS);
            android.graphics.drawable.GradientDrawable line = new android.graphics.drawable.GradientDrawable();
            line.setColor(on ? U.INK : 0);
            android.graphics.drawable.LayerDrawable bg = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{line});
            bg.setLayerInset(0, 0, U.dp(this, 45), 0, 0);
            PressB.rowOn(ts[i], on ? bg : null); // 0.21: la scheda si scurisce da premuta (almeno 240 ms); la linea sotto resta quella della scelta
            ts[i].setContentDescription((i == 0 ? "Contatti" : "Gruppi") + ", scheda" + (on ? ", selezionata" : ""));
        }
    }

    /** Nomi in rubrica (minuscoli), per «non in rubrica» e per «Tutti i contatti». */
    private java.util.LinkedHashMap<String, String> rubrica() {
        java.util.LinkedHashMap<String, String> out = new java.util.LinkedHashMap<String, String>();
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return out;
        android.database.Cursor cur = null;
        try {
            cur = getContentResolver().query(android.provider.ContactsContract.Contacts.CONTENT_URI,
                    new String[]{android.provider.ContactsContract.Contacts.DISPLAY_NAME}, null, null,
                    android.provider.ContactsContract.Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC");
            while (cur != null && cur.moveToNext()) {
                String n = cur.getString(0);
                if (n != null && n.trim().length() > 0) out.put(low(n), n.trim());
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return out;
    }

    private void section(String title) {
        TextView h = U.label(this, title);
        h.setPadding(0, U.dp(this, 14), 0, U.dp(this, 4));
        U.heading(h);
        list.addView(h);
    }

    /**
     * 0.18 (Evolutiva 009): Contatti e Gruppi, con le stesse sezioni: Aperte ora, Passano sempre,
     * Ti hanno scritto di recente, poi «Tutti…, dalla A alla Z ›». Una sezione vuota non si mostra.
     * Un gruppo aperto sta solo in Gruppi.
     */
    private void buildContacts() {
        final long now = System.currentTimeMillis();
        final boolean groups = tab == 1;
        java.util.LinkedHashMap<String, String> rub = groups ? new java.util.LinkedHashMap<String, String>() : rubrica();
        List<Entry> mine = new java.util.ArrayList<Entry>();
        for (Entry e : entries()) if (e.group == groups && (filter.length() == 0 || low(e.name).contains(filter))) mine.add(e);
        if (az) {
            java.util.Set<String> have = new java.util.HashSet<String>();
            for (Entry e : mine) have.add(low(e.name));
            if (groups) {
                for (String[] r : U.rows(U.prefs(this).getString(U.K_SEEN_GROUPS, ""))) {
                    if (r.length < 1) continue;
                    String gn = Rules.groupName(r[0]);
                    if (have.contains(low(gn)) || (filter.length() > 0 && !low(gn).contains(filter))) continue;
                    Entry e = new Entry(); e.name = gn; e.group = true; e.code = r.length >= 2 ? codeOfLabel(r[1]) : null;
                    e.groupRule = U.groupRules(this).get(low(gn));
                    mine.add(e); have.add(low(gn));
                }
            } else for (String n : rub.values()) {
                if (have.contains(low(n)) || (filter.length() > 0 && !low(n).contains(filter))) continue;
                Entry e = new Entry(); e.name = n;
                mine.add(e); have.add(low(n));
            }
            final java.text.Collator col = java.text.Collator.getInstance(java.util.Locale.ITALY);
            java.util.Collections.sort(mine, new java.util.Comparator<Entry>() {
                public int compare(Entry a, Entry b) { return col.compare(a.name, b.name); }
            });
            for (Entry e : mine) entryRow(e, rub, now);
            if (mine.isEmpty()) empty(groups);
            return;
        }
        List<Entry> open = new java.util.ArrayList<Entry>(), always = new java.util.ArrayList<Entry>(), recent = new java.util.ArrayList<Entry>();
        for (Entry e : mine) {
            if (e.until > now) open.add(e);
            else if (modeOf(this, e) == 0) always.add(e);
            else if (e.last > 0) recent.add(e);
        }
        if (!open.isEmpty()) { section("Aperte ora"); for (Entry e : open) entryRow(e, rub, now); }
        if (!always.isEmpty()) { section("Preferiti"); for (Entry e : always) entryRow(e, rub, now); }
        if (!recent.isEmpty()) { section("Ti hanno scritto di recente"); for (Entry e : recent) entryRow(e, rub, now); }
        if (open.isEmpty() && always.isEmpty() && recent.isEmpty()) empty(groups);
        // 0.24 (Evolutiva 018): una sola voce, «Tutti i contatti ›» (A–Z con ricerca e lo stato di ogni riga); niente «+ Aggiungi dalla rubrica».
        // Senza il permesso dei contatti diventa «Scegli dalla rubrica ›»: il selettore con il riquadro del permesso al suo posto.
        final boolean noRubrica = !groups && !Onboarding.hasContacts(this);
        TextView all = U.text(this, noRubrica ? "Scegli dalla rubrica  ›" : (groups ? "Tutti i gruppi" : "Tutti i contatti") + "  ›", 15, U.INK, false);
        all.setMinHeight(U.dp(this, 48));
        all.setGravity(Gravity.CENTER_VERTICAL);
        PressB.row(all);
        all.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (noRubrica) { startActivity(SelectorActivity.intent(ListActivity.this, Scegli.PEOPLE, "Contatti e gruppi")); return; }
                startActivity(new Intent(ListActivity.this, ListActivity.class).putExtra(EXTRA_MODE, MODE_CONTACTS)
                        .putExtra("tab", tab).putExtra("az", true).putExtra("back", "Contatti e gruppi"));
            }
        });
        list.addView(all, U.full());
        TextView hint = U.text(this, "Tocca lo stato per cambiarlo.", 12, U.MUTED, false);
        hint.setPadding(0, U.dp(this, 4), 0, U.dp(this, 8));
        list.addView(hint);
    }

    private void empty(boolean groups) {
        TextView t = U.text(this, filter.length() > 0 ? (groups ? "Nessun gruppo con questo nome." : "Nessun contatto con questo nome.")
                : (groups ? "Ancora nessun gruppo: compaiono con i loro primi messaggi." : "Ancora nessun contatto."), 15, U.MUTED, false);
        t.setPadding(0, U.dp(this, 16), 0, U.dp(this, 8));
        list.addView(t);
    }

    private void entryRow(final Entry e, java.util.Map<String, String> rub, long now) {
        String status = Pass.state(this, e.group, e.groupRule, e.code, e.person, e.until, now);
        if (status == null) status = "in Per te";
        String badge;
        if (e.person != null && e.person.apps.size() > 0) {
            StringBuilder b = new StringBuilder();
            for (String a : e.person.apps) { if (b.length() > 0) b.append(" · "); b.append(a); }
            badge = b.toString();
        } else badge = e.code;
        boolean outside = !e.group && e.person == null && !rub.isEmpty() && !rub.containsKey(low(e.name));
        View r = convRow(e.name, e.group, status, badge, outside, new Runnable() { public void run() { sheet(e); } });
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openEntry(e); }
        });
        r.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { sheet(e); return true; }
        });
    }

    private View convRow(String name, boolean group, String status, String badge, boolean outside, final Runnable onStatus) {
        LinearLayout r = U.row(this);
        r.setMinimumHeight(U.dp(this, 60));
        r.setPadding(0, U.dp(this, 8), 0, U.dp(this, 8));
        View ini = group ? U.groupBadge(this, 36) : U.initial(this, name, false, 36); // 0.26 (Evolutiva 021): i gruppi, quadrato con l'icona
        r.addView(ini, new LinearLayout.LayoutParams(U.dp(this, 36), U.dp(this, 36)));
        PressB.rowWithBadge(r, ini); // 0.21: riga premuta: fondo più scuro e il cerchio dell'iniziale si riempie d'inchiostro
        LinearLayout txt = U.col(this);
        txt.setPadding(U.dp(this, 12), 0, U.dp(this, 8), 0);
        TextView nm = U.text(this, name, 17, U.INK, false);
        nm.setSingleLine(true);
        nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
        txt.addView(nm);
        StringBuilder apps = new StringBuilder();
        if (badge != null) for (String code : badge.split(" · ")) {
            if (code.trim().length() == 0) continue;
            if (apps.length() > 0) apps.append(" · ");
            apps.append(Rules.appName(code.trim()));
        }
        // 0.17: sotto il nome tornano le icone delle app (il nome solo se l'icona non c'è ancora), poi «· gruppo»
        LinearLayout icons = U.row(this);
        icons.setPadding(0, U.dp(this, 3), 0, 0);
        if (badge != null) for (String code : badge.split(" · ")) {
            if (code.trim().length() == 0) continue;
            View b = AppIcons.badge(this, AppIcons.pkgOfCode(this, code.trim()), Rules.appName(code.trim()), U.MUTED);
            b.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            if (icons.getChildCount() == 0) b.setPadding(0, 0, b.getPaddingRight(), 0);
            icons.addView(b, b.getLayoutParams() != null ? b.getLayoutParams() : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, U.dp(this, 18)));
        }
        if (group) {
            apps.append(apps.length() > 0 ? " · gruppo" : "gruppo");
            icons.addView(U.text(this, icons.getChildCount() > 0 ? " · gruppo" : "gruppo", 12, U.MUTED, false));
        }
        if (outside) { // 0.18: chi ti scrive senza essere in rubrica (WhatsApp, Telegram)
            apps.append(apps.length() > 0 ? " · non in rubrica" : "non in rubrica");
            icons.addView(U.text(this, icons.getChildCount() > 0 ? " · non in rubrica" : "non in rubrica", 12, U.MUTED, false));
        }
        if (icons.getChildCount() > 0) txt.addView(icons);
        r.addView(txt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (status != null) {
            TextView st = U.text(this, "", 13, U.INK, false);
            Pass.asLink(st, status); // 0.18: «○ fino alle…», il pallino non è sottolineato
            st.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
            st.setMinHeight(U.dp(this, 48));
            st.setPadding(U.dp(this, 8), 0, 0, 0);
            st.setContentDescription(name + ": " + Pass.spoken(status) + ". Cambia come passano i messaggi");
            PressB.link(st); // 0.21: lo stato è un link: anello di 6 dp
            st.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { onStatus.run(); } });
            r.addView(st);
        }
        r.setContentDescription(name + (apps.length() > 0 ? " (" + apps + ")" : "") + (status != null ? ", " + Pass.spoken(status) : ""));
        list.addView(r, U.full());
        list.addView(U.divider(this));
        return r;
    }

    /** Codice di chat dal nome dell'app (come salvato tra i gruppi visti). */
    private static String codeOfLabel(String label) {
        String l = label == null ? "" : label.toLowerCase(java.util.Locale.ITALY);
        if (l.contains("whatsapp")) return "WA";
        if (l.contains("telegram")) return "TG";
        if (l.contains("messag")) return "SMS";
        return null;
    }

    /** Pacchetto da aprire per un codice di chat: quello visto nelle notifiche, o quello installato. */
    private String pkgFor(String code, String name) {
        String seen = Convs.pkg(code, name);
        if (seen != null && getPackageManager().getLaunchIntentForPackage(seen) != null) return seen;
        return U.chatPkg(this, code);
    }

    /** Numero di cellulare in rubrica per quel nome, se c'è. */
    private String numberOf(String name) {
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return null;
        android.database.Cursor cur = null;
        try {
            cur = getContentResolver().query(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER, android.provider.ContactsContract.CommonDataKinds.Phone.TYPE},
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " = ?", new String[]{name}, null);
            String any = null;
            while (cur != null && cur.moveToNext()) {
                if (cur.getInt(1) == android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) return cur.getString(0);
                if (any == null) any = cur.getString(0);
            }
            return any;
        } catch (Exception e) {
            return null;
        } finally {
            if (cur != null) cur.close();
        }
    }

    private void openEntry(Entry e) {
        if (e.person != null) {
            // un gruppo salvato tra le persone (senza numero né username): provo con l'ultima notifica
            boolean noTarget = e.person.target.replaceAll("[^0-9]", "").length() < 6 && !e.person.target.trim().matches("@?[A-Za-z][A-Za-z0-9_]{3,}");
            if (noTarget) for (String a : e.person.apps) if (Convs.openChat(this, a, e.person.name)) { Convs.started(this, a, e.person.name, null); return; }
            choose(e.person);
            return;
        }
        // 1. l'azione dell'ultima notifica: apre proprio quel gruppo o quella chat
        boolean ok = e.code != null && Convs.openChat(this, e.code, e.name);
        // 1b. 0.6.2: la riga «Messaggio a …» che Telegram e WhatsApp aggiungono in rubrica
        if (!ok && !e.group && e.code != null) ok = U.openChatFromContacts(this, e.name, e.code);
        if (!ok && e.code == null) for (String c : new String[]{"WA", "TG", "SMS"}) if (!ok && Convs.openChat(this, c, e.name)) { ok = true; e.code = c; }
        // 2. per le persone, il numero in rubrica
        if (!ok && !e.group && e.code != null) {
            String num = numberOf(e.name);
            if (num != null) ok = contact(this, e.code, num, null);
        }
        // 3. l'app, sulla lista delle chat
        if (!ok) {
            ok = U.launchPkg(this, pkgFor(e.code, e.name));
            if (!ok && e.code == null) for (String c : new String[]{"WA", "TG"}) if (!ok) ok = U.launchPkg(this, U.chatPkg(this, c));
            if (ok && e.group) U.toast(this, "Cerca «" + e.name + "» nell'app. Dal prossimo messaggio del gruppo, −minus lo apre direttamente");
            else if (!ok) U.toast(this, "Non riesco ad aprire questa conversazione");
        }
        if (ok) Convs.started(this, e.code, e.name, e.group);
    }

    /** Scheda: come passano i suoi messaggi. */
    private void sheet(final Entry e) {
        final String[] modes = e.group ? new String[]{"Preferito · chiamate e messaggi passano sempre", "In Per te", "In Altro"}
                : new String[]{"Preferito · chiamate e messaggi passano sempre", "In Per te · passa mentre vi scrivete", "In Per te · anche mentre vi scrivete"};
        final int cur = modeOf(this, e);
        final boolean open = e.until > System.currentTimeMillis();
        // 0.6.3: una lista sola di azioni: come passano, con quale app vi scrivete, chiudi, togli
        final List<String> items = new java.util.ArrayList<String>();
        for (int i = 0; i < modes.length; i++) items.add((i == cur ? "✓  " : "     ") + modes[i]);
        final int appsAt = items.size();
        if (!e.group) items.add("Vi scrivete con: " + appsLabel(e) + "  ›");
        final int closeAt = items.size();
        if (open) items.add("Chiudi ora la conversazione aperta");
        final int removeAt = items.size();
        items.add("Togli dalla lista");
        U.dlg(this)
                .setTitle(e.name)
                .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        if (w < modes.length) { if (w != cur) setMode(e, w); }
                        else if (!e.group && w == appsAt) chooseApps(e);
                        else if (open && w == closeAt) {
                            if (e.person != null) for (String a : e.person.apps) Convs.close(ListActivity.this, a, e.name);
                            else Convs.close(ListActivity.this, e.code, e.name);
                            list.removeAllViews();
                            buildContacts();
                        } else if (w == removeAt) remove(e);
                    }
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private String appsLabel(Entry e) {
        List<String> apps = e.person != null ? e.person.apps : java.util.Collections.singletonList(e.code == null ? "WA" : e.code);
        StringBuilder b = new StringBuilder();
        for (String a : apps) { if (b.length() > 0) b.append(", "); b.append(Rules.appName(a)); }
        return b.toString();
    }

    /** 0.6.3: scegliere le app con cui scrivi a una persona (WhatsApp, Telegram, SMS, chiamata). */
    private void chooseApps(final Entry e) {
        final String[] codes = {"WA", "TG", "SMS", "TEL"};
        final String[] names = {"WhatsApp", "Telegram", "SMS", "Chiamata"};
        List<String> cur = e.person != null ? e.person.apps : U.chatAppsOf(this, e.name, e.code);
        final boolean[] on = new boolean[codes.length];
        for (int i = 0; i < codes.length; i++) on[i] = cur.contains(codes[i]);
        U.dlg(this)
                .setTitle("Con " + e.name + " vi scrivete con")
                .setMultiChoiceItems(names, on, new DialogInterface.OnMultiChoiceClickListener() {
                    public void onClick(DialogInterface d, int w, boolean c) { on[w] = c; }
                })
                .setPositiveButton("Fatto", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        final List<String> sel = new java.util.ArrayList<String>();
                        for (int i = 0; i < codes.length; i++) if (on[i]) sel.add(codes[i]);
                        if (sel.isEmpty()) { U.toast(ListActivity.this, "Scegli almeno un'app"); return; }
                        StringBuilder lab = new StringBuilder();
                        for (String c : sel) { if (lab.length() > 0) lab.append(", "); lab.append(Rules.appName(c)); }
                        Changes.track(ListActivity.this, e.name + ": " + lab, new String[]{U.K_CONTACTS}, new Runnable() {
                            public void run() {
                                List<Rules.Person> people = Rules.people(U.cfg(ListActivity.this, U.K_CONTACTS, U.DEF_CONTACTS));
                                Rules.Person p = null;
                                for (Rules.Person x : people) if (low(x.name).equals(low(e.name))) p = x;
                                if (p == null) {
                                    p = new Rules.Person();
                                    p.name = e.name.replace("|", "/");
                                    p.section = "Aperta";
                                    String num = numberOf(e.name);
                                    p.target = num == null ? "" : num;
                                    people.add(p);
                                }
                                p.apps.clear();
                                p.apps.addAll(sel);
                                U.prefs(ListActivity.this).edit().putString(U.K_CONTACTS, Rules.peopleText(people)).commit();
                            }
                        }, new Runnable() {
                            public void run() { list.removeAllViews(); buildContacts(); }
                        });
                    }
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void remove(final Entry e) {
        Changes.track(this, e.name + ": tolto dalla lista", new String[]{U.K_CONTACTS, U.K_GROUPS, Convs.KEY}, new Runnable() {
            public void run() {
                if (e.person != null) {
                    List<Rules.Person> people = Rules.people(U.cfg(ListActivity.this, U.K_CONTACTS, U.DEF_CONTACTS));
                    for (int i = people.size() - 1; i >= 0; i--) if (low(people.get(i).name).equals(low(e.name))) people.remove(i);
                    U.prefs(ListActivity.this).edit().putString(U.K_CONTACTS, Rules.peopleText(people)).commit();
                }
                if (e.group) U.setGroupRule(ListActivity.this, e.name, null);
                Convs.forget(ListActivity.this, e.code, e.name);
                Convs.forget(ListActivity.this, null, e.name);
            }
        }, new Runnable() {
            public void run() { list.removeAllViews(); buildContacts(); }
        });
    }

    private void setMode(final Entry e, final int m) {
        // 0.6: ogni scelta si annulla subito e si ritrova in Modifiche recenti
        String desc = e.name + (m == 0 ? ": preferito" : e.group && m == 2 ? ": in Altro" : ": in Per te");
        Changes.track(this, desc, e.group ? new String[]{U.K_GROUPS, Convs.KEY} : new String[]{U.K_CONTACTS}, new Runnable() {
            public void run() { applyMode(e, m); }
        }, new Runnable() {
            public void run() { list.removeAllViews(); buildContacts(); }
        });
    }

    private void applyMode(Entry e, int m) {
        if (e.group) {
            U.setGroupRule(this, e.name, m == 0 ? "sempre" : m == 2 ? "altro" : "notifiche");
            if (m > 0 && e.code != null) Convs.close(this, e.code, e.name);
            if (m == 2) Store.moveGroup(this, e.name);
        } else {
            List<Rules.Person> people = Rules.people(U.cfg(this, U.K_CONTACTS, U.DEF_CONTACTS));
            Rules.Person p = null;
            for (Rules.Person x : people) if (low(x.name).equals(low(e.name))) { p = x; break; }
            if (p == null) {
                p = new Rules.Person();
                p.name = e.name.replace("|", "/");
                p.apps.add(e.code == null ? "WA" : e.code);
                String num = numberOf(e.name);
                p.target = num == null ? "" : num;
                people.add(0, p);
            }
            p.section = m == 0 ? "Sempre" : m == 2 ? "Attesa" : "Aperta";
            U.prefs(this).edit().putString(U.K_CONTACTS, Rules.peopleText(people)).commit();
        }
    }

    /** 0.6.2: prima la chat dall'ultima notifica, poi dalla rubrica, poi numero o username. */
    private boolean openPerson(Rules.Person p, String app) {
        if (("TG".equals(app) || "WA".equals(app)) && Convs.openChat(this, app, p.name)) return true;
        if (U.openChatFromContacts(this, p.name, app)) return true;
        String target = p.target;
        if ((target == null || digits(target).length() < 6) && !"TG".equals(app)) { String n = numberOf(p.name); if (n != null) target = n; }
        if ("TG".equals(app) && (target == null || target.trim().length() == 0)) { String n = numberOf(p.name); if (n != null) target = n; }
        return contact(this, app, target, null);
    }

    /** Con più app per la stessa persona chiedo quale usare. */
    private void choose(final Rules.Person p) {
        if (p.apps.size() <= 1) {
            String a = p.apps.isEmpty() ? "WA" : p.apps.get(0);
            if (openPerson(p, a)) Convs.started(this, a, p.name, false);
            return;
        }
        final String[] names = new String[p.apps.size()];
        for (int i = 0; i < names.length; i++) names[i] = Rules.appName(p.apps.get(i));
        U.dlg(this).setTitle(p.name).setItems(names, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                if (openPerson(p, p.apps.get(w))) Convs.started(ListActivity.this, p.apps.get(w), p.name, false);
            }
        }).show();
    }

    private static String digits(String s) {
        return s.replaceAll("[^0-9]", "");
    }

    /** Apre la conversazione con quella persona su quell'app; con text, il messaggio è già scritto (lo invii tu). */
    static boolean contact(Activity a, String app, String target, String text) {
        boolean ok;
        if (target == null) target = "";
        boolean hasNumber = digits(target).length() >= 6;
        String enc = "";
        try { if (text != null) enc = java.net.URLEncoder.encode(text, "UTF-8").replace("+", "%20"); } catch (Exception ignored) {}
        if (app.equals("WA")) {
            if (hasNumber) {
                String url = "https://wa.me/" + digits(target) + (text != null ? "?text=" + enc : "");
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                i.setPackage("com.whatsapp");
                ok = U.start(a, i);
                if (!ok) { i.setPackage("com.whatsapp.w4b"); ok = U.start(a, i); }
                if (!ok) ok = U.start(a, new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } else if (text != null) {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                i.setPackage("com.whatsapp");
                ok = U.start(a, i);
            } else ok = U.launchPkg(a, U.chatPkg(a, "WA"));
        } else if (app.equals("TG")) {
            if (text != null) {
                // Telegram non accetta un destinatario insieme al testo: scegli tu la chat, il testo è pronto
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                i.setPackage(U.chatPkg(a, "TG"));
                ok = U.start(a, i);
            } else if (target.length() > 0) {
                String uri = hasNumber && target.trim().matches("[+0-9 ]+")
                        ? "tg://resolve?phone=" + digits(target)
                        : "tg://resolve?domain=" + target.replace("@", "").trim();
                ok = U.start(a, new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
            } else ok = U.launchPkg(a, U.chatPkg(a, "TG"));
        } else if (app.equals("SMS")) {
            Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + target.replace(" ", "")));
            if (text != null) i.putExtra("sms_body", text);
            ok = U.start(a, i);
        } else if (app.equals("TEL")) {
            ok = U.start(a, new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + target.replace(" ", ""))));
        } else if (app.equals("MAIL")) {
            ok = U.launchPkg(a, "com.google.android.gm");
            if (!ok) {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_APP_EMAIL);
                ok = U.start(a, i);
            }
        } else {
            ok = U.open(a, U.findApp(a, app));
        }
        if (!ok) U.toast(a, "Non riesco ad aprire questa conversazione");
        return ok;
    }

    // ---------- Shop / Movimenti ----------

    private void buildApps(List<String[]> rows, boolean sectioned) {
        String section = null;
        for (String[] r : rows) {
            String name;
            if (sectioned && r.length >= 2) {
                if (!r[0].equals(section)) { section = r[0]; list.addView(U.label(this, section)); }
                name = r[1];
            } else name = r[r.length - 1];
            appRow(name);
        }
    }

    private void appRow(String name) {
        final String clean = name.replace("!", "").trim();
        final U.AppInfo app = U.findApp(this, clean);
        boolean paused = app != null && U.isPaused(this, app.pkg);
        TextView t = row(clean, app == null ? "non installata" : (paused ? "pausa 6 s" : null), app == null);
        onRowClick(t, new View.OnClickListener() {
            public void onClick(View v) {
                if (app == null) { U.toast(ListActivity.this, clean + " non è installata"); return; }
                U.open(ListActivity.this, app);
            }
        });
    }
}
