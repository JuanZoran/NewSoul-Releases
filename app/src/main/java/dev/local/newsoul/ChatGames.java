package dev.local.newsoul;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import java.lang.reflect.*;
import java.util.*;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;

/** Ordinary one-to-one game messages in Soul 6.37.0. No automated sending. */
final class ChatGames {
    static boolean diceReady, fingerReady;
    private static final ThreadLocal<Integer> SELECTED = new ThreadLocal<>();
    private static final Set<Activity> OPEN = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static XposedModule module;
    // Protocol values are populated from Soul's own renderer mapping.
    private static final String[] FINGERS = {"布", "剪刀", "石头"};
    private static Constructor<?> resultConstructor;
    private static Field resultNumber;

    static void install(XposedModule m, ClassLoader loader) {
        module=m; diceReady=false; fingerReady=false;
        try {
            resultConstructor=HostBindings.gameResult(loader);
            resultNumber=HostBindings.gameNumber(loader);
            module.hook(resultConstructor)
                .setId("new-soul/game-message-result")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    Integer selected=SELECTED.get();
                    if(selected!=null && selected>0) {
                        info("game message constructed with selected result="+selected);
                        return chain.proceed(new Object[]{selected});
                    }
                    return chain.proceed();
                });
            HostBindings.Chat chat = new HostBindings.Chat(loader);
            diceReady=hookSender(loader,chat,true);
            fingerReady=hookSender(loader,chat,false);
            info("chat games installed; dice="+diceReady+" finger="+fingerReady);
        } catch(Throwable error) {module.log(Log.ERROR,"SoulHook102","chat games unavailable",error);}
    }
    private static boolean hookSender(ClassLoader loader,HostBindings.Chat chat,boolean dice) {
        String name = dice ? "dice" : "finger";
        Field fragment = chat.senderFragment;
        try {
            Method method=HostBindings.gameSend(loader,dice);
            module.hook(method).setId("new-soul/game-picker/"+method.getName())
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    if(SELECTED.get()!=null) return chain.proceed();
                    Object sender=chain.getThisObject();Object frag=fragment.get(sender);
                    Activity activity=(Activity)chat.activity.invoke(frag);
                    if(activity==null || activity.isFinishing() || activity.isDestroyed()
                        || !PluginPreferences.get(activity).getBoolean(dice?"game_dice":"game_finger",true))
                        return chain.proceed();
                    final GameSendGuard guard;
                    try { guard = new GameSendGuard(activity, frag, sender, fragment); }
                    catch (Exception unavailable) {
                        Toast.makeText(activity,"聊天已变化，请重新打开选择器",Toast.LENGTH_SHORT).show();
                        return null;
                    }
                    MAIN.post(()->{
                        try { guard.validate(); }
                        catch (Exception stale) { return; }
                        picker(activity,dice,value->{
                        try { guard.validate(); }
                        catch (Exception stale) {
                            Toast.makeText(activity,"聊天已变化，请重新打开选择器",Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            // Re-enter the normal hook chain, bypass only our picker while
                            // constraining message construction to this synchronous send call.
                            SELECTED.set(value);
                            module.getInvoker(method).invoke(sender);
                        } catch(Throwable error) {
                            module.log(Log.ERROR,"SoulHook102","selected game send failed",error);
                            Toast.makeText(activity,"发送未完成，请检查聊天页面",Toast.LENGTH_SHORT).show();
                        } finally {SELECTED.remove();}
                    });
                    });
                    return null;
                });
            module.deoptimize(method); // Prevent DiceFingerMsg(int) constructor inlining.
            return true;
        } catch(Throwable error) {module.log(Log.ERROR,"SoulHook102","game hook unavailable: "+name,error);return false;}
    }
    private interface Selection {void accept(int value);}
    private static void picker(Activity activity,boolean dice,Selection action) {
        if(activity.isFinishing() || activity.isDestroyed() || OPEN.contains(activity)) return;
        OPEN.add(activity);
        try {
            GamePicker.show(activity,dice,action==null,value->{
                if(action!=null) {action.accept(value);return;}
                int actual=value==0?new java.security.SecureRandom().nextInt(dice?6:3)+1:value;
                boolean valid=verifyResult(actual);
                String label=dice?actual+" 点":FINGERS[actual-1];
                Toast.makeText(activity,valid?label+" · 未发送":"预览校验失败",Toast.LENGTH_SHORT).show();
            },()->OPEN.remove(activity));
        } catch(Throwable error) {
            OPEN.remove(activity);module.log(Log.ERROR,"SoulHook102","game picker failed",error);
        }
    }

    private static boolean verifyResult(int selected) {
        try {
            // Constructs only a local value object. No chat sender, packet or network call.
            SELECTED.set(selected);
            Object result=resultConstructor.newInstance(0);
            boolean ok=resultNumber.getInt(result)==selected;
            info("game local dry-run: result="+selected+" verified="+ok+"; no message sent");
            return ok;
        } catch(Throwable error) {
            module.log(Log.ERROR,"SoulHook102","game local dry-run failed",error);return false;
        } finally { SELECTED.remove(); }
    }
    static void preview(Activity activity,boolean dice) {picker(activity,dice,null);}
    private static void info(String text) {Log.i("SoulHook102",text);module.log(Log.INFO,"SoulHook102",text);}
}
