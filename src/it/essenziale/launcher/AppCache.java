package it.essenziale.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 0.25: le app installate e le loro icone restano in memoria. Prima la Ricerca rileggeva tutte le app (nomi e
 * ordine) sul thread principale a ogni apertura, e la salita partiva in ritardo. Si preparano in sottofondo
 * quando si torna in home e si rifanno solo quando un'app si installa, si aggiorna o si toglie.
 */
final class AppCache {
    private AppCache() {}

    private static volatile List<U.AppInfo> apps;
    private static final Map<String, Drawable.ConstantState> ICONS = new ConcurrentHashMap<String, Drawable.ConstantState>();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static boolean registered;

    /** L'elenco se è già pronto, altrimenti null (non legge niente). */
    static List<U.AppInfo> peek() { return apps; }

    /** L'elenco ordinato; se non c'è ancora lo legge adesso (sul thread di chi chiama). */
    static List<U.AppInfo> get(Context c) {
        List<U.AppInfo> l = apps;
        if (l == null) {
            l = java.util.Collections.unmodifiableList(U.sortedApps(c.getApplicationContext()));
            apps = l;
        }
        return l;
    }

    /** Dalla home: prepara l'elenco in sottofondo, se manca. */
    static void warm(Context c) {
        final Context app = c.getApplicationContext();
        register(app);
        if (apps != null) return;
        IO.execute(new Runnable() { public void run() { try { get(app); } catch (Exception ignored) {} } });
    }

    /** L'icona vera dell'app (colori originali), tenuta in memoria; null se non si trova. */
    static Drawable icon(Context c, String pkg, String cls) {
        if (pkg == null) return null;
        String key = cls != null ? pkg + "/" + cls : pkg;
        Drawable.ConstantState cs = ICONS.get(key);
        if (cs == null) {
            try {
                android.content.pm.PackageManager pm = c.getPackageManager();
                Drawable d = cls != null ? pm.getActivityIcon(new android.content.ComponentName(pkg, cls)) : pm.getApplicationIcon(pkg);
                cs = d.getConstantState();
                if (cs == null) return d;
                ICONS.put(key, cs);
            } catch (Exception e) {
                return null;
            }
        }
        return cs.newDrawable(c.getResources());
    }

    static void invalidate() {
        apps = null;
        ICONS.clear();
    }

    private static synchronized void register(final Context app) {
        if (registered) return;
        registered = true;
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        try {
            app.registerReceiver(new BroadcastReceiver() {
                public void onReceive(Context c, Intent i) { invalidate(); warm(c); }
            }, f);
        } catch (Exception e) {
            registered = false;
        }
    }
}
