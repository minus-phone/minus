package it.essenziale.launcher;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;

/**
 * Evolutiva 003 · Gesti in home: su apre la ricerca, sinistra e destra passano da Oggi a Settimana.
 * Si scorre al centro: bordi e fondo restano ad Android (indietro, home, app recenti). Ogni gesto ha
 * il suo tasto visibile, quindi con TalkBack non serve.
 */
final class Swipe extends GestureDetector.SimpleOnGestureListener {
    interface Act { void up(); void left(); void right(); }

    private final Context c;
    private final Act act;
    private final boolean upOk[];
    final GestureDetector detector;

    Swipe(Context c, boolean[] upOk, Act act) {
        this.c = c;
        this.act = act;
        this.upOk = upOk;
        detector = new GestureDetector(c, this);
    }

    @Override
    public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
        if (a == null || b == null) return false;
        int w = c.getResources().getDisplayMetrics().widthPixels, h = c.getResources().getDisplayMetrics().heightPixels;
        int edge = U.dp(c, 32), min = U.dp(c, 80);
        if (a.getX() < edge || a.getX() > w - edge || a.getY() > h - U.dp(c, 64)) return false; // gesti di Android
        float dx = b.getX() - a.getX(), dy = b.getY() - a.getY();
        if (Math.abs(dx) > Math.abs(dy) * 1.5f && Math.abs(dx) > min && Math.abs(vx) > 600) {
            if (dx < 0) act.left(); else act.right();
            return true;
        }
        if (-dy > Math.abs(dx) * 1.5f && -dy > min && -vy > 600 && (upOk == null || upOk[0])) {
            act.up();
            return true;
        }
        return false;
    }
}
