package dev.local.newsoul;

import cn.soulapp.android.component.chat.fragment.BaseConversationFragment;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Production UI ABI resolution against modern, legacy and incompatible neighboring signatures. */
public final class HostUiBindingsTest {
    private static int checks, invoked;
    interface Attempt { void run() throws Exception; }
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("check " + checks);
    }
    private static void rejects(Attempt attempt) throws Exception {
        try { attempt.run(); throw new AssertionError("incompatible UI ABI accepted"); }
        catch (ReflectiveOperationException expected) { checks++; }
    }
    public static void hostInvoked() { invoked++; throw new AssertionError("binding executed host code"); }
    public static class Context {}
    public static class View {}
    public static class Message {}
    public static class Presenter {}
    public static class ModernFragment {
        private void ac(View view, Message message, int index) { hostInvoked(); }
        private void ac(View view, Object message, int index) { hostInvoked(); }
    }
    public static class LegacyFragment extends BaseConversationFragment {}
    public static class Menu {
        public Menu(String title, int icon, int id) { hostInvoked(); }
        public Menu(String title, Integer icon, int id) { hostInvoked(); }
        public int b() { hostInvoked(); return 0; }
        public int b(int ignored) { hostInvoked(); return 0; }
    }
    public static class WrongMenu {
        public WrongMenu(String title, Integer icon, int id) { hostInvoked(); }
        public int b() { hostInvoked(); return 0; }
    }
    public static class WrongMenuId {
        public WrongMenuId(String title, int icon, int id) { hostInvoked(); }
        public int b(int ignored) { hostInvoked(); return 0; }
    }
    public static class Sender {
        private void p1(Message message) { hostInvoked(); }
        private void p1(Object message) { hostInvoked(); }
    }
    public static class LegacySender { private void a1(Message message) { hostInvoked(); } }
    public static class WrongSender { private void p1(Object message) { hostInvoked(); } }
    public static class Gate { public static void a(Message message) { hostInvoked(); } }
    public static class WrongGate { public static void a(Object message) { hostInvoked(); } }
    public static class ModernHelper {
        static void p(Message m, ModernFragment f, Context c, Presenter p, View v, int i, View row, Menu item, int j) { hostInvoked(); }
        static void p(Object m, ModernFragment f, Context c, Presenter p, View v, int i, View row, Menu item, int j) { hostInvoked(); }
        static List<Menu> k(ModernFragment f, View v, Message m) { hostInvoked(); return null; }
        static List<Menu> k(ModernFragment f, View v, Object m) { hostInvoked(); return null; }
    }
    public static class LegacyHelper {
        static void f(Message m, Context c, BaseConversationFragment f, Presenter p, View v, int i, View row, Menu item, int j) { hostInvoked(); }
        static List<Menu> d(BaseConversationFragment f, View v, Message m) { hostInvoked(); return null; }
    }
    public static class WrongClickHelper {
        static void p(Message m, Context c, ModernFragment f, Presenter p, View v, int i, View row, Menu item, int j) { hostInvoked(); }
        static List<Menu> k(ModernFragment f, View v, Message m) { hostInvoked(); return null; }
    }
    public static class WrongListHelper {
        static void p(Message m, ModernFragment f, Context c, Presenter p, View v, int i, View row, Menu item, int j) { hostInvoked(); }
        static List<Menu> k(ModernFragment f, View v, Object m) { hostInvoked(); return null; }
    }
    public static class Holder {}
    public static class Square {
        void d(Holder holder, Object post) { hostInvoked(); }
        void d(Holder holder, String post) { hostInvoked(); }
        void j(Holder holder) { hostInvoked(); }
        void h(Holder holder) { hostInvoked(); }
    }
    public static class LegacySquare {
        void c(Holder holder, Object post) { hostInvoked(); }
        void h(Holder holder) { hostInvoked(); }
        void g(Holder holder) { hostInvoked(); }
    }
    public static class WrongSquareBind {
        void d(Holder holder, String post) { hostInvoked(); }
        void j(Holder holder) { hostInvoked(); }
        void h(Holder holder) { hostInvoked(); }
    }
    public static class WrongSquareRecycle {
        void d(Holder holder, Object post) { hostInvoked(); }
        void j(Object holder) { hostInvoked(); }
        void h(Holder holder) { hostInvoked(); }
    }
    public static class WrongSquareAttach {
        void d(Holder holder, Object post) { hostInvoked(); }
        void j(Holder holder) { hostInvoked(); }
        void h(Object holder) { hostInvoked(); }
    }
    public static class WrongLegacySquare {
        void c(Holder holder, String post) { hostInvoked(); }
        void h(Holder holder) { hostInvoked(); }
        void g(Holder holder) { hostInvoked(); }
    }
    public static class RecyclerHolder { public View itemView; }
    public static class WrongRecyclerHolder { public View item; }
    public static class Post {}
    public static class CityActivity {}
    public static class ModernCity {
        Post i(int index) { hostInvoked(); return null; }
        Post i(Integer index) { hostInvoked(); return null; }
        void onBindViewHolder(RecyclerHolder holder, int index) { hostInvoked(); }
        void onBindViewHolder(Object holder, int index) { hostInvoked(); }
    }
    public static class LegacyCity {
        void onBind(Post post, int index) { hostInvoked(); }
        void onBind(Object post, int index) { hostInvoked(); }
    }
    public static class WrongCityItem {
        Post i(Integer index) { hostInvoked(); return null; }
        void onBindViewHolder(RecyclerHolder holder, int index) { hostInvoked(); }
    }
    public static class WrongCityBind {
        Post i(int index) { hostInvoked(); return null; }
        void onBindViewHolder(Object holder, int index) { hostInvoked(); }
    }
    public static class WrongLegacyCity { void onBind(Object post, int index) { hostInvoked(); } }

    private static ClassLoader loader(Map<String, Class<?>> types) {
        return new ClassLoader(HostUiBindingsTest.class.getClassLoader()) {
            @Override public Class<?> loadClass(String name) throws ClassNotFoundException {
                return types.containsKey(name) ? types.get(name) : super.loadClass(name);
            }
        };
    }
    private static final String MENU = "cn.soulapp.android.component.chat.window.ChatLongClickPopUp$a";
    private static final String HELPER = "cn.soulapp.android.component.chat.utils.u0";
    private static final String SQUARE = "cn.soulapp.android.component.square.main.squarepost.SquareAdapter";
    private static final String CITY = "cn.soulapp.android.component.square.location.PostLightAdapter";
    private static final String LEGACY_CITY = "cn.soulapp.android.component.square.main.PostVH";
    private static final String RECYCLER_HOLDER = "androidx.recyclerview.widget.RecyclerView$ViewHolder";
    private static ClassLoader chat(boolean legacy, Map<String, Class<?>> changes) {
        Map<String, Class<?>> types = new HashMap<>(Map.ofEntries(
                Map.entry(HostBindings.FRAGMENT, legacy ? LegacyFragment.class : ModernFragment.class),
                Map.entry(HostBindings.MESSAGE, Message.class),
                Map.entry(legacy ? HostBindings.LEGACY_SENDER : HostBindings.SENDER,
                        legacy ? LegacySender.class : Sender.class),
                Map.entry("android.content.Context", Context.class), Map.entry("android.view.View", View.class),
                Map.entry(MENU, Menu.class),
                Map.entry(legacy ? "cn.soulapp.android.component.chat.utils.o" : HELPER,
                        legacy ? LegacyHelper.class : ModernHelper.class),
                Map.entry("cn.soulapp.android.chat.utils.f", Gate.class)));
        types.putAll(changes);
        return loader(types);
    }
    private static ClassLoader feed(boolean legacy, Map<String, Class<?>> changes) {
        Map<String, Class<?>> types = new HashMap<>(Map.ofEntries(
                Map.entry(SQUARE, legacy ? LegacySquare.class : Square.class),
                Map.entry("cn.soulapp.android.component.square.main.squarepost.viewholder.BaseSquareViewHolder", Holder.class),
                Map.entry("cn.soulapp.android.component.square.location.LocationSquareActivity", CityActivity.class),
                Map.entry(RECYCLER_HOLDER, RecyclerHolder.class),
                Map.entry(legacy ? LEGACY_CITY : CITY, legacy ? LegacyCity.class : ModernCity.class)));
        types.putAll(changes);
        return loader(types);
    }
    private static void method(Method method, Class<?> owner, String name, Class<?>... arguments) {
        check(method.getDeclaringClass() == owner && method.getName().equals(name));
        check(java.util.Arrays.equals(method.getParameterTypes(), arguments));
    }
    public static void main(String[] args) throws Exception {
        HostBindings.ChatMenu modern = new HostBindings.ChatMenu(chat(false, Map.of()), Presenter.class);
        check(!modern.legacy && modern.clickContextIndex == 2);
        check(modern.fragmentType == ModernFragment.class && modern.messageType == Message.class);
        check(modern.helperType == ModernHelper.class && modern.item.getDeclaringClass() == Menu.class);
        check(java.util.Arrays.equals(modern.item.getParameterTypes(), new Class<?>[]{String.class, int.class, int.class}));
        method(modern.itemId, Menu.class, "b");
        method(modern.send, Sender.class, "p1", Message.class);
        method(modern.gate, Gate.class, "a", Message.class);
        method(modern.click, ModernHelper.class, "p", Message.class, ModernFragment.class, Context.class,
                Presenter.class, View.class, int.class, View.class, Menu.class, int.class);
        method(modern.list, ModernHelper.class, "k", ModernFragment.class, View.class, Message.class);
        HostBindings.ChatMenu legacy = new HostBindings.ChatMenu(chat(true, Map.of()), null);
        check(legacy.legacy && legacy.clickContextIndex == 1 && legacy.gate == null);
        check(legacy.helperType == LegacyHelper.class && legacy.fragmentType == LegacyFragment.class);
        method(legacy.send, LegacySender.class, "a1", Message.class);
        method(legacy.click, LegacyHelper.class, "f", Message.class, Context.class, BaseConversationFragment.class,
                Presenter.class, View.class, int.class, View.class, Menu.class, int.class);
        method(legacy.list, LegacyHelper.class, "d", BaseConversationFragment.class, View.class, Message.class);
        rejects(() -> new HostBindings.ChatMenu(chat(false, Map.of()), null));
        rejects(() -> new HostBindings.ChatMenu(chat(false, Map.of(MENU, WrongMenu.class)), Presenter.class));
        rejects(() -> new HostBindings.ChatMenu(chat(false, Map.of(MENU, WrongMenuId.class)), Presenter.class));
        rejects(() -> new HostBindings.ChatMenu(chat(false, Map.of(HostBindings.SENDER, WrongSender.class)), Presenter.class));
        rejects(() -> new HostBindings.ChatMenu(chat(false, Map.of("cn.soulapp.android.chat.utils.f", WrongGate.class)), Presenter.class));
        rejects(() -> new HostBindings.ChatMenu(chat(false, Map.of(HELPER, WrongClickHelper.class)), Presenter.class));
        rejects(() -> new HostBindings.ChatMenu(chat(false, Map.of(HELPER, WrongListHelper.class)), Presenter.class));
        check("c_ct_longclick_menu_share".equals(HostBindings.chatShareIcon(false)));
        check("c_ct_group_share_white".equals(HostBindings.chatShareIcon(true)));
        method(HostBindings.bubbleLongClick(ModernFragment.class, Message.class, View.class, false),
                ModernFragment.class, "ac", View.class, Message.class, int.class);
        method(HostBindings.bubbleLongClick(LegacyFragment.class, Message.class, View.class, true),
                BaseConversationFragment.class, "onBubbleLongClick", View.class, Message.class, int.class);
        rejects(() -> HostBindings.bubbleLongClick(ModernFragment.class, Message.class, Context.class, false));
        rejects(() -> HostBindings.bubbleLongClick(LegacyFragment.class, Presenter.class, View.class, true));
        rejects(() -> HostBindings.bubbleLongClick(LegacyFragment.class, Message.class, View.class, false));

        HostBindings.SquareFeed square = new HostBindings.SquareFeed(feed(false, Map.of()), false);
        check(square.adapterType == Square.class && square.holderType == Holder.class);
        method(square.bind, Square.class, "d", Holder.class, Object.class);
        method(square.recycle, Square.class, "j", Holder.class);
        method(square.attach, Square.class, "h", Holder.class);
        HostBindings.SquareFeed oldSquare = new HostBindings.SquareFeed(feed(true, Map.of()), true);
        method(oldSquare.bind, LegacySquare.class, "c", Holder.class, Object.class);
        method(oldSquare.recycle, LegacySquare.class, "h", Holder.class);
        method(oldSquare.attach, LegacySquare.class, "g", Holder.class);
        for (Class<?> incompatible : new Class<?>[]{WrongSquareBind.class, WrongSquareRecycle.class, WrongSquareAttach.class})
            rejects(() -> new HostBindings.SquareFeed(feed(false, Map.of(SQUARE, incompatible)), false));
        rejects(() -> new HostBindings.SquareFeed(feed(true, Map.of(SQUARE, WrongLegacySquare.class)), true));

        HostBindings.CityFeed city = new HostBindings.CityFeed(feed(false, Map.of()), Post.class, false);
        check(city.activityType == CityActivity.class && city.adapterType == ModernCity.class);
        check(city.itemView.getDeclaringClass() == RecyclerHolder.class && city.itemView.getName().equals("itemView"));
        method(city.item, ModernCity.class, "i", int.class);
        method(city.bind, ModernCity.class, "onBindViewHolder", RecyclerHolder.class, int.class);
        HostBindings.CityFeed oldCity = new HostBindings.CityFeed(feed(true, Map.of()), Post.class, true);
        check(oldCity.item == null && oldCity.adapterType == LegacyCity.class);
        method(oldCity.bind, LegacyCity.class, "onBind", Post.class, int.class);
        rejects(() -> new HostBindings.CityFeed(feed(false, Map.of(RECYCLER_HOLDER, WrongRecyclerHolder.class)), Post.class, false));
        rejects(() -> new HostBindings.CityFeed(feed(false, Map.of(CITY, WrongCityItem.class)), Post.class, false));
        rejects(() -> new HostBindings.CityFeed(feed(false, Map.of(CITY, WrongCityBind.class)), Post.class, false));
        rejects(() -> new HostBindings.CityFeed(feed(true, Map.of(LEGACY_CITY, WrongLegacyCity.class)), Post.class, true));
        check(invoked == 0);
        System.out.println("HostUiBindingsTest: " + checks + " modern/legacy signatures and side-effect-free resolution checks passed");
    }
}
