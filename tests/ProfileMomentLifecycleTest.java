package dev.local.newsoul;

import android.app.Activity;
import android.os.Handler;
import android.view.View;
import android.widget.FrameLayout;
import java.lang.reflect.*;

/** Execute the production Binding, including detach during a parent's layout loop. */
public class ProfileMomentLifecycleTest {
    static int checks;
    public static class Model {String id;public String b(){return id;}}
    public static class Page {public Model model;}
    public static class Account {static String id;public static String x(){return id;}public static String t(){return id;}}
    public static class LegacyUser {public String userIdEcpt;}
    public static class LegacyOriginal extends HostBase {public LegacyUser z0;public View getView(){return null;}public Activity getActivity(){return null;}public void onResume(){}public void onPause(){}}
    public static class LegacyMeta extends HostBase {public LegacyUser y;public View getView(){return null;}public Activity getActivity(){return null;}public void onResume(){}public void onPause(){}}
    public static class HostModel {public String b(){return "other";}}
    public static class HostBase {public void onDestroyView(){}}
    public static class HostPage extends HostBase {public HostModel k;public View getView(){return null;}public Activity getActivity(){return null;}public void onResume(){}public void onPause(){}}

    static final Class<?> binding;
    static { try { binding=Class.forName("dev.local.newsoul.ProfileMomentLikes$Binding"); }
        catch(Exception e){throw new RuntimeException(e);} }
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static Object create(Activity a,View root,FrameLayout parent)throws Exception{
        Constructor<?> c=binding.getDeclaredConstructor(Activity.class,View.class,FrameLayout.class);
        c.setAccessible(true);return c.newInstance(a,root,parent);
    }
    static void call(Object b,String name)throws Exception{
        Method m=binding.getDeclaredMethod(name);m.setAccessible(true);m.invoke(b);
    }
    static SquareLikeControls.Controller controller(Object b)throws Exception{
        Field f=binding.getDeclaredField("controller");f.setAccessible(true);return (SquareLikeControls.Controller)f.get(b);
    }
    static void invokeDestroy(io.github.libxposed.api.XposedInterface.Hooker hook,Object fragment,boolean throwsOriginal)throws Exception{
        var chain=(io.github.libxposed.api.XposedInterface.Chain)Proxy.newProxyInstance(ProfileMomentLifecycleTest.class.getClassLoader(),new Class<?>[]{io.github.libxposed.api.XposedInterface.Chain.class},(proxy,method,args)->switch(method.getName()){
          case "getThisObject"->fragment;
          case "proceed"->{if(throwsOriginal)throw new IllegalStateException("host destroy failed");yield null;}
          default->throw new AssertionError(method);
        });
        try{hook.intercept(chain);}catch(Exception e){throw e;}catch(Throwable t){throw new AssertionError(t);}
    }
    public static void main(String[] args)throws Exception{
        Page page=new Page();page.model=new Model();page.model.id="other";Account.id="me";
        Field vm=Page.class.getField("model");Method id=Model.class.getMethod("b"),self=Account.class.getMethod("x");
        check(ProfileMomentLikes.otherProfile(page,vm,id,self),"other profile allowed");
        page.model.id="me";check(!ProfileMomentLikes.otherProfile(page,vm,id,self),"own profile excluded regardless of entry");
        page.model.id=null;check(!ProfileMomentLikes.otherProfile(page,vm,id,self),"missing target hidden");
        page.model.id="other";Account.id="";check(!ProfileMomentLikes.otherProfile(page,vm,id,self),"missing account hidden");
        Account.id="other";check(!ProfileMomentLikes.otherProfile(page,vm,id,self),"account switch rechecked");
        page.model=null;check(!ProfileMomentLikes.otherProfile(page,vm,id,self),"uninitialized model hidden");
        check(!ProfileMomentLikes.otherProfile(null,vm,id,self),"released fragment hidden");
        LegacyOriginal oldPage=new LegacyOriginal();oldPage.z0=new LegacyUser();oldPage.z0.userIdEcpt="other";Account.id="me";
        Field oldUser=LegacyOriginal.class.getField("z0"),encrypted=LegacyUser.class.getField("userIdEcpt");Method oldAccount=Account.class.getMethod("t");
        check(ProfileMomentLikes.otherLegacyProfile(oldPage,oldUser,encrypted,oldAccount),"old other profile checks encrypted IDs");
        Account.id="other";check(!ProfileMomentLikes.otherLegacyProfile(oldPage,oldUser,encrypted,oldAccount),"old own profile is hidden after account switch");
        Account.id="";check(!ProfileMomentLikes.otherLegacyProfile(oldPage,oldUser,encrypted,oldAccount),"old missing account fails closed");
        Account.id="me";oldPage.z0=null;check(!ProfileMomentLikes.otherLegacyProfile(oldPage,oldUser,encrypted,oldAccount),"old uninitialized profile is hidden");
        Activity a=new Activity();FrameLayout parent=new FrameLayout();View root=new View(a);parent.add(root);
        Object b=create(a,root,parent);call(b,"resume");
        check(parent.getChildCount()==1,"resume must not mutate children during layout");
        Handler.drain();check(parent.getChildCount()==2&&controller(b).active,"mount after traversal");
        int count=parent.getChildCount();
        for(int i=0;i<count;i++){
            View child=parent.getChildAt(i);
            if(child==root)root.detach();
            check(child!=null,"layout retains original children after root detach");
        }
        check(!controller(b).active,"detach immediately stops work");
        check(parent.getChildCount()==2,"detach defers structural removal");
        Handler.drain();check(parent.getChildCount()==1,"queued removal cleans only overlay");
        check(!root.listeners.isEmpty(),"temporary detach retains reattach listener");
        root.attach();Handler.drain();
        check(parent.getChildCount()==2&&controller(b).active,"reattach restores without Fragment resume");
        root.detach();root.attach();Handler.drain();
        check(parent.getChildCount()==2&&controller(b).active,"same-turn reparent removes before remount");
        call(b,"remove");Handler.drain();
        check(root.listeners.isEmpty(),"permanent removal releases listener");
        call(b,"resume");Handler.drain();check(parent.getChildCount()==1,"removed binding cannot remount");
        View second=new View(a);Object pending=create(a,second,parent);call(pending,"resume");call(pending,"pause");
        Handler.drain();check(controller(pending).button.getParent()==null,"pause cancels pending mount");
        call(pending,"resume");call(pending,"resume");Handler.drain();
        check(parent.getChildCount()==2&&controller(pending).resumes==1,"repeated resume queues once");
        call(pending,"remove");call(pending,"remove");Handler.drain();check(parent.getChildCount()==1,"remove is idempotent");
        View third=new View(a);Object stale=create(a,third,parent);call(stale,"resume");third.detach();Handler.drain();
        check(controller(stale).button.getParent()==null,"detach before mount cancels stale overlay");
        View fourth=new View(a);Object destroyed=create(a,fourth,parent);call(destroyed,"resume");a.destroyed=true;Handler.drain();
        check(controller(destroyed).button.getParent()==null,"destroyed activity cannot mount");
        call(destroyed,"remove");Handler.drain();a.destroyed=false;
        View late=new View(a);late.attached=false;Object waiting=create(a,late,parent);call(waiting,"resume");Handler.drain();
        check(controller(waiting).button.getParent()==null,"resume before attach defers mount");
        late.attach();Handler.drain();check(controller(waiting).button.getParent()==parent,"late attach mounts without another resume");
        late.detach();call(waiting,"pause");late.attach();Handler.drain();
        check(!controller(waiting).active&&controller(waiting).button.getParent()==null,"paused fragment does not remount on attach");
        call(waiting,"remove");Handler.drain();
        a.window.decor=parent;parent.height=1200;
        View expanded=new View(a);expanded.height=1300;expanded.screenTop=100;
        View actions=new View(a);actions.id=4;actions.screenTop=1000;actions.height=200;parent.add(actions);
        Object placed=create(a,expanded,parent);call(placed,"resume");Handler.drain();
        Field position=binding.getDeclaredField("position");position.setAccessible(true);
        android.view.ViewTreeObserver.OnPreDrawListener listener=(android.view.ViewTreeObserver.OnPreDrawListener)position.get(placed);
        listener.onPreDraw();check(controller(placed).button.y==908,"expanded root stays 24dp above action bar");
        expanded.screenTop=500;expanded.screenLeft=30;expanded.height=500;listener.onPreDraw();
        check(controller(placed).button.y==908&&controller(placed).button.x==32,"scrolling moments root cannot move overlay");
        actions.screenTop=900;listener.onPreDraw();check(controller(placed).button.y==808,"moving action bar updates anchor");
        actions.shown=false;listener.onPreDraw();check(controller(placed).button.y==1108,"hidden action bar uses visible container boundary");
        call(placed,"remove");Handler.drain();
        // Fragment remains strongly held in the back stack after its view dies.
        Field bindingsField=ProfileMomentLikes.class.getDeclaredField("bindings");bindingsField.setAccessible(true);
        @SuppressWarnings("unchecked") java.util.Map<Object,Object> bindings=(java.util.Map<Object,Object>)bindingsField.get(null);
        Object backStackFragment=new Object();View backStackRoot=new View(a);
        Object backStackBinding=create(a,backStackRoot,parent);bindings.put(backStackFragment,backStackBinding);
        call(backStackBinding,"resume");Handler.drain();
        backStackRoot.detach();Handler.drain();
        check(bindings.containsKey(backStackFragment)&&!backStackRoot.listeners.isEmpty(),"temporary detach keeps map binding for reparent");
        ProfileMomentLikes.destroyBinding(backStackFragment);Handler.drain();
        check(!bindings.containsKey(backStackFragment)&&backStackRoot.listeners.isEmpty(),"destroy view releases map value and root listener while fragment remains held");
        backStackRoot.attach();call(backStackBinding,"resume");Handler.drain();
        check(controller(backStackBinding).button.getParent()==null,"destroyed view binding cannot remount on late attach or resume");
        ProfileMomentLikes.destroyBinding(backStackFragment);Handler.drain();
        check(!bindings.containsKey(backStackFragment),"duplicate destroy view is harmless");
        View replacementRoot=new View(a);Object replacement=create(a,replacementRoot,parent);bindings.put(backStackFragment,replacement);
        call(replacement,"resume");ProfileMomentLikes.destroyBinding(backStackFragment);Handler.drain();
        check(!bindings.containsKey(backStackFragment)&&controller(replacement).button.getParent()==null,"destroy view cancels queued mount for recreated root");
        // Install the actual hook against a page inheriting onDestroyView.
        java.util.Map<String,io.github.libxposed.api.XposedInterface.Hooker> hooks=new java.util.HashMap<>();
        io.github.libxposed.api.XposedInterface framework=(io.github.libxposed.api.XposedInterface)Proxy.newProxyInstance(
          ProfileMomentLifecycleTest.class.getClassLoader(),new Class<?>[]{io.github.libxposed.api.XposedInterface.class},(proxy,method,arguments)->{
            if(method.getName().equals("deoptimize"))return true;
            if(method.getName().equals("hook")){
              String name=((Method)arguments[0]).getName();
              return Proxy.newProxyInstance(ProfileMomentLifecycleTest.class.getClassLoader(),new Class<?>[]{io.github.libxposed.api.XposedInterface.HookBuilder.class},(builder,bm,ba)->{
                if(bm.getName().equals("intercept")){hooks.put(name,(io.github.libxposed.api.XposedInterface.Hooker)ba[0]);return null;}
                return builder;
              });
            }
            throw new AssertionError(method);
          });
        io.github.libxposed.api.XposedModule module=new io.github.libxposed.api.XposedModule(){};
        module.attachFramework(framework,()->{});
        ClassLoader loader=new ClassLoader(){public Class<?> loadClass(String name)throws ClassNotFoundException{return switch(name){
          case "cn.soulapp.android.square.api.user.UserHomePostFragment"->HostPage.class;
          case "cn.soulapp.android.square.api.user.viewmodel.UserHomePostViewModel"->HostModel.class;
          case "cn.soulapp.android.client.component.middle.platform.utils.user.a"->Account.class;
          default->super.loadClass(name);
        };}};
        LoadedPostLikes.profileReady=true;ProfileMomentLikes.install(module,loader);
        check(ProfileMomentLikes.ready&&hooks.containsKey("onDestroyView"),"install hooks inherited destroy view");
        HostPage hostPage=new HostPage();View hookRoot=new View(a);Object hooked=create(a,hookRoot,parent);bindings.put(hostPage,hooked);call(hooked,"resume");
        invokeDestroy(hooks.get("onDestroyView"),hostPage,false);Handler.drain();
        check(!bindings.containsKey(hostPage)&&hookRoot.listeners.isEmpty()&&controller(hooked).button.getParent()==null,"installed hook removes binding and pending mount");
        View throwingRoot=new View(a);Object throwing=create(a,throwingRoot,parent);bindings.put(hostPage,throwing);call(throwing,"resume");Handler.drain();
        boolean threw=false;try{invokeDestroy(hooks.get("onDestroyView"),hostPage,true);}catch(IllegalStateException expected){threw=true;}
        Handler.drain();check(threw&&!bindings.containsKey(hostPage)&&throwingRoot.listeners.isEmpty(),"destroy hook cleans even when original lifecycle throws");
        HostBase nonTarget=new HostBase();View foreignRoot=new View(a);Object foreign=create(a,foreignRoot,parent);bindings.put(nonTarget,foreign);
        invokeDestroy(hooks.get("onDestroyView"),nonTarget,false);Handler.drain();
        check(bindings.containsKey(nonTarget)&&!foreignRoot.listeners.isEmpty(),"inherited hook does not clean non-target fragment");
        ProfileMomentLikes.destroyBinding(nonTarget);Handler.drain();
        // The absent modern page redirects registration to both real old home layouts.
        java.util.Set<String> oldHooks=new java.util.HashSet<>();
        io.github.libxposed.api.XposedInterface legacyFramework=(io.github.libxposed.api.XposedInterface)Proxy.newProxyInstance(
          ProfileMomentLifecycleTest.class.getClassLoader(),new Class<?>[]{io.github.libxposed.api.XposedInterface.class},(proxy,method,arguments)->{
            if(method.getName().equals("deoptimize"))return true;
            if(method.getName().equals("hook")){
              Method registered=(Method)arguments[0];
              return Proxy.newProxyInstance(ProfileMomentLifecycleTest.class.getClassLoader(),new Class<?>[]{io.github.libxposed.api.XposedInterface.HookBuilder.class},(builder,bm,ba)->{
                if(bm.getName().equals("intercept")){oldHooks.add(registered.toString());return null;}
                return builder;
              });
            }
            throw new AssertionError(method);
          });
        io.github.libxposed.api.XposedModule legacyModule=new io.github.libxposed.api.XposedModule(){};legacyModule.attachFramework(legacyFramework,()->{});
        ClassLoader oldLoader=new ClassLoader(){public Class<?> loadClass(String name)throws ClassNotFoundException{return switch(name){
          case "cn.soulapp.android.square.api.user.UserHomePostFragment"->throw new ClassNotFoundException(name);
          case "cn.soulapp.android.component.home.user.fragment.PageUserHomeFragment"->LegacyOriginal.class;
          case "cn.soulapp.android.component.home.meta.user.MetaPageUserHomeFragment"->LegacyMeta.class;
          case "com.soul.component.componentlib.service.user.bean.User"->LegacyUser.class;
          case "l9.a"->Account.class;
          default->super.loadClass(name);
        };}};
        ProfileMomentLikes.install(legacyModule,oldLoader);
        check(ProfileMomentLikes.ready&&oldHooks.size()==5,"both old resume/pause entries and shared inherited destroy are installed");
        LoadedPostLikes.profileReady=false;ProfileMomentLikes.install(legacyModule,oldLoader);
        check(!ProfileMomentLikes.ready,"old entry never claims ready when post sources failed");
        System.out.println("Profile lifecycle: "+checks+" production-binding checks passed");
    }
}
