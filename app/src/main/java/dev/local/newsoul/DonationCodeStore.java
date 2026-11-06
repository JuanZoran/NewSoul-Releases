package dev.local.newsoul;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Copies the original bundled bytes, preserving the QR image without recompression. */
final class DonationCodeStore {
    private DonationCodeStore() {}

    static boolean exists(Context context, Uri uri) {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            return input != null && input.read() >= 0;
        } catch (Exception missing) { return false; }
    }

    static Uri save(Context context, int index) throws Exception {
        byte[] bytes = DonationImages.bytes(index);
        boolean wechat = index == 0;
        String extension = wechat ? ".png" : ".jpg";
        String mime = wechat ? "image/png" : "image/jpeg";
        String name = "NewSoul-" + (wechat ? "WeChat-" : "Alipay-")
                + System.currentTimeMillis() + extension;
        try (InputStream input = new java.io.ByteArrayInputStream(bytes)) {
            if (Build.VERSION.SDK_INT >= 29) return saveMedia(context, input, name, mime);
            if (context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) throw new SecurityException();
            File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "New Soul");
            if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Cannot create image directory");
            File file = new File(folder, name);
            boolean complete = false;
            // Never overwrite a pre-existing image, even if names collide.
            if (!file.createNewFile()) throw new IOException("Image already exists");
            try {
                try (OutputStream output = new FileOutputStream(file)) { copy(input, output); }
                complete = true;
                android.media.MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()},
                        new String[]{mime}, null);
                return Uri.fromFile(file);
            } finally {
                if (!complete) file.delete();
            }
        }
    }

    private static Uri saveMedia(Context context, InputStream input, String name, String mime) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, mime);
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/New Soul");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("Cannot create image");
        boolean complete = false;
        try {
            try (OutputStream output = resolver.openOutputStream(uri)) {
                if (output == null) throw new IOException("Cannot open image");
                copy(input, output);
            }
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            if (resolver.update(uri, values, null, null) != 1) throw new IOException("Cannot publish image");
            complete = true;
            return uri;
        } finally {
            if (!complete) {
                try { resolver.delete(uri, null, null); } catch (RuntimeException ignored) {}
            }
        }
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    }
}
