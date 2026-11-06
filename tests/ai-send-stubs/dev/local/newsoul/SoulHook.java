package dev.local.newsoul;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
final class SoulHook {
    interface Chain { Object proceed() throws Throwable; }
    interface Interceptor { Object intercept(Chain chain) throws Throwable; }
    static Interceptor interceptor;
    static final java.util.Set<String> deoptimized = new java.util.HashSet<>();
    void deoptimize(Method method){deoptimized.add(method.getName());}
    Builder hook(Method method){return new Builder();}
    static final class Builder {
        Builder setId(String id){return this;}
        Builder setExceptionMode(ExceptionMode mode){return this;}
        void intercept(Interceptor hook){interceptor=hook;}
    }
    static Object create(Chain factory) {
        try { return interceptor==null?factory.proceed():interceptor.intercept(factory); }
        catch(Throwable error){throw new AssertionError(error);}
    }
}
