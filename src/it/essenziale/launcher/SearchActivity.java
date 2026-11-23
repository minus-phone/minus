package it.essenziale.launcher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.MetricAffectingSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * La ricerca (0.7): un solo accesso a persone, gruppi, app, impegni, azioni e impostazioni.
 * 0.12: tastiera chiusa all'apertura, ✕ in alto a destra. 0.13: Impostazioni come prima voce, sopra i recenti.
 * 0.24 (Evolutiva 020, contenitore B): schermo intero, campo in alto, risultati dall'alto in basso. Una sola riga per ogni
 * risultato (tipo a sinistra, titolo con la parte scritta in grassetto, suggerimento solo sulla preselezionata) e un
 * ordine fisso: Migliore, Contatti e gruppi, App, Impegni, Azioni, Impostazioni, Chiedi (regole in {@link Cerca}).
 * Scrivendo non si ricostruisce la lista: le righe si riusano per chiave e si muovono sul posto.
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
    private View clear;
    private List<U.AppInfo> apps;
    private Runnable best;                 // ciò che apre Invio; null senza risultati
    private boolean rendered;              // la prima volta niente movimento
    private boolean scheduled;             // un solo ridisegno per frame
    private String lastQ = "";
    private int expanded = -1;             // gruppo aperto da «Vedi tutti» mentre si scrive
    private boolean portataAll;            // «Vedi tutti» di A portata a campo vuoto
    private final android.os.Handler announce = new android.os.Handler();
    private final Map<String, Drawable.ConstantState> icons = new HashMap<String, Drawable.ConstantState>();
    // ricerche lente (rubrica, calendario) fuori dal thread principale
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private List<U.Event> events;          // impegni dei prossimi 30 giorni, letti una volta
    private List<Hit> rubricaHits = new ArrayList<Hit>();
    private String rubricaAsked = "";
    private List<Hit> numberHits = new ArrayList<Hit>();
    private String numberAsked = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = U.col(this);
        root.setBackgroundColor(U.BG);
        root.setPadding(0, U.dp(this, 12), 0, U.dp(this, 0));
        root.setClipChildren(true);
        // prima riga: ✕ a sinistra, «Cambia impostazioni» (icona) a destra
        LinearLayout top = U.row(this);
        top.setPadding(U.dp(this, 6), 0, U.dp(this, 6), 0);
        ImageView close = iconButton("ic_chiudi", "Chiudi la ricerca", U.INK, 24, 44);
        close.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        top.addView(close, new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 44)));
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        ImageView set = iconButton("ic_impostazioni", "Cambia impostazioni", U.INK, 24, 44);
        set.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(SearchActivity.this, SettingsActivity.class).putExtra("back", "Ricerca")); }
        });
        top.addView(set, new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 44)));
        root.addView(top, U.full());

        // seconda riga: pulsante telefono e campo (con il microfono), come in home
        LinearLayout field = U.row(this);
        field.setBackground(U.box(this, U.SURFACE, U.INK, 26));
        field.setPadding(U.dp(this, 16), U.dp(this, 2), U.dp(this, 6), U.dp(this, 2));
        query = new EditText(this);
        query.setHint("Cerca o chiedi");
        query.setHintTextColor(U.HINT);
        query.setTextColor(U.INK);
        query.setTextSize(16);
        query.setBackground(null);
        query.setSingleLine(true);
        query.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        query.setImeOptions(EditorInfo.IME_ACTION_GO);
        query.setId(View.generateViewId());
        field.addView(query, new LinearLayout.LayoutParams(0, U.dp(this, 48), 1f));
        ImageView cl = Ic.view(this, "ic_chiudi", U.MUTED, 20);
        if (cl == null) cl = new ImageView(this);
        cl.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        cl.setPadding(U.dp(this, 10), U.dp(this, 12), U.dp(this, 10), U.dp(this, 12));
        cl.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        cl.setContentDescription("Cancella");
        cl.setVisibility(View.GONE);
        Press.icon(cl, null);
        cl.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { query.setText(""); } });
        clear = cl;
        field.addView(clear, new LinearLayout.LayoutParams(U.dp(this, 40), U.dp(this, 44)));
        MicView mic = new MicView(this, true); // 0.19 (Evolutiva 014): microfono a contorno
        mic.setContentDescription("Comando vocale");
        mic.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { voice(); } });
        field.addView(mic, new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 44)));
        field.setMinimumHeight(U.dp(this, 62));
        LinearLayout bar = U.row(this);
        bar.addView(Telefono.button(this), Telefono.buttonLp(this));
        bar.addView(field, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams flp = U.full();
        flp.topMargin = U.dp(this, 2);
        flp.leftMargin = U.dp(this, 12);
        flp.rightMargin = U.dp(this, 12);
        root.addView(bar, flp);
        // TalkBack: il campo viene letto per primo
        close.setAccessibilityTraversalAfter(query.getId());

        // i risultati scendono dall'alto; il contenitore ritaglia le righe ai suoi bordi (mai sopra l'intestazione)
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(true);
        scroll.setClipToPadding(true);
        results = U.col(this);
        results.setPadding(U.dp(this, 12), U.dp(this, 8), U.dp(this, 12), U.dp(this, 24));
        results.setClipChildren(true);
        scroll.addView(results, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.setOnTouchListener(new View.OnTouchListener() {
            private float y0, x0;
            private boolean top0, hidden;
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: y0 = e.getY(); x0 = e.getX(); top0 = scroll.getScrollY() == 0; hidden = false; break;
                    case MotionEvent.ACTION_MOVE:
                        // la tastiera si chiude appena si scorre (oltre ~12 dp)
                        if (!hidden && Math.abs(e.getY() - y0) > U.dp(SearchActivity.this, 12)) { hidden = true; hideKeyboard(); }
                        break;
                    case MotionEvent.ACTION_UP:
                        // tirata verso il basso dalla cima della lista: la ricerca si chiude, come è salita (dal basso)
                        float dy = e.getY() - y0;
                        if (top0 && dy > U.dp(SearchActivity.this, 140) && Math.abs(e.getX() - x0) < dy / 2) finish();
                        break;
                    default: break;
                }
                return false;
            }
        });
        setContentView(root);

        query.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { render(); }
        });
        query.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int id, KeyEvent e) { if (best != null) best.run(); return true; }
        });
        // 0.12: la tastiera resta chiusa (si vedono aperte ora, recenti e a portata); si apre toccando il campo.
        // Impostazioni › App › «Apri subito la tastiera» la riporta come prima
        if (U.prefs(this).getBoolean(U.K_KEYBOARD, false)) { // 0.18 (Evolutiva 013)
            // 0.20 (Evolutiva 016): il campo prende il focus (e la tastiera si apre) a fine transizione, non a metà
            root.setFocusableInTouchMode(true);
            root.requestFocus();
            root.postDelayed(new Runnable() {
                public void run() {
                    if (isFinishing()) return;
                    query.requestFocus();
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(query, 0);
                }
            }, Motion.dur(this, Motion.BASE) + 20);
        } else {
            root.setFocusableInTouchMode(true);
            root.requestFocus();
        }
        if (getIntent().getBooleanExtra("voice", false)) voice();
    }

    /** Un'icona della direzione A che si tocca: riquadro di {@code boxDp}, icona di {@code sizeDp}, alone da premuta. */
    private ImageView iconButton(String name, String desc, int color, int sizeDp, int boxDp) {
        ImageView v = Ic.view(this, name, color, sizeDp);
        if (v == null) v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = U.dp(this, (boxDp - sizeDp) / 2f);
        v.setPadding(pad, pad, pad, pad);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        v.setContentDescription(desc);
        v.setClickable(true);
        v.setFocusable(true);
        Press.icon(v, null);
        return v;
    }

    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && query != null) imm.hideSoftInputFromWindow(query.getWindowToken(), 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        apps = null;
        loadEvents();
        renderNow();
    }

    /** 0.20 (Evolutiva 016): esce verso il basso (salita al contrario, BASE), prima si chiude la tastiera. */
    @Override
    public void finish() {
        hideKeyboard();
        super.finish();
        Motion.closeSearch(this);
    }

    @Override
    protected void onDestroy() {
        announce.removeCallbacksAndMessages(null);
        io.shutdownNow();
        super.onDestroy();
    }

    // ---------- Ricerche lente, fuori dal thread principale ----------

    /** Gli impegni dei prossimi 30 giorni si leggono una volta (e a ogni ritorno); poi si filtrano a ogni lettera senza interrogare il calendario. */
    private void loadEvents() {
        if (!U.hasCalendar(this)) { events = null; return; }
        final long from = System.currentTimeMillis() - 3600000L, to = U.startOfDay(30);
        io.execute(new Runnable() {
            public void run() {
                final List<U.Event> list = U.events(SearchActivity.this, from, to);
                runOnUiThread(new Runnable() { public void run() { if (isFinishing()) return; events = list; render(); } });
            }
        });
    }

    /** Rubrica per nome: una richiesta alla volta; arriva dopo le persone di −minus e le app, che si vedono subito. */
    private void askRubrica(final String q) {
        if (q.equals(rubricaAsked) || !Onboarding.hasContacts(this) || Cerca.norm(q).length() < 2) return;
        rubricaAsked = q;
        io.execute(new Runnable() {
            public void run() {
                final List<Hit> found = rubrica(q);
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (isFinishing()) return;
                        rubricaHits = found;
                        render();
                    }
                });
            }
        });
    }

    /** Rubrica per numero: i contatti il cui numero contiene le cifre scritte. */
    private void askNumbers(final String digits) {
        if (digits.equals(numberAsked) || !Onboarding.hasContacts(this)) return;
        numberAsked = digits;
        io.execute(new Runnable() {
            public void run() {
                final List<Hit> found = numberContacts(digits);
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (isFinishing()) return;
                        numberHits = found;
                        render();
                    }
                });
            }
        });
    }

    // ---------- Ridisegno: una volta per frame, righe riusate per chiave ----------

    private final Runnable renderRun = new Runnable() { public void run() { scheduled = false; renderNow(); } };

    private void render() {
        if (scheduled || results == null) return;
        scheduled = true;
        results.postOnAnimation(renderRun);
    }

    private void renderNow() {
        String q = query.getText().toString().trim();
        boolean clearWas = clear.getVisibility() == View.VISIBLE;
        // ✕ del campo: compare e sparisce in dissolvenza (RAPIDO), senza scala
        if (q.length() > 0 && !clearWas) { clear.setVisibility(View.VISIBLE); if (rendered) Motion.enter(clear, 0, Motion.RAPIDO, 0); }
        else if (q.length() == 0 && clearWas) clear.setVisibility(View.GONE);
        // lo scorrimento si azzera solo se il testo diventa vuoto o cambia la prima lettera; altrimenti resta
        if (Cerca.resetsScroll(lastQ, q)) { scroll.scrollTo(0, 0); expanded = -1; if (q.length() == 0) portataAll = false; }
        lastQ = q;
        List<Item> items = new ArrayList<Item>();
        best = null;
        hitsNow = 0;
        bestLabel = null;
        if (q.length() == 0) buildEmpty(items); else buildTyped(q, items);
        show(items);
        rendered = true;
        // un solo annuncio, 600 ms dopo l'ultimo cambiamento, mai uno a tasto
        announce.removeCallbacksAndMessages(null);
        if (q.length() > 0) {
            final String say = Cerca.announcement(bestLabel, hitsNow);
            announce.postDelayed(new Runnable() { public void run() { Motion.say(results, say); } }, 600);
        }
    }

    private int hitsNow;
    private String bestLabel;

    // ---------- Il modello: intestazioni, righe e una nota ----------

    private static final int T_HEAD = 0, T_ROW = 1, T_NOTE = 2;
    private static final int L_APP = 0, L_PERSON = 1, L_ICON = 2;

    private static final class Item {
        int type, lead, group;
        String key, title, sub, q, pkg, cls, icon, hint, desc, dupSub;
        boolean best, more, personRow;
        Runnable run;

        static Item row(String key, int lead, String title) { Item i = new Item(); i.type = T_ROW; i.key = key; i.lead = lead; i.title = title; return i; }
    }

    private Item iconRow(String key, String icon, String title, Runnable run) {
        Item i = Item.row(key, L_ICON, title);
        i.icon = icon; i.run = run;
        return i;
    }

    private Item personItem(String key, String name, String sub, Runnable run) {
        Item i = Item.row(key, L_PERSON, name);
        i.sub = sub; i.run = run; i.personRow = true;
        return i;
    }

    private Item appItem(final U.AppInfo a, Set<String> paused, final boolean tap) {
        Item i = Item.row("a:" + a.pkg, L_APP, a.label);
        i.pkg = a.pkg; i.cls = a.cls;
        if (paused.contains(a.pkg)) i.sub = "si apre dopo " + U.pauseSeconds(this) + " secondi";
        i.run = new Runnable() { public void run() { tapped = tap; openApp(a.pkg, a.label); } };
        return i;
    }

    // ---------- Campo vuoto: Aperte ora, Recenti, A portata ----------

    private void buildEmpty(List<Item> out) {
        Set<String> paused = U.pausedPkgs(this);
        long now = System.currentTimeMillis();
        if (apps == null) apps = U.sortedApps(this);
        Map<String, U.AppInfo> byPkg = new HashMap<String, U.AppInfo>();
        for (U.AppInfo a : apps) byPkg.put(a.pkg, a);
        Set<String> groups = groupNames();
        final List<String[]> fixed = byLastTouch(fixedPeople(this));
        Set<String> used = new HashSet<String>();
        Map<String, Item> ent = new HashMap<String, Item>();

        // Aperte ora: le conversazioni aperte (solo se ce ne sono), la più recente prima
        List<Cerca.Cand> open = new ArrayList<Cerca.Cand>();
        if (U.prefs(this).getBoolean(K_ACTIVE_ON, true)) {
            List<Rules.Conv> cs = new ArrayList<Rules.Conv>();
            for (Rules.Conv c : Convs.load(this)) if (c.isOpen(now) && c.listed(now) && !Rules.isCallTitle(c.name)) cs.add(c);
            Collections.sort(cs, new Comparator<Rules.Conv>() {
                public int compare(Rules.Conv a, Rules.Conv b) { return Long.compare(Rules.touched(b), Rules.touched(a)); }
            });
            Map<String, Set<String>> nums = new HashMap<String, Set<String>>();
            for (Rules.Conv c : cs) {
                String[] f = fixedMatch(c, fixed, nums);
                String name = f != null ? f[0] : Rules.groupName(c.name);
                String key = "p:" + Cerca.norm(name);
                if (ent.containsKey(key)) continue;
                final Hit h = new Hit();
                h.name = Rules.groupName(c.name); h.code = c.code; h.group = c.group;
                Item it = personItem(key, name, "aperta fino alle " + U.hhmm(c.until), new Runnable() { public void run() { openHit(h); } });
                ent.put(key, it);
                open.add(new Cerca.Cand(key, Cerca.PERSONA, name, 0));
            }
        }
        // Recenti: ciò che hai cercato nelle ultime 2 ore
        List<Cerca.Cand> rec = new ArrayList<Cerca.Cand>();
        if (U.prefs(this).getBoolean(K_RECENT_ON, true)) {
            for (final String[] r : recents(this)) {
                if (r.length < 3 || ("app".equals(r[0]) && paused.contains(r[1])) || !Cerca.recentVisible(r, now)) continue;
                Item it;
                String key;
                if ("app".equals(r[0])) {
                    U.AppInfo a = byPkg.get(r[1]);
                    if (a == null) continue;
                    key = "a:" + a.pkg;
                    it = appItem(a, paused, false);
                    it.run = new Runnable() { public void run() { openRecent(r); } };
                } else {
                    key = "p:" + Cerca.norm(r[2]);
                    it = personItem(key, r[2], groups.contains(Cerca.norm(r[2])) ? "Gruppo" : null, new Runnable() { public void run() { openRecent(r); } });
                }
                if (ent.containsKey(key)) continue;
                ent.put(key, it);
                rec.add(new Cerca.Cand(key, "app".equals(r[0]) ? Cerca.APP : Cerca.PERSONA, r[2], 0));
            }
        }
        // Passano sempre: persone E gruppi che passano sempre (nell'ordine scelto), con l'iniziale
        List<Cerca.Cand> always = new ArrayList<Cerca.Cand>();
        for (final String[] f : fixed) {
            String key = "p:" + Cerca.norm(f[0]);
            if (!ent.containsKey(key)) {
                boolean g = "g".equals(f[2]);
                ent.put(key, personItem(key, f[0], g ? "Gruppo" : null, new Runnable() { public void run() { openFixed(f); } }));
            }
            always.add(new Cerca.Cand(key, Cerca.PERSONA, f[0], 0));
        }
        // A portata: solo le app (l'ordine scelto in A portata)
        List<Cerca.Cand> port = new ArrayList<Cerca.Cand>();
        for (String[] a : portata(this)) {
            U.AppInfo ai = byPkg.get(a[0]);
            if (ai == null) continue;
            String key = "a:" + ai.pkg;
            if (!ent.containsKey(key)) ent.put(key, appItem(ai, paused, true));
            port.add(new Cerca.Cand(key, Cerca.APP, ai.label, 0));
        }
        Cerca.Piece pOpen = Cerca.take(open, used, Cerca.VUOTA_APERTE);
        Cerca.Piece pRec = Cerca.take(rec, used, Cerca.VUOTA_RECENTI);
        Cerca.Piece pAlw = Cerca.take(always, used, Cerca.VUOTA_SEMPRE);
        Cerca.Piece pPort = Cerca.take(port, used, Cerca.portataMax(portataAll));
        Runnable allPeople = new Runnable() { public void run() {
            startActivity(new Intent(SearchActivity.this, ListActivity.class).putExtra(ListActivity.EXTRA_MODE, ListActivity.MODE_CONTACTS));
        } };
        Runnable allApps = new Runnable() { public void run() { portataAll = true; render(); } };
        addEmpty(out, "h:aperte", "Aperte ora", pOpen, ent, null);
        addEmpty(out, "h:recenti", "Recenti", pRec, ent, null);
        addEmpty(out, "h:sempre", "Passano sempre", pAlw, ent, allPeople);
        addEmpty(out, "h:portata", "A portata", pPort, ent, allApps);
        if (out.isEmpty()) {
            Item n = new Item();
            n.type = T_NOTE; n.key = "note"; n.title = "Scrivi il nome di una persona, di un'app o di un impegno. Puoi anche chiedere o dire un comando.";
            out.add(n);
        }
    }

    private void addEmpty(List<Item> out, String key, String title, Cerca.Piece p, Map<String, Item> ent, Runnable more) {
        if (p.shown.isEmpty()) return;
        Item h = new Item();
        h.type = T_HEAD; h.key = key; h.title = title;
        if (more != null && p.more) { h.more = true; h.run = more; }
        out.add(h);
        for (Cerca.Cand c : p.shown) out.add(ent.get(c.key));
    }

    /** I nomi (normalizzati) dei gruppi che −minus conosce. */
    private Set<String> groupNames() {
        Set<String> out = new HashSet<String>();
        for (Rules.Conv c : Convs.load(this)) if (c.group) out.add(Cerca.norm(Rules.groupName(c.name)));
        for (String[] r : U.rows(U.prefs(this).getString(U.K_GROUPS, ""))) if (r.length > 0) out.add(Cerca.norm(Rules.groupName(r[0])));
        return out;
    }

    /** La persona o il gruppo di «chi passa sempre» che è la stessa di questa conversazione (stesso nome o stesso numero). */
    private String[] fixedMatch(Rules.Conv c, List<String[]> fixed, Map<String, Set<String>> nums) {
        String n = Cerca.norm(Rules.groupName(c.name));
        for (String[] f : fixed) if (Cerca.norm(f[0]).equals(n)) return f;
        if (c.group) return null;
        Set<String> cn = Nums.forConv(this, c.name);
        if (cn.isEmpty()) return null;
        for (String[] f : fixed) {
            if ("g".equals(f[2])) continue;
            Set<String> fn = nums.get(f[0]);
            if (fn == null) {
                fn = new HashSet<String>();
                for (Rules.Person p : Rules.people(U.cfg(this, U.K_CONTACTS, U.DEF_CONTACTS))) if (p.name.equals(f[0])) fn.addAll(Nums.forPerson(this, p));
                nums.put(f[0], fn);
            }
            if (!Collections.disjoint(fn, cn)) return f;
        }
        return null;
    }

    /** Una persona o un gruppo di «chi passa sempre»: si apre come dalla tessera di prima. */
    private void openFixed(String[] f) {
        boolean group = "g".equals(f[2]);
        if (!group) for (Rules.Person p : Rules.people(U.cfg(this, U.K_CONTACTS, U.DEF_CONTACTS)))
            if (Cerca.norm(p.name).equals(Cerca.norm(f[0]))) { openPerson(p); return; }
        Hit h = new Hit();
        h.name = f[0]; h.code = f[1]; h.group = group;
        openHit(h);
    }

    // ---------- Il ridisegno sul posto ----------

    /**
     * Le righe che restano si riusano (stessa chiave = stessa vista, cambia solo il testo e il grassetto) e si spostano al
     * loro posto in RAPIDO; quelle nuove entrano in dissolvenza dove stanno; quelle che spariscono svaniscono dov'erano.
     * Righe di altezza minima fissa: una riga non cambia altezza se compare la riga muta. Con «Rimuovi animazioni»:
     * solo dissolvenze da 80 ms, nessun movimento.
     */
    private void show(List<Item> items) {
        final boolean animate = rendered;
        final Map<String, View> oldView = new HashMap<String, View>();
        final Map<String, Integer> oldTop = new HashMap<String, Integer>();
        for (int i = 0; i < results.getChildCount(); i++) {
            View c = results.getChildAt(i);
            c.animate().cancel(); // se arriva un'altra lettera mentre qualcosa si muove, quel movimento finisce subito
            c.setTranslationY(0f);
            c.setAlpha(1f);
            String k = (String) c.getTag();
            oldView.put(k, c);
            oldTop.put(k, c.getTop());
        }
        results.getOverlay().clear();
        final Set<String> fresh = new HashSet<String>();
        Set<String> keep = new HashSet<String>();
        List<View> order = new ArrayList<View>();
        for (Item it : items) {
            View v = oldView.get(it.key);
            boolean isNew = v == null;
            if (isNew) {
                v = create(it);
                v.setTag(it.key);
                fresh.add(it.key);
            }
            bind(v, it, animate && !isNew);
            keep.add(it.key);
            order.add(v);
        }
        boolean same = results.getChildCount() == order.size();
        for (int i = 0; same && i < order.size(); i++) if (results.getChildAt(i) != order.get(i)) same = false;
        if (!same) {
            results.removeAllViews();
            for (View v : order) results.addView(v);
        }
        final List<View> ghosts = new ArrayList<View>();
        for (Map.Entry<String, View> e : oldView.entrySet()) if (!keep.contains(e.getKey())) ghosts.add(e.getValue());
        if (!animate || (same && ghosts.isEmpty() && fresh.isEmpty())) return;
        final java.util.Map<String, Integer> was = oldTop;
        results.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            public boolean onPreDraw() {
                results.getViewTreeObserver().removeOnPreDrawListener(this);
                transitions(was, fresh, ghosts);
                return true;
            }
        });
    }

    private void transitions(Map<String, Integer> oldTop, Set<String> fresh, List<View> ghosts) {
        boolean red = Motion.reduced(this);
        int ms = Motion.dur(this, Motion.RAPIDO);
        for (int i = 0; i < results.getChildCount(); i++) {
            View c = results.getChildAt(i);
            String k = (String) c.getTag();
            if (fresh.contains(k)) {
                c.setAlpha(0f);
                c.animate().alpha(1f).setDuration(ms).setInterpolator(Motion.DECELERA).start();
                continue;
            }
            Integer was = oldTop.get(k);
            if (was == null || red) continue;
            int dy = was - c.getTop();
            if (Math.abs(dy) > 1) {
                c.setTranslationY(dy);
                c.animate().translationY(0f).setDuration(ms).setInterpolator(Motion.DECELERA).start();
            }
        }
        for (final View g : ghosts) {
            g.setAlpha(1f);
            g.setTranslationY(0f);
            results.getOverlay().add(g);
            g.animate().alpha(0f).setDuration(ms).setInterpolator(Motion.DECELERA).withEndAction(new Runnable() {
                public void run() { results.getOverlay().remove(g); }
            }).start();
        }
    }

    private View create(Item it) {
        switch (it.type) {
            case T_HEAD: return new HeadView();
            case T_NOTE: return noteView();
            default: return new RowView(it);
        }
    }

    private void bind(View v, Item it, boolean animateBest) {
        if (v instanceof RowView) ((RowView) v).bind(it, animateBest);
        else if (v instanceof HeadView) ((HeadView) v).bind(it);
        else if (v instanceof TextView) ((TextView) v).setText(it.title);
    }

    private View noteView() {
        TextView t = U.text(this, "", 15, U.INK, false);
        t.setPadding(U.dp(this, 10), U.dp(this, 10), U.dp(this, 10), U.dp(this, 2));
        t.setLayoutParams(U.full());
        return t;
    }

    /** Il pezzo scritto, in grassetto (Atkinson bold, il corsivo e il resto restano com'è). */
    private static final class Bold extends MetricAffectingSpan {
        @Override public void updateDrawState(TextPaint p) { p.setTypeface(U.SANS_B); }
        @Override public void updateMeasureState(TextPaint p) { p.setTypeface(U.SANS_B); }
    }

    /** Il titolo di un gruppo: scritta in maiuscole spaziate (12), a destra «Vedi tutti ›» se ce n'è altro. Letto da TalkBack come intestazione. */
    private final class HeadView extends LinearLayout {
        final TextView label, link;
        Runnable onMore;

        HeadView() {
            super(SearchActivity.this);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.BOTTOM);
            setMinimumHeight(U.dp(SearchActivity.this, 36));
            setPadding(U.dp(SearchActivity.this, 10), U.dp(SearchActivity.this, 10), U.dp(SearchActivity.this, 10), U.dp(SearchActivity.this, 2));
            label = U.text(SearchActivity.this, "", 12, U.MUTED, false);
            label.setLetterSpacing(0.12f);
            label.setPadding(0, 0, 0, U.dp(SearchActivity.this, 5));
            U.heading(label);
            addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            link = U.text(SearchActivity.this, "Vedi tutti  ›", 13, U.INK, false);
            link.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
            link.setPadding(U.dp(SearchActivity.this, 10), 0, U.dp(SearchActivity.this, 2), 0);
            link.setMinHeight(U.dp(SearchActivity.this, 36));
            link.setMinWidth(U.dp(SearchActivity.this, 72));
            Press.link(link, U.INK);
            link.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (onMore != null) onMore.run(); } });
            addView(link, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            setLayoutParams(U.full());
        }

        void bind(Item it) {
            label.setText(it.title.toUpperCase(Locale.ITALY));
            label.setContentDescription(it.title);
            link.setVisibility(it.more ? View.VISIBLE : View.GONE);
            link.setContentDescription("Vedi tutti: " + it.title);
            onMore = it.run;
        }
    }

    /**
     * Una riga, sempre la stessa: elemento di tipo da 32 dp (icona dell'app, cerchio con l'iniziale, icona a linea), titolo
     * da 16 con la parte scritta in grassetto, riga muta da 13 solo se distingue, suggerimento a destra solo sulla preselezionata.
     * Alta almeno 52 dp; margini 10, intervallo 12. Niente frecce, linee, link.
     */
    private final class RowView extends LinearLayout {
        final TextView title, sub, hint;
        final TextView badge;      // l'iniziale (persone), per lo stato premuto
        final ImageView appIcon;   // l'icona dell'app, quando c'è
        final Press.Fade fx;
        Runnable action;

        RowView(Item it) {
            super(SearchActivity.this);
            final Context c = SearchActivity.this;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(U.dp(c, 52));
            setPadding(U.dp(c, 10), 0, U.dp(c, 10), 0);
            FrameLayout lead = new FrameLayout(c);
            TextView ini = null;
            ImageView ai = null;
            if (it.lead == L_PERSON) {
                ini = U.initial(c, it.title, false, 32); // sempre un cerchio, anche per i gruppi
                lead.addView(ini, new FrameLayout.LayoutParams(U.dp(c, 32), U.dp(c, 32)));
            } else if (it.lead == L_APP) {
                ai = new ImageView(c);
                ai.setScaleType(ImageView.ScaleType.FIT_CENTER);
                ai.setOutlineProvider(new ViewOutlineProvider() {
                    @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), U.dp(c, 9)); }
                });
                ai.setClipToOutline(true);
                ai.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                lead.addView(ai, new FrameLayout.LayoutParams(U.dp(c, 32), U.dp(c, 32)));
            } else {
                ImageView ic = Ic.view(c, it.icon, U.INK, 24);
                if (ic != null) {
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(U.dp(c, 24), U.dp(c, 24));
                    lp.gravity = Gravity.CENTER;
                    lead.addView(ic, lp);
                }
            }
            lead.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(lead, new LinearLayout.LayoutParams(U.dp(c, 32), U.dp(c, 32)));
            badge = ini;
            appIcon = ai;
            LinearLayout tx = U.col(c);
            tx.setGravity(Gravity.CENTER_VERTICAL);
            tx.setPadding(U.dp(c, 12), 0, U.dp(c, 6), 0);
            title = U.text(c, "", 16, U.INK, false);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            tx.addView(title);
            sub = U.text(c, "", 13, U.MUTED, false);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            sub.setVisibility(View.GONE);
            tx.addView(sub);
            addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            hint = U.text(c, "", 13, U.INK, true);
            hint.setVisibility(View.GONE);
            addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            fx = Press.rowSwitch(this, ini);
            setClickable(true);
            setFocusable(true);
            setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (action != null) action.run(); } });
            setLayoutParams(U.full());
        }

        void bind(Item it, boolean animateBest) {
            if (badge != null) badge.setText(it.title.replace("[", "").trim().length() > 0 ? it.title.replace("[", "").trim().substring(0, 1).toUpperCase(Locale.ITALY) : "?");
            if (appIcon != null && appIcon.getDrawable() == null) appIcon.setImageDrawable(appIcon(it.pkg, it.cls));
            SpannableString s = new SpannableString(it.title);
            int[] m = it.q == null ? null : Cerca.match(it.title, it.q);
            if (m != null) s.setSpan(new Bold(), m[0], m[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            title.setText(s);
            if (it.sub != null && it.sub.length() > 0) { sub.setText(it.sub); sub.setVisibility(View.VISIBLE); } else sub.setVisibility(View.GONE);
            hint.setText(it.hint == null ? "" : it.hint);
            hint.setVisibility(it.best && it.hint != null ? View.VISIBLE : View.GONE);
            action = it.run;
            String d = it.desc != null ? it.desc : it.title + (it.sub != null && it.sub.length() > 0 ? ", " + it.sub : "");
            setContentDescription(it.best ? d + ", primo risultato" : d);
            fx.setBest(it.best, animateBest);
        }
    }

    /** L'icona dell'app così com'è (colori veri), dallo stesso pacchetto; null se non si trova. */
    private Drawable appIcon(String pkg, String cls) {
        if (pkg == null) return null;
        Drawable.ConstantState cs = icons.get(pkg);
        if (cs == null) {
            try {
                android.content.pm.PackageManager pm = getPackageManager();
                Drawable d = cls != null ? pm.getActivityIcon(new android.content.ComponentName(pkg, cls)) : pm.getApplicationIcon(pkg);
                cs = d.getConstantState();
                if (cs == null) return d;
                icons.put(pkg, cs);
            } catch (Exception e) {
                return null;
            }
        }
        return cs.newDrawable(getResources());
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
        String name, code, number, apps;
        boolean group, strong;
        Rules.Person person;
        long contactId = -1;
        long until, last;
    }

    private void buildTyped(final String q, List<Item> out) {
        if (apps == null) apps = U.sortedApps(this);
        Map<String, Item> ent = new HashMap<String, Item>();
        List<Cerca.Cand> cands = new ArrayList<Cerca.Cand>();
        long now = System.currentTimeMillis();
        Map<String, Long> recentAt = recentTimes();
        final String phone = Cerca.phoneSearch(q);
        final String typed = q.trim();

        if (phone != null) {
            numberCandidates(typed, phone, ent, cands);
        } else {
            askRubrica(q);
            // persone e gruppi: quelli di −minus subito, la rubrica quando arriva
            List<Hit> people = peopleLocal(q);
            Set<String> seen = new HashSet<String>();
            for (Hit h : people) seen.add(Cerca.norm(h.name));
            for (Hit r : rubricaHits) {
                String k = Cerca.norm(r.name);
                if (seen.contains(k) || !(Cerca.score(r.name, q) > 0 || r.name.toLowerCase(Locale.ITALY).contains(q.toLowerCase(Locale.ITALY)))) continue;
                r.rank = Math.max(1, Cerca.rank(r.name, q, false));
                people.add(r);
                seen.add(k);
            }
            for (final Hit h : people) {
                String key = "p:" + Cerca.norm(h.name);
                Item it = personItem(key, h.name, null, new Runnable() { public void run() { openHit(h); } });
                if (h.until > now) it.sub = "aperta fino alle " + U.hhmm(h.until);
                else if (h.group) it.sub = "Gruppo";
                it.q = q;
                it.dupSub = h.contactId >= 0 ? "in rubrica" : h.apps;
                ent.put(key, it);
                Long rt = recentAt.get(key);
                cands.add(new Cerca.Cand(key, Cerca.PERSONA, h.name, h.rank).recent(Math.max(h.last, rt == null ? 0 : rt)));
            }
        }
        // app
        Set<String> paused = U.pausedPkgs(this);
        Set<String> pref = new HashSet<String>();
        for (String[] a : portata(this)) pref.add(a[0]);
        for (U.AppInfo a : apps) {
            int s = Cerca.rank(a.label, q, pref.contains(a.pkg));
            if (s == 0) continue;
            Item it = appItem(a, paused, false);
            it.q = q;
            ent.put(it.key, it);
            Long rt = recentAt.get(it.key);
            cands.add(new Cerca.Cand(it.key, Cerca.APP, a.label, s).recent(rt == null ? 0 : rt));
        }
        // impegni dei prossimi 30 giorni
        if (events != null && q.length() >= 2) {
            for (final U.Event e : events) {
                int s = Cerca.score(e.title, q);
                if (s == 0) continue;
                String when = (e.begin < U.startOfDay(1) ? "oggi" : e.begin < U.startOfDay(2) ? "domani" : U.dayLabel(e.begin).toLowerCase(Locale.ITALY))
                        + (e.allDay ? "" : " · " + U.hhmm(e.begin));
                String key = "ev:" + e.eventId + ":" + e.instBegin;
                if (ent.containsKey(key)) continue;
                Item it = iconRow(key, "ic_calendario", e.title, new Runnable() { public void run() { U.openEvent(SearchActivity.this, e); } });
                it.sub = when; it.q = q;
                ent.put(key, it);
                cands.add(new Cerca.Cand(key, Cerca.IMPEGNO, e.title, s * 10).recent(-(e.begin / 1000L)));
            }
        }
        // azioni
        VoiceParser.Cmd c = VoiceParser.parse(q, VoiceActivity.names(this), java.util.Calendar.getInstance());
        if (!VoiceParser.ASK.equals(c.type)) {
            Item it = iconRow("x:esegui", "ic_microfono", "Esegui «" + q + "»", new Runnable() { public void run() { command(q); } });
            ent.put(it.key, it);
            cands.add(new Cerca.Cand(it.key, Cerca.AZIONE, it.title, 0).order(0));
        }
        if (phone == null) {
            final String cap = q.substring(0, 1).toUpperCase(Locale.ITALY) + q.substring(1);
            Item it = iconRow("x:aggiungi", "ic_aggiungi", "Aggiungi «" + cap + "»", new Runnable() {
                public void run() {
                    Intent i = new Intent(SearchActivity.this, AddActivity.class);
                    i.putExtra("title", cap);
                    i.putExtra("day", U.startOfDay(Quando.addDayOffset(U.hourNow())));
                    startActivity(i);
                }
            });
            it.sub = "Impegno · " + (Quando.addDayOffset(U.hourNow()) == 1 ? "domani" : "oggi");
            ent.put(it.key, it);
            cands.add(new Cerca.Cand(it.key, Cerca.AZIONE, it.title, 0).order(1).needsHit());
        }
        // impostazioni di −minus
        for (Cerca.Cand s : Cerca.settingCands(q)) {
            final String dest = Cerca.SETTINGS[s.order][2];
            Item it = iconRow(s.key, "ic_impostazioni", s.label, new Runnable() { public void run() { openSetting(dest); } });
            it.q = q;
            ent.put(it.key, it);
            cands.add(s);
        }
        // Chiedi, sempre per ultimo (tre righe solo se non c'è altro)
        if (phone == null) {
            Item it = iconRow("q:chiedi", "ic_info", "Chiedi «" + q + "» all'assistente", new Runnable() { public void run() { ask(q); } });
            ent.put(it.key, it);
            cands.add(new Cerca.Cand(it.key, Cerca.CHIEDI, it.title, 0).order(0));
            Item w = iconRow("q:web", "ic_cerca", "Cerca sul web", new Runnable() { public void run() { web(q); } });
            ent.put(w.key, w);
            cands.add(new Cerca.Cand(w.key, Cerca.CHIEDI, w.title, 0).order(1));
            Item st = iconRow("q:store", "ic_cerca", "Cerca nel Play Store", new Runnable() { public void run() { store(q); } });
            ent.put(st.key, st);
            cands.add(new Cerca.Cand(st.key, Cerca.CHIEDI, st.title, 0).order(2));
        }

        Cerca.Plan plan = Cerca.plan(cands, expanded);
        hitsNow = plan.hits();
        // nessun risultato: nessuna preselezione (Invio non fa niente) e una frase che lo dice
        if (!plan.anything && phone == null) {
            Item n = new Item();
            n.type = T_NOTE; n.key = "note"; n.title = "Nessun contatto, app o impegno con «" + q + "».";
            out.add(n);
        }
        if (plan.best != null) {
            Item b = ent.get(plan.best.key);
            b.best = true;
            b.hint = Cerca.hint(plan.best);
            best = b.run;
            bestLabel = b.title;
            out.add(b);
        }
        // due «Marco» in elenco: la riga muta dice quale è quale
        Map<String, Integer> names = new HashMap<String, Integer>();
        List<Item> people = new ArrayList<Item>();
        if (plan.best != null && ent.get(plan.best.key).personRow) people.add(ent.get(plan.best.key));
        for (Cerca.Cand cd : plan.groups.get(Cerca.PERSONA)) people.add(ent.get(cd.key));
        for (Item p : people) { String k = Cerca.norm(p.title); Integer n = names.get(k); names.put(k, n == null ? 1 : n + 1); }
        for (Item p : people) if (p.sub == null && names.get(Cerca.norm(p.title)) > 1 && p.dupSub != null && p.dupSub.length() > 0) p.sub = p.dupSub;
        for (int k = 0; k < 6; k++) {
            List<Cerca.Cand> g = plan.groups.get(k);
            if (g.isEmpty()) continue;
            Item h = new Item();
            h.type = T_HEAD; h.key = "h:" + k; h.title = Cerca.TITOLI[k]; h.group = k;
            if (plan.more[k] > 0) {
                h.more = true;
                final int kk = k;
                h.run = new Runnable() { public void run() { expanded = kk; render(); } };
            }
            out.add(h);
            for (Cerca.Cand cd : g) out.add(ent.get(cd.key));
        }
    }

    /** Con un numero scritto (da 3 cifre): Migliore è il contatto unico o «Chiama»; poi i contatti con quel numero; Azioni: WhatsApp, Aggiungi ai contatti. */
    private void numberCandidates(final String typed, String digits, Map<String, Item> ent, List<Cerca.Cand> cands) {
        askNumbers(digits);
        List<Hit> found = new ArrayList<Hit>();
        for (Hit h : numberHits) if (Cerca.numberMatches(h.number, digits)) found.add(h);
        boolean unique = found.size() == 1;
        for (final Hit h : found) {
            String key = "c:" + h.contactId;
            Item it = personItem(key, h.name, h.number, new Runnable() { public void run() { openHit(h); } });
            ent.put(key, it);
            cands.add(new Cerca.Cand(key, Cerca.PERSONA, h.name, unique ? 100 : 50));
        }
        if (!unique) {
            Item call = iconRow("x:chiama", "ic_telefono", "Chiama " + typed, new Runnable() { public void run() { Telefono.dial(SearchActivity.this, typed); } });
            call.desc = "Chiama " + typed + ". Si apre il tastierino di Android con il numero già scritto";
            ent.put(call.key, call);
            cands.add(new Cerca.Cand(call.key, Cerca.AZIONE, call.title, 100).canBest());
        }
        if (Telefono.hasWhatsApp(this)) {
            Item wa = iconRow("x:wa", "ic_chat", "Scrivi su WhatsApp", new Runnable() { public void run() { Telefono.whatsapp(SearchActivity.this, typed); } });
            wa.desc = "Scrivi su WhatsApp. Apre la chat con questo numero";
            ent.put(wa.key, wa);
            cands.add(new Cerca.Cand(wa.key, Cerca.AZIONE, wa.title, 0).order(0));
        }
        Item add = iconRow("x:addc", "ic_contatti", "Aggiungi ai contatti", new Runnable() { public void run() { Telefono.newContact(SearchActivity.this, typed); } });
        add.desc = "Aggiungi ai contatti. Si apre la rubrica di Android con il numero già scritto";
        ent.put(add.key, add);
        cands.add(new Cerca.Cand(add.key, Cerca.AZIONE, add.title, 0).order(1));
    }

    /** Quando hai aperto per l'ultima volta app e persone cercate (dai «cercati di recente»), per lo spareggio. */
    private Map<String, Long> recentTimes() {
        Map<String, Long> m = new HashMap<String, Long>();
        for (String[] r : recents(this)) {
            if (r.length < 4) continue;
            try {
                long t = Long.parseLong(r[3].trim());
                m.put("app".equals(r[0]) ? "a:" + r[1] : "p:" + Cerca.norm(r[2]), t);
            } catch (NumberFormatException ignored) {}
        }
        return m;
    }

    /** Persone e gruppi che −minus conosce (chi passa sempre, i frequenti, le conversazioni viste), senza la rubrica. */
    private List<Hit> peopleLocal(String q) {
        List<Hit> out = new ArrayList<Hit>();
        Set<String> seen = new HashSet<String>();
        Map<String, String> rules = U.groupRules(this);
        Map<String, Rules.Conv> conv = new HashMap<String, Rules.Conv>();
        long now = System.currentTimeMillis();
        for (Rules.Conv c : Convs.load(this)) {
            String k = Cerca.norm(Rules.groupName(c.name));
            Rules.Conv o = conv.get(k);
            if (o == null || c.until > o.until || c.last > o.last) conv.put(k, c);
        }
        for (Rules.Person p : Rules.people(U.cfg(this, U.K_CONTACTS, U.DEF_CONTACTS))) {
            Rules.Conv c = conv.get(Cerca.norm(p.name));
            boolean always = p.section.equalsIgnoreCase("Sempre");
            int s = Cerca.rank(p.name, q, always || (c != null && c.isOpen(now)));
            if (s == 0) continue;
            Hit h = new Hit();
            h.rank = s; h.name = p.name; h.person = p; h.strong = always;
            h.code = p.apps.isEmpty() ? "WA" : p.apps.get(0);
            h.apps = names(p.apps);
            if (c != null) { h.until = c.isOpen(now) ? c.until : 0; h.last = Rules.touched(c); }
            out.add(h); seen.add(Cerca.norm(p.name));
        }
        for (Rules.Conv c : conv.values()) {
            String k = Cerca.norm(Rules.groupName(c.name));
            if (seen.contains(k)) continue;
            String rule = c.group ? rules.get(Rules.groupName(c.name).toLowerCase(Locale.ITALY)) : null;
            int s = Cerca.rank(c.name, q, Rules.groupAlways(rule) || c.isOpen(now));
            if (s == 0) continue;
            Hit h = new Hit();
            h.rank = s; h.name = Rules.groupName(c.name); h.code = c.code; h.group = c.group;
            h.apps = Rules.appName(c.code);
            h.until = c.isOpen(now) ? c.until : 0; h.last = Rules.touched(c);
            out.add(h); seen.add(k);
        }
        return out;
    }

    private static String names(List<String> codes) {
        StringBuilder b = new StringBuilder();
        for (String c : codes) { if (b.length() > 0) b.append(" e "); b.append(Rules.appName(c)); }
        return b.toString();
    }

    /** La rubrica per nome (fino a 12). Si chiama fuori dal thread principale, tranne che da un recente. */
    private List<Hit> rubrica(String q) {
        List<Hit> out = new ArrayList<Hit>();
        if (!Onboarding.hasContacts(this) || Cerca.norm(q).length() < 2) return out;
        Cursor cur = null;
        try {
            cur = getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
                    new String[]{ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME},
                    ContactsContract.Contacts.DISPLAY_NAME + " LIKE ?", new String[]{"%" + q + "%"}, ContactsContract.Contacts.DISPLAY_NAME + " ASC");
            Set<String> seen = new HashSet<String>();
            while (cur != null && cur.moveToNext() && out.size() < 12) {
                String name = cur.getString(1);
                if (name == null || !seen.add(Cerca.norm(name))) continue;
                Hit h = new Hit();
                h.rank = Math.max(1, Cerca.rank(name, q, false));
                h.name = name; h.contactId = cur.getLong(0);
                out.add(h);
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return out;
    }

    /** I contatti il cui numero contiene le cifre scritte (fino a 12). */
    private List<Hit> numberContacts(String digits) {
        List<Hit> out = new ArrayList<Hit>();
        Cursor cur = null;
        try {
            cur = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
                    null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");
            Set<Long> seen = new HashSet<Long>();
            while (cur != null && cur.moveToNext() && out.size() < 12) {
                long id = cur.getLong(0);
                String name = cur.getString(1), num = cur.getString(2);
                if (name == null || seen.contains(id) || !Cerca.numberMatches(num, digits)) continue;
                seen.add(id);
                Hit h = new Hit();
                h.name = name; h.contactId = id; h.number = num;
                out.add(h);
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return out;
    }

    /** Per un recente: persone di −minus e rubrica, nello stesso ordine di prima (qui si può aspettare: è un tocco). */
    private List<Hit> peopleHits(String q) {
        List<Hit> out = peopleLocal(q);
        Set<String> seen = new HashSet<String>();
        for (Hit h : out) seen.add(Cerca.norm(h.name));
        for (Hit r : rubrica(q)) if (seen.add(Cerca.norm(r.name))) out.add(r);
        return out;
    }

    /** 0.15: chi passa sempre, con prima le conversazioni in corso e poi chi hai sentito più di recente. */
    private List<String[]> byLastTouch(List<String[]> fixed) {
        long now = System.currentTimeMillis();
        List<Rules.Conv> convs = Convs.load(this);
        java.util.Map<String, Rules.Person> persons = new java.util.HashMap<String, Rules.Person>();
        for (Rules.Person p : Rules.people(U.cfg(this, U.K_CONTACTS, U.DEF_CONTACTS))) persons.put(Cerca.norm(p.name), p);
        java.util.Map<String, Long> last = new java.util.HashMap<String, Long>();
        Set<String> live = new java.util.HashSet<String>();
        java.util.Map<String, String[]> byName = new java.util.LinkedHashMap<String, String[]>();
        for (String[] f : fixed) {
            byName.put(f[0], f);
            String key = Cerca.norm(f[0]);
            Rules.Person p = "g".equals(f[2]) ? null : persons.get(key);
            Set<String> nums = p == null ? java.util.Collections.<String>emptySet() : Nums.forPerson(this, p);
            long t = 0;
            for (Rules.Conv c : convs) {
                boolean same = Cerca.norm(Rules.groupName(c.name)).equals(key)
                        || (!c.group && !nums.isEmpty() && !java.util.Collections.disjoint(nums, Nums.forConv(this, c.name)));
                if (!same) continue;
                t = Math.max(t, Rules.touched(c));
                if (c.listed(now)) live.add(f[0]);
            }
            last.put(f[0], t);
        }
        List<String[]> out = new ArrayList<String[]>();
        for (String n : Cerca.byLast(new ArrayList<String>(byName.keySet()), last, live)) out.add(byName.get(n));
        return out;
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
        if (ok) { Motion.scatto(query); Convs.started(this, h.code, h.name, h.group); remember("person", h.name, h.name); }
        else U.toast(this, "Non riesco ad aprire questa conversazione");
    }

    private void openPerson(final Rules.Person p) {
        if (p.apps.size() <= 1) { openWith(p.name, p.apps.isEmpty() ? "WA" : p.apps.get(0), p.target); return; }
        final String[] names = new String[p.apps.size()];
        for (int i = 0; i < names.length; i++) names[i] = Rules.appName(p.apps.get(i));
        U.dlg(this).setTitle(p.name).setItems(names, new DialogInterface.OnClickListener() {
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
        U.dlg(this).setTitle(name).setItems(labels, new DialogInterface.OnClickListener() {
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
            Motion.scatto(query);
            if (!"TEL".equals(app) && !"MAIL".equals(app)) Convs.started(this, app, name, false);
            remember("person", name, name);
        }
    }

    private void openApp(String pkg, String label) {
        U.AppInfo a = null;
        if (apps == null) apps = U.sortedApps(this);
        for (U.AppInfo x : apps) if (x.pkg.equals(pkg)) { a = x; break; }
        if (a == null) { U.toast(this, label + " non è installata"); return; }
        if (U.open(this, a)) { Motion.scatto(query); remember("app", a.pkg, a.label); logOpen(tapped); } // 0.20: aprire un risultato dà lo scatto
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
        fromRecent = true;
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
        else if ("groups".equals(dest)) i = SettingsActivity.peopleIntent(this, "Ricerca").putExtra("tab", 1);
        else if ("servizi".equals(dest)) i = SelectorActivity.intent(this, Scegli.SERVICES, null);
        else if ("waiting".equals(dest)) { i = new Intent(this, ContainerActivity.class); i.putExtra("tab", ContainerActivity.TAB_WAITING); }
        else if ("portata".equals(dest)) i = new Intent(this, PortataActivity.class);
        else if ("pause".equals(dest)) i = SelectorActivity.intent(this, Scegli.DOOM, null);
        else if ("session".equals(dest)) i = new Intent(this, SessionActivity.class);
        else if ("calendars".equals(dest)) i = new Intent(this, CalendarsActivity.class);
        else if ("voice".equals(dest)) i = new Intent(this, VoiceHelpActivity.class);
        else if ("changes".equals(dest)) i = new Intent(this, ChangesActivity.class);
        else if ("sticky".equals(dest)) i = new Intent(this, StickyActivity.class);
        else if ("bug".equals(dest)) i = new Intent(this, BugActivity.class);
        else if ("night".equals(dest)) i = SelectorActivity.intent(this, Scegli.NIGHT, null);
        else if ("aspetto".equals(dest)) i = new Intent(this, AspettoActivity.class);
        else if ("wellbeing".equals(dest)) { Session.openWellbeing(this); return; }
        else if ("alarms".equals(dest)) { U.openAlarms(this); return; }
        else return;
        if (!i.hasExtra("back")) i.putExtra("back", "Ricerca");
        startActivity(i);
    }

    /** 0.15: tra i cercati di recente va solo ciò che hai scritto nel campo (o riaperto da lì); quello che tocchi negli elenchi è già a portata. */
    private boolean fromRecent;

    private void remember(String kind, String value, String label) {
        boolean typed = fromRecent || (query != null && query.getText().toString().trim().length() > 0);
        fromRecent = false;
        if (typed) remember(this, kind, value, label);
    }

    /** Anche i comandi vocali lo usano: ciò che chiedi a voce conta come cercato. */
    static void remember(Context c, String kind, String value, String label) {
        List<String> old = new ArrayList<String>();
        for (String[] r : recents(c)) old.add(r[0] + " | " + r[1] + " | " + r[2] + (r.length > 3 ? " | " + r[3] : ""));
        List<String> now = Cerca.pushRecent(old, kind + " | " + value.replace("|", "/") + " | " + label.replace("|", "/") + " | " + System.currentTimeMillis());
        U.prefs(c).edit().putString(K_RECENT, U.lines(now)).apply();
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
        Motion.scatto(query);
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
        Motion.scatto(query);
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
