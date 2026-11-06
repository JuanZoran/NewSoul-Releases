package android.content.res;
public class Resources {public android.util.DisplayMetrics getDisplayMetrics(){android.util.DisplayMetrics d=new android.util.DisplayMetrics();d.density=1;return d;}public int getIdentifier(String n,String t,String p){return switch(n){case "pager_square"->1;case "key_item_post"->2;case "iv_like"->3;default->4;};}}
