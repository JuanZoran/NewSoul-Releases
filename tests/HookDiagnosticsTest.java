package dev.local.newsoul;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Executes production diagnostic classification and edition-specific AI status. */
public final class HookDiagnosticsTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static HookDiagnostics.Result result(List<String> lines, int missing) {
        return new HookDiagnostics.Result("4.88.0 (20217)", null, lines, missing);
    }

    private static List<String> aiStatus(boolean editionHasAi, boolean ready) throws Exception {
        VariantFeatures.ai = editionHasAi;
        VariantFeatures.ready = ready;
        VariantFeatures.readinessChecks = 0;
        List<String> lines = new ArrayList<>();
        Method probe = HookDiagnostics.class.getDeclaredMethod("probeAiEntry", List.class);
        probe.setAccessible(true);
        probe.invoke(null, lines);
        return lines;
    }

    public static void main(String[] args) throws Exception {
        for (String fieldFailure : List.of("内容类型筛选 · 字段未就绪", "VIP 帖子筛选 · 字段未就绪",
                "广场 IP 属地显示 · 字段或绑定入口未就绪")) {
            HookDiagnostics.Result unavailable = result(List.of(fieldFailure), 0);
            check(unavailable.unready == 1, "field failure must count as unready: " + fieldFailure);
            check(unavailable.summary().contains("部分检测项需要检查（1 项）"), "field failure must affect summary");
            check(unavailable.entries(true).equals(fieldFailure), "field failure must appear in issues");
            check(unavailable.entries(false).isEmpty(), "field failure must not appear as normal");
            check(unavailable.report().contains("需要检查\n" + fieldFailure), "report must include field issue");
        }

        HookDiagnostics.Result mixed = result(List.of("复读：Hook 点缺失（NoSuchMethodException）\n  private detail",
                "定位 · Hook 未就绪", "内容类型筛选 · 字段未就绪", "VIP 帖子筛选 · 字段未就绪",
                "广场 IP 属地显示 · 字段或绑定入口未就绪", "闪照：当前宿主无此功能",
                "广告：当前宿主无此广告场景", "字段已识别，待实测"), 1);
        check(mixed.missing == 1, "missing count must remain separate");
        check(mixed.unready == 4, "only present but unready hooks and fields count");
        check(mixed.summary().contains("部分检测项需要检查（5 项）"), "summary must combine missing and unready");
        check(!mixed.entries(true).contains("private detail"), "entries must keep implementation details hidden");
        check(!mixed.entries(true).contains("当前宿主无此"), "absent scenes must not become issues");
        check(mixed.entries(false).contains("字段已识别，待实测"), "valid fields must remain normal");

        List<String> publicLines = aiStatus(false, false);
        check(publicLines.isEmpty(), "public edition must omit AI status");
        check(VariantFeatures.readinessChecks == 0, "public edition must not initialize AI state");

        List<String> readyLines = aiStatus(true, true);
        check(VariantFeatures.readinessChecks == 1, "AI diagnostic must query dynamic readiness");
        check(readyLines.size() == 1 && readyLines.get(0).equals("AI 原生入口：已注册 · 待功能实测"),
                "dynamic readiness must pass without an obfuscated listener class");
        HookDiagnostics.Result readyAi = result(readyLines, 0);
        check(readyAi.unready == 0 && readyAi.summary().contains("关键检测项适配通过"), "ready AI must not be falsely missing");

        List<String> unreadyLines = aiStatus(true, false);
        check(VariantFeatures.readinessChecks == 1, "unready AI must also use dynamic readiness");
        check(unreadyLines.size() == 1 && unreadyLines.get(0).equals("AI 原生入口：Hook 未就绪"), "unready AI must remain actionable");
        HookDiagnostics.Result unreadyAi = result(unreadyLines, 0);
        check(unreadyAi.missing == 0 && unreadyAi.unready == 1, "dynamic unready state must not claim a missing host class");
        check(unreadyAi.entries(true).contains("AI 原生入口"), "unready AI must appear in issues");
        System.out.println("HookDiagnostics production checks: " + checks);
    }
}
