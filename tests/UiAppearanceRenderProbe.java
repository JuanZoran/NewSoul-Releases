package dev.local.newsoul;

import android.graphics.*;
import android.graphics.drawable.*;
import android.media.*;
import android.os.*;
import java.io.*;
import java.lang.reflect.Field;

/** Offscreen production-factory coverage; does not launch Soul, use Views, or send input. */
public final class UiAppearanceRenderProbe {
    public static void main(String[] args) throws Exception {
        Looper.prepare();
        int scenes=0;
        for(UiAppearance.Style style:UiAppearance.Style.values()) for(boolean dark:new boolean[]{false,true}) {
            UiAppearance appearance=new UiAppearance(style,dark,2);
            verifySegmentedFeedback(appearance);
            UiAppearance.Role[] roles={UiAppearance.Role.PANEL,UiAppearance.Role.SHEET,UiAppearance.Role.CARD,
                    UiAppearance.Role.CHOICE,UiAppearance.Role.PRIMARY,UiAppearance.Role.INPUT,UiAppearance.Role.ACTION,UiAppearance.Role.INLINE_ACTION,UiAppearance.Role.MEDIA_ACTION,UiAppearance.Role.RADIO_ROW,UiAppearance.Role.LIST_ITEM};
            int width=960,height=roles.length*120;
            RenderNode root=new RenderNode("AppearanceProbe");root.setPosition(0,0,width,height);
            RecordingCanvas canvas=root.beginRecording(width,height);canvas.drawColor(0xff737383);
            // Distinct colors behind the first card reveal whether it is truly transmitting the backdrop.
            Paint backdrop=new Paint();backdrop.setColor(0xff3f6680);canvas.drawRect(20,270,120,330,backdrop);
            backdrop.setColor(0xff803f66);canvas.drawRect(120,270,220,330,backdrop);
            Drawable[] surfaces=new Drawable[roles.length*4];
            int index=0;
            for(int row=0;row<roles.length;row++) for(int state=0;state<4;state++) {
                Drawable surface=appearance.background(roles[row]);
                surfaces[index++]=surface;
                surface.setBounds(state*240+12,row*120+12,state*240+228,row*120+108);
                int enabled=android.R.attr.state_enabled, selected=android.R.attr.state_selected;
                surface.setState(state==0?new int[]{enabled}:state==1?new int[]{enabled,selected}:
                        state==2?new int[]{selected}:new int[]{enabled,android.R.attr.state_focused});
                surface.draw(canvas);
                verifyRenderer(surface,style);
                scenes++;
            }
            root.endRecording();
            Bitmap image=render(root,width,height);
            // Selected and keyboard-focused choices/fields must have visible state, in both styles/themes.
            for(int row:new int[]{3,5}) {
                int x=style==UiAppearance.Style.ORIGINAL_GLASS_TABS && row==5 ? 13 : 120;
                int normal=image.getPixel(x,row*120+60);
                if(normal==image.getPixel(x+240,row*120+60))throw new AssertionError("Selection lost: "+style+" "+dark);
                if(image.getPixel(x+240,row*120+60)==image.getPixel(x+480,row*120+60))throw new AssertionError("Disabled selection not muted");
                if(normal==image.getPixel(x+720,row*120+60))throw new AssertionError("Focus lost: "+style+" "+dark);
            }
            if(style==UiAppearance.Style.LIQUID_GLASS) {
                if(image.getPixel(60,300)==image.getPixel(180,300))throw new AssertionError("Clear card hid backdrop");
            } else if(image.getPixel(60,300)!=image.getPixel(180,300))throw new AssertionError("Flat card transmitted backdrop");
            // Inline controls keep an empty vertical gutter inside the full 48dp target.
            if(image.getPixel(120,7*120+16)!=0xff737383)throw new AssertionError("Inline surface fills touch target");
            if(appearance.quietActions()) {
                if(image.getPixel(120,7*120+60)!=0xff737383)throw new AssertionError("Matte inline action has a plate");
                if(image.getPixel(120,6*120+60)!=0xff737383)throw new AssertionError("Matte secondary action has a plate");
            } else if(image.getPixel(120,7*120+60)==0xff737383)throw new AssertionError("Inline surface disappeared");
            if(image.getPixel(120,9*120+60)!=0xff737383 || image.getPixel(360,9*120+60)!=0xff737383)
                throw new AssertionError("Radio list row acquired a selection plate");
            if(image.getPixel(120,10*120+60)==0xff737383)
                throw new AssertionError("List item lost its surface");
            if(image.getPixel(13,10*120+13)!=0xff737383)
                throw new AssertionError("List item corners are not rounded");
            // Sheet bottom corners remain filled, while its top corners remain rounded.
            if(image.getPixel(16,120+104)==0xff737383)throw new AssertionError("Sheet bottom rounded");
            if(image.getPixel(16,120+16)!=0xff737383)throw new AssertionError("Sheet top not rounded");
            if(image.getPixel(0,0)!=0xff737383)throw new AssertionError("Surface escaped bounds");
            try(FileOutputStream stream=new FileOutputStream(args[0]+"-"+style+"-"+dark+".png")) {
                image.compress(Bitmap.CompressFormat.PNG,100,stream);
            }
            for(Drawable surface:surfaces)UiAppearance.release(surface);
            // Released backgrounds must be reusable when a cached page attaches again.
            canvas=root.beginRecording(width,height);
            for(Drawable surface:surfaces){surface.draw(canvas);verifyRenderer(surface,style);}
            root.endRecording();render(root,width,height);
            for(Drawable surface:surfaces)UiAppearance.release(surface);
            root.discardDisplayList();
            System.out.println(style+" dark="+dark+": states, clipping, release/reuse passed");
        }
        System.out.println(scenes+" production role/state GPU scenes passed");
        System.exit(0);
    }
    private static void verifySegmentedFeedback(UiAppearance appearance) {
        for (int height : new int[]{96, 160}) {
            RippleDrawable feedback = (RippleDrawable) appearance.segmentedFeedback();
            feedback.setBounds(0, 0, 280, height);
            Drawable mask = feedback.findDrawableByLayerId(android.R.id.mask);
            Bitmap bitmap = Bitmap.createBitmap(280, height, Bitmap.Config.ARGB_8888);
            mask.draw(new Canvas(bitmap));
            // Default and wrapped labels: feedback must exclude corners and lens gutters.
            if (Color.alpha(bitmap.getPixel(2, 8)) != 0
                    || Color.alpha(bitmap.getPixel(140, 2)) != 0
                    || Color.alpha(bitmap.getPixel(140, height - 3)) != 0)
                throw new AssertionError("Segmented feedback escaped capsule");
            if (Color.alpha(bitmap.getPixel(140, height / 2)) != 255)
                throw new AssertionError("Segmented feedback lost its center");
            bitmap.recycle();
        }
    }

