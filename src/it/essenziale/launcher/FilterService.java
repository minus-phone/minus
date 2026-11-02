package it.essenziale.launcher;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Vibrator;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Il "guardiano dell'attenzione": riceve ogni notifica e decide il livello.
 * 1 = passa subito (resta visibile, vibra e suona); 2 = In attesa; 3 = Altro.
 * Le notifiche di livello 2 e 3 vengono tolte dalla tendina e conservate in Store.
 */
public class FilterService extends NotificationListenerService implements Store.Listener {

    private static final int STATUS_ID = 1, SESSION_ID = 2;
    private static final String ACT_SESSION_OK = "it.essenziale.launcher.SESSION_OK", ACT_SESSION_GONE = "it.essenziale.launcher.SESSION_GONE";
    private final android.os.Handler handler = new android.os.Handler();
    /** In questa sessione l'avviso è già arrivato (o è stato scorso via). */
    private boolean sessionAlerted;
    /** La sessione è stata aperta da questo processo: un inizio salvato senza di lei è vecchio. */
    private boolean sessionLive;
    private final Runnable sessionCheck = new Runnable() { public void run() { checkSession(); } };
    private static final int IGNORE = Rules.IGNORE, DROP = Rules.DROP, PASS = Rules.PASS;

    private static final Pattern URGENT = Pattern.compile(
            "(?i)(codice|code|otp|password monouso|verific|verification|autorizz|authoriz|approv|" +
            "conferma (il |l'|un )?(pagamento|acquisto|accesso|operazione)|3d ?secure|securecode|" +
            "pagamento (riuscito|effettuato|confermato)|sei in arrivo|il tuo autista)");

    private static final String[] DIALERS = {
            "com.google.android.dialer", "com.android.dialer", "com.android.server.telecom", "com.android.phone"};

    private static final String[] ALWAYS_PASS_PKGS = {
            "com.google.android.apps.walletnfcrel", "com.google.android.apps.maps",
            "com.google.android.deskclock", "com.android.deskclock"};

