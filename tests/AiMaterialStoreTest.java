package dev.local.newsoul;

import android.content.Context;
import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

/** Uses Android's commitToMemory-before-disk-result ordering with the real store and wrapper. */
public final class AiMaterialStoreTest {
    static int checks;
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    interface Operation { void run() throws Exception; }
    static void rejects(Operation operation) throws Exception {
        try { operation.run(); throw new AssertionError("Unexpected successful save"); }
        catch (IllegalStateException expected) { checks++; }
    }
    static final class Disk {
        final Map<String, Object> memory = new LinkedHashMap<>(), durable = new LinkedHashMap<>();
        boolean succeeds = true, restoreThrows;
        final SharedPreferences preferences = (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class}, (self, method, args) -> {
            if (method.getName().equals("getAll")) return new LinkedHashMap<>(memory);
            if (method.getName().equals("edit")) return editor();
            throw new AssertionError(method.getName());
        });
        Object editor() {
            Map<String, Object> changes = new LinkedHashMap<>();
            boolean[] clear = {false};
            return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
                    new Class<?>[]{SharedPreferences.Editor.class}, (self, method, args) -> {
                String name = method.getName();
                if (name.equals("clear")) { clear[0] = true; return self; }
                if (name.startsWith("put")) { changes.put((String) args[0], args[1]); return self; }
                if (name.equals("apply") || name.equals("commit")) {
                    if (name.equals("apply") && restoreThrows) throw new IllegalStateException("rollback failed");
                    if (clear[0]) memory.clear();
                    memory.putAll(changes);
                    // Failed commit is deliberately observable in raw memory before its result.
                    if (succeeds) { durable.clear(); durable.putAll(memory); }
                    return name.equals("apply") ? null : succeeds;
                }
                throw new AssertionError(name);
            });
        }
    }
    static final class App extends Context {
        final Disk disk = new Disk();
        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            check(name.equals("newsoul_ai_materials_v1") && mode == MODE_PRIVATE, "isolated namespace unchanged");
            return disk.preferences;
        }
    }
    static AiMaterialRules.Library read(App app) throws Exception {
        return AiMaterialStore.read(app, AiMaterialRules.Kind.REAL);
    }
    static void save(App app, String id, long revision, String title) throws Exception {
        AiMaterialStore.save(app, AiMaterialRules.Kind.REAL, id, revision, title, "", "真实经历", true);
    }
    public static void main(String[] args) throws Exception {
        App app = new App();
        app.disk.memory.put("unrelated", "keep");
        app.disk.succeeds = false;
        rejects(() -> save(app, null, 0, "失败的新增"));
        check(read(app).entries.isEmpty(), "failed insertion never becomes selectable");
        check(!app.disk.memory.containsKey("REAL"), "raw in-memory failed value restored");
        app.disk.succeeds = true;
        save(app, null, 0, "原素材");
        AiMaterialRules.Entry original = read(app).entries.get(0);
        AiMaterialRules.Selection selected = new AiMaterialRules.Selection();
        selected.toggle(original);
        app.disk.succeeds = false;
        rejects(() -> save(app, original.id, original.revision, "失败的修改"));
        check(read(app).find(original.id).revision == original.revision, "failed save preserves revision");
        check(!selected.reconcile(read(app)), "selected original remains valid after failed save");
        rejects(() -> AiMaterialStore.delete(app, AiMaterialRules.Kind.REAL, original.id, original.revision));
        check(read(app).find(original.id) != null, "failed deletion remains visible");
        app.disk.restoreThrows = true;
        rejects(() -> save(app, original.id, original.revision, "回滚也失败"));
        check(read(app).find(original.id).title.equals("原素材"), "accepted snapshot survives failed rollback");
        app.disk.restoreThrows = false; app.disk.succeeds = true;
        AiMaterialStore.save(app, AiMaterialRules.Kind.INSPIRATION, null, 0, "灵感", "", "草稿", false);
        check(read(app).find(original.id).title.equals("原素材"), "other kind save cannot publish rejected edit");
        check(AiMaterialRules.read((String) app.disk.durable.get("REAL"), AiMaterialRules.Kind.REAL)
                .find(original.id).title.equals("原素材"), "later successful disk write excludes rejected values");
        check(app.disk.durable.get("unrelated").equals("keep"), "unrelated namespace keys preserved");
        AiMaterialStore.delete(app, AiMaterialRules.Kind.REAL, original.id, original.revision);
        check(read(app).entries.isEmpty(), "retry uses the original revision and deletes once");
        check(AiMaterialStore.read(app, AiMaterialRules.Kind.INSPIRATION).entries.size() == 1,
                "real material changes preserve separate inspiration library");
        System.out.println("AiMaterialStore: " + checks + " checks passed");
    }
}
