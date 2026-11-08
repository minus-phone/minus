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
public class ListActivity extends BaseActivity {

    public static final String EXTRA_MODE = "mode";
    public static final int MODE_CONTACTS = 1, MODE_SHOP = 2, MODE_MONEY = 3;

    private LinearLayout list;
    private int mode;
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
        String title = mode == MODE_CONTACTS ? "Conversazioni" : mode == MODE_SHOP ? "Shop" : "Movimenti";
        if (getIntent().getStringExtra("title") != null) title = getIntent().getStringExtra("title"); // 0.7: da Impostazioni
        LinearLayout root = U.screen(this, title);
        this.mode = mode;
        if (mode == MODE_CONTACTS) root.addView(searchField(), searchParams());
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
        search.setHint("Cerca una persona o un gruppo");
        search.setHintTextColor(U.MUTED);
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
    private static int modeOf(Entry e) {
        if (e.group) return Rules.groupAlways(e.groupRule) ? 0 : 1; // 0.6: gruppi «Passa sempre» o «In silenzio»
        if (e.person == null) return 1;
        if (e.person.section.equalsIgnoreCase("Sempre")) return 0;
        if (e.person.section.equalsIgnoreCase("Attesa")) return 2;
        return 1;
    }

    private void buildContacts() {
        long now = System.currentTimeMillis();
        int shown = 0;
        for (final Entry e : entries()) {
            if (filter.length() > 0 && !low(e.name).contains(filter)) continue;
            shown++;
            int m = modeOf(e);
            String status = null;
            if (e.until > now && m != 0) status = "aperta · " + Convs.until(e.until);
            else if (m == 2) status = "aspetta in Notifiche";
            else if (m == 0) status = "passa sempre";
            else if (e.group) status = "in silenzio";
            String badge;
            if (e.person != null && e.person.apps.size() > 0) {
                StringBuilder b = new StringBuilder();
                for (String a : e.person.apps) { if (b.length() > 0) b.append(" · "); b.append(a); }
                badge = b.toString();
            } else badge = e.code;
            View r = convRow(e.name, e.group, status, status != null && status.startsWith("aperta"), badge);
            // 0.6.1: un pulsante visibile per scegliere come passa (prima solo tenendo premuto)
            TextView more = U.text(this, "⋯", 20, U.INK, false);
            more.setGravity(Gravity.CENTER);
            more.setContentDescription("Come passano i messaggi di " + e.name);
            more.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { sheet(e); }
            });
            ((LinearLayout) r).addView(more, new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 44)));
            r.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { openEntry(e); }
            });
            r.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) { sheet(e); return true; }
            });
        }
        if (shown == 0) {
            TextView t = U.text(this, filter.length() > 0 ? "Nessuna conversazione con questo nome." : "Ancora nessuna conversazione.", 15, U.MUTED, false);
            t.setPadding(0, U.dp(this, 16), 0, U.dp(this, 8));
            list.addView(t);
        }
        if (filter.length() == 0) {
            List<String[]> apps = U.rows(U.prefs(this).getString(U.K_RW_APPS, ""));
            if (!apps.isEmpty()) {
                list.addView(U.label(this, "App"));
                for (String[] r : apps) appRow(r[0]);
            }
        }
        TextView all = U.text(this, "Tutti i contatti  ›", 15, U.INK, false);
        all.setMinHeight(U.dp(this, 48));
        all.setGravity(Gravity.CENTER_VERTICAL);
        all.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!U.start(ListActivity.this, new Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI)))
                    U.toast(ListActivity.this, "Non trovo l'app Contatti");
            }
        });
        list.addView(all, U.full());
        TextView hint = U.text(this, "In ordine di ultimo messaggio. Quando scrivi a qualcuno da qui, da Notifiche, a voce o rispondendo dalla notifica, i suoi messaggi passano per 12 ore. Con ⋯ scegli come passano, o togli una riga dalla lista.", 12, U.MUTED, false);
        hint.setPadding(0, U.dp(this, 8), 0, U.dp(this, 8));
        list.addView(hint);
    }

    private View convRow(String name, boolean group, String status, boolean open, String badge) {
        LinearLayout r = U.row(this);
        r.setMinimumHeight(U.dp(this, 60));
        r.setPadding(0, U.dp(this, 8), 0, U.dp(this, 8));
        String n0 = name.replace("[", "").trim();
        TextView ini = U.text(this, n0.length() > 0 ? n0.substring(0, 1).toUpperCase(java.util.Locale.ITALY) : "?", 14, U.INK, true);
        ini.setGravity(Gravity.CENTER);
        ini.setBackground(U.box(this, U.CHIP, 0, group ? 10 : 18));
        ini.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        r.addView(ini, new LinearLayout.LayoutParams(U.dp(this, 36), U.dp(this, 36)));
        LinearLayout txt = U.col(this);
        txt.setPadding(U.dp(this, 12), 0, U.dp(this, 8), 0);
        txt.addView(U.text(this, name, 17, U.INK, false));
        if (status != null) {
            TextView st = U.text(this, (open ? "○ " : "") + status, 13, U.MUTED, false);
            txt.addView(st);
        }
        r.addView(txt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (badge != null) {
            // 0.15: le icone delle app al posto delle sigle WA, TG, SMS (il nome per intero se l'icona non c'è ancora)
            LinearLayout icons = U.row(this);
            StringBuilder said = new StringBuilder();
            for (String code : badge.split(" · ")) {
                if (code.trim().length() == 0) continue;
                View b = AppIcons.badge(this, AppIcons.pkgOfCode(this, code.trim()), Rules.appName(code.trim()), U.MUTED);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = U.dp(this, 6);
                icons.addView(b, lp);
                if (said.length() > 0) said.append(", ");
                said.append(Rules.appName(code.trim()));
            }
            r.addView(icons);
            name = name + " (" + said + ")";
        }
        r.setContentDescription(name + (group ? ", gruppo" : "") + (status != null ? ", " + status : "") + ". Tieni premuto per scegliere come passano i messaggi");
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
            if (noTarget) for (String a : e.person.apps) if (Convs.openChat(this, a, e.person.name)) { Convs.open(this, a, e.person.name, null); return; }
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
        if (ok) Convs.open(this, e.code, e.name, e.group);
    }

    /** Scheda: come passano i suoi messaggi. */
    private void sheet(final Entry e) {
        final String[] modes = e.group ? new String[]{"Passa sempre", "In silenzio (passa finché ci scrivi)"}
                : new String[]{"Passano sempre", "Passano finché vi scrivete", "Aspettano in Notifiche"};
        final int cur = modeOf(e);
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
        new AlertDialog.Builder(this)
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
        new AlertDialog.Builder(this)
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
        String desc = e.group ? e.name + (m == 0 ? ": passa sempre" : ": in silenzio")
                : m == 0 ? e.name + ": passa sempre" : m == 2 ? e.name + ": aspetta in Notifiche" : e.name + ": passa finché vi scrivete";
        Changes.track(this, desc, new String[]{e.group ? U.K_GROUPS : U.K_CONTACTS}, new Runnable() {
            public void run() { applyMode(e, m); }
        }, new Runnable() {
            public void run() { list.removeAllViews(); buildContacts(); }
        });
    }

    private void applyMode(Entry e, int m) {
        if (e.group) {
            U.setGroupRule(this, e.name, m == 0 ? "sempre" : "silenzio");
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
            if (openPerson(p, a)) Convs.open(this, a, p.name, false);
            return;
        }
        final String[] names = new String[p.apps.size()];
        for (int i = 0; i < names.length; i++) names[i] = Rules.appName(p.apps.get(i));
        new AlertDialog.Builder(this).setTitle(p.name).setItems(names, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                if (openPerson(p, p.apps.get(w))) Convs.open(ListActivity.this, p.apps.get(w), p.name, false);
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