    private final Map<String, Long> lastPass = new HashMap<String, Long>();
    /** L'ultima notifica classificata era un tuo messaggio (risposta dalla notifica): niente vibrazione. */
    private boolean selfReply;

    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(a)) {
                LockActivity.showIfLocked(c);
                android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE);
                if (km != null && !km.isKeyguardLocked()) unlocked();
            } else if (Intent.ACTION_USER_PRESENT.equals(a)) unlocked();
            else if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                endSession();
                Night.screenOff(c);
                Scope.stopGuard();
                if (Scope.active(c)) Scope.end(c, "schermo spento"); // spegnere lo schermo chiude lo scopo
            }
            else if (ACT_SESSION_OK.equals(a)) postSession(false);
            else if (ACT_SESSION_GONE.equals(a)) sessionAlerted = true;
            if (!ACT_SESSION_OK.equals(a) && !ACT_SESSION_GONE.equals(a)) updateStatus();
        }
    };

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Store.addListener(this);
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(ACT_SESSION_OK);
        f.addAction(ACT_SESSION_GONE);
        registerReceiver(screenOn, f);
        if (U.prefs(this).getBoolean(U.K_SILENCE, false)) applySilence(this, true);
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (pm != null && pm.isInteractive() && km != null && !km.isKeyguardLocked()) { startSession(); Scope.startGuard(this); }
        else endSession();
        // Ripulisce ciò che era già in tendina all'avvio del filtro
        try {
            StatusBarNotification[] all = getActiveNotifications();
            if (all != null) for (StatusBarNotification s : all) handle(s, false);
        } catch (Exception ignored) {}
        updateStatus();
    }

    /** Chiamato da Android 7+ (stesso nome del metodo di sistema). */
    public void onListenerDisconnected() {
        handler.removeCallbacks(sessionCheck);
        Store.removeListener(this);
        try { unregisterReceiver(screenOn); } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() {
        onListenerDisconnected();
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        handle(sbn, true);
    }

    @Override
    public void onChanged() { updateStatus(); }

    // ---------------------------------------------------------------------

    private void handle(StatusBarNotification sbn, boolean live) {
        // 0.9: chi tiene un'icona fissa nella barra (Essenziale non può toglierla: la propone silenziosa)
        try {
            Notification sn = sbn.getNotification();
            if (live && !getPackageName().equals(sbn.getPackageName())) AppIcons.save(this, sbn.getPackageName(), sn); // 0.13
            if (Sticky.isSticky(sn) && !getPackageName().equals(sbn.getPackageName())) {
                CharSequence st = sn.extras == null ? null : sn.extras.getCharSequence(Notification.EXTRA_TITLE);
                Sticky.seen(this, sbn.getPackageName(), Sticky.channel(sn), st == null ? "" : st.toString());
            }
        } catch (Exception ignored) {}
        int level;
        try { level = classify(sbn); } catch (Exception e) { level = IGNORE; }
        if (level == IGNORE) return;
        if (level == PASS) {
            lastPass.put(sbn.getPackageName(), System.currentTimeMillis());
            if (live && !selfReply) alert();
            return;
        }
        if (level == DROP) {
            Long t = lastPass.get(sbn.getPackageName());
            if (t == null || System.currentTimeMillis() - t > 15000) cancelNotification(sbn.getKey());
            return;
        }
        Notification n = sbn.getNotification();
        Store.Item it = new Store.Item();
        it.id = sbn.getKey();
        it.pkg = sbn.getPackageName();
        it.app = appLabel(sbn.getPackageName());
        String[] tt = titleAndText(n);
        it.title = tt[0];
        it.text = tt[1];
        it.time = sbn.getPostTime();
        it.level = level;
        if (TextUtils.isEmpty(it.title) && TextUtils.isEmpty(it.text)) { cancelNotification(sbn.getKey()); return; }
        Store.put(this, it, n.contentIntent);
        cancelNotification(sbn.getKey());
    }

    /** La decisione vera è in Rules.decide (pura, coperta dal test di non regressione). */
    private int classify(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        Rules.Note note = new Rules.Note();
        note.pkg = sbn.getPackageName();
        note.ownPkg = getPackageName();
        note.category = n.category;
        note.flags = n.flags;
        String[] tt = titleAndText(n);
        note.title = tt[0] == null ? "" : tt[0];
        note.text = tt[1] == null ? "" : tt[1];
        Bundle ex = n.extras;
        note.group = ex != null && (ex.getBoolean("android.isGroupConversation", false)
                || !TextUtils.isEmpty(ex.getCharSequence("android.conversationTitle")));
        // 0.6.1: gruppi di WhatsApp e Telegram non segnati come tali (titolo = gruppo, testo = «Mittente: …»)
        // Solo senza stile chat (lì l'app dice già se è un gruppo) e mai per le persone scelte
        if (!note.group && (ex == null || ex.getParcelableArray("android.messages") == null)
                && !isPerson(note.title) && Rules.looksLikeGroup(note.pkg, note.title, note.text)) {
            note.group = true;
            note.title = Rules.groupTitle(note.title);
        }
        note.appLabel = appLabel(note.pkg);
        if (note.group && (n.flags & Rules.FLAG_GROUP_SUMMARY) == 0) U.seenGroup(this, Rules.groupName(note.title), note.appLabel);
        selfReply = false;
        // 0.4: conversazioni recenti e aperte. Se l'ultimo messaggio è tuo, le hai risposto dalla notifica.
        String code = Rules.chatCode(note.pkg);
        if (code != null && (n.flags & Rules.FLAG_GROUP_SUMMARY) == 0 && note.title.length() > 0
                && (n.flags & (Rules.FLAG_ONGOING_EVENT | Rules.FLAG_FOREGROUND_SERVICE | Rules.FLAG_NO_CLEAR)) == 0) {
            selfReply = lastIsMine(ex);
            long t = Math.min(sbn.getPostTime(), System.currentTimeMillis());
            Convs.remember(code, note.title, note.pkg, n.contentIntent);
            if (!note.group) Nums.remember(this, note.title, n); // 0.13.8: per riconoscere la stessa persona dal numero
            // 0.6.1: se tra i messaggi della notifica ce n'è uno tuo (anche scritto dentro l'app),
            // la conversazione è aperta per 12 ore da quel messaggio
            long mine = lastMineTime(ex);
            if (selfReply) Convs.open(this, code, note.title, note.group, t);
            else {
                Convs.seen(this, code, note.title, note.group, t);
                if (mine > 0 && System.currentTimeMillis() - mine < Rules.OPEN_MS) Convs.open(this, code, note.title, note.group, mine);
            }
        }
        int level = Rules.decide(note, U.ruleConfig(this));
        if (code != null && (n.flags & Rules.FLAG_GROUP_SUMMARY) == 0) log(this, note, level);
        return level;
    }

    /** 0.6.2: le ultime 40 notifiche di chat viste (solo app, nome e decisione, mai il testo). */
    static synchronized void log(Context c, Rules.Note note, int level) {
        String lv = level == Rules.PASS ? "passa" : level == Rules.WAITING ? "Per te" : level == Rules.OTHER ? "Altro" : level == Rules.DROP ? "riassunto" : "ignorata";
        String line = System.currentTimeMillis() + " | " + note.appLabel.replace("|", "/") + " | " + note.title.replace("|", "/").replace("\n", " ")
                + " | " + (note.group ? "gruppo" : "persona") + " | " + lv;
        String old = U.prefs(c).getString(K_LOG, "");
        String[] rows = old.split("\n");
        StringBuilder b = new StringBuilder(line).append("\n");
        for (int i = 0; i < rows.length && i < 39; i++) if (rows[i].trim().length() > 0) b.append(rows[i]).append("\n");
        U.prefs(c).edit().putString(K_LOG, b.toString()).apply();
    }

    static final String K_LOG = "seen_log";

    private boolean isPerson(String title) {
        String t = Rules.groupName(title).toLowerCase(Locale.ITALY);
        for (Rules.Person p : Rules.people(U.cfg(this, U.K_CONTACTS, U.DEF_CONTACTS))) if (p.name.toLowerCase(Locale.ITALY).equals(t)) return true;
        return false;
    }

    /** Ora del tuo messaggio più recente tra quelli della notifica, o 0 (0.6.1). */
    static long lastMineTime(Bundle ex) {
        if (ex == null) return 0;
        try {
            Parcelable[] msgs = ex.getParcelableArray("android.messages");
            if (msgs == null) return 0;
            CharSequence me = ex.getCharSequence("android.selfDisplayName");
            long best = 0;
            for (Parcelable p : msgs) {
                if (!(p instanceof Bundle)) continue;
                Bundle m = (Bundle) p;
                if (m.getCharSequence("text") == null) continue;
                CharSequence sender = m.getCharSequence("sender");
                boolean mine = sender == null ? m.getParcelable("sender_person") == null : me != null && me.toString().equals(sender.toString());
                if (mine) best = Math.max(best, m.getLong("time", 0));
            }
            return best;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 0.6.1: Android 8+ dice perché una notifica è sparita. Se l'hai toccata (motivo 1), stai
     * aprendo quella chat: la conversazione resta aperta per 12 ore, come da Notifiche di Essenziale.
     */
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap, int reason) {
        try {
            if (reason != 1) return;
            String code = Rules.chatCode(sbn.getPackageName());
            if (code == null) return;
            Notification n = sbn.getNotification();
            if ((n.flags & Rules.FLAG_GROUP_SUMMARY) != 0) return;
            String[] tt = titleAndText(n);
            Bundle ex = n.extras;
            boolean group = ex != null && (ex.getBoolean("android.isGroupConversation", false)
                    || !TextUtils.isEmpty(ex.getCharSequence("android.conversationTitle")));
            String title = tt[0];
            if (!group && (ex == null || ex.getParcelableArray("android.messages") == null) && !isPerson(tt[0])
                    && Rules.looksLikeGroup(sbn.getPackageName(), tt[0], tt[1])) { group = true; title = Rules.groupTitle(tt[0]); }
            if (title != null && title.length() > 0) Convs.open(this, code, title, group);
        } catch (Exception ignored) {}
    }

    /** Nei messaggi in stile chat, i tuoi non hanno mittente (o hanno il tuo nome). */
    static boolean lastIsMine(Bundle ex) {
        if (ex == null) return false;
        try {
            Parcelable[] msgs = ex.getParcelableArray("android.messages");
            if (msgs == null || msgs.length == 0 || !(msgs[msgs.length - 1] instanceof Bundle)) return false;
            Bundle last = (Bundle) msgs[msgs.length - 1];
            if (last.getCharSequence("text") == null) return false;
            CharSequence sender = last.getCharSequence("sender");
            if (sender == null) return last.getParcelable("sender_person") == null;
            CharSequence me = ex.getCharSequence("android.selfDisplayName");
            return me != null && me.toString().equals(sender.toString());
        } catch (Exception e) {
            return false;
        }
    }

    static String[] titleAndText(Notification n) {
        Bundle ex = n.extras;
        String title = "", text = "";
        if (ex == null) return new String[]{title, text};
        CharSequence conv = ex.getCharSequence("android.conversationTitle");
        CharSequence t = ex.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
        CharSequence tx = ex.getCharSequence(Notification.EXTRA_TEXT);
        title = t == null ? "" : t.toString();
        text = big != null ? big.toString() : (tx == null ? "" : tx.toString());
        // MessagingStyle: ultimo messaggio con mittente
        Parcelable[] msgs = ex.getParcelableArray("android.messages");
        if (msgs != null && msgs.length > 0 && msgs[msgs.length - 1] instanceof Bundle) {
            Bundle last = (Bundle) msgs[msgs.length - 1];
            CharSequence mt = last.getCharSequence("text");
            CharSequence ms = last.getCharSequence("sender");
            if (mt != null) text = (conv != null && ms != null ? ms + ": " : "") + mt;
            if (msgs.length > 1) text = text + "  (+" + (msgs.length - 1) + ")";
        }
        if (conv != null) title = conv.toString();
        return new String[]{title, text};
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return String.valueOf(pm.getApplicationLabel(ai));
        } catch (Exception e) {
            return pkg;
        }
    }

    /** Avviso per ciò che passa: vibrazione e suono, anche con il silenzio attivo. */
    private void alert() {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build();
            if (v != null) v.vibrate(new long[]{0, 180, 120, 180}, -1, aa);
            Ringtone r = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            if (r != null) { r.setAudioAttributes(aa); r.play(); }
        } catch (Exception ignored) {}
    }

    /** 0.10: allo sblocco la sessione, il controllo delle app con pausa e, di notte, la schermata ridotta. */
    private void unlocked() {
        Sblocco.unlocked(this); // 0.12: sblocchi senza scopo (spento di default)
        startSession();
        Scope.startGuard(this);
        if (Night.onUnlock(this)) {
            Intent n = new Intent(this, NightActivity.class);
            n.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            U.start(this, n);
        }
    }

    // ---------- Tempo dall'ultimo sblocco (0.5) ----------

    private void startSession() {
        if (sessionLive && Session.start(this) > 0) { scheduleCheck(); return; } // già in corso
        sessionLive = true;
        U.prefs(this).edit().putLong(Session.K_START, System.currentTimeMillis()).apply();
        sessionAlerted = false;
        scheduleCheck();
        handler.removeCallbacks(pillTick);
        handler.postDelayed(pillTick, 1000L);
    }

    /** 0.9: la pillola sopra le app si aggiorna ogni 30 secondi finché lo schermo è acceso. */
    private final Runnable pillTick = new Runnable() {
        public void run() {
            if (!sessionLive) { Pill.hide(); return; }
            Pill.setPaused(sessionPaused());
            Pill.update(FilterService.this);
            handler.postDelayed(this, 30000L);
        }
    };

    private void endSession() {
        handler.removeCallbacks(sessionCheck);
        handler.removeCallbacks(pillTick);
        Pill.hide();
        sessionLive = false;
        U.prefs(this).edit().putLong(Session.K_START, 0).apply();
        sessionAlerted = false;
        try { ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(SESSION_ID); } catch (Exception ignored) {}
    }

    private void scheduleCheck() {
        handler.removeCallbacks(sessionCheck);
        long start = Session.start(this);
        if (start <= 0 || !Session.on(this)) return;
        long due = start + Session.threshold(this) * 60000L - System.currentTimeMillis();
        handler.postDelayed(sessionCheck, Math.max(1000L, due));
    }

    private void checkSession() {
        long start = Session.start(this);
        if (start <= 0 || !Session.on(this) || sessionAlerted) return;
        if (Rules.sessionMinutes(start, System.currentTimeMillis(), Session.threshold(this)) < 0) { scheduleCheck(); return; }
        if (sessionPaused()) { handler.postDelayed(sessionCheck, 60000L); return; }
        sessionAlerted = true;
        if (Pill.active(this)) return; // un segnale alla volta: c'è già la pillola
        postSession(true);
    }

    /** In chiamata o in navigazione il tempo non viene fatto notare. */
    private boolean sessionPaused() {
        try {
            StatusBarNotification[] all = getActiveNotifications();
            if (all != null) for (StatusBarNotification s : all)
                if (Rules.pausesSession(s.getPackageName(), s.getNotification().category, s.getNotification().flags)) return true;
        } catch (Exception ignored) {}
        return false;
    }

    /** L'avviso (alert = una vibrazione breve e in primo piano) o, dopo "Ok", lo stesso in silenzio nella tendina. */
    private void postSession(boolean alert) {
        try {
            long start = Session.start(this);
            if (start <= 0) return;
            int min = Math.max(0, (int) ((System.currentTimeMillis() - start) / 60000L));
            Intent home = new Intent(this, MainActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent homePi = PendingIntent.getActivity(this, 21, home, PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent okPi = PendingIntent.getBroadcast(this, 22, new Intent(ACT_SESSION_OK).setPackage(getPackageName()), PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent gonePi = PendingIntent.getBroadcast(this, 23, new Intent(ACT_SESSION_GONE).setPackage(getPackageName()), PendingIntent.FLAG_UPDATE_CURRENT);
            Notification.Builder b = new Notification.Builder(this)
                    .setSmallIcon(Icon.createWithResource("android", android.R.drawable.ic_lock_idle_alarm))
                    .setContentTitle(alert ? "Telefono acceso da " + min + " min" : "Acceso dall'ultimo sblocco")
                    .setContentText(alert ? "Dall'ultimo sblocco, senza pause." : "Si azzera quando spegni lo schermo.")
                    .setWhen(start)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setOnlyAlertOnce(!alert)
                    .setDefaults(0)
                    .setSound(null)
                    .setVibrate(null)
                    .setCategory(Notification.CATEGORY_REMINDER)
                    .setPriority(alert ? Notification.PRIORITY_HIGH : Notification.PRIORITY_LOW)
                    .setContentIntent(homePi)
                    .setDeleteIntent(gonePi);
            if (alert) {
                b.addAction(new Notification.Action.Builder(null, "Torna alla home", homePi).build());
                b.addAction(new Notification.Action.Builder(null, "Ok", okPi).build());
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (v != null) v.vibrate(120);
            }
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(SESSION_ID, b.build());
        } catch (Exception ignored) {}
    }

    /** La notifica fissa che fa da schermata di blocco: prossimo impegno e numero in attesa. */
    void updateStatus() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            // Con la schermata di blocco di Essenziale la notifica fissa non serve più
            if (U.prefs(this).getBoolean(U.K_LOCK, true)) { nm.cancel(STATUS_ID); return; }
            int waiting = Store.count(this, Store.WAITING);
            String next = U.nextEvent(this);
            String title = next != null ? next : "Nessun impegno in vista";
            String text = waiting == 0 ? "Niente in attesa" : (waiting == 1 ? "1 in attesa" : waiting + " in attesa");
            Intent open = new Intent(this, InboxActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(this)
                    .setSmallIcon(Icon.createWithResource("android", android.R.drawable.ic_menu_today))
                    .setContentTitle(title)
                    .setContentText(text)
                    .setOngoing(true)
                    .setShowWhen(false)
                    .setOnlyAlertOnce(true)
                    .setDefaults(0)
                    .setSound(null)
                    .setVibrate(null)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .setPriority(Notification.PRIORITY_DEFAULT)
                    .setContentIntent(pi)
                    .build();
            nm.notify(STATUS_ID, n);
        } catch (Exception ignored) {}
    }

    /**
     * Silenzio di default: modalità "Solo priorità" di Android, con chiamate dai preferiti,
     * chiamate ripetute, sveglie e media consentiti. Tutto il resto lo filtra l'app.
     */
    static boolean applySilence(Context c, boolean on) {
        NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
        if (!nm.isNotificationPolicyAccessGranted()) return false;
        try {
            if (on) {
                int cats = NotificationManager.Policy.PRIORITY_CATEGORY_CALLS
                        | NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS
                        | 32 /* ALARMS */ | 64 /* MEDIA */;
                nm.setNotificationPolicy(new NotificationManager.Policy(cats,
                        NotificationManager.Policy.PRIORITY_SENDERS_STARRED,
                        NotificationManager.Policy.PRIORITY_SENDERS_STARRED));
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
            } else {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
