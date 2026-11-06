package dev.local.newsoul;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Resolve only the profile explicitly bound to a user-settings menu, never a nearby page. */
final class ProfileBookmarkTargetRules {
    static Object uniqueValue(Object source,Class<?> type) throws IllegalAccessException {
        Field match=null;
        for (Field field:source.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType()!=type) continue;
            if (match!=null) return null;
            match=field;
        }
        if (match==null) return null;
        match.setAccessible(true);
        return match.get(source);
    }

    static Object boundOwner(Object callback,Object dialog,Class<?> dialogType,Class<?> pageType)
            throws IllegalAccessException {
        if (uniqueValue(callback,dialogType)!=dialog) return null;
        return uniqueValue(callback,pageType);
    }

    static boolean sameProfile(String pageId,String userId,String postAuthor) {
        return pageId!=null && !pageId.trim().isEmpty() && pageId.equals(userId)
                && (postAuthor==null || pageId.equals(postAuthor));
    }
}
