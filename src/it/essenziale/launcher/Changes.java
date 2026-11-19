package it.essenziale.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Libertà di sbagliare (0.6): ogni scelta nelle impostazioni si annulla subito (avviso di 6 secondi
 * con «Annulla») e si ritrova in Modifiche recenti (ultime 20, per 30 giorni). Ripristinare è a sua
 * volta una modifica, quindi si può annullare.
 */
public final class Changes {
    private Changes() {}

    static final String KEY = "changes";
    static final int MAX = 20;
    static final long KEEP_MS = 30L * 24 * 3600 * 1000;
    static final int SHOW_MS = 6000;

    public static class Change {
        public long time;
        public String desc;
        public JSONObject before; // chiave -> valore precedente
    }

    /** Fotografa le chiavi, applica la scelta, la registra e mostra l'avviso con «Annulla». */
    public static void track(Activity a, String desc, String[] keys, Runnable change, Runnable refresh) {
        JSONObject before = snapshot(a, keys);
        change.run();
        if (sameAsNow(a, before)) { if (refresh != null) refresh.run(); return; }
        final Change c = new Change();
        c.time = System.currentTimeMillis();
        c.desc = desc;
        c.before = before;
        save(a, c);
        if (refresh != null) refresh.run();
        snackbar(a, desc, c, refresh);
    }

    private static Change pending;
    private static long pendingAt;

    /**
     * Come track, ma per le schermate che si chiudono subito dopo la scelta: l'avviso con «Annulla» compare nella
     * schermata che si apre dopo (BaseActivity.onResume), non in quella che sta per sparire (0.20).
     */
    public static void later(Activity a, String desc, String[] keys, Runnable change) {
        JSONObject before = snapshot(a, keys);
        change.run();
        if (sameAsNow(a, before)) return;
        Change c = new Change();
        c.time = System.currentTimeMillis();
        c.desc = desc;
        c.before = before;
        save(a, c);
        pending = c;
        pendingAt = c.time;
    }

    /** Mostra l'avviso rimandato da {@link #later}, se è recente. «Annulla» ridisegna la schermata. */
    static void showPending(final Activity a) {
        Change c = pending;
        if (c == null) return;
        pending = null;
        if (System.currentTimeMillis() - pendingAt > 10000L) return;
        snackbar(a, c.desc, c, new Runnable() { public void run() { a.recreate(); } });
    }

    /** Registra senza avviso (per le schermate che si chiudono subito dopo il salvataggio). */
    public static void record(Context c, String desc, String[] keys) {
        Change ch = new Change();
        ch.time = System.currentTimeMillis();
        ch.desc = desc;
        ch.before = snapshot(c, keys);
        save(c, ch);
    }

    /** Rimette i valori di prima; anche questo finisce in Modifiche recenti. */
    public static void restore(Activity a, Change c, Runnable refresh) {
        final List<String> keys = new ArrayList<String>();
        Iterator<String> it = c.before.keys();
        while (it.hasNext()) keys.add(it.next());
        final JSONObject before = c.before;
        final Context ctx = a;
        track(a, "Ripristinato: " + c.desc, keys.toArray(new String[0]), new Runnable() {
            public void run() { apply(ctx, before); }
        }, refresh);
    }

    public static List<Change> list(Context c) {
        List<Change> out = new ArrayList<Change>();
        long now = System.currentTimeMillis();
        try {
            JSONArray arr = new JSONArray(U.prefs(c).getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Change ch = new Change();
                ch.time = o.getLong("t");
                ch.desc = o.getString("d");
                ch.before = o.getJSONObject("b");
                if (now - ch.time <= KEEP_MS) out.add(ch);
            }
        } catch (Exception ignored) {}
        return out;
    }

    // ---------------------------------------------------------------------

    static JSONObject snapshot(Context c, String[] keys) {
        JSONObject o = new JSONObject();
        Map<String, ?> all = U.prefs(c).getAll();
        try {
            for (String k : keys) {
                Object v = all.get(k);
                JSONObject e = new JSONObject();
                if (v == null) e.put("n", true);
                else if (v instanceof Boolean) e.put("b", v);
                else if (v instanceof Integer) e.put("i", v);
                else if (v instanceof Long) e.put("l", v);
                else e.put("s", String.valueOf(v));
                o.put(k, e);
            }
        } catch (Exception ignored) {}
        return o;
    }

    static boolean sameAsNow(Context c, JSONObject before) {
        List<String> keys = new ArrayList<String>();
        Iterator<String> it = before.keys();
        while (it.hasNext()) keys.add(it.next());
        return snapshot(c, keys.toArray(new String[0])).toString().equals(before.toString());
    }

