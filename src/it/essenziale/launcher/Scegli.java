package it.essenziale.launcher;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 0.24 (Evolutiva 018): UN SOLO selettore per app, persone e gruppi, nel primo avvio e nelle Impostazioni.
 * Dall'alto: «Cerca»; il riquadro del permesso al posto dell'elenco (persone senza rubrica); «Scelte · n»;
 * «Proposte, già scelte · n» (al massimo 4, ognuna col suo motivo, un tocco la toglie); poi tutte, dalla A alla Z.
 * La casella a destra vuol dire dentro o fuori; il chip c'è solo per le scelte che si escludono (Pausa o Scopo;
 * Passa sempre, In Per te, In Altro). Si salva al tocco, con «Aggiunta: X · Annulla» (Changes.track).
 *
 * Nel primo avvio le proposte sono già scelte (e restano proposte finché dura); nelle Impostazioni, dove ci sono già
 * le scelte di sempre, le proposte nuove stanno nella loro sezione ma non si aggiungono da sole: un tocco le aggiunge.
 */
final class Scegli {

    static final int PEOPLE = 0, SERVICES = 1, GROUPS = 2, DOOM = 3, NIGHT = 4, APPNOTIF = 5;
    static final String[] KINDS = {"persone", "servizi", "gruppi", "doom", "notte", "appnotif"};

    /** Una riga: persona, gruppo o app. */
    static final class Item {
        String key, label, sub, pkg, code, number;
        long id = -1, last;
        boolean group, starred;
    }

    /** Chi ospita il selettore (primo avvio o pagina). */
    interface Host {
        void grantContacts();      // «Concedi l'accesso ai contatti»
        void pickFromAndroid();    // «Oppure scegli dalla rubrica di Android»
    }

    /** I dati di un tipo di scelta. */
    private abstract static class Source {
        abstract List<Item> all(Activity a);
        abstract Set<String> loadOn(Activity a);
        abstract void set(Activity a, Item i, boolean on);
        void applyProposal(Activity a, Item i) { set(a, i, true); }
        abstract String[] keys();
        abstract String allTitle();
        String chosenTitle() { return "Scelte"; }
        boolean applies() { return true; }                       // nel primo avvio le proposte si applicano subito
        List<Proposte.Cand> propose(Activity a, List<Item> all, Set<String> on, long now) { return new ArrayList<Proposte.Cand>(); }
        View extra(Activity a, Item i, Runnable refresh) { return null; }  // il chip delle scelte che si escludono
        boolean female() { return false; }
        boolean propsAreOn() { return false; }                   // Servizi: le proposte sono già scelte da sole
    }

    private final Activity act;
    private final int kind;
    private final boolean onboarding;
    private final Host host;
    private final Source src;
    final LinearLayout root;
    private final EditText q;
    private final LinearLayout permBox, list;
    private List<Item> all = new ArrayList<Item>();
    private Set<String> on = new HashSet<String>();
    private List<Item> props = new ArrayList<Item>();
    private final Map<String, String> propReason = new HashMap<String, String>();

    /** Le chiavi proposte durante il primo avvio: riaprendo il selettore dal riepilogo le proposte restano al loro posto. */
    private static final Map<Integer, List<String>> SESSION = new HashMap<Integer, List<String>>();
    private static final Map<Integer, Map<String, String>> SESSION_REASON = new HashMap<Integer, Map<String, String>>();

    static void resetSession() { SESSION.clear(); SESSION_REASON.clear(); }

