package dev.local.newsoul;
public final class AiMessageStatusTest {
    public static void main(String[] args) {
        int checks=0;
        for(int status : new int[]{0,1,2,3,4,5,99}) {
            if(AiMessageStatus.sent(status)!=(status==3||status==4))throw new AssertionError("sent "+status);
            if(AiMessageStatus.failed(status)!=(status==5))throw new AssertionError("failed "+status);
            if(AiMessageStatus.contentState(status)!=(status==3?4:status))throw new AssertionError("guard "+status);
            checks+=3;
        }
        System.out.println("AiMessageStatusTest: "+checks+" checks passed");
    }
}
