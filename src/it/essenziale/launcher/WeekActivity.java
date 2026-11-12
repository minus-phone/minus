package it.essenziale.launcher;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** 7 giorni alla volta, da oggi, avanti e indietro di settimana in settimana. Tocca un impegno per modificarlo. */
public class WeekActivity extends BaseActivity {

    private LinearLayout list;
    private TextView range, backToNow, updated;
    private int week = 0;
    private android.database.ContentObserver observer;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = U.screen(this, "Settimana");
        // 0.15: «Torna a questa settimana» a destra del titolo, solo fuori dalla settimana attuale
        View title = root.getChildAt(1);
        root.removeView(title);
        LinearLayout tr = U.row(this);
        tr.setGravity(Gravity.BOTTOM);
        tr.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        backToNow = U.text(this, "Torna a questa settimana", 14, U.INK, false);
        backToNow.setPaintFlags(backToNow.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        backToNow.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        backToNow.setMinHeight(U.dp(this, 44));
        backToNow.setPadding(U.dp(this, 12), 0, 0, 0);
        backToNow.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { week = 0; render(); } });
        tr.addView(backToNow);
        root.addView(tr, U.full());
        // 0.6: niente + in alto; ogni giorno ha il suo +
        // ‹  19 – 25 ottobre  ›
        LinearLayout nav = U.row(this);
        TextView prev = U.text(this, "‹", 24, U.INK, false);
        prev.setGravity(Gravity.CENTER);
        prev.setContentDescription("Settimana precedente");
        prev.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { week--; render(); } });
        nav.addView(prev, new LinearLayout.LayoutParams(U.dp(this, 48), U.dp(this, 48)));
        range = U.text(this, "", 16, U.INK, true);
        range.setGravity(Gravity.CENTER);
        nav.addView(range, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView next = U.text(this, "›", 24, U.INK, false);
        next.setGravity(Gravity.CENTER);
        next.setContentDescription("Settimana successiva");
        next.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { week++; render(); } });
        nav.addView(next, new LinearLayout.LayoutParams(U.dp(this, 48), U.dp(this, 48)));
        root.addView(nav, U.full());

        ScrollView sv = new ScrollView(this);
        list = U.col(this);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        updated = U.text(this, "", 12, U.MUTED, false);
        updated.setGravity(Gravity.CENTER);
        updated.setPadding(0, U.dp(this, 6), 0, 0);
        root.addView(updated, U.full());
        setContentView(root);
        observer = U.calendarObserver(new Runnable() { public void run() { render(); } });
    }

    @Override
    protected void onResume() {
        super.onResume();
        U.watchCalendar(this, observer, true);
        render();
    }

    @Override
    protected void onPause() {
        U.watchCalendar(this, observer, false);
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) { render(); }

    private void render() {
        list.removeAllViews();
        int base = week * 7;
        java.text.SimpleDateFormat fd = new java.text.SimpleDateFormat("d MMM", java.util.Locale.ITALY);
        range.setText(fd.format(new java.util.Date(U.startOfDay(base))) + " – " + fd.format(new java.util.Date(U.startOfDay(base + 6))));
        backToNow.setVisibility(week == 0 ? View.GONE : View.VISIBLE);
        updated.setText("Aggiornato alle " + U.hhmm(System.currentTimeMillis()) + " · si aggiorna a ogni modifica");
        // 0.15: i task senza scadenza in alto, dove si vedono (prima stavano in fondo e si perdevano)
        if (week == 0) {
            List<Tasks.Task> und = Tasks.undated(this);
            if (!und.isEmpty()) {
                list.addView(U.label(this, "Senza scadenza"));
                for (Tasks.Task k : und) list.addView(taskRow(k));
            }
        }
        for (int d = base; d < base + 7; d++) {
            long day = U.startOfDay(d);
            list.addView(U.divider(this));
            String name = d == 0 ? "Oggi · " + U.dayLabel(day) : d == 1 ? "Domani · " + U.dayLabel(day) : U.dayLabel(day);
            // 0.6: un + per ogni giorno, che apre Aggiungi con quel giorno già scelto
            LinearLayout head = U.row(this);
            head.addView(U.label(this, name), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            final long dayF = day;
            TextView plus = U.text(this, "+", 22, U.INK, false);
            plus.setGravity(Gravity.CENTER);
            plus.setContentDescription("Aggiungi a " + name);
            plus.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Intent i = new Intent(WeekActivity.this, AddActivity.class);
                    i.putExtra("day", dayF);
                    startActivity(i);
                }
            });
            head.addView(plus, new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 44)));
            list.addView(head, U.full());
            // 0.15: da fare con scadenza quel giorno (oggi anche gli scaduti), più i completati in quel giorno
            List<Tasks.Task> tasks = new java.util.ArrayList<Tasks.Task>();
            if (d == 0) { for (Tasks.Task k : Tasks.forToday(this, false, false)) tasks.add(k); }
            else if (d > 0) tasks.addAll(Tasks.forDay(this, day));
            // E001: in ogni giorno gli urgenti vanno in cima, prima degli impegni
            for (Tasks.Task k : tasks) if (k.urgent) list.addView(taskRow(k));
            List<U.Event> ev = U.events(this, day, U.startOfDay(d + 1));
            for (final U.Event e : ev) {
                LinearLayout r = U.row(this);
                r.setMinimumHeight(U.dp(this, 40));
                r.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { U.openEvent(WeekActivity.this, e); }
                });
                r.setOnLongClickListener(new View.OnLongClickListener() { // 0.15: Modifica impegno, Elimina impegno
                    public boolean onLongClick(View v) { Menu.event(WeekActivity.this, e); return true; }
                });
                TextView t = U.text(this, e.allDay ? "giorno" : U.hhmm(e.begin), 14, U.MUTED, false);
                t.setTypeface(U.SERIF);
                t.setMinWidth(U.dp(this, 64));
                r.addView(t);
                TextView ti = U.text(this, e.title, 15, U.INK, false);
                ti.setSingleLine(true);
                ti.setEllipsize(TextUtils.TruncateAt.END);
                r.addView(ti, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                if (e.repeats()) r.addView(U.text(this, "↻", 13, U.MUTED, false));
                list.addView(r);
            }
            for (Tasks.Task k : tasks) if (!k.urgent) list.addView(taskRow(k));
            tasks.addAll(Tasks.doneOn(this, day));
            for (Tasks.Task k : tasks) if (k.done) list.addView(taskRow(k));
            if (ev.isEmpty() && tasks.isEmpty()) {
                TextView n = U.text(this, "Niente in programma", 14, U.MUTED, false);
                n.setPadding(0, 0, 0, U.dp(this, 6));
                list.addView(n);
            }
        }
    }

    /** 0.15: un task con la casella, il collegamento alla sua app (come in home) e «scaduto» in rosso. */
    private View taskRow(final Tasks.Task k) {
        LinearLayout r = U.row(this);
        r.setMinimumHeight(U.dp(this, 40));
        LinearLayout w = U.row(this);
        w.setMinimumWidth(U.dp(this, 30));
        w.setMinimumHeight(U.dp(this, 44));
        w.setGravity(Gravity.CENTER_VERTICAL);
        w.addView(U.checkbox(this, k.done, U.INK), new LinearLayout.LayoutParams(U.dp(this, 18), U.dp(this, 18)));
        r.addView(w);
        r.setMinimumHeight(U.dp(this, 44));
        TextView ti = U.text(this, k.title, 15, k.done ? U.BORDER : U.INK, false);
        if (k.urgent && !k.done) ti.setText(U.bang(k.title));
        if (Tasks.overdue(k)) ti.setTypeface(android.graphics.Typeface.create(ti.getTypeface(), android.graphics.Typeface.ITALIC));
        if (k.done) ti.setPaintFlags(ti.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        ti.setSingleLine(true);
        ti.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(ti, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (Tasks.overdue(k)) {
            TextView sc = U.text(this, "Scaduto", 12, U.MUTED, true);
            sc.setTypeface(android.graphics.Typeface.create(U.SANS_B, android.graphics.Typeface.ITALIC));
            r.addView(sc);
        }
        View.OnClickListener toggle = new View.OnClickListener() {
            public void onClick(View v) { Tasks.setDone(WeekActivity.this, k.id, !k.done); render(); }
        };
        if (k.app != null && !k.done && U.installed(this, k.app)) {
            final String label = U.appLabel(this, k.app);
            TextView open = U.text(this, label + " ›", 13, U.MUTED, false);
            open.setPadding(U.dp(this, 10), 0, 0, 0);
            r.addView(open);
            w.setOnClickListener(toggle);
            w.setContentDescription("Segna fatto: " + k.title);
            r.setContentDescription(k.title + ", apri " + label);
            r.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { if (!U.launchPkg(WeekActivity.this, k.app)) U.toast(WeekActivity.this, "Non riesco ad aprire " + label); }
            });
        } else {
            r.setOnClickListener(toggle);
            if (k.done) r.setContentDescription(k.title + ", completato. Tocca per riaprirlo");
        }
        return r;
    }
}
