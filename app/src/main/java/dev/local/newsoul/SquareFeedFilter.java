package dev.local.newsoul;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Collapses eligible feed rows without mutating Soul's data or pagination. */
final class SquareFeedFilter {
    static final String ENABLED = "feed_filter_enabled";
    static final String GENDER = "feed_filter_gender";
    static final String UNKNOWN = "feed_filter_keep_unknown";
    static final String KEYWORDS_ENABLED = "feed_keywords_enabled_v1";
    static final String KEYWORDS = "feed_keywords_v1";
    static final String NAME_KEYWORDS_ENABLED = "feed_keywords_name_enabled_v1";
    static final String NAME_KEYWORDS = "feed_keywords_name_v1";
    static final String CITY_PAGES = "feed_filter_city_pages_v1";
    static final String VIP = "feed_filter_vip_v1";
    static final String CONTENT_ENABLED = "feed_filter_content_enabled_v1";
    static final String CONTENT_TYPES = "feed_filter_content_types_v1";
    static boolean contentReady;
    private static java.lang.reflect.Field mediaType;
    static boolean vipReady;
    private static java.lang.reflect.Field superVip;
    static boolean cityReady;
    private static Class<?> cityActivity;
    static boolean keywordsReady;
    private static java.lang.reflect.Field content, title, contentTitle, tags, tagName;
    static boolean nameKeywordsReady;
    private static java.lang.reflect.Method showName;
    private static final Map<String, String> cachedWords = new java.util.HashMap<>();
    private static final Map<String, FeedKeywordRules> keywordRules = new java.util.HashMap<>();
    static boolean ready;
    private static boolean legacyGenderLookup;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<View, Row> rows = new WeakHashMap<>();
    private static Class<?> postClass;
    private static java.lang.reflect.Field author, genderField;
    private static boolean refreshScheduled;
    private static int hiddenCount;

    private static final class Row {
        Object post;
        boolean hidden, city, dataFiltered;
        int height, visibility, topMargin, bottomMargin;
    }

