package dev.local.newsoul;

/** Host Media names, independent of ordinal values and translated labels. */
final class FeedContentRules {
    static final int TEXT = 1, IMAGE = 2, VIDEO = 4, ALL = 7;
    static int normalize(int mask) { return mask > 0 && mask <= ALL ? mask : ALL; }
    static int toggle(int mask, int bit) {
        int next = normalize(mask) ^ bit;
        return next == 0 ? normalize(mask) : next;
    }
    static boolean allows(int mask, String type) {
        int bit = "TEXT".equals(type) ? TEXT : "IMAGE".equals(type) ? IMAGE
                : "VIDEO".equals(type) || "IMG_VDO_MIX".equals(type) ? VIDEO : 0;
        return bit == 0 || (normalize(mask) & bit) != 0;
    }
}
