package dev.local.newsoul;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.UsingType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.FieldMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;

/** Semantic selectors shared verbatim by the module and the APK-only verification runner. */
final class HostDexRules {
    static final String REVISION = "3-dexkit-2.0.7";
    static final String CONTEXT = "context", AD_ORIGIN = "ad-origin", AD_PROVIDER = "ad-provider";
    static final String FOLLOW = "profile-follow", DAYS = "profile-days", COLLAPSE = "collapse";
    static final String RECEIVE = "chat-receive", READ_ACK = "chat-read-ack";
    static final String PROFILE = "cn.soulapp.android.component.home.revision.RevisionProfileProvider";
    static final String USER = "com.soul.component.componentlib.service.user.bean.User";
    static final String MEDIA_MENU = "cn.soulapp.android.component.chat.inputmenu.BaseMediaMenu";
    static final String[] KEYS = {CONTEXT, AD_ORIGIN, AD_PROVIDER, FOLLOW, DAYS, COLLAPSE, RECEIVE, READ_ACK};

    private HostDexRules() {}

    static List<String> find(DexKitBridge bridge, String key) {
        List<String> result = new ArrayList<>();
        if (AD_ORIGIN.equals(key) || AD_PROVIDER.equals(key)) {
            ClassMatcher matcher = AD_ORIGIN.equals(key)
                    ? ClassMatcher.create().source("ConversationAdPresenter.java")
                        .usingStrings("MsgFragment requestChatMsgAd start", "ADConversation")
                    : ClassMatcher.create().source("MsgConversationAdProvider.java")
                        .superClass("com.chad.library.adapter.base.provider.BaseItemProvider")
                        .usingStrings("RED_DOT_CLICKED_TIME", "KEY_USER_ID_ECPT");
            bridge.findClass(FindClass.create().matcher(matcher))
                    .forEach(data -> result.add(data.getDescriptor()));
            return result;
        }
        MethodMatcher matcher;
        if (RECEIVE.equals(key)) {
            matcher = MethodMatcher.create().declaredClass(ClassMatcher.create().source("ChatHandler.java"))
                    .name("run").paramCount(0).returnType("void")
                    .addInvoke(MethodMatcher.create().declaredClass("cn.soulapp.imlib.listener.MsgListener")
                        .name("onChatMsgReceive").paramTypes("java.util.List").returnType("void"));
        } else if (READ_ACK.equals(key)) {
            matcher = MethodMatcher.create().declaredClass("cn.soulapp.imlib.Conversation")
                    .paramTypes("cn.soulapp.imlib.msg.ImMessage").returnType("void")
                    .usingStrings("key_is_notify_readed")
                    .addInvoke(MethodMatcher.create().declaredClass("cn.soulapp.imlib.msg.chat.ChatMessage")
                        .paramTypes("int").returnType("void")
                        .addUsingField(FieldMatcher.create().declaredClass("cn.soulapp.imlib.msg.chat.ChatMessage")
                            .name("msgType").type("int"), UsingType.Write));
        } else if (CONTEXT.equals(key)) {
            matcher = MethodMatcher.create()
                    .declaredClass(ClassMatcher.create().source("CornerStone.java"))
                    .modifiers(Modifier.PUBLIC | Modifier.STATIC).paramCount(0)
                    .returnType("android.content.Context")
                    .addUsingField(FieldMatcher.create().type("android.app.Application")
                        .modifiers(Modifier.STATIC), UsingType.Read);
        } else if (FOLLOW.equals(key) || DAYS.equals(key)) {
            matcher = MethodMatcher.create().declaredClass(PROFILE)
                    .modifiers(Modifier.PRIVATE | Modifier.FINAL).returnType("void")
                    .paramTypes(USER, PROFILE + "$ViewHolder");
            if (FOLLOW.equals(key)) {
                String count = "cn.soulapp.android.component.home.api.user.user.bean.UserFollowCount";
                matcher.addUsingField(FieldMatcher.create().declaredClass(count).name("followNum").type("int"), UsingType.Read)
                        .addUsingField(FieldMatcher.create().declaredClass(count).name("beFollowNum").type("int"), UsingType.Read);
            } else {
                matcher.addUsingField(FieldMatcher.create().declaredClass(USER)
                        .name("registerDay").type("java.lang.String"), UsingType.Read);
            }
            matcher.addInvoke(MethodMatcher.create().declaredClass("android.widget.TextView")
                    .name("setText").paramTypes("java.lang.CharSequence"));
        } else if (COLLAPSE.equals(key)) {
            matcher = MethodMatcher.create().declaredClass(MEDIA_MENU)
                    .modifiers(Modifier.PUBLIC | Modifier.FINAL).paramCount(0).returnType("boolean")
                    .addInvoke(MethodMatcher.create().declaredClass(MEDIA_MENU)
                        .name("getKeyboardShow").paramCount(0).returnType("boolean"))
                    .addInvoke(MethodMatcher.create()
                        .declaredClass("cn.soulapp.lib_input.behavior.BottomSheetBehavior")
                        .paramTypes("int").returnType("void"));
        } else throw new IllegalArgumentException("Unknown host rule: " + key);
        bridge.findMethod(FindMethod.create().matcher(matcher))
                .forEach(data -> result.add(data.getDescriptor()));
        if (result.isEmpty() && COLLAPSE.equals(key)) {
            // Java 4.88 implementation reads keyboard state directly; Kotlin hosts use its getter.
            bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().declaredClass(MEDIA_MENU)
                    .modifiers(Modifier.PUBLIC).paramCount(0).returnType("boolean")
                    .addInvoke(MethodMatcher.create().declaredClass("cn.soulapp.lib_input.behavior.BottomSheetBehavior")
                        .name("getState").paramCount(0).returnType("int"))
                    .addInvoke(MethodMatcher.create().declaredClass("cn.soulapp.lib_input.behavior.BottomSheetBehavior")
                        .name("setState").paramTypes("int").returnType("void"))))
                    .forEach(data -> result.add(data.getDescriptor()));
        }
        return result;
    }
}
