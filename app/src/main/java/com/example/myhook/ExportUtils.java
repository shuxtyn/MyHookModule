package com.example.myhook;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class ExportUtils {

    /** Ghi chuỗi text ra thư mục Downloads (API 29+), không xin permission. */
    public static Uri exportTextToDownloads(Context ctx, String fileName, String text) {
        if (Build.VERSION.SDK_INT < 29) return null;
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            v.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            v.put(MediaStore.Downloads.IS_PENDING, 1);

            Uri uri = ctx.getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) return null;

            try (OutputStream os = ctx.getContentResolver().openOutputStream(uri, "w")) {
                if (os == null) return null;
                os.write(text.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            v.clear();
            v.put(MediaStore.Downloads.IS_PENDING, 0);
            ctx.getContentResolver().update(uri, v, null, null);
            return uri;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Copy một file có sẵn (nội bộ app) ra thư mục Downloads (API 29+), không xin permission. */
    public static Uri exportFileToDownloads(Context ctx, File sourceFile, String destFileName) {
        if (Build.VERSION.SDK_INT < 29) return null;
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, destFileName);
            v.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            v.put(MediaStore.Downloads.IS_PENDING, 1);

            Uri uri = ctx.getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) return null;

            try (InputStream is = new FileInputStream(sourceFile);
                 OutputStream os = ctx.getContentResolver().openOutputStream(uri, "w")) {
                if (os == null) return null;
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
                os.flush();
            }

            v.clear();
            v.put(MediaStore.Downloads.IS_PENDING, 0);
            ctx.getContentResolver().update(uri, v, null, null);
            return uri;
        } catch (Throwable t) {
            return null;
        }
    }
}
