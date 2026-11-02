package it.essenziale.launcher;

import android.app.Notification;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 0.13.8: la stessa persona si riconosce dal numero, non dal nome («Filippo» in rubrica,
 * «Filippo Ferro» su Telegram). Il filtro, quando arriva un messaggio, ricorda i numeri che la
 * notifica porta con sé (le persone allegate) o, se mancano, quelli della rubrica con lo stesso nome.
 */
final class Nums {
    private Nums() {}

    static final String K_MAP = "conv_numbers"; // nome normalizzato | numeri separati da virgola

    /** Le ultime 9 cifre: +39 333 1234567 e 3331234567 diventano uguali. Null se non è un numero. */
    static String digits(String s) { return Cerca.digits(s); }

    /** Dal filtro notifiche: ricorda i numeri di chi ha scritto. */
    static void remember(Context c, String name, Notification n) {
        if (name == null || name.length() == 0) return;
        Set<String> found = new HashSet<String>();
        try {
            if (n != null && n.extras != null) {
                String[] people = n.extras.getStringArray(Notification.EXTRA_PEOPLE);
                if (people != null) for (String u : people) found.addAll(fromUri(c, u));
                Object list = n.extras.get("android.people.list"); // Android 9+: oggetti Person con l'uri
                if (list instanceof java.util.List) for (Object p : (java.util.List<?>) list) {
                    try {
                        Object u = p.getClass().getMethod("getUri").invoke(p);
                        if (u != null) found.addAll(fromUri(c, u.toString()));
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        if (found.isEmpty()) found.addAll(byName(c, name));
        if (found.isEmpty()) return;
        String key = Cerca.norm(name).replace("|", "/");
        StringBuilder b = new StringBuilder();
        String old = U.prefs(c).getString(K_MAP, "");
        int kept = 0;
        for (String line : old.split("\n")) {
            if (line.length() == 0 || line.startsWith(key + " | ")) continue;
            if (++kept > 300) break;
            b.append(line).append('\n');
        }
        StringBuilder v = new StringBuilder();
        for (String d : found) { if (v.length() > 0) v.append(','); v.append(d); }
        U.prefs(c).edit().putString(K_MAP, key + " | " + v + "\n" + b).apply();
    }

    /** I numeri noti per il nome di una conversazione. */
    static Set<String> forConv(Context c, String name) {
        Set<String> out = new HashSet<String>();
        String key = Cerca.norm(name).replace("|", "/") + " | ";
        for (String line : U.prefs(c).getString(K_MAP, "").split("\n"))
            if (line.startsWith(key)) for (String d : line.substring(key.length()).split(",")) if (d.trim().length() > 0) out.add(d.trim());
        return out;
    }

    /** I numeri di una persona che passa sempre: quello salvato e quelli in rubrica con lo stesso nome. */
    static Set<String> forPerson(Context c, Rules.Person p) {
        Set<String> out = new HashSet<String>();
        String d = digits(p.target);
        if (d != null) out.add(d);
        out.addAll(byName(c, p.name));
        return out;
    }

    private static Set<String> fromUri(Context c, String u) {
        Set<String> out = new HashSet<String>();
        if (u == null) return out;
        String l = u.toLowerCase(Locale.ROOT);
        if (l.startsWith("tel:")) { String d = digits(u.substring(4)); if (d != null) out.add(d); return out; }
        if (!l.startsWith("content://com.android.contacts")) return out;
        if (!canRead(c)) return out;
        Cursor cur = null;
        try {
            // l'uri di un contatto (anche lookup): l'id, poi i suoi numeri
            Uri cu = ContactsContract.Contacts.lookupContact(c.getContentResolver(), Uri.parse(u));
            if (cu == null) cu = Uri.parse(u);
            cur = c.getContentResolver().query(cu, new String[]{ContactsContract.Contacts._ID}, null, null, null);
            if (cur != null && cur.moveToFirst()) {
                long id = cur.getLong(0);
                cur.close();
                cur = c.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[]{String.valueOf(id)}, null);
                while (cur != null && cur.moveToNext()) { String d = digits(cur.getString(0)); if (d != null) out.add(d); }
            }
        } catch (Throwable ignored) {
        } finally { if (cur != null) cur.close(); }
        return out;
    }

    private static Set<String> byName(Context c, String name) {
        Set<String> out = new HashSet<String>();
        if (name == null || !canRead(c)) return out;
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " = ?", new String[]{name.trim()}, null);
            while (cur != null && cur.moveToNext()) { String d = digits(cur.getString(0)); if (d != null) out.add(d); }
        } catch (Throwable ignored) {
        } finally { if (cur != null) cur.close(); }
        return out;
    }

    private static boolean canRead(Context c) {
        return c.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }
}
