package dev.local.newsoul;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.JSONObject;

/** Runs the production reflection transport, cancellation owner, queue consumers and automation. */
public final class AccountHttpLifecycleTest {
    private static final String URL = "https://api-a.soulapp.cn/test";
    private static final Context CONTEXT = new Context();
    private static final Map<String, Map<String, Object>> STORES = new ConcurrentHashMap<>();
    private static final ExecutorService CLIENT = Executors.newCachedThreadPool();
    private static final Queue<Plan> PLANS = new ConcurrentLinkedQueue<>();
    private static final List<FakeCall> CALLS = new CopyOnWriteArrayList<>();
    private static volatile String user = "A";
    private static volatile boolean kickFromTransport;
    private static final AtomicBoolean fatalState = new AtomicBoolean();
    private static final AtomicBoolean failCommit = new AtomicBoolean();
    private static final AtomicInteger fatalErrors = new AtomicInteger();
    private static final CountDownLatch fatalObserved = new CountDownLatch(1);
    private static int checks;

    public static Context context() { return CONTEXT; }
    public static SharedPreferences preferences(String name) {
        if (name.equals("newsoul-account-automation-state") && fatalState.compareAndSet(true, false))
            throw new AssertionError("injected fatal state read");
        Map<String, Object> values = STORES.computeIfAbsent(name, ignored -> new ConcurrentHashMap<>());
        return (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getBoolean": case "getString": case "getInt": return values.getOrDefault(args[0], args[1]);
                case "edit":
                    Map<String, Object> edits = new HashMap<>();
                    return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(), new Class<?>[]{SharedPreferences.Editor.class}, (editor, operation, arguments) -> {
                        if (operation.getName().startsWith("put")) { edits.put((String) arguments[0], arguments[1]); return editor; }
                        if (operation.getName().equals("apply")) { values.putAll(edits); return null; }
                        if (operation.getName().equals("commit")) {
                            if (failCommit.compareAndSet(true, false)) return false;
                            values.putAll(edits); return true;
                        }
                        throw new AssertionError(operation);
                    });
                default: throw new AssertionError(method);
            }
        });
    }
    static void flag(String name, boolean value) { preferences("settings").edit().putBoolean(name, value).apply(); }
    static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
    static void await(CountDownLatch latch, String reason) throws Exception { check(latch.await(2, TimeUnit.SECONDS), reason); }

    public static final class Mine { public String userIdEcpt = user; }
    public static final class Account { public static Mine v() { return new Mine(); } }
    public static final class Url { final String value; Url(String value) { this.value = value; } public String toString() { return value; } }
    public static final class Request { final String value; Request(String value) { this.value = value; } public Url k() { return new Url(value); } }
    public static final class MediaType {}
    public interface WelfareCallback { void success(JSONObject json); void failed(int code, String message); }
    public static final class WelfareModel {
        final String url, method; final Map<String, Object> data;
        public WelfareModel(String url, String method, long timeout, MediaType type, Map<String, Object> data) {
            this.url = url; this.method = method; this.data = data;
        }
    }
    public static final class Winter {
        static final Winter INSTANCE = new Winter();
        static volatile WelfareCallback callback;
        static volatile WelfareModel model;
        static volatile java.util.function.Function<WelfareModel, JSONObject> replies = request ->
                new JSONObject("{\"code\":10001,\"data\":{\"already\":1}}");
        public static Winter d() { return INSTANCE; }
        public Call k(WelfareModel request, WelfareCallback cb) {
            model = request; callback = cb;
            Plan p = PLANS.poll(); if (p == null) p = new Plan(false, false);
            FakeCall call = new FakeCall(new Request(request.url), p); CALLS.add(call);
            p.started.countDown();
            if (!p.blockExecute) cb.success(replies.apply(request));
            return call;
        }
    }
    public static final class RequestBody { public static RequestBody create(MediaType type, String body) { return new RequestBody(); } }
    public static final class Builder {
        String url;
        public Builder() {}
        public Builder q(String value) { url = value; return this; }
        public Builder f() { return this; }
        public Builder h(String key, String value) { return this; }
        public Builder j(String method, RequestBody body) { return this; }
        public Request b() { return new Request(url); }
    }
    public interface Call { Request request(); Response execute() throws Exception; void cancel(); }
    public interface Factory { Call newCall(Request request); }
    public static final class FakeFactory implements Factory {
        public Call newCall(Request request) {
            Plan plan = PLANS.poll();
            FakeCall call = new FakeCall(request, plan == null ? new Plan(false, false) : plan);
            CALLS.add(call);
            return call;
        }
    }
    public static final class Service { final Factory callFactory = new FakeFactory(); }
    public static final class RawCall { final Service serviceMethod = new Service(); private Call createRawCall() { return serviceMethod.callFactory.newCall(new Request(URL)); } }
    static final class Plan {
        final CountDownLatch started = new CountDownLatch(1), reading = new CountDownLatch(1), released = new CountDownLatch(1), cancelled = new CountDownLatch(1), returnAllowed = new CountDownLatch(1);
        final boolean blockExecute, blockBody;
        final AtomicInteger cancellations = new AtomicInteger();
        volatile boolean closed, returnAfterCancel, delayAfterCancel;
        Plan(boolean execute, boolean body) { blockExecute = execute; blockBody = body; }
    }
    public static final class FakeCall implements Call {
        final Request request; final Plan plan; final String account = user;
        FakeCall(Request request, Plan plan) { this.request = request; this.plan = plan; }
        public Request request() { return request; }
        public Response execute() throws Exception {
            plan.started.countDown();
            if (kickFromTransport) AccountAutomationControls.kick();
            if (plan.blockExecute) plan.released.await();
            if (plan.delayAfterCancel && plan.cancelled.getCount() == 0) plan.returnAllowed.await();
            if (plan.cancelled.getCount() == 0 && !plan.returnAfterCancel) throw new IOException("cancelled");
            return new Response(plan);
        }
        public void cancel() { plan.cancellations.incrementAndGet(); plan.cancelled.countDown(); plan.released.countDown(); }
    }
    public static final class ResponseBody {
        final Plan plan; ResponseBody(Plan plan) { this.plan = plan; }
        public String string() throws Exception {
            plan.reading.countDown();
            if (plan.blockBody) plan.released.await();
            if (plan.cancelled.getCount() == 0 && !plan.returnAfterCancel) throw new IOException("cancelled body");
            return "{\"code\":10001,\"success\":true,\"data\":{\"todayIsSign\":true,\"already\":1,\"signState\":1000,\"registerDay\":20,\"gender\":\"FEMALE\",\"cityName\":\"江西\"}}";
        }
    }
    public static final class Response {
        final Plan plan; Response(Plan plan) { this.plan = plan; }
        public int h() { return 200; }
        public ResponseBody e() { return new ResponseBody(plan); }
        public void close() { plan.closed = true; }
    }
    static void installFakeState(boolean soul637) throws Exception {
        Map<String, Class<?>> types = Map.ofEntries(
            Map.entry("okhttp3.t$a", Builder.class), Map.entry("okhttp3.t", Request.class),
            Map.entry("okhttp3.Call", Call.class), Map.entry("okhttp3.Call$Factory", Factory.class),
            Map.entry("okhttp3.v", Response.class), Map.entry("okhttp3.w", ResponseBody.class),
            Map.entry("okhttp3.n", Url.class), Map.entry("okhttp3.u", RequestBody.class), Map.entry("okhttp3.p", MediaType.class),
            Map.entry("retrofit2.ServiceMethod", Service.class), Map.entry("retrofit2.OkHttpCall", RawCall.class),
            Map.entry("cn.soulapp.android.client.component.middle.platform.model.api.user.Mine", Mine.class),
            Map.entry("cn.soulapp.android.client.component.middle.platform.utils.user.a", Account.class),
            Map.entry(soul637 ? "sy.w" : "ty.w", Winter.class),
            Map.entry(soul637 ? "wy.a" : "xy.a", WelfareModel.class), Map.entry("okhttp3.m", MediaType.class),
            Map.entry("cn.soulapp.android.net.winter.api.INetCallBack", WelfareCallback.class));
        ClassLoader loader = new ClassLoader(AccountHttpLifecycleTest.class.getClassLoader()) {
            public Class<?> loadClass(String name) throws ClassNotFoundException { return types.containsKey(name) ? types.get(name) : super.loadClass(name); }
        };
        Class<?> state = Class.forName("dev.local.newsoul.HostAccountHttp$State");
        Constructor<?> constructor = state.getDeclaredConstructor(ClassLoader.class); constructor.setAccessible(true);
        Object active = constructor.newInstance(loader);
        Field factory = state.getDeclaredField("factory"); factory.setAccessible(true); factory.set(active, new FakeFactory());
        Field field = HostAccountHttp.class.getDeclaredField("active"); field.setAccessible(true); field.set(null, active);
        HostAccountHttp.ready = true;
        HostWelfareHttp.install(null, loader);
        check(HostWelfareHttp.ready, "welfare binding resolves exact constructor, callbacks and Call");
        // A present 6.38 owner must never borrow a 6.37 model from another ABI pair.
        Map<String, Class<?>> mixed = new java.util.HashMap<>(types);
        mixed.put("ty.w", Winter.class); mixed.put("sy.w", Winter.class);
        mixed.remove("xy.a"); mixed.put("wy.a", WelfareModel.class);
        ClassLoader mixedLoader = new ClassLoader(AccountHttpLifecycleTest.class.getClassLoader()) {
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                return mixed.containsKey(name) ? mixed.get(name) : super.loadClass(name);
            }
        };
        try { new HostBindings.Welfare(mixedLoader); throw new AssertionError("mixed welfare ABI accepted"); }
        catch (ClassNotFoundException expected) { checks++; }
    }
    static Future<JSONObject> request(HostRequestScope scope) {
        String account = user;
        return CLIENT.submit(() -> HostAccountHttp.request(account, URL, false, scope));
    }
    static void fails(Future<?> future, String reason) throws Exception {
        try { future.get(2, TimeUnit.SECONDS); throw new AssertionError(reason); }
        catch (ExecutionException expected) { checks++; }
    }
    static ExecutorService worker(Class<?> owner) throws Exception {
        Field field = owner.getDeclaredField("IO"); field.setAccessible(true); return (ExecutorService) field.get(null);
    }
    static void drain(Class<?> owner) throws Exception { worker(owner).submit(() -> {}).get(2, TimeUnit.SECONDS); }
    public static final class Post { public String authorIdEcpt; Post(String id) { authorIdEcpt = id; } }
    static void expireQueuedBeforeActive() {
        Runnable active = Handler.delayed.poll(), queued = Handler.delayed.poll();
        check(active != null && queued != null && Handler.delayed.isEmpty(), "two outstanding request deadlines");
        queued.run();
        active.run();
    }

    static void transportChecks() throws Exception {
        Plan timeout = new Plan(true, false); PLANS.add(timeout);
        Future<?> future = request(new HostRequestScope(150)); await(timeout.started, "deadline request executed");
        fails(future, "deadline must end blocked execute");
        check(timeout.cancellations.get() == 1, "deadline calls host cancel exactly once");

        Plan body = new Plan(false, true); PLANS.add(body);
        future = request(new HostRequestScope(150)); await(body.reading, "body read reached");
        fails(future, "deadline covers response body");
        check(body.closed && body.cancellations.get() == 1, "cancelled response body is closed");

        Plan switched = new Plan(true, false); PLANS.add(switched);
        future = request(new HostRequestScope(5000)); await(switched.started, "account request started"); user = "B";
        fails(future, "account change cancels without waiting for deadline");
        check(switched.cancellations.get() == 1, "account guard cancels underlying call");

        Plan unavailable = new Plan(true, false); PLANS.add(unavailable);
        future = request(new HostRequestScope(5000)); await(unavailable.started, "state request started"); HostAccountHttp.ready = false;
        fails(future, "module state invalidation cancels request"); HostAccountHttp.ready = true;
        check(unavailable.cancellations.get() == 1, "module invalidation calls cancel");

        AtomicBoolean enabled = new AtomicBoolean(true);
        Plan disabled = new Plan(true, false); PLANS.add(disabled);
        future = request(new HostRequestScope(5000, enabled::get)); await(disabled.started, "enabled request started"); enabled.set(false);
        fails(future, "disabled feature cancels without new UI event");
        check(disabled.cancellations.get() == 1, "feature guard calls cancel");

        int before = CALLS.size(); HostRequestScope cancelled = new HostRequestScope(5000); cancelled.cancel();
        fails(request(cancelled), "pre-cancelled request rejected"); check(CALLS.size() == before, "pre-cancel sends no host request");

        Plan racing = new Plan(true, false); racing.returnAfterCancel = true; PLANS.add(racing);
        HostRequestScope scope = new HostRequestScope(5000); future = request(scope); await(racing.started, "racing request started"); scope.cancel();
        fails(future, "stale response after cancellation rejected"); check(racing.closed, "stale response closed");

        Plan success = new Plan(false, false); PLANS.add(success);
        check(request(new HostRequestScope(150)).get(2, TimeUnit.SECONDS).optInt("code") == 10001, "normal request completes");
        check(!success.cancelled.await(200, TimeUnit.MILLISECONDS) && success.closed, "completed request cancels watchdog and closes response");

        HostRequestScope expiredDisabled = new HostRequestScope(1, () -> false);
        Thread.sleep(3);
        expiredDisabled.refreshCancellation();
        check(expiredDisabled.isCancelled() && !expiredDisabled.timedOut(), "invalid queued state takes precedence even after its real deadline");
        expiredDisabled.close();
        HostRequestScope expiredValid = new HostRequestScope(1);
        Thread.sleep(3);
        expiredValid.refreshCancellation();
        check(expiredValid.timedOut(), "a valid queued request retains real timeout semantics");
        expiredValid.close();
    }

    static void lookupChecks() throws Exception {
        user = "lookup";
        flag(RegistrationDaysFilter.ENABLED, true);
        flag(RegistrationDaysFilter.DISPLAY, true);
        preferences("settings").edit().putInt(RegistrationDaysFilter.MIN, 30).apply();
        Plan blocked = new Plan(true, false); PLANS.add(blocked);
        check(!RegistrationDaysFilter.allows(CONTEXT, new Post("timed")), "pending registration row waits");
        await(blocked.started, "registration call started");
        check(!RegistrationDaysFilter.allows(CONTEXT, new Post("queued")), "second registration queues");
        int before = CALLS.size(); expireQueuedBeforeActive(); drain(RegistrationDaysFilter.class);
        check(blocked.cancellations.get() == 1, "UI timeout cancels host registration call");
        check(CALLS.size() == before, "queued timeout never dispatches");
        check(((ThreadPoolExecutor) worker(RegistrationDaysFilter.class)).getQueue().isEmpty(), "timed out queue is released");
        check(RegistrationDaysFilter.allows(CONTEXT, new Post("timed")), "timeout keeps row visible");
        check(RegistrationDaysFilter.resolve(CONTEXT, new Post("timed")) == null, "cancelled registration invents no display");
        RegistrationDaysFilter.resolve(CONTEXT, new Post("queued")); drain(RegistrationDaysFilter.class);
        check(CALLS.size() == before, "ordinary registration queue timeout keeps negative cache without retrying");
        RegistrationDaysFilter.resolve(CONTEXT, new Post("next")); drain(RegistrationDaysFilter.class);
        check(Integer.valueOf(20).equals(RegistrationDaysFilter.resolve(CONTEXT, new Post("next"))), "new lookup advances after blocked call cancellation");

        flag(SquareIpLocation.ENABLED, true);
        Plan ip = new Plan(true, false); PLANS.add(ip);
        SquareIpLocationLookup.resolve(CONTEXT, new Post("ip-timed")); await(ip.started, "IP call started");
        SquareIpLocationLookup.resolve(CONTEXT, new Post("ip-queued")); before = CALLS.size();
        expireQueuedBeforeActive(); drain(SquareIpLocationLookup.class);
        check(ip.cancellations.get() == 1 && CALLS.size() == before, "IP timeout cancels active call and skips expired queue");
        SquareIpLocationLookup.resolve(CONTEXT, new Post("ip-queued")); drain(SquareIpLocationLookup.class);
        check(CALLS.size() == before, "ordinary IP queue timeout keeps negative cache without retrying");
        SquareIpLocationLookup.resolve(CONTEXT, new Post("ip-next")); drain(SquareIpLocationLookup.class);
        check("江西".equals(SquareIpLocationLookup.resolve(CONTEXT, new Post("ip-next"))), "IP worker resumes for next author");

        Plan account = new Plan(true, false); PLANS.add(account);
        SquareIpLocationLookup.resolve(CONTEXT, new Post("old-account")); await(account.started, "old account IP request starts");
        user = "lookup-new";
        SquareIpLocationLookup.resolve(CONTEXT, new Post("new-account")); drain(SquareIpLocationLookup.class);
        check(account.cancellations.get() == 1, "account switch cancels old lookup");
        check("江西".equals(SquareIpLocationLookup.resolve(CONTEXT, new Post("new-account"))), "new account lookup advances without external timeout");
        for (boolean registration : new boolean[]{false, true}) {
            cancelAndReopen(registration, false);
            cancelAndReopen(registration, true);
            queuedCancelAndReopen(registration, false);
            queuedCancelAndReopen(registration, true);
        }
        flag(SquareIpLocation.ENABLED, false); flag(RegistrationDaysFilter.ENABLED, false); flag(RegistrationDaysFilter.DISPLAY, false);
    }

    static void lookupEnabled(boolean registration, boolean enabled) {
        if (registration) {
            flag(RegistrationDaysFilter.ENABLED, enabled);
            flag(RegistrationDaysFilter.DISPLAY, enabled);
        } else flag(SquareIpLocation.ENABLED, enabled);
    }
    static void cancelAndReopen(boolean registration, boolean reopenBeforeReturn) throws Exception {
        Class<?> owner = registration ? RegistrationDaysFilter.class : SquareIpLocationLookup.class;
        Post post = new Post("reopen-" + registration + "-" + reopenBeforeReturn);
        lookupEnabled(registration, true);
        Plan blocked = new Plan(true, false); blocked.delayAfterCancel = true; PLANS.add(blocked);
        if (registration) RegistrationDaysFilter.resolve(CONTEXT, post);
        else SquareIpLocationLookup.resolve(CONTEXT, post);
        await(blocked.started, "reopen request started");
        lookupEnabled(registration, false);
        await(blocked.cancelled, "disabling lookup cancels its actual host call");
        if (reopenBeforeReturn) {
            lookupEnabled(registration, true);
            Handler.expire(); // A stale UI deadline also must not turn state cancellation into a failed lookup.
        }
        blocked.returnAllowed.countDown();
        drain(owner);
        if (!reopenBeforeReturn) lookupEnabled(registration, true);
        int before = CALLS.size();
        if (registration) RegistrationDaysFilter.resolve(CONTEXT, post);
        else SquareIpLocationLookup.resolve(CONTEXT, post);
        drain(owner);
        check(CALLS.size() == before + 1, "cancelled lookup does not negative-cache its author after reopening");
        check(registration ? Integer.valueOf(20).equals(RegistrationDaysFilter.resolve(CONTEXT, post))
                : "江西".equals(SquareIpLocationLookup.resolve(CONTEXT, post)), "reopened lookup receives real metadata");
    }

    static void queuedCancelAndReopen(boolean registration, boolean moduleUnavailable) throws Exception {
        Class<?> owner = registration ? RegistrationDaysFilter.class : SquareIpLocationLookup.class;
        String key = "queued-reopen-" + registration + "-" + moduleUnavailable;
        Post active = new Post(key + "-active"), queued = new Post(key + "-queued");
        lookupEnabled(registration, true);
        Plan blocked = new Plan(true, false); blocked.delayAfterCancel = true; PLANS.add(blocked);
        if (registration) RegistrationDaysFilter.resolve(CONTEXT, active);
        else SquareIpLocationLookup.resolve(CONTEXT, active);
        await(blocked.started, "active lookup occupies worker before queued state cancellation");
        if (registration) RegistrationDaysFilter.resolve(CONTEXT, queued);
        else SquareIpLocationLookup.resolve(CONTEXT, queued);
        int before = CALLS.size();
        if (moduleUnavailable) HostAccountHttp.ready = false;
        else lookupEnabled(registration, false);
        await(blocked.cancelled, "invalid state cancels active lookup while queued scope remains unattached");
        Handler.expire();
        blocked.returnAllowed.countDown();
        drain(owner);
        check(CALLS.size() == before, "invalid queued lookup is removed without sending");
        HostAccountHttp.ready = true;
        lookupEnabled(registration, true);
        if (registration) RegistrationDaysFilter.resolve(CONTEXT, queued);
        else SquareIpLocationLookup.resolve(CONTEXT, queued);
        drain(owner);
        check(CALLS.size() == before + 1, "unattached state cancellation leaves no negative cache after reopening");
        check(registration ? Integer.valueOf(20).equals(RegistrationDaysFilter.resolve(CONTEXT, queued))
                : "江西".equals(SquareIpLocationLookup.resolve(CONTEXT, queued)), "queued author receives real metadata after reopening");
    }

    static void automationChecks() throws Exception {
        user = "auto-A"; CALLS.clear();
        flag(AccountAutomationControls.SIGN, true); flag(AccountAutomationControls.HIDE, true);
        kickFromTransport = true;
        Plan blocked = new Plan(true, false); PLANS.add(blocked);
        AccountAutomationControls.kick(); await(blocked.started, "A automation started");
        for (int i = 0; i < 30; i++) AccountAutomationControls.kick();
        user = "auto-B";
        for (int i = 0; i < 30; i++) AccountAutomationControls.kick();
        drain(AccountAutomationControls.class);
        check(blocked.cancellations.get() == 1, "switching account cancels automation call A");
        check(CALLS.stream().filter(call -> call.account.equals("auto-A")).count() == 1, "cancelled A never retries automatically");
        check(CALLS.stream().filter(call -> call.account.equals("auto-B")).count() == 3, "pending B completes two signs and hide");
        check(AccountAutomationControls.signStatus.equals("今日签到已完成") && AccountAutomationControls.hideStatus.equals("已设为隐身"), "latest account has its own successful status");
        int before = CALLS.size();
        for (int i = 0; i < 100; i++) AccountAutomationControls.kick();
        drain(AccountAutomationControls.class);
        check(CALLS.size() == before, "self-generated and repeated kicks retain side-effect throttling");

        user = "auto-disabled"; Plan disabled = new Plan(true, false); PLANS.add(disabled);
        AccountAutomationControls.kick(); await(disabled.started, "automation before disable starts");
        flag(AccountAutomationControls.SIGN, false); flag(AccountAutomationControls.HIDE, false); AccountAutomationControls.changed();
        drain(AccountAutomationControls.class);
        check(disabled.cancellations.get() == 1, "switches off cancel in-flight automation");
        check(CALLS.stream().filter(call -> call.account.equals("auto-disabled")).count() == 1, "disabled automation performs no follow-up action");

        flag(AccountAutomationControls.SIGN, true); flag(AccountAutomationControls.HIDE, true); AccountAutomationControls.changed();
        drain(AccountAutomationControls.class);
        check(CALLS.stream().filter(call -> call.account.equals("auto-disabled")).count() == 4, "explicit re-enable resets attempts once and completes current account");
        kickFromTransport = false;

        user = "auto-fatal"; fatalState.set(true);
        AccountAutomationControls.kick(); drain(AccountAutomationControls.class);
        Field busy = AccountAutomationControls.class.getDeclaredField("busy"); busy.setAccessible(true);
        check(!busy.getBoolean(null), "fatal failure releases busy scheduling state");
        AccountAutomationControls.kick(); drain(AccountAutomationControls.class);
        check(CALLS.stream().filter(call -> call.account.equals("auto-fatal")).count() == 3, "subsequent kick recovers after fatal worker failure");
        await(fatalObserved, "fatal failure reached the uncaught handler");
        check(fatalErrors.get() == 1, "fatal failure was not silently swallowed");

        worker(AccountAutomationControls.class).shutdownNow();
        AccountAutomationControls.kick();
        check(!busy.getBoolean(null), "rejected scheduling cannot leave busy stuck");
    }

    static void welfareChecks() throws Exception {
        user = "welfare-A";
        String url = "https://api-pay.soulapp.cn/ip/queryUserAsset";
        check(HostWelfareHttp.request(user, url, true, Map.of("activityId", "313"), () -> true).optInt("code") == 10001,
                "synchronous callback before returned Call is retained");
        check(Winter.model.method.equals("POST") && Winter.model.data.equals(Map.of("activityId", "313")), "host receives exact request parameters");
        int before = CALLS.size();
        try { HostWelfareHttp.request(user, "https://api-pay.soulapp.cn/ip/buy", true, Map.of(), () -> true); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { checks++; }
        check(CALLS.size() == before, "unlisted request cannot reach host");
        try { HostWelfareHttp.request(user, url, true, Map.of(), () -> false); throw new AssertionError(); }
        catch (IOException expected) { checks++; }
        check(CALLS.size() == before, "disabled feature sends nothing");
        AtomicBoolean enabled = new AtomicBoolean(true);
        Plan disabled = new Plan(true, false); PLANS.add(disabled);
        Future<?> pending = CLIENT.submit(() -> HostWelfareHttp.request("welfare-A", url, true, Map.of(), enabled::get));
        await(disabled.started, "welfare request starts before disable"); enabled.set(false);
        fails(pending, "disable ends callback wait"); check(disabled.cancellations.get() == 1, "disable cancels real welfare Call exactly once");
        Plan switched = new Plan(true, false); PLANS.add(switched);
        pending = CLIENT.submit(() -> HostWelfareHttp.request("welfare-A", url, true, Map.of(), () -> true));
        await(switched.started, "welfare request starts before account switch");
        WelfareCallback late = Winter.callback; user = "welfare-B";
        fails(pending, "account switch ends callback wait");
        late.success(new JSONObject("{\"code\":10001,\"data\":{}}"));
        check(switched.cancellations.get() == 1, "late welfare callback cannot revive cancelled request");
    }

    static void welfareAutomationChecks() throws Exception {
        Set<String> signed = ConcurrentHashMap.newKeySet();
        Winter.replies = request -> {
            if (request.url.endsWith("/sign")) signed.add(user);
            int today = java.time.LocalDate.now(SignInTasks.ZONE).getDayOfMonth();
            JSONObject page = new JSONObject().put("obtainedFragmentCount", 3)
                    .put("lotterySignCalendarDetails", new org.json.JSONArray().put(new JSONObject().put("day", today).put("signed", signed.contains(user))));
            return new JSONObject().put("code", 10001).put("data", new JSONObject()
                    .put("code", 200).put("success", true).put("data", page));
        };
        user = "welfare-auto-A"; int before = CALLS.size();
        flag(SignInTasks.PLAY, true); AccountAutomationControls.changed(); drain(AccountAutomationControls.class);
        check(CALLS.size() == before + 3, "new welfare switch alone schedules query, sign and verification");
        check(SignInAutomation.status(SignInTasks.PLAY).startsWith("今日已签到"), "new task publishes verified status to its current account");
        before = CALLS.size();
        for (int i = 0; i < 50; i++) AccountAutomationControls.kick();
        drain(AccountAutomationControls.class);
        check(CALLS.size() == before, "repeated lifecycle events preserve new task completion cache");
        user = "welfare-auto-B"; AccountAutomationControls.kick(); drain(AccountAutomationControls.class);
        check(CALLS.size() == before + 3 && signed.contains(user), "second account cannot reuse first account's completion");
        user = "welfare-auto-disabled";
        Plan blocked = new Plan(true, false); PLANS.add(blocked);
        AccountAutomationControls.kick(); await(blocked.started, "new welfare automation begins before switch off");
        WelfareCallback late = Winter.callback;
        flag(SignInTasks.PLAY, false); AccountAutomationControls.changed(); drain(AccountAutomationControls.class);
        late.success(new JSONObject().put("code", 10001).put("data", new JSONObject()));
        check(blocked.cancellations.get() == 1, "new welfare switch off cancels production Call");
        check(preferences("newsoul-account-automation-state").getString(user + ":complete:" + SignInTasks.PLAY, "").isEmpty(), "late callback cannot persist new task success");
        check(SignInAutomation.status(SignInTasks.PLAY).isEmpty(), "cancelled generation leaves no running status");
    }

    static void avatarNotificationChecks() throws Exception {
        android.widget.Toast.shown.clear(); Handler.drain();
        SharedPreferences state = preferences("avatar-test"); String day = SignInTasks.day();
        JSONObject reward = new JSONObject().put("id", "r1").put("createTime", day + " 12:00:00")
                .put("lotteryReward", new JSONObject().put("type", "AVATAR").put("assetName", "月光"));
        for (String title : new String[]{"头像框", "头像碎片", "头像兑换券", "头像体验卡", "头像礼包", "头像背景", "空间装扮"}) {
            check(AvatarRewardNotifications.avatar(new JSONObject().put("type", "AVATAR").put("name", title)) == null,
                    "excluded reward never produces full-avatar notice: " + title);
        }
        check(AvatarRewardNotifications.avatar(new JSONObject().put("type", 59).put("name", "头像空间装扮")) == null,
                "observed numeric decoration type is excluded");
        check(AvatarRewardNotifications.avatar(new JSONObject().put("type", "AVATAR_FRAME").put("name", "月光头像")) == null,
                "conflicting explicit non-avatar type wins over title");
        check(AvatarRewardNotifications.avatar(new JSONObject().put("type", 123).put("name", "月光")) == null,
                "unknown numeric type is not guessed to be avatar");
        check(AvatarRewardNotifications.avatar(new JSONObject().put("name", "月光头像")) == null,
                "title alone does not establish a full avatar");
        SignInTasks.Result numeric = new SignInTasks.Result(true, "今日已抽奖", List.of(new JSONObject()
                .put("type", 31).put("name", "云笙璃").put("itemId", "avatar-item")));
        AtomicInteger previewQueries = new AtomicInteger();
        AvatarRewardNotifications.resolve(numeric, (url, post, data) -> {
            check(url.endsWith("/preview/commonCommodity") && post && data.equals(Map.of("itemIdentity", "avatar-item")),
                    "category uses the host read-only preview with the record's own item ID");
            previewQueries.incrementAndGet();
            return new JSONObject().put("code",10001).put("data",new JSONObject().put("itemCategoryType","SECOND_SUPER_LIMIT"));
        });
        check(previewQueries.get() == 1 && AvatarRewardNotifications.avatar(numeric.drawnRewards.get(0)) != null,
                "observed current full-avatar category works even without avatar words in the title");
        JSONObject misleading = new JSONObject().put("name", "月光头像").put("itemId", "frame-item");
        AvatarRewardNotifications.resolve(new SignInTasks.Result(true,"",List.of(misleading)), (url, post, data) ->
                new JSONObject().put("code",10001).put("data",new JSONObject().put("itemCategoryType","AVATAR_IDENTIFIER")));
        check(AvatarRewardNotifications.avatar(misleading) == null, "authoritative non-avatar category overrides a misleading name");
        try {
            AvatarRewardNotifications.resolve(numeric, (url, post, data) -> new JSONObject().put("code",500));
            throw new AssertionError("failed preview must leave notification unconfirmed");
        } catch (IllegalStateException expected) { checks++; }
        SignInTasks.Result result = new SignInTasks.Result(true, "今日已抽奖", List.of(reward));
        check(AvatarRewardNotifications.complete(state, "A", SignInTasks.BOX, day, result), "reward and completion saved together");
        AtomicBoolean valid = new AtomicBoolean(true);
        AvatarRewardNotifications.deliver(CONTEXT, state, "A", SignInTasks.BOX, day, valid::get);
        check(android.widget.Toast.shown.isEmpty(), "toast waits for main thread");
        valid.set(false); Handler.drain();
        check(android.widget.Toast.shown.isEmpty(), "disabled or switched account cannot show queued notice");
        valid.set(true);
        AvatarRewardNotifications.deliver(CONTEXT, state, "A", SignInTasks.BOX, day, valid::get);
        failCommit.set(true); Handler.drain();
        check(android.widget.Toast.shown.isEmpty(), "receipt save failure produces no toast");
        AvatarRewardNotifications.deliver(CONTEXT, state, "A", SignInTasks.BOX, day, valid::get); Handler.drain();
        check(android.widget.Toast.shown.equals(List.of("抽中头像：月光")), "confirmed full avatar emits the requested toast");
        AvatarRewardNotifications.complete(state, "A", SignInTasks.BOX, day, result);
        for (int i = 0; i < 5; i++) AvatarRewardNotifications.deliver(CONTEXT, state, "A", SignInTasks.BOX, day, valid::get);
        Handler.drain(); check(android.widget.Toast.shown.size() == 1, "durable receipt prevents repeated and recovered notices");
        AvatarRewardNotifications.deliver(CONTEXT, state, "B", SignInTasks.BOX, day, valid::get); Handler.drain();
        check(android.widget.Toast.shown.size() == 1, "other account does not read first account queue");
        failCommit.set(true);
        check(!AvatarRewardNotifications.complete(state, "B", SignInTasks.BOX, day, result), "completion save failure is reported");
        AvatarRewardNotifications.deliver(CONTEXT, state, "B", SignInTasks.BOX, day, valid::get); Handler.drain();
        check(android.widget.Toast.shown.size() == 1, "failed completion save has no queue to deliver");
        AvatarRewardNotifications.complete(state, "B", SignInTasks.BOX, day, result);
        AvatarRewardNotifications.deliver(CONTEXT, state, "B", SignInTasks.BOX, day, valid::get); Handler.drain();
        check(android.widget.Toast.shown.size() == 2, "second account owns an independent receipt");
        AvatarRewardNotifications.complete(state, "C", SignInTasks.BOX, "2000-01-01", result);
        AvatarRewardNotifications.deliver(CONTEXT, state, "C", SignInTasks.BOX, "2000-01-01", valid::get); Handler.drain();
        check(android.widget.Toast.shown.size() == 2, "expired day never displays historical reward notice");
        AvatarRewardNotifications.complete(state, "D", SignInTasks.BOX, day, result);
        AvatarRewardNotifications.complete(state, "D", SignInTasks.BOX, day, new SignInTasks.Result(true, "今日已抽奖"));
        AvatarRewardNotifications.deliver(CONTEXT, state, "D", SignInTasks.BOX, day, valid::get); Handler.drain();
        check(android.widget.Toast.shown.size() == 3, "empty cached result preserves previously undelivered queue");
        SignInTasks.Result incomplete = new SignInTasks.Result(false, "待确认", List.of(reward));
        check(incomplete.drawnRewards.isEmpty(), "unverified result cannot carry rewards into notification");
        user = "avatar-cache-user"; flag(SignInTasks.BOX, true);
        SharedPreferences runtime = preferences("newsoul-account-automation-state");
        AvatarRewardNotifications.complete(runtime, user, SignInTasks.BOX, day, result);
        int calls = CALLS.size();
        SignInAutomation.run(CONTEXT, preferences("settings"), user, AccountAutomationControls.generation()); Handler.drain();
        check(CALLS.size() == calls && android.widget.Toast.shown.size() == 4,
                "production completed-cache branch recovers notice without any new HTTP or draw");
        SignInAutomation.run(CONTEXT, preferences("settings"), user, AccountAutomationControls.generation()); Handler.drain();
        check(android.widget.Toast.shown.size() == 4, "production repeated lifecycle event does not repeat toast");
        flag(SignInTasks.BOX, false);
    }

    public static void main(String[] args) throws Exception {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            if (error instanceof AssertionError && "injected fatal state read".equals(error.getMessage())) { fatalErrors.incrementAndGet(); fatalObserved.countDown(); }
            else if (previous != null) previous.uncaughtException(thread, error);
            else error.printStackTrace();
        });
        try {
            boolean soul637 = args.length > 0 && args[0].equals("6.37");
            installFakeState(soul637);
            transportChecks(); welfareChecks(); welfareAutomationChecks(); lookupChecks(); automationChecks(); avatarNotificationChecks();
            System.out.println("Account HTTP lifecycle (Soul " + (soul637 ? "6.37" : "6.38") + "): " + checks + " production checks passed");
        } finally {
            HostAccountHttp.ready = false;
            for (FakeCall call : CALLS) call.cancel();
            CLIENT.shutdownNow();
            worker(AccountAutomationControls.class).shutdownNow();
            worker(RegistrationDaysFilter.class).shutdownNow();
            worker(SquareIpLocationLookup.class).shutdownNow();
            Thread.setDefaultUncaughtExceptionHandler(previous);
        }
    }
}
