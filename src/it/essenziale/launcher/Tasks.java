package it.essenziale.launcher;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Task locali di Essenziale. Scadenza = mezzanotte del giorno (0 = nessuna scadenza). */
public final class Tasks {
    private Tasks() {}

    public static class Task {
        public String id, title;
        public String app;    // 0.13: pacchetto dell'app che si apre toccando il task («Fare la spesa» → Keep), o null
        public long due;      // inizio del giorno di scadenza, 0 se nessuna
        public boolean done;
        public long doneAt;
        public long order;    // 0.15: ordine scelto nella giornata (0 = non scelto: per scadenza)
    }

    /** 0.15: i completati restano per la vista settimana, nel giorno in cui sono stati spuntati. */
    static final long KEEP_DONE_MS = 35L * 24 * 3600 * 1000;
    static final String K_LAST_UNLOCK = "last_unlock";

    public static synchronized List<Task> all(Context c) {
        List<Task> out = new ArrayList<Task>();
        try {
            JSONArray a = new JSONArray(U.prefs(c).getString("tasks", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Task t = new Task();
                t.id = o.optString("id");
                t.title = o.optString("title");
                t.due = o.optLong("due");
                t.done = o.optBoolean("done");
                t.doneAt = o.optLong("doneAt");
                t.app = o.optString("app", "");
                if (t.app.length() == 0) t.app = null;
                t.order = o.optLong("order");
                // 0.15: i completati restano 35 giorni, per la vista settimana
                if (t.done && t.doneAt < System.currentTimeMillis() - KEEP_DONE_MS) continue;
                out.add(t);
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static void save(Context c, List<Task> list) {
        JSONArray a = new JSONArray();
        try {
            for (Task t : list) {
                JSONObject o = new JSONObject();
                o.put("id", t.id); o.put("title", t.title); o.put("due", t.due);
                o.put("done", t.done); o.put("doneAt", t.doneAt);
                if (t.app != null) o.put("app", t.app);
                if (t.order != 0) o.put("order", t.order);
                a.put(o);
            }
        } catch (Exception ignored) {}
        U.prefs(c).edit().putString("tasks", a.toString()).apply();
    }

    public static synchronized void add(Context c, String title, long due) { add(c, title, due, null); }

    public static synchronized void add(Context c, String title, long due, String app) {
        List<Task> list = all(c);
        Task t = new Task();
        t.app = app;
        t.id = String.valueOf(System.currentTimeMillis());
        t.title = title;
        t.due = due;
        list.add(t);
        save(c, list);
    }

    public static synchronized void setDone(Context c, String id, boolean done) {
        List<Task> list = all(c);
        for (Task t : list) if (t.id.equals(id)) { t.done = done; t.doneAt = done ? System.currentTimeMillis() : 0; }
        save(c, list);
    }

    public static synchronized void delete(Context c, String id) {
        List<Task> list = all(c);
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id.equals(id)) list.remove(i);
        save(c, list);
    }

    /** Da fare oggi: scaduti, in scadenza oggi, senza scadenza; più i completati di oggi. Scaduti prima. */
    public static List<Task> forToday(Context c, boolean includeDone, boolean includeUndated) {
        long today = U.startOfDay(0);
        List<Task> out = new ArrayList<Task>();
        for (Task t : all(c)) {
            if (t.done && (!includeDone || t.doneAt < today)) continue;
            if (t.due == 0 && !includeUndated && !t.done) continue;
            if (t.done || t.due <= today) out.add(t);
        }
        sort(out);
        return out;
    }

    /** Da fare in un giorno futuro (con quella scadenza). */
    public static List<Task> forDay(Context c, long dayStart) {
        List<Task> out = new ArrayList<Task>();
        for (Task t : all(c)) if (!t.done && t.due == dayStart) out.add(t);
        sort(out);
        return out;
    }

    /** 0.15: completati in un giorno (per la vista settimana). */
    public static List<Task> doneOn(Context c, long dayStart) {
        long end = dayStart + 24L * 3600 * 1000;
        List<Task> out = new ArrayList<Task>();
        for (Task t : all(c)) if (t.done && t.doneAt >= dayStart && t.doneAt < end) out.add(t);
        return out;
    }

    /** 0.15: da fare senza scadenza, in alto nella vista settimana. */
    public static List<Task> undated(Context c) {
        List<Task> out = new ArrayList<Task>();
        for (Task t : all(c)) if (!t.done && t.due == 0) out.add(t);
        sort(out);
        return out;
    }

    /** 0.15: sposta un task su (−1) o giù (+1) tra quelli mostrati; da qui in poi l'ordine è quello scelto. */
    public static synchronized void move(Context c, List<Task> shown, String id, int delta) {
        List<String> ids = new ArrayList<String>();
        int at = -1;
        for (int i = 0; i < shown.size(); i++) { ids.add(shown.get(i).id); if (shown.get(i).id.equals(id)) at = i; }
        List<String> moved = Ordine.move(ids, at, delta);
        List<Task> list = all(c);
        for (Task t : list) { int k = moved.indexOf(t.id); if (k >= 0) t.order = k + 1; }
        save(c, list);
    }

    static long lastUnlock(Context c) { return U.prefs(c).getLong(K_LAST_UNLOCK, 0); }

    public static boolean overdue(Task t) {
        return !t.done && t.due != 0 && t.due < U.startOfDay(0);
    }

    public static String dueLabel(Task t) {
        if (t.done) return "";
        if (t.due == 0) return "";
        if (t.due < U.startOfDay(0)) return "scaduto";
        if (t.due == U.startOfDay(0)) return "oggi";
        if (t.due == U.startOfDay(1)) return "domani";
        return U.dayLabel(t.due);
    }

    private static void sort(List<Task> l) {
        Collections.sort(l, new Comparator<Task>() {
            public int compare(Task a, Task b) {
                if (a.done != b.done) return a.done ? 1 : -1;
                // 0.15: l'ordine scelto prima; i task nuovi (senza ordine) dopo, per scadenza
                long oa = a.order == 0 ? Long.MAX_VALUE : a.order, ob = b.order == 0 ? Long.MAX_VALUE : b.order;
                if (oa != ob) return oa < ob ? -1 : 1;
                long da = a.due == 0 ? Long.MAX_VALUE : a.due, db = b.due == 0 ? Long.MAX_VALUE : b.due;
                return da < db ? -1 : (da > db ? 1 : 0);
            }
        });
    }
}
