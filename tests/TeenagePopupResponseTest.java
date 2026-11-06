package dev.local.newsoul;

import static dev.local.newsoul.ChatGateControlsTest.*;

/** Exercise decoded transport, exact endpoint isolation and every flag combination. */
public final class TeenagePopupResponseTest {
    private static int assertions;
    public static class Flags {
        public boolean isTeenageMode, needTeenageExam, isPopTeenWindow;
        public boolean unrelated = true;
        Flags(int bits) {
            isTeenageMode=(bits&1)!=0; needTeenageExam=(bits&2)!=0; isPopTeenWindow=(bits&4)!=0;
        }
        int bits() { return (isTeenageMode?1:0)|(needTeenageExam?2:0)|(isPopTeenWindow?4:0); }
    }
    public static class InvalidFlags { public boolean isTeenageMode, needTeenageExam; public String isPopTeenWindow; }
    public static class LegacyRequest { final Url url; LegacyRequest(String value){url=new Url(value);} public Url k(){return url;} }
    public static class LegacyRaw { final LegacyRequest req; LegacyRaw(String value){req=new LegacyRequest(value);} public LegacyRequest t(){return req;} }
    public interface LegacyHttpCall { LegacyRequest request(); LegacyRaw execute(); }
    public static class LegacyCall { Result parseResponse(LegacyRaw raw){return null;} }
    public static class FailedEnvelope extends Envelope {
        FailedEnvelope(Object data) { super(data); }
        public Object getData() { throw new IllegalStateException("decode accessor failed"); }
    }
    static ClassLoader flagsLoader(Class<?> flags) {
        ClassLoader transport=loader(false);
        return new ClassLoader() {
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name.equals("cn.soulapp.android.client.component.middle.platform.model.api.account.FuncSetting")) return flags;
                return transport.loadClass(name);
            }
        };
    }
    static ClassLoader legacyLoader() {
        ClassLoader modern=flagsLoader(Flags.class);
        return new ClassLoader() {
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                switch(name) {
                    case "okhttp3.Call": return LegacyHttpCall.class;
                    case "okhttp3.r": return LegacyRequest.class;
                    case "okhttp3.t": return LegacyRaw.class;
                    case "okhttp3.m": return Url.class;
                    case "retrofit2.OkHttpCall": return LegacyCall.class;
                    default: return modern.loadClass(name);
                }
            }
        };
    }
    static void verify(boolean value, String message) { assertions++; if(!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        TeenagePopupResponse response=new TeenagePopupResponse(flagsLoader(Flags.class));
        Raw raw=new Raw("https://api-a.soulapp.cn/teenager/config?sample=1");
        verify(response.parse.equals(Call.class.getDeclaredMethod("parseResponse",Raw.class)),"exact parse overload");
        for (int bits=0;bits<8;bits++) for (boolean enabled:new boolean[]{false,true}) {
            Flags model=new Flags(bits); Result result=new Result(new Envelope(model));
            verify(response.apply(raw,result,enabled)==enabled,"enabled response classification");
            verify(model.bits()==(enabled?6:bits),"Xyner flags or untouched disabled response");
            verify(model.unrelated,"unrelated setting retained");
            verify(response.apply(raw,result,enabled)==enabled && model.bits()==(enabled?6:bits),"idempotent processing");
        }
        for (String path:new String[]{"/chat/limitInfo","/teenager/configExtra","/teenager/config/","/v2/teenager/config",
                "/other?next=/teenager/config","/teenager/%63onfig","bad url"}) {
            Flags model=new Flags(1);
            verify(!response.apply(new Raw(path),new Result(new Envelope(model)),true),"unrelated path rejected: "+path);
            verify(model.bits()==1,"unrelated flags preserved");
        }
        for (Object body:new Object[]{null,new Object(),new Envelope(null),new Envelope(new Object())})
            verify(!response.apply(raw,new Result(body),true),"foreign or empty body retained");
        verify(!response.apply(null,new Result(new Envelope(new Flags(1))),true),"null raw");
        verify(!response.apply(raw,null,true),"null decoded response");
        verify(!response.apply(new Raw(null),new Result(new Envelope(new Flags(1))),true),"null request URL");
        Flags failed=new Flags(1);
        try { response.apply(raw,new Result(new FailedEnvelope(failed)),true); throw new AssertionError("expected accessor failure"); }
        catch (java.lang.reflect.InvocationTargetException expected) { verify(failed.bits()==1,"read failure leaves model intact"); }
        try { new TeenagePopupResponse(flagsLoader(InvalidFlags.class)); throw new AssertionError("expected changed ABI rejection"); }
        catch (NoSuchFieldException expected) { verify(true,"changed field type rejected at registration"); }
        // Both features share the decoder while their target paths stay independent.
        ClassLoader both=flagsLoader(Flags.class);
        ChatGateResponse gift=new ChatGateResponse(both);
        Model limit=new Model(true);
        verify(!response.apply(new Raw("https://api-a.soulapp.cn/chat/limitInfo"),new Result(new Envelope(limit)),true)
                && limit.limit,"teenager handler preserves gift response");
        Flags flags=new Flags(1);
        verify(!gift.apply(raw,new Result(new Envelope(flags)),true) && flags.bits()==1,"gift handler preserves teenager response");
        verify(gift.apply(new Raw("https://api-a.soulapp.cn/chat/limitInfo"),new Result(new Envelope(limit)),true)
                && !limit.limit,"gift behavior preserved after extraction");
        TeenagePopupResponse legacy=new TeenagePopupResponse(legacyLoader());
        verify(legacy.parse.equals(LegacyCall.class.getDeclaredMethod("parseResponse",LegacyRaw.class)),"4.88 parse overload");
        for(int bits=0;bits<8;bits++) for(boolean enabled:new boolean[]{false,true}) {
            Flags model=new Flags(bits);
            verify(legacy.apply(new LegacyRaw("https://api-a.soulapp.cn/teenager/config"),new Result(new Envelope(model)),enabled)==enabled,
                    "4.88 enabled classification");
            verify(model.bits()==(enabled?6:bits),"4.88 Xyner flags and switch off");
        }
        Flags untouched=new Flags(1);
        verify(!legacy.apply(new LegacyRaw("https://api-a.soulapp.cn/teenager/configExtra"),new Result(new Envelope(untouched)),true)
                && untouched.bits()==1,"4.88 endpoint isolation");
        System.out.println("TeenagePopupResponse: "+assertions+" production checks passed");
    }
}
