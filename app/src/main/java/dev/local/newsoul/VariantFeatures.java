package dev.local.newsoul;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.widget.LinearLayout;
final class VariantFeatures {
    static final String PACKAGE_ID="io.github.juanzoran.newsoul";
    static final String LABEL="New Soul";
    // Stored by Soul, not by this APK: keep the established configuration filename.
    static final String PREFERENCES="dev.local.soulhookdemo.public.settings.v1";
    static boolean hasAi(){return true;}
    static boolean aiEntryReady(){return AiReplyEntry.ready();}
    static void install(SoulHook module,ClassLoader loader) {
        AiReplySender.install(module,loader);
        AiReplyEntry.install(module,loader);
        AiOfficialSuggestionTrigger.install(module,loader);
        AiAutomaticSuggestionCard.installBackHook(module,loader);
        AiReplyDock.installBackHook(module,loader);
        try {
            module.hook(Activity.class.getDeclaredMethod("onPause"))
                .setId("new-soul/ai-cancel-on-pause")
                .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Activity activity=(Activity)chain.getThisObject();
                    try {if("cn.soulapp.android".equals(activity.getPackageName()))AiReplyUi.cancelFor(activity);}
                    catch(Exception ignored){}
                    return chain.proceed();
                });
        } catch(Throwable error){android.util.Log.w("SoulHook102","AI pause cleanup hook unavailable",error);}
    }
    static void observe(Activity activity){ChatContextDiagnostics.observe(activity);AiReplyUi.observe(activity);}
    static void addSettings(SettingsUi.MenuPanel panel){
        Activity activity=panel.activity;
        SharedPreferences prefs=PluginPreferences.get(activity);
        LinearLayout body = SettingsUi.section(panel, "AI 回复",
                "开启替换官方 AI 入口，关闭恢复\n点模型切换，长按管理密钥");
        SettingsUi.MenuSwitch enabled = SettingsUi.addSwitch(body, prefs, AiReplyConfig.ENABLED,
                "使用 New Soul AI", "",
                panel.foreground, panel.secondary);
        SettingsUi.bindAvailability(panel, enabled, VariantFeatures::aiEntryReady);
        SettingsUi.attachSwitchPreview(enabled, panel, () -> AiReplyUi.showPreview(panel));
        AiReplyUi.addSettingsRows(panel, body);
    }

    static Drawable hostDrawable(SettingsUi.MenuPanel panel, String name) {
        int id=panel.activity.getResources().getIdentifier(name,"drawable",panel.activity.getPackageName());
        if(id==0)return null;
        try{return panel.context.getResources().getDrawable(id,panel.context.getTheme()).mutate();}
        catch(android.content.res.Resources.NotFoundException | IllegalArgumentException e){return null;}
    }
    static int hostColor(SettingsUi.MenuPanel panel,String name,int fallback) {
        int id=panel.activity.getResources().getIdentifier(name,"color",panel.activity.getPackageName());
        if(id==0)return fallback;
        try{return panel.context.getResources().getColor(id,panel.context.getTheme());}
        catch(android.content.res.Resources.NotFoundException | IllegalArgumentException e){return fallback;}
    }
}
