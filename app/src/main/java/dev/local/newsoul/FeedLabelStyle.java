package dev.local.newsoul;

import android.content.SharedPreferences;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.widget.TextView;

/** Shared by passive feed labels and their settings samples; default inherits the host. */
final class FeedLabelStyle {
    static final String IP = "square_ip_label_", DAYS = "square_days_label_";
    static Integer color(SharedPreferences prefs, String prefix) {
        return color(prefs.getString(prefix + "color", "default"));
    }

    static Integer color(String value) {
        if (FeedLabelStyleRules.color(value) && value.startsWith("#")) return android.graphics.Color.parseColor(value);
        switch (value == null ? "default" : value) {
            case "mint": return 0xff27cbb5;
            case "blue": return 0xff4285e8;
            case "orange": return 0xffe58a36;
            default: return null;
        }
    }

    static int size(SharedPreferences prefs, String prefix) {
        return size(prefs.getString(prefix + "size", "default"));
    }

    static int size(String value) {
        return !FeedLabelStyleRules.size(value) || "default".equals(value) ? 0 : Integer.parseInt(value);
    }

    static void apply(TextView label, String prefix, float defaultPx, int defaultColor) {
        SharedPreferences prefs = PluginPreferences.get(label.getContext());
        apply(label, prefs.getString(prefix + "color", "default"), prefs.getString(prefix + "size", "default"), defaultPx, defaultColor);
    }

    static void apply(TextView label, String colorValue, String sizeValue, float defaultPx, int defaultColor) {
        int sp = size(sizeValue);
        float px = sp == 0 ? defaultPx : TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                sp, label.getResources().getDisplayMetrics());
        if (label.getTextSize() != px) label.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        Integer custom = color(colorValue);
        int tint = custom == null ? defaultColor : custom;
        if (label.getCurrentTextColor() != tint) label.setTextColor(tint);
    }

    static CharSequence appendDays(TextView time, CharSequence original, int days) {
        SharedPreferences prefs = PluginPreferences.get(time.getContext());
        return appendDays(time, original, days, prefs.getString(DAYS + "color", "default"),
                prefs.getString(DAYS + "size", "default"));
    }

    static CharSequence appendDays(TextView time, CharSequence original, int days, String tint, String fontSize) {
        if (original.length() == 0) return original;
        SpannableStringBuilder text = new SpannableStringBuilder(original).append(" · 注册")
                .append(Integer.toString(days)).append("天");
        Integer color = color(tint);
        int size = size(fontSize);
        int start = original.length();
        if (color != null) text.setSpan(new ForegroundColorSpan(color), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (size != 0) {
            int px = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                    size, time.getResources().getDisplayMetrics()));
            text.setSpan(new AbsoluteSizeSpan(px), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return text;
    }

    private FeedLabelStyle() {}
}
