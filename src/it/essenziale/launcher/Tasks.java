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
    }

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
                // i completati restano visibili fino a fine giornata, poi spariscono
                if (t.done && t.doneAt < U.startOfDay(0)) continue;
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
            if (t.done && !includeDone) continue;
            if (t.due == 0 && !includeUndated) continue;
            if (t.due <= today) out.add(t);
        }
        sort(out);
        return out;
    }

    public static List<Task> forDay(Context c, long dayStart) {
        List<Task> out = new ArrayList<Task>();
        for (Task t : all(c)) if (!t.done && t.due == dayStart) out.add(t);
        return out;
    }

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
                long da = a.due == 0 ? Long.MAX_VALUE : a.due, db = b.due == 0 ? Long.MAX_VALUE : b.due;
                return da < db ? -1 : (da > db ? 1 : 0);
            }
        });
    }
}
