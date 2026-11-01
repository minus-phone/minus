package it.essenziale.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Helper condivisi: colori, layout, configurazione, app, calendario. */
public final class U {
    private U() {}

    // Palette dei wireframe. 0.10: non più costanti, cambiano con il tema (Aspetto)
    public static int BG = Color.parseColor("#F3F1EB");
    public static int INK = Color.parseColor("#171716");
    public static int MUTED = Color.parseColor("#5C5A55");
    public static int LINE = Color.parseColor("#D8D5CC");
    public static int CHIP = Color.parseColor("#E7E4DB");
    public static int SURFACE = Color.parseColor("#FBFAF6");                   // schede e campi
    public static int BORDER = Color.parseColor("#8A877F");    // bordi di chip e campi
    public static int TRACK_OFF = Color.parseColor("#8A877F"); // interruttore spento
    public static boolean dark = false;
    public static float textScale = 1f;
    public static int themeStamp = 0;

    public static final String K_THEME = "theme", K_TEXT_BIG = "text_big";
    public static final String THEME_LIGHT = "chiaro", THEME_DARK = "scuro", THEME_AUTO = "automatico";

    /** Applica tema e dimensione del testo scelti in Aspetto. Restituisce true se è cambiato qualcosa. */
    public static boolean applyTheme(Context c) {
        fonts(c);
        String t = prefs(c).getString(K_THEME, THEME_LIGHT);
        boolean d = THEME_DARK.equals(t);
        if (THEME_AUTO.equals(t)) {
            int ui = c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            d = ui == android.content.res.Configuration.UI_MODE_NIGHT_YES || Night.active(c);
        }
        float sc = prefs(c).getBoolean(K_TEXT_BIG, false) ? 1.15f : 1f;
        boolean changed = d != dark || sc != textScale;
        dark = d;
        textScale = sc;
        BG = Color.parseColor(d ? "#121211" : "#F3F1EB");
        INK = Color.parseColor(d ? "#E9E7E1" : "#171716");
        MUTED = Color.parseColor(d ? "#A19F98" : "#5C5A55");
        LINE = Color.parseColor(d ? "#33322F" : "#D8D5CC");
        CHIP = Color.parseColor(d ? "#2A2927" : "#E7E4DB");
        SURFACE = Color.parseColor(d ? "#1C1C1A" : "#FBFAF6");
        BORDER = Color.parseColor(d ? "#6E6D68" : "#8A877F"); // 0.11: almeno 3:1 su fondo e campi (WCAG 1.4.11)
        TRACK_OFF = Color.parseColor(d ? "#6E6D68" : "#8A877F");
        ALERT = Color.parseColor(d ? "#E39A72" : "#A4461C");
        if (changed) themeStamp++;
        return changed;
    }
    public static int ALERT = Color.parseColor("#A4461C"); // scaduto, contrasto AA su BG (chiaro e scuro)
    public static final int DARK = Color.parseColor("#121211");
    public static final int ON_DARK = Color.parseColor("#E9E7E1");
    public static final int ON_DARK_MUTED = Color.parseColor("#A19F98");

    // ---------- Layout ----------
    /** Nome dell'app come appare sul telefono (0.9). */
    public static String appLabel(Context c, String pkg) {
        try {
            android.content.pm.PackageManager pm = c.getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }

    public static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    // ---------- 0.13: carta e inchiostro. Atkinson Hyperlegible per leggere, Literata per titoli e ore ----------
    public static Typeface SANS = Typeface.SANS_SERIF, SANS_B = Typeface.DEFAULT_BOLD, SERIF = Typeface.SERIF, SERIF_M = Typeface.SERIF, SERIF_I = Typeface.SERIF;
    private static boolean fontsLoaded;

    public static void fonts(Context c) {
        if (fontsLoaded) return;
        try {
            android.content.res.AssetManager am = c.getApplicationContext().getAssets();
            SANS = Typeface.createFromAsset(am, "fonts/atkinson-regular.ttf");
            SANS_B = Typeface.createFromAsset(am, "fonts/atkinson-bold.ttf");
            SERIF = Typeface.createFromAsset(am, "fonts/literata-regular.ttf");
            SERIF_M = Typeface.createFromAsset(am, "fonts/literata-medium.ttf");
            SERIF_I = Typeface.createFromAsset(am, "fonts/literata-italic-medium.ttf");
        } catch (Exception ignored) {}
        fontsLoaded = true;
    }

    /** Titolo in Literata (Oggi, titoli delle schermate). */
    public static TextView title(Context c, String s, float sp, int color) {
        TextView t = text(c, s, sp, color, false);
        t.setTypeface(SERIF_M);
        t.setLetterSpacing(-0.01f);
        return t;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        fonts(c);
        TextView t = new TextView(c);
        t.setTypeface(bold ? SANS_B : SANS);
        t.setText(brand(s));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * textScale);
        t.setTextColor(color);
        return t;
    }

    public static TextView label(Context c, String s) {
        TextView t = text(c, s.toUpperCase(Locale.ITALY), 12, MUTED, false);
        t.setLetterSpacing(0.06f);
        t.setPadding(0, dp(c, 14), 0, dp(c, 4));
        return t;
    }

