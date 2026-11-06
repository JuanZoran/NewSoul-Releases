package dev.local.newsoul;
public class FeedContentRulesTest {
    public static void main(String[] args) {
        int checks = 0;
        for (int mask = 1; mask <= 7; mask++) {
            for (int bit : new int[]{1,2,4}) {
                int next = FeedContentRules.toggle(mask, bit);
                if (next < 1 || next > 7 || (mask == bit ? next != mask : next != (mask ^ bit))) throw new AssertionError("toggle");
                checks++;
            }
            for (String type : new String[]{"TEXT","IMAGE","VIDEO","IMG_VDO_MIX","AUDIO",null,"NEW_TYPE"}) {
                int bit = "TEXT".equals(type) ? 1 : "IMAGE".equals(type) ? 2 : "VIDEO".equals(type) || "IMG_VDO_MIX".equals(type) ? 4 : 0;
                if (FeedContentRules.allows(mask,type) != (bit==0 || (mask&bit)!=0)) throw new AssertionError(type);
                checks++;
            }
        }
        for (int bad : new int[]{0,-1,8,Integer.MAX_VALUE})
            if (FeedContentRules.normalize(bad)!=7) throw new AssertionError("invalid mask");
        System.out.println("Content rules: " + checks + " cases passed");
    }
}
