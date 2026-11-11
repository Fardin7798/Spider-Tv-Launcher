package com.spidertv.launcher;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Builds the rows of the Settings and Manage apps screens from the source picker's row layout. */
final class SpiderSettingsRows {

    interface Action {
        void run(View row);
    }

    private SpiderSettingsRows() {
    }

    /** A small non-focusable heading between groups of rows. */
    static void section(LinearLayout list, int title) {
        final TextView label = new TextView(list.getContext());
        final float dp = list.getResources().getDisplayMetrics().density;
        label.setText(title);
        label.setAllCaps(true);
        label.setTextColor(0x80FFFFFF);
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f);
        label.setLetterSpacing(0.08f);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        label.setPadding((int) (12 * dp), (int) ((list.getChildCount() == 0 ? 4 : 14) * dp), 0, (int) (4 * dp));
        list.addView(label);
    }

    /**
     * One focusable row. {@code icon} null hides the glyph; an app icon is drawn untinted. {@code status}
     * null hides the status text; {@code dot} shows the green dot beside it.
     */
    static View row(LinearLayout list, Drawable icon, String title, String status, boolean dot, Action action) {
        final View row = LayoutInflater.from(list.getContext()).inflate(R.layout.spider_input_row, list, false);
        final ImageView glyph = row.findViewById(R.id.spiderRowGlyph);
        if (icon == null) {
            glyph.setVisibility(View.GONE);
        } else {
            glyph.setImageTintList(null);
            glyph.setImageDrawable(icon);
        }
        ((TextView) row.findViewById(R.id.spiderRowTitle)).setText(title);
        status(row, status, dot);
        row.setOnClickListener(action::run);
        list.addView(row);
        return row;
    }

    static void status(View row, String status, boolean dot) {
        final TextView text = row.findViewById(R.id.spiderRowStatus);
        text.setVisibility(status == null ? View.GONE : View.VISIBLE);
        text.setText(status);
        row.findViewById(R.id.spiderStatusDot).setVisibility(dot ? View.VISIBLE : View.GONE);
        row.setContentDescription(status == null ? null
                : ((TextView) row.findViewById(R.id.spiderRowTitle)).getText() + ", " + status);
    }
}
