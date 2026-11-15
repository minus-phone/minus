package it.essenziale.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;

/**
 * 0.16: Ripeti › Personalizza…, una finestra di sistema al posto della pagina: ogni [n] [giorni,
 * settimane, mesi, anni], i giorni della settimana (solo con le settimane), la fine (mai, il giorno,
 * dopo n volte) e una riga di riepilogo. In modifica parte dalla regola della serie.
 */
final class RepeatDialog {
    interface Done { void done(Rec r); }

    private final Activity a;
    private final long start;
    private final Rec r;
    private final Done done;
    private LinearLayout body;
    private TextView summary;

    private RepeatDialog(Activity a, Rec cur, long start, Done done) {
        this.a = a;
        this.start = start;
        this.done = done;
        r = cur.custom != null || cur.freq == Rec.NONE ? new Rec() : cur.copy();
        if (r.freq == Rec.NONE) r.freq = Rec.WEEKLY;
        if (r.freq == Rec.WEEKLY && r.days == 0) r.days = 1 << Rec.dow(start);
    }

    static void show(Activity a, Rec cur, long start, Done done) { new RepeatDialog(a, cur, start, done).open(); }

    private void open() {
        body = U.col(a);
        body.setPadding(U.dp(a, 24), U.dp(a, 4), U.dp(a, 24), 0);
        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.addView(body);
        render();
        U.show(U.dlg(a).setTitle("Ripeti").setView(sv)
                .setPositiveButton("Fatto", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { done.done(r); }
                })
                .setNegativeButton("Annulla", null));
    }

    private void render() {
        body.removeAllViews();
        body.addView(label("Ogni"));
        LinearLayout n = U.row(a);
        n.addView(step("−", new Runnable() { public void run() { if (r.interval > 1) r.interval--; render(); } }));
        TextView v = U.text(a, String.valueOf(r.interval), 18, U.INK, true);
        v.setGravity(Gravity.CENTER);
        v.setMinWidth(U.dp(a, 40));
        n.addView(v);
        n.addView(step("+", new Runnable() { public void run() { if (r.interval < 99) r.interval++; render(); } }));
        body.addView(n);
        LinearLayout units = U.row(a);
        int[] fs = {Rec.DAILY, Rec.WEEKLY, Rec.MONTHLY, Rec.YEARLY};
        for (final int f : fs) units.addView(chip(Rec.unitLabel(f, r.interval), r.freq == f, new Runnable() {
            public void run() {
                r.freq = f;
                if (f == Rec.WEEKLY && r.days == 0) r.days = 1 << Rec.dow(start);
                render();
            }
        }));
        body.addView(units);
        if (r.freq == Rec.WEEKLY) {
            body.addView(label("Giorni"));
            LinearLayout days = U.row(a);
            for (int i = 0; i < 7; i++) {
                final int bit = 1 << i;
                TextView d = chip(Rec.SHORT[i], (r.days & bit) != 0, new Runnable() {
                    public void run() { int nd = r.days ^ bit; if (nd != 0) r.days = nd; render(); }
                });
                d.setContentDescription(Rec.DAY_NAMES[i] + ((r.days & bit) != 0 ? ", scelto" : ""));
                days.addView(d);
            }
            body.addView(days);
        }
        body.addView(label("Fine"));
        body.addView(choice("Mai", r.end == Rec.END_NEVER, new Runnable() { public void run() { r.end = Rec.END_NEVER; render(); } }));
        String until = r.until > 0 ? new java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.ITALY).format(new java.util.Date(r.until)) : "scegli la data";
        body.addView(choice("Il " + until, r.end == Rec.END_UNTIL, new Runnable() { public void run() { pickUntil(); } }));
        body.addView(choice("Dopo " + r.count + " volte", r.end == Rec.END_COUNT, new Runnable() {
            public void run() {
                if (r.end == Rec.END_COUNT) r.count = r.count >= 30 ? 2 : r.count + 1; // un tocco in più aumenta
                r.end = Rec.END_COUNT;
                render();
            }
        }));
        summary = U.text(a, r.summary(start), 14, U.MUTED, false);
        summary.setPadding(0, U.dp(a, 12), 0, U.dp(a, 4));
        body.addView(summary);
    }

    private void pickUntil() {
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(r.until > 0 ? r.until : start + 30L * 86400000L);
        new DatePickerDialog(a, new DatePickerDialog.OnDateSetListener() {
            public void onDateSet(DatePicker v, int y, int m, int d) {
                Calendar s = Calendar.getInstance();
                s.clear();
                s.set(y, m, d);
                r.until = s.getTimeInMillis();
                r.end = Rec.END_UNTIL;
                render();
            }
        }, k.get(Calendar.YEAR), k.get(Calendar.MONTH), k.get(Calendar.DAY_OF_MONTH)).show();
    }

    private TextView label(String s) {
        TextView t = U.label(a, s);
        return t;
    }

    private TextView step(String s, final Runnable r) {
        TextView t = U.text(a, s, 20, U.INK, false);
        t.setGravity(Gravity.CENTER);
        t.setBackground(U.box(a, U.SURFACE, U.BORDER, 22));
        t.setLayoutParams(new LinearLayout.LayoutParams(U.dp(a, 44), U.dp(a, 44)));
        t.setContentDescription("−".equals(s) ? "Uno in meno" : "Uno in più");
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        return t;
    }

    private TextView chip(String s, boolean on, final Runnable r) {
        TextView t = U.text(a, s, 14, on ? U.BG : U.INK, false);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(U.dp(a, 44));
        t.setMinWidth(U.dp(a, 40));
        t.setPadding(U.dp(a, 10), 0, U.dp(a, 10), 0);
        t.setBackground(on ? U.box(a, U.INK, 0, 21) : U.box(a, U.SURFACE, U.BORDER, 21));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = U.dp(a, 4);
        lp.topMargin = U.dp(a, 6);
        t.setLayoutParams(lp);
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        return t;
    }

    private View choice(String s, boolean on, final Runnable r) {
        LinearLayout row = U.row(a);
        row.setMinimumHeight(U.dp(a, 48));
        TextView dot = U.text(a, on ? "●" : "○", 18, U.INK, false);
        dot.setPadding(0, 0, U.dp(a, 12), 0);
        row.addView(dot);
        row.addView(U.text(a, s, 16, U.INK, on));
        row.setContentDescription(s + (on ? ", scelto" : ""));
        row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        return row;
    }
}
