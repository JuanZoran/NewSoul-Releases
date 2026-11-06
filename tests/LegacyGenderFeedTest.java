package dev.local.newsoul;

import android.content.Context;
import android.os.Handler;
import android.view.View;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import io.github.libxposed.api.*;

/** Exercise numeric metadata all the way through old adapter hooks and completion refresh. */
public class LegacyGenderFeedTest {
    public static class Square {
        public void c(CityFeedFilterTest.Holder holder, Object post) {}
        public void h(CityFeedFilterTest.Holder holder) {}
        public void g(CityFeedFilterTest.Holder holder) {}
    }
    public static class CityHolder extends CityFeedFilterTest.Holder {
        public CityHolder() { super(new Context()); }
        public void onBind(CityFeedFilterTest.Post post, int position) {}
    }
    static int checks;
    static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
    public static void main(String[] args) throws Throwable {
        Field ioField = RegistrationDaysFilter.class.getDeclaredField("IO"); ioField.setAccessible(true);
        ThreadPoolExecutor io = (ThreadPoolExecutor) ioField.get(null);
        try {
            Map<Executable, XposedInterface.Hooker> hooks = CityFeedFilterTest.hooks;
            XposedModule module = new XposedModule() {};
            module.attachFramework(CityFeedFilterTest.proxy(XposedInterface.class, (p, method, a) -> {
                if (method.getName().equals("hook")) return CityFeedFilterTest.proxy(XposedInterface.HookBuilder.class, (b, bm, ba) -> {
                    if (bm.getName().equals("intercept")) { hooks.put((Executable) a[0], (XposedInterface.Hooker) ba[0]); return null; }
                    return b;
                });
                return method.getName().equals("deoptimize") ? true : null;
            }), () -> {});
            ClassLoader loader = new ClassLoader() {
                public Class<?> loadClass(String name) throws ClassNotFoundException {
                    return switch (name) {
                        case "cn.soulapp.android.square.post.bean.Post" -> CityFeedFilterTest.Post.class;
                        case "cn.soulapp.android.square.bean.Tag" -> CityFeedFilterTest.Tag.class;
                        case "cn.soulapp.android.component.square.main.squarepost.SquareAdapter" -> Square.class;
                        case "cn.soulapp.android.component.square.main.squarepost.viewholder.BaseSquareViewHolder", "androidx.recyclerview.widget.RecyclerView$ViewHolder" -> CityFeedFilterTest.Holder.class;
                        case "cn.soulapp.android.component.square.location.LocationSquareActivity" -> CityFeedFilterTest.City.class;
                        case "cn.soulapp.android.component.square.main.PostVH" -> CityHolder.class;
                        default -> super.loadClass(name);
                    };
                }
            };
            SquareFeedFilter.install(module, loader);
            check(SquareFeedFilter.ready && SquareFeedFilter.cityReady, "legacy hooks ready");
            SettingsUi.values.put(SquareFeedFilter.ENABLED, true);
            SettingsUi.values.put(SquareFeedFilter.GENDER, "FEMALE");
            CityFeedFilterTest.Holder row = new CityFeedFilterTest.Holder(new Context());
            row.itemView.parent = new View(new Context()); row.itemView.parent.id = 1;
            Method bind = Square.class.getMethod("c", CityFeedFilterTest.Holder.class, Object.class);
            Square adapter = new Square();
            for (int gender : new int[]{0, 1}) {
                // Keep completion blocked until the pending row has been observed.
                HostAccountHttp.started = new CountDownLatch(1);
                HostAccountHttp.release = new CountDownLatch(1);
                HostAccountHttp.gender = gender;
                CityFeedFilterTest.Post post = new CityFeedFilterTest.Post(1);
                post.authorIdEcpt = "numeric-" + gender;
                CityFeedFilterTest.invoke(bind, adapter, row, post);
                check(HostAccountHttp.started.await(2, TimeUnit.SECONDS), "metadata request starts");
                check(CityFeedFilterTest.hidden(row), "pending author waits before draw");
                HostAccountHttp.release.countDown();
                io.submit(() -> {}).get(2, TimeUnit.SECONDS); Handler.drain();
                check(CityFeedFilterTest.hidden(row) == (gender == 0), "numeric completion refreshes existing row");
                SettingsUi.values.put(SquareFeedFilter.GENDER, "MALE");
                SquareFeedFilter.refresh(); Handler.drain();
                check(CityFeedFilterTest.hidden(row) == (gender == 1), "selection change uses cached gender");
                SettingsUi.values.put(SquareFeedFilter.GENDER, "FEMALE");
            }
            SettingsUi.values.put(SquareFeedFilter.ENABLED, false); SquareFeedFilter.refresh(); Handler.drain();
            check(!CityFeedFilterTest.hidden(row) && row.itemView.lp.height == 120, "disabled filter restores row dimensions");
            check(HostAccountHttp.calls == 2, "selection changes do not duplicate requests");
            System.out.println("Legacy gender feed: " + checks + " production checks passed");
        } finally { HostAccountHttp.release.countDown(); io.shutdownNow(); }
    }
}
