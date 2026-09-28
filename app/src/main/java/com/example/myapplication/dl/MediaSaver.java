package com.example.myapplication.dl;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Best-effort copy of a finished file into the system gallery / music library. */
public final class MediaSaver {

    private static final String TAG = "GODX_MEDIA";

    private MediaSaver() {
    }

    public static void exportToGallery(Context ctx, File file, boolean audio) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportScoped(ctx, file, audio);
            } else {
                exportLegacy(ctx, file, audio);
            }
        } catch (Throwable t) {
            Log.w(TAG, "export skipped: " + t.getMessage());
        }
    }

    private static void exportScoped(Context ctx, File file, boolean audio) throws Exception {
        ContentResolver resolver = ctx.getContentResolver();
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, file.getName());
        if (audio) {
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/GodxShadow");
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg");
        } else {
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/GodxShadow");
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        }
        Uri collection = audio
                ? MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                : MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        Uri item = resolver.insert(collection, cv);
        if (item == null) return;
        try (InputStream in = new FileInputStream(file);
             OutputStream out = resolver.openOutputStream(item)) {
            if (out == null) return;
            byte[] buf = new byte[1 << 16];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
        }
    }

    private static void exportLegacy(Context ctx, File file, boolean audio) throws Exception {
        File pub = new File(Environment.getExternalStoragePublicDirectory(
                audio ? Environment.DIRECTORY_MUSIC : Environment.DIRECTORY_MOVIES), "GodxShadow");
        //noinspection ResultOfMethodCallIgnored
        pub.mkdirs();
        File dst = new File(pub, file.getName());
        try (InputStream in = new FileInputStream(file);
             OutputStream out = new java.io.FileOutputStream(dst)) {
            byte[] buf = new byte[1 << 16];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
        }
        try {
            android.media.MediaScannerConnection.scanFile(
                    ctx, new String[]{dst.getAbsolutePath()}, null, null);
        } catch (Throwable ignored) {
        }
    }
}