    static void apply(Context c, JSONObject before) {
        SharedPreferences.Editor ed = U.prefs(c).edit();
        Iterator<String> it = before.keys();
        while (it.hasNext()) {
            String k = it.next();
            JSONObject e = before.optJSONObject(k);
            if (e == null || e.has("n")) ed.remove(k);
            else if (e.has("b")) ed.putBoolean(k, e.optBoolean("b"));
            else if (e.has("i")) ed.putInt(k, e.optInt("i"));
            else if (e.has("l")) ed.putLong(k, e.optLong("l"));
            else ed.putString(k, e.optString("s"));
        }
        ed.commit();
    }

    static void save(Context c, Change ch) {
        try {
            List<Change> l = list(c);
            l.add(0, ch);
            JSONArray arr = new JSONArray();
            for (int i = 0; i < l.size() && i < MAX; i++) {
                JSONObject o = new JSONObject();
                o.put("t", l.get(i).time);
                o.put("d", l.get(i).desc);
                o.put("b", l.get(i).before);
                arr.put(o);
            }
            U.prefs(c).edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    /** Avviso in basso per 6 secondi: «descrizione · Annulla». */
    static void snackbar(final Activity a, String desc, final Change c, final Runnable refresh) {
        final ViewGroup content = (ViewGroup) a.findViewById(android.R.id.content);
        if (content == null) return;
        final View old = content.findViewWithTag("snackbar");
        if (old != null) content.removeView(old);
        LinearLayout bar = U.row(a);
        bar.setTag("snackbar");
        bar.setPadding(U.dp(a, 16), U.dp(a, 4), U.dp(a, 6), U.dp(a, 4));
        bar.setBackground(U.box(a, U.INK, 0, 14));
        bar.setMinimumHeight(U.dp(a, 48));
        TextView t = U.text(a, desc, 14, U.BG, false);
        t.setMaxLines(2);
        t.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); // 0.20: «Comandi vocali: acceso» lo dice TalkBack, una volta
        bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView undo = U.text(a, "Annulla", 14, U.BG, true);
        undo.setPaintFlags(undo.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        undo.setGravity(Gravity.CENTER);
        undo.setPadding(U.dp(a, 12), 0, U.dp(a, 12), 0);
        undo.setMinHeight(U.dp(a, 44));
        Press.on(undo, null, U.box(a, Press.alpha(U.BG, 0.16f), 0, 10)); // 0.20: premuto, un alone chiaro sul fondo scuro dell'avviso
        final View barF = bar;
        undo.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                content.removeView(barF);
                apply(a, c.before);
                dropLatest(a, c);
                if (refresh != null) refresh.run();
                U.toast(a, "Annullato");
            }
        });
        bar.addView(undo);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        lp.setMargins(U.dp(a, 16), 0, U.dp(a, 16), U.dp(a, 16));
        content.addView(bar, lp);
        // 0.20 (Evolutiva 016): entra in BASE (dissolvenza e 16 dp in su, decelera), esce in RAPIDO in dissolvenza
        Motion.enter(bar, 0, Motion.BASE, 16);
        new Handler().postDelayed(new Runnable() {
            public void run() {
                if (barF.getParent() != content) return;
                barF.animate().alpha(0f).setDuration(Motion.dur(a, Motion.RAPIDO)).setInterpolator(Motion.DECELERA).withEndAction(new Runnable() {
                    public void run() { content.removeView(barF); }
                }).start();
            }
        }, SHOW_MS);
    }

    /** Un «Annulla» immediato non lascia traccia in Modifiche recenti. */
    static void dropLatest(Context c, Change ch) {
        try {
            JSONArray arr = new JSONArray(U.prefs(c).getString(KEY, "[]"));
            JSONArray out = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.getLong("t") == ch.time && o.getString("d").equals(ch.desc)) continue;
                out.put(o);
            }
            U.prefs(c).edit().putString(KEY, out.toString()).apply();
        } catch (Exception ignored) {}
    }

    /** «Ieri 22:14», «Oggi 9:05», «Mar 20». */
    public static String when(long t) {
        long d0 = U.startOfDay(0);
        String hm = new java.text.SimpleDateFormat("H:mm", java.util.Locale.ITALY).format(new java.util.Date(t));
        if (t >= d0) return "Oggi " + hm;
        if (t >= U.startOfDay(-1)) return "Ieri " + hm;
        return U.dayLabel(t);
    }
}
