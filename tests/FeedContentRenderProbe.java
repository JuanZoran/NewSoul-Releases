package dev.local.newsoul;
import android.graphics.*;
import java.io.FileOutputStream;
/** Uses the production drawable on a software canvas, matching the View's software layer. */
public class FeedContentRenderProbe {
 public static void main(String[] args)throws Exception {
  Bitmap bitmap=Bitmap.createBitmap(1000,720,Bitmap.Config.ARGB_8888);
  Canvas c=new Canvas(bitmap);c.drawColor(0xffd4d7df);
  Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  for(int theme=0;theme<2;theme++) {
   boolean dark=theme==1;int x=theme*500;
   p.setColor(dark?0xff1a1b2c:0xfff5f6f8);c.drawRect(x,0,x+500,720,p);
   for(int row=0;row<3;row++) {
    for(int i=0;i<3;i++) {
     boolean selected=row==0||i==1,error=row==2&&i==1;
     SoftFilterBackground bg=new SoftFilterBackground(dark,2);
     bg.state(selected,error);bg.setBounds(x+15+i*158,row*220+90,x+169+i*158,row*220+206);bg.draw(c);
     p.setColor(error?0xffed737a:selected?(dark?0xff66dec4:0xff008c75):(dark?0xffe0e2ea:0xff424958));
    }
   }
  }
  try(FileOutputStream out=new FileOutputStream(args[0])){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
  System.out.println("18 production key states rendered");
 }
}
