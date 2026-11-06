package dev.local.newsoul;
final class SquareLikeControls {
 static boolean ready=true;
 static final class ActionView extends android.view.View {ActionView(android.app.Activity a){super(a);}}
 static final class Controller {
  java.util.function.BooleanSupplier profileAccess; final ActionView button;boolean active;int resumes;
  Controller(android.app.Activity a,android.view.View root,boolean profile){button=new ActionView(a);}
  int id(String name){return 4;}
  void resume(){active=true;resumes++;}void pause(){active=false;button.setVisibility(8);}
 }
}
