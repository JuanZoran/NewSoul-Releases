package dev.local.newsoul;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AdFeedRulesTest {
    static final class Normal {final int id;Normal(int id){this.id=id;}}
    static final class PostAd {}
    static final class ExpressAd {}
    static int checks;
    static void check(boolean value) {checks++;if(!value) throw new AssertionError("check "+checks);}

    public static void main(String[] args) {
        Normal first=new Normal(1),last=new Normal(2);PostAd post=new PostAd();ExpressAd express=new ExpressAd();
        List<Object> source=new ArrayList<>(Arrays.asList(first,post,null,express,last));
        AdFeedRules.Result result=AdFeedRules.filter(source,PostAd.class,ExpressAd.class);
        check(result.removed==2);
        check(result.items.size()==3);
        check(result.items.get(0)==first);
        check(result.items.get(1)==null);
        check(result.items.get(2)==last);
        check(source.size()==5);
        check(AdFeedRules.isAd(post,PostAd.class,ExpressAd.class));
        check(!AdFeedRules.isAd(first,PostAd.class,ExpressAd.class));
        check(!AdFeedRules.isAd(null,PostAd.class,ExpressAd.class));
        System.out.println(checks+" ad feed rule checks passed");
    }
}
