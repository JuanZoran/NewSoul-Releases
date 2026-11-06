package dev.local.newsoul;
import android.content.Context;
import java.util.*;
final class SquareFeedFilter {
 static final Set<Object> blocked=Collections.newSetFromMap(new IdentityHashMap<>());
 static boolean hasRules(Context c){return !blocked.isEmpty() || fieldsEnabled;}
 static boolean fieldsEnabled=true;
 static boolean allows(Context c,Object item){return !blocked.contains(item) && (!(item instanceof SquareTimeOrderTest.Post) || !((SquareTimeOrderTest.Post)item).filtered);}
}
