package dev.local.newsoul;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** One explicit confirmation for a frozen payload and a validated set of private recipients. */
final class MessageForwardRequest {
    static final long MAX_AGE_MS = 10 * 60 * 1000L;
    static final int MAX_TARGETS = 9;
    static final int POST = 32;
    final String account, source;
    final int type;
    final Object body;
    final long created;
    private boolean consumed;

    MessageForwardRequest(String account, String source, int type, Object body, long created) {
        if (account == null || account.isEmpty() || source == null || (source.isEmpty() && type != POST) || body == null
                || (type != 1 && type != 2 && type != 3 && type != 4 && type != 5 && type != POST)) throw new IllegalArgumentException("Invalid forward");
        if (type == POST && !(body instanceof PostSharePayload)) throw new IllegalArgumentException("Invalid post snapshot");
        this.account=account; this.source=source; this.type=type; this.body=body; this.created=created;
    }
    boolean live(String currentAccount, long now) {
        return !consumed && account.equals(currentAccount) && now >= created && now-created <= MAX_AGE_MS;
    }
    boolean targetAllowed(String target,int chatType) {
        return chatType==0 && target!=null && !target.isEmpty() && !account.equals(target) && !source.equals(target);
    }
    synchronized List<String> claim(String currentAccount,List<String> targets,long now) {
        if (!live(currentAccount,now) || targets==null || targets.isEmpty() || targets.size()>MAX_TARGETS) return null;
        LinkedHashSet<String> unique=new LinkedHashSet<>();
        for(String target:targets) if(!targetAllowed(target,0) || !unique.add(target)) return null;
        consumed=true;
        return new ArrayList<>(unique);
    }
}
