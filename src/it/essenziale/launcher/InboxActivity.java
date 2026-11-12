package it.essenziale.launcher;

import android.app.Activity;
import android.app.PendingIntent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** "Cosa mi sono persa?": In attesa (contato) e Altro (mai contato). */
public class InboxActivity extends BaseActivity implements Store.Listener {

    private int level = Store.WAITING;
    private LinearLayout list;
    private TextView tabWaiting, tabOther, clearAll;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = U.screen(this, "Notifiche");
        // 0.15: ⚙ a destra del titolo, 26: le impostazioni delle notifiche
        View title = root.getChildAt(1);
        root.removeView(title);
        LinearLayout tr = U.row(this);
        tr.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView gear = U.text(this, "⚙", 26, U.INK, false);
        gear.setGravity(Gravity.CENTER);
        gear.setContentDescription("Impostazioni delle notifiche");
        gear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new android.content.Intent(InboxActivity.this, SettingsActivity.class)
                        .putExtra("area", SettingsActivity.NOTIF).putExtra("back", "Notifiche"));
            }
        });
        tr.addView(gear, new LinearLayout.LayoutParams(U.dp(this, 48), U.dp(this, 48)));
        root.addView(tr, U.full());

        LinearLayout tabs = U.row(this);
        tabs.setBackground(U.box(this, U.CHIP, 0, 24));
        tabs.setPadding(U.dp(this, 4), U.dp(this, 4), U.dp(this, 4), U.dp(this, 4));
        tabWaiting = tab("Per te", Store.WAITING);
        tabOther = tab("Altro", Store.OTHER);
        tabs.addView(tabWaiting, new LinearLayout.LayoutParams(0, U.dp(this, 42), 1f));
        tabs.addView(tabOther, new LinearLayout.LayoutParams(0, U.dp(this, 42), 1f));
        LinearLayout.LayoutParams tlp = U.full();
        tlp.topMargin = U.dp(this, 8);
        tlp.bottomMargin = U.dp(this, 6);
        root.addView(tabs, tlp);

        ScrollView sv = new ScrollView(this);
        list = U.col(this);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        clearAll = U.text(this, "Segna tutto come visto", 15, U.INK, true);
        clearAll.setGravity(Gravity.CENTER);
        clearAll.setMinHeight(U.dp(this, 48));
        clearAll.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Store.clearLevel(InboxActivity.this, level); }
        });
        root.addView(clearAll, U.full());
        setContentView(root);
    }

    private TextView tab(String s, final int lvl) {
        TextView t = U.text(this, s, 15, U.INK, true);
        t.setGravity(Gravity.CENTER);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { level = lvl; render(); }
        });
        return t;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Store.addListener(this);
        render();
    }

    @Override
    protected void onPause() {
        Store.removeListener(this);
        super.onPause();
    }

    @Override
    public void onChanged() {
        runOnUiThread(new Runnable() { public void run() { render(); } });
    }

    private void render() {
        int n = Store.count(this, Store.WAITING);
        tabWaiting.setText(n > 0 ? "Per te  " + n : "Per te");
        style(tabWaiting, level == Store.WAITING);
        style(tabOther, level == Store.OTHER);
        list.removeAllViews();
        // 0.15: Altro non si accumula, e la pagina lo dice
        if (level == Store.OTHER) {
            TextView d = U.text(this, "Qui restano 24 ore, poi spariscono da sole.", 12, U.MUTED, false);
            d.setPadding(0, U.dp(this, 4), 0, U.dp(this, 8));
            list.addView(d, U.full());
        }
        List<Store.Item> items = Store.load(this);
        // 0.15: un'app nuova (installata dopo −minus) arriva qui, mai in Altro, con una domanda una volta sola
        if (level == Store.WAITING) {
            java.util.Set<String> asked = new java.util.LinkedHashSet<String>();
            for (Store.Item it : items) if (it.level == Store.WAITING && !asked.contains(it.pkg) && U.isFreshApp(this, it.pkg)) {
                asked.add(it.pkg);
                list.addView(askCard(it.pkg, it.app), U.full());
            }
        }
        int shown = 0;
        for (final Store.Item it : items) {
            if (it.level != level) continue;
            shown++;
            list.addView(itemView(it), U.full());
            list.addView(U.divider(this));
        }
        if (shown == 0) {
            TextView e = U.text(this, level == Store.WAITING ? "Niente per te." : "Niente qui.", 16, U.MUTED, false);
            e.setPadding(0, U.dp(this, 24), 0, 0);
            e.setGravity(Gravity.CENTER);
            list.addView(e, U.full());
        }
        clearAll.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
        if (shown > 0) {
            TextView hint = U.text(this, "Tieni premuto un messaggio di WhatsApp, Telegram o SMS per far passare sempre chi lo ha scritto, o il gruppo.", 12, U.MUTED, false);
            hint.setPadding(0, U.dp(this, 12), 0, U.dp(this, 8));
            list.addView(hint, U.full());
        }
        if (!U.listenerEnabled(this)) {
            TextView w = U.text(this, "Il filtro notifiche non è attivo: si accende nelle impostazioni qui sotto.", 14, U.MUTED, false);
            w.setPadding(0, U.dp(this, 16), 0, 0);
            list.addView(w, U.full());
        }
        // 0.15: da Notifiche si arriva alle sue impostazioni (chi conta, servizi, gruppi, silenzio)
        TextView set = U.text(this, "Impostazioni delle notifiche  ›", 15, U.INK, false);
        set.setMinHeight(U.dp(this, 48));
        set.setGravity(Gravity.CENTER_VERTICAL);
        set.setPadding(0, U.dp(this, 12), 0, U.dp(this, 20));
        set.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new android.content.Intent(InboxActivity.this, SettingsActivity.class)
                        .putExtra("area", SettingsActivity.NOTIF).putExtra("back", "Notifiche"));
            }
        });
        list.addView(set, U.full());
    }

    private void style(TextView t, boolean on) {
        t.setBackground(on ? U.box(this, U.INK, 0, 20) : null);
        t.setTextColor(on ? U.BG : U.INK);
    }

    /** 0.15: «Nuova app: le sue notifiche?» con le tre scelte. */
    private View askCard(final String pkg, final String app) {
        LinearLayout c = U.col(this);
        c.setPadding(U.dp(this, 14), U.dp(this, 12), U.dp(this, 14), U.dp(this, 12));
        c.setBackground(U.box(this, U.SURFACE, U.INK, 14));
        c.addView(U.text(this, "Nuova app · " + app, 15, U.INK, true));
        TextView q = U.text(this, "Le sue notifiche? Si cambia in Impostazioni › Meno notifiche › Notifiche delle app.", 12, U.MUTED, false);
        q.setPadding(0, U.dp(this, 2), 0, U.dp(this, 8));
        c.addView(q);
        String[][] opts = {{Rules.APP_ALWAYS, "Passano sempre"}, {Rules.APP_WAITING, "Restano qui"}, {Rules.APP_OTHER, "In Altro"}};
        LinearLayout row = U.row(this);
        for (final String[] o : opts) {
            TextView t = U.text(this, o[1], 13, U.INK, false);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(U.dp(this, 44));
            t.setPadding(U.dp(this, 10), 0, U.dp(this, 10), 0);
            t.setBackground(U.box(this, U.BG, U.INK, 22));
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Changes.track(InboxActivity.this, app + ": " + o[1].toLowerCase(java.util.Locale.ITALY), new String[]{U.K_APP_CHOICE},
                            new Runnable() { public void run() { U.setAppChoice(InboxActivity.this, pkg, o[0]); } },
                            new Runnable() { public void run() { render(); } });
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = U.dp(this, 6);
            row.addView(t, lp);
        }
        c.addView(row, U.full());
        LinearLayout wrap = U.col(this);
        wrap.setPadding(0, U.dp(this, 4), 0, U.dp(this, 10));
        wrap.addView(c, U.full());
        return wrap;
    }

    private View itemView(final Store.Item it) {
        LinearLayout r = U.row(this);
        r.setPadding(0, U.dp(this, 12), 0, U.dp(this, 12));
        LinearLayout txt = U.col(this);
        String meta = it.app + " · " + U.hhmm(it.time);
        if (it.time < U.startOfDay(0)) meta = it.app + " · " + U.dayLabel(it.time) + " " + U.hhmm(it.time);
        // 0.13 (178c): l'icona della barra di stato al posto del nome dell'app, se c'è
        android.graphics.drawable.Drawable ic = AppIcons.drawable(this, it.pkg, U.MUTED, 14);
        if (ic != null && it.app != null && meta.startsWith(it.app + " · ")) meta = meta.substring(it.app.length() + 3); else ic = null;
        TextView m = U.text(this, meta.toUpperCase(java.util.Locale.ITALY), 11, U.MUTED, false);
        m.setLetterSpacing(0.05f);
        if (ic != null) {
            m.setCompoundDrawablesRelative(ic, null, null, null);
            m.setCompoundDrawablePadding(U.dp(this, 6));
            m.setContentDescription(it.app + ", " + meta);
        }
        txt.addView(m);
        TextView t = U.text(this, TextUtils.isEmpty(it.title) ? it.app : it.title, 16, U.INK, true);
        txt.addView(t);
        if (!TextUtils.isEmpty(it.text)) {
            TextView d = U.text(this, it.text, 14, U.MUTED, false);
            d.setMaxLines(3);
            d.setEllipsize(TextUtils.TruncateAt.END);
            txt.addView(d);
        }
        r.addView(txt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView seen = U.text(this, "✓", 18, U.INK, false);
        seen.setGravity(Gravity.CENTER);
        seen.setContentDescription("Segna come visto");
        seen.setBackground(U.box(this, U.BG, U.LINE, 22));
        seen.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Store.remove(InboxActivity.this, it.id); }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 44));
        slp.leftMargin = U.dp(this, 10);
        r.addView(seen, slp);
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { open(it); }
        });
        // 0.6: da un messaggio di chat si sceglie subito chi far passare sempre
        if (Rules.chatCode(it.pkg) != null && !TextUtils.isEmpty(it.title)) {
            r.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) { Pass.sheet(InboxActivity.this, it.pkg, it.title, new Runnable() { public void run() { render(); } }); return true; }
            });
        } else if (Rules.chatCode(it.pkg) == null) {
            // 0.15: per le altre app si sceglie dove vanno le sue notifiche
            r.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) { AppChoice.ask(InboxActivity.this, it.pkg, it.app, new Runnable() { public void run() { render(); } }); return true; }
            });
        }
        return r;
    }

    /** 0.15: una chiamata persa apre il registro delle chiamate, non i contatti. */
    static boolean missedCall(Store.Item it) {
        boolean dialer = false;
        for (String d : Rules.DIALERS) if (d.equals(it.pkg)) dialer = true;
        String t = ((it.title == null ? "" : it.title) + " " + (it.text == null ? "" : it.text)).toLowerCase(java.util.Locale.ITALY);
        return dialer || t.contains("chiamata persa") || t.contains("chiamate perse") || t.contains("missed call");
    }

    private void open(Store.Item it) {
        boolean ok = false;
        if (missedCall(it)) {
            android.content.Intent log = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            log.setType(android.provider.CallLog.Calls.CONTENT_TYPE);
            log.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(log); Store.remove(this, it.id); return; } catch (Exception ignored) {}
        }
        PendingIntent pi = Store.intent(it.id);
        if (pi != null) {
            try { pi.send(); ok = true; } catch (Exception ignored) {}
        }
        if (!ok) ok = U.launchPkg(this, it.pkg);
        // 0.4: aprire una chat da Notifiche la apre per 12 ore (ti stai scrivendo con quella persona)
        if (ok) Convs.open(this, Rules.chatCode(it.pkg), it.title, null);
        Store.remove(this, it.id);
        if (!ok) U.toast(this, "Non riesco ad aprire " + it.app);
    }
}
