package dev.local.newsoul;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Explicit local profile bookmarks, using the host's existing profile action row and route. */
final class ProfileBookmarkControls {
    private static final String HOME = "cn.soulapp.android.component.home.user.UserHomeActivity";
    private static final int ACTION = 0x4e5310;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final WeakHashMap<Object, Binding> BINDINGS = new WeakHashMap<>();
    private static Method account, openHome;
    static boolean ready;
    private static final class Binding {
        final String account, id, name;
        final Object action;
        boolean loaded, saved, busy;
        boolean readFailed;
        Binding(String account, String id, String name, Object action) {
            this.account=account; this.id=id; this.name=name; this.action=action;
        }
    }

    static void install(XposedModule module, ClassLoader loader) {
        try {
            account = HostBindings.account(loader);
            openHome = loader.loadClass(HOME).getDeclaredMethod(HostBindings.legacyChat(loader) ? "J" : "y", String.class, String.class);
            Class<?> sheet = loader.loadClass("cn.soulapp.android.square.SeedsShareDialogFragment");
            Class<?> base = loader.loadClass("cn.soulapp.android.square.BaseSeedsDialogFragment");
            Class<?> operation = loader.loadClass(base.getName()+"$Operate");
            HostShareBindings bindings = new HostShareBindings(loader);
            Field user=bindings.user, post=bindings.post, kind=bindings.kind, items=bindings.actions;
            Field id=field(operation,"a"), name=field(operation,"b"), icon=field(operation,"f");
            Field adapter=field(sheet,bindings.legacy ? "v" : "y");
            Method init=sheet.getDeclaredMethod("initViews",View.class);
            Method click=bindings.click;
            // Bind at the holder boundary, including every RecyclerView reuse, not just initial layout.
            Class<?> holder=bindings.holder;
            Field holderIcon=field(holder,"c"), holderLabel=field(holder,"a");
            Method bind=holder.getDeclaredMethod("a",operation);
            module.hook(bind).setId("new-soul/profile-bookmark-icon")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result=chain.proceed();
                        try {
                            android.widget.ImageView image=(android.widget.ImageView)holderIcon.get(chain.getThisObject());
                            boolean custom=id.getInt(chain.getArg(0))==ACTION;
                            if (custom && image.getDrawable() instanceof android.graphics.drawable.BitmapDrawable) {
                                TextView label=(TextView)holderLabel.get(chain.getThisObject());
                                image.setImageDrawable(new ProfileBookmarkIcon(
                                        (android.graphics.drawable.BitmapDrawable)image.getDrawable(),label.getCurrentTextColor()));
                            }
                            PluginActionMark.set(image,custom || PostCopyControls.isCopyAction(id.getInt(chain.getArg(0)))
                                    || PostBatchShareControls.isBatchAction(id.getInt(chain.getArg(0))));
                        } catch (Exception error) { Log.w("SoulHook102","bookmark icon binding failed",error); }
                        return result;
                    });
            module.deoptimize(bind);
            for (Method caller:bindings.adapter.getDeclaredMethods()) module.deoptimize(caller);
            // Register the consumer first: a custom action must never reach a native follow/share handler.
            module.hook(click).setId("new-soul/profile-bookmark-click")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        if (id.getInt(chain.getArg(0)) != ACTION) return chain.proceed();
                        try {
                            Object dialog=chain.getThisObject();
                            Binding binding=BINDINGS.get(dialog);
                            Activity activity=(Activity)call(dialog,"getActivity");
                            if (binding==null || activity==null) return true;
                            if (!binding.loaded) { toast(activity,binding.readFailed ? "读取插眼列表失败，请重新打开菜单" : "插眼列表尚未加载，请稍后重试"); return true; }
                            if (binding.busy) return true;
                            requireAccount(binding.account);
                            binding.busy=true;
                            IO.execute(() -> {
                                try {
                                    requireAccount(binding.account);
                                    ProfileBookmarkStore store=new ProfileBookmarkStore(activity.getFilesDir(),binding.account);
                                    boolean remove=binding.saved;
                                    if (remove) store.remove(binding.id); else store.put(binding.id,binding.name);
                                    MAIN.post(() -> {
                                        binding.busy=false;
                                        if (!alive(activity) || !sameAccount(binding.account)) return;
                                        toast(activity,remove?"已取消插眼":"已插眼");
                                        try { call(dialog,"dismissAllowingStateLoss"); } catch (Exception ignored) { }
                                    });
                                } catch (Exception error) { MAIN.post(() -> { binding.busy=false; failure(activity,error); }); }
                            });
                        } catch (Exception error) {
                            Log.w("SoulHook102","profile bookmark click failed",error);
                            try { failure((Activity)call(chain.getThisObject(),"getActivity"),error); }
                            catch (Exception ignored) { }
                        }
                        return true;
                    });
            module.hook(init).setId("new-soul/profile-bookmark-menu")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        if (!ready) return chain.proceed();
                        Object dialog=chain.getThisObject(); Binding binding=null;
                        try {
                            Activity activity=(Activity)call(dialog,"getActivity");
                            Object target=user.get(dialog);
                            boolean profileMenu=activity!=null && HOME.equals(activity.getClass().getName());
                            boolean standard=target!=null && post.get(dialog)==null && kind.getInt(dialog)==2;
                            if (profileMenu && !standard) target=boundProfileTarget(dialog,activity,base,sheet,user.getType(),loader);
                            if (profileMenu && target!=null) {
                                String targetId=(String)field(target.getClass(),"userIdEcpt").get(target);
                                String nickname=(String)field(target.getClass(),"alias").get(target);
                                if (nickname==null || nickname.trim().isEmpty())
                                    nickname=(String)field(target.getClass(),"signature").get(target);
                                if (targetId!=null && !targetId.trim().isEmpty() && nickname!=null && !nickname.trim().isEmpty()) {
                                    int resource=activity.getResources().getIdentifier("icon_share_collect","drawable",activity.getPackageName());
                                    if (resource!=0) {
                                        String owner=currentAccount();
                                        Object entry=bindings.create(0,"插眼…",false);
                                        id.setInt(entry,ACTION); icon.setInt(entry,resource);
                                        List<?> original=(List<?>)items.get(dialog);
                                        List<Object> copy=new ArrayList<>();
                                        if (original!=null) for (Object old:original) if (id.getInt(old)!=ACTION) copy.add(old);
                                        copy.add(0,entry); items.set(dialog,copy);
                                        binding=new Binding(owner,targetId,nickname,entry);
                                        BINDINGS.put(dialog,binding);
                                    }
                                }
                            }
                        } catch (Exception error) {
                            module.log(Log.WARN,"SoulHook102","profile bookmark menu unavailable",error);
                        }
                        Object result=chain.proceed();
                        if (binding!=null) {
                            final Binding current=binding;
                            final Activity activity=(Activity)call(dialog,"getActivity");
                            if (activity!=null) IO.execute(() -> {
                                try {
                                    requireAccount(current.account);
                                    boolean saved=new ProfileBookmarkStore(activity.getFilesDir(),current.account).contains(current.id);
                                    MAIN.post(() -> {
                                        if (!alive(activity) || !sameAccount(current.account) || BINDINGS.get(dialog)!=current) return;
                                        try {
                                            current.saved=saved; current.loaded=true;
                                            name.set(current.action,saved?"取消插眼":"插眼");
                                            Object renderer=adapter.get(dialog);
                                            if (renderer!=null) call(renderer,"notifyDataSetChanged");
                                        } catch (Exception error) { failure(activity,error); }
                                    });
                                } catch (Exception error) { MAIN.post(() -> {
                                    current.readFailed=true;
                                    if (!alive(activity) || BINDINGS.get(dialog)!=current) return;
                                    try {
                                        name.set(current.action,"插眼不可用");
                                        Object renderer=adapter.get(dialog);
                                        if (renderer!=null) call(renderer,"notifyDataSetChanged");
                                    } catch (Exception ignored) { }
                                    failure(activity,error);
                                }); }
                            });
                        }
                        return result;
                    });
            module.deoptimize(init); module.deoptimize(click);
            for (Method caller : bindings.clickCallers) module.deoptimize(caller);
            for (Method caller:loader.loadClass(bindings.legacy ? "cn.soulapp.android.square.k" : "cn.soulapp.android.square.a0").getDeclaredMethods()) module.deoptimize(caller);
            ready=true;
            DebugDiagnostics.registration("profile.bookmarks",null);
            Log.i("SoulHook102","profile bookmark menu and navigation installed");
        } catch (Throwable error) {
            DebugDiagnostics.registration("profile.bookmarks",error);
            Log.e("SoulHook102","profile bookmarks unavailable",error);
        }
    }

    static boolean isBookmarkAction(int id) { return id==ACTION; }

    /** Some user-settings menus carry a Post; their submit callback still owns the profile. */
    private static Object boundProfileTarget(Object dialog,Activity activity,Class<?> base,
            Class<?> sheet,Class<?> userType,ClassLoader loader) throws Exception {
        boolean legacy = HostBindings.legacyChat(loader);
        int title=activity.getResources().getIdentifier("dialog_user_settings","string",activity.getPackageName());
        if (title==0 || field(sheet,legacy ? "L" : "Q").getInt(dialog)!=title
                || (!legacy && !"HomePage_TAMain".equals(field(base,"n").get(dialog)))) return null;
        Object listener=field(base,legacy ? "o" : "r").get(dialog);
        if (listener==null) return null;
        Class<?> page=loader.loadClass("cn.soulapp.android.component.home.meta.user.MetaPageUserHomeFragment");
        // 4.88's callback captures BaseSeedsDialogFragment and leaves its page tag
        // as "other". The exact callback, activity and three matching IDs bind it.
        Object owner=ProfileBookmarkTargetRules.boundOwner(listener,dialog,legacy ? base : sheet,page);
        if (owner==null && legacy) {
            Class<?> originalPage=loader.loadClass("cn.soulapp.android.component.home.user.fragment.PageUserHomeFragment");
            owner=ProfileBookmarkTargetRules.boundOwner(listener,dialog,base,originalPage);
        }
        if (owner==null || call(owner,"getActivity")!=activity) return null;
        Object target=ProfileBookmarkTargetRules.uniqueValue(owner,userType);
        if (target==null) return null;
        Object currentPost=field(sheet,legacy ? "B" : "E").get(dialog);
        // A profile without posts still has this settings menu. Only an existing
        // post needs the additional author check; the bound page supplies its user.
        String author=currentPost==null?null:(String)field(currentPost.getClass(),"authorIdEcpt").get(currentPost);
        if (currentPost!=null && (author==null || author.trim().isEmpty())) return null;
        String targetId=(String)field(userType,"userIdEcpt").get(target);
        String pageId=activity.getIntent().getStringExtra("KEY_USER_ID_ECPT");
        return ProfileBookmarkTargetRules.sameProfile(pageId,targetId,author) ? target : null;
    }

    static void addSettings(SettingsUi.MenuPanel parent) {
        LinearLayout body=SettingsUi.section(parent,"主页插眼",
                "在用户主页右上角菜单点击「插眼」");
        TextView entry=action(parent,"插眼列表");
        entry.setOnClickListener(v -> showList(parent.activity,parent.dark));
        body.addView(entry,new LinearLayout.LayoutParams(-1,-2));
        if (!ready) body.addView(SettingsUi.label(parent.context,"当前版本的主页插眼入口尚未就绪",13,parent.secondary));
    }
    static void showList(Activity activity,boolean dark) {
        final String owner;
        try { owner=currentAccount(); } catch (Exception error) { failure(activity,error); return; }
        SettingsUi.MenuPanel panel=new SettingsUi.MenuPanel(activity,dark);
        panel.help="点击昵称打开主页，长按记录移除\n记录按登录账号分别保存在本机；打开主页仍遵循 Soul 原有的访问行为";
        panel.builder.setNeutralButton("重新加载",null);
        panel.show("插眼列表","关闭");
        TextView retry=panel.dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL);
        retry.setVisibility(View.GONE);
        Runnable[] refresh=new Runnable[1];
        refresh[0]=() -> {
            if (!visible(panel)) return;
            retry.setEnabled(false);
            retry.setVisibility(View.GONE);
            panel.body.removeAllViews();
            panel.body.addView(SettingsUi.label(panel.context,"正在加载…",14,panel.secondary));
            IO.execute(() -> {
                try {
                    requireAccount(owner);
                    List<ProfileBookmarkStore.Entry> entries=new ProfileBookmarkStore(activity.getFilesDir(),owner).list();
                    MAIN.post(() -> {
                        if (!visible(panel)) return;
                        panel.body.removeAllViews();
                        if (!sameAccount(owner)) { panel.body.addView(SettingsUi.label(panel.context,"账号已切换，请重新打开列表",14,panel.secondary)); return; }
                        if (entries.isEmpty()) panel.body.addView(SettingsUi.label(panel.context,"暂无插眼",14,panel.secondary));
                        for (ProfileBookmarkStore.Entry e:entries) {
                            TextView row=action(panel,e.name);
                            row.setContentDescription(e.name+"，打开主页；长按移除插眼");
                            row.setOnClickListener(v -> {
                                try {
                                    requireAccount(owner);
                                    if (!ready) throw new IllegalStateException("当前版本暂无法打开主页");
                                    openHome.invoke(null,e.id,"newsoul_bookmark");
                                    panel.dialog.dismiss();
                                } catch (Exception error) { failure(activity,error); }
                            });
                            row.setOnLongClickListener(v -> {
                                new SettingsUi.MenuPanel(activity,dark).showActionMenu(row,new String[]{"移除插眼"},true,which -> {
                                    row.setEnabled(false);
                                    IO.execute(() -> {
                                        try {
                                            requireAccount(owner);
                                            new ProfileBookmarkStore(activity.getFilesDir(),owner).remove(e.id);
                                            MAIN.post(() -> { if (visible(panel)) refresh[0].run(); });
                                        } catch (Exception error) { MAIN.post(() -> { row.setEnabled(true); failure(activity,error); }); }
                                    });
                                });
                                return true;
                            });
                            SettingsUi.addListItem(panel,panel.body,row);
                        }
                    });
                } catch (Exception error) { MAIN.post(() -> {
                    if (!visible(panel)) return;
                    panel.body.removeAllViews();
                    panel.body.addView(SettingsUi.label(panel.context,"读取失败，原有记录已保留",14,panel.secondary));
                    retry.setEnabled(true);
                    retry.setVisibility(View.VISIBLE);
                    Log.w("SoulHook102","bookmark list read failed",error);
                }); }
            });
        };
        retry.setOnClickListener(v -> refresh[0].run());
        refresh[0].run();
    }
    private static TextView action(SettingsUi.MenuPanel panel,String text) {
        TextView view=SettingsUi.label(panel.context,text,16,panel.foreground);
        view.setMinHeight(SettingsUi.dp(panel.context,48));
        view.setGravity(android.view.Gravity.CENTER_VERTICAL);
        view.setPadding(SettingsUi.dp(panel.context,12),SettingsUi.dp(panel.context,10),SettingsUi.dp(panel.context,12),SettingsUi.dp(panel.context,10));
        panel.appearance.apply(view,UiAppearance.Role.ICON);
        SettingsUi.buttonSemantics(view); return view;
    }
    private static boolean visible(SettingsUi.MenuPanel panel) { return alive(panel.activity) && panel.dialog!=null && panel.dialog.isShowing(); }
    private static boolean alive(Activity activity) { return activity!=null && !activity.isFinishing() && !activity.isDestroyed(); }
    private static String currentAccount() throws Exception {
        String value=account==null?null:(String)account.invoke(null);
        if (value==null || value.trim().isEmpty()) throw new IllegalStateException("请先登录 Soul"); return value;
    }
    private static boolean sameAccount(String owner) { try { return owner.equals(currentAccount()); } catch(Exception error) { return false; } }
    private static void requireAccount(String owner) throws Exception { if (!owner.equals(currentAccount())) throw new IllegalStateException("账号已切换，请重新打开页面"); }
    private static Field field(Class<?> type,String name) throws Exception {
        for (Class<?> current=type; current!=null; current=current.getSuperclass()) {
            try { Field f=current.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static Object call(Object target,String name) throws Exception { return target.getClass().getMethod(name).invoke(target); }
    private static void toast(Activity activity,String text) { if (alive(activity)) Toast.makeText(activity,text,Toast.LENGTH_SHORT).show(); }
    private static void failure(Activity activity,Exception error) { Log.w("SoulHook102","profile bookmark operation failed",error); toast(activity,error instanceof IllegalStateException || error instanceof IllegalArgumentException ? error.getMessage() : "操作失败，原有记录已保留"); }
}
