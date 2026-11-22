package it.essenziale.launcher;

import android.app.Activity;
import android.app.Notification;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;

/**
 * Evolutiva 019 · «Attività in corso sul blocco»: la parte che parla con Android. Legge le notifiche attive dal guardiano
 * (FilterService), le passa a LiveLogic (che decide) e disegna quelle scelte con la vista che l'app ha preparato per
 * Android, dentro una scheda scura arrotondata, senza ridisegnarla (direzione B «nativa»). Niente viene salvato.
 */
final class Live {
    private Live() {}

    static final String K_ON = "live_lock";
    static boolean on(Context c) { return U.prefs(c).getBoolean(K_ON, true); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Runnable listener;

    /** La schermata di blocco si fa avvisare quando le notifiche cambiano. */
    static void listen(Runnable r) { listener = r; }

    /** Dal guardiano, a ogni notifica pubblicata o tolta. */
    static void changed() {
        final Runnable r = listener;
        if (r != null) MAIN.post(r);
    }

    // ---------- Quali ----------

    private static int promotedFlag() {
        try { return Notification.class.getField("FLAG_PROMOTED_ONGOING").getInt(null); } catch (Throwable t) { return LiveLogic.FLAG_PROMOTED_ONGOING; }
    }

    private static boolean template(Bundle ex, String name) {
        String t = ex == null ? null : ex.getString("android.template");
        return t != null && t.endsWith(name);
    }

    /** Le attività da mostrare ora, le più recenti prima (al massimo LiveLogic.MAX). */
    static List<StatusBarNotification> pick(Context c) {
        List<StatusBarNotification> out = new ArrayList<StatusBarNotification>();
        StatusBarNotification[] all = FilterService.active();
        if (all == null) return out;
        long now = System.currentTimeMillis();
        List<LiveLogic.Note> notes = new ArrayList<LiveLogic.Note>();
        final List<StatusBarNotification> src = new ArrayList<StatusBarNotification>();
        for (StatusBarNotification s : all) {
            try {
                LiveLogic.Note n = note(c, s, now);
                n.key = String.valueOf(src.size());
                notes.add(n);
                src.add(s);
            } catch (Throwable ignored) {}
        }
        for (LiveLogic.Note n : LiveLogic.pick(notes, now, LiveLogic.MAX)) out.add(src.get(Integer.parseInt(n.key)));
        return out;
    }

    private static LiveLogic.Note note(Context c, StatusBarNotification s, long now) {
        Notification n = s.getNotification();
        Bundle ex = n.extras == null ? new Bundle() : n.extras;
        LiveLogic.Note o = new LiveLogic.Note();
        o.pkg = s.getPackageName();
        o.ownPkg = c.getPackageName();
        o.category = n.category;
        o.flags = n.flags;
        o.when = s.getPostTime();
        String[] tt = FilterService.titleAndText(n);
        o.title = tt[0];
        o.text = tt[1];
        o.promoted = (n.flags & promotedFlag()) != 0;
        o.progressStyle = template(ex, "ProgressStyle");
        o.chrono = ex.getBoolean("android.showChronometer", false);
        o.countdown = ex.getBoolean("android.chronometerCountDown", false);
        o.hasProgress = ex.getInt("android.progressMax", 0) > 0 || ex.getBoolean("android.progressIndeterminate", false);
        Parcelable tok = ex.getParcelable("android.mediaSession");
        o.media = tok != null && template(ex, "MediaStyle") || tok != null && "transport".equals(n.category);
        if (o.media) mediaState(c, tok, s, o, now);
        return o;
    }

    /** In riproduzione o in pausa, e da quando. Senza accesso alla sessione: la musica in corso è quella con la notifica «in corso». */
    private static void mediaState(Context c, Parcelable tok, StatusBarNotification s, LiveLogic.Note o, long now) {
        o.playing = (s.getNotification().flags & LiveLogic.FLAG_ONGOING_EVENT) != 0;
        o.since = s.getPostTime();
        try {
            android.media.session.MediaController mc = new android.media.session.MediaController(c, (android.media.session.MediaSession.Token) tok);
            android.media.session.PlaybackState ps = mc.getPlaybackState();
            if (ps != null) {
                int st = ps.getState();
                o.playing = st == android.media.session.PlaybackState.STATE_PLAYING || st == android.media.session.PlaybackState.STATE_BUFFERING
                        || st == android.media.session.PlaybackState.STATE_CONNECTING || st == android.media.session.PlaybackState.STATE_FAST_FORWARDING
                        || st == android.media.session.PlaybackState.STATE_REWINDING;
                long upd = ps.getLastPositionUpdateTime(); // elapsedRealtime
                if (upd > 0) o.since = now - Math.max(0L, android.os.SystemClock.elapsedRealtime() - upd);
            }
        } catch (Throwable ignored) {}
    }

    /** Cambia quando cambia ciò che si vede (chi, e l'ultimo aggiornamento): serve a non ridisegnare per niente. */
    static String signature(List<StatusBarNotification> l) {
        StringBuilder b = new StringBuilder();
        for (StatusBarNotification s : l) b.append(s.getKey()).append('@').append(s.getPostTime()).append(';');
        return b.toString();
    }

    // ---------- Come ----------

    private static final int CARD = 0xFF2C2B30; // E019: lo sfondo scuro della scheda

    /** Il contesto con cui costruire la vista: quello della schermata di blocco, ma «di notte», perché i testi della scheda siano chiari. */
    private static Context nightContext(Activity a) {
        try {
            android.view.ContextThemeWrapper w = new android.view.ContextThemeWrapper(a, android.R.style.Theme_DeviceDefault);
            Configuration cfg = new Configuration(a.getResources().getConfiguration());
            cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
            w.applyOverrideConfiguration(cfg);
            return w;
        } catch (Throwable t) {
            return a;
        }
    }

    private static Object builder(Context c, Notification n) {
        if (Build.VERSION.SDK_INT < 24) return null;
        try {
            return Notification.Builder.class.getMethod("recoverBuilder", Context.class, Notification.class).invoke(null, c, n);
        } catch (Throwable t) {
            return null;
        }
    }

    private static RemoteViews views(Object builder, String method) {
        if (builder == null) return null;
        try {
            return (RemoteViews) builder.getClass().getMethod(method).invoke(builder);
        } catch (Throwable t) {
            return null;
        }
    }

    /** La vista dell'app: grande, poi normale, poi quelle che l'app ha messo a mano nella notifica. */
    @SuppressWarnings("deprecation")
    static RemoteViews remote(Context c, Notification n) {
        Object b = builder(c, n);
        RemoteViews rv = views(b, "createBigContentView");
        if (rv == null) rv = views(b, "createContentView");
        if (rv == null) rv = n.bigContentView;
        if (rv == null) rv = n.contentView;
        return rv;
    }

    /**
     * La scheda: sfondo scuro arrotondato e dentro la vista nativa dell'app, così com'è (nessun restyling). Un tocco fuori dai
     * pulsanti della vista chiama {@code open}. TalkBack legge app, titolo e testo.
     */
    static View card(Activity a, StatusBarNotification s, View.OnClickListener open) {
        Notification n = s.getNotification();
        FrameLayout f = new FrameLayout(a);
        GradientDrawable bg = U.box(a, CARD, 0, 24);
        f.setBackground(bg);
        if (Build.VERSION.SDK_INT >= 21) f.setClipToOutline(true);
        Context ctx = nightContext(a);
        View inner = null;
        try {
            RemoteViews rv = remote(ctx, n);
            if (rv != null) inner = rv.apply(ctx, f);
        } catch (Throwable ignored) {}
        String app = U.appLabel(a, s.getPackageName());
        String[] tt = FilterService.titleAndText(n);
        if (inner == null) inner = plain(a, app, tt[0], tt[1]); // l'app non ha dato una vista che si possa usare: titolo e testo
        f.addView(inner, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        f.setClickable(true);
        f.setFocusable(true);
        f.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        f.setContentDescription(LiveLogic.describe(app, tt[0], tt[1]));
        f.setOnClickListener(open);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = U.dp(a, 8);
        f.setLayoutParams(lp);
        return f;
    }

    private static View plain(Context c, String app, String title, String text) {
        LinearLayout col = U.col(c);
        col.setPadding(U.dp(c, 16), U.dp(c, 14), U.dp(c, 16), U.dp(c, 14));
        col.addView(U.text(c, app, 12, 0xFFC9C5CA, false));
        if (title != null && title.length() > 0) col.addView(U.text(c, title, 16, 0xFFE6E1E6, true));
        if (text != null && text.length() > 0) col.addView(U.text(c, text, 14, 0xFFC9C5CA, false));
        return col;
    }
}
