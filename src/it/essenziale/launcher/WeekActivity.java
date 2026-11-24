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
public class WeekActivity extends PageActivity {

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
        backToNow.setPadding(U.dp(this, 12), 0, U.dp(this, 4), 0);
        PressB.link(backToNow); // 0.21: link di testo, anello di 6 dp
        backToNow.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { week = 0; render(); } });
        tr.addView(backToNow);
        root.addView(tr, U.full());
        // 0.6: niente + in alto; ogni giorno ha il suo +
        // ‹  19 – 25 ottobre  ›
        LinearLayout nav = U.row(this);
        TextView prev = U.text(this, "‹", 24, U.INK, false);
        prev.setGravity(Gravity.CENTER);
        prev.setContentDescription("Settimana precedente");
        PressB.icon(prev); // 0.21: alone circolare di 56 dp
        prev.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { week--; render(); } });
        nav.addView(prev, new LinearLayout.LayoutParams(U.dp(this, 48), U.dp(this, 48)));
        range = U.text(this, "", 16, U.INK, true);
        range.setGravity(Gravity.CENTER);
        nav.addView(range, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView next = U.text(this, "›", 24, U.INK, false);
        next.setGravity(Gravity.CENTER);
        next.setContentDescription("Settimana successiva");
        PressB.icon(next);
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

    private Swipe swipe;

    /** E003: da Settimana, scorrendo verso destra si torna a Oggi. Di lato non cambia settimana: restano ‹ e ›. */
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        if (swipe == null) swipe = new Swipe(this, new boolean[]{false}, new Swipe.Act() {
            public void up() {}
            public void left() {}
            public void right() { finish(); overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out); }
        });
        if (swipe.detector.onTouchEvent(e)) {
            android.view.MotionEvent cancel = android.view.MotionEvent.obtain(e);
            cancel.setAction(android.view.MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
            return true;
        }
        return super.dispatchTouchEvent(e);
    }

    @Override
    protected void onResume() {
        super.onResume();
        U.watchCalendar(this, observer, true);
        if (busyRows == 0) hiddenDone.clear(); // tornando in Settimana, i task fatti stanno di nuovo tra i completati
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
        if (busyRows > 0) { dirty = true; return; } // una riga si sta chiudendo: la pagina si rifà alla fine
        list.removeAllViews();
        int base = week * 7;
        java.text.SimpleDateFormat fd = new java.text.SimpleDateFormat("d MMM", java.util.Locale.ITALY);
        range.setText(fd.format(new java.util.Date(U.startOfDay(base))) + " – " + fd.format(new java.util.Date(U.startOfDay(base + 6))));
        backToNow.setVisibility(week == 0 ? View.INVISIBLE : View.VISIBLE); // 0.22: INVISIBLE tiene l'altezza: il titolo non si sposta tra le settimane
        updated.setText("Aggiornato alle " + U.hhmm(System.currentTimeMillis()) + " · si aggiorna a ogni modifica");
        // 0.15: i task senza scadenza in alto, dove si vedono (prima stavano in fondo e si perdevano)
        if (week == 0) {
            List<Tasks.Task> und = Tasks.undated(this);
            if (!und.isEmpty()) {
                // 0.16: un «+» anche qui, per aggiungere un task senza scadenza
                LinearLayout uh = U.row(this);
                uh.addView(U.label(this, "Senza scadenza"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView up = U.text(this, "+", 22, U.INK, false);
                up.setGravity(Gravity.CENTER);
                up.setContentDescription("Aggiungi un task senza scadenza");
                PressB.icon(up);
                up.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { startActivity(new Intent(WeekActivity.this, AddActivity.class).putExtra("kind", "task").putExtra("day", 0L)); }
                });
                uh.addView(up, new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 44)));
                list.addView(uh, U.full());
                for (Tasks.Task k : und) if (!hiddenDone.contains(k.id)) list.addView(taskRow(k));
            }
        }
        for (int d = base; d < base + 7; d++) {
            long day = U.startOfDay(d);
            list.addView(U.divider(this));
            String name = d == 0 ? "Oggi · " + U.dayLabel(day) : d == 1 ? "Domani · " + U.dayLabel(day) : U.dayLabel(day);
            // 0.6: un + per ogni giorno, che apre Aggiungi con quel giorno già scelto
            LinearLayout head = U.row(this);
            List<U.Event> allEv = U.events(this, day, U.startOfDay(d + 1));
            View chips = U.allDayChipsPressed(this, allEv); // 0.17 (Evolutiva 007): tutto il giorno sulla riga del giorno
            TextView hl = U.label(this, name);
            hl.setMinWidth(U.dp(this, 64));
            if (chips == null) head.addView(hl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            else {
                head.addView(hl);
                LinearLayout cw = new LinearLayout(this);
                cw.setPadding(U.dp(this, 8), U.dp(this, 4), 0, U.dp(this, 4));
                cw.addView(chips, U.full());
                head.addView(cw, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            final long dayF = day;
            TextView plus = U.text(this, "+", 22, U.INK, false);
            plus.setGravity(Gravity.CENTER);
            plus.setContentDescription("Aggiungi a " + name);
            PressB.icon(plus);
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
            for (Tasks.Task k : tasks) if (k.urgent && !hiddenDone.contains(k.id)) list.addView(taskRow(k));
            List<U.Event> ev = new java.util.ArrayList<U.Event>();
            for (U.Event e : allEv) if (!e.allDay) ev.add(e);
            for (final U.Event e : ev) {
                LinearLayout r = U.row(this);
                r.setMinimumHeight(U.dp(this, 40));
                PressB.row(r); // 0.21: riga senza bordo, si scurisce il fondo
                r.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { U.openEvent(WeekActivity.this, e); }
                });
                r.setOnLongClickListener(new View.OnLongClickListener() { // 0.15: Modifica impegno, Elimina impegno
                    public boolean onLongClick(View v) { Menu.event(WeekActivity.this, e); return true; }
                });
                TextView t = U.text(this, U.hhmm(e.begin), 14, U.MUTED, false);
                t.setTypeface(U.SERIF);
                t.setMinWidth(U.dp(this, 64));
                r.addView(t);
                TextView ti = U.text(this, e.title, 15, U.INK, false);
                ti.setTypeface(U.SERIF); // 0.18 (Evolutiva 008)
                ti.setSingleLine(true);
                ti.setEllipsize(TextUtils.TruncateAt.END);
                r.addView(ti, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                if (e.repeats()) r.addView(U.text(this, "↻", 13, U.MUTED, false));
                list.addView(r);
            }
            for (Tasks.Task k : tasks) if (!k.urgent && !hiddenDone.contains(k.id)) list.addView(taskRow(k));
            tasks.addAll(Tasks.doneOn(this, day));
            for (Tasks.Task k : tasks) if (k.done && !hiddenDone.contains(k.id)) list.addView(taskRow(k));
            if (allEv.isEmpty() && tasks.isEmpty()) {
                TextView n = U.text(this, "Niente in programma", 14, U.MUTED, false);
                n.setPadding(0, 0, 0, U.dp(this, 6));
                list.addView(n);
            }
        }
    }

    // 0.20 (Evolutiva 016): la casella dei task è quella della home (TaskBox + TaskRows): stesso gesto, stessa vibrazione,
    // stessa riga che se ne va; la pagina si rifà solo quando tutte le righe in movimento hanno finito
    private int busyRows;
    private boolean dirty;
    private final java.util.Set<String> hiddenDone = new java.util.HashSet<String>();
    private final TaskRows.Host taskHost = new TaskRows.Host() {
        public void setDone(String id, boolean done) { Tasks.setDone(WeekActivity.this, id, done); }
        public void gone(String id) { hiddenDone.add(id); }
        public void reopened(String id) { hiddenDone.remove(id); }
        public void festa(int kind, View row) {} // la festa è solo della home: «Tutto fatto per oggi» non vale per un giorno della settimana
        public void busy(int delta) {
            busyRows = Math.max(0, busyRows + delta);
            if (busyRows == 0) { dirty = false; render(); }
        }
    };

    /** 0.15: un task con la casella, il collegamento alla sua app (come in home) e «scaduto» in rosso. */
    private View taskRow(final Tasks.Task k) {
        LinearLayout r = U.row(this);
        r.setMinimumHeight(U.dp(this, 40));
        PressB.row(r); // 0.21: riga senza bordo, si scurisce il fondo (la casella ha il suo alone di 56 dp)
        LinearLayout w = U.row(this);
        w.setMinimumWidth(U.dp(this, 30));
        w.setMinimumHeight(U.dp(this, 44));
        w.setGravity(Gravity.CENTER_VERTICAL);
        final TaskBox tbox = new TaskBox(this, k.done);
        w.addView(tbox, new LinearLayout.LayoutParams(U.dp(this, 18), U.dp(this, 18)));
        r.addView(w);
        r.setMinimumHeight(U.dp(this, 44));
        TextView ti = U.text(this, k.title, 15, k.done ? U.BORDER : U.INK, false);
        final TaskRows.BangSpan bang = k.urgent && !k.done ? new TaskRows.BangSpan(U.URGENT) : null;
        if (bang != null) ti.setText(TaskRows.bangTitle(k.title, bang));
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
            public void onClick(View v) { hiddenDone.remove(k.id); Tasks.setDone(WeekActivity.this, k.id, !k.done); render(); }
        };
        // 0.16: come in home: la casella segna fatto, la riga apre (l'app o «Modifica task»)
        if (k.done) {
            r.setOnClickListener(toggle);
            r.setContentDescription(k.title + ", completato. Tocca per riaprirlo");
            TaskRows.addNoteIcon(r, k); // 0.27 (Evolutiva 023)
            return r;
        }
        final TaskRows.State st = new TaskRows.State();
        st.id = k.id; st.title = k.title; st.urgent = k.urgent; st.box = tbox; st.titleView = ti; st.bang = bang; st.row = r;
        r.setTag(U.R.id_task, st);
        w.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { TaskRows.toggle(st, taskHost); }
        });
        w.setContentDescription("Segna fatto: " + k.title);
        w.setMinimumHeight(U.dp(this, 48));
        Press.checkHalo(w, MotionLogic.ALONE_ICONA_DP, 9); // come in home: l'alone è alla casella, 56 dp
        U.widenLeft(r, w, 18);
        if (k.app != null && U.installed(this, k.app)) {
            final String label = U.appLabel(this, k.app);
            TextView open = U.text(this, label + " ›", 13, U.MUTED, false);
            open.setPadding(U.dp(this, 10), 0, 0, 0);
            r.addView(open);
            r.setContentDescription(k.title + ", apri " + label);
            r.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { if (!U.launchPkg(WeekActivity.this, k.app)) U.toast(WeekActivity.this, label + " non si apre. Controlla che sia installata"); }
            });
        } else r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { MainActivity.editTask(WeekActivity.this, k); } });
        TaskRows.addNoteIcon(r, k); // 0.27 (Evolutiva 023): in fondo alla riga, dopo il link all'app
        // 0.16: tocco lungo: Modifica task, ! Urgente, Elimina task (subito, con Annulla)
        r.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                new Menu(WeekActivity.this, k.title)
                        .item("Modifica task", false, new Runnable() { public void run() { MainActivity.editTask(WeekActivity.this, k); } })
                        .toggle(U.bang("Urgente"), "Urgente", k.urgent, new Runnable() { public void run() { Tasks.setUrgent(WeekActivity.this, k.id, !k.urgent); render(); } })
                        .item("Elimina task", true, new Runnable() {
                            public void run() {
                                Changes.track(WeekActivity.this, "Task eliminato: " + k.title, new String[]{"tasks"},
                                        new Runnable() { public void run() { Tasks.delete(WeekActivity.this, k.id); } },
                                        new Runnable() { public void run() { render(); } });
                            }
                        }).show();
                return true;
            }
        });
        return r;
    }
}
