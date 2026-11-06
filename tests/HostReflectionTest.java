package dev.local.newsoul;

import java.lang.reflect.Method;
import com.walid.rxretrofit.HttpSubscriber;

public final class HostReflectionTest {
    public interface Callback {}
    public static class Observable {}
    public static class ChildObservable extends Observable {}
    public static class Api {
        public HttpSubscriber m(Observable value, Callback callback) { throw new AssertionError("must not execute request"); }
    }
    public static class Ambiguous extends Api {
        public HttpSubscriber m(Object value, Callback callback) { throw new AssertionError("must not execute request"); }
    }
    public static class WrongReturn {
        public Object m(Observable value, Callback callback) { return null; }
    }
    public static class Presenter {}
    public static class Fragment { public Presenter E; }
    public static class Adapter { private Presenter c; }
    public static void main(String[] args) throws Exception {
        Method expected = Api.class.getMethod("m", Observable.class, Callback.class);
        if (!HostSymbols.subscribe(new Api(),new ChildObservable(),Callback.class).equals(expected)) throw new AssertionError();
        for (Object api : new Object[]{new Ambiguous(),new WrongReturn()}) {
            try { HostSymbols.subscribe(api,new ChildObservable(),Callback.class); throw new AssertionError(); }
            catch (IllegalStateException | NoSuchMethodException correct) { }
        }
        try { HostSymbols.subscribe(new Api(),null,Callback.class); throw new AssertionError(); }
        catch(IllegalArgumentException correct) { }
        ClassLoader loader = new ClassLoader() {
            @Override public Class<?> loadClass(String name) throws ClassNotFoundException {
                if(name.endsWith(".ConversationFragment"))return Fragment.class;
                if(name.endsWith(".ConversationAdapter"))return Adapter.class;
                return super.loadClass(name);
            }
        };
        if(HostSymbols.chatPresenter(loader)!=Presenter.class || HostSymbols.recipientPresenter(loader)!=Presenter.class)throw new AssertionError();
        System.out.println("6 host-reflection checks passed; no host method executed");
    }
}
