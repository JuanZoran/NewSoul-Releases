package dev.local.newsoul;

/** Soul 6.35.0: verified against AbsChatDualItem.r0 and Conversation.markMsgAllRead. */
final class AiMessageStatus {
    static boolean sent(long status) { return status == 3 || status == 4; }
    static boolean failed(long status) { return status == 5; }
    // A sent -> read receipt does not change the conversation content.
    static long contentState(long status) { return status == 3 ? 4 : status; }
}