    Scegli(Activity a, int kind, boolean onboarding, Host host) {
        this.act = a;
        this.kind = kind;
        this.onboarding = onboarding;
        this.host = host;
        this.src = source(kind);
        root = U.col(a);
        q = new EditText(a);
        q.setHint("Cerca");
        q.setHintTextColor(U.HINT);
        q.setTextColor(U.INK);
        q.setTextSize(15 * U.textScale);
        q.setTypeface(U.SANS);
        q.setSingleLine(true);
        q.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        q.setMinHeight(U.dp(a, 48));
        q.setBackground(U.box(a, U.SURFACE, U.BORDER, 24));
        q.setPadding(U.dp(a, 18), U.dp(a, 10), U.dp(a, 18), U.dp(a, 10));
        q.setContentDescription("Cerca");
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable e) { refresh(); }
        });
        LinearLayout.LayoutParams qlp = U.full();
        qlp.bottomMargin = U.dp(a, 6);
        root.addView(q, qlp);
        permBox = U.col(a);
        root.addView(permBox, U.full());
        list = U.col(a);
        root.addView(list, U.full());
        reload();
    }

    /** Rilegge i dati (dopo un permesso, dopo un'altra pagina) e ridisegna. */
    void reload() {
        all = src.all(act);
        on = src.loadOn(act);
        computeProps();
        refresh();
    }

    int kind() { return kind; }

    // ---------- Proposte ----------

    private String seenKey() { return "scegli_gia_" + KINDS[kind]; }

    private void computeProps() {
        props = new ArrayList<Item>();
        propReason.clear();
        List<String> keys;
        Map<String, String> reasons;
        final int sk = sessionKey();
        if (onboarding && SESSION.containsKey(sk)) {
            keys = SESSION.get(sk);
            reasons = SESSION_REASON.get(sk);
        } else {
            keys = new ArrayList<String>();
            reasons = new HashMap<String, String>();
            Set<String> seen = new HashSet<String>();
            if (src.applies()) for (String[] r : U.rows(U.prefs(act).getString(seenKey(), ""))) if (r.length > 0) seen.add(r[0]);
            List<Proposte.Cand> cs = new ArrayList<Proposte.Cand>();
            for (Proposte.Cand c : src.propose(act, all, on, System.currentTimeMillis())) if (!seen.contains(c.key)) cs.add(c);
            for (Proposte.Cand c : Proposte.top(cs, Proposte.MAX)) { keys.add(c.key); reasons.put(c.key, c.reason); }
            if (onboarding && src.applies() && !keys.isEmpty()) {
                // già scelte: si salvano subito (una sola volta), e si ricordano per non riproporle se le togli
                Changes.record(act, "Proposte accettate: " + keys.size(), src.keys());
                StringBuilder b = new StringBuilder(U.prefs(act).getString(seenKey(), ""));
                for (String k : keys) {
                    Item it = find(k);
                    if (it != null) src.applyProposal(act, it);
                    b.append(k.replace("|", "/")).append('\n');
                }
                U.prefs(act).edit().putString(seenKey(), b.toString()).apply();
                on = src.loadOn(act);
            }
            if (onboarding) { SESSION.put(sk, keys); SESSION_REASON.put(sk, reasons); }
        }
        for (String k : keys) {
            Item it = find(k);
            if (it != null) { props.add(it); propReason.put(k, reasons.get(k)); }
        }
    }

    /** Le persone con la rubrica collegata si propongono di nuovo (le stelle), anche se senza rubrica erano già state proposte. */
    private int sessionKey() { return kind + (kind == PEOPLE && Onboarding.hasContacts(act) ? 100 : 0); }

    private Item find(String key) {
        for (Item i : all) if (i.key.equals(key)) return i;
        return null;
    }

    private boolean isOn(Item i) { return on.contains(i.key); }

    /** Nel primo avvio le proposte sono scelte («già scelte»); nelle Impostazioni, o su Servizi, valgono per quel che sono. */
    private boolean propsChecked() { return onboarding || src.propsAreOn(); }

    // ---------- Disegno ----------

    void refresh() {
        permBox.removeAllViews();
        if (kind == PEOPLE && !Onboarding.hasContacts(act)) permBox();
        list.removeAllViews();
        String query = q.getText().toString().trim();
        if (query.length() > 0) {
            List<Item> hits = new ArrayList<Item>();
            for (Item i : all) if (Cerca.score(i.label, query) > 0) hits.add(i);
            heading("Risultati · " + hits.size(), null);
            int n = 0;
            for (Item i : hits) { if (n++ >= 60) break; row(i, null); }
            if (hits.isEmpty()) hint(kind == GROUPS ? "Nessun gruppo con questo nome." : kind == PEOPLE ? "Nessuna persona con questo nome." : "Nessuna app con questo nome.");
            return;
        }
        Set<String> inProps = new HashSet<String>();
        for (Item i : props) inProps.add(i.key);
        List<Item> chosen = new ArrayList<Item>();
        for (Item i : all) if (isOn(i) && !inProps.contains(i.key)) chosen.add(i);
        if (!chosen.isEmpty()) {
            heading(src.chosenTitle() + " · " + chosen.size(), null);
            for (Item i : chosen) row(i, null);
        }
        if (!props.isEmpty()) {
            heading(propsChecked() ? "Proposte, già scelte · " + props.size() : "Proposte · " + props.size(),
                    propsChecked() ? "Un tocco per toglierle." : "Un tocco per aggiungerle.");
            for (Item i : props) row(i, propReason.get(i.key));
        }
        boolean none = all.isEmpty();
        if (kind == PEOPLE && !Onboarding.hasContacts(act)) {
            if (chosen.isEmpty() && props.isEmpty()) hint("Ancora nessuno.");
            return;
        }
        if (none) {
            hint(kind == GROUPS ? "Ancora nessun gruppo: compaiono qui appena arriva un loro messaggio." + (U.listenerEnabled(act) ? "" : " Serve l'accesso alle notifiche, che si dà più avanti.")
                    : "Nessuna.");
            return;
        }
        heading(src.allTitle(), null);
        int n = 0;
        for (Item i : all) {
            if (n++ >= 300) { hint("Cerca per trovare le altre."); break; }
            row(i, null);
        }
    }

    private void heading(String t, String sub) {
        TextView h = U.label(act, t);
        h.setPadding(0, U.dp(act, 16), 0, U.dp(act, 4));
        U.heading(h);
        list.addView(h);
        if (sub != null) {
            TextView s = U.text(act, sub, 13, U.MUTED, false);
            s.setPadding(0, 0, 0, U.dp(act, 4));
            list.addView(s);
        }
    }

    private void hint(String t) {
        TextView h = U.text(act, t, 14, U.MUTED, false);
        h.setPadding(0, U.dp(act, 12), 0, U.dp(act, 12));
        list.addView(h);
    }

    private void row(final Item it, String reason) {
        final boolean now = isOn(it);
        LinearLayout r = U.row(act);
        r.setMinimumHeight(U.dp(act, 56));
        final PressB.Face face;
        if (kind == PEOPLE || kind == GROUPS) {
            TextView ini = U.initial(act, it.label, it.group, 36);
            face = PressB.rowWithBadge(r, ini); // riga premuta: fondo più scuro e il cerchio dell'iniziale si riempie
            r.addView(ini, new LinearLayout.LayoutParams(U.dp(act, 36), U.dp(act, 36)));
        } else {
            face = PressB.row(r);
            r.addView(AppIcons.view(act, it.pkg, 28), AppIcons.lp(act, 28));
        }
        LinearLayout tx = U.col(act);
        tx.setPadding(U.dp(act, 12), U.dp(act, 6), U.dp(act, 8), U.dp(act, 6));
        TextView t = U.text(act, it.label, 15, U.INK, false);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tx.addView(t);
        String sub = reason != null ? reason : it.sub;
        if (sub != null && sub.length() > 0) tx.addView(U.text(act, sub, 12, U.MUTED, false));
        r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (now) {
            View ex = src.extra(act, it, new Runnable() { public void run() { reload(); } });
            if (ex != null) r.addView(ex);
        }
        final TaskBox cb = PressB.box(act, now); // la casella dei task: riempimento, spunta, rimbalzo
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(U.dp(act, 20), U.dp(act, 20));
        clp.leftMargin = U.dp(act, 8);
        r.addView(cb, clp);
        PressB.boxHalo(face, cb);
        r.setContentDescription(it.label + (sub != null && sub.length() > 0 ? ", " + sub : "") + (now ? ", scelto" : ", non scelto"));
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                PressB.checkThen(v, cb, !now, new Runnable() {
                    public void run() { change(it, !now); }
                });
            }
        });
        list.addView(r, U.full());
        list.addView(U.divider(act));
    }

    /** Salva al tocco: «Aggiunta: X · Annulla». */
    private void change(final Item it, final boolean to) {
        Changes.track(act, Proposte.added(it.label, to), src.keys(),
                new Runnable() { public void run() { src.set(act, it, to); } },
                new Runnable() { public void run() { on = src.loadOn(act); refresh(); } });
    }

    // ---------- Persone senza rubrica ----------

    private void permBox() {
        LinearLayout c = U.col(act);
        c.setPadding(U.dp(act, 16), U.dp(act, 14), U.dp(act, 16), U.dp(act, 12));
        c.setBackground(U.box(act, U.SURFACE, U.BORDER, 16));
        TextView h = U.text(act, "La rubrica non è collegata", 15, U.INK, true);
        c.addView(h);
        TextView d = U.text(act, "Senza accesso ai contatti non posso proporti i preferiti né mettere la stella. Puoi aggiungere le persone a mano.", 13, U.MUTED, false);
        d.setPadding(0, U.dp(act, 4), 0, U.dp(act, 10));
        c.addView(d);
        TextView grant = U.text(act, "Concedi l'accesso ai contatti", 15, U.INK, true);
        grant.setGravity(Gravity.CENTER);
        grant.setMinHeight(U.dp(act, 48));
        Press.outlined(grant, U.BG, U.INK, 24);
        grant.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (host != null) host.grantContacts(); } });
        c.addView(grant, U.full());
        TextView pick = U.text(act, "Oppure scegli dalla rubrica di Android", 14, U.INK, false);
        pick.setPaintFlags(pick.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        pick.setGravity(Gravity.CENTER);
        pick.setMinHeight(U.dp(act, 48));
        PressB.link(pick);
        pick.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (host != null) host.pickFromAndroid(); } });
        LinearLayout.LayoutParams plp = U.wrap();
        plp.gravity = Gravity.CENTER_HORIZONTAL;
        c.addView(pick, plp);
        LinearLayout.LayoutParams lp = U.full();
        lp.topMargin = U.dp(act, 12);
        permBox.addView(c, lp);
    }

    /** La persona scelta nella rubrica di Android (ACTION_PICK): entra tra chi conta, con «Aggiunta: X · Annulla». */
    void picked(Uri u) {
        Cursor cur = null;
        try {
            cur = act.getContentResolver().query(u, new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null);
            if (cur != null && cur.moveToFirst()) {
                String name = cur.getString(0), num = cur.getString(1);
                if (name == null) name = num;
                if (name == null) return;
                final Item it = new Item();
                it.label = name;
                it.key = low(name);
                it.number = num == null ? "" : num;
                if (find(it.key) == null) all.add(0, it);
                change(it, true);
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
    }

    // ---------- Conteggi per le righe «3 scelti ›» ----------

    static int count(Activity a, int kind) {
        switch (kind) {
            case PEOPLE: return Onboarding.alwaysCount(a);
            case SERVICES: return ServiziActivity.installed(a).size();
            case DOOM: return U.pausedPkgs(a).size();
            case NIGHT: return Night.nightApps(a).size();
            case APPNOTIF: return U.appChoices(a).size();
            default: {
                int n = 0;
                Map<String, String> rules = U.groupRules(a);
                for (GroupsActivity.G g : GroupsActivity.groups(a).values()) if (Rules.groupAlways(rules.get(low(g.name)))) n++;
                return n;
            }
        }
    }

    static String countText(Activity a, int kind) {
        return Proposte.count(count(a, kind), kind == SERVICES || kind == APPNOTIF);
    }

    static String title(int kind) {
        switch (kind) {
            case PEOPLE: return "Chi conta";
            case SERVICES: return "Servizi";
            case GROUPS: return "Gruppi che passano sempre";
            case DOOM: return "App che rapiscono";
            case NIGHT: return "App frenate di notte";
            default: return "Notifiche delle app";
        }
    }

    static String low(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ITALY); }

    // ---------- I dati di ogni tipo ----------

    private static Source source(int kind) {
        switch (kind) {
            case PEOPLE: return new PeopleSource();
            case SERVICES: return new ServicesSource();
            case GROUPS: return new GroupsSource();
            case DOOM: return new DoomSource();
            case NIGHT: return new NightSource();
            default: return new AppNotifSource();
        }
    }

    private static List<Item> apps(Activity a, boolean noChat) {
        List<Item> out = new ArrayList<Item>();
        for (U.AppInfo x : U.sortedApps(a)) {
            if (a.getPackageName().equals(x.pkg)) continue;
            if (noChat && Rules.chatCode(x.pkg) != null) continue;
            Item i = new Item();
            i.key = x.pkg; i.label = x.label; i.pkg = x.pkg;
            String k = OnboardingActivity.kind(x);
            i.sub = U.isSocial(x.pkg) ? "social" : OnboardingActivity.isVideo(x) ? "video" : k != null && k.length() > 0 ? k : null;
            out.add(i);
        }
        return out;
    }

    // Persone

    private static final class PeopleSource extends Source {
        String allTitle() { return "Tutti i contatti"; }
        String[] keys() { return new String[]{U.K_CONTACTS}; }

        List<Item> all(Activity a) {
            LinkedHashMap<String, Item> out = new LinkedHashMap<String, Item>();
            if (Onboarding.hasContacts(a)) {
                Cursor cur = null;
                try {
                    cur = a.getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
                            new String[]{ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts.STARRED},
                            null, null, ContactsContract.Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC");
                    while (cur != null && cur.moveToNext() && out.size() < 2000) {
                        String n = cur.getString(1);
                        if (n == null || n.trim().length() == 0) continue;
                        String k = low(n);
                        if (out.containsKey(k)) continue;
                        Item i = new Item();
                        i.key = k; i.label = n.trim(); i.id = cur.getLong(0); i.starred = cur.getInt(2) == 1;
                        out.put(k, i);
                    }
                } catch (Exception ignored) {
                } finally {
                    if (cur != null) cur.close();
                }
            }
            for (Rules.Person p : Rules.people(U.cfg(a, U.K_CONTACTS, U.DEF_CONTACTS))) {
                if (!p.section.equalsIgnoreCase("Sempre") || p.target.contains("...") || out.containsKey(low(p.name))) continue; // gli esempi non contano
                Item i = new Item();
                i.key = low(p.name); i.label = p.name; i.number = p.target;
                out.put(i.key, i);
            }
            return new ArrayList<Item>(out.values());
        }

        Set<String> loadOn(Activity a) {
            Set<String> s = new HashSet<String>();
            for (Rules.Person p : Rules.people(U.cfg(a, U.K_CONTACTS, U.DEF_CONTACTS)))
                if (p.section.equalsIgnoreCase("Sempre") && !p.target.contains("...")) s.add(low(p.name));
            return s;
        }

        List<Proposte.Cand> propose(Activity a, List<Item> all, Set<String> on, long now) {
            List<Proposte.Cand> out = new ArrayList<Proposte.Cand>();
            Map<String, Rules.Conv> conv = new HashMap<String, Rules.Conv>();
            for (Rules.Conv c : Convs.load(a)) if (!c.group) {
                Rules.Conv old = conv.get(low(c.name));
                if (old == null || c.last > old.last) conv.put(low(c.name), c);
            }
            boolean rubrica = Onboarding.hasContacts(a);
            Set<String> have = new HashSet<String>();
            for (Item i : all) {
                have.add(i.key);
                if (on.contains(i.key)) continue;
                Rules.Conv c = conv.get(i.key);
                long last = c == null ? 0 : c.last, mine = c == null ? 0 : c.mine;
                String r = Proposte.reasonPerson(i.starred, last, mine, now);
                if (r != null) out.add(new Proposte.Cand(i.key, i.label, r, Proposte.scorePerson(i.starred, last, mine, now)));
            }
            if (!rubrica) {
                // senza rubrica le proposte vengono da chi ti ha scritto: la persona si aggiunge all'elenco
                for (Map.Entry<String, Rules.Conv> e : conv.entrySet()) {
                    if (have.contains(e.getKey()) || on.contains(e.getKey())) continue;
                    Rules.Conv c = e.getValue();
                    String r = Proposte.reasonPerson(false, c.last, c.mine, now);
                    if (r == null) continue;
                    Item i = new Item();
                    i.key = e.getKey(); i.label = c.name;
                    all.add(i);
                    out.add(new Proposte.Cand(i.key, i.label, r, Proposte.scorePerson(false, c.last, c.mine, now)));
                }
            }
            return out;
        }

        void set(Activity a, Item it, boolean onNow) {
            List<Rules.Person> list = Rules.people(U.cfg(a, U.K_CONTACTS, U.DEF_CONTACTS));
            boolean sample = !U.prefs(a).contains(U.K_CONTACTS);
            List<Rules.Person> out = new ArrayList<Rules.Person>();
            Rules.Person mine = null;
            for (Rules.Person p : list) {
                if (sample && (p.target.contains("...") || "Contatto".equals(p.name))) continue; // niente esempi
                if (low(p.name).equals(it.key)) { mine = p; if (!onNow && p.section.equalsIgnoreCase("Sempre")) continue; }
                out.add(p);
            }
            if (onNow) {
                // 0.6.3: se la persona c'era già, le app scelte restano (le app scelte restano: si cambia solo la sezione)
                if (mine == null) {
                    mine = new Rules.Person();
                    mine.name = it.label.replace("|", "/");
                    String num = it.number != null && it.number.length() > 0 ? it.number : it.id >= 0 ? numberOf(a, it.id) : "";
                    mine.target = num;
                    mine.apps.addAll(U.chatAppsOf(a, it.label, null));       // WhatsApp e Telegram dalla rubrica
                    if (num.replaceAll("[^0-9]", "").length() >= 6 && !mine.apps.contains("TEL")) mine.apps.add("TEL");
                    out.add(0, mine);
                }
                mine.section = "Sempre";
            }
            U.prefs(a).edit().putString(U.K_CONTACTS, Rules.peopleText(out)).commit();
        }

        private static String numberOf(Activity a, long contactId) {
            Cursor cur = null;
            try {
                cur = a.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE},
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[]{String.valueOf(contactId)}, null);
                String any = "";
                while (cur != null && cur.moveToNext()) {
                    if (cur.getInt(1) == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) return cur.getString(0);
                    if (any.length() == 0) any = cur.getString(0);
                }
                return any;
            } catch (Exception e) {
                return "";
            } finally {
                if (cur != null) cur.close();
            }
        }
    }

    // Servizi: le app riconosciute sono già scelte, da sole

    private static final class ServicesSource extends Source {
        String allTitle() { return "Tutte le app"; }
        String[] keys() { return new String[]{U.K_SERVICES}; }
        boolean female() { return true; }
        boolean applies() { return false; }
        boolean propsAreOn() { return true; }

        List<Item> all(Activity a) { return apps(a, false); }

        Set<String> loadOn(Activity a) {
            Set<String> s = new HashSet<String>();
            for (U.AppInfo x : ServiziActivity.installed(a)) s.add(x.pkg);
            return s;
        }

        List<Proposte.Cand> propose(Activity a, List<Item> all, Set<String> on, long now) {
            Set<String> manual = new HashSet<String>();
            for (String line : U.cfg(a, U.K_SERVICES, U.DEF_SERVICES).split("\n")) {
                String l = line.trim();
                if (l.startsWith("pkg:")) manual.add(l.substring(4).trim());
                else if (l.startsWith("!pkg:")) manual.add(l.substring(5).trim());
            }
            List<Proposte.Cand> out = new ArrayList<Proposte.Cand>();
            for (Item i : all) {
                if (!on.contains(i.key) || manual.contains(i.pkg)) continue; // solo ciò che −minus ha riconosciuto da sé
                String cat = Proposte.serviceCategory(i.label, i.pkg);
                out.add(new Proposte.Cand(i.key, i.label, Proposte.reasonService(cat), Proposte.scoreService(cat)));
            }
            return out;
        }

        void set(Activity a, Item it, boolean onNow) { ServiziActivity.set(a, it.pkg, onNow); }
    }

    // Gruppi

    private static final class GroupsSource extends Source {
        String allTitle() { return "Tutti i gruppi"; }
        String chosenTitle() { return "Scelti"; }
        String[] keys() { return new String[]{U.K_GROUPS}; }

        List<Item> all(Activity a) {
            List<Item> out = new ArrayList<Item>();
            for (GroupsActivity.G g : GroupsActivity.groups(a).values()) {
                Item i = new Item();
                i.key = low(g.name); i.label = g.name; i.group = true; i.last = g.last; i.code = g.code;
                i.sub = Rules.appName(g.code == null ? "WA" : g.code);
                out.add(i);
            }
            java.text.Collator col = java.text.Collator.getInstance(Locale.ITALY);
            final java.text.Collator cc = col;
            java.util.Collections.sort(out, new java.util.Comparator<Item>() {
                public int compare(Item x, Item y) { return cc.compare(x.label, y.label); }
            });
            return out;
        }

        Set<String> loadOn(Activity a) {
            Set<String> s = new HashSet<String>();
            Map<String, String> rules = U.groupRules(a);
            for (GroupsActivity.G g : GroupsActivity.groups(a).values()) if (Rules.groupAlways(rules.get(low(g.name)))) s.add(low(g.name));
            return s;
        }

        List<Proposte.Cand> propose(Activity a, List<Item> all, Set<String> on, long now) {
            List<Proposte.Cand> out = new ArrayList<Proposte.Cand>();
            for (Item i : all) {
                if (on.contains(i.key)) continue;
                String r = Proposte.reasonGroup(i.last, now);
                if (r != null) out.add(new Proposte.Cand(i.key, i.label, r, Proposte.scoreGroup(i.last, now)));
            }
            return out;
        }

        void set(Activity a, Item it, boolean onNow) { U.setGroupRule(a, it.label, onNow ? "sempre" : "silenzio"); }
    }

    // App che rapiscono: casella = in pausa; chip = Pausa o Scopo

    private static final class DoomSource extends Source {
        String allTitle() { return "Tutte le app"; }
        String[] keys() { return new String[]{U.K_PAUSE, Scope.K_LEVEL}; }
        List<Item> all(Activity a) { return apps(a, false); }

        Set<String> loadOn(Activity a) {
            Set<String> s = new HashSet<String>(U.pausedPkgs(a));
            s.remove(a.getPackageName());
            return s;
        }

        List<Proposte.Cand> propose(Activity a, List<Item> all, Set<String> on, long now) {
            List<Proposte.Cand> out = new ArrayList<Proposte.Cand>();
            boolean explicit = U.prefs(a).contains(U.K_PAUSE); // senza scelte salvate, i social «in pausa» sono solo il predefinito
            for (Item i : all) {
                if (explicit && on.contains(i.key)) continue;
                boolean social = U.isSocial(i.pkg), video = OnboardingActivity.isVideo(app(i));
                String r = Proposte.reasonApp(social, video);
                if (r != null) out.add(new Proposte.Cand(i.key, i.label, r, Proposte.scoreApp(social, video)));
            }
            return out;
        }

        private static U.AppInfo app(Item i) {
            U.AppInfo x = new U.AppInfo();
            x.label = i.label; x.pkg = i.pkg;
            return x;
        }

        void set(Activity a, Item it, boolean onNow) {
            int lv = Scope.dayLevel(a, it.pkg);
            Scope.setLevel(a, it.pkg, !onNow ? Scopo.NONE : lv == Scopo.NONE ? Scopo.PAUSE : lv);
        }

        /** Le proposte del primo avvio partono con «pausa e scopo», come nel primo avvio di prima: il chip le cambia in «Pausa». */
        void applyProposal(Activity a, Item it) { Scope.setLevel(a, it.pkg, Scopo.SCOPE); }

        View extra(final Activity a, final Item it, final Runnable refresh) {
            final int lv = Scope.dayLevel(a, it.pkg);
            if (lv == Scopo.NONE) return null;
            TextView t = U.text(a, lv == Scopo.SCOPE ? "Scopo" : "Pausa", 13, U.INK, false);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(U.dp(a, 40));
            t.setPadding(U.dp(a, 12), 0, U.dp(a, 12), 0);
            Press.chip(t, U.BG, U.BORDER, 18);
            t.setContentDescription(it.label + ": " + (lv == Scopo.SCOPE ? "pausa e scopo" : "pausa") + ". Tocca per cambiare");
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    final int to = lv == Scopo.SCOPE ? Scopo.PAUSE : Scopo.SCOPE;
                    Changes.track(a, it.label + ": " + (to == Scopo.SCOPE ? "pausa e scopo" : "pausa"), new String[]{U.K_PAUSE, Scope.K_LEVEL},
                            new Runnable() { public void run() { Scope.setLevel(a, it.pkg, to); } }, refresh);
                }
            });
            return t;
        }
    }

    // App frenate di notte

    private static final class NightSource extends Source {
        String allTitle() { return "Tutte le app"; }
        String[] keys() { return new String[]{Night.K_APPS}; }
        List<Item> all(Activity a) { return apps(a, false); }
        Set<String> loadOn(Activity a) { return new HashSet<String>(Night.nightApps(a)); }

        List<Proposte.Cand> propose(Activity a, List<Item> all, Set<String> on, long now) {
            List<Proposte.Cand> out = new ArrayList<Proposte.Cand>();
            boolean explicit = U.prefs(a).contains(Night.K_APPS);
            for (Item i : all) {
                if (explicit && on.contains(i.key)) continue;
                U.AppInfo x = new U.AppInfo();
                x.label = i.label; x.pkg = i.pkg;
                boolean social = U.isSocial(i.pkg), video = OnboardingActivity.isVideo(x);
                String r = Proposte.reasonApp(social, video);
                if (r != null) out.add(new Proposte.Cand(i.key, i.label, r, Proposte.scoreApp(social, video)));
            }
            return out;
        }

        void set(Activity a, Item it, boolean onNow) { Night.setNightApp(a, it.pkg, onNow); }
    }

    // Notifiche delle app: casella = ha una scelta; chip = quale (Passa sempre, In Per te, In Altro)

    private static final class AppNotifSource extends Source {
        String allTitle() { return "Tutte le app"; }
        String[] keys() { return new String[]{U.K_APP_CHOICE}; }
        boolean female() { return true; }
        boolean applies() { return false; }
        List<Item> all(Activity a) { return apps(a, true); }
        Set<String> loadOn(Activity a) { return new HashSet<String>(U.appChoices(a).keySet()); }

        void set(Activity a, Item it, boolean onNow) { U.setAppChoice(a, it.pkg, onNow ? Rules.APP_ALWAYS : null); }

        View extra(final Activity a, final Item it, final Runnable refresh) {
            String ch = U.appChoices(a).get(it.pkg);
            TextView t = U.text(a, AppChoice.label(ch), 13, U.INK, false);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(U.dp(a, 40));
            t.setPadding(U.dp(a, 12), 0, U.dp(a, 12), 0);
            Press.chip(t, U.BG, U.BORDER, 18);
            t.setContentDescription(it.label + ": " + AppChoice.label(ch) + ". Tocca per cambiare");
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { AppChoice.ask(a, it.pkg, it.label, refresh); }
            });
            return t;
        }
    }
}
