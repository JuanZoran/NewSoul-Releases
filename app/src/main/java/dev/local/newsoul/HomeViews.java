package dev.local.newsoul;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Home cards/actions share one layout implementation, including large-font fallback. */
final class HomeViews {
    final Activity activity;
    final boolean dark;
    final UiAppearance appearance;
    final int foreground, secondary;
    HomeViews(Activity activity, boolean dark) {
        this.activity = activity;
        this.dark = dark;
        appearance = UiAppearance.of(activity, dark);
        foreground = dark ? 0xfff1f4fa : 0xff111827;
        secondary = dark ? 0xffa5afc0 : 0xff707b91;
    }
    int dp(int value) { return SettingsUi.dp(activity, value); }
    TextView text(String value, int size, int color, boolean bold) {
        TextView text = SettingsUi.label(activity,value,size,color);
        text.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        text.setSingleLine(false); text.setEllipsize(null);
        return text;
    }
    LinearLayout column() {
        LinearLayout box = new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);return box;
    }
    LinearLayout card(String title) {
        LinearLayout card = column();card.setPadding(dp(14),dp(12),dp(14),dp(12));
        appearance.homeSurface(card,false,false);
        card.addView(text(title,16,foreground,true));return card;
    }
    TextView action(String title, int icon, boolean primary, Runnable click) {
        TextView action = text(title,17,primary ? 0xffffffff : foreground,true);
        action.setGravity(Gravity.CENTER);action.setMinHeight(dp(52));
        action.setPadding(dp(10),dp(12),dp(10),dp(12));
        if (icon >= 0) {
            HomeGlyph glyph = new HomeGlyph(icon,foreground);glyph.setBounds(0,0,dp(23),dp(23));
            android.text.SpannableString label = new android.text.SpannableString("\ufffc  " + title);
            label.setSpan(new android.text.style.ImageSpan(glyph, android.os.Build.VERSION.SDK_INT >= 29
                    ? android.text.style.DynamicDrawableSpan.ALIGN_CENTER : android.text.style.ImageSpan.ALIGN_BASELINE),
                    0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            action.setText(label);
        }
        action.setOnClickListener(v -> click.run());
        SettingsUi.buttonSemantics(action);
        action.setContentDescription(title);
        appearance.homeSurface(action,primary,false);return action;
    }
    TextView quiet(String title, int icon, Runnable click) {
        TextView view = action(title, icon, false, click);
        appearance.homeQuiet(view);
        return view;
    }
    LinearLayout link(String title, String subtitle, int glyph, Runnable click) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setMinimumHeight(dp(72));
        android.widget.ImageView icon = new android.widget.ImageView(activity);
        icon.setImageDrawable(new HomeGlyph(glyph, foreground));
        row.addView(icon, new LinearLayout.LayoutParams(dp(26), dp(26)));
        LinearLayout labels = column();
        labels.addView(text(title, 17, foreground, true));
        add(labels, text(subtitle, 13, secondary, false), 4);
        LinearLayout.LayoutParams bounds = new LinearLayout.LayoutParams(0, -2, 1);
        bounds.leftMargin = dp(14);
        row.addView(labels, bounds);
        row.setOnClickListener(v -> click.run());
        row.setContentDescription(title + "，" + subtitle);
        SettingsUi.buttonSemantics(row);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        labels.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        appearance.homeSurface(row, false, false);
        return row;
    }
    void add(LinearLayout parent, View child, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(margin);
        parent.addView(child,params);
    }
    /** Equal actions wrap into equal full-width rows when labels no longer fit. */
    void pair(LinearLayout parent, TextView left, TextView right, int margin) {
        LinearLayout row = new LinearLayout(activity);
        int available = Math.min(activity.getResources().getDisplayMetrics().widthPixels, dp(480)) - dp(40);
        float needed = Math.max(width(left),width(right));
        boolean horizontal = needed <= (available - dp(10)) / 2f;
        row.setOrientation(horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(horizontal ? 0 : -1,-2,horizontal ? 1 : 0);
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(horizontal ? 0 : -1,-2,horizontal ? 1 : 0);
        if(horizontal) second.leftMargin=dp(10);else second.topMargin=dp(10);
        row.addView(left,first);row.addView(right,second);add(parent,row,margin);
    }
    private float width(TextView text) {
        return text.getPaint().measureText(text.getText().toString()) + text.getPaddingLeft()+text.getPaddingRight()
                + (text.getText() instanceof android.text.Spanned ? dp(23) : 0);
    }
}
