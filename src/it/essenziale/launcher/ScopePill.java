package it.essenziale.launcher;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 0.15 · La pillola dello scopo sopra le altre app (wireframe 204–210). Con il permesso «Mostra sopra
 * altre app»: chiusa di default (204), si apre al tocco per 5 secondi (205), al preavviso si apre da
 * sola con un tocco aptico (206), allo scadere un foglio dal basso (207, poi 208), dalla terza volta
 * resta aperta con i minuti in più (209). Senza permesso restano gli avvisi di Android (210), in Scope.
 * Mai sopra −minus: si nasconde quando −minus è in primo piano. Le scelte e i testi sono in Scopo.
 */
final class ScopePill {
    private ScopePill() {}

    private static final Handler H = new Handler(Looper.getMainLooper());
    private static Context app;
    private static LinearLayout pill;      // la pillola, chiusa o aperta
    private static View sheet;             // il foglio allo scadere
    private static boolean wide;           // la finestra della pillola è larga (aperta)
    private static long openUntil = 0;     // aperta da sola fino a…
    private static String[] warnText;      // preavviso in corso
    private static long collapsedAt = 0;   // dalla terza volta: chiusa a mano alle…
    private static long lastBuzz = 0;

    private static final Runnable REFRESH = new Runnable() {
        public void run() { if (app != null) Pill.update(app); }
    };

    static boolean usable(Context c) { return Pill.canDraw(c); }

    // ---------- Dati dello scopo ----------

    private static long msLeft(Context c) {
        return U.prefs(c).getLong(Scope.K_END, 0) - System.currentTimeMillis();
    }

    private static long totalMs(Context c) {
        android.content.SharedPreferences p = U.prefs(c);
        return p.getLong(Scope.K_END, 0) - p.getLong(Scope.K_START, 0);
    }

    /** Dalla terza volta (dopo due proroghe e di nuovo scaduto) la pillola resta aperta. */
    private static boolean overtime(Context c) {
        return U.prefs(c).getInt(Scope.K_EXT, 0) >= 2 && msLeft(c) <= 0;
    }

    // ---------- Eventi da Scope ----------

