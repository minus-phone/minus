package it.essenziale.launcher;

import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Persone e app", in due passi: 1) chi deve poterti raggiungere sempre (dalla rubrica),
 * 2) con quali app vi scrivete. Salva nella lista Conversazioni, sezione "Sempre".
 */
public class PeopleActivity extends BaseActivity {

    private static final int REQ_CONTACTS = 30;
    private static final String[] CODES = {"WA", "TG", "SMS", "TEL"};

    private int step = 1;
    private final Map<String, Rules.Person> chosen = new LinkedHashMap<String, Rules.Person>();
    private final List<Rules.Person> book = new ArrayList<Rules.Person>();
    private final List<Rules.Person> others = new ArrayList<Rules.Person>();
    private String filter = "";

    private TextView stepLabel, heading, sub, cta, back;
    private EditText search;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        for (Rules.Person p : Rules.people(U.cfg(this, U.K_CONTACTS, U.DEF_CONTACTS))) {
            if (p.section.equalsIgnoreCase("Sempre")) chosen.put(key(p.name), p);
            else others.add(p);
        }

        LinearLayout root = U.col(this);
        root.setBackgroundColor(U.BG);
        root.setPadding(U.dp(this, 20), U.dp(this, 16), U.dp(this, 20), U.dp(this, 16));
        LinearLayout head = U.row(this);
        back = U.text(this, "Annulla", 16, U.INK, false);
        back.setMinHeight(U.dp(this, 44));
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { if (step == 2) { step = 1; render(); } else finish(); }
        });
        head.addView(back, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        stepLabel = U.text(this, "", 14, U.MUTED, false);
        head.addView(stepLabel);
        root.addView(head, U.full());
        heading = U.text(this, "", 22, U.INK, true);
        heading.setPadding(0, U.dp(this, 4), 0, U.dp(this, 2));
        root.addView(heading);
        sub = U.text(this, "", 14, U.MUTED, false);
        sub.setPadding(0, 0, 0, U.dp(this, 8));
        root.addView(sub);

        search = new EditText(this);
        search.setHint("Cerca in rubrica");
        search.setHintTextColor(U.MUTED);
        search.setTextColor(U.INK);
        search.setTextSize(16);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
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

        cta = U.text(this, "", 16, U.BG, true);
        cta.setGravity(Gravity.CENTER);
        cta.setMinHeight(U.dp(this, 52));
        cta.setBackground(U.box(this, U.INK, 0, 26));
        cta.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (step == 1) { step = 2; render(); } else save();
            }
        });
        LinearLayout.LayoutParams clp = U.full();
        clp.topMargin = U.dp(this, 10);
        root.addView(cta, clp);
        setContentView(root);

        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) loadBook();
        else requestPermissions(new String[]{android.Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
        render();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) loadBook();
        render();
    }

    static String key(String name) { return name.trim().toLowerCase(Locale.ITALY); }

    /** Rubrica: una voce per contatto, con il numero di cellulare se c'è. */
    private void loadBook() {
        book.clear();
        Map<String, Rules.Person> byId = new LinkedHashMap<String, Rules.Person>();
        Map<String, Boolean> mobile = new LinkedHashMap<String, Boolean>();
        Cursor cur = null;
        try {
            cur = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE},
                    null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC");
            while (cur != null && cur.moveToNext()) {
                String id = cur.getString(0), name = cur.getString(1), num = cur.getString(2);
                if (name == null || num == null) continue;
                boolean isMobile = cur.getInt(3) == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE;
                Rules.Person p = byId.get(id);
                if (p == null) {
                    p = new Rules.Person();
                    p.section = "Sempre";
                    p.name = name.replace("|", "/");
                    p.target = num;
                    p.apps.add("WA");
                    byId.put(id, p);
                    mobile.put(id, isMobile);
                } else if (isMobile && !mobile.get(id)) {
                    p.target = num;
                    mobile.put(id, true);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        book.addAll(byId.values());
        // le persone già scelte prendono il numero vero dalla rubrica, se il loro è un segnaposto
        for (Rules.Person p : book) {
            Rules.Person c = chosen.get(key(p.name));
            if (c != null && c.target.replaceAll("[^0-9]", "").length() < 6) c.target = p.target;
        }
    }

    private void render() {
        stepLabel.setText(step + " / 2");
        back.setText(step == 1 ? "Annulla" : "‹ Indietro");
        heading.setText(step == 1 ? "Chi deve poterti raggiungere sempre?" : "Come vi scrivete?");
        sub.setText(step == 1 ? "Scegli dalla rubrica. Queste persone passano anche col silenzio attivo."
                : "Scegli le app che usate. Solo queste notifiche di queste persone passeranno.");
        search.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        int n = chosen.size();
        cta.setText(step == 1 ? "Avanti (" + n + (n == 1 ? " persona)" : " persone)") : "Fatto");
        renderList();
    }

    private void renderList() {
        list.removeAllViews();
        if (step == 2) {
            if (chosen.isEmpty()) list.addView(U.text(this, "Nessuna persona scelta: con “Fatto” la sezione Sempre resta vuota.", 15, U.MUTED, false));
            for (final Rules.Person p : chosen.values()) {
                list.addView(U.label(this, p.name));
                LinearLayout chips = U.row(this);
                for (final String code : CODES) {
                    final boolean on = p.apps.contains(code);
                    TextView t = U.text(this, Rules.appName(code), 14, on ? U.BG : U.INK, false);
                    t.setGravity(Gravity.CENTER);
                    t.setMinHeight(U.dp(this, 44));
                    t.setPadding(U.dp(this, 12), 0, U.dp(this, 12), 0);
                    t.setBackground(on ? U.box(this, U.INK, 0, 20) : U.box(this, U.BG, U.LINE, 20));
                    t.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            if (on) { if (p.apps.size() > 1) p.apps.remove(code); else U.toast(PeopleActivity.this, "Almeno un'app"); }
                            else p.apps.add(code);
                            renderList();
                        }
                    });
                    LinearLayout.LayoutParams lp = U.wrap();
                    lp.rightMargin = U.dp(this, 6);
                    chips.addView(t, lp);
                }
                list.addView(chips, U.full());
                if (p.target.length() > 0) {
                    TextView num = U.text(this, p.target, 12, U.MUTED, false);
                    num.setPadding(0, U.dp(this, 4), 0, U.dp(this, 4));
                    list.addView(num);
                }
            }
            return;
        }
        // Passo 1: prima chi è già scelto (anche se non in rubrica), poi la rubrica
        List<Rules.Person> all = new ArrayList<Rules.Person>();
        for (Rules.Person p : chosen.values()) all.add(p);
        for (Rules.Person p : book) if (!chosen.containsKey(key(p.name))) all.add(p);
        if (book.isEmpty()) {
            TextView t = U.text(this, checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                    ? "La rubrica è vuota." : "Tocca qui per permettere a Essenziale di leggere la rubrica.", 15, U.INK, false);
            t.setPadding(0, U.dp(this, 10), 0, U.dp(this, 10));
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { requestPermissions(new String[]{android.Manifest.permission.READ_CONTACTS}, REQ_CONTACTS); }
            });
            list.addView(t);
        }
        int shown = 0;
        for (final Rules.Person p : all) {
            if (filter.length() > 0 && !p.name.toLowerCase(Locale.ITALY).contains(filter)) continue;
            if (shown++ > 300) break;
            final boolean on = chosen.containsKey(key(p.name));
            LinearLayout r = U.row(this);
            r.setMinimumHeight(U.dp(this, 52));
            String n0 = p.name.replace("[", "").trim();
            TextView ini = U.text(this, n0.length() > 0 ? n0.substring(0, 1).toUpperCase(Locale.ITALY) : "?", 15, U.INK, true);
            ini.setGravity(Gravity.CENTER);
            ini.setBackground(U.box(this, U.CHIP, 0, 18));
            r.addView(ini, new LinearLayout.LayoutParams(U.dp(this, 36), U.dp(this, 36)));
            TextView nm = U.text(this, p.name, 16, U.INK, false);
            nm.setPadding(U.dp(this, 12), 0, 0, 0);
            r.addView(nm, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            r.addView(U.checkbox(this, on, U.INK), new LinearLayout.LayoutParams(U.dp(this, 22), U.dp(this, 22)));
            r.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (on) chosen.remove(key(p.name));
                    else { p.section = "Sempre"; chosen.put(key(p.name), p); }
                    render();
                }
            });
            list.addView(r, U.full());
            list.addView(U.divider(this));
        }
    }

    private void save() {
        List<Rules.Person> out = new ArrayList<Rules.Person>();
        for (Rules.Person p : chosen.values()) { p.section = "Sempre"; out.add(p); }
        for (Rules.Person p : others) if (!chosen.containsKey(key(p.name))) out.add(p); // chi diventa "Sempre" non resta anche altrove
        final List<Rules.Person> outF = out;
        // 0.6: la scelta si ritrova in Modifiche recenti
        Changes.record(this, "Persone che passano sempre: " + chosen.size(), new String[]{U.K_CONTACTS});
        U.prefs(this).edit().putString(U.K_CONTACTS, Rules.peopleText(outF)).apply();
        U.toast(this, "Salvato · si ritrova in Modifiche recenti");
        finish();
    }
}
