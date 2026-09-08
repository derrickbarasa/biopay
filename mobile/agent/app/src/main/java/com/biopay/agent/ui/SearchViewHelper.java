package com.biopay.agent.ui;

import androidx.appcompat.widget.SearchView;

/** A non-iconified SearchView's leading magnifier glyph is a plain, non-clickable ImageView --
 *  tapping exactly on it does nothing, only the EditText area next to it responds. Every search
 *  bar in the app should feel like one uniform tappable field, so this makes the whole view
 *  respond to a tap that lands outside the EditText (the icon and any padding around it). */
public final class SearchViewHelper {
    private SearchViewHelper() { }

    public static void makeFullyClickable(SearchView searchView) {
        searchView.setOnClickListener(v -> {
            searchView.setIconified(false);
            searchView.requestFocusFromTouch();
        });
    }
}
