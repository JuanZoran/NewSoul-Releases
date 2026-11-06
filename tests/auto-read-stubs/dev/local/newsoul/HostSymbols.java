package dev.local.newsoul;
import java.lang.reflect.Method;
final class HostSymbols {
    static Method method(String key, ClassLoader loader) throws Exception {
        if(key.equals(HostDexRules.CONTEXT)) return AutoReadControlsTest.Access.class.getMethod("context");
        if(key.equals(HostDexRules.RECEIVE)) return AutoReadControlsTest.Dispatch.class.getMethod("run");
        if(key.equals(HostDexRules.READ_ACK)) return AutoReadControlsTest.Conversation.class.getMethod("ackMsgRead",AutoReadControlsTest.Message.class);
        throw new AssertionError(key);
    }
}
