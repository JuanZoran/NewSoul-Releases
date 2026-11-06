package dev.local.newsoul;

public final class ProfileBookmarkTargetRulesTest {
    static class Dialog {}
    static final class ShareDialog extends Dialog {}
    static final class User {}
    static final class Page { private User renamedUser=new User(); }
    static final class Callback { private Dialog renamedDialog; private Page renamedPage;
        Callback(Dialog d,Page p) { renamedDialog=d;renamedPage=p; } }
    static final class Ambiguous { private Page a=new Page(), b=new Page(); }
    static final class Wrong { private Dialog d; private String text="ignored"; Wrong(Dialog d){this.d=d;} }
    static void check(boolean ok) { if(!ok) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        Dialog menu=new Dialog();Page page=new Page();Callback callback=new Callback(menu,page);
        check(ProfileBookmarkTargetRules.boundOwner(callback,menu,Dialog.class,Page.class)==page);
        check(ProfileBookmarkTargetRules.boundOwner(callback,new Dialog(),Dialog.class,Page.class)==null);
        check(ProfileBookmarkTargetRules.boundOwner(new Wrong(menu),menu,Dialog.class,Page.class)==null);
        check(ProfileBookmarkTargetRules.uniqueValue(new Ambiguous(),Page.class)==null);
        check(ProfileBookmarkTargetRules.uniqueValue(page,User.class)==page.renamedUser);
        check(ProfileBookmarkTargetRules.sameProfile("a","a","a"));
        check(!ProfileBookmarkTargetRules.sameProfile("a","b","a"));
        check(!ProfileBookmarkTargetRules.sameProfile("a","a","b"));
        check(ProfileBookmarkTargetRules.sameProfile("a","a",null));
        check(!ProfileBookmarkTargetRules.sameProfile("a","b",null));
        check(!ProfileBookmarkTargetRules.sameProfile("a","a",""));
        check(!ProfileBookmarkTargetRules.sameProfile(null,"a","a"));
        check(!ProfileBookmarkTargetRules.sameProfile(" "," "," "));
        ShareDialog legacyMenu=new ShareDialog();Callback legacyCallback=new Callback(legacyMenu,page);
        check(ProfileBookmarkTargetRules.boundOwner(legacyCallback,legacyMenu,Dialog.class,Page.class)==page);
        check(ProfileBookmarkTargetRules.boundOwner(legacyCallback,legacyMenu,ShareDialog.class,Page.class)==null);
        check(ProfileBookmarkTargetRules.boundOwner(legacyCallback,new ShareDialog(),Dialog.class,Page.class)==null);
        System.out.println("ProfileBookmarkTargetRules: 16 checks passed");
    }
}
