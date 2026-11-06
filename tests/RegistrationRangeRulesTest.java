package dev.local.newsoul;
public final class RegistrationRangeRulesTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        int cap=RegistrationRangeRules.ceiling(30,365);
        check(RegistrationRangeRules.value(0,cap,false)==1);
        check(RegistrationRangeRules.value(1,cap,true)==0);
        check(RegistrationRangeRules.value(0,cap,true)==1);
        check(RegistrationRangeRules.position(0,cap,true)==1);
        for(int day:new int[]{1,30,365,1000,3650})
            check(RegistrationRangeRules.value(RegistrationRangeRules.position(day,cap,true),cap,true)==day);
        check(RegistrationRangeRules.ceiling(8000,9000)==9000);
        check(RegistrationRangeRules.value(1,Integer.MAX_VALUE,false)==Integer.MAX_VALUE);
        check(RegistrationRangeRules.value(-1,cap,false)==1);
        check(RegistrationRangeRules.position(0,3650,false)==0);
        check("1 天".equals(RegistrationRangeRules.label(0,false)));
        check("+∞ 天".equals(RegistrationRangeRules.label(0,true)));
        check(RegistrationRangeRules.parse("1 天",false)==1);
        check(RegistrationRangeRules.parse("+∞ 天",true)==0);
        check(RegistrationRangeRules.parse("365 天",true)==365);
        check(RegistrationRangeRules.parse("",true)==0);
        try { RegistrationRangeRules.parse("-1",false); throw new AssertionError(); }
        catch(NumberFormatException expected) {}
        for (String invalid : new String[]{"0", "-1", "0.5", "2147483648"}) {
            for (boolean upper : new boolean[]{false,true}) {
                try { RegistrationRangeRules.parse(invalid,upper); throw new AssertionError(); }
                catch(NumberFormatException expected) {}
            }
        }
        try { RegistrationRangeRules.parse("",false); throw new AssertionError(); }
        catch(NumberFormatException expected) {}
        check(!AccountAutomationRules.validRange(365,30));
        check(AccountAutomationRules.validRange(30,30));
        check(AccountAutomationRules.validRange(30,0));
        check(RegistrationRangeRules.position(30,3650,false)>.35f);
        check(RegistrationRangeRules.position(365,3650,false)>.65f);
        check(RegistrationRangeRules.value(.5f,3650,false)<100);
        int previous=0;
        for(int i=0;i<=960;i++) {
            int current=RegistrationRangeRules.value(i/1000f,3650,false);
            check(current>=previous); previous=current;
        }
        System.out.println("RegistrationRangeRules: boundary and nonlinear monotonicity checks passed");
    }
}
