package com.biopay.agent.session;

import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * Delegates every {@link Window.Callback} method to the window's original callback, but first
 * reports any touch/key event as user activity. A dialog runs on its own {@link Window}, so
 * touches inside it never reach the hosting Activity's {@code onUserInteraction()} -- wrapping
 * the dialog's own callback (rather than a view-level touch listener) is what catches every
 * touch regardless of which child view inside the dialog actually consumes it, matching how
 * Activity itself detects interaction for its own window.
 */
final class KeepAliveWindowCallback implements Window.Callback {

    private final Window.Callback delegate;

    KeepAliveWindowCallback(Window.Callback delegate) {
        this.delegate = delegate;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        SessionTimeoutManager.get().reset();
        return delegate.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchKeyShortcutEvent(KeyEvent event) {
        return delegate.dispatchKeyShortcutEvent(event);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        SessionTimeoutManager.get().reset();
        return delegate.dispatchTouchEvent(event);
    }

    @Override public boolean dispatchTrackballEvent(MotionEvent event) {
        return delegate.dispatchTrackballEvent(event);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return delegate.dispatchGenericMotionEvent(event);
    }

    @Override public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        return delegate.dispatchPopulateAccessibilityEvent(event);
    }

    @Override public View onCreatePanelView(int featureId) {
        return delegate.onCreatePanelView(featureId);
    }

    @Override public boolean onCreatePanelMenu(int featureId, Menu menu) {
        return delegate.onCreatePanelMenu(featureId, menu);
    }

    @Override public boolean onPreparePanel(int featureId, View view, Menu menu) {
        return delegate.onPreparePanel(featureId, view, menu);
    }

    @Override public boolean onMenuOpened(int featureId, Menu menu) {
        return delegate.onMenuOpened(featureId, menu);
    }

    @Override public boolean onMenuItemSelected(int featureId, MenuItem item) {
        return delegate.onMenuItemSelected(featureId, item);
    }

    @Override public void onWindowAttributesChanged(WindowManager.LayoutParams attrs) {
        delegate.onWindowAttributesChanged(attrs);
    }

    @Override public void onContentChanged() {
        delegate.onContentChanged();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        delegate.onWindowFocusChanged(hasFocus);
    }

    @Override public void onAttachedToWindow() {
        delegate.onAttachedToWindow();
    }

    @Override public void onDetachedFromWindow() {
        delegate.onDetachedFromWindow();
    }

    @Override public void onPanelClosed(int featureId, Menu menu) {
        delegate.onPanelClosed(featureId, menu);
    }

    @Override public boolean onSearchRequested() {
        return delegate.onSearchRequested();
    }

    @Override
    @androidx.annotation.RequiresApi(23)
    public boolean onSearchRequested(SearchEvent searchEvent) {
        return delegate.onSearchRequested(searchEvent);
    }

    @Override public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) {
        return delegate.onWindowStartingActionMode(callback);
    }

    @Override
    @androidx.annotation.RequiresApi(23)
    public ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int type) {
        return delegate.onWindowStartingActionMode(callback, type);
    }

    @Override public void onActionModeStarted(ActionMode mode) {
        delegate.onActionModeStarted(mode);
    }

    @Override public void onActionModeFinished(ActionMode mode) {
        delegate.onActionModeFinished(mode);
    }
}
