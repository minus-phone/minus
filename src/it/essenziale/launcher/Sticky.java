package it.essenziale.launcher;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Icone fisse nella barra di stato (0.9, wireframe 119). Le notifiche che non passano Essenziale
 * le toglie già; quelle fisse (musica, servizi attivi) Android non permette di toglierle. Qui si
 * ricorda chi le ha mostrate negli ultimi 7 giorni, per proporre «Rendi silenziosa».
 */
final class Sticky {
    private Sticky() {}

    static final String KEY = "sticky", K_KEEP = "sticky_keep";
    static final long WEEK = 7L * 24 * 3600000L;
    static final int MAX = 20;

    /** Una notifica fissa di un'altra app: pacchetto | categoria (canale) | ultima volta | descrizione. */
    static boolean isSticky(Notification n) {
        return (n.flags & (Notification.FLAG_ONGOING_EVENT | 0x40 /* FLAG_FOREGROUND_SERVICE */)) != 0;
    }

    static String channel(Notification n) {
        try {
            Object o = n.getClass().getMethod("getChannelId").invoke(n);
            return o == null ? "" : o.toString();
        } catch (Exception e) {
            return "";
        }
    }

    static synchronized void seen(Context c, String pkg, String channel, String what) {
        if (pkg == null || pkg.equals(c.getPackageName()) || pkg.equals("android") || pkg.equals("com.android.systemui")) return;
        long now = System.currentTimeMillis();
        List<String[]> rows = rows(c);
        List<String[]> out = new ArrayList<String[]>();
        out.add(new String[]{pkg, channel == null ? "" : channel.replace("|", "/"), String.valueOf(now), what == null ? "" : what.replace("|", "/").replace("\n", " ")});
        for (String[] r : rows) {
            if (r[0].equals(pkg) && r[1].equals(channel == null ? "" : channel)) continue;
            if (out.size() >= MAX) break;
            out.add(r);
        }
        save(c, out);
    }

    /** Righe degli ultimi 7 giorni, senza quelle lasciate con «Lascia». */
    static List<String[]> recent(Context c) {
        long now = System.currentTimeMillis();
        String keep = "," + U.prefs(c).getString(K_KEEP, "") + ",";
        List<String[]> out = new ArrayList<String[]>();
        for (String[] r : rows(c)) {
            long t = 0;
            try { t = Long.parseLong(r[2]); } catch (Exception ignored) {}
            if (now - t > WEEK || keep.contains("," + r[0] + "/" + r[1] + ",")) continue;
            out.add(r);
        }
        return out;
    }

    static void keep(Context c, String pkg, String channel) {
        String k = U.prefs(c).getString(K_KEEP, "");
        U.prefs(c).edit().putString(K_KEEP, k.length() == 0 ? pkg + "/" + channel : k + "," + pkg + "/" + channel).apply();
    }

    private static List<String[]> rows(Context c) {
        List<String[]> out = new ArrayList<String[]>();
        for (String line : U.prefs(c).getString(KEY, "").split("\n")) {
            String[] r = line.split("\\|", -1);
            if (r.length >= 4) out.add(r);
        }
        return out;
    }

    private static void save(Context c, List<String[]> rows) {
        StringBuilder b = new StringBuilder();
        for (String[] r : rows) b.append(r[0]).append('|').append(r[1]).append('|').append(r[2]).append('|').append(r[3]).append('\n');
        U.prefs(c).edit().putString(KEY, b.toString()).apply();
    }

    /** Apre la pagina di quella categoria di notifiche (Android 8+), altrimenti quella dell'app. */
    static void openSettings(Context c, String pkg, String channel) {
        if (channel != null && channel.length() > 0) {
            Intent i = new Intent("android.settings.CHANNEL_NOTIFICATION_SETTINGS");
            i.putExtra("android.provider.extra.APP_PACKAGE", pkg);
            i.putExtra("android.provider.extra.CHANNEL_ID", channel);
            if (U.start(c, i)) return;
        }
        Intent a = new Intent("android.settings.APP_NOTIFICATION_SETTINGS");
        a.putExtra("android.provider.extra.APP_PACKAGE", pkg);
        a.putExtra("app_package", pkg);
        a.putExtra("app_uid", 0);
        if (U.start(c, a)) return;
        U.start(c, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
    }
}
