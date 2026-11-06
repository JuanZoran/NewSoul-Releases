package dev.local.newsoul;

import android.app.Activity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Frozen ordinary-chat identity for the delay between opening a picker and sending. */
final class GameSendGuard {
    private final Activity activity;
    private final Object fragment, sender, conversation, user;
    private final Field senderFragment;
    private final Method account, currentFragment;
    private final HostBindings.Chat chat;
    private final String own, peer, session, target;

    GameSendGuard(Activity activity, Object fragment, Object sender, Field senderFragment) throws Exception {
        this.activity = activity; this.fragment = fragment; this.sender = sender;
        this.senderFragment = senderFragment;
        chat = new HostBindings.Chat(activity.getClassLoader());
        HostBindings.require(fragment, chat.fragmentType);
        HostBindings.require(sender, chat.senderType);
        account = HostBindings.account(activity.getClassLoader());
        currentFragment = HostBindings.currentFragment(activity.getClassLoader());
        own = required(account.invoke(null));
        peer = required(chat.peer.get(fragment));
        user = chat.user.get(fragment);
        target = required(chat.encryptedId.get(user));
        conversation = chat.current(fragment);
        session = required(chat.session.invoke(conversation));
        validate();
    }

    void validate() throws Exception {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()
                || chat.activity.invoke(fragment) != activity
                || currentFragment.invoke(activity) != fragment || !Boolean.TRUE.equals(chat.resumed.invoke(fragment))
                || senderFragment.get(sender) != fragment || chat.sender.get(fragment) != sender
                || !own.equals(account.invoke(null)) || own.equals(peer)
                || !peer.equals(chat.peer.get(fragment)) || chat.current(fragment) != conversation
                || ((Number) chat.chatType.invoke(conversation)).intValue() != 0
                || !session.equals(chat.session.invoke(conversation))
                || !peer.equals(chat.recipient.invoke(conversation))
                || chat.user.get(fragment) != user || !target.equals(chat.encryptedId.get(user))) {
            throw new IllegalStateException("Chat identity changed");
        }
    }

    private static String required(Object value) {
        if (!(value instanceof String) || ((String) value).isEmpty()) throw new IllegalStateException("Identity unavailable");
        return (String) value;
    }
}
