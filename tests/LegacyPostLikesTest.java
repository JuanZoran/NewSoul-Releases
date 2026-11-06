package dev.local.newsoul;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.*;
import java.util.*;

public class LegacyPostLikesTest {
    static int checks;
    static void check(boolean ok,String label) { checks++; if(!ok)throw new AssertionError(label); }
    public static class Post { public boolean liked; public long likes; public String g(){return likes==0?"":String.valueOf(likes);} }
    public interface Consumer {void accept(Object value);}
    public static class Single {Post post;Single(Post p){post=p;}public Object subscribe(Consumer ok,Consumer fail){post.liked=true;post.likes++;ok.accept(post);return null;}}
    public static class Helper {static int sends;Post post;public Helper(Post p){post=p;}public Single h(int mode){if(mode!=0)throw new AssertionError("ordinary heart");sends++;return new Single(post);}}
    public static class MainAdapter {public final List<Object> values=new ArrayList<>();public List<?> getData(){return values;}}
    public static class LightAdapter {public final List<Object> values=new ArrayList<>();public List<?> j(){return values;}}
    public static class OriginalAdapter {public final List<Object> values=new ArrayList<>();public List<?> getAllData(){return values;}}
    public static class Recycler extends ViewGroup {public Object adapter;public final Map<View,Object> holders=new HashMap<>();public Object getAdapter(){return adapter;}public Object getChildViewHolder(View view){return holders.get(view);}}
    public static class Footer {public Post post;public int binds;public Post getPost(){return post;}public void onBindViewHolder(int position,Post expected){if(expected!=post||position!=7)throw new AssertionError("bound footer");binds++;}}
    public static class Config {public Footer footer;public Footer k(){return footer;}}
    public static class Holder {private Post post;public Config config=new Config();public int getLayoutPositionProxy(){return 7;}Holder(Post value){post=value;config.footer=new Footer();config.footer.post=value;}}
    public static class Account {public static String id(){return "account";}}
    static void set(Class<?> owner,String name,Object value)throws Exception{Field f=owner.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
    public static void main(String[] args)throws Exception {
        set(SquareLikeControls.class,"accountId",Account.class.getMethod("id"));
        ClassLoader loader=new ClassLoader(){public Class<?> loadClass(String name)throws ClassNotFoundException{return switch(name){
            case "androidx.recyclerview.widget.RecyclerView" -> Recycler.class;
            case "cn.soulapp.android.component.square.main.squarepost.SquareAdapter" -> MainAdapter.class;
            case "cn.soulapp.android.component.square.post.q0" -> Helper.class;
            case "cn.soulapp.android.component.square.main.PostVH" -> Holder.class;
            case "cn.soulapp.android.component.home.user.adapter.l0" -> OriginalAdapter.class;
            case "com.lufficc.lightadapter.LightAdapter" -> LightAdapter.class;
            case "io.reactivex.functions.Consumer" -> Consumer.class;
            default -> super.loadClass(name);
        };}};
        LoadedPostLikes.install(loader,Post.class);
        check(LoadedPostLikes.ready&&LoadedPostLikes.cityReady&&LoadedPostLikes.profileReady,"all old source ABIs bind");
        Activity activity=new Activity();ViewGroup root=new ViewGroup();root.context=activity;
        Recycler square=new Recycler();square.context=activity;MainAdapter main=new MainAdapter();square.adapter=main;root.add(square);
        Recycler city=new Recycler();city.context=activity;LightAdapter light=new LightAdapter();city.adapter=light;root.add(city);
        Recycler profile=new Recycler();profile.context=activity;OriginalAdapter original=new OriginalAdapter();profile.adapter=original;root.add(profile);
        check(LoadedPostLikes.find(root).adapter==main,"square never treats old LightAdapter as main feed");
        check(LoadedPostLikes.find(city,true).adapter==light,"city uses old LightAdapter");
        check(LoadedPostLikes.find(profile,false,true).adapter==original,"original home uses old getAllData");
        check(LoadedPostLikes.find(city,false,true).adapter==light,"meta home uses old LightAdapter in profile root");
        Post post=new Post();light.values.add(post);View row=new View(activity);city.add(row);Holder holder=new Holder(post);city.holders.put(row,holder);
        LoadedPostLikes.Source source=LoadedPostLikes.find(city,true);List<?> snapshot=source.data();light.values.add(new Post());check(snapshot.size()==1,"old data is copied");
        source.refresh(post);check(holder.config.footer.binds==1,"only legacy bound footer refreshed");
        holder.config.footer.post=new Post();source.refresh(post);check(holder.config.footer.binds==1,"stale footer is untouched");holder.config.footer.post=post;
        row.visibility=View.GONE;source.refresh(post);check(holder.config.footer.binds==1,"hidden legacy row stays untouched");row.visibility=0;
        LoadedPostLikes.send(activity,source,post,ok->check(ok,"old helper success callback"));
        check(Helper.sends==1&&post.liked&&post.likes==1&&holder.config.footer.binds==2,"old ordinary helper mutates model once then refreshes footer");
        source.refreshCompleted();check(holder.config.footer.binds==2,"stable completed row not rebound repeatedly");
        Post moment=new Post();original.values.add(moment);ViewGroup wrapper=new ViewGroup();wrapper.context=activity;profile.add(wrapper);
        ViewGroup momentRow=new ViewGroup();momentRow.context=activity;momentRow.setTag(2,moment);wrapper.add(momentRow);
        TextView text=new TextView(activity);text.id=4;momentRow.add(text);
        LoadedPostLikes.Source profileSource=LoadedPostLikes.find(profile,false,true);
        LoadedPostLikes.send(activity,profileSource,moment,ok->check(ok,"old profile helper success callback"));
        check(text.text.equals("1")&&text.icon==4,"old profile text heart updates native count and icon");
        int before=Helper.sends;profileSource.refresh(new Post());check(Helper.sends==before&&text.text.equals("1"),"refresh never submits and rejects unrelated model");
        original.values.clear();moment.likes=10;profileSource.refresh(moment);check(text.text.equals("1"),"removed old profile model stays untouched");
        System.out.println("Legacy post likes: "+checks+" source/footer checks passed; fake requests only");
    }
}
