package it.essenziale.launcher;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * La schermata di blocco di Essenziale: si mostra sopra quella di sistema.
 * La sicurezza resta di Android: "Sblocca" chiede impronta o PIN di sistema.
 */
public class LockActivity extends Activity {

    private static final int MAX_ROWS = 4;

    private TextView clock, date, notif, torchBtn, alarm;
    private LinearLayout next, todo;
    private final Handler handler = new Handler();
    private boolean torchOn = false;
    private String torchId;

    private final Runnable tick = new Runnable() {
        public void run() { refresh(); handler.postDelayed(this, 30000); }
    };

    private final BroadcastReceiver unlocked = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) { finish(); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        getWindow().setStatusBarColor(U.DARK);
        getWindow().setNavigationBarColor(U.DARK);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        LinearLayout root = U.col(this);
        root.setBackgroundColor(U.DARK);
        root.setPadding(U.dp(this, 28), U.dp(this, 36), U.dp(this, 28), U.dp(this, 20));

        clock = U.text(this, "", 56, U.ON_DARK, false);
        clock.setTypeface(Typeface.MONOSPACE);
        root.addView(clock);
        date = U.text(this, "", 16, U.ON_DARK_MUTED, false);
        root.addView(date);
        // 0.5.1: prossima sveglia entro 24 ore, sotto la data
        alarm = U.text(this, "", 15, U.ON_DARK_MUTED, false);
        alarm.setPadding(0, U.dp(this, 6), 0, 0);
        alarm.setMinHeight(U.dp(this, 44));
        alarm.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(alarm);

        View line = new View(this);
        line.setBackgroundColor(0xFF3A3A37);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, U.dp(this, 1));
        llp.topMargin = U.dp(this, 18);
        root.addView(line, llp);

        next = U.col(this);
        root.addView(next, U.full());
        todo = U.col(this);
        root.addView(todo, U.full());

        notif = U.text(this, "", 16, U.ON_DARK, false);
        notif.setPadding(0, U.dp(this, 14), 0, 0);
        notif.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { unlockThen(new Intent(LockActivity.this, InboxActivity.class)); }
        });
        root.addView(notif);

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        // Torcia · Sblocca · Fotocamera
        LinearLayout actions = U.row(this);
        torchBtn = round("Torcia", false);
        torchBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { toggleTorch(); } });
        actions.addView(torchBtn, new LinearLayout.LayoutParams(U.dp(this, 64), U.dp(this, 64)));
        TextView unlock = U.text(this, "Sblocca", 15, U.ON_DARK, true);
        unlock.setGravity(Gravity.CENTER);
        unlock.setMinHeight(U.dp(this, 64));
        unlock.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { unlockThen(null); } });
        actions.addView(unlock, new LinearLayout.LayoutParams(0, U.dp(this, 64), 1f));
        TextView cam = round("Foto", false);
        cam.setContentDescription("Fotocamera");
        cam.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { camera(); } });
        actions.addView(cam, new LinearLayout.LayoutParams(U.dp(this, 64), U.dp(this, 64)));
        root.addView(actions, U.full());

        TextView sos = U.text(this, "Emergenza", 14, U.ON_DARK, true);
        sos.setGravity(Gravity.CENTER);
        sos.setPadding(U.dp(this, 20), 0, U.dp(this, 20), 0);
        sos.setMinHeight(U.dp(this, 44));
        sos.setBackground(U.box(this, U.DARK, 0xFF62625C, 22));
        sos.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { emergency(); } });
        LinearLayout.LayoutParams slp = U.wrap();
        slp.gravity = Gravity.CENTER_HORIZONTAL;
        slp.topMargin = U.dp(this, 14);
        root.addView(sos, slp);

        setContentView(root);

        IntentFilter f = new IntentFilter(Intent.ACTION_USER_PRESENT);
        registerReceiver(unlocked, f);
        findTorch();
    }

    private TextView round(String s, boolean on) {
        TextView t = U.text(this, s, 12, on ? U.DARK : U.ON_DARK, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(U.box(this, on ? U.ON_DARK : 0xFF2A2A28, 0, 32));
        return t;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Pill.resumed(this);
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km != null && !km.isKeyguardLocked()) { finish(); return; }
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
        Pill.pausedActivity(this);
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(unlocked); } catch (Exception ignored) {}
        if (torchOn) setTorch(false);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() { /* resta qui finché non sblocchi */ }

    private void refresh() {
        clock.setText(U.hhmm(System.currentTimeMillis()));
        String d = new java.text.SimpleDateFormat("EEEE d MMMM", Locale.ITALY).format(new java.util.Date());
        date.setText(d.substring(0, 1).toUpperCase(Locale.ITALY) + d.substring(1));
        // 0.6: sveglia di oggi nelle 2 ore prima; dalle 19 quella di domani, o «Nessuna sveglia per domani»
        String al = Quando.lockAlarm(U.hourNow(), System.currentTimeMillis(), U.nextAlarmTime(this), U.startOfDay(1), U.startOfDay(2));
        alarm.setText(al == null ? "" : al);
        alarm.setVisibility(al == null ? View.GONE : View.VISIBLE);
        U.alarmIcon(alarm, al != null, U.ON_DARK);
        alarm.setTextColor(U.ON_DARK);

        next.removeAllViews();
        // 0.12.4: stessa regola della home. Oggi: impegni non finiti, anche quelli di tutto il giorno;
        // Domani compare dalle 17, o prima se oggi non resta niente (né impegni né cose da fare)
        long now = System.currentTimeMillis();
        List<U.Event> ev = new java.util.ArrayList<U.Event>();
        for (U.Event e : U.events(this, U.startOfDay(0), U.startOfDay(1))) if (e.allDay || e.end >= now) ev.add(e);
        List<Tasks.Task> tasks = Tasks.forToday(this, false, false);
        boolean todayEmpty = ev.isEmpty() && tasks.isEmpty();
        if (!ev.isEmpty()) next.addView(darkLabel("Prossimi"));
        else if (todayEmpty) {
            next.addView(darkLabel("Oggi"));
            next.addView(U.text(this, "Niente più in programma", 16, U.ON_DARK_MUTED, false));
        }
        boolean nextMarked = false;
        int shown = 0;
        for (int i = 0; i < ev.size() && i < MAX_ROWS; i++) {
            final U.Event e = ev.get(i);
            boolean strong = !nextMarked && !e.allDay;
            if (strong) nextMarked = true;
            next.addView(eventRow(e, strong));
            shown++;
        }
        if (ev.size() > MAX_ROWS) next.addView(more(ev.size() - MAX_ROWS));
        if (Quando.showTomorrow(U.hourNow(), todayEmpty)) {
            List<U.Event> tom = U.events(this, U.startOfDay(1), U.startOfDay(2));
            int room = Math.max(2, MAX_ROWS - shown);
            next.addView(darkLabel("Domani"));
            if (tom.isEmpty()) next.addView(U.text(this, "Niente in programma", 16, U.ON_DARK_MUTED, false));
            for (int i = 0; i < tom.size() && i < room; i++) next.addView(eventRow(tom.get(i), false));
            if (tom.size() > room) next.addView(more(tom.size() - room));
        }

        todo.removeAllViews();
        if (!tasks.isEmpty()) {
            todo.addView(darkLabel("Da fare"));
            int max = MAX_ROWS - 1;
            for (int i = 0; i < tasks.size() && i < max; i++) {
                Tasks.Task t = tasks.get(i);
                todo.addView(row("☐", t.title, Tasks.dueLabel(t), false));
            }
            if (tasks.size() > max) todo.addView(more(tasks.size() - max));
        }

        if (U.listenerEnabled(this)) {
            int n = Store.count(this, Store.WAITING);
            // 0.5.1: senza notifiche non scrivo niente
            notif.setText(n == 1 ? "1 notifica" : n + " notifiche");
            notif.setVisibility(n == 0 ? View.GONE : View.VISIBLE);
        } else notif.setVisibility(View.GONE);
    }

    private View eventRow(final U.Event e, boolean strong) {
        View r = row(e.allDay ? "giorno" : U.hhmm(e.begin), e.title, null, strong);
        if (e.editable() && e.eventId >= 0) {
            r.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { unlockThen(U.editIntent(LockActivity.this, e)); }
            });
        }
        return r;
    }

    private TextView darkLabel(String s) {
        TextView t = U.text(this, s.toUpperCase(Locale.ITALY), 12, U.ON_DARK_MUTED, false);
        t.setLetterSpacing(0.08f);
        t.setPadding(0, U.dp(this, 14), 0, U.dp(this, 4));
        return t;
    }

    private View row(String left, String title, String right, boolean strong) {
        LinearLayout r = U.row(this);
        r.setPadding(0, U.dp(this, 3), 0, U.dp(this, 3));
        r.setMinimumHeight(U.dp(this, 36));
        TextView l = U.text(this, left, 15, U.ON_DARK, strong);
        l.setTypeface(Typeface.MONOSPACE, strong ? Typeface.BOLD : Typeface.NORMAL);
        l.setMinWidth(U.dp(this, 64));
        r.addView(l);
        TextView t = U.text(this, title, 16, strong ? U.ON_DARK : 0xFFD0D0CA, strong);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!TextUtils.isEmpty(right)) {
            boolean late = "scaduto".equals(right);
            r.addView(U.text(this, right, 12, late ? 0xFFE8B08A : U.ON_DARK_MUTED, late));
        }
        return r;
    }

    private TextView more(int n) {
        TextView t = U.text(this, "+" + n + (n == 1 ? " altro" : " altri"), 13, U.ON_DARK_MUTED, false);
        t.setPadding(U.dp(this, 64), U.dp(this, 2), 0, 0);
        return t;
    }

    // ---------- Azioni ----------

    /** Chiede lo sblocco di sistema; poi, se indicato, apre una schermata. */
    private void unlockThen(final Intent after) {
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (after != null) {
            // Una schermata normale aperta da qui compare subito dopo lo sblocco
            after.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            U.start(this, after);
        }
        boolean asked = false;
        try {
            // Android 8+: KeyguardManager.requestDismissKeyguard(activity, null)
            java.lang.reflect.Method m = KeyguardManager.class.getMethod("requestDismissKeyguard",
                    Activity.class, Class.forName("android.app.KeyguardManager$KeyguardDismissCallback"));
            m.invoke(km, this, null);
            asked = true;
        } catch (Throwable ignored) {}
        if (!asked) finish(); // mostra la schermata di sistema, che chiede impronta o PIN
    }

    private void camera() {
        Intent i = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE);
        if (!U.start(this, i)) U.start(this, new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
    }

    private void emergency() {
        Intent i = new Intent("com.android.phone.EmergencyDialer.DIAL");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!U.start(this, i)) U.start(this, new Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")));
    }

    private void findTorch() {
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                Boolean flash = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                if (flash != null && flash && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) { torchId = id; break; }
            }
        } catch (Exception ignored) {}
        if (torchId == null) torchBtn.setVisibility(View.INVISIBLE);
    }

    private void toggleTorch() { setTorch(!torchOn); }

    private void setTorch(boolean on) {
        if (torchId == null) return;
        try {
            ((CameraManager) getSystemService(CAMERA_SERVICE)).setTorchMode(torchId, on);
            torchOn = on;
            torchBtn.setTextColor(on ? U.DARK : U.ON_DARK);
            torchBtn.setBackground(U.box(this, on ? U.ON_DARK : 0xFF2A2A28, 0, 32));
        } catch (Exception e) {
            U.toast(this, "Torcia non disponibile");
        }
    }

    /** Da chiamare all'accensione dello schermo. */
    static void showIfLocked(Context c) {
        if (!U.prefs(c).getBoolean(U.K_LOCK, true)) return;
        KeyguardManager km = (KeyguardManager) c.getSystemService(KEYGUARD_SERVICE);
        if (km == null || !km.isKeyguardLocked()) return;
        Intent i = new Intent(c, LockActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        U.start(c, i);
    }
}
