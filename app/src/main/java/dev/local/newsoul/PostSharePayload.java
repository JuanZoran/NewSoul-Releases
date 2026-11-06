package dev.local.newsoul;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Frozen post card, restored separately for each recipient and sent by Soul's share sender. */
final class PostSharePayload {
    private static final int MAX_JSON_CHARS = 2 * 1024 * 1024;
    private final Factory factory;
    private final String json, id;

    private PostSharePayload(Factory factory, String json, String id) {
        this.factory = factory; this.json = json; this.id = id;
    }

    void send(String target) throws Exception {
        if (target == null || target.isEmpty()) throw new IllegalArgumentException("Missing recipient");
        // The original Post and other recipients' mutable copies never reach this dispatch.
        factory.send.invoke(factory.sender, restore(), target);
    }

    private Object restore() throws Exception {
        Object post = factory.decode.invoke(null, json, factory.postClass);
        if (post == null || !factory.postClass.isInstance(post) || !id.equals(factory.id.get(post)))
            throw new IllegalArgumentException("Invalid post snapshot");
        return post;
    }

    static final class Factory {
        final Class<?> postClass;
        final Field id;
        final Method encode, decode, send;
        final Object sender;

        Factory(Class<?> postClass, Method encode, Method decode, Method send, Object sender) throws Exception {
            this.postClass = postClass; this.id = postClass.getField("postIdEcpt");
            this.encode = encode; this.decode = decode; this.send = send; this.sender = sender;
        }

        static Factory resolve(ClassLoader loader) throws Exception {
            Class<?> post = loader.loadClass("cn.soulapp.android.square.post.bean.Post");
            boolean legacy = HostBindings.legacyChat(loader);
            Class<?> json = loader.loadClass(legacy ? "ks.i" : "cn.soulapp.imlib.utils.j");
            Class<?> sender = loader.loadClass(legacy ? "cn.soulapp.android.component.chat.utils.t3" : "cn.soulapp.android.component.chat.utils.n6");
            Method encode = json.getMethod("b", Object.class);
            Method decode = json.getMethod("d", String.class, Class.class);
            Method send = sender.getMethod("g", post, String.class);
            Field singleton = sender.getField("a");
            if (!Modifier.isStatic(encode.getModifiers()) || encode.getReturnType() != String.class
                    || !Modifier.isStatic(decode.getModifiers()) || send.getReturnType() != void.class
                    || Modifier.isStatic(send.getModifiers()) || singleton.getType() != sender
                    || !Modifier.isStatic(singleton.getModifiers()))
                throw new NoSuchMethodException("Unexpected post sender contract");
            Object instance = singleton.get(null);
            if (instance == null) throw new IllegalStateException("Missing native post sender");
            return new Factory(post, encode, decode, send, instance);
        }

        PostSharePayload snapshot(Object post) throws Exception {
            if (!postClass.isInstance(post)) throw new IllegalArgumentException("Missing post");
            Object key = id.get(post);
            if (!(key instanceof String) || ((String) key).isEmpty())
                throw new IllegalArgumentException("Missing post id");
            String json = (String) encode.invoke(null, post);
            if (json == null || json.isEmpty() || json.length() > MAX_JSON_CHARS)
                throw new IllegalArgumentException("Invalid post data");
            PostSharePayload payload = new PostSharePayload(this, json, (String) key);
            payload.restore(); // Fail before opening a picker, not after partial dispatch.
            return payload;
        }
    }
}
