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
