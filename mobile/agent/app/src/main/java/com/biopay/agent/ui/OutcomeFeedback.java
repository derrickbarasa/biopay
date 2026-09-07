package com.biopay.agent.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.biopay.agent.R;
import com.google.android.material.snackbar.Snackbar;

/** High-contrast, readable feedback for biometric and other important field outcomes. */
public final class OutcomeFeedback {
    private static final int ERROR_DURATION_MS = 10_000;
    private static final int SUCCESS_DURATION_MS = 6_000;

    private OutcomeFeedback() { }

    public static void error(Activity activity, @StringRes int messageId) {
        error(activity, activity.getString(messageId));
    }

    public static void error(Activity activity, CharSequence message) {
        show(activity, activity.getString(R.string.feedback_failure_title), message,
                R.color.bp_error, ERROR_DURATION_MS, ViewCompat.ACCESSIBILITY_LIVE_REGION_ASSERTIVE);
    }

    public static void success(Activity activity, @StringRes int messageId) {
        success(activity, activity.getString(messageId));
    }

    public static void success(Activity activity, CharSequence message) {
        show(activity, activity.getString(R.string.feedback_success_title), message,
                R.color.bp_success, SUCCESS_DURATION_MS, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);
    }

    private static void show(Activity activity, String title, CharSequence message,
            int backgroundColor, int duration, int liveRegionMode) {
        View anchor = activity.findViewById(android.R.id.content);
        SpannableStringBuilder content = new SpannableStringBuilder(title)
                .append('\n')
                .append(message == null || message.length() == 0
                        ? activity.getString(R.string.feedback_unknown_error)
                        : message);
        content.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        Snackbar snackbar = Snackbar.make(anchor, content, duration)
                .setAction(R.string.feedback_dismiss, ignored -> { });
        snackbar.setBackgroundTint(ContextCompat.getColor(activity, backgroundColor));
        snackbar.setTextColor(Color.WHITE);
        snackbar.setActionTextColor(Color.WHITE);

        View snackbarView = snackbar.getView();
        snackbarView.setElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8,
                activity.getResources().getDisplayMetrics()));
        ViewCompat.setAccessibilityLiveRegion(snackbarView, liveRegionMode);
        TextView text = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
        if (text != null) {
            text.setMaxLines(6);
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            text.setLineSpacing(0, 1.12f);
        }
        snackbar.show();
    }
}
