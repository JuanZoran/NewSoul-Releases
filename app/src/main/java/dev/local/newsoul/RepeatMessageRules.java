package dev.local.newsoul;

/** Eligibility shared by the menu and click path. */
final class RepeatMessageRules {
    static boolean ordinary(int envelope, int type, int snap, String snapFlag, boolean anonymous) {
        return envelope == 1 && !anonymous && snap == 0
                && (snapFlag == null || snapFlag.isEmpty() || "0".equals(snapFlag) || "false".equals(snapFlag))
                && (type == 1 || type == 2 || type == 3 || type == 5);
    }

    static boolean forwardable(int envelope, int type, int snap, String snapFlag, boolean anonymous) {
        return ordinary(envelope, type == 4 ? 2 : type, snap, snapFlag, anonymous);
    }

    static boolean sameChat(String account, String peer, String from, String to) {
        return account != null && !account.isEmpty() && peer != null && !peer.isEmpty()
                && ((account.equals(from) && peer.equals(to)) || (peer.equals(from) && account.equals(to)));
    }

    static boolean mediaUrl(String value) {
        if (value == null) return false;
        try {
            java.net.URI uri = new java.net.URI(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && !uri.getHost().isEmpty() && uri.getUserInfo() == null;
        } catch (java.net.URISyntaxException error) { return false; }
    }
}
