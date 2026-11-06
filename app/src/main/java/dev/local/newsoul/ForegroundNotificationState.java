package dev.local.newsoul;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Visible activity transitions, excluding navigation and configuration recreation. */
final class ForegroundNotificationState {
    private final Set<Object> started = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean changingConfiguration;

    synchronized boolean start(Object activity) {
        boolean entering = started.isEmpty() && !changingConfiguration;
        if (!started.add(activity)) return false;
        changingConfiguration = false;
        return entering;
    }

    synchronized void stop(Object activity, boolean changing) {
        if (!started.remove(activity)) return;
        if (started.isEmpty()) changingConfiguration = changing;
    }
}