    /** Con TalkBack 5 secondi non bastano e il foglio non ferma i gesti: meglio l'avviso di Android. */
    private static boolean talkBack(Context c) {
        try {
            android.view.accessibility.AccessibilityManager am = (android.view.accessibility.AccessibilityManager) c.getSystemService(Context.ACCESSIBILITY_SERVICE);
            return am != null && am.isEnabled() && am.isTouchExplorationEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Il foglio va solo sopra l'app dello scopo: mai sopra una chiamata, la navigazione o un'altra app.
     * Senza dati di utilizzo non si sa quale app è aperta: allora si mostra, come prima.
     */
    private static boolean overScopeApp(Context c) {
        if (Pill.isPaused()) return false;
        if (!Pill.usageGranted(c)) return true;
        try {
            String fg = Scope.foreground(c);
            return fg == null || fg.equals(U.prefs(c).getString(Scope.K_PKG, ""));
        } catch (Exception e) {
            return true;
        }
    }

    /** 206: il preavviso apre la pillola per 5 secondi, con un solo tocco aptico breve. false = usa l'avviso di Android. */
    static boolean warn(Context c, String[] text) {
        if (talkBack(c) || Pill.isPaused()) return false;
        app = c.getApplicationContext();
        warnText = text;
        openUntil = System.currentTimeMillis() + Scopo.OPEN_MS;
        buzz(c, new long[]{0, 40});
        Pill.update(app);
        later(Scopo.OPEN_MS);
        return true;
    }

    /** 207 e 208: allo scadere un foglio dal basso, con due tocchi aptici. false = usa l'avviso di Android. */
    static boolean expired(Context c, String[] t) {
        if (talkBack(c) || !overScopeApp(c)) return false;
        app = c.getApplicationContext();
        buzz(c, new long[]{0, 60, 120, 60});
        showSheet(app, t);
        return sheet != null;
    }

    /** Ogni 15 secondi da Scope: dalla terza volta si riapre dopo un minuto e vibra ogni 5. */
    static void tick(Context c) {
        if (!overtime(c)) return;
        long now = System.currentTimeMillis();
        if (lastBuzz == 0) lastBuzz = now; // il primo tocco è quello dello scadere
        if (Scopo.buzz(lastBuzz, now)) { lastBuzz = now; buzz(c, new long[]{0, 40}); }
    }

    // ---------- La pillola ----------

    /** Da Pill.update, quando c'è uno scopo e −minus non è in primo piano. */
    static void show(Context c) {
        app = c.getApplicationContext();
        long now = System.currentTimeMillis();
        if (now > openUntil) warnText = null;
        boolean open = now < openUntil || (overtime(c) && Scopo.reopen(collapsedAt, now));
        try {
            WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            if (pill != null && wide != open) { try { wm.removeView(pill); } catch (Exception ignored) {} pill = null; }
            if (pill == null) {
                wide = open;
                pill = new LinearLayout(app);
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        open ? app.getResources().getDisplayMetrics().widthPixels - U.dp(app, 48) : WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT, type(),
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                lp.y = U.dp(app, 34);
                wm.addView(pill, lp);
            }
            fill(open);
        } catch (Exception e) {
            pill = null;
        }
    }

    private static void fill(boolean open) {
        Context a = app;
        long left = msLeft(a), total = totalMs(a);
        String label = U.prefs(a).getString(Scope.K_LABEL, ""), purpose = U.prefs(a).getString(Scope.K_PURPOSE, "");
        pill.removeAllViews();
        pill.setBackground(U.box(a, U.INK, 0, open ? 18 : 14));
        pill.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { toggle(); }
        });
        float ring = Scopo.ring(left, total);
        if (!open) {
            pill.setOrientation(LinearLayout.HORIZONTAL);
            pill.setGravity(Gravity.CENTER_VERTICAL);
            pill.setPadding(U.dp(a, 7), 0, U.dp(a, 10), 0);
            pill.setMinimumHeight(U.dp(a, 28));
            pill.addView(new Ring(a, ring), new LinearLayout.LayoutParams(U.dp(a, 16), U.dp(a, 16)));
            TextView t = U.text(a, Scopo.closed(left), 13, U.BG, true);
            t.setPadding(U.dp(a, 6), 0, 0, 0);
            pill.addView(t);
            pill.setContentDescription(label + ", " + Scopo.openTitle(label, left) + ". Tocca per aprire.");
            return;
        }
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setPadding(U.dp(a, 14), U.dp(a, 12), U.dp(a, 14), U.dp(a, 12));
        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(new Ring(a, ring), new LinearLayout.LayoutParams(U.dp(a, 18), U.dp(a, 18)));
        String title = warnText != null ? warnText[0] : Scopo.openTitle(label, left);
        TextView tt = U.text(a, title, 15, U.BG, true);
        tt.setPadding(U.dp(a, 8), 0, 0, 0);
        top.addView(tt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView up = U.text(a, "▴", 13, U.BG, false);
        up.setAlpha(0.7f);
        up.setContentDescription("Chiudi la pillola");
        top.addView(up);
        pill.addView(top);
        TextView line = U.text(a, warnText != null ? warnText[1] : Scopo.openLine(purpose, left), 13, U.BG, false);
        line.setAlpha(0.85f);
        line.setPadding(0, U.dp(a, 6), 0, 0);
        pill.addView(line);
        if (left > 0) { // dalla terza volta nessun pulsante: decidi tu (209)
            LinearLayout b = new LinearLayout(a);
            b.setPadding(0, U.dp(a, 10), 0, 0);
            if (warnText != null) {
                b.addView(chip(a, warnText[2], true, new Runnable() { public void run() { finish(); } }));
                b.addView(chip(a, warnText[3], false, new Runnable() { public void run() { collapse(); } }));
            } else {
                b.addView(chip(a, "Ho finito", true, new Runnable() { public void run() { finish(); } }));
                b.addView(chip(a, "Altri " + Scopo.EXTRA_MIN + " min", false, new Runnable() { public void run() { collapse(); Scope.extend(app); } }));
            }
            pill.addView(b);
        }
        pill.setContentDescription(null);
    }

    private static TextView chip(Context a, String s, boolean filled, final Runnable r) {
        TextView t = U.text(a, s, 13, filled ? U.INK : U.BG, filled);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(U.dp(a, 36));
        t.setPadding(U.dp(a, 12), 0, U.dp(a, 12), 0);
        t.setBackground(filled ? U.box(a, U.BG, 0, 18) : U.box(a, U.INK, U.MUTED, 18));
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = U.dp(a, 8);
        t.setLayoutParams(lp);
        return t;
    }

    /** Tocco sulla pillola: chiusa → aperta per 5 secondi; aperta → chiusa. */
    private static void toggle() {
        long now = System.currentTimeMillis();
        if (wide) collapse();
        else {
            openUntil = now + Scopo.OPEN_MS;
            collapsedAt = 0;
            Pill.update(app);
            later(Scopo.OPEN_MS);
        }
    }

    private static void collapse() {
        openUntil = 0;
        warnText = null;
        collapsedAt = System.currentTimeMillis();
        Pill.update(app);
        if (overtime(app)) later(Scopo.REOPEN_MS);
    }

    /**
     * «Ho finito» / «Chiudo»: lo scopo si chiude subito qui, poi si torna alla home di −minus
     * (Android non lascia chiudere un'altra app). La home si apre prima di togliere la finestra,
     * finché la pillola è ancora visibile e Android lascia avviarla.
     */
    private static void finish() {
        Intent home = new Intent(app, MainActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        U.start(app, home);
        Scope.end(app, "chiusa");
    }

    private static void later(long ms) {
        H.removeCallbacks(REFRESH);
        H.postDelayed(REFRESH, ms + 50);
    }

    // ---------- Il foglio allo scadere (207, 208) ----------

    private static void showSheet(final Context a, String[] t) {
        hideSheet();
        try {
            WindowManager wm = (WindowManager) a.getSystemService(Context.WINDOW_SERVICE);
            LinearLayout dim = new LinearLayout(a);
            dim.setOrientation(LinearLayout.VERTICAL);
            dim.setGravity(Gravity.BOTTOM);
            dim.setBackgroundColor(0x59121211); // l'app resta visibile dietro, ma non si tocca finché non scegli
            dim.setClickable(true);
            LinearLayout s = new LinearLayout(a);
            s.setOrientation(LinearLayout.VERTICAL);
            s.setPadding(U.dp(a, 20), U.dp(a, 18), U.dp(a, 20), U.dp(a, 24));
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(U.BG);
            float r = U.dp(a, 22);
            bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
            s.setBackground(bg);
            LinearLayout head = new LinearLayout(a);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(new Ring(a, 1f, U.INK), new LinearLayout.LayoutParams(U.dp(a, 22), U.dp(a, 22)));
            TextView title = U.text(a, t[0], 20, U.INK, true);
            title.setPadding(U.dp(a, 10), 0, 0, 0);
            head.addView(title);
            s.addView(head);
            if (t[1].length() > 0) {
                TextView line = U.text(a, t[1], 14, U.MUTED, false);
                line.setPadding(0, U.dp(a, 8), 0, U.dp(a, 4));
                s.addView(line);
            }
            TextView close = U.text(a, t[2], 16, U.BG, true);
            close.setGravity(Gravity.CENTER);
            close.setMinHeight(U.dp(a, 50));
            close.setBackground(U.box(a, U.INK, 0, 25));
            close.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { hideSheet(); finish(); } });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.topMargin = U.dp(a, 12);
            s.addView(close, clp);
            TextView more = U.text(a, t[3], 15, U.INK, false);
            more.setGravity(Gravity.CENTER);
            more.setMinHeight(U.dp(a, 46));
            more.setBackground(U.box(a, U.BG, U.INK, 23));
            more.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { hideSheet(); Scope.extend(a); } });
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            mlp.topMargin = U.dp(a, 10);
            s.addView(more, mlp);
            dim.addView(s, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            wm.addView(dim, lp);
            sheet = dim;
            dim.announceForAccessibility(t[0] + ". " + t[1]);
        } catch (Exception e) {
            sheet = null;
        }
    }

    private static void hideSheet() {
        if (sheet == null) return;
        try { ((WindowManager) sheet.getContext().getSystemService(Context.WINDOW_SERVICE)).removeView(sheet); } catch (Exception ignored) {}
        sheet = null;
    }

    /** −minus in primo piano, scopo finito o schermo spento: via tutto. */
    static void hide() {
        H.removeCallbacks(REFRESH);
        if (pill != null) {
            try { ((WindowManager) pill.getContext().getSystemService(Context.WINDOW_SERVICE)).removeView(pill); } catch (Exception ignored) {}
            pill = null;
        }
        hideSheet();
    }

    /** Scopo finito: si riparte da capo la prossima volta. */
    static void reset() {
        hide();
        openUntil = 0;
        collapsedAt = 0;
        lastBuzz = 0;
        warnText = null;
    }

    private static int type() {
        return Build.VERSION.SDK_INT >= 26 ? 2038 /* TYPE_APPLICATION_OVERLAY */ : 2002 /* TYPE_PHONE */;
    }

    private static void buzz(Context c, long[] pattern) {
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(pattern, -1);
        } catch (Exception ignored) {}
    }

    /** L'anello che si svuota: in chiaro sulla pillola scura, in scuro sul foglio. */
    static final class Ring extends View {
        private final float frac;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG);

        Ring(Context c, float frac) { this(c, frac, U.BG); }

        Ring(Context c, float frac, int color) {
            super(c);
            this.frac = frac;
            float w = U.dp(c, 2);
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeWidth(w);
            track.setColor(color);
            track.setAlpha(77);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeWidth(w);
            arc.setStrokeCap(Paint.Cap.ROUND);
            arc.setColor(color);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = track.getStrokeWidth(), s = Math.min(getWidth(), getHeight());
            RectF r = new RectF(w, w, s - w, s - w);
            cv.drawOval(r, track);
            if (frac > 0) cv.drawArc(r, -90, 360 * frac, false, arc);
        }
    }
}
