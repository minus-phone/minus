package it.essenziale.launcher;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

/** "Ripeti", come nel calendario di Windows. Restituisce la RRULE nell'extra "rule" (null = mai). */
public class RepeatActivity extends BaseActivity {

    private Rec rec;
    private long start;
    private LinearLayout body;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        start = getIntent().getLongExtra("start", System.currentTimeMillis());
        rec = Rec.parse(getIntent().getStringExtra("rule"), start);

        LinearLayout root = U.col(this);
        root.setBackgroundColor(U.BG);
        root.setPadding(U.dp(this, 20), U.dp(this, 16), U.dp(this, 20), U.dp(this, 16));
        LinearLayout head = U.row(this);
        TextView cancel = U.text(this, "Annulla", 16, U.INK, false);
        cancel.setMinHeight(U.dp(this, 44));
        cancel.setGravity(Gravity.CENTER_VERTICAL);
        cancel.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        head.addView(cancel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView ok = U.text(this, "Fatto", 16, U.INK, true);
        ok.setMinHeight(U.dp(this, 44));
        ok.setGravity(Gravity.CENTER_VERTICAL);
        ok.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent r = new Intent();
                r.putExtra("rule", rec.toRule(start));
                setResult(RESULT_OK, r);
                finish();
            }
        });
        head.addView(ok);
        root.addView(head, U.full());
        TextView t = U.text(this, "Ripeti", 24, U.INK, true);
        t.setPadding(0, U.dp(this, 4), 0, U.dp(this, 4));
        root.addView(t);

        ScrollView sv = new ScrollView(this);
        body = U.col(this);
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        render();
    }

    private void render() {
        body.removeAllViews();
        if (rec.custom != null) {
            TextView n = U.text(this, "Questo impegno ha una ripetizione impostata in un'altra app. Se scegli qui sotto, la sostituisci.", 14, U.MUTED, false);
            n.setPadding(0, U.dp(this, 6), 0, U.dp(this, 6));
            body.addView(n);
        }
        // Frequenza
        LinearLayout f1 = U.row(this), f2 = U.row(this);
        String[] labels = {"Mai", "Ogni giorno", "Ogni settimana", "Ogni mese", "Ogni anno"};
        int[] freqs = {Rec.NONE, Rec.DAILY, Rec.WEEKLY, Rec.MONTHLY, Rec.YEARLY};
        for (int i = 0; i < labels.length; i++) {
            final int fq = freqs[i];
            boolean on = rec.custom == null && rec.freq == fq;
            TextView chip = chip(labels[i], on, new View.OnClickListener() {
                public void onClick(View v) {
                    rec.custom = null;
                    rec.freq = fq;
                    if (fq == Rec.WEEKLY && rec.days == 0) rec.days = 1 << Rec.dow(start);
                    render();
                }
            });
            (i < 3 ? f1 : f2).addView(chip, gap());
        }
        body.addView(f1, U.full());
        LinearLayout.LayoutParams f2p = U.full();
        f2p.topMargin = U.dp(this, 6);
        body.addView(f2, f2p);
        if (rec.custom != null || rec.freq == Rec.NONE) { summary(); return; }

        // Ogni n
        body.addView(U.label(this, "Ogni"));
        LinearLayout every = U.row(this);
        every.addView(round("−", new View.OnClickListener() {
            public void onClick(View v) { if (rec.interval > 1) { rec.interval--; render(); } }
        }), new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 44)));
        TextView n = U.text(this, String.valueOf(rec.interval), 18, U.INK, true);
        n.setGravity(Gravity.CENTER);
        every.addView(n, new LinearLayout.LayoutParams(U.dp(this, 48), U.dp(this, 44)));
        every.addView(round("+", new View.OnClickListener() {
            public void onClick(View v) { if (rec.interval < 99) { rec.interval++; render(); } }
        }), new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 44)));
        TextView unit = U.text(this, Rec.unitLabel(rec.freq, rec.interval), 16, U.INK, false);
        unit.setPadding(U.dp(this, 12), 0, 0, 0);
        every.addView(unit);
        body.addView(every, U.full());

        if (rec.freq == Rec.WEEKLY) {
            body.addView(U.label(this, "Nei giorni"));
            LinearLayout days = U.row(this);
            for (int i = 0; i < 7; i++) {
                final int bit = 1 << i;
                boolean on = (rec.days & bit) != 0;
                TextView d = U.text(this, Rec.SHORT[i], 15, on ? U.BG : U.INK, true);
                d.setGravity(Gravity.CENTER);
                d.setContentDescription(Rec.DAY_NAMES[i] + (on ? ", scelto" : ""));
                d.setBackground(on ? U.box(this, U.INK, 0, 20) : U.box(this, U.BG, U.LINE, 20));
                d.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        int next = rec.days ^ bit;
                        if (next != 0) rec.days = next; // almeno un giorno
                        render();
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, U.dp(this, 40), 1f);
                lp.rightMargin = U.dp(this, 4);
                days.addView(d, lp);
            }
            body.addView(days, U.full());
        }
        if (rec.freq == Rec.MONTHLY) {
            Calendar k = Calendar.getInstance();
            k.setTimeInMillis(start);
            int p = Rec.weekPos(start);
            body.addView(U.label(this, "Quando"));
            body.addView(option("Il giorno " + k.get(Calendar.DAY_OF_MONTH), !rec.byPos, new View.OnClickListener() {
                public void onClick(View v) { rec.byPos = false; render(); }
            }), U.full());
            body.addView(option("Il " + (p == -1 ? "ultimo" : Rec.ORD[p]) + " " + Rec.DAY_NAMES[Rec.dow(start)], rec.byPos, new View.OnClickListener() {
                public void onClick(View v) { rec.byPos = true; render(); }
            }), top());
        }

        // Fine
        body.addView(U.label(this, "Fine"));
        body.addView(option("Mai", rec.end == Rec.END_NEVER, new View.OnClickListener() {
            public void onClick(View v) { rec.end = Rec.END_NEVER; render(); }
        }), U.full());
        String untilLabel = rec.until > 0 ? "Il " + new java.text.SimpleDateFormat("d MMMM yyyy", Locale.ITALY).format(new java.util.Date(rec.until)) : "In una data…";
        body.addView(option(untilLabel, rec.end == Rec.END_UNTIL, new View.OnClickListener() {
            public void onClick(View v) { pickUntil(); }
        }), top());
        LinearLayout cnt = U.row(this);
        TextView after = option("Dopo " + rec.count + (rec.count == 1 ? " volta" : " volte"), rec.end == Rec.END_COUNT, new View.OnClickListener() {
            public void onClick(View v) { rec.end = Rec.END_COUNT; render(); }
        });
        cnt.addView(after, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (rec.end == Rec.END_COUNT) {
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(U.dp(this, 44), U.dp(this, 44));
            bl.leftMargin = U.dp(this, 6);
            cnt.addView(round("−", new View.OnClickListener() {
                public void onClick(View v) { if (rec.count > 1) { rec.count--; render(); } }
            }), bl);
            cnt.addView(round("+", new View.OnClickListener() {
                public void onClick(View v) { if (rec.count < 999) { rec.count++; render(); } }
            }), bl);
        }
        body.addView(cnt, top());
        summary();
    }

    private void summary() {
        TextView s = U.text(this, rec.summary(start), 15, U.INK, true);
        s.setPadding(U.dp(this, 14), U.dp(this, 12), U.dp(this, 14), U.dp(this, 12));
        s.setBackground(U.box(this, U.CHIP, 0, 12));
        LinearLayout.LayoutParams lp = U.full();
        lp.topMargin = U.dp(this, 18);
        body.addView(s, lp);
    }

    private void pickUntil() {
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(rec.until > 0 ? rec.until : start + 30L * 86400000L);
        new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            public void onDateSet(DatePicker v, int y, int m, int d) {
                Calendar s = Calendar.getInstance();
                s.clear();
                s.set(y, m, d);
                rec.until = s.getTimeInMillis();
                rec.end = Rec.END_UNTIL;
                render();
            }
        }, k.get(Calendar.YEAR), k.get(Calendar.MONTH), k.get(Calendar.DAY_OF_MONTH)).show();
    }

    private LinearLayout.LayoutParams gap() {
        LinearLayout.LayoutParams lp = U.wrap();
        lp.rightMargin = U.dp(this, 6);
        return lp;
    }

    private LinearLayout.LayoutParams top() {
        LinearLayout.LayoutParams lp = U.full();
        lp.topMargin = U.dp(this, 6);
        return lp;
    }

    private TextView chip(String s, boolean on, View.OnClickListener l) {
        TextView t = U.text(this, s, 14, on ? U.BG : U.INK, false);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(U.dp(this, 44));
        t.setPadding(U.dp(this, 14), 0, U.dp(this, 14), 0);
        t.setBackground(on ? U.box(this, U.INK, 0, 20) : U.box(this, U.BG, U.LINE, 20));
        t.setOnClickListener(l);
        return t;
    }

    private TextView option(String s, boolean on, View.OnClickListener l) {
        TextView t = U.text(this, s, 15, on ? U.BG : U.INK, false);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinHeight(U.dp(this, 44));
        t.setPadding(U.dp(this, 14), 0, U.dp(this, 14), 0);
        t.setBackground(on ? U.box(this, U.INK, 0, 12) : U.box(this, U.BG, U.LINE, 12));
        t.setOnClickListener(l);
        return t;
    }

    private TextView round(String s, View.OnClickListener l) {
        TextView t = U.text(this, s, 20, U.INK, false);
        t.setGravity(Gravity.CENTER);
        t.setBackground(U.box(this, U.BG, U.INK, 22));
        t.setOnClickListener(l);
        return t;
    }
}
