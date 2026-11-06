package dev.local.newsoul;
import java.util.*;
import org.json.*;

public final class AiMaterialRulesTest {
    static int checks;
    static void ok(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    interface Run{void run()throws Exception;}
    static void rejects(Run run)throws Exception{try{run.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException|JSONException expected){checks++;}}
    public static void main(String[] args)throws Exception {
        AiMaterialRules.Kind real=AiMaterialRules.Kind.REAL,idea=AiMaterialRules.Kind.INSPIRATION;
        AiMaterialRules.Library empty=AiMaterialRules.read("",real);
        ok(empty.entries.isEmpty(),"missing new key does not migrate or reinterpret legacy profile");
        rejects(()->AiMaterialRules.save(empty,null,0,"旅行","周末","去过海边",false));
        AiMaterialRules.Library saved=AiMaterialRules.save(empty,null,0,"义卖","校园","和室友办过义卖，剩下的书捐了",true);
        AiMaterialRules.Entry e=saved.entries.get(0);
        ok(empty.entries.isEmpty()&&saved.entries.size()==1,"editing is immutable until save");
        String encoded=AiMaterialRules.encode(saved);
        AiMaterialRules.Library loaded=AiMaterialRules.read(encoded,real);
        ok(loaded.find(e.id).text.equals(e.text)&&loaded.find(e.id).revision==1,"round-trip real data");
        rejects(()->AiMaterialRules.read(encoded,idea));
        rejects(()->AiMaterialRules.read("{",real));
        rejects(()->AiMaterialRules.read(new JSONObject(encoded).put("version",2).toString(),real));
        rejects(()->AiMaterialRules.read(entryField(encoded,"confirmed",false),real));
        AiMaterialRules.Selection selection=new AiMaterialRules.Selection();
        AiMaterialRules.Selection draft=selection.copy();draft.toggle(e);
        ok(selection.size()==0&&draft.size()==1,"cancel selection discards staged choice");
        selection.replace(draft);ok(selection.size()==1,"explicit selection commit");
        draft=selection.copy();draft.clear();ok(selection.size()==1&&draft.size()==0,"clear remains staged until confirmed");
        AiMaterialRules.Library updated=AiMaterialRules.save(saved,e.id,1,"义卖","校园","剩下的旧书捐给社区阅览室",true);
        rejects(()->AiMaterialRules.save(updated,e.id,1,"旧编辑","","不覆盖",true));
        rejects(()->selection.snapshot(updated));
        ok(selection.reconcile(updated)&&selection.size()==0,"edit invalidates selection and requires reselect");
        selection.toggle(updated.find(e.id));
        List<AiMaterialRules.Entry> snapshot=selection.snapshot(updated);
        AiMaterialRules.Library deleted=AiMaterialRules.delete(updated,e.id,2);
        ok(deleted.entries.isEmpty()&&snapshot.get(0).text.contains("阅览室"),"delete does not change captured request snapshot");
        rejects(()->AiMaterialRules.delete(deleted,e.id,2));
        rejects(()->selection.snapshot(deleted));selection.clear();
        AiMaterialRules.Library ideas=AiMaterialRules.save(AiMaterialRules.read("",idea),null,0,"故事灵感","虚构","想象一次旅行",false);
        AiMaterialRules.Entry fiction=ideas.entries.get(0);
        rejects(()->selection.toggle(fiction));
        rejects(()->AiMaterialRules.profileForRequest("旧资料",Collections.singletonList(fiction),AiWritingRules.Mode.REPLY));
        ok(!new JSONObject(AiMaterialRules.encode(ideas)).getJSONArray("entries").getJSONObject(0).getBoolean("confirmed"),"inspiration cannot be truth-confirmed by save");
        rejects(()->AiMaterialRules.read(entryField(encoded,"confirmed","true"),real));
        rejects(()->AiMaterialRules.read(entryField(encoded,"revision",1.5),real));
        AiMaterialRules.Library huge=empty;
        for(int i=0;i<50;i++)huge=AiMaterialRules.save(huge,null,0,"\"".repeat(40),"\"".repeat(120),"\"".repeat(1000),true);
        AiMaterialRules.Library oversized=huge;
        rejects(()->AiMaterialRules.encode(oversized));
        rejects(()->AiMaterialRules.save(oversized,null,0,"不能超数","","真实",true));
        String old="  我喜欢看电影\n保留原文  ";
        ok(AiMaterialRules.profileForRequest(old,Collections.emptyList(),AiWritingRules.Mode.REPLY).equals(old),"no selection preserves legacy profile byte-for-byte");
        ok(AiMaterialRules.profileForRequest(old,snapshot,AiWritingRules.Mode.REWRITE).equals(old),"rewrite receives no additional material");
        ok(AiMaterialRules.profileForRequest("字".repeat(8000),Collections.emptyList(),AiWritingRules.Mode.REPLY).length()==8000,"legacy maximum profile is not truncated");
        String assembled=AiMaterialRules.profileForRequest(old,snapshot,AiWritingRules.Mode.REPLY);
        ok(assembled.startsWith(old)&&assembled.contains(snapshot.get(0).text)&&!assembled.contains(fiction.text),"only explicitly selected real text is added");
        rejects(()->AiMaterialRules.profileForRequest(old,Arrays.asList(e,e),AiWritingRules.Mode.REPLY));
        rejects(()->AiMaterialRules.profileForRequest("长".repeat(12000),snapshot,AiWritingRules.Mode.REPLY));
        rejects(()->AiMaterialRules.save(empty,null,0,"","","内容",true));
        rejects(()->AiMaterialRules.save(empty,null,0,"名","","长".repeat(1001),true));
        AiMaterialRules.Library many=empty;
        for(int i=0;i<4;i++)many=AiMaterialRules.save(many,null,0,"事件"+i,"","真实描述",true);
        for(int i=0;i<3;i++)selection.toggle(many.entries.get(i));
        AiMaterialRules.Entry fourth=many.entries.get(3);rejects(()->selection.toggle(fourth));
        selection.toggle(many.entries.get(0));ok(selection.size()==2,"repeated tap deselects without duplicates");
        for(AiModelConfig model:new AiModelConfig[]{AiModelConfig.MIMO,AiModelConfig.DEEPSEEK}) {
            JSONObject original=new JSONObject().put("self_profile",new JSONObject().put("source_id","self_profile").put("text",old));
            JSONObject same=new JSONObject(original.toString());same.getJSONObject("self_profile").put("text",AiMaterialRules.profileForRequest(old,Collections.emptyList(),AiWritingRules.Mode.REPLY));
            String core=AiReplyPrompt.systemPrompt();
            ok(AiReplyClient.createRequest(model,core,original.toString(),Collections.emptyList()).similar(AiReplyClient.createRequest(model,core,same.toString(),Collections.emptyList())),"no selection preserves provider payload");
            JSONObject selected=new JSONObject(original.toString());selected.getJSONObject("self_profile").put("text",assembled);
            JSONObject request=AiReplyClient.createRequest(model,core,selected.toString(),Collections.emptyList());
            String provided=new JSONObject(request.getJSONArray("messages").getJSONObject(1).getString("content")).getJSONObject("self_profile").getString("text");
            ok(provided.equals(assembled)&&request.getJSONArray("messages").getJSONObject(0).getString("content").startsWith(core),"selected text survives production request; core untouched");
            JSONObject visual=AiReplyClient.createImageRequest(model,core,selected.toString(),Collections.singletonList(AiReplyClient.ImageInput.remote("https://example.com/image.jpg")));
            JSONObject visualContext=new JSONObject(visual.getJSONArray("messages").getJSONObject(1).getJSONArray("content").getJSONObject(0).getString("text"));
            ok(visualContext.getJSONObject("self_profile").getString("text").equals(assembled),"image request preserves selected real material");
        }
        System.out.println("AiMaterialRules: "+checks+" offline checks passed; no live material-specific generation or visual claim");
    }
    static String entryField(String encoded,String field,Object value) {
        JSONObject data=new JSONObject(encoded);
        data.getJSONArray("entries").getJSONObject(0).put(field,value);
        return data.toString();
    }
}
