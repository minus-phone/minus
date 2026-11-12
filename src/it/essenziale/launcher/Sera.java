package it.essenziale.launcher;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;

/**
 * Evolutiva 002 · Avviso e consiglio della sera, dentro «Meno interferenze sul sonno».
 * 10 minuti prima dell'ora di staccare una riga in home e sulla pillola; all'ora di staccare un avviso,
 * una volta: la pillola sopra un'app frenata (tocco aptico breve), una notifica silenziosa sopra le altre
 * app, la scheda in home allo sblocco. «Ancora 15 min» una volta a sera. La logica è in Serale (pura).
 */
final class Sera {
    private Sera() {}

    static final String K_PREWARN = "sera_prewarn", K_TIP = "sera_tip", K_TIP_START = "sera_tip_start",
            K_ACK = "sera_ack", K_ALERTED = "sera_alerted", K_SNOOZE_UNTIL = "sera_snooze_until", K_SNOOZED = "sera_snoozed";
    static final String ACT_OK = "it.essenziale.launcher.SERA_OK", ACT_MORE = "it.essenziale.launcher.SERA_MORE";
    static final int NOTIF_ID = 4402;

    static SharedPreferences p(Context c) { return U.prefs(c); }

    static boolean prewarnOn(Context c) { return p(c).getBoolean(K_PREWARN, true); }
    static boolean tipOn(Context c) { return p(c).getBoolean(K_TIP, true); }

    /** Con l'ora di staccare accesa (obiettivo sonno, orario). */
    static boolean scheduled(Context c) {
        return Night.on(c) && Notte.SCHED.equals(Night.mode(c)) && Goals.on(c, Goals.SLEEP);
    }

    static int nowMin() {
        Calendar k = Calendar.getInstance();
        return k.get(Calendar.HOUR_OF_DAY) * 60 + k.get(Calendar.MINUTE);
    }

    /** La sera di oggi: anche dopo mezzanotte resta quella cominciata ieri. */
    static long evening() { return U.startOfDayOf(System.currentTimeMillis() - 12L * 3600000L); }

    // ---------- Preavviso ----------

    /** «Tra 10 minuti −minus passa alla sera», o null. */
    static String prewarn(Context c) {
        if (!scheduled(c) || !prewarnOn(c) || !p(c).getBoolean(Night.K_EVENING, true)) return null;
        int now = nowMin(), off = Night.offMin(c);
        return Serale.prewarn(now, off) ? Serale.prewarnText(Serale.minutesTo(now, off)) : null;
    }

    // ---------- Ancora 15 minuti ----------

    static boolean snoozed(Context c) { return System.currentTimeMillis() < p(c).getLong(K_SNOOZE_UNTIL, 0); }

    static boolean canSnooze(Context c) { return p(c).getLong(K_SNOOZED, 0) != evening(); }

    static void snooze(Context c) {
        if (!canSnooze(c)) { ack(c); return; }
        p(c).edit().putLong(K_SNOOZED, evening()).putLong(K_SNOOZE_UNTIL, System.currentTimeMillis() + Serale.SNOOZE_MS)
                .putLong(K_ALERTED, 0).apply();
        clear(c);
    }

    // ---------- Avviso ----------

    /** All'ora di staccare, finché non dici «Va bene». */
    static boolean pending(Context c) {
        return scheduled(c) && Night.evening(c) && p(c).getLong(K_ACK, 0) != evening();
    }

    static void ack(Context c) {
        p(c).edit().putLong(K_ACK, evening()).apply();
        clear(c);
    }

