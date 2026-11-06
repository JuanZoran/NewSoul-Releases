package dev.local.newsoul;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParser;

/** Presentation-only pieces: no host adapters, requests, or send actions. */
final class AiReplyComponents {
    private static final Object CANDIDATE_TEXT = new Object();
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String CONSTRAINT_LAYOUT = "androidx.constraintlayout.widget.ConstraintLayout";

    private AiReplyComponents() {}

    /** A reply card invokes caller-supplied actions; preview callers remain inert. */
    static View candidate(SettingsUi.MenuPanel panel, String text, Runnable action) {
        return candidate(panel, text, (String) null, action);
    }

    /** Description is caller-supplied display metadata, never part of the adopted reply text. */
    static View candidate(SettingsUi.MenuPanel panel, String text, String description, Runnable action) {
        if (action == null) throw new IllegalArgumentException("Missing candidate action");
        String reply = text == null ? "" : text;
        String detail = description == null ? "" : description.trim();
        View nativeRow = nativeCandidate(panel, reply, detail, action);
        return nativeRow != null ? nativeRow : fallbackCandidate(panel, reply, detail, action);
    }

    static View candidate(SettingsUi.MenuPanel panel, String text, Runnable action, Runnable edit) {
        final View[] holder = new View[1];
        View row = candidate(panel, text, (String) null, () -> {
            // Only a fully laid-out, untruncated candidate keeps the one-tap shortcut.
            TextView label = holder[0].findViewWithTag(CANDIDATE_TEXT);
            android.text.Layout layout = label == null ? null : label.getLayout();
            boolean complete = layout != null && layout.getLineCount() > 0
                    && layout.getLineCount() <= label.getMaxLines();
            if (complete) {
                for (int i = 0; i < layout.getLineCount(); i++)
                    if (layout.getEllipsisCount(i) > 0) complete = false;
                complete &= layout.getLineEnd(layout.getLineCount() - 1) == label.length();
            }
            if (complete) action.run(); else edit.run();
        });
        holder[0] = row;
        View target = find(row, panel.activity, "ctl_content");
        if (target == null) target = row;
        target.setOnLongClickListener(v -> { edit.run(); return true; });
        return row;
    }

