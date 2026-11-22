package it.essenziale.launcher;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 0.24 (Evolutiva 018): la pagina del selettore unico ({@link Scegli}) per le Impostazioni: Chi conta, Servizi, Gruppi,
 * App che rapiscono, App frenate di notte, Notifiche delle app. Extra "kind" = uno dei tipi di {@link Scegli}.
 * Si salva al tocco, con «Annulla»; «Fatto» c'è solo nel primo avvio.
 */
public class SelectorActivity extends PageActivity implements Scegli.Host {

    static final String EXTRA_KIND = "kind";
    private static final int REQ_CONTACTS = 31, REQ_PICK = 32;

    private Scegli scegli;
    private boolean first = true;
    private LinearLayout destBox;

    static Intent intent(android.content.Context c, int kind, String back) {
        Intent i = new Intent(c, SelectorActivity.class).putExtra(EXTRA_KIND, kind);
        if (back != null) i.putExtra("back", back);
        return i;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int kind = getIntent().getIntExtra(EXTRA_KIND, Scegli.PEOPLE);
        LinearLayout root = U.screen(this, Scegli.title(kind));
        String intro = intro(kind);
        if (intro != null) {
            TextView t = U.text(this, intro, 14, U.MUTED, false);
            t.setPadding(0, 0, 0, U.dp(this, 10));
            root.addView(t);
        }
        ScrollView sv = new ScrollView(this);
        LinearLayout body = U.col(this);
        sv.addView(body);
        scegli = new Scegli(this, kind, false, this);
        body.addView(scegli.root, U.full());
        if (kind == Scegli.DOOM) body.addView(previews(), U.full());
        if (kind == Scegli.GROUPS) { destBox = U.col(this); body.addView(destBox, U.full()); destinations(); }
        View pad = new View(this);
        body.addView(pad, new LinearLayout.LayoutParams(1, U.dp(this, 40)));
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private static String intro(int kind) {
        switch (kind) {
            case Scegli.PEOPLE: return "Queste persone ti raggiungono sempre, anche col silenzio: messaggi e chiamate.";
            case Scegli.SERVICES: return "Banca, pagamenti, SPID, app IO: i loro codici e accessi passano sempre, anche con il silenzio; il resto aspetta in Notifiche, mai in Altro.";
            case Scegli.GROUPS: return "Quelli che hai visto scrivere. Gli altri aspettano in Per te.";
            case Scegli.DOOM: return "Si aprono dopo qualche secondo di pausa. E se scegli «scopo» ti chiedono anche per cosa e per quanto.";
            case Scegli.NIGHT: return "Dall'ora di staccare alla sveglia queste app ti chiedono per cosa le apri e per quanto: 2 o 5 minuti. Di giorno restano come le hai scelte.";
            default: return "Per ogni app: passa sempre, in Per te o in Altro. Le altre le decide −minus.";
        }
    }

    /** Dove vanno i gruppi che aspettano, app per app: Per te o Altro (scelte che si escludono, quindi chip). */
    private void destinations() {
        destBox.removeAllViews();
        for (final String code : new String[]{"WA", "TG"}) {
            TextView q = U.text(this, "I gruppi che aspettano, di " + Rules.appName(code) + ", vanno in:", 15, U.INK, false);
            q.setPadding(0, U.dp(this, 18), 0, U.dp(this, 8));
            destBox.addView(q);
            String dest = U.groupDest(this).get(code);
            if (dest == null) dest = Rules.DEST_OTHER;
            LinearLayout chips = U.row(this);
            destChip(chips, code, "Per te", "contati", Rules.DEST_NOTIF, dest);
            destChip(chips, code, "Altro", "non contati", Rules.DEST_OTHER, dest);
            destBox.addView(chips, U.full());
        }
    }

    private void destChip(LinearLayout chips, final String code, String title, String sub, final String value, String cur) {
        boolean on = value.equals(cur);
        LinearLayout c = U.col(this);
        c.setGravity(Gravity.CENTER);
        c.setMinimumHeight(U.dp(this, 52));
        if (on) Press.chipOn(c, 14); else Press.chip(c, U.BG, U.LINE, 14);
        TextView t = U.text(this, title, 15, on ? U.BG : U.INK, true);
        t.setGravity(Gravity.CENTER);
        c.addView(t);
        TextView s = U.text(this, sub, 12, on ? U.BG : U.MUTED, false);
        s.setGravity(Gravity.CENTER);
        c.addView(s);
        c.setContentDescription(Rules.appName(code) + ": " + title + (on ? ", scelto" : ""));
        c.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Changes.track(SelectorActivity.this, "Gruppi che aspettano di " + Rules.appName(code) + ": in " + (Rules.DEST_NOTIF.equals(value) ? "Per te" : "Altro"),
                        new String[]{U.K_GROUP_DEST},
                        new Runnable() { public void run() { U.setGroupDest(SelectorActivity.this, code, value); } },
                        new Runnable() { public void run() { destinations(); } });
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (Rules.DEST_NOTIF.equals(value)) lp.rightMargin = U.dp(this, 8);
        chips.addView(c, lp);
    }

    private View previews() {
        LinearLayout prevs = U.row(this);
        prevs.addView(preview("Anteprima pausa", false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        prevs.addView(preview("Anteprima scopo", true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return prevs;
    }

    private View preview(String s, final boolean scope) {
        TextView t = U.text(this, s + " ›", 14, U.INK, true);
        t.setMinHeight(U.dp(this, 48));
        t.setGravity(Gravity.CENTER);
        t.setPadding(U.dp(this, 14), 0, U.dp(this, 14), 0);
        Press.link(t, U.INK);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(SelectorActivity.this, FrictionActivity.class).putExtra("scope", scope)); }
        });
        android.widget.FrameLayout w = new android.widget.FrameLayout(this);
        w.addView(t, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return w;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (first) { first = false; return; }
        scegli.reload(); // tornata da Android: il permesso o i dati possono essere cambiati
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (scegli != null && scegli.kind() == Scegli.PEOPLE) Stars.starAlways(this); // chi conta prende la stella, come nel primo avvio
    }

    public void grantContacts() {
        Onboarding.askOrInfo(this, new String[]{android.Manifest.permission.READ_CONTACTS, android.Manifest.permission.WRITE_CONTACTS}, REQ_CONTACTS);
    }

    public void pickFromAndroid() {
        try { startActivityForResult(new Intent(Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI), REQ_PICK); }
        catch (Exception e) { U.toast(this, "Non trovo la rubrica"); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PICK && res == RESULT_OK && data != null && data.getData() != null) scegli.picked(data.getData());
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        if (scegli != null) scegli.reload();
    }
}
