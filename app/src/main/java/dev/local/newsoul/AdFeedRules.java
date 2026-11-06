package dev.local.newsoul;

import java.util.ArrayList;
import java.util.Collection;

/** Pure collection rules kept separate so feed filtering can be checked on a desktop JVM. */
final class AdFeedRules {
    static final class Result {
        final ArrayList<Object> items;
        final int removed;
        Result(ArrayList<Object> items,int removed) {this.items=items;this.removed=removed;}
    }

    static boolean isAd(Object item,Class<?>... adClasses) {
        if(item==null) return false;
        for(Class<?> type:adClasses) if(type!=null && type.isInstance(item)) return true;
        return false;
    }

    static Result filter(Collection<?> source,Class<?>... adClasses) {
        ArrayList<Object> result=new ArrayList<>(source.size());int removed=0;
        for(Object item:source) {
            if(isAd(item,adClasses)) removed++;
            else result.add(item);
        }
        return new Result(result,removed);
    }
}
