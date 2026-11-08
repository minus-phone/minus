package it.essenziale.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
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

import java.util.Calendar;

/**
 * La pausa prima di aprire un'app. Due livelli (0.10, wireframe 141 e 153):
 * - pausa: 8 secondi (o 4, 6: Impostazioni › Tempo e uso) e «Ti serve davvero adesso?»;
 * - pausa e scopo: 4 secondi di respiro, «Per cosa ti serve?» e «Per quanto?».
 * «Non ora» pesa quanto «Apri»: è la parte che funziona di più (one sec, PNAS 2023).
 * Di notte le app con la pausa chiedono lo scopo, con tempi brevi e la tua alternativa.
 */
public class FrictionActivity extends Activity {

    static final int SCOPE_SECONDS = 4;

    private int seconds = 8, left = 8;
    private LinearLayout cosaBox;
    private TextView count, openBtn;
    private U.AppInfo app;
    private boolean demo, scope, night, outside;
    private String purpose = null;
    private int minutes;
    private LinearLayout purposeBox, minutesBox;
    private EditText own;
    private final Handler h = new Handler();
    private final Runnable tick = new Runnable() {
        public void run() {
            left--;
            render();
            if (left > 0) h.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(U.DARK);
        getWindow().setNavigationBarColor(U.DARK);
        Intent in = getIntent();
        demo = in.getStringExtra("pkg") == null;
        outside = in.getBooleanExtra("outside", false);
        app = new U.AppInfo();
        app.pkg = in.getStringExtra("pkg");
        app.cls = in.getStringExtra("cls");
        app.label = in.getStringExtra("label") != null ? in.getStringExtra("label") : "Instagram";
        night = Night.active(this);
        scope = demo ? in.getBooleanExtra("scope", true) : Scope.level(this, app.pkg) == Scopo.SCOPE;
        int[] mins = night ? Scopo.NIGHT_MIN : Scopo.DAY_MIN;
        minutes = mins.length > 1 ? mins[night ? 0 : 1] : mins[0];
        seconds = U.pauseSeconds(this); // 0.12: 8 secondi, oppure 4 o 6
        left = scope ? SCOPE_SECONDS : seconds;

        LinearLayout root = U.col(this);
        root.setBackgroundColor(U.DARK);
        root.setPadding(U.dp(this, 24), U.dp(this, scope ? 28 : 60), U.dp(this, 24), U.dp(this, 20));
        ScrollView sv = new ScrollView(this);
        LinearLayout body = U.col(this);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        count = U.text(this, "", scope ? 24 : 40, U.ON_DARK, false);
        count.setTypeface(U.SERIF);
        count.setGravity(Gravity.CENTER);
        count.setBackground(U.box(this, U.DARK, 0xFF62625C, 60));
        int size = U.dp(this, scope ? 64 : 120);
        if (!scope) {
            body.addView(U.text(this, demo ? "Anteprima · stai aprendo" : "Stai aprendo", 15, U.ON_DARK_MUTED, false));
            TextView name = U.text(this, app.label, 28, U.ON_DARK, true);
            name.setPadding(0, U.dp(this, 6), 0, U.dp(this, 30));
            body.addView(name);
            body.addView(count, new LinearLayout.LayoutParams(size, size));
            TextView q = U.text(this, "Un respiro. Ti serve davvero adesso?", 18, U.ON_DARK, false);
            q.setGravity(Gravity.CENTER);
            q.setPadding(0, U.dp(this, 30), 0, 0);
            body.addView(q, U.full());
        } else {
            body.addView(count, new LinearLayout.LayoutParams(size, size));
            String when = night ? String.format(java.util.Locale.ITALY, "%d:%02d", Calendar.getInstance().get(Calendar.HOUR_OF_DAY), Calendar.getInstance().get(Calendar.MINUTE)) : null;
            TextView sub = U.text(this, (demo ? "Anteprima · " : "") + "Stai aprendo " + app.label, 13, U.ON_DARK_MUTED, false);
            sub.setPadding(0, U.dp(this, 8), 0, U.dp(this, 14));
            body.addView(sub);
            LinearLayout left = U.col(this);
            body.addView(left, U.full());
            // 0.12 (172): prima dello scopo, una cosa che puoi fare adesso. Se la fai, l'app non serve
            boolean cosa = false;
            if (!night) {
                cosaBox = U.col(this);
                left.addView(cosaBox, U.full());
                cosa = renderCosa();
            }
            TextView q = U.text(this, night ? "Sono le " + when + ". Per cosa apri " + app.label + "?" : cosa ? "Se apri, per cosa ti serve?" : "Per cosa ti serve?", cosa ? 18 : 22, U.ON_DARK, true);
            left.addView(q);
            purposeBox = U.col(this);
            left.addView(purposeBox, U.full());
            own = new EditText(this);
            own.setHint("Oppure scrivilo: «messaggio a Giulia»");
            own.setHintTextColor(U.ON_DARK_MUTED);
            own.setTextColor(U.ON_DARK);
            own.setTextSize(15);
            own.setSingleLine(true);
            own.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            own.setBackground(U.box(this, U.DARK, 0xFF62625C, 20));
            own.setPadding(U.dp(this, 14), U.dp(this, 10), U.dp(this, 14), U.dp(this, 10));
            own.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                public void onTextChanged(CharSequence s, int a, int b, int c) {}
                public void afterTextChanged(Editable s) {
                    if (s.toString().trim().length() > 0) purpose = s.toString().trim();
                    else if (purpose != null && !isPreset(purpose)) purpose = null;
                    renderChips();
                    render();
                }
            });
            LinearLayout.LayoutParams olp = U.full();
            olp.topMargin = U.dp(this, 8);
            left.addView(own, olp);
            TextView hq = U.text(this, "Per quanto?", 17, U.ON_DARK, true);
            hq.setPadding(0, U.dp(this, 16), 0, U.dp(this, 6));
            left.addView(hq);
            minutesBox = U.row(this);
            left.addView(minutesBox, U.full());
            if (night) {
                TextView al = U.text(this, "OPPURE, COME AVEVI SCELTO", 12, U.ON_DARK_MUTED, false);
                al.setLetterSpacing(0.06f);
                al.setPadding(0, U.dp(this, 18), 0, U.dp(this, 6));
                left.addView(al);
                TextView alt = U.text(this, Night.alternative(this), 16, U.ON_DARK, false);
                alt.setMinHeight(U.dp(this, 54));
                alt.setGravity(Gravity.CENTER_VERTICAL);
                alt.setPadding(U.dp(this, 16), 0, U.dp(this, 16), 0);
                alt.setBackground(U.box(this, U.DARK, U.ON_DARK, 14));
                alt.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Night.count(FrictionActivity.this, Night.K_ALTS, 1);
                        if (!demo) { Scope.notNow(FrictionActivity.this, app.pkg); Beta.add(FrictionActivity.this, Registro.PAUSE_NOT_NOW); Checks.add(FrictionActivity.this, Registro.PAUSE_NOT_NOW); }
                        startActivity(new Intent(FrictionActivity.this, DimActivity.class));
                        finish();
                    }
                });
                left.addView(alt, U.full());
            }
            renderChips();
        }

        // In fondo: «Non ora» e «Apri» con lo stesso peso
        LinearLayout btns = scope ? U.row(this) : U.col(this);
        TextView home = U.text(this, scope ? "Non ora" : "Non ora, torna alla home", 16, scope ? U.ON_DARK : U.DARK, true);
        home.setGravity(Gravity.CENTER);
        home.setMinHeight(U.dp(this, 52));
        home.setBackground(scope ? U.box(this, U.DARK, U.ON_DARK, 26) : U.box(this, U.ON_DARK, 0, 26));
        home.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { notNow(); } });
        openBtn = U.text(this, "", 15, U.ON_DARK_MUTED, true);
        openBtn.setGravity(Gravity.CENTER);
        openBtn.setMinHeight(U.dp(this, 52));
        openBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { open(); } });
        if (scope) {
            LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            a.rightMargin = U.dp(this, 10);
            btns.addView(home, a);
            btns.addView(openBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        } else {
            btns.addView(home, U.full());
            LinearLayout.LayoutParams olp = U.full();
            olp.topMargin = U.dp(this, 10);
            btns.addView(openBtn, olp);
        }
        LinearLayout.LayoutParams blp = U.full();
        blp.topMargin = U.dp(this, 12);
        root.addView(btns, blp);
        setContentView(root);
        render();
    }

    /** La scheda «una cosa adesso»; false se oggi non c'è niente da proporre. */
    private boolean renderCosa() {
        cosaBox.removeAllViews();
        View card = Adesso.card(this, true, new Runnable() {
            public void run() { notNow(); }
        }, new Runnable() {
            public void run() { renderCosa(); }
        });
        if (card == null) return false;
        LinearLayout.LayoutParams lp = U.full();
        lp.bottomMargin = U.dp(this, 18);
        cosaBox.addView(card, lp);
        return true;
    }

    private boolean isPreset(String p) {
        for (String s : night ? Scopo.NIGHT_PURPOSES : Scopo.DAY_PURPOSES) if (s.equals(p)) return true;
        return false;
    }

    private void renderChips() {
        if (purposeBox == null) return;
        purposeBox.removeAllViews();
        String[] ps = night ? Scopo.NIGHT_PURPOSES : Scopo.DAY_PURPOSES;
        LinearLayout row = null;
        int used = 0;
        for (final String p : ps) {
            if (row == null || used + p.length() > 30) {
                row = U.row(this);
                LinearLayout.LayoutParams lp = U.full();
                lp.topMargin = U.dp(this, 8);
                purposeBox.addView(row, lp);
                used = 0;
            }
            used += p.length();
            row.addView(chip(p, p.equals(purpose), new Runnable() {
                public void run() {
                    purpose = p;
                    if (own != null && own.getText().length() > 0) own.setText("");
                    purpose = p;
                    renderChips();
                    render();
                }
            }));
        }
        minutesBox.removeAllViews();
        for (final int m : night ? Scopo.NIGHT_MIN : Scopo.DAY_MIN) {
            minutesBox.addView(chip(m + " min", m == minutes, new Runnable() {
                public void run() { minutes = m; renderChips(); render(); }
            }));
        }
    }

    private TextView chip(String s, boolean on, final Runnable r) {
        TextView t = U.text(this, s, 13, on ? U.DARK : U.ON_DARK, false);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(U.dp(this, 44));
        t.setPadding(U.dp(this, 12), 0, U.dp(this, 12), 0);
        t.setBackground(on ? U.box(this, U.ON_DARK, 0, 20) : U.box(this, U.DARK, 0xFF62625C, 20));
        t.setContentDescription(s + (on ? ", scelto" : ""));
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        LinearLayout.LayoutParams lp = U.wrap();
        lp.rightMargin = U.dp(this, 6);
        t.setLayoutParams(lp);
        return t;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Pill.resumed(this);
        // Ogni volta che torno qui la pausa riparte: è il senso dell'attrito
        h.removeCallbacks(tick);
        left = scope ? SCOPE_SECONDS : seconds;
        render();
        h.postDelayed(tick, 1000);
    }

    @Override
    protected void onPause() {
        h.removeCallbacks(tick);
        super.onPause();
        Pill.pausedActivity(this);
    }

    @Override
    public void onBackPressed() { notNow(); }

    private void notNow() {
        if (!demo) { Scope.notNow(this, app.pkg); Beta.add(this, Registro.PAUSE_NOT_NOW); Checks.add(this, Registro.PAUSE_NOT_NOW); }
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        U.start(this, i);
        finish();
    }

    private void open() {
        if (!ready()) return;
        if (demo) { U.toast(this, "Qui si aprirebbe l'app"); finish(); return; }
        if (scope) Scope.start(this, app.pkg, app.cls, app.label, purpose, minutes);
        else Scope.passed(this, app.pkg);
        Beta.add(this, Registro.PAUSE_OPEN);
        if (scope) Beta.add(this, Registro.SCOPE);
        if (app.cls != null) U.launch(this, app); else U.launchPkg(this, app.pkg);
        finish();
    }

    private boolean ready() {
        return left <= 0 && (!scope || (purpose != null && purpose.length() > 0));
    }

    private void render() {
        boolean timeOk = left <= 0, ok = ready();
        count.setText(timeOk ? "✓" : String.valueOf(left));
        if (scope) openBtn.setText(!timeOk ? "Apri tra " + left + " s" : purpose == null ? "Scegli per cosa" : "Apri · " + minutes + " min");
        else openBtn.setText(ok ? "Apri " + app.label : "Apri tra " + left + " s");
        openBtn.setTextColor(ok ? (scope ? U.DARK : U.ON_DARK) : 0xFF8A8A84);
        openBtn.setBackground(ok && scope ? U.box(this, U.ON_DARK, 0, 26) : U.box(this, U.DARK, ok ? U.ON_DARK : 0xFF5F5F5A, 26));
        openBtn.setEnabled(ok);
    }
}
