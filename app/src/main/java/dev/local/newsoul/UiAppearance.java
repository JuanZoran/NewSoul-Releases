package dev.local.newsoul;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.*;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import java.util.WeakHashMap;

/** Semantic surface factory. Pages choose a role, never a renderer or style-specific palette.
 * Resolve once per menu tree; a future preference change must recreate that tree.
 */
final class UiAppearance {
    // About 89% depth on the RGB-weighted scale; keep every settings panel on one blue tone.
    static final int DARK_SETTINGS_PANEL = 0xff1a1b2c;
    enum Style { LIQUID_GLASS, FLAT, MATTE, ORIGINAL_GLASS_TABS }
    enum Role { PANEL, SHEET, CARD, LIST_ITEM, CHOICE, RADIO_ROW, PRIMARY, INPUT, ACTION, INLINE_ACTION, MEDIA_ACTION, ICON, CONTENT }
    // The only style resolver. No persisted preference is introduced by this migration.
    static Style currentStyle() { return Style.ORIGINAL_GLASS_TABS; }
    final Style style;
    final boolean dark;
    final float density;
    boolean categorySurface;
    boolean navigationTrack;
    private static final WeakHashMap<View, Drawable> bound = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> observed = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> cards = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> inlineInputs = new WeakHashMap<>();

    static boolean isCard(View view) { return cards.containsKey(view); }

    /** Preserve the exact ordinary presentation for live toggle/system-blur fallback. */
    static final class CardPresentation {
        final Drawable background, foreground;
        final float elevation;
        CardPresentation(View view) {
            background = view.getBackground();
            foreground = view.getForeground();
            elevation = view.getElevation();
        }
        void restore(View view) {
            bind(view, background);
            view.setForeground(foreground);
            view.setElevation(elevation);
        }
    }

    CardPresentation outlineCard(View view) {
        CardPresentation original = new CardPresentation(view);
        bind(view, view.getBackground() instanceof SearchSurface
                ? new SearchSurface((SearchSurface) view.getBackground(), true)
                : frostedCardBackground());
        view.setForeground(null);
        view.setElevation(0);
        return original;
    }

    Drawable frostedCardBackground() {
        GradientDrawable outline = shape(dark ? 0x04ffffff : 0x02ffffff, 14 * density, false);
        outline.setStroke(Math.max(1, Math.round(.65f * density)), dark ? 0x30ffffff : 0x28505b70);
        return outline;
    }

