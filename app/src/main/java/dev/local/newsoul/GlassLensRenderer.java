package dev.local.newsoul;

import android.graphics.*;
import android.os.Build;

/** A local rounded glass surface. Callers supply only the layers behind the lens, never itself. */
final class GlassLensRenderer {
    interface Source { void draw(Canvas canvas); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path clip = new Path();
    private Api33 gpu;
    private boolean gpuFailed;
    private Bitmap bitmap;
    private Canvas bitmapCanvas;

    void draw(Canvas canvas, int width, int height, RectF bounds, float radius,
            float activity, float density, boolean dark, Source source) {
        if (width <= 0 || height <= 0 || bounds.isEmpty()) return;
        clip.reset();
        clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(clip);
        try {
            if (Build.VERSION.SDK_INT >= 33 && canvas.isHardwareAccelerated() && !gpuFailed) {
                try {
                    if (gpu == null) gpu = new Api33();
                    gpu.draw(canvas, width, height, bounds, radius, activity, density, dark, source);
                    return;
                } catch (RuntimeException error) {
                    gpuFailed = true;
                    if (gpu != null) gpu.release();
                    gpu = null;
                    android.util.Log.w("SoulHook102", "Glass lens uses Canvas fallback", error);
                }
            }
            if (bitmap == null || bitmap.getWidth() != width || bitmap.getHeight() != height) {
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmapCanvas = new Canvas(bitmap);
            }
            bitmap.eraseColor(Color.TRANSPARENT);
            source.draw(bitmapCanvas);
            int scaled = canvas.save();
            canvas.scale(1 + .16f * activity, 1 + .16f * activity, bounds.centerX(), bounds.centerY());
            canvas.drawBitmap(bitmap, 0, 0, paint);
            canvas.restoreToCount(scaled);
            // Older systems keep a visible, translucent surface even without refraction.
            paint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                    dark ? 0x0c9bddcf : 0x09008070, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(bounds, radius, radius, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(density * .6f);
            paint.setColor(dark ? 0x35ffffff : 0x18616875);
            canvas.drawRoundRect(bounds, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
        } finally { canvas.restoreToCount(save); }
    }

    void release() {
        if (gpu != null) gpu.release();
        gpu = null;
        bitmap = null;
        bitmapCanvas = null;
    }

    /** API-isolated GPU source/effect: no bitmap capture or GPU readback on this path. */
    private static final class Api33 {
        private final RenderNode sourceNode = new RenderNode("NewSoul glass backdrop");
        private final RuntimeShader shader = new RuntimeShader("""
                uniform shader content;
                uniform float2 center;
                uniform float2 halfSize;
                uniform float2 sourceSize;
                uniform float radius;
                uniform float strength;
                uniform float density;
                uniform float dark;
                half4 main(float2 p) {
                    float2 local = p-center;
                    float2 corner = abs(local)-halfSize+radius;
                    float2 outside = max(corner, 0.0);
                    float sd = length(outside)+min(max(corner.x,corner.y),0.0)-radius;
                    float2 n;
                    if (length(outside)>0.001) n = sign(local)*normalize(outside);
                    else n = abs(corner.x-corner.y)<0.001 ? float2(0.0,-1.0)
                            : sign(local)*(corner.x>corner.y ? float2(1.0,0.0) : float2(0.0,1.0));
                    float depth = max(-sd,0.0);
                    float rimWidth = min(min(halfSize.x,halfSize.y)*0.65, density*(4.5+strength*3.0));
                    float edge = 1.0-smoothstep(0.0,max(rimWidth,0.01),depth);
                    float curved = 1.0-sqrt(max(0.0,1.0-edge*edge));
                    float bend = curved*rimWidth*(0.35+strength*0.5);
                    float2 src = center+local/(1.0+strength*0.16)-n*bend;
                    float2 split = n*density*(0.10+strength*0.45)*edge;
                    float2 lo = float2(0.5), hi = sourceSize-float2(0.5);
                    half4 c = content.eval(clamp(src,lo,hi));
                    half4 r = content.eval(clamp(src+split,lo,hi));
                    half4 b = content.eval(clamp(src-split,lo,hi));
                    // content is premultiplied. Keep a clear film clear instead of turning
                    // transparent pixels into an opaque pastel plate during tint/highlight math.
                    half alpha = c.a;
                    half3 rgb = half3(r.r,c.g,b.b)/max(alpha,half(0.001));
                    // A thin transmission tint; highlights remain neutral rather than green.
                    rgb = mix(rgb,half3(0.46,0.86,0.79),half(0.018+dark*0.012));
                    float facing = dot(n,float2(-0.6,-0.8));
                    float front = pow(max(facing,0.0),4.0);
                    float back = pow(max(-facing,0.0),6.0);
                    float rim = 1.0-smoothstep(0.0,density*0.8,depth);
                    float inner = (1.0-smoothstep(density*0.8,density*2.8,depth))*(1.0-rim);
                    rgb *= half(1.0-inner*(0.035+0.025*strength));
                    half reflection = half(rim*(0.04+front*0.32+back*0.12));
                    return half4(mix(rgb*alpha,half3(1.0),reflection),alpha+(1.0-alpha)*reflection);
                }
                """);

        void draw(Canvas canvas, int width, int height, RectF bounds, float radius,
                float activity, float density, boolean dark, Source source) {
            sourceNode.setPosition(0, 0, width, height);
            RecordingCanvas recorded = sourceNode.beginRecording(width, height);
            try { source.draw(recorded); } finally { sourceNode.endRecording(); }
            shader.setFloatUniform("center", bounds.centerX(), bounds.centerY());
            shader.setFloatUniform("halfSize", bounds.width()/2, bounds.height()/2);
            shader.setFloatUniform("sourceSize", width, height);
            shader.setFloatUniform("radius", Math.min(radius, Math.min(bounds.width(), bounds.height())/2));
            shader.setFloatUniform("strength", activity);
            shader.setFloatUniform("density", density);
            shader.setFloatUniform("dark", dark ? 1 : 0);
            // RenderEffect snapshots uniforms: recreate after updating, not just on construction.
            sourceNode.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"));
            canvas.drawRenderNode(sourceNode);
        }

        void release() { sourceNode.discardDisplayList(); }
    }
}
