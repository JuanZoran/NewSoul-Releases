package dev.local.newsoul;

import dalvik.system.PathClassLoader;
import java.lang.annotation.Annotation;

/** Resolve production bindings in an isolated host loader; never launch or call Soul. */
public final class TeenagePopupHostProbe {
    public static void main(String[] args) throws Exception {
        ClassLoader loader=new PathClassLoader(args[0],TeenagePopupHostProbe.class.getClassLoader());
        TeenagePopupResponse response=new TeenagePopupResponse(loader);
        if(!response.parse.getName().equals("parseResponse")) throw new AssertionError("parse method");
        loader.loadClass("retrofit2.OkHttpCall$1").getDeclaredMethods();
        Class<?> api=loader.loadClass("cn.soulapp.android.square.IAccountApi");
        boolean endpoint=false;
        for(Annotation annotation:api.getDeclaredMethod("getTeenagerConfig").getDeclaredAnnotations())
            if(annotation.annotationType().getName().equals("retrofit2.http.GET"))
                endpoint="teenager/config".equals(annotation.annotationType().getMethod("value").invoke(annotation));
        if(!endpoint) throw new AssertionError("teenager endpoint");
        System.out.println("PASS production reader, three flags, async caller and teenager/config endpoint");
    }
}
