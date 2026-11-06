package dev.local.newsoul;

final class SquareIpLocationRules {
    static String label(String raw) {
        if (raw == null) return "";
        String region = raw.trim();
        // Some responses already include the public label prefix.
        if (region.startsWith("IP属地")) region = region.substring(4).trim();
        if (region.startsWith(":") || region.startsWith("：")) region = region.substring(1).trim();
        if (region.isEmpty()) return "";
        return "IP属地：" + region;
    }
    static boolean fits(int available, int used, int needed) {
        return available > 0 && used >= 0 && needed > 0 && (long) used + needed <= available;
    }
    private SquareIpLocationRules() {}
}
