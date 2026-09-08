package com.biopay.agent.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;

import com.biopay.agent.R;

/**
 * Full-screen view of one already-captured photo -- opened by tapping any small photo thumbnail
 * in the app (a captured person's photo during registration, their avatar on the Household
 * Profile screen, or the row in the household edit screen's "Captured people" list). Purely a
 * viewer: no edit/delete/retake action here, those stay on the screen the tap came from.
 */
public class PhotoViewerActivity extends BaseActivity {

    private static final String EXTRA_LOCAL_PATH = "photo_viewer_local_path";
    /** Generous relative to any real device's screen so the shown image is effectively
     *  full-resolution, while still going through {@link ThumbnailLoader}'s sampled decode
     *  rather than loading a multi-megabyte camera JPEG at its native size. */
    private static final int TARGET_SIZE_DP = 900;

    public static Intent intent(Context context, String localPath) {
        Intent intent = new Intent(context, PhotoViewerActivity.class);
        intent.putExtra(EXTRA_LOCAL_PATH, localPath);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_viewer);
        setupBackToolbar(R.id.toolbar);

        String localPath = getIntent().getStringExtra(EXTRA_LOCAL_PATH);
        ImageView imageView = findViewById(R.id.ivFullPhoto);
        if (!ThumbnailLoader.loadInto(imageView, localPath, TARGET_SIZE_DP)) {
            OutcomeFeedback.error(this, R.string.photo_viewer_unavailable);
            finish();
        }
    }
}
