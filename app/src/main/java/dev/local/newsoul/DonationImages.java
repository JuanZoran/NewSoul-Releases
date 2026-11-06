package dev.local.newsoul;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.IOException;

/** Process-local compressed bytes only; never retain a page, Activity or decoded bitmap. */
final class DonationImages {
    private static final byte[][] CACHE = new byte[2][];

    private DonationImages() {}

    static synchronized byte[] bytes(int index) {
        if (index < 0 || index >= CACHE.length) throw new IllegalArgumentException("Unknown payment code");
        if (CACHE[index] == null) CACHE[index] = DonationImageData.read(index);
        return CACHE[index];
    }

    static Bitmap preview(int index) throws IOException {
        byte[] bytes = bytes(index);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
            throw new IOException("Invalid payment image dimensions");
        BitmapFactory.Options options = new BitmapFactory.Options();
        // Bound preview memory; saving always uses the unmodified original bytes.
        options.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 1600)
            options.inSampleSize *= 2;
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (bitmap == null) throw new IOException("Payment image decode returned null");
        return bitmap;
    }
}
