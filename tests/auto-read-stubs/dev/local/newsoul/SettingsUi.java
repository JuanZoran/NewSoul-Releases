package dev.local.newsoul;
import android.content.*;
import java.lang.reflect.Proxy;
final class SettingsUi {
    static boolean enabled, blockReceipt;
    static SharedPreferences preferences(Context value) {
        return (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(p,m,a)-> {
            if(m.getName().equals("getBoolean")) {
                if(a[0].equals("chat_block_read_receipt_v1")) return blockReceipt;
                if(!a[0].equals("chat_auto_read_v1") || !a[1].equals(false)) throw new AssertionError("wrong default/key");
                return enabled;
            }
            throw new AssertionError(m);
        });
    }
}
