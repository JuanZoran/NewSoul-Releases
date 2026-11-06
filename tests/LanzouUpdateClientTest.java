package dev.local.newsoul;

import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONArray;
import org.json.JSONObject;

public final class LanzouUpdateClientTest {
    static int checks;
    static final String HTML = "var tok='123'; var key='abc'; url:'/filemoreajax.php?file=1', data:{'lx':2,'fid':1,'uid':'2','puid':'abc','pg':pgs,'rep':'0','t':tok,'k':key,'up':1,'ls':1,'pwd':pwd}";
    static void yes(boolean ok) { checks++; if (!ok) throw new AssertionError("check " + checks); }
    interface Work { void run() throws Exception; }
    static void fail(Work work) throws Exception { checks++; try { work.run(); } catch (Exception expected) { return; } throw new AssertionError("expected failure " + checks); }
    static JSONObject file(String name, String id, int type) { return new JSONObject().put("name_all", name).put("id", id).put("t", type); }
    static String page(JSONArray files) { return new JSONObject().put("zt", 1).put("text", files).toString(); }
    static LanzouUpdateClient.Release index(JSONArray files) throws Exception { return LanzouUpdateClient.check((url, body) -> body == null ? HTML : page(files)); }
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--live".equals(args[0])) {
            LanzouUpdateClient.Release live = LanzouUpdateClient.check();
            System.out.println("Live release: " + live.version + " " + live.url); return;
        }
        if (args.length == 2) {
            String html = Files.readString(Path.of(args[0])), json = Files.readString(Path.of(args[1]));
            LanzouUpdateClient.Release fixture = LanzouUpdateClient.check((url, body) -> body == null ? html : json);
            System.out.println("Fixture release: " + fixture.version + " " + fixture.url); return;
        }
        yes(LanzouUpdateClient.compare("1.10.0", "1.9.99") > 0);
        yes(LanzouUpdateClient.compare("2.0.0", "1.999.999") > 0);
        yes(LanzouUpdateClient.compare("1.31.0", "1.31.0") == 0);
        yes(LanzouUpdateClient.compare("1.30.7", "1.31.0") < 0);
        fail(() -> LanzouUpdateClient.compare("1.2.3-beta", "1.2.3"));
        fail(() -> LanzouUpdateClient.compare("1.2", "1.2.3"));
        fail(() -> LanzouUpdateClient.compare("9999999999.1.1", "1.2.3"));
        JSONArray list = new JSONArray().put(file("new-soul-1.9.0.zip", "i123456", 0))
                .put(file("new-soul-1.10.0.zip", "i123457", 0))
                .put(file("new-soul-99.0.0.zip", "https://evil.example", 1))
                .put(file("new-soul-internal-99.0.0.zip", "i123458", 0));
        yes(index(list).version.equals("1.10.0"));
        yes(index(list).url.equals(LanzouUpdateClient.ORIGIN + "/i123457"));
        String routedId = "iepeg4at789i?webpage=BjdXNwhsVTAGYFQ7UzABMgFoAjACIVFgBTAEMFwyWmoEMwFmDmAFaVN5BmE_c";
        JSONArray routed = new JSONArray().put(file("new-soul-1.45.0.zip", routedId, 0))
                .put(file("new-soul-1.44.1.zip", "i30jw4asp9li", 0));
        yes(index(routed).version.equals("1.45.0"));
        yes(index(routed).url.equals(LanzouUpdateClient.ORIGIN + "/" + routedId));
        for (String invalid : new String[]{"i123456?webpage=", "i123456?redirect=evil",
                "i123456?webpage=ok&url=evil", "i123456?webpage=//evil.example",
                "i123456?webpage=ok#fragment", "i123456/other", "//evil.example/i123456",
                "i123456?webpage=%0aevil", "i123456?webpage=" + "a".repeat(513)}) {
            fail(() -> index(new JSONArray().put(file("new-soul-2.0.0.zip", invalid, 0))));
        }
        fail(() -> index(new JSONArray()));
        fail(() -> index(new JSONArray().put(file("new-soul-2.0.0.zip", "https://evil.example/x", 0))));
        fail(() -> index(new JSONArray().put(file("new-soul-2.0.0.zip", "../evil", 0))));
        fail(() -> index(new JSONArray().put(file("new-soul-2.0.0.zip", "i123456", 2))));
        fail(() -> LanzouUpdateClient.check((u,b) -> b == null ? "captcha" : "{}"));
        fail(() -> LanzouUpdateClient.check((u,b) -> b == null ? HTML : "{\"zt\":3}"));
        fail(() -> LanzouUpdateClient.check((u,b) -> b == null ? HTML : "{\"zt\":2}"));
        fail(() -> LanzouUpdateClient.check((u,b) -> b == null ? HTML : "{\"zt\":1}"));
        JSONArray full = new JSONArray();
        for(int i=0;i<50;i++) full.put(file("notes-"+i, "i123456", 0));
        full.put(0,file("new-soul-1.0.0.zip", "i123456", 0));
        int[] calls = {0};
        LanzouUpdateClient.Release later = LanzouUpdateClient.check((u,b) -> {
            if(b == null) return HTML;
            yes(b.contains("t=123") && b.contains("k=abc") && b.contains("pwd=3gin"));
            return ++calls[0] == 1 ? page(full) : page(new JSONArray().put(file("new-soul-2.0.0.zip", "i123459", 0)));
        });
        yes(later.version.equals("2.0.0") && calls[0] == 2);
        fail(() -> LanzouUpdateClient.check((u,b) -> b == null ? HTML : page(full)));
        int[] limit = {0};
        fail(() -> LanzouUpdateClient.check((u,b) -> {
            if(b == null) return HTML;
            full.put(1,file("unique-" + ++limit[0], "i123456",0)); return page(full);
        }));
        yes(limit[0] == 80); // Each domain starts a fresh, bounded pagination pass.
        int[] ending = {0};
        yes(LanzouUpdateClient.check((u,b) -> b == null ? HTML : ++ending[0] == 1 ? page(full) : "{\"zt\":2}").version.equals("1.0.0"));
        fail(() -> LanzouUpdateClient.check((u,b) -> b == null ? HTML.replace("'t':tok", "'t':getToken()") : page(list)));
        fail(() -> LanzouUpdateClient.check((u,b) -> b == null ? HTML.replace("file=1", "file=2") : page(list)));
        String[] hosts = {"https://wwbnj.lanzoux.com", "https://wwbnj.lanzoul.com",
                "https://wwbnj.lanzoui.com", "https://wwbnj.lanzoux.com"};
        // Each fallback can succeed; no later domain should be contacted after success.
        for (int success = 0; success < hosts.length; success++) {
            final int target = success;
            java.util.List<String> visited = new java.util.ArrayList<>();
            LanzouUpdateClient.Release result = LanzouUpdateClient.check((u,b) -> {
                if (b == null) {
                    visited.add(u);
                    if (visited.size() - 1 < target) throw new java.net.SocketTimeoutException();
                    return HTML;
                }
                yes(u.equals(hosts[target] + "/filemoreajax.php?file=1"));
                yes(b.contains("pwd=3gin") && b.endsWith("pg=1"));
                return page(list);
            });
            yes(visited.size() == target + 1);
            for (int j = 0; j <= target; j++) yes(visited.get(j).equals(hosts[j] + LanzouUpdateClient.FOLDER_PATH));
            yes(result.url.equals(hosts[target] + "/i123457"));
        }
        int[] unavailable = {0};
        fail(() -> LanzouUpdateClient.check((u,b) -> {
            yes(u.equals(hosts[unavailable[0]++] + LanzouUpdateClient.FOLDER_PATH));
            throw new java.io.IOException("unreachable");
        }));
        yes(unavailable[0] == 4);
        // A later-page failure must discard the partial result and restart on the next host.
        int[] attempts = {0};
        LanzouUpdateClient.Release recovered = LanzouUpdateClient.check((u,b) -> {
            if (b == null) { attempts[0]++; return attempts[0] == 1 ? "captcha" : HTML; }
            if (attempts[0] == 2) {
                if (b.endsWith("pg=1")) return page(full);
                throw new java.io.IOException("page unavailable");
            }
            yes(u.startsWith(hosts[2]) && b.endsWith("pg=1"));
            return page(list);
        });
        yes(attempts[0] == 3 && recovered.version.equals("1.10.0"));
        int[] interrupted = {0};
        try {
            fail(() -> LanzouUpdateClient.check((u,b) -> {
                interrupted[0]++;
                throw new InterruptedException();
            }));
            yes(interrupted[0] == 1 && Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        Thread.currentThread().interrupt();
        try {
            fail(() -> LanzouUpdateClient.check((u,b) -> {
                throw new AssertionError("Cancelled check contacted network");
            }));
        } finally { Thread.interrupted(); }
        System.out.println("LanzouUpdateClient: " + checks + " checks passed");
    }
}
