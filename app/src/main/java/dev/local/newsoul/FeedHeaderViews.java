package dev.local.newsoul;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Finds the host's existing label strips in both header generations. */
final class FeedHeaderViews {
    static final Object METADATA_TAG = new Object();
    static View time(View root) {
        View modern = find(root, "vHeaderCommonLabel");
        if (modern != null) return modern;
        View time = find(root, "tvStatus");
        if (time != null && time.getVisibility() == View.VISIBLE) return time;
        // Some legacy medal headers omit the time view or keep it in a hidden
        // container. Attach to the visible medal's existing horizontal row.
        View medal = find(root, "llWornMedal");
        if (medal != null && medal.getVisibility() == View.VISIBLE && medal.getParent() instanceof ViewGroup
                && ((View) medal.getParent()).getVisibility() == View.VISIBLE)
            return (View) medal.getParent();
        return time != null && time.getParent() instanceof ViewGroup ? (View) time.getParent() : time;
    }
    static View names(View root) {
        View modern = find(root, "vHeaderUserInfoLabel");
        if (modern != null) return modern;
        return find(root, "header_top");
    }
    /** Borrow the native title style when its text is nested inside the old horizontal strip. */
    static TextView nameAnchor(View root, View strip) {
        if (find(root, "vHeaderUserInfoLabel") == null && strip == find(root, "header_top")) {
            View title = find(root, "tv_title");
            return title instanceof TextView && title.getVisibility() == View.VISIBLE ? (TextView) title : null;
        }
        TextView text = firstText(strip);
        return text != null && text.getParent() == strip ? text : null;
    }
    static int availableWidth(ViewGroup strip) {
        int width = strip.getWidth() - strip.getPaddingLeft() - strip.getPaddingRight();
        int oldNames = strip.getResources().getIdentifier("header_top", "id", "cn.soulapp.android");
        if (oldNames != 0 && strip.getId() == oldNames && strip.getParent() instanceof View) {
            // header_top and its immediate FrameLayout wrap the name. Their parent
            // is the allocated title column, already excluding avatar/follow/menu.
            View frame = (View) strip.getParent();
            if (frame.getParent() instanceof ViewGroup) {
                ViewGroup column = (ViewGroup) frame.getParent();
                width = column.getWidth() - column.getPaddingLeft() - column.getPaddingRight()
                        - frame.getLeft() - strip.getLeft() - strip.getPaddingLeft() - strip.getPaddingRight();
            }
        }
        return Math.max(0, width);
    }
    static View layoutAnchor(ViewGroup strip, TextView style) {
        if (style != null && style.getParent() == strip) return style;
        for (int i = 0; i < strip.getChildCount(); i++) {
            View child = strip.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE && child.getTag() != METADATA_TAG) return child;
        }
        return null;
    }
    static TextView firstText(View view) {
        if (view == null || view.getTag() == METADATA_TAG || view.getVisibility() != View.VISIBLE) return null;
        // Legacy medal rows keep an empty school TextView beside the hidden
        // time. It is not a text anchor; choosing it suppresses badge fallback.
        if (view instanceof TextView) return ((TextView) view).getText().length() == 0 ? null : (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView text = firstText(group.getChildAt(i));
                if (text != null) return text;
            }
        }
        return null;
    }
    private static View find(View root, String name) {
        int id = root.getResources().getIdentifier(name, "id", "cn.soulapp.android");
        return id == 0 ? null : root.findViewById(id);
    }
    private FeedHeaderViews() {}
}
