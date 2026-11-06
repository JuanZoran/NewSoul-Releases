package dev.local.newsoul;
import android.os.Looper;
final class PreferenceRuntime {
    static final java.util.Map<String, Integer> calls = new java.util.LinkedHashMap<>();
    static java.util.function.BooleanSupplier accountEnabled = () -> false;
    static boolean observedAccountEnabled;
    static void hit(String key) {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new AssertionError("UI refresh off main thread");
        calls.merge(key, 1, Integer::sum);
    }
}
final class AdControls {
    static final String[][] ITEMS = {{"ad_feed"}, {"ad_detail"}, {"block_splash"}};
    static void refreshViews() { PreferenceRuntime.hit("ads"); }
}
final class SquareFeedFilter { static void refresh() { PreferenceRuntime.hit("feed"); SquareTimeOrder.refresh(); } }
final class SquareTimeOrder {
    static final String ENABLED = "square_time_order_v1";
    static void refresh() { PreferenceRuntime.hit("order"); }
}
final class AccountAutomationControls {
    static final String SIGN = "account_auto_sign_v1", HIDE = "account_auto_hide_v1";
    static void changed() { PreferenceRuntime.hit("account"); PreferenceRuntime.observedAccountEnabled = PreferenceRuntime.accountEnabled.getAsBoolean(); }
}
final class FollowDisguiseControls {
    static final String FOLLOW = "profile_disguise_follow_", FOLLOWED = "profile_disguise_followed_";
    static final String VISITOR = "profile_disguise_visitor_", DAYS = "profile_disguise_days_";
    static void refresh() { PreferenceRuntime.hit("disguise"); }
}
final class ChatTimeControls {
    static final String ENABLED = "chat_time_format_enabled_v1", EVERY = "chat_time_every_message_v1", FORMAT = "chat_time_format_v1";
    static void refreshUi() { PreferenceRuntime.hit("time"); }
}
final class ChatRecallControls {
    static final String ENABLED = "chat_anti_recall", STYLE = "chat_recall_style";
    static void refreshUi() { PreferenceRuntime.hit("recall"); }
}
final class AutoReadControls { static final String ENABLED = "chat_auto_read_v1"; }
final class InteractionPrivacyControls { static final String RECEIPT = "chat_block_read_receipt_v1"; }
