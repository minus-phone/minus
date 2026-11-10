package it.essenziale.launcher;

import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;

/**
 * 0.13 (wireframe 178–178c): al posto delle sigle «WA TG SMS» l'icona che l'app usa nella barra di stato,
 * monocromatica. −minus la salva la prima volta che l'app manda una notifica; finché non c'è, resta il nome.
 * Dove si legge: solo l'icona (con il nome per TalkBack). Dove si sceglie: icona e nome.
 */
final class AppIcons {
    private AppIcons() {}

    private static File file(Context c, String pkg) {
        return new File(new File(c.getFilesDir(), "sb-icons"), pkg.replaceAll("[^A-Za-z0-9._]", "_") + ".png");
    }

    /** Dal filtro notifiche: salva l'icona piccola della notifica, una volta per app (rinfrescata ogni 30 giorni). */
    static void save(Context c, String pkg, Notification n) {
        try {
            if (pkg == null || n == null || n.getSmallIcon() == null) return;
            File f = file(c, pkg);
            if (f.length() > 0 && System.currentTimeMillis() - f.lastModified() < 30L * 24 * 3600 * 1000) return;
            Drawable d = n.getSmallIcon().loadDrawable(c);
            if (d == null) return;
            d = d.mutate();
            // 0.13.9: sagoma nera su trasparente, in ARGB (prima: una bitmap di sola trasparenza che
            // su molti telefoni non si salvava in PNG, e restava un file vuoto: per questo si vedeva il nome)
            d.setColorFilter(0xFF000000, PorterDuff.Mode.SRC_IN);
            int s = 96;
            Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            d.setBounds(0, 0, s, s);
            d.draw(cv);
            f.getParentFile().mkdirs();
            File tmp = new File(f.getPath() + ".tmp");
            FileOutputStream o = new FileOutputStream(tmp);
            boolean ok = b.compress(Bitmap.CompressFormat.PNG, 100, o);
            o.close();
            if (ok && tmp.length() > 0) tmp.renameTo(f); else tmp.delete();
        } catch (Throwable ignored) {}
    }

    static boolean has(Context c, String pkg) { return pkg != null && file(c, pkg).length() > 0; }

    /** Per «Cosa vede −minus»: quante icone sono già state salvate. */
    static int count(Context c) {
        File[] l = new File(c.getFilesDir(), "sb-icons").listFiles();
        int n = 0;
        if (l != null) for (File x : l) if (x.getName().endsWith(".png") && x.length() > 0) n++;
        return n;
    }

    /** L'icona colorata con il colore del testo, o null se l'app non ha ancora mandato notifiche. */
    static Drawable drawable(Context c, String pkg, int color, int sizeDp) {
        if (!has(c, pkg)) return null;
        try {
            Bitmap b = BitmapFactory.decodeFile(file(c, pkg).getAbsolutePath());
            if (b == null) return null;
            BitmapDrawable d = new BitmapDrawable(c.getResources(), b);
            d.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            int s = U.dp(c, sizeDp);
            d.setBounds(0, 0, s, s);
            return d;
        } catch (Throwable e) { return null; }
    }

    /** Icona per un codice di chat: WA, TG, SMS, TEL. */
    static String pkgOfCode(Context c, String code) {
        if ("TEL".equals(code)) {
            for (String p : Rules.DIALERS) if (has(c, p)) return p;
            return Rules.DIALERS[0];
        }
        if ("WA".equals(code) || "TG".equals(code) || "SMS".equals(code)) { String p = U.chatPkg(c, code); if (p != null) return p; }
        return SearchActivity.pkgOf(code);
    }

    /** Dove si legge: un'icona sola, o il nome per intero se l'icona non c'è ancora. */
    static View badge(Context c, String pkg, String name, int color) {
        Drawable d = drawable(c, pkg, color, 18);
        if (d == null) {
            TextView t = U.text(c, name, 12, color, false);
            t.setPadding(U.dp(c, 6), 0, U.dp(c, 2), 0);
            return t;
        }
        ImageView v = new ImageView(c);
        v.setImageDrawable(d);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setContentDescription(name);
        v.setPadding(U.dp(c, 4), 0, U.dp(c, 4), 0);
        v.setLayoutParams(new LinearLayout.LayoutParams(U.dp(c, 26), U.dp(c, 18)));
        return v;
    }

    /** Dove si sceglie: l'icona accanto al nome, se c'è. */
    static void beside(TextView t, String pkg, int color) {
        Drawable d = drawable(t.getContext(), pkg, color, 18);
        if (d == null) return;
        t.setCompoundDrawablesRelative(d, null, null, null);
        t.setCompoundDrawablePadding(U.dp(t.getContext(), 8));
    }

    static View view(Context c, String pkg, int sizeDp) {
        ImageView v = new ImageView(c);
        Drawable d = drawable(c, pkg, U.INK, sizeDp);
        if (d != null) v.setImageDrawable(d);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return v;
    }

    static LinearLayout.LayoutParams lp(Context c, int sizeDp) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(U.dp(c, sizeDp), U.dp(c, sizeDp));
        l.rightMargin = U.dp(c, 12);
        return l;
    }
}