    /** Standalone home uses the approved thin outlines, through the shared surface owner. */
    void homeSurface(View view, boolean primary, boolean selected) {
        int fill = primary ? SettingsUi.ACCENT : Color.TRANSPARENT;
        GradientDrawable plate = shape(fill, 12 * density, false);
        if (!primary) plate.setStroke(Math.max(1, Math.round(density)),
                selected ? 0xff00bf62 : dark ? 0xff535e70 : 0xffcdd4df);
        Drawable surface = view.isClickable()
                ? new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(
                    dark ? 0x22ffffff : 0x1400b5a2), plate, shape(Color.WHITE, 12 * density, false)) : plate;
        bind(view, surface);
    }

    enum HomeState { PENDING, ACTIVE, FAILED }

    int homeStatus(View view, HomeState state) {
        int fill, border, foreground;
        switch (state) {
            case ACTIVE:
                fill = dark ? 0xff192b27 : 0xfff0faf4;
                border = dark ? 0xff3b8f72 : 0xffa3d6b6;
                foreground = successColor();
                break;
            case FAILED:
                fill = dark ? 0xff321f25 : 0xfffff3f4;
                border = dark ? 0xffa65060 : 0xffe2a6af;
                foreground = dark ? 0xffee929e : 0xffb53549;
                break;
            default:
                fill = dark ? 0xff302b1d : 0xfffffaec;
                border = dark ? 0xffa38238 : 0xffdfc477;
                foreground = dark ? 0xffe9c465 : 0xff85600e;
                break;
        }
        GradientDrawable plate = shape(fill, 12 * density, false);
        plate.setStroke(Math.max(1, Math.round(density)), border);
        bind(view, plate);
        return foreground;
    }

    void homeQuiet(View view) {
        bind(view, new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(dark ? 0x22ffffff : 0x1400b5a2),
                null, shape(Color.WHITE, 12 * density, false)));
    }

    void homeBackdrop(View view) {
        bind(view, new android.graphics.drawable.ColorDrawable(dark ? 0xff171b26 : 0xfff8fafc));
    }

    void drawFrostedCapsule(Canvas canvas, android.graphics.RectF bounds) {
        if (bounds.isEmpty()) return;
        frostedTabPaint.setStyle(Paint.Style.STROKE);
        frostedTabPaint.setStrokeWidth(Math.max(1, .65f * density));
        frostedTabPaint.setColor(dark ? 0x30ffffff : 0x28505b70);
        canvas.drawRoundRect(bounds, bounds.height() / 2, bounds.height() / 2, frostedTabPaint);
    }

    void drawFrostedLens(Canvas canvas, android.graphics.RectF bounds) {
        frostedTabPaint.setStyle(Paint.Style.FILL);
        frostedTabPaint.setColor(dark ? 0x181a1b2c : 0x18f5f6f8);
        canvas.drawRoundRect(bounds, bounds.height() / 2, bounds.height() / 2, frostedTabPaint);
    }

    private final Paint frostedTabPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    void drawFrostedTab(Canvas canvas, android.graphics.RectF bounds, boolean selected, float alpha) {
        if (alpha <= 0 || bounds.isEmpty()) return;
        float radius = Math.min(12 * density, bounds.height() / 2);
        frostedTabPaint.setStyle(Paint.Style.FILL);
        int fill = selected ? (dark ? 0x123dd9c1 : 0x0a00a995) : (dark ? 0x04ffffff : 0x02ffffff);
        frostedTabPaint.setColor(fill);
        frostedTabPaint.setAlpha(Math.round(Color.alpha(fill) * alpha));
        canvas.drawRoundRect(bounds, radius, radius, frostedTabPaint);
        int edge = selected ? (dark ? 0xb33dd9c1 : 0xb0009989) : (dark ? 0x30ffffff : 0x28505b70);
        frostedTabPaint.setStyle(Paint.Style.STROKE);
        frostedTabPaint.setStrokeWidth(Math.max(1, density * (selected ? .85f : .65f)));
        frostedTabPaint.setColor(edge);
        frostedTabPaint.setAlpha(Math.round(Color.alpha(edge) * alpha));
        canvas.drawRoundRect(bounds, radius, radius, frostedTabPaint);
    }

    static UiAppearance of(Context context, boolean dark) {
        return new UiAppearance(currentStyle(), dark, context.getResources().getDisplayMetrics().density);
    }
    static Context menuContext(Context host, boolean dark) {
        // Drawable alpha alone does not make PhoneWindow.isTranslucent() true.
        // Seed the framework translucent attribute, then restore the full existing dialog theme.
        android.view.ContextThemeWrapper themed = new android.view.ContextThemeWrapper(
                host, android.R.style.Theme_Translucent);
        themed.getTheme().applyStyle(dark ? android.R.style.Theme_Material_Dialog_Alert
                : android.R.style.Theme_Material_Light_Dialog_Alert, true);
        return themed;
    }
    UiAppearance(Style style, boolean dark, float density) {
        this.style = style; this.dark = dark; this.density = density;
    }
    /** Help uses the shared theme; the only custom contour is its rounded pointer. */
    void helpBubble(View view, boolean below, float pointer) {
        view.setBackground(new BubbleSurface(dark, below, pointer, density));
    }
    /** PopupWindow has no public background-blur API; blur only its sampled backdrop. */
    void frostHelpBubble(View view, android.app.Activity activity, View parent,
            int x, int y, int width, int height) {
        if (!(view.getBackground() instanceof BubbleSurface)
                || android.os.Build.VERSION.SDK_INT < 31
                || !PluginPreferences.get(activity).getBoolean(FrostedMenuEffect.ENABLED,
                        ConfigDefaults.booleanValue(FrostedMenuEffect.ENABLED))) return;
        android.view.WindowManager manager = activity.getSystemService(android.view.WindowManager.class);
        if (manager == null || !manager.isCrossWindowBlurEnabled()) return;
        try {
            BubbleSurface surface = (BubbleSurface) view.getBackground();
            surface.backdrop = PopupBackdrop.capture(activity.getWindow().getDecorView(), parent,
                    x, y, width, height);
            surface.invalidateSelf();
        } catch (RuntimeException | OutOfMemoryError unavailable) {
            // Hardware-only host content may reject software capture; retain the readable surface.
            android.util.Log.w("SoulHook102", "Help backdrop unavailable", unavailable);
        }
    }
    void transparentPopup(android.widget.PopupWindow popup) {
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    }
    void clearBackground(View view) {
        bound.remove(view);
        view.setBackground(null);
    }
    int panelColor() {
        if (style == Style.ORIGINAL_GLASS_TABS) return dark ? DARK_SETTINGS_PANEL : categorySurface ? 0xfff5f6f8 : Color.WHITE;
        if (style == Style.MATTE) return dark ? 0xff181d21 : 0xfff3f6f6;
        return dark ? 0xff181828 : 0xfff5f6f8;
    }
    boolean glassTabs() { return style == Style.LIQUID_GLASS || style == Style.ORIGINAL_GLASS_TABS; }
    boolean quietActions() { return style == Style.MATTE || style == Style.ORIGINAL_GLASS_TABS; }
    int tabTrackTop() {
        if (navigationTrack) return dark ? 0xff303545 : 0xffe9edf2;
        return dark ? 0xff171626 : 0xffe5e7ed;
    }
    int tabTrackBottom() {
        if (navigationTrack) return dark ? 0xff2b3040 : 0xffe1e6ed;
        return dark ? 0xff181927 : 0xffe1e3ea;
    }
    int tabLensColor() {
        // Keep the sampled lens backdrop close to the navigation track.
        if (navigationTrack) return dark ? 0xff2e3343 : 0xffedf0f5;
        return dark ? 0xff202030 : 0xfff7f8fa;
    }
    int choiceForeground() { return style == Style.ORIGINAL_GLASS_TABS ? 0xff183c3b : accent(); }
    int cardColor() { return dark ? 0xff242b30 : Color.WHITE; }
    int selectedColor() { return dark ? 0xff24443e : 0xffd9f2ec; }
    int primaryForeground() { return dark ? 0xff063a35 : Color.WHITE; }
    int foreground() { return dark ? 0xffe6e6ef : 0xff333333; }
    int accent() { return dark ? (style == Style.ORIGINAL_GLASS_TABS ? 0xff00b5a2 : 0xff54dfcb) : 0xff00796b; }
    float radius(Role role) {
        if (style == Style.MATTE && (role == Role.CHOICE || role == Role.PRIMARY)) return 12;
        switch (role) {
            case CHOICE: return 20;
            case PRIMARY: return 22;
            case PANEL: return 16;
            case SHEET: return 20;
            case INPUT: return 12;
            case INLINE_ACTION: return 16;
            case MEDIA_ACTION: return 22;
            default: return 14;
        }
    }
    // Same resting vertical inset as the glass lens; the hit target stays full size.
    float segmentLensInset() { return 3 * density; }
    void searchActionFeedback(View view) {
        // Match GlassTabTrack's resting 1dp inset and half-height capsule exactly.
        // The full 48dp view remains the hit target for both search and cancel.
        clearBackground(view);
        int inset = Math.round(density);
        Drawable mask = new InsetDrawable(shape(Color.WHITE, 999 * density, false),
                inset, inset, inset, inset);
        view.setForeground(new RippleDrawable(ColorStateList.valueOf(
                dark ? 0x18ffffff : 0x1800796b), null, mask));
    }

    Drawable segmentedFeedback() {
        Drawable mask = new InsetDrawable(shape(Color.WHITE, 999 * density, false),
                0, Math.round(segmentLensInset()), 0, Math.round(segmentLensInset()));
        return new RippleDrawable(ColorStateList.valueOf(dark ? 0x18ffffff : 0x1800796b),
                null, mask);
    }
    void applySegmentedFeedback(View option) {
        option.setForeground(segmentedFeedback());
    }

    void searchResultDivider(View view) {
        bind(view,new ColorDrawable(dark ? 0x18b8bdd6 : 0x16505b70));
    }

    void searchCloseSurface(View view) {
        bind(view,shape(dark ? 0x664f536c : 0x18505b70,999*density,false));
    }

    void searchSurface(View view) {
        GradientDrawable plate=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                dark ? new int[]{0xfa232936,0xfa1f2431} : new int[]{0xfcfafbfd,0xfcf3f5f8});
        // One quiet plate under the original capsule and results; no nested contour.
        plate.setCornerRadius(26*density);
        cards.put(view, Boolean.TRUE);
        bind(view,new SearchSurface(plate, dark, density));
    }

    void prepareSearchBackdrop(View view, android.app.Activity activity, View parent,
            int x, int y, int width, int height) {
        if (!(view.getBackground() instanceof SearchSurface)) return;
        SearchSurface surface = (SearchSurface) view.getBackground();
        surface.source.shader = null;
        if (android.os.Build.VERSION.SDK_INT < 31 || width <= 0 || height <= 0) return;
        // Keep one opening snapshot available if blur is enabled from a search result.
        // The normal surface never draws it while the preference/system blur is off.
        try {
            surface.source.shader = PopupBackdrop.capture(activity.getWindow().getDecorView(),
                    parent, x, y, width, height);
        } catch (RuntimeException | OutOfMemoryError unavailable) {
            android.util.Log.w("SoulHook102", "Search backdrop unavailable", unavailable);
        }
        view.invalidate();
    }

    void clearSearchBackdrop(View view) {
        if (view.getBackground() instanceof SearchSurface)
            ((SearchSurface) view.getBackground()).source.shader = null;
    }

    private static final class SearchBackdrop {
        android.graphics.Shader shader;
    }
    private static final class SearchSurface extends Drawable {
        final Drawable ordinary;
        final SearchBackdrop source;
        final boolean dark, frosted;
        final float density;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        SearchSurface(Drawable ordinary, boolean dark, float density) {
            this.ordinary = ordinary; this.dark = dark; this.density = density;
            this.source = new SearchBackdrop(); this.frosted = false;
        }
        SearchSurface(SearchSurface original, boolean frosted) {
            ordinary = original.ordinary; source = original.source;
            dark = original.dark; density = original.density; this.frosted = frosted;
        }
        @Override public void draw(Canvas canvas) {
            if (!frosted || source.shader == null) {
                ordinary.setBounds(getBounds()); ordinary.draw(canvas); return;
            }
            android.graphics.RectF bounds = new android.graphics.RectF(getBounds());
            float radius = 26 * density;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE); paint.setShader(source.shader);
            canvas.drawRoundRect(bounds, radius, radius, paint);
            paint.setShader(null);
            paint.setColor(dark ? 0xb31a1b2c : 0xb3f5f6f8);
            canvas.drawRoundRect(bounds, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(.65f * density);
            paint.setColor(dark ? 0x30ffffff : 0x28505b70);
            canvas.drawRoundRect(bounds, radius, radius, paint);
        }
        @Override public void getOutline(android.graphics.Outline outline) {
            ordinary.setBounds(getBounds()); ordinary.getOutline(outline);
        }
        @Override public void setAlpha(int alpha) { ordinary.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { ordinary.setColorFilter(filter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
    int searchSurfacePadding() { return Math.round(8*density); }
    void searchInput(View view) {
        if(!dark) { apply(view,Role.INPUT,28); return; }
        GradientDrawable plate=shape(0x30202234,28*density,false);
        plate.setStroke(Math.max(1,Math.round(.65f*density)),0x32b4bddb);
        bind(view,plate);
    }

    void searchPanelDepth(View panel) {
        panel.setElevation(3*density);
        if(android.os.Build.VERSION.SDK_INT>=28) {
            panel.setOutlineAmbientShadowColor(0x30000000);
            panel.setOutlineSpotShadowColor(0x48000000);
        }
    }

    void settingsCardDepth(View card) {
        GradientDrawable edge = new GradientDrawable();
        edge.setColor(Color.TRANSPARENT);
        edge.setCornerRadius(14 * density);
        edge.setStroke(Math.max(1, Math.round(.65f * density)), dark ? 0x18b8bdd6 : 0x16505b70);
        card.setForeground(edge);
        card.setElevation(2 * density);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            card.setOutlineAmbientShadowColor(dark ? 0x66000000 : 0x24505b70);
            card.setOutlineSpotShadowColor(dark ? 0x88000000 : 0x38505b70);
        }
    }

    Drawable background(Role role) { return background(role, radius(role)); }
    Drawable background(Role role, float radiusDp) {
        float radius = radiusDp * density;
        if (role == Role.CONTENT) return null;
        if (role == Role.LIST_ITEM) {
            Drawable plate;
            if (style == Style.LIQUID_GLASS) plate = surface(Role.CARD, radius, false);
            else {
                int color = style == Style.MATTE ? cardColor()
                        : dark ? 0xff242436 : 0xfff5f6f8;
                GradientDrawable layer = shape(color, radius, false);
                layer.setStroke(Math.max(1, Math.round(.5f*density)), dark ? 0x0fffffff : 0x08000000);
                plate = layer;
            }
            return new RippleDrawable(ColorStateList.valueOf(dark ? 0x18ffffff : 0x1400796b),
                    plate, shape(Color.WHITE, radius, false));
        }
        // Secondary actions remain quiet text within their parent's surface.
        if (quietActions() && (role == Role.ACTION || role == Role.INLINE_ACTION))
            return new RippleDrawable(ColorStateList.valueOf(dark ? 0x22ffffff : 0x2200796b),
                    null, shape(Color.WHITE, radius, false));
        // Visible surface is smaller than its accessible/touch target. Text stays outside the effect.
        if (role == Role.INLINE_ACTION) return new InsetDrawable(
                background(Role.ACTION, radiusDp), 0, Math.round(6*density), 0, Math.round(6*density));
        if (role == Role.ICON || role == Role.RADIO_ROW) return new RippleDrawable(
                ColorStateList.valueOf(dark ? 0x22ffffff : 0x2200b5a2), null, shape(Color.WHITE, radius, false));
        Drawable content;
        if (role == Role.CHOICE || role == Role.INPUT || role == Role.PRIMARY) {
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[]{-android.R.attr.state_enabled, android.R.attr.state_selected}, surface(role, radius, true, true));
            states.addState(new int[]{-android.R.attr.state_enabled, android.R.attr.state_checked}, surface(role, radius, true, true));
            states.addState(new int[]{-android.R.attr.state_enabled}, surface(role, radius, role == Role.PRIMARY, true));
            states.addState(new int[]{android.R.attr.state_selected}, surface(role, radius, true));
            states.addState(new int[]{android.R.attr.state_checked}, surface(role, radius, true));
            states.addState(new int[]{android.R.attr.state_focused}, surface(role, radius, true));
            states.addState(new int[]{}, surface(role, radius, role == Role.PRIMARY));
            content = states;
        } else content = surface(role, radius, role == Role.PRIMARY);
        if (role == Role.CHOICE || role == Role.PRIMARY || role == Role.ACTION || role == Role.MEDIA_ACTION || role == Role.INPUT) {
            return new RippleDrawable(ColorStateList.valueOf(dark ? 0x22ffffff : 0x2200b5a2),
                    content, shape(Color.WHITE, radius, false));
        }
        return content;
    }
    private Drawable surface(Role role, float radius, boolean selected) {
        return surface(role, radius, selected, false);
    }
    private Drawable surface(Role role, float radius, boolean selected, boolean disabled) {
        boolean sheet = role == Role.SHEET;
        int base = role == Role.MEDIA_ACTION ? 0xdb181828 : panelColor();
        if (style == Style.ORIGINAL_GLASS_TABS) {
            if (role == Role.CARD) base = categorySurface ? (dark ? 0xff25293c : Color.WHITE)
                    : (dark ? 0xff282838 : 0xffededed);
            else if (role == Role.SHEET) base = dark ? DARK_SETTINGS_PANEL : 0xfff7f8fa;
            else if (role == Role.CHOICE || role == Role.INPUT) base = dark ? 0xff282838 : 0xffededed;
            if (selected && role == Role.CHOICE) base = disabled ? (dark ? 0xff42605a : 0xffb4d5ce) : 0xff00b5a2;
            if (role == Role.PRIMARY) base = disabled ? (dark ? 0xff42605a : 0xffb4d5ce) : 0xff54dfcb;
            GradientDrawable restored = shape(base, radius, sheet);
            if (selected && role == Role.INPUT) restored.setStroke(Math.max(1, Math.round(density)), disabled ? 0xff81958e : accent());
            return restored;
        }
        if (style == Style.MATTE) {
            if (role == Role.CARD) base = cardColor();
            else if (role == Role.CHOICE || role == Role.INPUT) base = dark ? 0xff30383d : 0xffeaf0ef;
            if (selected) base = disabled ? (dark ? 0xff2c3936 : 0xffe3eae7) : selectedColor();
            if (role == Role.PRIMARY) base = disabled ? (dark ? 0xff354d48 : 0xffcfdfd9)
                    : (dark ? 0xff54dfcb : 0xff007f73);
            GradientDrawable matte = shape(base, radius, sheet);
            // A focused field needs an explicit cue; selected choices use fill, not a bright rim.
            if (selected && role == Role.INPUT)
                matte.setStroke(Math.max(1, Math.round(density)), disabled ? 0xff81958e : accent());
            return matte;
        }
        if (style == Style.FLAT) {
            if (selected) base = disabled ? (dark ? 0xff263c39 : 0xffe4eee9) : (dark ? 0xff214940 : 0xffd8f5ed);
            else if (role != Role.PANEL && role != Role.MEDIA_ACTION && !sheet) base = dark ? 0xff282838 : Color.WHITE;
            GradientDrawable flat = shape(base, radius, sheet);
            if (selected) flat.setStroke(Math.max(1, Math.round(density)), disabled ? (dark ? 0xff45635d : 0xffa7c2ba) : (dark ? 0xff54dfcb : 0xff00a995));
            return flat;
        }
        Drawable glass = new GlassCardBackground(dark, base, radius, density, sheet,
                role != Role.PANEL && role != Role.SHEET && role != Role.MEDIA_ACTION);
        if (!selected) return glass;
        GradientDrawable tint = shape(disabled ? (dark ? 0x1236d7b7 : 0x0d4cdec3) : (dark ? 0x2436d7b7 : 0x1b4cdec3), radius, sheet);
        tint.setStroke(Math.max(1, Math.round(density)), disabled ? (dark ? 0x2454dfcb : 0x1d00a995) : (dark ? 0x5054dfcb : 0x4200a995));
        return new LayerDrawable(new Drawable[]{glass, tint});
    }
    private static GradientDrawable shape(int color, float radius, boolean sheet) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        if (sheet) drawable.setCornerRadii(new float[]{radius,radius,radius,radius,0,0,0,0});
        else drawable.setCornerRadius(radius);
        return drawable;
    }
    /** Soul LoveBoard's flat Compose surfaces; host-resolved palette, shared lifecycle. */
    void hostBoardSurface(View view, int color, float radiusDp, int outline) {
        GradientDrawable plate = shape(color, radiusDp * density, false);
        if (outline != 0) plate.setStroke(Math.max(1, Math.round(density)), outline);
        bind(view, plate);
    }

    /** Reuse the existing frosted card surface for an editor inside a settings card. */
    void applyInlineInput(View view) {
        inlineInputs.put(view, Boolean.TRUE);
        applyOutlinedInput(view);
    }

    /** Persistent rectangular outline makes an inline numeric field visibly editable. */
    void applyOutlinedInput(View view) {
        boolean inline = inlineInputs.containsKey(view);
        float radiusDp = inline ? 14 : radius(Role.INPUT);
        StateListDrawable outline = new StateListDrawable();
        int[][] states = {{-android.R.attr.state_enabled}, {android.R.attr.state_focused}, {}};
        int[] colors = {dark ? 0xff454552 : 0xff9999a3,
                dark ? 0xff4b827b : 0xff32655f, dark ? 0xff60606e : 0xff686874};
        for (int i = 0; i < states.length; i++) {
            GradientDrawable frame = shape(Color.TRANSPARENT, radiusDp * density, false);
            if (!inline || i != 2)
                frame.setStroke(Math.max(1, Math.round((i == 1 ? 1.5f : 1f) * density)), colors[i]);
            outline.addState(states[i], frame);
        }
        bind(view, new LayerDrawable(new Drawable[]{inline ? frostedCardBackground()
                : background(Role.INPUT, radiusDp), outline}));
    }

    int successColor() { return dark ? 0xff68d6a5 : 0xff21834c; }
    int errorColor() { return dark ? 0xffff8585 : 0xffbf303a; }

    /** Keep the shared input surface and add a theme-aware validation outline. */
    void inputError(View view, boolean invalid) {
        boolean inline = inlineInputs.containsKey(view);
        if (!invalid) {
            if (inline) applyOutlinedInput(view);
            else apply(view, Role.INPUT);
            return;
        }
        GradientDrawable outline = shape(Color.TRANSPARENT, (inline ? 14 : radius(Role.INPUT)) * density, false);
        outline.setStroke(Math.max(1, Math.round(1.5f * density)), errorColor());
        bind(view, new LayerDrawable(new Drawable[]{inline ? frostedCardBackground()
                : background(Role.INPUT), outline}));
    }

    void apply(View view, Role role) { apply(view, role, radius(role)); }
    void apply(View view, Role role, float radiusDp) {
        if (role == Role.CARD) cards.put(view, Boolean.TRUE);
        else cards.remove(view);
        bind(view, background(role, radiusDp));
        if (role == Role.LIST_ITEM) {
            // The rounded background owns press feedback; no rectangular foreground above it.
            view.setForeground(null);
            view.setElevation(style == Style.FLAT ? 0 : (dark ? .5f : 1f)*density);
        }
        if (role == Role.CARD) view.setElevation(style == Style.MATTE
                ? (dark ? 0 : .75f*density) : style == Style.ORIGINAL_GLASS_TABS && categorySurface && !dark ? density : 0);
        if (view instanceof TextView && !(view instanceof android.widget.EditText)
                && (role == Role.CHOICE || role == Role.PRIMARY || role == Role.ACTION || role == Role.INLINE_ACTION)) {
            int horizontal = Math.round((role == Role.INLINE_ACTION ? 12 : 14)*density);
            int vertical = role == Role.INLINE_ACTION ? Math.round(10*density) : 0;
            view.setPadding(Math.max(view.getPaddingLeft(), horizontal), Math.max(view.getPaddingTop(), vertical),
                    Math.max(view.getPaddingRight(), horizontal), Math.max(view.getPaddingBottom(), vertical));
            ((TextView)view).setMinHeight(Math.max(((TextView)view).getMinHeight(), Math.round(48*density)));
        }
        if (view instanceof TextView && (role == Role.CHOICE || role == Role.PRIMARY)) {
            ((TextView)view).setTextColor(new ColorStateList(new int[][]{
                    new int[]{-android.R.attr.state_enabled}, new int[]{android.R.attr.state_selected},
                    new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{dark ? 0xff777788 : 0xff92929e,
                            role == Role.PRIMARY && style == Style.MATTE ? primaryForeground() : choiceForeground(),
                            role == Role.PRIMARY && style == Style.MATTE ? primaryForeground() : choiceForeground(),
                            role == Role.PRIMARY ? (style == Style.MATTE ? primaryForeground() : style == Style.ORIGINAL_GLASS_TABS ? 0xff183c3b : accent()) : foreground()}));
        }
    }
    /** Compact homepage switch, with full 48dp native Switch hit target retained. */
    void compactSwitchControl(android.widget.Switch toggle) {
        HomeSwitchTrack tracks = new HomeSwitchTrack(density,
                dark ? 0xff454655 : 0xffbfc5cf, SettingsUi.ACCENT);
        toggle.addOnAttachStateChangeListener(tracks);
        GradientDrawable empty = shape(Color.TRANSPARENT, 0, false);
        empty.setSize(Math.round(18 * density), Math.round(22 * density));
        toggle.setThumbTintList(null);
        toggle.setTrackTintList(null);
        toggle.setThumbDrawable(empty);
        toggle.setTrackDrawable(tracks);
        toggle.setSwitchMinWidth(Math.round(40 * density));
        toggle.setShowText(false);
        toggle.setSplitTrack(false);
        toggle.setMinHeight(Math.round(48 * density));
    }

    void switchControl(android.widget.Switch toggle) {
        int[][] states = {{-android.R.attr.state_enabled, android.R.attr.state_checked},
                {-android.R.attr.state_enabled}, {android.R.attr.state_checked}, {}};
        // Soul's official settings use these full-state assets (45 x 26dp).
        android.content.res.Resources resources=toggle.getContext().getResources();
        String host=toggle.getContext().getPackageName();
        int onId=resources.getIdentifier("c_st_ic_setting_switch_on","drawable",host);
        int offId=resources.getIdentifier("c_st_ic_setting_switch_off","drawable",host);
        if(onId!=0 && offId!=0) {
            StateListDrawable track=new StateListDrawable();
            for(int i=0;i<states.length;i++) {
                boolean checked=i==0 || i==2;
                Drawable asset=resources.getDrawable(checked?onId:offId,toggle.getContext().getTheme()).mutate();
                if(checked) {
                    // Preserve white thumb pixels while shifting the host turquoise to plugin mint.
                    float red=255f/(255-32),green=(255-191f)/(255-196),blue=(255-169f)/(255-200);
                    asset.setColorFilter(new android.graphics.ColorMatrixColorFilter(new float[]{
                        red,0,0,0,255*(1-red), 0,green,0,0,255*(1-green),
                        0,0,blue,0,255*(1-blue), 0,0,0,1,0}));
                }
                if(i<2) asset.setAlpha(110);
                track.addState(states[i],asset);
            }
            GradientDrawable transparentThumb=new GradientDrawable();
            transparentThumb.setColor(Color.TRANSPARENT);
            transparentThumb.setSize(Math.round(22*density),Math.round(26*density));
            toggle.setThumbTintList(null); toggle.setTrackTintList(null);
            toggle.setThumbDrawable(transparentThumb); toggle.setTrackDrawable(track);
            toggle.setShowText(false); toggle.setSplitTrack(false);
            toggle.setSwitchMinWidth(Math.round(45*density));
            toggle.setMinHeight(Math.round(48*density));
            return;
        }
        toggle.setThumbTintList(null);
        toggle.setTrackTintList(null);
        StateListDrawable thumbs = new StateListDrawable(), tracks = new StateListDrawable();
        int[] thumbColors = {dark ? 0xff819d99 : 0xffd4e6e2, dark ? 0xff70717e : 0xffc4c9ce, 0xfff7fffe, dark ? 0xffa7a9bb : 0xffffffff};
        int[] trackColors = {dark ? 0xff285950 : 0xffaed5cd, dark ? 0xff30313f : 0xffe0e3e8, 0xff00bfa9, dark ? 0xff454655 : 0xffbfc5cf};
        for(int i=0;i<states.length;i++) {
            GradientDrawable thumb = new GradientDrawable();
            thumb.setShape(GradientDrawable.OVAL); thumb.setColor(thumbColors[i]);
            thumb.setSize(Math.round(24*density),Math.round(24*density));
            thumbs.addState(states[i],thumb);
            GradientDrawable track = new GradientDrawable();
            track.setColor(trackColors[i]); track.setCornerRadius(14*density);
            track.setSize(Math.round(48*density),Math.round(28*density));
            tracks.addState(states[i],track);
        }
        toggle.setThumbDrawable(thumbs); toggle.setTrackDrawable(tracks);
        toggle.setShowText(false); toggle.setSplitTrack(false);
        toggle.setSwitchMinWidth(Math.round(48*density));
        toggle.setMinHeight(Math.round(48*density));
    }

    void checkControl(android.widget.CompoundButton button) {
        button.setButtonTintList(new ColorStateList(new int[][]{
                new int[]{-android.R.attr.state_enabled}, new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{dark ? 0xff62626f : 0xffb7bdc2, accent(), dark ? 0xff828296 : 0xff666673}));
        apply(button, Role.ICON);
    }
    void window(Window window, Role role) {
        Drawable drawable = background(role);
        watch(window.getDecorView(), drawable);
        window.setBackgroundDrawable(drawable);
    }
    void frostedWindow(Window window) {
        GradientDrawable glass = shape(dark ? 0xc21a1b2c : 0xc9f5f6f8,
                radius(Role.PANEL) * density, false);
        glass.setStroke(Math.max(1, Math.round(.5f * density)), dark ? 0x35ffffff : 0x99ffffff);
        window.setBackgroundDrawable(glass);
    }
    void restoreWindow(Window window, Drawable background) {
        window.setBackgroundDrawable(background);
    }
    static void bind(View view, Drawable drawable) {
        int l=view.getPaddingLeft(), t=view.getPaddingTop(), r=view.getPaddingRight(), b=view.getPaddingBottom();
        watch(view, drawable);
        view.setBackground(drawable);
        view.setPadding(l,t,r,b);
    }
    private static void watch(View view, Drawable drawable) {
        Drawable old = bound.put(view, drawable);
        if (old != drawable) release(old);
        if (observed.put(view, Boolean.TRUE) != null) return;
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) { Drawable d=bound.get(v); if(d!=null)d.invalidateSelf(); }
            public void onViewDetachedFromWindow(View v) { release(bound.get(v)); }
        });
    }
    static void release(Drawable drawable) {
        if (drawable instanceof GlassCardBackground) ((GlassCardBackground)drawable).release();
        else if (drawable instanceof InsetDrawable) release(((InsetDrawable)drawable).getDrawable());
        else if (drawable instanceof LayerDrawable) {
            LayerDrawable layers=(LayerDrawable)drawable;
            for(int i=0;i<layers.getNumberOfLayers();i++)release(layers.getDrawable(i));
        } else if (drawable instanceof StateListDrawable) {
            StateListDrawable states=(StateListDrawable)drawable;
            if (android.os.Build.VERSION.SDK_INT >= 29)
                for(int i=0;i<states.getStateCount();i++)release(states.getStateDrawable(i));
            else release(states.getCurrent());
        }
    }
    /** One continuous contour: rounded tip and tangent shoulders, no triangular seam. */
    static final class BubbleSurface extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final boolean dark, below;
        private android.graphics.Shader backdrop;
        private final float pointer, density;
        BubbleSurface(boolean dark, boolean below, float pointer, float density) {
            this.dark = dark; this.below = below; this.pointer = pointer; this.density = density;
        }
        @Override public void draw(Canvas canvas) {
            float d = density, w = getBounds().width(), h = getBounds().height();
            float l = 4*d, r = w - 4*d, t = 12*d, b = h - 4*d, radius = 12*d;
            float p = Math.max(l + radius + 12*d, Math.min(pointer, r - radius - 12*d));
            path.reset();
            path.moveTo(l + radius, t);
            path.lineTo(p - 12*d, t);
            path.cubicTo(p - 5*d, t, p - 5*d, 4*d, p, 4*d);
            path.cubicTo(p + 5*d, 4*d, p + 5*d, t, p + 12*d, t);
            path.lineTo(r - radius, t);
            path.quadTo(r, t, r, t + radius);
            path.lineTo(r, b - radius);
            path.quadTo(r, b, r - radius, b);
            path.lineTo(l + radius, b);
            path.quadTo(l, b, l, b - radius);
            path.lineTo(l, t + radius);
            path.quadTo(l, t, l + radius, t);
            path.close();
            int save = canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            if (!below) { canvas.translate(0, h); canvas.scale(1, -1); }
            paint.setStyle(Paint.Style.FILL);
            if (backdrop != null) {
                // Counter the pointer orientation so the sampled background stays upright.
                int layer = canvas.save();
                canvas.clipPath(path);
                if (!below) { canvas.translate(0, h); canvas.scale(1, -1); }
                paint.setShader(backdrop);
                paint.setColor(Color.WHITE);
                canvas.drawRect(0, 0, w, h, paint);
                paint.setShader(null);
                canvas.restoreToCount(layer);
            }
            paint.setColor(backdrop == null ? (dark ? 0xff343446 : Color.WHITE)
                    : (dark ? 0xb31a1b2c : 0xb3f5f6f8));
            paint.setShadowLayer(3*d, 0, 0, dark ? 0x44000000 : 0x28000000);
            canvas.drawPath(path, paint);
            paint.clearShadowLayer();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(.6f*d);
            paint.setColor(dark ? 0xff555565 : 0xffe3e3e8);
            canvas.drawPath(path, paint);
            canvas.restoreToCount(save);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}
