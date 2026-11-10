package it.essenziale.launcher;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;

/**
 * 0.14 (wireframe 185): chi conta prende la stella in rubrica. Così il silenzio di Android, che
 * fa passare i preferiti, e la lista «Sempre» di −minus sono la stessa cosa.
 */
public final class Stars {
    private Stars() {}

    static final String K_STARRED = "stars_by_minus"; // id dei contatti a cui la stella l'ha messa −minus
    static final String K_SEEN = "stars_seen";        // 0.15: le stelle in rubrica già viste da −minus

    /**
     * 0.15: la stella vale anche al contrario. Un contatto a cui metti la stella in rubrica (anche in Google
     * Contatti) entra tra chi passa sempre, con WhatsApp e chiamata. Le stelle che c'erano prima di questo
     * controllo non contano: si parte da adesso. Restituisce i nomi aggiunti.
     */
    static java.util.List<String> adoptNewStars(Context c) {
        java.util.List<String> added = new java.util.ArrayList<String>();
        if (!Onboarding.hasContacts(c)) return added;
        java.util.Map<Long, String> starred = new java.util.LinkedHashMap<Long, String>();
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
                    new String[]{ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME},
                    ContactsContract.Contacts.STARRED + " = 1", null, null);
            while (cur != null && cur.moveToNext()) starred.put(cur.getLong(0), cur.getString(1));
        } catch (Exception e) {
            return added;
        } finally {
            if (cur != null) cur.close();
        }
        String raw = U.prefs(c).getString(K_SEEN, null);
        java.util.Set<Long> seen = null;
        if (raw != null) {
            seen = new java.util.HashSet<Long>();
            for (String s : raw.split(",")) try { if (s.length() > 0) seen.add(Long.parseLong(s)); } catch (Exception ignored) {}
        }
        java.util.List<Rules.Person> people = Rules.people(U.cfg(c, U.K_CONTACTS, U.DEF_CONTACTS));
        java.util.Set<String> always = new java.util.HashSet<String>();
        for (Rules.Person p : people) if (p.section.equalsIgnoreCase("Sempre")) always.add(p.name.trim().toLowerCase(java.util.Locale.ITALY));
        java.util.List<Long> fresh = Rules.newStars(seen, starred, always);
        if (!fresh.isEmpty()) {
            for (Long id : fresh) {
                String name = starred.get(id).replace("|", "/").trim();
                // se era tra chi aspetta o altrove, diventa «Sempre»
                for (int i = people.size() - 1; i >= 0; i--) if (people.get(i).name.trim().equalsIgnoreCase(name)) people.remove(i);
                Rules.Person p = new Rules.Person();
                p.section = "Sempre";
                p.name = name;
                p.target = numberOf(c, id);
                p.apps.add("WA");
                p.apps.add("TEL");
                people.add(0, p);
                added.add(name);
            }
            Changes.record(c, "Stella in rubrica: " + android.text.TextUtils.join(", ", added) + " passa sempre", new String[]{U.K_CONTACTS});
            U.prefs(c).edit().putString(U.K_CONTACTS, Rules.peopleText(people)).apply();
        }
        StringBuilder b = new StringBuilder();
        for (Long id : starred.keySet()) b.append(id).append(',');
        U.prefs(c).edit().putString(K_SEEN, b.toString()).apply();
        return added;
    }

    private static String numberOf(Context c, long id) {
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE},
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[]{String.valueOf(id)}, null);
            String any = "";
            while (cur != null && cur.moveToNext()) {
                if (cur.getInt(1) == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) return cur.getString(0);
                if (any.length() == 0 && cur.getString(0) != null) any = cur.getString(0);
            }
            return any;
        } catch (Exception e) {
            return "";
        } finally {
            if (cur != null) cur.close();
        }
    }

    static boolean canWrite(Context c) {
        return c.checkSelfPermission(android.Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    /** L'id del contatto con quel nome esatto, o -1. */
    static long idOf(Context c, String name) {
        if (name == null || !Onboarding.hasContacts(c)) return -1;
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(ContactsContract.Contacts.CONTENT_URI, new String[]{ContactsContract.Contacts._ID},
                    ContactsContract.Contacts.DISPLAY_NAME + " = ?", new String[]{name.trim()}, null);
            return cur != null && cur.moveToFirst() ? cur.getLong(0) : -1;
        } catch (Exception e) {
            return -1;
        } finally {
            if (cur != null) cur.close();
        }
    }

    static boolean isStarred(Context c, long id) {
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(ContactsContract.Contacts.CONTENT_URI, new String[]{ContactsContract.Contacts.STARRED},
                    ContactsContract.Contacts._ID + " = ?", new String[]{String.valueOf(id)}, null);
            return cur != null && cur.moveToFirst() && cur.getInt(0) == 1;
        } catch (Exception e) {
            return false;
        } finally {
            if (cur != null) cur.close();
        }
    }

    /** Mette la stella alle persone che passano sempre. Restituisce quante ne ha aggiunte. */
    static int starAlways(Context c) {
        if (!canWrite(c)) return 0;
        int n = 0;
        StringBuilder mine = new StringBuilder(U.prefs(c).getString(K_STARRED, ""));
        for (Rules.Person p : Rules.people(U.cfg(c, U.K_CONTACTS, U.DEF_CONTACTS))) {
            if (!p.section.equalsIgnoreCase("Sempre")) continue;
            long id = idOf(c, p.name);
            if (id < 0 || isStarred(c, id)) continue;
            if (set(c, id, true)) { n++; mine.append(id).append(','); }
        }
        U.prefs(c).edit().putString(K_STARRED, mine.toString()).apply();
        return n;
    }

    /** Toglie la stella solo dove l'aveva messa −minus (wireframe 200: «con la proposta di toglierla»). */
    static int unstarMine(Context c) {
        if (!canWrite(c)) return 0;
        int n = 0;
        for (String s : U.prefs(c).getString(K_STARRED, "").split(",")) {
            try { if (s.length() > 0 && set(c, Long.parseLong(s), false)) n++; } catch (Exception ignored) {}
        }
        U.prefs(c).edit().remove(K_STARRED).apply();
        return n;
    }

    static int mineCount(Context c) {
        int n = 0;
        for (String s : U.prefs(c).getString(K_STARRED, "").split(",")) if (s.length() > 0) n++;
        return n;
    }

    static boolean set(Context c, long id, boolean on) {
        try {
            ContentValues v = new ContentValues();
            v.put(ContactsContract.Contacts.STARRED, on ? 1 : 0);
            return c.getContentResolver().update(ContactsContract.Contacts.CONTENT_URI, v,
                    ContactsContract.Contacts._ID + " = ?", new String[]{String.valueOf(id)}) > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
