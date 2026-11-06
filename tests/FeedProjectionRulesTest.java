package dev.local.newsoul;
import android.content.Context;
public class FeedProjectionRulesTest {
 static int checks;static final Context ctx=new Context();
 static void check(boolean expected,String name){checks++;if(SquareFeedFilter.hasRules(ctx)!=expected)throw new AssertionError(name);}
 public static void main(String[] args){
  SquareFeedFilter.ready=true;SquareFeedFilter.vipReady=true;SquareFeedFilter.keywordsReady=true;
  check(false,"defaults are native");
  SettingsUi.values.put(SquareFeedFilter.CITY_PAGES,true);check(false,"city scope alone cannot own square");
  SettingsUi.values.put(SquareFeedFilter.ENABLED,true);check(true,"gender restriction");
  SettingsUi.values.put(SquareFeedFilter.GENDER,"ANY");check(false,"ANY disables gender projection");
  SettingsUi.values.put(SquareFeedFilter.VIP,true);check(true,"VIP independent of ANY");
  SquareFeedFilter.vipReady=false;check(false,"VIP capability unavailable");
  SettingsUi.values.put(SquareFeedFilter.KEYWORDS_ENABLED,true);check(false,"empty keywords are native");
  SettingsUi.values.put(SquareFeedFilter.KEYWORDS," \n\t");check(false,"whitespace keywords are native");
  SettingsUi.values.put(SquareFeedFilter.KEYWORDS,"word");check(true,"nonempty keywords");
  SquareFeedFilter.keywordsReady=false;check(false,"keyword capability unavailable");SquareFeedFilter.keywordsReady=true;
  SettingsUi.values.put(SquareFeedFilter.KEYWORDS,"x".repeat(101));check(false,"invalid keyword list cannot project");
  SettingsUi.values.put(SquareFeedFilter.KEYWORDS,"valid");check(true,"keyword cache accepts repaired list");
  SettingsUi.values.put(SquareFeedFilter.KEYWORDS_ENABLED,false);
  SettingsUi.values.put(RegistrationDaysFilter.ENABLED,true);check(true,"registration enabled includes pending rows");
  SettingsUi.values.put(RegistrationDaysFilter.MIN,10);SettingsUi.values.put(RegistrationDaysFilter.MAX,3);check(false,"invalid registration range is native");
  SettingsUi.values.put(RegistrationDaysFilter.MAX,0);check(true,"registration unbounded max still restricts min");
  SquareFeedFilter.ready=false;check(false,"unavailable filter cannot own projection");
  System.out.println("Projection rules: "+checks+" checks passed");
 }
}
