package dev.local.newsoul;

import java.lang.reflect.Method;
import java.net.URI;

/** Read an already decoded Retrofit envelope without consuming the response body. */
final class HostDecodedResponse {
    final Method parse;
    private final Class<?> envelopeType;
    private final Method request, url, body, data;

    HostDecodedResponse(ClassLoader loader) throws Exception {
        HostBindings.HttpTypes http = new HostBindings.HttpTypes(loader);
        Class<?> result = loader.loadClass("retrofit2.Response");
        envelopeType = loader.loadClass("cn.soulapp.android.net.HttpResult");
        parse = HostBindings.method(loader.loadClass("retrofit2.OkHttpCall"), "parseResponse", result, false, http.response);
        request = HostBindings.method(http.response, "t", http.request, false);
        url = HostBindings.method(http.request, "k", http.url, false);
        body = HostBindings.method(result, "body", Object.class, false);
        data = HostBindings.method(envelopeType, "getData", Object.class, false);
    }

    Object data(Object raw, Object result, String path) throws Exception {
        if (raw == null || result == null) return null;
        Object req = request.invoke(raw);
        if (req == null || !matchesPath(String.valueOf(url.invoke(req)), path)) return null;
        Object envelope = body.invoke(result);
        return envelopeType.isInstance(envelope) ? data.invoke(envelope) : null;
    }

    static boolean matchesPath(String url, String path) {
        if (url == null || path == null) return false;
        try { return path.equals(URI.create(url).getRawPath()); }
        catch (IllegalArgumentException invalid) { return false; }
    }
}