    private static void verifyRenderer(Drawable drawable,UiAppearance.Style style) throws Exception {
        if(drawable instanceof GlassCardBackground) {
            if(style!=UiAppearance.Style.LIQUID_GLASS)throw new AssertionError("Flat style uses glass");
            Field field=GlassCardBackground.class.getDeclaredField("renderer");field.setAccessible(true);
            Object renderer=field.get(drawable);
            Field gpu=GlassLensRenderer.class.getDeclaredField("gpu");gpu.setAccessible(true);
            Field failed=GlassLensRenderer.class.getDeclaredField("gpuFailed");failed.setAccessible(true);
            if(gpu.get(renderer)==null || failed.getBoolean(renderer))throw new AssertionError("GPU silently fell back");
        } else if(drawable instanceof InsetDrawable)verifyRenderer(((InsetDrawable)drawable).getDrawable(),style);
        else if(drawable instanceof LayerDrawable) {
            LayerDrawable layers=(LayerDrawable)drawable;
            // Ripple mask is a primitive, not an independently rendered surface.
            verifyRenderer(layers.getDrawable(0),style);
        } else if(drawable instanceof StateListDrawable)verifyRenderer(drawable.getCurrent(),style);
    }
    private static Bitmap render(RenderNode root,int w,int h) throws Exception {
        try(ImageReader reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2)) {
            HardwareRenderer renderer=new HardwareRenderer();
            try {
                renderer.setSurface(reader.getSurface());renderer.setContentRoot(root);
                renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw();
                Image image=null;
                for(int i=0;i<40&&image==null;i++){image=reader.acquireLatestImage();if(image==null)Thread.sleep(25);}
                if(image==null)throw new AssertionError("No frame");
                try {
                    Image.Plane plane=image.getPlanes()[0];
                    Bitmap wide=Bitmap.createBitmap(plane.getRowStride()/plane.getPixelStride(),h,Bitmap.Config.ARGB_8888);
                    wide.copyPixelsFromBuffer(plane.getBuffer());
                    return Bitmap.createBitmap(wide,0,0,w,h);
                } finally {image.close();}
            } finally {renderer.destroy();}
        }
    }
}
