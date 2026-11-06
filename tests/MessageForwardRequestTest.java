package dev.local.newsoul;
import java.util.*;
public class MessageForwardRequestTest {
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static MessageForwardRequest request(){return new MessageForwardRequest("me","source",1,"snapshot",100);}
 public static void main(String[] args)throws Exception {
  MessageForwardRequest r=request();check(r.live("me",100));check(!r.live("other",100));check(!r.live("me",99));
  check(!r.live("me",101+MessageForwardRequest.MAX_AGE_MS));
  for(String target:new String[]{null,"","me","source"})check(!r.targetAllowed(target,0));
  check(r.targetAllowed("a",0));check(!r.targetAllowed("group",1));check(!r.targetAllowed("anon",2));
  for(List<String> targets:Arrays.asList(List.<String>of(),List.of("a","a"),List.of("a","source"),List.of("a","me"))) {
   check(r.claim("me",targets,100)==null);check(r.live("me",100));
  }
  check(r.claim("other",List.of("a"),100)==null);check(r.claim("me",null,100)==null);
  check(r.claim("me",List.of("a"),99)==null);
  List<String> targets=new ArrayList<>(List.of("a","b","c"));List<String> claimed=r.claim("me",targets,100);
  targets.clear();check(claimed.equals(List.of("a","b","c")));check(!r.live("me",100));
  check(r.claim("me",List.of("d"),100)==null);
  List<String> nine=new ArrayList<>();for(int i=0;i<9;i++)nine.add("p"+i);
  check(request().claim("me",nine,100).size()==9);nine.add("p9");check(request().claim("me",nine,100)==null);
  check(request().claim("me",List.of("a"),101+MessageForwardRequest.MAX_AGE_MS)==null);
  for(int type:new int[]{1,2,3,4,5})check(new MessageForwardRequest("me","source",type,"snapshot",100).live("me",100));
  try{new MessageForwardRequest("me","source",8,"snapshot",100);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
  MessageForwardRequest race=request();List<List<String>> results=Collections.synchronizedList(new ArrayList<>());
  Thread a=new Thread(()->results.add(race.claim("me",List.of("a"),100))),b=new Thread(()->results.add(race.claim("me",List.of("b"),100)));
  a.start();b.start();a.join();b.join();check(results.stream().filter(Objects::nonNull).count()==1);
  System.out.println("MessageForwardRequest: "+checks+" checks passed");
 }
}
