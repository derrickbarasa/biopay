package com.biopay.agent.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

/** Decodes a captured-photo file into a small in-memory bitmap sized for a thumbnail/avatar
 *  ImageView, instead of loading the full camera-resolution JPEG into memory. No image-loading
 *  library (Glide/Coil) is in this app's dependencies, so this is a minimal hand-rolled decode. */
public final class ThumbnailLoader {

    private ThumbnailLoader() { }

    /** Loads {@code localPath} into {@code target} sized to roughly {@code targetSizeDp}, or
     *  leaves {@code target} showing its existing placeholder if the path is missing/unreadable. */
    public static boolean loadInto(ImageView target, String localPath, int targetSizeDp) {
        if (localPath == null || localPath.trim().isEmpty()) return false;
        int targetPx = Math.round(targetSizeDp * target.getResources().getDisplayMetrics().density);
        Bitmap bitmap = decodeSampled(localPath, targetPx);
        if (bitmap == null) return false;
        target.setImageBitmap(bitmap);
        return true;
    }

    /** Wires (or clears) tapping {@code target} to open the photo full-screen via {@link
     *  PhotoViewerActivity}. Call after {@link #loadInto}, passing its result as {@code hasPhoto}
     *  -- there is nothing worth expanding when the placeholder icon is showing instead. */
    public static void makeExpandable(ImageView target, String localPath, boolean hasPhoto) {
        if (hasPhoto) {
            target.setOnClickListener(v -> v.getContext().startActivity(PhotoViewerActivity.intent(v.getContext(), localPath)));
        } else {
            target.setOnClickListener(null);
        }
        target.setClickable(hasPhoto);
        target.setFocusable(hasPhoto);
    }

    private static Bitmap decodeSampled(String path, int targetPx) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sampleSize = 1;
        int halfWidth = bounds.outWidth / 2;
        int halfHeight = bounds.outHeight / 2;
        while ((halfWidth / sampleSize) >= targetPx && (halfHeight / sampleSize) >= targetPx) {
            sampleSize *= 2;
        }

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = sampleSize;
        return BitmapFactory.decodeFile(path, decodeOptions);
    }
}