    public static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static GradientDrawable box(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) g.setStroke(dp(c, 1.5f), stroke);
        return g;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1))));
        return v;
    }

    public static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams full() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** Intestazione con "‹ Home" e titolo per le schermate secondarie. */
    public static LinearLayout screen(final Activity a, String title) {
        LinearLayout root = col(a);
        root.setBackgroundColor(BG);
        root.setPadding(dp(a, 20), dp(a, 16), dp(a, 20), dp(a, 16));
        // 0.6: la freccia dice dove torna (dalle aree di Impostazioni, il nome dell'area)
        String bl = a.getIntent() == null ? null : a.getIntent().getStringExtra("back");
        TextView back = text(a, "‹  " + (bl == null ? "Home" : bl), 16, INK, false);
        back.setMinHeight(dp(a, 44));
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { a.finish(); }
        });
        root.addView(back);
        TextView t = title(a, title, 28, INK);
        t.setPadding(0, dp(a, 4), 0, dp(a, 6));
        root.addView(t);
        return root;
    }

    /** Interruttore disegnato come nei wireframe (0.6: condiviso). */
    public static View toggle(Context c, boolean on) {
        LinearLayout sw = row(c);
        sw.setGravity(on ? Gravity.END | Gravity.CENTER_VERTICAL : Gravity.START | Gravity.CENTER_VERTICAL);
        sw.setPadding(dp(c, 3), dp(c, 3), dp(c, 3), dp(c, 3));
        sw.setBackground(box(c, on ? INK : TRACK_OFF, 0, 13));
        View knob = new View(c);
        knob.setBackground(box(c, BG, 0, 10));
        sw.addView(knob, new LinearLayout.LayoutParams(dp(c, 20), dp(c, 20)));
        return sw;
    }

    // ---------- Configurazione ----------
    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("essenziale", Context.MODE_PRIVATE);
    }

    public static final String K_CONTACTS = "contacts";
    public static final String K_SHOP = "shop";
    public static final String K_MONEY = "money";
    public static final String K_WAITING_APPS = "waiting_apps";
    public static final String K_MYNAME = "myname";
    public static final String K_SILENCE = "silence";
    public static final String K_LOCK = "lockscreen";
    public static final String K_GROUPS = "groups";          // nome | sempre/silenzio (0.5: menzioni/attesa)
    public static final String K_SEEN_GROUPS = "seen_groups"; // nome | app | ultima volta
    public static final String K_RW_APPS = "rw_apps";        // app in Conversazioni (era Leggi/scrivi)
    public static final String K_PAUSE = "pause_pkgs";       // pacchetti con la pausa
    public static final String K_CAL_SEL = "cal_shown";      // id dei calendari mostrati, separati da virgola

    public static final String DEF_CONTACTS =
            "# Sezione | Nome | App (WA, TG, SMS, TEL, MAIL) | Numero con prefisso o username Telegram\n" +
            "Sempre | Partner | WA | +39...\n" +
            "Sempre | Mamma | WA | +39...\n" +
            "Sempre | Sorella | WA | +39...\n" +
            "Gruppi | Gruppo famiglia | WA |\n" +
            "Frequenti | Contatto | TG | username\n" +
            "Email | Posta | MAIL |\n";

    public static final String DEF_SHOP =
            "# Nome dell'app come appare sul telefono. Aggiungi ! per aprirla dopo 5 secondi\n" +
            "dm\nToo Good To Go\nVinted !\nWidilo\n";

    public static final String DEF_MONEY =
            "# Sezione | Nome dell'app\n" +
            "Conti | BNL\nConti | ING\nInvestimenti | Trade Republic\nInvestimenti | Scalable Capital\n";

    public static final String DEF_WAITING_APPS =
            "# App le cui notifiche vanno In attesa (il resto va in Altro)\n" +
            "WhatsApp\nTelegram\nMessaggi\nGmail\nTelefono\nCalendar\nCalendario\nToo Good To Go\nVinted\nMioPediatra\nPagineMediche\n";

    public static String cfg(Context c, String key, String def) {
        // 0.6.1: le persone di esempio non sono mai persone vere
        if (K_CONTACTS.equals(key)) return Rules.stripSamples(prefs(c).getString(key, ""));
        return prefs(c).getString(key, def);
    }

    /** Righe non vuote e non commentate, divise sul carattere |. */
    public static List<String[]> rows(String raw) {
        return Rules.rows(raw);
    }

    /** Nomi delle persone nel gruppo "Sempre" (per il filtro). */
    public static List<String> alwaysNames(Context c) {
        List<String> names = new ArrayList<String>();
        for (String[] r : rows(cfg(c, K_CONTACTS, DEF_CONTACTS))) {
            if (r.length >= 2 && r[0].equalsIgnoreCase("Sempre")) names.add(r[1].toLowerCase(Locale.ITALY));
        }
        return names;
    }

    /** Configurazione del filtro, letta dalle preferenze. */
    public static final String K_SERVICES = "services";
    public static final String DEF_SERVICES =
            "# Servizi: banca, pagamenti, identità digitale. Mai in Altro; codici e accessi passano subito\n" +
            "pkg:bank\npkg:banca\npkg:banking\npkg:poste\npkg:pagopa\npkg:cieid\npkg:authenticator\npkg:paypal\npkg:satispay\n" +
            "pkg:revolut\npkg:fineco\npkg:intesa\npkg:unicredit\npkg:ingdirect\npkg:mediolanum\npkg:widiba\npkg:isybank\n" +
            "pkg:hype\npkg:number26\npkg:bnl\npkg:credem\npkg:bper\npkg:sella\npkg:webank\npkg:latuabanca\npkg:mps\n" +
            "IO\nCieID\nPosteID\nBancoPosta\nSPID\nAuthenticator\nBanca\nBank\n";

    public static Rules.Config ruleConfig(Context c) {
        Rules.Config cfg = new Rules.Config();
        for (String[] r : rows(cfg(c, K_SERVICES, DEF_SERVICES))) cfg.services.add(r[0]);
        cfg.people = Rules.people(cfg(c, K_CONTACTS, DEF_CONTACTS));
        for (String[] r : rows(cfg(c, K_WAITING_APPS, DEF_WAITING_APPS))) cfg.waitingApps.add(r[0]);
        cfg.groups = groupRules(c);
        cfg.groupDest = groupDest(c);
        cfg.open = prefs(c).getBoolean(K_OPEN_ON, true) ? Convs.openMap(c) : null;
        cfg.now = System.currentTimeMillis();
        return cfg;
    }

    public static java.util.Map<String, String> groupRules(Context c) {
        java.util.Map<String, String> m = new java.util.HashMap<String, String>();
        for (String[] r : rows(prefs(c).getString(K_GROUPS, ""))) if (r.length >= 2) m.put(Rules.groupName(r[0]).toLowerCase(Locale.ITALY), r[1]);
        return m;
    }

    public static final String K_GROUP_DEST = "group_dest"; // codice app | notifiche/altro (0.6)
    public static final String K_OPEN_ON = "open_on";        // conversazioni aperte attive (0.6)

    public static java.util.Map<String, String> groupDest(Context c) {
        java.util.Map<String, String> m = new java.util.HashMap<String, String>();
        for (String[] r : rows(prefs(c).getString(K_GROUP_DEST, ""))) if (r.length >= 2) m.put(r[0], r[1]);
        return m;
    }

    public static void setGroupDest(Context c, String code, String dest) {
        java.util.Map<String, String> m = groupDest(c);
        m.put(code, dest);
        StringBuilder b = new StringBuilder();
        for (java.util.Map.Entry<String, String> e : m.entrySet()) b.append(e.getKey()).append(" | ").append(e.getValue()).append("\n");
        prefs(c).edit().putString(K_GROUP_DEST, b.toString()).commit();
    }

    public static void setGroupRule(Context c, String name, String rule) {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<String, String>();
        for (String[] r : rows(prefs(c).getString(K_GROUPS, ""))) if (r.length >= 2) m.put(Rules.groupName(r[0]), r[1]);
        name = Rules.groupName(name).replace("|", "/");
        for (String k : new ArrayList<String>(m.keySet())) if (k.equalsIgnoreCase(name)) m.remove(k);
        if (rule != null) m.put(name, rule);
        StringBuilder b = new StringBuilder();
        for (java.util.Map.Entry<String, String> e : m.entrySet()) b.append(e.getKey()).append(" | ").append(e.getValue()).append("\n");
        prefs(c).edit().putString(K_GROUPS, b.toString()).commit();
    }

    /** Ricorda i gruppi visti nelle notifiche (per la scelta guidata). */
    public static synchronized void seenGroup(Context c, String name, String app) {
        name = Rules.groupName(name);
        if (name.length() < 2) return;
        name = name.replace("|", "/");
        long now = System.currentTimeMillis();
        java.util.Map<String, String[]> m = new java.util.LinkedHashMap<String, String[]>();
        for (String[] r : rows(prefs(c).getString(K_SEEN_GROUPS, ""))) {
            if (r.length < 3) continue;
            r[0] = Rules.groupName(r[0]);
            String k = r[0].toLowerCase(Locale.ITALY);
            String[] old = m.get(k);
            if (old == null || r[2].compareTo(old[2]) > 0) m.put(k, r); // doppioni della 0.4: tengo il più recente
        }
        m.put(name.toLowerCase(Locale.ITALY), new String[]{name, app == null ? "" : app.replace("|", "/"), String.valueOf(now)});
        StringBuilder b = new StringBuilder();
        for (String[] r : m.values()) {
            long t = 0;
            try { t = Long.parseLong(r[2]); } catch (Exception ignored) {}
            if (now - t > 30L * 24 * 3600 * 1000) continue;
            b.append(r[0]).append(" | ").append(r[1]).append(" | ").append(r[2]).append("\n");
        }
        prefs(c).edit().putString(K_SEEN_GROUPS, b.toString()).apply();
    }

    public static String lines(List<String> items) {
        StringBuilder b = new StringBuilder();
        for (String s : items) b.append(s).append("\n");
        return b.toString();
    }

    // ---------- App ----------
    public static class AppInfo {
        public String label, pkg, cls;
    }

    public static List<AppInfo> launchableApps(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        List<AppInfo> out = new ArrayList<AppInfo>();
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            if (ri.activityInfo.packageName.equals(c.getPackageName())) continue;
            AppInfo a = new AppInfo();
            a.label = String.valueOf(ri.loadLabel(pm));
            a.pkg = ri.activityInfo.packageName;
            a.cls = ri.activityInfo.name;
            out.add(a);
        }
        return out;
    }

    /** Trova un'app installata dal nome visibile: prima uguale, poi "contiene". */
    public static AppInfo findApp(Context c, String name) {
        String n = name.toLowerCase(Locale.ITALY).trim();
        List<AppInfo> apps = launchableApps(c);
        for (AppInfo a : apps) if (a.label.toLowerCase(Locale.ITALY).equals(n)) return a;
        for (AppInfo a : apps) if (a.label.toLowerCase(Locale.ITALY).contains(n)) return a;
        String compact = n.replace(" ", "");
        for (AppInfo a : apps) if (a.label.toLowerCase(Locale.ITALY).replace(" ", "").contains(compact)) return a;
        return null;
    }

    public static boolean launch(Context c, AppInfo a) {
        if (a == null) return false;
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setComponent(new ComponentName(a.pkg, a.cls));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        boolean ok = start(c, i);
        if (ok) launched(c, a.pkg);
        return ok;
    }

    public static final String[] SOCIAL_PKGS = {"com.instagram.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
            "com.facebook.katana", "com.facebook.lite", "com.google.android.youtube", "com.twitter.android"};

    public static boolean isSocial(String pkg) {
        for (String s : SOCIAL_PKGS) if (s.equals(pkg)) return true;
        return false;
    }

    /** Pacchetti con la pausa. Se non è mai stata impostata: i social e le app di Shop segnate con "!". */
    public static java.util.Set<String> pausedPkgs(Context c) {
        java.util.Set<String> out = new java.util.HashSet<String>();
        String raw = prefs(c).getString(K_PAUSE, null);
        if (raw != null) {
            for (String[] r : rows(raw)) out.add(r[0]);
            return out;
        }
        for (String s : SOCIAL_PKGS) out.add(s);
        for (String[] r : rows(cfg(c, K_SHOP, DEF_SHOP))) {
            String n = r[r.length - 1];
            if (n.endsWith("!")) {
                AppInfo a = findApp(c, n.replace("!", "").trim());
                if (a != null) out.add(a.pkg);
            }
        }
        return out;
    }

    public static void setPaused(Context c, String pkg, boolean on) {
        java.util.Set<String> s = pausedPkgs(c);
        if (on) s.add(pkg); else s.remove(pkg);
        List<String> l = new ArrayList<String>(s);
        java.util.Collections.sort(l);
        prefs(c).edit().putString(K_PAUSE, "# pacchetti con la pausa\n" + lines(l)).apply();
    }

    public static boolean isPaused(Context c, String pkg) { return pausedPkgs(c).contains(pkg); }

    /** Apre un'app scelta in Essenziale: con la pausa, se attiva per quell'app. */
    public static boolean open(Context c, AppInfo a) {
        if (a == null) return false;
        if (Scope.activeFor(c, a.pkg)) return launch(c, a); // scopo in corso: niente nuova pausa
        if (Scope.level(c, a.pkg) != Scopo.NONE) { // 0.10: anche le app solo di notte
            Intent i = new Intent(c, FrictionActivity.class);
            i.putExtra("pkg", a.pkg);
            i.putExtra("cls", a.cls);
            i.putExtra("label", a.label);
            return start(c, i);
        }
        return launch(c, a);
    }

    public static List<AppInfo> sortedApps(Context c) {
        List<AppInfo> l = launchableApps(c);
        java.util.Collections.sort(l, new java.util.Comparator<AppInfo>() {
            public int compare(AppInfo x, AppInfo y) { return x.label.compareToIgnoreCase(y.label); }
        });
        // niente doppioni (alcune app hanno più icone)
        List<AppInfo> out = new ArrayList<AppInfo>();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (AppInfo a : l) if (seen.add(a.pkg + "/" + a.label)) out.add(a);
        return out;
    }

    /** L'app di chat installata per quel codice (WA, TG, SMS): anche Telegram web, WhatsApp Business, ecc. */
    public static String chatPkg(Context c, String code) {
        if (code == null) return null;
        String[] pref = "WA".equals(code) ? new String[]{"com.whatsapp", "com.whatsapp.w4b"}
                : "TG".equals(code) ? new String[]{"org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram"} : new String[0];
        for (String p : pref) if (c.getPackageManager().getLaunchIntentForPackage(p) != null) return p;
        if ("SMS".equals(code)) {
            String d = android.provider.Telephony.Sms.getDefaultSmsPackage(c);
            if (d != null) return d;
        }
        for (AppInfo a : launchableApps(c)) if (Rules.pkgMatches(code, a.pkg)) return a.pkg;
        return null;
    }

    /**
     * 0.6.2: apre proprio la chat di quella persona passando dalla rubrica. Telegram e WhatsApp
     * aggiungono al contatto una riga «Messaggio a …»: aprirla porta dritti nella chat.
     */
    public static boolean openChatFromContacts(Activity a, String name, String code) {
        if (name == null || code == null) return false;
        if (a.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return false;
        String mime = "TG".equals(code) ? "vnd.android.cursor.item/vnd.org.telegram.messenger.android.profile"
                : "WA".equals(code) ? "vnd.android.cursor.item/vnd.com.whatsapp.profile" : null;
        if (mime == null) return false;
        Cursor cur = null;
        try {
            cur = a.getContentResolver().query(android.provider.ContactsContract.Data.CONTENT_URI,
                    new String[]{android.provider.ContactsContract.Data._ID},
                    android.provider.ContactsContract.Data.MIMETYPE + " = ? AND " + android.provider.ContactsContract.Data.DISPLAY_NAME + " = ?",
                    new String[]{mime, name.trim()}, null);
            if (cur == null || !cur.moveToFirst()) return false;
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(android.content.ContentUris.withAppendedId(android.provider.ContactsContract.Data.CONTENT_URI, cur.getLong(0)), mime);
            return start(a, i);
        } catch (Exception e) {
            return false;
        } finally {
            if (cur != null) cur.close();
        }
    }

    /** 0.6.3: con quali app di chat la persona è in rubrica (WhatsApp, Telegram); altrimenti quella vista. */
    public static List<String> chatAppsOf(Context c, String name, String fallback) {
        List<String> out = detectedChatApps(c, name);
        if (fallback != null && !out.contains(fallback)) out.add(0, fallback);
        if (out.isEmpty()) out.add("WA");
        return out;
    }

    /** Solo le app di chat con cui la persona risulta in rubrica (0.7). */
    public static List<String> detectedChatApps(Context c, String name) {
        List<String> out = new ArrayList<String>();
        if (name != null && c.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            Cursor cur = null;
            try {
                cur = c.getContentResolver().query(android.provider.ContactsContract.Data.CONTENT_URI,
                        new String[]{android.provider.ContactsContract.Data.MIMETYPE},
                        android.provider.ContactsContract.Data.DISPLAY_NAME + " = ?", new String[]{name.trim()}, null);
                while (cur != null && cur.moveToNext()) {
                    String m = cur.getString(0);
                    if (m == null) continue;
                    if (m.contains("com.whatsapp") && !out.contains("WA")) out.add("WA");
                    if (m.contains("org.telegram") && !out.contains("TG")) out.add("TG");
                }
            } catch (Exception ignored) {
            } finally {
                if (cur != null) cur.close();
            }
        }
        return out;
    }

    public static boolean launchPkg(Context c, String pkg) {
        if (pkg == null) return false;
        Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        boolean ok = start(c, i);
        if (ok) launched(c, pkg);
        return ok;
    }

    public static boolean installed(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean start(Context c, Intent i) {
        try {
            if (!(c instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        } catch (SecurityException e) {
            return false;
        }
    }

    public static void toast(Context c, String s) {
        Toast.makeText(c, brand(s), Toast.LENGTH_SHORT).show();
    }

    /** 0.12: il nome si scrive sempre «−minus», in corsivo. */
    public static final String NAME = "\u2212minus";

    /** 0.13: il nome in Literata corsivo, come nel logotipo (prima: corsivo sintetico del carattere di testo). */
    static final class BrandSpan extends android.text.style.MetricAffectingSpan {
        private void apply(android.text.TextPaint p) {
            if (SERIF_I == Typeface.SERIF) { p.setTextSkewX(-0.2f); return; }
            p.setTypeface(SERIF_I);
        }
        @Override public void updateMeasureState(android.text.TextPaint p) { apply(p); }
        @Override public void updateDrawState(android.text.TextPaint p) { apply(p); }
    }

    public static CharSequence brand(String s) {
        if (s == null || !s.contains(NAME)) return s;
        android.text.SpannableString sp = new android.text.SpannableString(s);
        int i = s.indexOf(NAME);
        while (i >= 0) {
            sp.setSpan(new BrandSpan(), i, i + NAME.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            i = s.indexOf(NAME, i + NAME.length());
        }
        return sp;
    }

    public static boolean listenerEnabled(Context c) {
        String flat = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(c.getPackageName());
    }

    // ---------- Calendario ----------
    public static class Event {
        public long begin, end;
        public boolean allDay;
        public String title;
        public long eventId = -1, calId = -1;
        /** inizio e fine dell'istanza così come li dà il calendario (per le eccezioni delle serie) */
        public long instBegin, instEnd;
        public String rrule, calName;
        public int access;
        public boolean repeats() { return rrule != null && rrule.length() > 0; }
        public boolean editable() { return access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR; }
    }

    /** Apre la modifica di un impegno (da home, settimana, blocco). */
    public static Intent editIntent(Context c, Event e) {
        Intent i = new Intent(c, AddActivity.class);
        i.putExtra("event_id", e.eventId);
        i.putExtra("inst_begin", e.instBegin);
        i.putExtra("inst_end", e.instEnd);
        return i;
    }

    public static void openEvent(Activity a, Event e) {
        if (e.eventId < 0) return;
        if (!e.editable()) {
            toast(a, "Calendario di sola lettura: lo apro nel calendario");
            Intent v = new Intent(Intent.ACTION_VIEW, android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.eventId));
            v.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.instBegin);
            v.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.instEnd);
            start(a, v);
            return;
        }
        if (!canWriteCalendar(a)) {
            a.requestPermissions(new String[]{android.Manifest.permission.WRITE_CALENDAR, android.Manifest.permission.READ_CALENDAR}, 77);
            return;
        }
        a.startActivity(editIntent(a, e));
    }

    /**
     * Nasconde la barra di navigazione di Android (riappare scorrendo dal bordo).
     * 0.13.2: in home anche la barra di stato è nascosta e il suo spazio è della home; scorrendo dall'alto
     * torna sopra la home per qualche secondo, senza spostare niente (IMMERSIVE_STICKY). Nelle altre
     * schermate la barra di stato resta sempre, e non c'è più un'opzione «schermo intero».
     */
    public static void immersive(Activity a) {
        boolean home = a instanceof MainActivity;
        a.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | (home ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN : 0)
                        | (dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR));
    }

    // ---------- 0.12: pausa, aperture (0.13: schermo intero tolto, barra di stato sempre visibile) ----------

    public static final String K_PAUSE_SEC = "pause_seconds", K_LAST_LAUNCH = "last_launch",
            K_LAST_LAUNCH_PKG = "last_launch_pkg", K_KEYBOARD = "search_keyboard";

    public static int pauseSeconds(Context c) { return Cosa.pauseSeconds(prefs(c).getInt(K_PAUSE_SEC, 8)); }

    /** Un'app aperta da minus: serve a capire gli sblocchi «senza aprire niente». */
    public static void launched(Context c, String pkg) {
        prefs(c).edit().putLong(K_LAST_LAUNCH, System.currentTimeMillis()).putString(K_LAST_LAUNCH_PKG, pkg).apply();
    }

    /** Casella del task, disegnata a mano: vuota o piena con spunta. */
    public static TextView checkbox(Context c, boolean done, int color) {
        TextView b = text(c, done ? "✓" : "", 12, done ? BG : color, true);
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, 4));
        g.setStroke(dp(c, 1.5f), color);
        g.setColor(done ? color : 0);
        b.setBackground(g);
        return b;
    }

    public static boolean canWriteCalendar(Context c) {
        return c.checkSelfPermission(android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    public static class Cal {
        public long id;
        public String name, account = "", type = "";
        public boolean sync = true;

        public boolean google() { return "com.google".equals(type); }
        public boolean local() { return CalendarContract.ACCOUNT_TYPE_LOCAL.equals(type); }
    }

    /** Dove finisce un impegno salvato in quel calendario, in parole semplici (0.4.2). */
    public static String calNote(Cal k) {
        if (k == null) return "";
        if (k.local()) return "Solo su questo telefono: non si sincronizza e non si vede in Google Calendar";
        if (!k.sync) return "Sincronizzazione spenta: gli impegni restano sul telefono finché non la riaccendi";
        if (k.google()) return "Google · " + k.account + " · si vede anche in Google Calendar";
        String acc = k.account == null || k.account.length() == 0 ? "account " + k.type : k.account;
        return acc + " · si sincronizza, ma Google Calendar mostra solo i calendari Google";
    }

    public static Cal calInfo(Context c, long id) {
        if (id < 0 || !hasCalendar(c)) return null;
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(android.content.ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id),
                    new String[]{CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                            CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.Calendars.SYNC_EVENTS},
                    null, null, null);
            if (cur == null || !cur.moveToFirst()) return null;
            return calFrom(cur);
        } catch (Exception e) {
            return null;
        } finally {
            if (cur != null) cur.close();
        }
    }

    private static Cal calFrom(Cursor cur) {
        Cal k = new Cal();
        k.id = cur.getLong(0);
        k.name = cur.getString(1) == null ? "(senza nome)" : cur.getString(1);
        k.account = cur.getString(2) == null ? "" : cur.getString(2);
        k.type = cur.getString(3) == null ? "" : cur.getString(3);
        k.sync = cur.getInt(4) == 1;
        return k;
    }

    /** Calendari in cui posso scrivere (proprietario o editor). */
    public static List<Cal> writableCalendars(Context c) {
        List<Cal> out = new ArrayList<Cal>();
        if (!hasCalendar(c)) return out;
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,
                    new String[]{CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                            CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.Calendars.SYNC_EVENTS},
                    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + ">=" + CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                            + " AND " + calendarFilter(c, CalendarContract.Calendars._ID, CalendarContract.Calendars.VISIBLE),
                    null, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC");
            List<Cal> others = new ArrayList<Cal>();
            while (cur != null && cur.moveToNext()) {
                Cal k = calFrom(cur);
                if (k.google()) out.add(k); else others.add(k); // prima i calendari Google
            }
            out.addAll(others);
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return out;
    }

    public static boolean insertEvent(Context c, long calId, String title, long start, long end) {
        return insertEvent(c, calId, title, start, end, false, null, null) >= 0;
    }

    /** Impegni da adesso a fine giornata (compresi quelli in corso). */
    public static List<Event> remainingToday(Context c) {
        long now = System.currentTimeMillis();
        List<Event> out = new ArrayList<Event>();
        for (Event e : events(c, startOfDay(0), startOfDay(1))) {
            if (e.allDay) continue;
            if (e.end > now || e.begin >= now) out.add(e);
        }
        return out;
    }

    public static boolean hasCalendar(Context c) {
        return c.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    /** Eventi tra due istanti, ordinati per inizio. Solo calendari visibili. */
    public static List<Event> events(Context c, long from, long to) {
        List<Event> out = new ArrayList<Event>();
        if (!hasCalendar(c)) return out;
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        android.content.ContentUris.appendId(b, from);
        android.content.ContentUris.appendId(b, to);
        String[] proj = {CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.TITLE,
                CalendarContract.Instances.END, CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.RRULE, CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                CalendarContract.Instances.CALENDAR_ACCESS_LEVEL, CalendarContract.Instances.STATUS};
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(b.build(), proj, calendarFilter(c, CalendarContract.Instances.CALENDAR_ID,
                    CalendarContract.Instances.VISIBLE), null, CalendarContract.Instances.BEGIN + " ASC");
            while (cur != null && cur.moveToNext()) {
                if (!cur.isNull(9) && cur.getInt(9) == CalendarContract.Events.STATUS_CANCELED) continue;
                Event e = new Event();
                e.begin = cur.getLong(0);
                e.allDay = cur.getInt(1) == 1;
                e.title = cur.getString(2);
                e.end = cur.getLong(3);
                e.instBegin = e.begin;
                e.instEnd = e.end;
                e.eventId = cur.getLong(4);
                e.calId = cur.getLong(5);
                e.rrule = cur.getString(6);
                e.calName = cur.getString(7);
                e.access = cur.getInt(8);
                if (TextUtils.isEmpty(e.title)) e.title = "(senza titolo)";
                if (e.allDay) {
                    // Gli eventi "tutto il giorno" sono in UTC: riportali alla mezzanotte locale
                    Calendar utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
                    utc.setTimeInMillis(e.begin);
                    Calendar loc = Calendar.getInstance();
                    loc.clear();
                    loc.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH));
                    e.begin = loc.getTimeInMillis();
                    e.end = e.begin + Math.max(1, Math.round((e.instEnd - e.instBegin) / 86400000.0)) * 86400000L;
                }
                out.add(e);
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return out;
    }

    // ---------- Calendario: scelta, lettura e scrittura degli impegni ----------

    /** Calendari da mostrare: quelli scelti in Essenziale, oppure (se mai scelti) i visibili di sistema. */
    static String calendarFilter(Context c, String idCol, String visibleCol) {
        String sel = prefs(c).getString(K_CAL_SEL, null);
        if (sel == null) return visibleCol + "=1";
        StringBuilder b = new StringBuilder();
        for (String s : sel.split(",")) {
            String t = s.trim();
            if (t.matches("\\d+")) { if (b.length() > 0) b.append(","); b.append(t); }
        }
        if (b.length() == 0) b.append("-1");
        return idCol + " IN (" + b + ")";
    }

    public static class CalRow {
        public long id;
        public String name, account, type;
        public boolean visible, sync;
        public int access;
    }

    public static List<CalRow> allCalendars(Context c) {
        List<CalRow> out = new ArrayList<CalRow>();
        if (!hasCalendar(c)) return out;
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,
                    new String[]{CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                            CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE,
                            CalendarContract.Calendars.VISIBLE, CalendarContract.Calendars.SYNC_EVENTS,
                            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL},
                    null, null, CalendarContract.Calendars.ACCOUNT_NAME + " ASC, " + CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC");
            while (cur != null && cur.moveToNext()) {
                CalRow r = new CalRow();
                r.id = cur.getLong(0);
                r.name = cur.getString(1);
                r.account = cur.getString(2);
                r.type = cur.getString(3);
                r.visible = cur.getInt(4) == 1;
                r.sync = cur.getInt(5) == 1;
                r.access = cur.getInt(6);
                if (r.name == null) r.name = "(senza nome)";
                out.add(r);
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return out;
    }

    public static java.util.Set<Long> shownCalendars(Context c) {
        java.util.Set<Long> out = new java.util.HashSet<Long>();
        String sel = prefs(c).getString(K_CAL_SEL, null);
        if (sel == null) {
            for (CalRow r : allCalendars(c)) if (r.visible) out.add(r.id);
            return out;
        }
        for (String s : sel.split(",")) try { out.add(Long.parseLong(s.trim())); } catch (Exception ignored) {}
        return out;
    }

    public static void setShownCalendars(Context c, java.util.Set<Long> ids) {
        StringBuilder b = new StringBuilder();
        for (Long id : ids) { if (b.length() > 0) b.append(","); b.append(id); }
        prefs(c).edit().putString(K_CAL_SEL, b.toString()).apply();
    }

    /** Chiede ad Android di sincronizzare quel calendario (serve per vederne gli impegni). */
    public static boolean setSync(Context c, long calId, boolean on) {
        try {
            android.content.ContentValues v = new android.content.ContentValues();
            v.put(CalendarContract.Calendars.SYNC_EVENTS, on ? 1 : 0);
            if (on) v.put(CalendarContract.Calendars.VISIBLE, 1);
            c.getContentResolver().update(android.content.ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calId), v, null, null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static class EventData {
        public long id = -1, calId = -1, start, end;
        public String title = "", rrule, desc = "", calName, tz;
        public boolean allDay;
    }

    public static EventData loadEvent(Context c, long id) {
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id),
                    new String[]{CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND,
                            CalendarContract.Events.DURATION, CalendarContract.Events.RRULE, CalendarContract.Events.ALL_DAY,
                            CalendarContract.Events.CALENDAR_ID, CalendarContract.Events.DESCRIPTION,
                            CalendarContract.Events.CALENDAR_DISPLAY_NAME, CalendarContract.Events.EVENT_TIMEZONE},
                    null, null, null);
            if (cur == null || !cur.moveToFirst()) return null;
            EventData d = new EventData();
            d.id = id;
            d.title = cur.getString(0) == null ? "" : cur.getString(0);
            d.start = cur.getLong(1);
            d.end = cur.isNull(2) ? 0 : cur.getLong(2);
            String dur = cur.getString(3);
            d.rrule = cur.getString(4);
            d.allDay = cur.getInt(5) == 1;
            d.calId = cur.getLong(6);
            d.desc = cur.getString(7) == null ? "" : cur.getString(7);
            d.calName = cur.getString(8);
            d.tz = cur.getString(9);
            if (d.end == 0) d.end = d.start + parseDuration(dur);
            return d;
        } catch (Exception e) {
            return null;
        } finally {
            if (cur != null) cur.close();
        }
    }

    /** Durata RFC 5545 semplice: P1D, PT1H30M, P3600S, P1W. */
    static long parseDuration(String d) {
        if (d == null) return 3600000L;
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)([WDHMS])").matcher(d.toUpperCase(Locale.US));
            long ms = 0;
            while (m.find()) {
                long n = Long.parseLong(m.group(1));
                char u = m.group(2).charAt(0);
                ms += n * (u == 'W' ? 604800000L : u == 'D' ? 86400000L : u == 'H' ? 3600000L : u == 'M' ? 60000L : 1000L);
            }
            return ms > 0 ? ms : 3600000L;
        } catch (Exception e) {
            return 3600000L;
        }
    }

    /** Mezzanotte UTC dello stesso giorno di calendario (per gli impegni "tutto il giorno"). */
    public static long utcDay(long localDayStart) {
        Calendar loc = Calendar.getInstance();
        loc.setTimeInMillis(localDayStart);
        Calendar utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(loc.get(Calendar.YEAR), loc.get(Calendar.MONTH), loc.get(Calendar.DAY_OF_MONTH));
        return utc.getTimeInMillis();
    }

    /** Riempie orari, durata e ricorrenza in modo valido per il provider del calendario. */
    static void putTimes(android.content.ContentValues v, long start, long end, boolean allDay, String rrule) {
        long s = allDay ? utcDay(start) : start;
        long e = allDay ? utcDay(end) : end;
        if (allDay && e <= s) e = s + 86400000L;
        v.put(CalendarContract.Events.DTSTART, s);
        v.put(CalendarContract.Events.ALL_DAY, allDay ? 1 : 0);
        v.put(CalendarContract.Events.EVENT_TIMEZONE, allDay ? "UTC" : java.util.TimeZone.getDefault().getID());
        if (rrule != null && rrule.length() > 0) {
            v.put(CalendarContract.Events.RRULE, rrule);
            v.put(CalendarContract.Events.DURATION, allDay ? "P" + Math.max(1, (e - s) / 86400000L) + "D" : "P" + Math.max(60, (e - s) / 1000L) + "S");
            v.putNull(CalendarContract.Events.DTEND);
        } else {
            v.putNull(CalendarContract.Events.RRULE);
            v.putNull(CalendarContract.Events.DURATION);
            v.put(CalendarContract.Events.DTEND, e);
        }
    }

    public static long insertEvent(Context c, long calId, String title, long start, long end, boolean allDay, String rrule, String desc) {
        try {
            android.content.ContentValues v = new android.content.ContentValues();
            v.put(CalendarContract.Events.CALENDAR_ID, calId);
            v.put(CalendarContract.Events.TITLE, title);
            if (desc != null) v.put(CalendarContract.Events.DESCRIPTION, desc);
            putTimes(v, start, end, allDay, rrule);
            Uri u = c.getContentResolver().insert(CalendarContract.Events.CONTENT_URI, v);
            return u == null ? -1 : android.content.ContentUris.parseId(u);
        } catch (Exception e) {
            return -1;
        }
    }

    public static boolean updateEvent(Context c, long id, String title, long start, long end, boolean allDay, String rrule, String desc) {
        try {
            android.content.ContentValues v = new android.content.ContentValues();
            v.put(CalendarContract.Events.TITLE, title);
            if (desc != null) v.put(CalendarContract.Events.DESCRIPTION, desc);
            putTimes(v, start, end, allDay, rrule);
            return c.getContentResolver().update(android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), v, null, null) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** "Solo questo": eccezione a una serie. cancel = elimina solo questa volta. */
    public static boolean exception(Context c, long id, long instBegin, boolean cancel, String title, long start, long end, boolean allDay, String desc) {
        try {
            android.content.ContentValues v = new android.content.ContentValues();
            v.put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, instBegin);
            if (cancel) v.put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED);
            else {
                v.put(CalendarContract.Events.TITLE, title);
                if (desc != null) v.put(CalendarContract.Events.DESCRIPTION, desc);
                long s = allDay ? utcDay(start) : start, e = allDay ? utcDay(end) : end;
                if (allDay && e <= s) e = s + 86400000L;
                v.put(CalendarContract.Events.DTSTART, s);
                v.put(CalendarContract.Events.DTEND, e);
                v.put(CalendarContract.Events.ALL_DAY, allDay ? 1 : 0);
                v.put(CalendarContract.Events.EVENT_TIMEZONE, allDay ? "UTC" : java.util.TimeZone.getDefault().getID());
            }
            Uri u = android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, id);
            return c.getContentResolver().insert(u, v) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** "Questo e i successivi": la serie originale finisce prima di questa istanza. */
    public static boolean endSeriesBefore(Context c, long id, String rrule, long instBegin) {
        try {
            android.content.ContentValues v = new android.content.ContentValues();
            v.put(CalendarContract.Events.RRULE, Rec.withUntil(rrule, instBegin - 1000L));
            return c.getContentResolver().update(android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), v, null, null) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean deleteEvent(Context c, long id) {
        try {
            return c.getContentResolver().delete(android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Osserva i cambi del calendario (sincronizzazioni, altre app) e richiama r sul thread principale. */
    public static android.database.ContentObserver calendarObserver(final Runnable r) {
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable debounced = new Runnable() { public void run() { r.run(); } };
        return new android.database.ContentObserver(h) {
            public void onChange(boolean self) { h.removeCallbacks(debounced); h.postDelayed(debounced, 400); }
        };
    }

    public static void watchCalendar(Context c, android.database.ContentObserver o, boolean on) {
        try {
            if (on) c.getContentResolver().registerContentObserver(CalendarContract.CONTENT_URI, true, o);
            else c.getContentResolver().unregisterContentObserver(o);
        } catch (Exception ignored) {}
    }

    public static long startOfDay(int offsetDays) {
        Calendar k = Calendar.getInstance();
        k.set(Calendar.HOUR_OF_DAY, 0);
        k.set(Calendar.MINUTE, 0);
        k.set(Calendar.SECOND, 0);
        k.set(Calendar.MILLISECOND, 0);
        k.add(Calendar.DAY_OF_MONTH, offsetDays);
        return k.getTimeInMillis();
    }

    public static String hhmm(long t) {
        return new java.text.SimpleDateFormat("HH:mm", Locale.ITALY).format(new java.util.Date(t));
    }

    public static String dayLabel(long t) {
        String s = new java.text.SimpleDateFormat("EEE d", Locale.ITALY).format(new java.util.Date(t));
        return s.substring(0, 1).toUpperCase(Locale.ITALY) + s.substring(1);
    }

    public static String eventTime(Event e) {
        return e.allDay ? "tutto il giorno" : hhmm(e.begin);
    }

    /** Il prossimo impegno da adesso a fine giornata di domani, testo pronto. */
    /** Prossima sveglia entro 24 ore: "oggi 6:45" / "domani 6:45", oppure null (0.5.1). */
    public static String nextAlarm(Context c) {
        try {
            android.app.AlarmManager am = (android.app.AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            android.app.AlarmManager.AlarmClockInfo info = am == null ? null : am.getNextAlarmClock();
            if (info == null) return null;
            long t = info.getTriggerTime(), now = System.currentTimeMillis();
            if (t < now || t - now > 24L * 3600 * 1000) return null;
            String h = new java.text.SimpleDateFormat("H:mm", Locale.ITALY).format(new java.util.Date(t));
            return (t < startOfDay(1) ? "oggi " : "domani ") + h;
        } catch (Exception e) {
            return null;
        }
    }

    /** Ora della prossima sveglia di sistema, o -1 (0.6). */
    public static long nextAlarmTime(Context c) {
        try {
            android.app.AlarmManager am = (android.app.AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            android.app.AlarmManager.AlarmClockInfo info = am == null ? null : am.getNextAlarmClock();
            return info == null ? -1 : info.getTriggerTime();
        } catch (Exception e) {
            return -1;
        }
    }

    public static int hourNow() {
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
    }

    /** Piccola icona della sveglia, nel colore del testo, a sinistra (o niente). */
    public static void alarmIcon(TextView t, boolean on, int color) {
        if (!on) { t.setCompoundDrawablesRelative(null, null, null, null); return; }
        try {
            android.graphics.drawable.Drawable d = t.getContext().getDrawable(android.R.drawable.ic_lock_idle_alarm).mutate();
            int s = dp(t.getContext(), 14);
            d.setBounds(0, 0, s, s);
            d.setTint(color);
            t.setCompoundDrawablesRelative(d, null, null, null);
            t.setCompoundDrawablePadding(dp(t.getContext(), 6));
        } catch (Exception ignored) {}
    }

    /** Apre l'elenco delle sveglie dell'orologio. */
    public static boolean openAlarms(Context c) {
        Intent i = new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return start(c, i);
    }

    public static String nextEvent(Context c) {
        long now = System.currentTimeMillis();
        for (Event e : events(c, now - 60000L, startOfDay(2))) {
            if (e.allDay) continue;
            if (e.begin >= now - 60000L) {
                boolean today = e.begin < startOfDay(1);
                return (today ? "" : "Domani ") + hhmm(e.begin) + "  " + e.title;
            }
        }
        return null;
    }
}
