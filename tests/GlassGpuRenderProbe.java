package dev.local.newsoul;
import android.graphics.*;
import android.media.*;
import android.os.*;
import java.io.*;
import java.lang.reflect.Field;
public class GlassGpuRenderProbe {
 public static void main(String[] args) throws Exception {
  Looper.prepare();
  int w=1140;float d=3;
  GlassLensRenderer lensRenderer=new GlassLensRenderer();
  for(boolean dark:new boolean[]{true,false}) for(int stage=0;stage<5;stage++) {
   final boolean night=dark;
   UiAppearance appearance=new UiAppearance(UiAppearance.Style.ORIGINAL_GLASS_TABS,dark,d);
   appearance.categorySurface=true;
   int h=stage==4?600:216;
   float pulse=stage==1?1:0, cell=279;
   RectF outer=new RectF(3+15*pulse,27+12*pulse,w-3-15*pulse,189-12*pulse);
   float left=12+cell*(stage==0?0:stage==1?1.45f:3);
   RectF lens=new RectF(Math.max(3,left-27*pulse),45-18*pulse,Math.min(w-3,left+cell+27*pulse),171+18*pulse);
   if (stage==3) lens.set(300,50,840,170);
   float radius=stage==3?24:lens.height()/2;
   GlassLensRenderer.Source source=canvas->{
    canvas.drawColor(appearance.panelColor());
    Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setShader(new LinearGradient(0,outer.top,0,outer.bottom,appearance.tabTrackTop(),appearance.tabTrackBottom(),Shader.TileMode.CLAMP));
    canvas.drawRoundRect(outer,outer.height()/2,outer.height()/2,p);p.setShader(null);
    p.setColor(appearance.tabLensColor());canvas.drawRoundRect(lens,radius,radius,p);
    p.setColor(night?0xff00b5a2:0xff00796b);
    for(int i=0;i<4;i++) {
      float x=12+279*(i+.5f);
      canvas.drawRoundRect(new RectF(x-44,84,x-6,126),3,3,p);
      canvas.drawRoundRect(new RectF(x+6,84,x+44,126),3,3,p);
    }
   };
   RenderNode root=new RenderNode("GlassProbe");root.setPosition(0,0,w,h);
   RecordingCanvas rc=root.beginRecording(w,h);source.draw(rc);
   GlassLensRenderer activeRenderer=lensRenderer;
   GlassCardBackground card=null;
   if(stage==4) {
    card=new GlassCardBackground(dark,dark?0xff181828:0xfff5f6f8,42,d);
    card.setBounds(18,18,w-18,h-18);card.draw(rc);
    Field field=GlassCardBackground.class.getDeclaredField("renderer");field.setAccessible(true);
    activeRenderer=(GlassLensRenderer)field.get(card);
    // Foreground drawn after the real card Drawable must not be refracted/tinted.
    Paint fg=new Paint();fg.setColor(dark?0xffe6e6ef:0xff333333);
    rc.drawRect(w/2-60,h/2-20,w/2+60,h/2+20,fg);
   } else lensRenderer.draw(rc,w,h,lens,radius,pulse,d,dark,source);
   root.endRecording();
   Field failed=GlassLensRenderer.class.getDeclaredField("gpuFailed");failed.setAccessible(true);
   Field gpu=GlassLensRenderer.class.getDeclaredField("gpu");gpu.setAccessible(true);
   if(failed.getBoolean(activeRenderer) || gpu.get(activeRenderer)==null)throw new AssertionError("GPU path silently fell back");
   try(ImageReader reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2)){
    HardwareRenderer renderer=new HardwareRenderer();renderer.setSurface(reader.getSurface());renderer.setContentRoot(root);
    int result=renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw();
    Image im=null;for(int i=0;i<40 && im==null;i++){im=reader.acquireLatestImage();if(im==null)Thread.sleep(25);}
    if(im==null)throw new Exception("No image; status="+result);
    Image.Plane plane=im.getPlanes()[0];int stride=plane.getRowStride()/plane.getPixelStride();
    Bitmap wide=Bitmap.createBitmap(stride,h,Bitmap.Config.ARGB_8888);wide.copyPixelsFromBuffer(plane.getBuffer());
    Bitmap out=Bitmap.createBitmap(wide,0,0,w,h);
    try(FileOutputStream f=new FileOutputStream(args[0]+"-"+(dark?"dark":"light")+"-"+stage+".png")){out.compress(Bitmap.CompressFormat.PNG,100,f);}
    if(Color.alpha(out.getPixel(w/2,h/2))!=255)throw new AssertionError("Unexpected transparent center");
    if(out.getPixel(0,0)!=appearance.panelColor())throw new AssertionError("Effect escaped its bounds");
    if(stage==4 && out.getPixel(w/2,h/2)!=(dark?0xffe6e6ef:0xff333333))
      throw new AssertionError("Glass card changed foreground color");
    im.close();renderer.destroy();
   }
   if(card!=null)card.release();
   System.out.println("Rendered "+dark+" "+stage);
  }
  // Exercise the software/older-API path separately without pretending it is a full old-device test.
  Bitmap fallback=Bitmap.createBitmap(w,216,Bitmap.Config.ARGB_8888);
  Canvas soft=new Canvas(fallback);soft.drawColor(0xff181828);
  lensRenderer.draw(soft,w,216,new RectF(200,30,500,180),75,1,3,true,c->{c.drawColor(0xff181828);});
  if(Color.alpha(fallback.getPixel(300,100))!=255)throw new AssertionError("Fallback lost opaque backdrop");
  lensRenderer.release();System.out.println("10 GPU scenes (including real card Drawable and foreground color) + software fallback passed");System.exit(0);
 }
}
