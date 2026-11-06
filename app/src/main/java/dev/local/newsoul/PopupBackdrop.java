package dev.local.newsoul;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.view.View;

/** Small, popup-local snapshot; foreground text is never blurred or retained. */
final class PopupBackdrop {
    static Shader capture(View host, View parent, int x, int y, int width, int height) {
        final int sample = 8, padding = 6;
        int w = (width + sample - 1) / sample + padding * 2;
        int h = (height + sample - 1) / sample + padding * 2;
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(1f / sample, 1f / sample);
        canvas.translate(padding * sample - x, padding * sample - y);
        try {
            draw(canvas, host);
        } catch (RuntimeException hardwareOnlyHost) {
            if (parent == host) throw hardwareOnlyHost;
            // Host feeds may contain hardware bitmaps that reject a software canvas.
            // The plugin dialog is still a usable local backdrop; keep its alpha so
            // the already blurred window remains visible underneath.
            bitmap.eraseColor(android.graphics.Color.TRANSPARENT);
        }
        if (parent != host) draw(canvas, parent);
        int[] pixels = new int[w * h], temp = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        // Three separable box passes approximate a soft Gaussian at bounded popup size.
        for (int pass = 0; pass < 3; pass++) {
            blur(pixels, temp, w, h, true);
            blur(temp, pixels, w, h, false);
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
        BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix matrix = new Matrix();
        matrix.setScale(sample, sample);
        matrix.postTranslate(-padding * sample, -padding * sample);
        shader.setLocalMatrix(matrix);
        return shader;
    }

    private static void draw(Canvas canvas, View root) {
        int[] location = new int[2];
        root.getLocationOnScreen(location);
        int save = canvas.save();
        canvas.translate(location[0], location[1]);
        try { root.draw(canvas); }
        finally { canvas.restoreToCount(save); }
    }

    private static void blur(int[] src, int[] dst, int w, int h, boolean horizontal) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int a = 0, r = 0, g = 0, b = 0;
            for (int offset = -2; offset <= 2; offset++) {
                int xx = horizontal ? Math.max(0, Math.min(w - 1, x + offset)) : x;
                int yy = horizontal ? y : Math.max(0, Math.min(h - 1, y + offset));
                int color = src[yy * w + xx];
                a += color >>> 24; r += color >> 16 & 255; g += color >> 8 & 255; b += color & 255;
            }
            dst[y * w + x] = (a / 5 << 24) | (r / 5 << 16) | (g / 5 << 8) | b / 5;
        }
    }
    private PopupBackdrop() {}
}