    /** LoveBoard candidate: native edit/multi-message assets with explicit plugin callbacks. */
    static View boardCandidate(SettingsUi.MenuPanel panel,String text,Runnable send,Runnable edit) {
        LinearLayout row=new LinearLayout(panel.context);row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(panel.context,12),dp(panel.context,8),dp(panel.context,12),dp(panel.context,8));
        panel.appearance.hostBoardSurface(row,hostColor(panel,"color_s_04",panel.dark?0xff282838:0xffededed),8,0);
        LinearLayout line=new LinearLayout(panel.context);line.setGravity(Gravity.CENTER_VERTICAL);
        TextView label=candidateText(panel,text);label.setTextSize(14);label.setMinHeight(dp(panel.context,32));label.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        ImageView editIcon=new ImageView(panel.context);editIcon.setImageDrawable(hostDrawable(panel,"c_ct_aigc_edit_icon"));
        editIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);editIcon.setPadding(dp(panel.context,10),dp(panel.context,8),0,dp(panel.context,8));
        editIcon.setContentDescription("编辑回复");editIcon.setOnClickListener(v->edit.run());editIcon.setFocusable(true);
        line.addView(editIcon,new LinearLayout.LayoutParams(dp(panel.context,40),dp(panel.context,40)));row.addView(line);
        if(AiSendRules.parts(text).size()>1){
            View divider=new View(panel.context);divider.setBackgroundColor(hostColor(panel,"color_s_04",panel.dark?0xff333344:0xffe4e1ec));
            LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(-1,Math.max(1,dp(panel.context,1)));dp.topMargin=dp(panel.context,4);row.addView(divider,dp);
            TextView hint=new TextView(panel.context);hint.setText("本内容会逐条发送");hint.setTextColor(0xff884dff);hint.setTextSize(10);hint.setPadding(0,dp(panel.context,6),0,0);
            Drawable icon=hostDrawable(panel,"c_ct_icon_aigc_multi_step_content");if(icon!=null){icon.setBounds(0,0,dp(panel.context,14),dp(panel.context,14));hint.setCompoundDrawables(icon,null,null,null);hint.setCompoundDrawablePadding(dp(panel.context,5));}
            row.addView(hint);
        }
        row.setFocusable(true);row.setMinimumHeight(dp(panel.context,48));
        row.setOnClickListener(v->{
            android.text.Layout layout=label.getLayout();boolean complete=layout!=null&&layout.getLineCount()>0;
            if(complete){for(int i=0;i<layout.getLineCount();i++)if(layout.getEllipsisCount(i)>0)complete=false;
                complete&=layout.getLineEnd(layout.getLineCount()-1)==label.length();}
            if(complete)send.run();else edit.run();
        });row.setOnLongClickListener(v->{edit.run();return true;});rowParams(panel,row);return row;
    }

    /** Guidance is a sibling outside the send target; tapping it never sends the reply. */
    static View boardCandidate(SettingsUi.MenuPanel panel, AiReplyRules.Candidate candidate,
            Runnable send, Runnable edit) {
        View card = boardCandidate(panel, candidate.text, send, edit);
        String guidance = candidate.guidance();
        if (guidance.isEmpty()) return card;
        LinearLayout group = new LinearLayout(panel.context);
        group.setOrientation(LinearLayout.VERTICAL);
        group.addView(card);
        TextView detail = new TextView(panel.context);
        bindDescription(panel, detail, guidance);
        // Reuse the host description typography/color, without adding an action icon or
        // truncating decision-relevant advice on narrow screens or at large font sizes.
        detail.setCompoundDrawables(null, null, null, null);
        detail.setMaxLines(Integer.MAX_VALUE);
        detail.setMaxWidth(Integer.MAX_VALUE);
        detail.setEllipsize(null);
        detail.setPadding(dp(panel.context,12),0,dp(panel.context,12),dp(panel.context,4));
        group.addView(detail, new LinearLayout.LayoutParams(-1,-2));
        rowParams(panel, group);
        return group;
    }

    /** Native content/layout uses the same appearance roles as the settings panels. */
    static Drawable panelBackground(SettingsUi.MenuPanel panel) {
        return panel.appearance.background(UiAppearance.Role.PANEL);
    }

    private static View nativeCandidate(SettingsUi.MenuPanel panel, String text, String description, Runnable action) {
        Context host = panel.activity;
        int layout = resource(host, "c_ct_aigc_chat_rec_content_item_view", "layout");
        if (layout == 0 || !safeLayout(panel.context.getResources(), layout)) return null;
        try {
            // A fresh inflater has no Activity factory and cannot attach host business listeners.
            // Only the presentation XML is loaded; AigcRecContentView is never instantiated.
            View outer = new SafeInflater(panel.context, host.getClassLoader()).inflate(layout, null, false);
            View root = find(outer, host, "ctl_content");
            View content = find(outer, host, "container");
            View divider = find(outer, host, "v_divider");
            View detail = find(outer, host, "tv_description");
            if (!(root instanceof ViewGroup) || !(content instanceof LinearLayout)
                    || divider == null || !(detail instanceof TextView)) return null;
            LinearLayout container = (LinearLayout) content;
            if (container.getChildCount() != 0 || container.getOrientation() != LinearLayout.HORIZONTAL)
                return null;
            // Respect both native horizontal constraints, including long unbroken text.
            ViewGroup.LayoutParams contentParams = container.getLayoutParams();
            if (contentParams == null || !CONSTRAINT_LAYOUT.equals(root.getClass().getName())) return null;
            contentParams.width = 0;
            container.setLayoutParams(contentParams);
            TextView label = candidateText(panel, text);
            container.addView(label, new LinearLayout.LayoutParams(-1, -2));
            if (description.isEmpty()) {
                divider.setVisibility(View.GONE);
                detail.setVisibility(View.GONE);
            } else {
                divider.setVisibility(View.VISIBLE);
                divider.setBackgroundColor(hostColor(panel, "color_s_04", panel.dark ? 0xff282838 : 0xffededed));
                bindDescription(panel, (TextView) detail, description);
            }
            panel.appearance.apply(root, UiAppearance.Role.ACTION);
            // ConstraintLayout measures from its own min-height constraint, not View's
            // setMinimumHeight field. Use the host class API so wrap_content rows really
            // measure at least 48dp; unsupported host versions use our fallback row.
            root.getClass().getMethod("setMinHeight", int.class)
                    .invoke(root, dp(panel.context, 48));
            root.setFocusable(true);
            root.setOnClickListener(v -> action.run());
            rowParams(panel, outer);
            return outer;
        } catch (Exception | LinkageError ignored) {
            // Resource/class drift is expected between host versions; fall back without logging text.
            return null;
        }
    }

    private static View fallbackCandidate(SettingsUi.MenuPanel panel, String text, String description, Runnable action) {
        LinearLayout row = new LinearLayout(panel.context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(panel.context, 12), dp(panel.context, 8),
                dp(panel.context, 12), dp(panel.context, 8));
        panel.appearance.apply(row, UiAppearance.Role.ACTION);
        row.addView(candidateText(panel, text), new LinearLayout.LayoutParams(-1, -2));
        if (!description.isEmpty()) {
            View divider = new View(panel.context);
            divider.setBackgroundColor(hostColor(panel, "color_s_04", panel.dark ? 0xff282838 : 0xffededed));
            LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1,
                    Math.max(1, Math.round(.5f * panel.context.getResources().getDisplayMetrics().density)));
            line.topMargin = dp(panel.context, 6);
            row.addView(divider, line);
            TextView detail = new TextView(panel.context);
            bindDescription(panel, detail, description);
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-2, -2);
            detailParams.topMargin = dp(panel.context, 6);
            row.addView(detail, detailParams);
        }
        row.setMinimumHeight(dp(panel.context, 48));
        row.setFocusable(true);
        row.setOnClickListener(v -> action.run());
        rowParams(panel, row);
        return row;
    }

    private static TextView candidateText(SettingsUi.MenuPanel panel, String text) {
        TextView label = new TextView(panel.context);
        label.setTag(CANDIDATE_TEXT);
        label.setText(displayText(panel, text));
        label.setContentDescription(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setTextColor(hostColor(panel, "color_s_03", panel.foreground));
        label.setGravity(Gravity.START);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(0, 0, 0, 0);
        return label;
    }

    /** Preview the same boundaries used by sending, with the existing host marker. */
    static CharSequence displayText(SettingsUi.MenuPanel panel, String text) {
        if(text.trim().isEmpty())return text;
        java.util.List<String> parts=AiSendRules.parts(text);
        if(parts.size()==1)return text;
        SpannableStringBuilder display = new SpannableStringBuilder();
        for (String part : parts) {
            if (part.trim().isEmpty()) continue;
            if (display.length() > 0) {
                Drawable divider = hostDrawable(panel, "c_ct_aigc_multi_step_content_divider");
                // Preserve visible line breaks when the host resource is unavailable.
                if (divider == null) display.append('\n');
                else {
                    int width = divider.getIntrinsicWidth(), height = divider.getIntrinsicHeight();
                    int gap = dp(panel.context, 6);
                    Drawable spaced = new InsetDrawable(divider, gap, 0, gap, 0);
                    spaced.setBounds(0, 0, (width > 0 ? width : dp(panel.context, 6)) + gap * 2,
                            height > 0 ? height : dp(panel.context, 14));
                    int start = display.length();
                    display.append('\ufffc');
                    display.setSpan(new ImageSpan(spaced, ImageSpan.ALIGN_BASELINE), start,
                            display.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            display.append(part);
        }
        return display;
    }

    private static void bindDescription(SettingsUi.MenuPanel panel, TextView detail, String description) {
        detail.setVisibility(View.VISIBLE);
        detail.setText(description);
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        detail.setTextColor(panel.dark ? 0xffb7a1ec : 0xff7050b0);
        detail.setMaxLines(1);
        detail.setMaxWidth(dp(panel.context, 265));
        detail.setEllipsize(TextUtils.TruncateAt.END);
        detail.setCompoundDrawablePadding(dp(panel.context, 5));
        detail.setCompoundDrawablesRelativeWithIntrinsicBounds(
                hostDrawable(panel, "c_ct_msg_item_aigc_desc_icon"), null, null, null);
    }

    private static void rowParams(SettingsUi.MenuPanel panel, View row) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(panel.context, 8);
        row.setLayoutParams(params);
    }

    static Drawable cardBackground(SettingsUi.MenuPanel panel) {
        return panel.appearance.background(UiAppearance.Role.CARD);
    }

    static Drawable hostDrawable(SettingsUi.MenuPanel panel, String name) {
        int id = resource(panel.activity, name, "drawable");
        if (id == 0) return null;
        try {
            return panel.context.getResources().getDrawable(id, panel.context.getTheme()).mutate();
        } catch (Resources.NotFoundException | IllegalArgumentException ignored) {
            return null;
        }
    }

    static int hostColor(SettingsUi.MenuPanel panel, String name, int fallback) {
        int id = resource(panel.activity, name, "color");
        if (id == 0) return fallback;
        try {
            return panel.context.getResources().getColor(id, panel.context.getTheme());
        } catch (Resources.NotFoundException | IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static View find(View root, Context host, String name) {
        int id = resource(host, name, "id");
        return id == 0 ? null : root.findViewById(id);
    }

    private static int resource(Context context, String name, String type) {
        return context.getResources().getIdentifier(name, type, context.getPackageName());
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static boolean safeLayout(Resources resources, int id) {
        try (XmlResourceParser xml = resources.getLayout(id)) {
            int nodes = 0;
            for (int event = xml.getEventType(); event != XmlPullParser.END_DOCUMENT; event = xml.next()) {
                if (event != XmlPullParser.START_TAG) continue;
                if (++nodes > 32 || !allowedView(xml.getName())
                        || xml.getAttributeValue(ANDROID_NS, "onClick") != null) return false;
            }
            return nodes > 0;
        } catch (Exception | LinkageError ignored) {
            return false;
        }
    }

    private static boolean allowedView(String name) {
        return "FrameLayout".equals(name) || "LinearLayout".equals(name) || "View".equals(name)
                || "TextView".equals(name) || "ImageView".equals(name)
                || "android.widget.FrameLayout".equals(name) || "android.widget.LinearLayout".equals(name)
                || "android.view.View".equals(name) || "android.widget.TextView".equals(name)
                || "android.widget.ImageView".equals(name) || CONSTRAINT_LAYOUT.equals(name)
                || "androidx.appcompat.widget.AppCompatImageView".equals(name)
                || "androidx.appcompat.widget.AppCompatTextView".equals(name)
                || "cn.soul.android.widget.image.MateImageView".equals(name);
    }

    private static final class SafeInflater extends LayoutInflater {
        private final ClassLoader hostLoader;

        SafeInflater(Context context, ClassLoader hostLoader) {
            super(context);
            this.hostLoader = hostLoader;
            setFactory2(new Factory2() {
                @Override public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
                    return createSafeView(name, context, attrs);
                }
                @Override public View onCreateView(String name, Context context, AttributeSet attrs) {
                    return createSafeView(name, context, attrs);
                }
            });
        }

        @Override public LayoutInflater cloneInContext(Context context) {
            return new SafeInflater(context, hostLoader);
        }

        private View createSafeView(String name, Context context, AttributeSet attrs) {
            if (!allowedView(name)) throw new IllegalArgumentException("Unsupported candidate view");
            if (CONSTRAINT_LAYOUT.equals(name)) {
                try {
                    return (View) Class.forName(name, true, hostLoader)
                            .getConstructor(Context.class, AttributeSet.class).newInstance(context, attrs);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Candidate layout unavailable", e);
                }
            }
            if (name.endsWith("FrameLayout")) return new FrameLayout(context, attrs);
            if (name.endsWith("LinearLayout")) return new LinearLayout(context, attrs);
            if (name.endsWith("TextView")) return new TextView(context, attrs);
            if (name.endsWith("ImageView")) return new ImageView(context, attrs);
            return new View(context, attrs);
        }
    }

}
