package dev.local.newsoul;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** One request preparation path for explicit generation and optional automatic suggestions. */
final class AiReplyRequest {
    static final class Output {
        final JSONObject context, answer;
        final AiReplyRules.Result result;
        final String delivery;

        Output(JSONObject context, JSONObject answer, AiReplyRules.Result result, String delivery) {
            this.context=context; this.answer=answer; this.result=result; this.delivery=delivery;
        }

        String packet() throws Exception {
            return new JSONObject().put("context",context).put("answer",answer)
                    .put("delivery",delivery).toString();
        }
    }

    static Output run(AiChatContext.Snapshot snapshot, String profile, String instruction,
            String draft, AiWritingRules.Mode mode, AiWritingRules.Style style,
            List<AiChatContext.Media> chosen, AiModelConfig model, String apiKey,
            AiReplyClient.Cancellation cancel) throws Exception {
        cancel.check();
        JSONObject data=new JSONObject(snapshot.context.toString());
        data.put("snapshot_id",snapshot.snapshotId);
        data.put("self_profile",new JSONObject().put("source_id","self_profile").put("text",profile));
        JSONArray imageRefs=new JSONArray(), unavailable=new JSONArray();
        List<AiReplyClient.ImageInput> images=new ArrayList<>();
        for(AiChatContext.Media media:chosen) {
            cancel.check();
            try {
                images.add(media.sticker?AiStickerImage.prepare(media.httpsUrl,cancel)
                        :AiReplyClient.ImageInput.remote(media.httpsUrl));
                imageRefs.put(new JSONObject().put("id",media.id).put("description",media.label)
                        .put("representation",media.sticker?"original_sticker_file":"image"));
            } catch(Exception error) {
                cancel.check();
                unavailable.put(new JSONObject().put("id",media.id)
                        .put("reason","图片读取失败或格式不支持，未提供画面"));
            }
        }
        String delivery="本轮实际提供 "+images.size()+" 张图片/表情画面"
                +(unavailable.length()>0?"；"+unavailable.length()+" 张读取失败，已标注":"");
        data.put("current_intent",instruction).put("current_draft",draft)
                .put("selected_images",imageRefs).put("unavailable_images",unavailable)
                .put("writing_mode",mode.name()).put("selected_style",style.label);
        AiTimingRules.enrich(data,System.currentTimeMillis());
        String system=AiWritingRules.systemRules(AiReplyConfig.systemPrompt(),mode,style);
        data=AiRequestContext.prepare(system,data);
        JSONObject payload=AiReplyClient.createImageRequest(model,system,data.toString(),images);
        cancel.check();
        AiReplyClient.ParsedResponse response=AiReplyClient.executeDetailed(model,apiKey,payload,cancel);
        android.util.Log.i("SoulHook102","AI request metrics: "+response.diagnostics());
        if(response.recoveredParameterSuffix)
            android.util.Log.i("SoulHook102","AI format recovery: mimo_parameter_suffix");
        AiReplyRules.Result result=AiReplyRules.validateForContext(response.value,snapshot.snapshotId,data);
        return new Output(data,response.value,result,delivery);
    }

    /** Caller must first revalidate identity, source/configuration fingerprint and local history. */
    static Output restore(String packet, String snapshotId) throws Exception {
        JSONObject saved=new JSONObject(packet);
        JSONObject data=saved.getJSONObject("context"), answer=saved.getJSONObject("answer");
        data.put("snapshot_id",snapshotId); answer.put("snapshot_id",snapshotId);
        AiReplyRules.Result result=AiReplyRules.validateForContext(answer,snapshotId,data);
        return new Output(data,answer,result,saved.getString("delivery"));
    }

    private AiReplyRequest() {}
}