    static void install(XposedModule module, ClassLoader loader) {
        try {
            postClass = loader.loadClass("cn.soulapp.android.square.post.bean.Post");
            boolean legacy = HostBindings.legacyChat(loader);
            legacyGenderLookup = legacy;
            if (legacy) {
                // Old posts carry author identity, while gender is in userDetail's User model.
                postClass.getField("authorIdEcpt");
            } else {
                author = postClass.getField("postAuthorModel");
                genderField = author.getType().getField("gender");
            }
            try {
                mediaType = postClass.getField("type");
                contentReady = mediaType.getType().isEnum();
            } catch (ReflectiveOperationException error) {
                Log.w("SoulHook102", "feed content type unavailable", error);
            }
            try {
                superVip = postClass.getField("superVIP");
                vipReady = superVip.getType() == boolean.class;
            } catch (ReflectiveOperationException error) { Log.w("SoulHook102", "VIP filter unavailable", error); }
            try {
                content = postClass.getField("content");
                title = postClass.getField("title");
                contentTitle = legacy ? null : postClass.getField("contentTitle");
                tags = postClass.getField("tags");
                tagName = loader.loadClass("cn.soulapp.android.square.bean.Tag").getField("name");
                keywordsReady = true;
            } catch (ReflectiveOperationException error) {
                Log.w("SoulHook102", "feed keyword fields unavailable", error);
            }
            try {
                showName = HostBindings.postMethod(postClass, "showName");
                nameKeywordsReady = showName.getReturnType() == String.class;
            } catch (ReflectiveOperationException error) {
                nameKeywordsReady = false;
                Log.w("SoulHook102", "feed username field unavailable", error);
            }
            HostBindings.SquareFeed binding = new HostBindings.SquareFeed(loader, legacy);
            Class<?> adapter = binding.adapterType, holder = binding.holderType;
            java.lang.reflect.Method bind = binding.bind, recycle = binding.recycle, attach = binding.attach;
            module.hook(bind)
                    .setId("new-soul/feed-filter-bind")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (!adapter.isInstance(chain.getThisObject()) || !holder.isInstance(chain.getArg(0))) return chain.proceed();
                        View view = (View) holder.getField("itemView").get(chain.getArg(0));
                        SquareIpLocation.clear(view);
                        SquareRegistrationDays.clear(view);
                        restore(view, rows.remove(view));
                        Object result = chain.proceed();
                        Row row = new Row();
                        row.post = chain.getArg(1);
                        row.dataFiltered = SquareTimeOrder.manages(chain.getThisObject());
                        rows.put(view, row);
                        // Binding can precede attachment; apply again before its first draw.
                        apply(view, row);
                        view.post(() -> { if (rows.get(view) == row) apply(view, row); });
                        return result;
                    });
            module.hook(recycle)
                    .setId("new-soul/feed-filter-recycle")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (!adapter.isInstance(chain.getThisObject()) || !holder.isInstance(chain.getArg(0))) return chain.proceed();
                        View view = (View) holder.getField("itemView").get(chain.getArg(0));
                        SquareIpLocation.clear(view);
                        SquareRegistrationDays.clear(view);
                        restore(view, rows.remove(view));
                        return chain.proceed();
                    });
            module.hook(attach)
                    .setId("new-soul/feed-filter-attach")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        View view = (View) holder.getField("itemView").get(chain.getArg(0));
                        Row row = rows.get(view);
                        if (row != null) apply(view, row);
                        return result;
                    });
            module.deoptimize(bind);
            module.deoptimize(recycle);
            module.deoptimize(attach);
            ready = true;
            Log.i("SoulHook102", "square feed filter ready");
        } catch (Throwable e) { Log.e("SoulHook102", "square feed filter unavailable", e); }
        if (ready) installCity(module, loader);
    }

    private static final View.OnAttachStateChangeListener cityAttachment = new View.OnAttachStateChangeListener() {
        public void onViewAttachedToWindow(View view) {
            Row row = rows.get(view);
            if (row != null && row.city) apply(view, row);
        }
        public void onViewDetachedFromWindow(View view) {
            Row row = rows.get(view);
            if (row != null && row.city) restore(view, row);
        }
    };

    private static void installCity(XposedModule module, ClassLoader loader) {
        try {
            boolean legacy = HostBindings.legacyChat(loader);
            HostBindings.CityFeed binding = new HostBindings.CityFeed(loader, postClass, legacy);
            cityActivity = binding.activityType;
            Class<?> adapter = binding.adapterType;
            java.lang.reflect.Field itemView = binding.itemView;
            java.lang.reflect.Method item = binding.item, bind = binding.bind;
            module.hook(bind).setId("new-soul/feed-filter-city-bind")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        View view = (View) itemView.get(legacy ? chain.getThisObject() : chain.getArg(0));
                        if (legacy && !city(view.getContext())) return chain.proceed();
                        SquareIpLocation.clear(view);
                        SquareRegistrationDays.clear(view);
                        restore(view, rows.remove(view));
                        Object result = chain.proceed();
                        // i(adapterPosition) accounts for headers and returns null for header/footer rows.
                        Object post;
                        try { post = legacy ? chain.getArg(0) : item.invoke(chain.getThisObject(), chain.getArg(1)); }
                        catch (ReflectiveOperationException error) {
                            DebugDiagnostics.failure("feed.city.item_failed", error);
                            return result;
                        }
                        Row row = new Row(); row.post = post; row.city = true;
                        rows.put(view, row);
                        view.removeOnAttachStateChangeListener(cityAttachment);
                        view.addOnAttachStateChangeListener(cityAttachment);
                        apply(view, row);
                        view.post(() -> { if (rows.get(view) == row) apply(view, row); });
                        return result;
                    });
            for (java.lang.reflect.Method method : adapter.getDeclaredMethods()) module.deoptimize(method);
            cityReady = true;
            DebugDiagnostics.registration("feed.city", null);
        } catch (Throwable error) {
            cityReady = false;
            DebugDiagnostics.registration("feed.city", error);
        }
    }

    private static boolean city(Context context) {
        java.util.Set<Context> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        while (context != null && seen.add(context)) {
            if (cityActivity != null && cityActivity.isInstance(context)) return true;
            context = context instanceof ContextWrapper ? ((ContextWrapper) context).getBaseContext() : null;
        }
        return false;
    }


    private static boolean square(View v) {
        for (View p = v; p != null;) {
            if (p.getId() != View.NO_ID) try {
                if ("pager_square".equals(p.getResources().getResourceEntryName(p.getId()))) return true;
            } catch (RuntimeException ignored) {}
            p = p.getParent() instanceof View ? (View) p.getParent() : null;
        }
        return false;
    }

    private static void apply(View view, Row row) {
        SquareIpLocation.update(view, row.post, !row.city && square(view));
        SquareRegistrationDays.update(view, row.post, !row.city && square(view));
        if (row.dataFiltered) { restore(view, row); return; }
        if (!ready || !(row.city
                ? cityReady && city(view.getContext()) && PluginPreferences.get(view.getContext()).getBoolean(CITY_PAGES, false)
                : square(view))) { restore(view, row); return; }
        boolean show = allows(view.getContext(), row.post);
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (!show && !row.hidden && lp != null) {
            row.height = lp.height; row.visibility = view.getVisibility();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) lp;
                row.topMargin = margins.topMargin; row.bottomMargin = margins.bottomMargin;
                margins.topMargin = margins.bottomMargin = 0;
            }
            row.hidden = true;
            hiddenCount++;
            if (hiddenCount <= 5 || hiddenCount % 25 == 0) Log.i("SoulHook102", "feed filter rows hidden=" + hiddenCount);
            lp.height = 0;
            view.setVisibility(View.GONE);
            view.setLayoutParams(lp);
        } else if (show) restore(view, row);
    }

    private static void restore(View view, Row row) {
        if (row == null || !row.hidden) return;
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp != null) {
            lp.height = row.height;
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) lp).topMargin = row.topMargin;
                ((ViewGroup.MarginLayoutParams) lp).bottomMargin = row.bottomMargin;
            }
            view.setLayoutParams(lp);
        }
        view.setVisibility(row.visibility);
        row.hidden = false;
    }

    /** Metadata display never reprojects/rebinds the feed. Only an active days rule can change membership. */
    static void metadataResolved(String key, boolean registration) {
        String account = HostAccountHttp.account();
        if (account.isEmpty() || !key.startsWith(account + ":")) return;
        Context context = HostAccountHttp.context();
        if (registration && context != null
                && (PluginPreferences.get(context).getBoolean(RegistrationDaysFilter.ENABLED, false)
                    || legacyGenderLookup && PluginPreferences.get(context).getBoolean(ENABLED, false))) {
            refresh();
            return;
        }
        for (Map.Entry<View, Row> entry : new ArrayList<>(rows.entrySet())) {
            View view = entry.getKey();
            Row row = entry.getValue();
            if (view == null || row == null || row.city || row.hidden
                    || postClass == null || !postClass.isInstance(row.post) || !square(view)) continue;
            try {
                Object id = HostAccountHttp.field(row.post, "authorIdEcpt");
                if (!(id instanceof String) || !key.equals(account + ":" + id)) continue;
                // Read the current row and current preference, never a captured recycled ViewHolder.
                if (registration) SquareRegistrationDays.update(view, row.post, true);
                else SquareIpLocation.update(view, row.post, true);
            } catch (Exception error) {
                DebugDiagnostics.failure("feed.metadata.display", error);
            }
        }
    }

    static void refresh() {
        SquareTimeOrder.refresh();
        if (refreshScheduled) return;
        refreshScheduled = true;
        main.postDelayed(() -> {
            refreshScheduled = false;
            for (Map.Entry<View, Row> entry : new ArrayList<>(rows.entrySet())) {
                if (entry.getKey() != null) apply(entry.getKey(), entry.getValue());
            }
        }, 80);
    }

    /** Whether the square projection can change rows, including pending registration lookups. */
    static boolean hasRules(Context ctx) {
        if (!ready) return false;
        SharedPreferences prefs = PluginPreferences.get(ctx);
        if (contentReady && prefs.getBoolean(CONTENT_ENABLED, false)
                && FeedContentRules.normalize(prefs.getInt(CONTENT_TYPES, FeedContentRules.ALL)) != FeedContentRules.ALL) return true;
        if (vipReady && prefs.getBoolean(VIP, false)) return true;
        if (keywordsReady && prefs.getBoolean(KEYWORDS_ENABLED, false)) {
            if (!keywords(prefs, KEYWORDS).words.isEmpty()) return true;
        }
        if (nameKeywordsReady && prefs.getBoolean(NAME_KEYWORDS_ENABLED, false)
                && !keywords(prefs, NAME_KEYWORDS).words.isEmpty()) return true;
        if (prefs.getBoolean(ENABLED, false) && !"ANY".equals(prefs.getString(GENDER, "FEMALE"))) return true;
        return prefs.getBoolean(RegistrationDaysFilter.ENABLED, false)
                && AccountAutomationRules.validRange(Math.max(1, prefs.getInt(RegistrationDaysFilter.MIN, 1)),
                        prefs.getInt(RegistrationDaysFilter.MAX, 0));
    }

    static boolean allows(Context ctx, Object post) {
        if (!ready || !postClass.isInstance(post)) return true;
        SharedPreferences prefs = PluginPreferences.get(ctx);
        if (contentReady && prefs.getBoolean(CONTENT_ENABLED, false)) {
            try {
                Object type = mediaType.get(post);
                if (type instanceof Enum<?> && !FeedContentRules.allows(
                        prefs.getInt(CONTENT_TYPES, FeedContentRules.ALL), ((Enum<?>) type).name())) return false;
            } catch (ReflectiveOperationException ignored) { /* Unreadable types stay visible. */ }
        }
        if (vipReady && prefs.getBoolean(VIP, false)) {
            try { if (superVip.getBoolean(post)) return false; }
            catch (ReflectiveOperationException ignored) { }
        }
        if (keywordsReady && prefs.getBoolean(KEYWORDS_ENABLED, false) && blocksKeywords(prefs, post)) return false;
        if (nameKeywordsReady && prefs.getBoolean(NAME_KEYWORDS_ENABLED, false)) {
            try {
                if (keywords(prefs, NAME_KEYWORDS).blocks((String) showName.invoke(post))) return false;
            } catch (ReflectiveOperationException ignored) { /* Missing names stay visible. */ }
        }
        if (!prefs.getBoolean(ENABLED, false)) return RegistrationDaysFilter.allows(ctx, post);
        String wanted = prefs.getString(GENDER, "FEMALE");
        if (legacyGenderLookup) return RegistrationDaysFilter.allowsGender(ctx, post, wanted,
                prefs.getBoolean(UNKNOWN, false)) && RegistrationDaysFilter.allows(ctx, post);
        String gender = null;
        try {
            Object model = author.get(post);
            if (model != null) {
                // Same mapping as PostHeaderUtil + HeadHelper: 1 uses the female frame.
                int value = genderField.getInt(model);
                gender = value == 1 ? "FEMALE" : value == 0 ? "MALE" : null;
            }
        } catch (ReflectiveOperationException ignored) {}
        // Feed rows do not carry birthdays. Profile models may declare one, but an age
        // rule needs separately verified response values rather than missing/guessed data.
        return FeedFilterRules.matches(wanted, gender,
                prefs.getBoolean(UNKNOWN, false)) && RegistrationDaysFilter.allows(ctx, post);
    }

    private static synchronized FeedKeywordRules keywords(SharedPreferences prefs, String key) {
        String raw = prefs.getString(key, "");
        if (!java.util.Objects.equals(raw, cachedWords.get(key)) || !keywordRules.containsKey(key)) {
            FeedKeywordRules rules;
            try { rules = new FeedKeywordRules(raw); }
            catch (IllegalArgumentException invalid) { rules = new FeedKeywordRules(""); }
            keywordRules.put(key, rules);
            cachedWords.put(key, raw);
        }
        return keywordRules.get(key);
    }

    private static synchronized boolean blocksKeywords(SharedPreferences prefs, Object post) {
        FeedKeywordRules rules = keywords(prefs, KEYWORDS);
        if (rules.words.isEmpty()) return false;
        try {
            if (rules.blocks((String) content.get(post)) || rules.blocks((String) title.get(post))
                    || contentTitle != null && rules.blocks((String) contentTitle.get(post))) return true;
            Object values = tags.get(post);
            if (values instanceof Iterable<?>) for (Object tag : (Iterable<?>) values) {
                if (tag != null && tagName.getDeclaringClass().isInstance(tag)
                        && rules.blocks((String) tagName.get(tag))) return true;
            }
        } catch (ReflectiveOperationException ignored) { /* Keep unfamiliar data visible. */ }
        return false;
    }
}
