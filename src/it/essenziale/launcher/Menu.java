package it.essenziale.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.15: la finestra di sistema del tocco lungo (task e impegni): voci semplici, un interruttore
 * («! Urgente») e la voce che elimina, in rosso. Colori fissi: la finestra è sempre quella chiara di Android.
 */
final class Menu {
    static final int INK = Color.parseColor("#1B1B1F"), RED = Color.parseColor("#B3261E"),
            TRACK_OFF = Color.parseColor("#8A877F"), TRACK_ON = Color.parseColor("#1B1B1F"), KNOB = Color.WHITE;

    final Activity a;
    final String title;
    final List<View> rows = new ArrayList<View>();
    final List<Runnable> actions = new ArrayList<Runnable>();
    AlertDialog dialog;

    Menu(Activity a, String title) { this.a = a; this.title = title; }

    Menu item(CharSequence label, boolean danger, Runnable r) {
        TextView t = U.text(a, "", 16, danger ? RED : INK, false);
        t.setText(label);
        t.setMinHeight(U.dp(a, 48));
        t.setGravity(Gravity.CENTER_VERTICAL);
        add(t, r);
        return this;
    }

    Menu toggle(CharSequence label, String spoken, boolean on, Runnable r) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(U.dp(a, 48));
        TextView t = U.text(a, "", 16, INK, false);
        t.setText(label);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout sw = new LinearLayout(a);
        sw.setGravity((on ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL);
        sw.setPadding(U.dp(a, 3), U.dp(a, 3), U.dp(a, 3), U.dp(a, 3));
        sw.setBackground(U.box(a, on ? TRACK_ON : TRACK_OFF, 0, 14));
        View knob = new View(a);
        knob.setBackground(U.box(a, KNOB, 0, 10));
        sw.addView(knob, new LinearLayout.LayoutParams(U.dp(a, 20), U.dp(a, 20)));
        row.addView(sw, new LinearLayout.LayoutParams(U.dp(a, 48), U.dp(a, 28)));
        row.setContentDescription(spoken + ", interruttore, " + (on ? "acceso" : "spento"));
        add(row, r);
        return this;
    }

    private void add(View v, final Runnable r) {
        v.setOnClickListener(new View.OnClickListener() {
            public void onClick(View x) { if (dialog != null) dialog.dismiss(); if (r != null) r.run(); }
        });
        rows.add(v);
    }

    void show() {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(U.dp(a, 24), U.dp(a, 4), U.dp(a, 24), U.dp(a, 4));
        for (View v : rows) c.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog = new AlertDialog.Builder(a).setTitle(title).setView(c).setNegativeButton("Annulla", null).show();
    }

    /** 0.15: tocco lungo su un impegno (home e Settimana): Modifica impegno, Elimina impegno in rosso. */
    static void event(final Activity a, final U.Event e) {
        if (e.eventId < 0) return;
        new Menu(a, e.title)
                .item("Modifica impegno", false, new Runnable() { public void run() { U.openEvent(a, e); } })
                .item("Elimina impegno", true, new Runnable() {
                    public void run() {
                        if (!e.editable()) { U.openEvent(a, e); return; }
                        if (!U.canWriteCalendar(a)) { U.openEvent(a, e); return; }
                        a.startActivity(U.editIntent(a, e).putExtra("ask_delete", true));
                    }
                }).show();
    }
}
