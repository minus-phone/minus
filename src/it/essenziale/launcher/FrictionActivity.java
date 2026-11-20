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
import android.widget.FrameLayout;
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
    private TextView count, openBtn, homeBtn;
    private RingView ring; // 0.20 (Evolutiva 016): l'anello attorno al conto
    private boolean leaving;
    private View rootView, bodyView, btnsView;
    private int openState = -1;
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
            if (left > 0) { h.postDelayed(this, 1000); ringStep(); } else if (ring != null) ring.set(1f);
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
        left = scope ? 0 : seconds; // 0.16: pausa e scopo senza timer: si apre quando scegli per cosa

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
            // 0.20 (Evolutiva 016): l'anello è 6 dp fuori dal cerchio; i margini negativi lasciano la pagina com'era
            FrameLayout ringWrap = new FrameLayout(this);
            ringWrap.addView(count, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
            ring = new RingView(this);
            ringWrap.addView(ring, new FrameLayout.LayoutParams(size + U.dp(this, 12), size + U.dp(this, 12), Gravity.CENTER));
            LinearLayout.LayoutParams rwlp = new LinearLayout.LayoutParams(size + U.dp(this, 12), size + U.dp(this, 12));
            rwlp.topMargin = -U.dp(this, 6);
            rwlp.bottomMargin = -U.dp(this, 6);
            body.addView(ringWrap, rwlp);
            TextView q = U.text(this, "Un respiro. Ti serve adesso?", 18, U.ON_DARK, false);
            q.setGravity(Gravity.CENTER);
            q.setPadding(0, U.dp(this, 30), 0, 0);
            body.addView(q, U.full());
        } else {
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
            own.setHint("Scrivilo: «messaggio a Giulia»");
            own.setContentDescription("Per cosa ti serve? Scrivilo");
            own.setHintTextColor(0xFF6F6E69);
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
                    purpose = s.toString().trim().length() > 0 ? s.toString().trim() : null;
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
        homeBtn = home;
        home.setGravity(Gravity.CENTER);
        home.setMinHeight(U.dp(this, 52));
        // 0.20 (Evolutiva 016): premuto cambia solo lo sfondo (il testo non si muove); poi lo scatto e la pausa si schiarisce
        if (scope) Press.outlined(home, U.DARK, U.ON_DARK, 26).dark(true); else Press.primary(home, U.ON_DARK, MotionLogic.mix(U.ON_DARK, U.DARK, 0.12f), 26).dark(true);
        home.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { notNow(true); } });
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
        rootView = root;
        bodyView = sv;
        btnsView = btns;
        setContentView(root);
        render();
    }

    /** La scheda «una cosa adesso»; false se oggi non c'è niente da proporre. */
    private boolean renderCosa() {
        cosaBox.removeAllViews();
        View card = Adesso.card(this, true, new Runnable() {
            public void run() { notNow(false); }
        }, new Runnable() {
            public void run() { renderCosa(); }
        });
        if (card == null) return false;
        LinearLayout.LayoutParams lp = U.full();
        lp.bottomMargin = U.dp(this, 18);
        cosaBox.addView(card, lp);
        return true;
    }

    private java.util.List<String> past;

    /** Le motivazioni scritte prima, dalla più recente (registro di Scope: ora|app|scopo|minuti|esito). */
    private java.util.List<String> pastPurposes() {
        if (past != null) return past;
        past = new java.util.ArrayList<String>();
        String[] lines = U.prefs(this).getString(Scope.K_LOG, "").split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String[] f = lines[i].split("\\|", -1);
            if (f.length >= 3 && f[2].trim().length() > 0) past.add(f[2].trim());
        }
        return past;
    }

    private static String[] concat(String[] a, String[] b) {
        String[] o = new String[a.length + b.length];
        System.arraycopy(a, 0, o, 0, a.length);
        System.arraycopy(b, 0, o, a.length, b.length);
        return o;
    }

    private boolean isPreset(String p) {
        for (String s : night ? Scopo.NIGHT_PURPOSES : Scopo.DAY_PURPOSES) if (s.equals(p)) return true;
        return false;
    }

    private void renderChips() {
        if (purposeBox == null) return;
        purposeBox.removeAllViews();
        // 0.15: solo le motivazioni che hai già scritto tu, dopo 2 lettere
        String typed = own == null ? "" : own.getText().toString();
        java.util.List<String> ps = Scopo.suggest(pastPurposes(), typed, concat(Scopo.DAY_PURPOSES, Scopo.NIGHT_PURPOSES));
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
            row.addView(chip(p, false, new Runnable() {
                public void run() {
                    if (own != null) { own.setText(p); own.setSelection(own.getText().length()); }
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
        if (on) Press.primary(t, U.ON_DARK, MotionLogic.mix(U.ON_DARK, U.DARK, 0.12f), 20).dark(true);
        else Press.box(t, U.DARK, 0xFF62625C, 20, MotionLogic.mix(U.DARK, U.ON_DARK, 0.10f)).ring(20, MotionLogic.ALONE_PICCOLO_DP).dark(true);
        t.setContentDescription(s + (on ? ", scelto" : ""));
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Motion.scatto(v); r.run(); } });
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
        left = scope ? 0 : seconds; // 0.16: pausa e scopo senza timer: si apre quando scegli per cosa
        render();
        h.postDelayed(tick, 1000);
        if (ring != null) { ring.set(MotionLogic.ring(seconds - left, seconds)); ringStep(); }
    }

    /** L'anello cresce di un passo nel secondo che sta per passare (in sincrono con il conto). */
    private void ringStep() {
        if (ring == null || left <= 0) return;
        ring.to(MotionLogic.ring(seconds - left + 1, seconds), 1000);
    }

    @Override
    protected void onPause() {
        h.removeCallbacks(tick);
        if (ring != null) ring.stop();
        super.onPause();
        Pill.pausedActivity(this);
    }

    @Override
    public void onBackPressed() { notNow(false); }

    /**
     * «Non ora»: lo scatto e il check (solo al tocco), poi la pausa si schiarisce verso lo sfondo della home in LENTO mentre la
     * home arriva morbida. Con «Rimuovi animazioni», un taglio rapido.
     */
    private void notNow(boolean tap) {
        if (leaving) return;
        leaving = true;
        h.removeCallbacks(tick);
        if (ring != null) ring.stop();
        if (tap) { // 0.21: il tasto resta premuto e la label lascia il posto a un check che si disegna; poi la pausa si schiarisce
            Motion.scatto(homeBtn);
            if (!scope && homeBtn.getBackground() instanceof Press.Fade) ((Press.Fade) homeBtn.getBackground()).hold(); // la pillola piena resta premuta (.pill.pressed)
            Tick.play(homeBtn);
        }
        if (!demo) { Scope.notNow(this, app.pkg); Beta.add(this, Registro.PAUSE_NOT_NOW); Checks.add(this, Registro.PAUSE_NOT_NOW); }
        Motion.say(rootView, "Pausa chiusa, sei in home");
        h.postDelayed(new Runnable() { public void run() { goHome(); } }, tap ? MotionLogic.tickAttesa(Motion.reduced(this)) : 0); // il tempo di vedere il check (mezzo RAPIDO + BASE + 250 ms)
    }

    private void goHome() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        U.start(this, i);
        Motion.softHome(this);
        if (!Motion.reduced(this)) {
            final int to = U.BG;
            final android.view.Window w = getWindow();
            bodyView.animate().alpha(0f).setDuration(Motion.LENTO).setInterpolator(Motion.DECELERA).start();
            btnsView.animate().alpha(0f).setDuration(Motion.LENTO).setInterpolator(Motion.DECELERA).start();
            Motion.color(Motion.LENTO, U.DARK, to, new Motion.ColorSink() {
                public void set(int c) {
                    rootView.setBackgroundColor(c);
                    w.setStatusBarColor(c);
                    w.setNavigationBarColor(c);
                }
            });
        }
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
        Motion.quickCut(this); // «Apri»: un taglio rapido e neutro (RAPIDO), senza vibrazione
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
        int st = ok ? (scope ? 1 : 2) : 0;
        if (st != openState) { // lo sfondo si rifà solo quando cambia stato: lo stato premuto non si interrompe a ogni secondo
            openState = st;
            if (st == 1) Press.primary(openBtn, U.ON_DARK, MotionLogic.mix(U.ON_DARK, U.DARK, 0.12f), 26).dark(true);
            else if (st == 2) Press.outlined(openBtn, U.DARK, U.ON_DARK, 26).dark(true);
            else openBtn.setBackground(U.box(this, U.DARK, 0xFF5F5F5A, 26));
        }
        openBtn.setEnabled(ok);
    }
}
