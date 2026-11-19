package it.essenziale.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.ContactsContract;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;

/**
 * 0.19 (Evolutiva 014): il telefono in home. Il pulsante tondo accanto alla ricerca apre il tastierino di Android
 * (ACTION_DIAL: nessun permesso di chiamata); tenuto premuto, «Nuovo contatto». Stesso pulsante nella ricerca.
 */
final class Telefono {
    private Telefono() {}

    static final String WHATSAPP = "com.whatsapp";

    /** Il tastierino di Android, con il numero già scritto se c'è. */
    static boolean dial(Context c, String number) {
        Intent i = new Intent(Intent.ACTION_DIAL, number == null || number.length() == 0 ? null : Uri.fromParts("tel", number, null));
        if (!U.start(c, i)) { U.toast(c, "Non trovo l'app Telefono"); return false; }
        return true;
    }

    /** La rubrica di Android su «Nuovo contatto», con il numero già scritto se c'è. */
    static boolean newContact(Context c, String number) {
        Intent i = new Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI);
        if (number != null && number.length() > 0) i.putExtra(ContactsContract.Intents.Insert.PHONE, number);
        if (!U.start(c, i)) { U.toast(c, "Non trovo l'app Contatti"); return false; }
        return true;
    }

    static boolean hasWhatsApp(Context c) { return U.installed(c, WHATSAPP); }

    /** La chat WhatsApp con un numero: https://wa.me/<cifre>. */
    static boolean whatsapp(Context c, String typed) {
        String d = Cerca.waDigits(typed);
        if (d.length() == 0) return false;
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + d));
        i.setPackage(WHATSAPP);
        if (!U.start(c, i)) { U.toast(c, "Non riesco ad aprire WhatsApp"); return false; }
        return true;
    }

    /** Il pulsante tondo da 62 dp: bordo di 1 dp, trasparente, cornetta. Un tocco = tastierino, pressione lunga = Nuovo contatto. */
    static View button(final Activity a) {
        View b = new Icon(a);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(android.graphics.Color.TRANSPARENT);
        g.setStroke(Math.max(1, U.dp(a, 1)), U.INK);
        Press.icon(b, g); // 0.20: premuto, alone circolare
        b.setClickable(true);
        b.setFocusable(true);
        b.setContentDescription("Telefono, apre il tastierino");
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dial(a, null); } });
        b.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { newContact(a, null); return true; } });
        // TalkBack: l'azione della pressione lunga si chiama «Nuovo contatto»
        b.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, "Nuovo contatto"));
            }
        });
        return b;
    }

    static LinearLayout.LayoutParams buttonLp(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(U.dp(c, 62), U.dp(c, 62));
        lp.rightMargin = U.dp(c, 8);
        lp.gravity = Gravity.CENTER_VERTICAL;
        return lp;
    }

    /** La cornetta a tratto (24x24, come nelle tavole), nel colore del testo. */
    static final class Icon extends View {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int color;

        Icon(Context c) { this(c, U.INK); }

        Icon(Context c, int color) {
            super(c);
            this.color = color;
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeJoin(Paint.Join.ROUND);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setColor(color);
        }

        @Override
        protected void onDraw(Canvas cv) {
            // l'icona misura 24 dp al centro del pulsante (nelle righe, quanto la vista)
            float side = Math.min(getWidth(), getHeight());
            float size = side > U.dp(getContext(), 40) ? U.dp(getContext(), 24) : side;
            float s = size / 24f, ox = (getWidth() - size) / 2f, oy = (getHeight() - size) / 2f;
            path.reset();
            path.moveTo(7.2f, 3.5f);
            path.lineTo(10.2f, 3.5f);
            path.lineTo(11.7f, 7.7f);
            path.lineTo(9.7f, 9.0f);
            path.quadTo(11.46f, 12.54f, 15.0f, 14.3f);
            path.lineTo(16.3f, 12.3f);
            path.lineTo(20.5f, 13.8f);
            path.lineTo(20.5f, 16.8f);
            path.quadTo(20.5f, 18.8f, 18.4f, 18.8f);
            path.quadTo(7.7f, 16.3f, 5.2f, 5.6f);
            path.quadTo(5.2f, 3.5f, 7.2f, 3.5f);
            path.close();
            cv.save();
            cv.translate(ox, oy);
            cv.scale(s, s);
            // il tratto va scalato con il disegno: lo si imposta già diviso per la scala
            line.setStrokeWidth(1.6f);
            cv.drawPath(path, line);
            cv.restore();
        }
    }
}
