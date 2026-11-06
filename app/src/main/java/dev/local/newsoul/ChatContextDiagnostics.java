package dev.local.newsoul;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** User-triggered, local-only context inspection. Never sends a chat message. */
final class ChatContextDiagnostics {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Set<View> BOUND = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<Activity, Session> ACTIVE = new WeakHashMap<>();

    static void observe(Activity activity) {
        if (!activity.getClass().getName().equals("cn.soulapp.android.component.chat.ConversationActivity")) return;
        activity.getWindow().getDecorView().postDelayed(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            int id = activity.getResources().getIdentifier("tv_title", "id", activity.getPackageName());
            View title = activity.findViewById(id);
            if (!(title instanceof TextView) || !BOUND.add(title)) return;
            title.setOnLongClickListener(v -> { open(activity, (TextView) title); return true; });
        }, 400);
    }

    private static void open(Activity activity, TextView title) {
        if (ACTIVE.containsKey(activity)) {
            Toast.makeText(activity, "当前诊断仍在进行", Toast.LENGTH_SHORT).show(); return;
        }
        try {
            Object fragment = HostBindings.currentFragment(activity.getClassLoader()).invoke(activity);
            // JADX reconstructs Kotlin names; runtime dex fields are O and d0 in 6.35.0.
            Object user = typedField(fragment, "cn.soulapp.android.client.component.middle.platform.model.api.user.ImUserBean");
            Object conversation = typedField(fragment, "cn.soulapp.imlib.Conversation");
            if (user == null || conversation == null) throw new IllegalStateException("聊天资料尚未加载");
            String target = String.valueOf(user.getClass().getField("userIdEcpt").get(user));
            if (target.isEmpty() || target.equals("null")) throw new IllegalStateException("缺少当前对象标识");
            Session session = new Session(activity, target, Color.luminance(title.getCurrentTextColor()) > .45);
            session.start(user, conversation);
        } catch (Throwable error) {
            ACTIVE.remove(activity);
            Toast.makeText(activity, "诊断无法启动：" + errorName(error), Toast.LENGTH_LONG).show();
        }
    }

    private static Object typedField(Object owner, String type) throws Exception {
        Field match = null;
        for (Class<?> cls = owner.getClass(); cls != null; cls = cls.getSuperclass()) {
            for (Field field : cls.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && field.getType().getName().equals(type)) {
                    if (match != null) throw new IllegalStateException("对象字段不唯一：" + type);
                    match = field;
                }
            }
        }
        if (match == null) throw new NoSuchFieldException(type);
        match.setAccessible(true);
        return match.get(owner);
    }

    private static String errorName(Throwable error) {
        while (error instanceof InvocationTargetException && error.getCause() != null) error = error.getCause();
        return error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
    }
    private static Object call(Object object, String name) throws Exception {
        return object.getClass().getMethod(name).invoke(object);
    }
    private static long messageTime(Object message) {
        try {
            long server = ((Number) call(message, "getServerTime")).longValue();
            return LocalHistoryRules.timestamp(server, ((Number) call(message, "getLocalTime")).longValue());
        } catch (Exception ignored) { return 0; }
    }

    private static final class Session {
        final Activity activity;
        final ClassLoader loader;
        final String target;
        final JSONObject report = new JSONObject();
        final LinkedHashMap<String, TextView> views = new LinkedHashMap<>();
        final Set<String> pending = new HashSet<>();
        final List<Object> subscriptions = new ArrayList<>();
        final SettingsUi.MenuPanel panel;
        volatile boolean closed;
        Object gson;
        Method toJson;
        Session(Activity activity, String target, boolean dark) throws Exception {
            this.activity = activity; this.target = target; loader = activity.getClassLoader();
            Class<?> cls = loader.loadClass("com.google.gson.Gson");
            gson = cls.getConstructor().newInstance(); toJson = cls.getMethod("toJson", Object.class);
            report.put("source", "LSP runtime objects and Soul authenticated GET APIs; not screenshots");
            report.put("capturedAt", System.currentTimeMillis()); report.put("targetUserIdEcpt", target);
            panel = new SettingsUi.MenuPanel(activity, dark);
            for (String key : new String[]{"聊天基本资料", "会话消息", "主页资料", "主页标签", "主页帖子"}) {
                TextView view = new TextView(panel.context);
                view.setTextColor(panel.foreground); view.setTextSize(13); view.setTextIsSelectable(true);
                view.setPadding(0, 12, 0, 20); view.setText(key + "：等待读取");
                panel.body.addView(view); views.put(key, view);
            }
            panel.help = "长按昵称读取本地可见消息；不含云端漫游，帖子仅读首页\n结果保存为本机私有文件 newsoul-context-diagnostic.json，每次覆盖；不调用外部 AI";
        }
        Object json(Object value) throws Exception {
            return new JSONTokener((String) toJson.invoke(gson, value)).nextValue();
        }
        void set(String key, String state, String source, Object data) {
            if (closed || activity.isDestroyed()) return;
            try {
                JSONObject section = new JSONObject().put("status", state).put("source", source)
                        .put("data", data == null ? JSONObject.NULL : data);
                report.put(key, section);
                String text = key + " · " + state + "\n" + source + "\n" + section.opt("data").toString();
                views.get(key).setText(text.length() > 18000 ? text.substring(0, 18000) + "\n[界面截断，完整内容保存在本机诊断文件]" : text);
                try (FileOutputStream out = new FileOutputStream(new File(activity.getFilesDir(), "newsoul-context-diagnostic.json"))) {
                    out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception error) {
                views.get(key).setText(key + "：结果整理失败 " + errorName(error));
            }
        }
        void start(Object user, Object conversation) throws Exception {
            ACTIVE.put(activity, this);
            panel.show("当前会话数据诊断", "关闭");
            panel.dialog.setOnDismissListener(d -> close());
            try { set("聊天基本资料", "已读取", "ConversationFragment.toUser", json(user)); }
            catch (Throwable error) { set("聊天基本资料", "读取失败", "toUser", errorName(error)); }
            readLocalHistory(conversation);
            query("主页资料", "USER", "v2/user/info", () -> loader.loadClass("cn.soulapp.android.component.home.api.user.user.UserService")
                    .getMethod("O",String.class,boolean.class).invoke(null,target,false));
            query("主页标签", "NEW_APIA", "homepage/tabs/v2", () -> {
                Class<?> cls=loader.loadClass("cn.soulapp.android.square.api.user.net.UserHomeContentApiService");
                return cls.getMethod("k",String.class).invoke(cls.getField("a").get(null),target);
            });
            query("主页帖子", "NEW_APIA", "v5/post/homepage（首页，非全部帖子）", () -> loader.loadClass("cn.soulapp.android.component.home.api.user.user.UserService")
                    .getMethod("T",String.class,String.class,boolean.class,boolean.class,String.class)
                    .invoke(null,target,null,false,true,null));
            MAIN.postDelayed(() -> {
                if (closed) return;
                for(String key : new ArrayList<>(pending)) { pending.remove(key); set(key,"请求超时","等待25秒未收到结果",null); }
            },25000);
        }
        void readLocalHistory(Object conversation) {
            set("会话消息", "读取本地历史中", "ChatDbManager.Z：仅本地查询，无漫游调用", null);
            Thread worker = new Thread(() -> {
                try {
                    int chatType = ((Number) call(conversation, "getChatType")).intValue();
                    if (chatType != 0) throw new IllegalStateException("本地完整性核验目前仅支持普通私聊");
                    String sessionId = String.valueOf(call(conversation,"getSessionId"));
                    if (sessionId.isEmpty() || sessionId.equals("null")) throw new IllegalStateException("缺少会话标识");
                    Method accountGetter = loader.loadClass("cn.soulapp.imlib.j0").getMethod("g");
                    String account = String.valueOf(accountGetter.invoke(null));
                    LocalChatHistory.Result history = LocalChatHistory.read(loader, sessionId);
                    DebugDiagnostics.event("chat.history.read", history.facts());
                    List<?> local = history.messages;
                    if (closed) return;
                    List<Object> sorted = new ArrayList<>(local);
                    sorted.sort(Comparator.comparingLong(ChatContextDiagnostics::messageTime));
                    JSONArray messages = new JSONArray();
                    int profileCards = 0;
                    long earliest = Long.MAX_VALUE, latest = Long.MIN_VALUE;
                    for (Object message : sorted) {
                        if (closed) return;
                        String id = String.valueOf(call(message,"getMsgId"));
                        Object chat = call(message,"getChatMessage");
                        JSONObject row = new JSONObject().put("id",id).put("from",call(message,"getFrom"))
                                .put("to",call(message,"getTo")).put("sender",account.equals(String.valueOf(call(message,"getFrom"))) ? "我" : "对方")
                                .put("localTime",call(message,"getLocalTime")).put("serverTime",call(message,"getServerTime"))
                                .put("outerType",call(message,"getMsgType")).put("messageStatus",call(message,"getMsgStatus"));
                        int type = chat == null ? -1 : ((Number) call(chat,"getMsgType")).intValue();
                        if (chat != null) row.put("type",type).put("body",json(call(chat,"getMsgContent")));
                        if (type == 27) {
                            profileCards++;
                            row.put("sender","系统资料卡").put("recordKind","profileCard");
                        } else {
                            row.put("recordKind","message");
                            long time = messageTime(message);
                            if (time > 1) { earliest = Math.min(earliest,time); latest = Math.max(latest,time); }
                        }
                        messages.put(row);
                    }
                    history.verify(messages.length());
                    if (!account.equals(String.valueOf(accountGetter.invoke(null)))) throw new IllegalStateException("账号发生切换，结果已丢弃");
                    DebugDiagnostics.event("chat.history.verified", history.facts());
                    JSONObject data = new JSONObject().put("scope","当前账号当前会话的本地数据库可见历史；排除宿主隐藏反应和已删除记录")
                            .put("cloudRoamingRequested",false).put("localCountBefore",history.rawBefore).put("localCountAfter",history.rawAfter)
                            .put("queriedCount",history.queriedCount).put("exportedCount",messages.length())
                            .put("filteredCount",history.rawAfter-history.queriedCount).put("duplicateIds",history.duplicateRows)
                            .put("duplicateRowsCollapsed",history.duplicateRows).put("invalidIds",0)
                            .put("completeLocalHistory",true).put("order","serverTime; fallback localTime; stable database order for ties")
                            .put("profileCardCount",profileCards).put("nonProfileCardCount",messages.length()-profileCards)
                            .put("earliestTime",earliest==Long.MAX_VALUE?JSONObject.NULL:earliest)
                            .put("latestTime",latest==Long.MIN_VALUE?JSONObject.NULL:latest).put("messages",messages);
                    MAIN.post(() -> set("会话消息","本地完整性核验通过",
                            "本地时间线两次全量查询一致；数据库总条数稳定；未请求漫游",data));
                } catch (Throwable error) {
                    DebugDiagnostics.failure("chat.history.failed", LocalChatHistory.failureFacts(error), error);
                    MAIN.post(() -> set("会话消息","本地读取失败","不回退到云端漫游",errorName(error)));
                }
            }, "NewSoul-local-history");
            worker.setDaemon(true); worker.start();
        }
        interface Request { Object get() throws Exception; }
        void query(String key, String root, String source, Request request) {
            pending.add(key); set(key,"请求中",source,null);
            try {
                Object observable=request.get();
                Class<?> callback=loader.loadClass("com.walid.rxretrofit.interfaces.IHttpCallback");
                Object proxy=Proxy.newProxyInstance(loader,new Class<?>[]{callback},(p,m,args) -> {
                    if(m.getDeclaringClass()==Object.class) {
                        if(m.getName().equals("hashCode")) return System.identityHashCode(p);
                        if(m.getName().equals("equals")) return p==args[0];
                        return "NewSoulContextCallback";
                    }
                    String name=m.getName();
                    if(name.equals("onNext") || name.equals("onError") || name.equals("reportExceptionLog")) {
                        MAIN.post(() -> {
                            if(closed || !pending.remove(key)) return;
                            try {
                                if(name.equals("onNext")) set(key,args[0]==null?"成功但数据为空":"请求成功",source,json(args[0]));
                                else if(name.equals("onError")) set(key,"接口失败",source,new JSONObject().put("code",args[0]).put("message",args[1]));
                                else set(key,"请求异常",source,errorName((Throwable)args[0]));
                            } catch(Throwable e) { set(key,"解析失败",source,errorName(e)); }
                        });
                    }
                    return null;
                });
                Object api=loader.loadClass("cn.soulapp.android.lib.common.api.ApiConstants").getField(root).get(null);
                Method subscribe=HostSymbols.subscribe(api, observable, callback);
                Object sub=subscribe.invoke(api,observable,proxy); if(sub!=null) subscriptions.add(sub);
            } catch(Throwable error) { pending.remove(key); set(key,"调用失败",source,errorName(error)); }
        }
        void close() {
            closed=true; ACTIVE.remove(activity);
            for(Object sub:subscriptions) try { sub.getClass().getMethod("dispose").invoke(sub); } catch(Exception ignored) {}
            subscriptions.clear();
        }
    }
}
