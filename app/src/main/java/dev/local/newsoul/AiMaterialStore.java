package dev.local.newsoul;

import android.content.Context;
import android.content.SharedPreferences;

/** Separate local namespace: never overwrites legacy profile or joins config exports. */
final class AiMaterialStore {
    private static SharedPreferences preferences(Context context) {
        return TransactionalPreferences.wrap(context.getSharedPreferences(
                "newsoul_ai_materials_v1", Context.MODE_PRIVATE));
    }
    static synchronized AiMaterialRules.Library read(Context context,AiMaterialRules.Kind kind) throws Exception {
        return AiMaterialRules.read(preferences(context).getString(kind.name(),""),kind);
    }
    static synchronized void save(Context context,AiMaterialRules.Kind kind,String id,long revision,String title,String tags,String text,boolean confirmed) throws Exception {
        write(context,AiMaterialRules.save(read(context,kind),id,revision,title,tags,text,confirmed));
    }
    static synchronized void delete(Context context,AiMaterialRules.Kind kind,String id,long revision) throws Exception {
        write(context,AiMaterialRules.delete(read(context,kind),id,revision));
    }
    private static void write(Context context,AiMaterialRules.Library library) throws Exception {
        if(!preferences(context).edit().putString(library.kind.name(),AiMaterialRules.encode(library)).commit())throw new IllegalStateException("保存失败，请重试");
    }
}
