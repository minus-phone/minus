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
        backToNow = U.text(this, "Torna a questa settimana", 14, U.INK, false);
        backToNow.setPaintFlags(backToNow.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        backToNow.setGravity(Gravity.CENTER);
        backToNow.setMinHeight(U.dp(this, 44));
        backToNow.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { week = 0; render(); } });
        root.addView(backToNow, U.full());

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
        backToNow.setVisibility(week == 0 ? View.INVISIBLE : View.VISIBLE);
        updated.setText("Aggiornato alle " + U.hhmm(System.currentTimeMillis()) + " · si aggiorna a ogni modifica");
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
            List<U.Event> ev = U.events(this, day, U.startOfDay(d + 1));
            for (final U.Event e : ev) {
                LinearLayout r = U.row(this);
                r.setMinimumHeight(U.dp(this, 40));
                r.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { U.openEvent(WeekActivity.this, e); }
                });
                TextView t = U.text(this, e.allDay ? "giorno" : U.hhmm(e.begin), 14, U.MUTED, false);
                t.setTypeface(Typeface.MONOSPACE);
                t.setMinWidth(U.dp(this, 64));
                r.addView(t);
                TextView ti = U.text(this, e.title, 15, U.INK, false);
                ti.setSingleLine(true);
                ti.setEllipsize(TextUtils.TruncateAt.END);
                r.addView(ti, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                if (e.repeats()) r.addView(U.text(this, "↻", 13, U.MUTED, false));
                r.addView(U.text(this, "  ›", 16, U.MUTED, false));
                list.addView(r);
            }
            List<Tasks.Task> tasks = d == 0 ? Tasks.forToday(this, true, false) : Tasks.forDay(this, day);
            for (final Tasks.Task k : tasks) {
                LinearLayout r = U.row(this);
                r.setMinimumHeight(U.dp(this, 38));
                LinearLayout w = U.row(this);
                w.setMinimumWidth(U.dp(this, 64));
                w.addView(U.checkbox(this, k.done, U.INK), new LinearLayout.LayoutParams(U.dp(this, 16), U.dp(this, 16)));
                r.addView(w);
                TextView ti = U.text(this, k.title, 15, k.done ? U.MUTED : U.INK, false);
                if (k.done) ti.setPaintFlags(ti.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                r.addView(ti, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                if (Tasks.overdue(k)) r.addView(U.text(this, "scaduto", 12, U.ALERT, true));
                r.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { Tasks.setDone(WeekActivity.this, k.id, !k.done); render(); }
                });
                list.addView(r);
            }
            if (ev.isEmpty() && tasks.isEmpty()) {
                TextView n = U.text(this, "Niente in programma", 14, U.MUTED, false);
                n.setPadding(0, 0, 0, U.dp(this, 6));
                list.addView(n);
            }
        }
        // Task senza scadenza (solo nella settimana corrente)
        if (week != 0) return;
        boolean header = false;
        for (final Tasks.Task k : Tasks.all(this)) {
            if (k.due != 0 || k.done) continue;
            if (!header) { list.addView(U.divider(this)); list.addView(U.label(this, "Senza scadenza")); header = true; }
            LinearLayout r = U.row(this);
            r.setMinimumHeight(U.dp(this, 38));
            LinearLayout w = U.row(this);
            w.setMinimumWidth(U.dp(this, 64));
            w.addView(U.checkbox(this, false, U.INK), new LinearLayout.LayoutParams(U.dp(this, 16), U.dp(this, 16)));
            r.addView(w);
            r.addView(U.text(this, k.title, 15, U.INK, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            r.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { Tasks.setDone(WeekActivity.this, k.id, true); render(); }
            });
            list.addView(r);
        }
    }
}
