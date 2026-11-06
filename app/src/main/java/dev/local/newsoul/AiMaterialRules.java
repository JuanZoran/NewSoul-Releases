package dev.local.newsoul;

import org.json.*;
import java.util.*;

/** Local, typed material storage. Inspiration never participates in request assembly. */
final class AiMaterialRules {
    enum Kind { REAL, INSPIRATION }
    static final int MAX_ENTRIES=50, MAX_SELECTED=3;
    static final class Entry {
        final String id,title,tags,text; final long revision; final Kind kind;
        private Entry(Kind kind,String id,long revision,String title,String tags,String text) {
            this.kind=kind;this.id=id;this.revision=revision;this.title=title;this.tags=tags;this.text=text;
        }
    }
    static final class Library {
        final Kind kind; final List<Entry> entries;
        private Library(Kind kind,List<Entry> entries){this.kind=kind;this.entries=Collections.unmodifiableList(new ArrayList<>(entries));}
        Entry find(String id){for(Entry e:entries)if(e.id.equals(id))return e;return null;}
    }
    static Library read(String raw,Kind expected) throws JSONException {
        if(raw==null||raw.isEmpty())return new Library(expected,Collections.emptyList());
        if(raw.length()>100000)throw new IllegalArgumentException("素材数据过大，未修改原数据");
        JSONObject root=new JSONObject(raw);
        if(!(root.get("version") instanceof Integer) || root.getInt("version")!=1 || !expected.name().equals(root.getString("kind")))throw new IllegalArgumentException("素材格式不兼容，未修改原数据");
        JSONArray rows=root.getJSONArray("entries");if(rows.length()>MAX_ENTRIES)throw new IllegalArgumentException("素材数量过多");
        List<Entry> entries=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.getJSONObject(i);String id=row.getString("id");
            Object rawRevision=row.get("revision");
            if(!(rawRevision instanceof Integer)&&!(rawRevision instanceof Long))throw new IllegalArgumentException("素材修订异常");
            long revision=row.getLong("revision");
            if(!id.matches("[a-zA-Z0-9-]{1,64}")||!ids.add(id)||revision<1||revision==Long.MAX_VALUE)throw new IllegalArgumentException("素材记录异常，未修改原数据");
            if(!(row.get("confirmed") instanceof Boolean) || row.getBoolean("confirmed")!=(expected==Kind.REAL))throw new IllegalArgumentException("素材真实性状态异常，未修改原数据");
            entries.add(new Entry(expected,id,revision,clean(row.getString("title"),40,true),clean(row.getString("tags"),120,false),clean(row.getString("text"),1000,true)));
        }
        return new Library(expected,entries);
    }
    static String encode(Library library) throws JSONException {
        JSONArray entries=new JSONArray();
        for(Entry e:library.entries)entries.put(new JSONObject().put("id",e.id).put("revision",e.revision).put("title",e.title).put("tags",e.tags).put("text",e.text).put("confirmed",library.kind==Kind.REAL));
        String result=new JSONObject().put("version",1).put("kind",library.kind.name()).put("entries",entries).toString();
        if(result.length()>100000)throw new IllegalArgumentException("素材数据过大，请减少内容");
        return result;
    }
    static Library save(Library library,String id,long expectedRevision,String title,String tags,String text,boolean confirmed) {
        if(library.kind==Kind.REAL&&!confirmed)throw new IllegalArgumentException("请确认这是真实经历");
        title=clean(title,40,true);tags=clean(tags,120,false);text=clean(text,1000,true);
        Entry previous=id==null?null:library.find(id);
        if(id!=null&&(previous==null||previous.revision!=expectedRevision))throw new IllegalArgumentException("素材已变更，请重新打开编辑");
        if(id==null&&library.entries.size()>=MAX_ENTRIES)throw new IllegalArgumentException("最多保存50条，请先删除不需要的素材");
        if(previous!=null&&previous.revision>=Long.MAX_VALUE-1)throw new IllegalArgumentException("素材修订异常，请重新录入");
        Entry next=new Entry(library.kind,id==null?UUID.randomUUID().toString():id,previous==null?1:previous.revision+1,title,tags,text);
        List<Entry> all=new ArrayList<>(library.entries);if(previous==null)all.add(next);else all.set(all.indexOf(previous),next);
        return new Library(library.kind,all);
    }
    static Library delete(Library library,String id,long expectedRevision) {
        Entry previous=library.find(id);
        if(previous==null||previous.revision!=expectedRevision)throw new IllegalArgumentException("素材已变更，请刷新后再删除");
        List<Entry> all=new ArrayList<>(library.entries);all.remove(previous);return new Library(library.kind,all);
    }
    static String clean(String value,int max,boolean required) {
        if(value==null||value.length()>max||value.indexOf('\0')>=0)throw new IllegalArgumentException("内容过长或格式不正确");
        String result=value.trim();if(required&&result.isEmpty())throw new IllegalArgumentException("请填写名称和内容");return result;
    }
    static final class Selection {
        private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>();
        Selection copy(){Selection next=new Selection();next.entries.putAll(entries);return next;}
        void replace(Selection next){entries.clear();entries.putAll(next.entries);}
        int size(){return entries.size();}
        boolean contains(String id){return entries.containsKey(id);}
        void clear(){entries.clear();}
        boolean sameIds(Selection other){return entries.keySet().equals(other.entries.keySet());}
        void toggle(Entry entry) {
            if(entry.kind!=Kind.REAL)throw new IllegalArgumentException("灵感草稿不能用于真实素材");
            if(entries.remove(entry.id)!=null)return;
            if(entries.size()>=MAX_SELECTED)throw new IllegalArgumentException("每轮最多选用3条素材");
            entries.put(entry.id,entry);
        }
        boolean reconcile(Library current) {
            if(current.kind!=Kind.REAL)throw new IllegalArgumentException("素材类型不正确");
            return entries.entrySet().removeIf(item -> !same(item.getValue(),current.find(item.getKey())));
        }
        List<Entry> snapshot(Library current) {
            for(Entry e:entries.values())if(!same(e,current.find(e.id)))throw new IllegalArgumentException("选用素材已变更，请重新选择");
            return Collections.unmodifiableList(new ArrayList<>(entries.values()));
        }
        private boolean same(Entry a,Entry b){return b!=null&&b.kind==Kind.REAL&&a.revision==b.revision&&a.title.equals(b.title)&&a.tags.equals(b.tags)&&a.text.equals(b.text);}
    }
    static String profileForRequest(String original,List<Entry> selected,AiWritingRules.Mode mode) {
        if(original==null)original="";
        if(mode!=AiWritingRules.Mode.REPLY || selected.isEmpty())return original;
        if(selected.size()>MAX_SELECTED)throw new IllegalArgumentException("每轮最多选用3条素材");
        StringBuilder out=new StringBuilder(original);Set<String> ids=new HashSet<>();
        out.append("\n\n用户本轮选用并确认真实的经历：");
        for(Entry e:selected) {
            if(e.kind!=Kind.REAL||!ids.add(e.id))throw new IllegalArgumentException("不能将灵感或重复素材作为事实");
            out.append("\n【").append(e.title).append("】\n").append(e.text);
        }
        if(out.length()>12000)throw new IllegalArgumentException("本轮资料过长，请减少选用素材");
        return out.toString();
    }
}
