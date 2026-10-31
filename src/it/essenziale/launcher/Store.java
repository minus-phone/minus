package it.essenziale.launcher;

import android.app.PendingIntent;
import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Notifiche trattenute: livello 2 (In attesa) e livello 3 (Altro). */
public final class Store {
    private Store() {}

    public static final int WAITING = 2;
    public static final int OTHER = 3;
    private static final int MAX_ITEMS = 300;
    private static final long OTHER_TTL = 7L * 24 * 3600 * 1000; // Altro: tenuto 7 giorni

    public static class Item {
        public String id, pkg, app, title, text;
        public long time;
        public int level;
    }

    /** Le azioni originali delle notifiche: valide finché il telefono non si riavvia. */
    private static final Map<String, PendingIntent> INTENTS = new HashMap<String, PendingIntent>();

    public interface Listener { void onChanged(); }
    private static final List<Listener> LISTENERS = new ArrayList<Listener>();

    public static synchronized void addListener(Listener l) { LISTENERS.add(l); }
    public static synchronized void removeListener(Listener l) { LISTENERS.remove(l); }

    private static void notifyChanged() {
        List<Listener> copy;
        synchronized (Store.class) { copy = new ArrayList<Listener>(LISTENERS); }
        for (Listener l : copy) l.onChanged();
    }

    public static synchronized List<Item> load(Context c) {
        List<Item> out = new ArrayList<Item>();
        try {
            JSONArray a = new JSONArray(U.prefs(c).getString("items", "[]"));
            long now = System.currentTimeMillis();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item it = new Item();
                it.id = o.optString("id");
                it.pkg = o.optString("pkg");
                it.app = o.optString("app");
                it.title = o.optString("title");
                it.text = o.optString("text");
                it.time = o.optLong("time");
                it.level = o.optInt("level", OTHER);
                if (it.level == OTHER && now - it.time > OTHER_TTL) continue;
                out.add(it);
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static void save(Context c, List<Item> items) {
        JSONArray a = new JSONArray();
        try {
            for (int i = 0; i < items.size() && i < MAX_ITEMS; i++) {
                Item it = items.get(i);
                JSONObject o = new JSONObject();
                o.put("id", it.id); o.put("pkg", it.pkg); o.put("app", it.app);
                o.put("title", it.title); o.put("text", it.text);
                o.put("time", it.time); o.put("level", it.level);
                a.put(o);
            }
        } catch (Exception ignored) {}
        U.prefs(c).edit().putString("items", a.toString()).apply();
    }

    /** Aggiunge o aggiorna (stessa chiave = stessa conversazione), in cima alla lista. */
    public static void put(Context c, Item item, PendingIntent pi) {
        synchronized (Store.class) {
            List<Item> items = load(c);
            for (int i = items.size() - 1; i >= 0; i--) if (items.get(i).id.equals(item.id)) items.remove(i);
            items.add(0, item);
            save(c, items);
            if (pi != null) INTENTS.put(item.id, pi);
        }
        notifyChanged();
    }

    public static void remove(Context c, String id) {
        synchronized (Store.class) {
            List<Item> items = load(c);
            for (int i = items.size() - 1; i >= 0; i--) if (items.get(i).id.equals(id)) items.remove(i);
            save(c, items);
            INTENTS.remove(id);
        }
        notifyChanged();
    }

    public static void clearLevel(Context c, int level) {
        synchronized (Store.class) {
            List<Item> items = load(c);
            for (int i = items.size() - 1; i >= 0; i--) {
                if (items.get(i).level == level) { INTENTS.remove(items.get(i).id); items.remove(i); }
            }
            save(c, items);
        }
        notifyChanged();
    }

    public static synchronized PendingIntent intent(String id) { return INTENTS.get(id); }

    public static int count(Context c, int level) {
        int n = 0;
        for (Item it : load(c)) if (it.level == level) n++;
        return n;
    }
}