    static void clear(Context c) {
        hidePill();
        try { ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIF_ID); } catch (Exception ignored) {}
    }

    /** Il consiglio della settimana: uno nuovo ogni lunedì, 26 in tutto. */
    static String tip(Context c) {
        if (!tipOn(c)) return null;
        return Serale.TIPS[tipIndex(c)];
    }

    static int tipIndex(Context c) {
        long w = Serale.week(localDay(System.currentTimeMillis()));
        if (!p(c).contains(K_TIP_START)) p(c).edit().putLong(K_TIP_START, w).apply();
        return Serale.tipIndex(w, p(c).getLong(K_TIP_START, w));
    }

    static long localDay(long t) {
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(t);
        return (t + k.get(Calendar.ZONE_OFFSET) + k.get(Calendar.DST_OFFSET)) / 86400000L;
    }

    static String alarmLine(Context c) {
        return "La sveglia suona alle " + Notte.hhmm(Night.wakeMin(c)) + ".";
    }

    /** Ogni 30 secondi a schermo acceso (FilterService): l'avviso arriva una volta, dove serve. */
    static void tick(Context c, boolean minusFront) {
        if (!pending(c)) { hidePill(); return; }
        if (p(c).getLong(K_ALERTED, 0) == evening()) return;
        String fg = minusFront ? null : foreground(c);
        boolean braked = fg != null && (Night.nightApps(c).contains(fg) || U.isPaused(c, fg));
        int where = Serale.where(true, minusFront || fg == null || fg.equals(c.getPackageName()), braked, Pill.canDraw(c));
        if (where == Serale.HOME) return; // la home lo mostra da sé
        p(c).edit().putLong(K_ALERTED, evening()).apply();
        if (where == Serale.PILL) { showPill(c); buzz(c); }
        else notify(c);
    }

    static String foreground(Context c) {
        try {
            UsageStatsManager um = (UsageStatsManager) c.getSystemService("usagestats");
            long now = System.currentTimeMillis();
            UsageEvents ev = um.queryEvents(now - 6 * 3600000L, now);
            UsageEvents.Event e = new UsageEvents.Event();
            String last = null;
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                if (e.getEventType() == Tempo.FG) last = e.getPackageName();
            }
            return last;
        } catch (Exception e) {
            return null;
        }
    }

    private static void buzz(Context c) {
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(30);
        } catch (Exception ignored) {}
    }

    private static PendingIntent action(Context c, String a, int code) {
        Intent i = new Intent(c, ScopeReceiver.class);
        i.setAction(a);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Sopra un'app non frenata (scrivi, lavori): una notifica silenziosa che resta finché non la tocchi. */
    private static void notify(Context c) {
        try {
            String tip = tip(c);
            String text = U.brand(alarmLine(c) + (tip != null ? " " + tip : "")).toString();
            Intent home = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            Notification.Builder b = new Notification.Builder(c)
                    .setSmallIcon(Icon.createWithResource("android", android.R.drawable.ic_lock_idle_alarm))
                    .setContentTitle("È l'ora di staccare")
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setPriority(Notification.PRIORITY_LOW)
                    .setOnlyAlertOnce(true)
                    .setCategory(Notification.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setContentIntent(PendingIntent.getActivity(c, 43, home, PendingIntent.FLAG_UPDATE_CURRENT))
                    .addAction(new Notification.Action.Builder(null, "Va bene", action(c, ACT_OK, 44)).build());
            if (canSnooze(c)) b.addAction(new Notification.Action.Builder(null, "Ancora 15 min", action(c, ACT_MORE, 45)).build());
            ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIF_ID, b.build());
        } catch (Exception ignored) {}
    }

    // ---------- La pillola aperta, sopra le app frenate ----------

    private static View pill;

    static void showPill(final Context c) {
        final Context a = c.getApplicationContext();
        try {
            hidePill();
            WindowManager wm = (WindowManager) a.getSystemService(Context.WINDOW_SERVICE);
            LinearLayout box = new LinearLayout(a);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(U.dp(a, 18), U.dp(a, 14), U.dp(a, 18), U.dp(a, 12));
            box.setBackground(U.box(a, 0xFF171716, 0, 22));
            TextView t = U.text(a, "È l'ora di staccare", 17, 0xFFF3F1EB, true);
            box.addView(t);
            TextView l = U.text(a, "−minus passa alla sera. " + alarmLine(a), 14, 0xFFD0CEC7, false);
            l.setPadding(0, U.dp(a, 4), 0, 0);
            box.addView(l);
            String tip = tip(a);
            if (tip != null) {
                View line = new View(a);
                line.setBackgroundColor(0xFF3A3A37);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, U.dp(a, 1)));
                llp.topMargin = U.dp(a, 10);
                box.addView(line, llp);
                TextView tl = U.text(a, "CONSIGLIO DELLA SETTIMANA", 11, 0xFFA19F98, false);
                tl.setLetterSpacing(0.06f);
                tl.setPadding(0, U.dp(a, 8), 0, 0);
                box.addView(tl);
                TextView tt = U.text(a, tip, 14, 0xFFF3F1EB, false);
                tt.setPadding(0, U.dp(a, 2), 0, 0);
                box.addView(tt);
            }
            LinearLayout btns = new LinearLayout(a);
            btns.setOrientation(LinearLayout.HORIZONTAL);
            btns.setPadding(0, U.dp(a, 12), 0, 0);
            TextView ok = button(a, "Va bene", true);
            ok.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { ack(a); } });
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, U.dp(a, 44), 1f);
            blp.rightMargin = U.dp(a, 8);
            btns.addView(ok, blp);
            if (canSnooze(a)) {
                TextView more = button(a, "Ancora 15 min", false);
                more.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { snooze(a); } });
                btns.addView(more, new LinearLayout.LayoutParams(0, U.dp(a, 44), 1f));
            }
            box.addView(btns);
            box.setContentDescription("È l'ora di staccare. " + alarmLine(a) + (tip != null ? " Consiglio della settimana: " + tip : ""));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    U.dp(a, 328), WindowManager.LayoutParams.WRAP_CONTENT,
                    Build.VERSION.SDK_INT >= 26 ? 2038 : 2002,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.y = U.dp(a, 34);
            wm.addView(box, lp);
            pill = box;
        } catch (Exception ignored) {
            pill = null;
        }
    }

    private static TextView button(Context a, String s, boolean primary) {
        TextView b = U.text(a, s, 15, primary ? 0xFF171716 : 0xFFF3F1EB, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(U.box(a, primary ? 0xFFF3F1EB : 0xFF171716, primary ? 0 : 0xFF62625C, 22));
        return b;
    }

    static void hidePill() {
        if (pill == null) return;
        try { ((WindowManager) pill.getContext().getSystemService(Context.WINDOW_SERVICE)).removeView(pill); } catch (Exception ignored) {}
        pill = null;
    }
}
