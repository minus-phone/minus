package it.essenziale.launcher;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Quali calendari mostrare (e sincronizzare) in Essenziale, e dove vanno i nuovi impegni. */
public class CalendarsActivity extends BaseActivity {

    private LinearLayout list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = U.screen(this, "Calendari");
        TextView intro = U.text(this, "Scegli quali calendari mostrare e sincronizzare. Gli altri restano negli account, ma non compaiono in −minus.", 14, U.MUTED, false);
        intro.setPadding(0, U.dp(this, 4), 0, U.dp(this, 6));
        root.addView(intro);
        ScrollView sv = new ScrollView(this);
        list = U.col(this);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        if (!U.hasCalendar(this) || !U.canWriteCalendar(this))
            requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR}, 40);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) { render(); }

    static String accountLabel(U.CalRow r) {
        String t = r.type == null ? "" : r.type;
        String a = r.account == null ? "" : r.account;
        if (t.equals("com.google")) return "Google · " + a;
        if (t.equals("LOCAL")) return "Sul telefono";
        if (t.contains("exchange") || t.contains("outlook") || t.contains("microsoft")) return "Lavoro · " + a;
        if (t.contains("ics") || t.contains("subscri")) return "Abbonamenti · " + a;
        return a.length() > 0 ? a : t;
    }

    private void render() {
        list.removeAllViews();
        if (!U.hasCalendar(this)) {
            list.addView(U.text(this, "Serve il permesso di leggere il calendario.", 15, U.INK, false));
            return;
        }
        List<U.CalRow> cals = U.allCalendars(this);
        final Set<Long> shown = U.shownCalendars(this);
        String account = null;
        for (final U.CalRow r : cals) {
            String acc = accountLabel(r);
            if (!acc.equals(account)) { account = acc; list.addView(U.label(this, acc)); }
            final boolean on = shown.contains(r.id);
            LinearLayout row = U.row(this);
            row.setMinimumHeight(U.dp(this, 54));
            LinearLayout tx = U.col(this);
            tx.addView(U.text(this, r.name, 16, U.INK, false));
            String note = r.access >= android.provider.CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR ? "" : "Solo lettura";
            if (!r.sync) note = (note.length() > 0 ? note + " · " : "") + "Non sincronizzato";
            if (note.length() > 0) tx.addView(U.text(this, note, 12, U.MUTED, false));
            row.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(toggle(on), new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 26)));
            row.setContentDescription(r.name + (on ? ", mostrato" : ", nascosto"));
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Changes.track(CalendarsActivity.this, r.name + (on ? ": nascosto" : ": mostrato"), new String[]{U.K_CAL_SEL}, new Runnable() { public void run() {
                        if (on) shown.remove(r.id);
                        else {
                            shown.add(r.id);
                            if (!r.sync && U.canWriteCalendar(CalendarsActivity.this)) U.setSync(CalendarsActivity.this, r.id, true);
                        }
                        U.setShownCalendars(CalendarsActivity.this, shown);
                    } }, new Runnable() { public void run() { render(); } });
                }
            });
            list.addView(row, U.full());
            list.addView(U.divider(this));
        }
        if (cals.isEmpty()) list.addView(U.text(this, "Nessun calendario sul telefono. Aggiungi un account Google in Impostazioni di Android › Account.", 15, U.INK, false));

        // Calendario per i nuovi impegni. 0.15: scelta in pagina, non in un popup; anche «solo in −minus»
        list.addView(U.label(this, "I nuovi impegni vanno in"));
        if (!U.canWriteCalendar(this)) {
            TextView ask = U.text(this, "Per aggiungere impegni serve il permesso di modificare il calendario ›", 15, U.INK, false);
            ask.setMinHeight(U.dp(this, 48));
            ask.setGravity(android.view.Gravity.CENTER_VERTICAL);
            ask.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                requestPermissions(new String[]{android.Manifest.permission.WRITE_CALENDAR, android.Manifest.permission.READ_CALENDAR}, 41);
            } });
            list.addView(ask, U.full());
        } else {
            final long cur = U.prefs(this).getLong("last_cal", -1);
            for (final U.Cal k : U.writableCalendars(this)) {
                if (U.isMinusCal(k)) continue;
                defaultRow(k.name, k.google() ? "Google · si vede anche in Google Calendar" : U.calNote(k), cur == k.id, k.id);
            }
            long mine = U.minusCalendar(this, false);
            defaultRow("Nessuno: restano in −minus", "Su questo telefono, senza andare in Google Calendar", cur >= 0 && cur == mine, -2);
        }
        TextView n = U.text(this, "Il calendario iCloud condiviso col partner arriva tramite l'account Google o con un'app di sincronizzazione.", 12, U.MUTED, false);
        n.setPadding(0, U.dp(this, 10), 0, U.dp(this, 20));
        list.addView(n);
    }

    /** Una riga a scelta singola; id -2 = il calendario di −minus, creato se manca. */
    private void defaultRow(final String name, String sub, boolean on, final long id) {
        LinearLayout r = U.row(this);
        r.setMinimumHeight(U.dp(this, 56));
        LinearLayout tx = U.col(this);
        tx.addView(U.text(this, name, 15, U.INK, false));
        tx.addView(U.text(this, sub, 12, U.MUTED, false));
        r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView dot = U.text(this, on ? "●" : "○", 18, U.INK, false);
        r.addView(dot);
        r.setContentDescription(name + (on ? ", scelto" : ""));
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Changes.track(CalendarsActivity.this, "Nuovi impegni in " + name, new String[]{"last_cal", "last_cal_name"}, new Runnable() { public void run() {
                    long k = id == -2 ? U.minusCalendar(CalendarsActivity.this, true) : id;
                    if (k >= 0) U.setDefaultCalendar(CalendarsActivity.this, k, id == -2 ? U.NAME : name);
                } }, new Runnable() { public void run() { render(); } });
            }
        });
        list.addView(r, U.full());
        list.addView(U.divider(this));
    }

    View toggle(boolean on) {
        LinearLayout sw = U.row(this);
        sw.setGravity(on ? android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL : android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        sw.setPadding(U.dp(this, 3), U.dp(this, 3), U.dp(this, 3), U.dp(this, 3));
        sw.setBackground(U.box(this, on ? U.INK : U.BORDER, 0, 13));
        View knob = new View(this);
        knob.setBackground(U.box(this, U.SURFACE, 0, 10));
        sw.addView(knob, new LinearLayout.LayoutParams(U.dp(this, 20), U.dp(this, 20)));
        return sw;
    }
}
