package dev.local.newsoul;

import android.app.Activity;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;

/** Synchronizes only Soul's settings document, using its existing theme classes. */
final class SettingsThemeSync {
    private static WebView web(View view) {
        if (view instanceof WebView) return (WebView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            WebView found = web(((ViewGroup) view).getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    static void observe(Activity activity, View content, TextView title) {
        // Poll only while attached and visible: handles asynchronous H5 loads without
        // replacing Soul's WebViewClient, JS bridge, or navigation callbacks.
        Runnable tick = new Runnable() {
            @Override public void run() {
                if (activity.isFinishing() || activity.isDestroyed() || !content.isAttachedToWindow()) return;
                if (content.isShown() && "设置".contentEquals(title.getText())) {
                    WebView browser = web(content);
                    if (browser != null) {
                        boolean dark = Color.luminance(title.getCurrentTextColor()) > 0.45;
                        browser.evaluateJavascript(script(dark), result -> {
                            if ("true".equals(result)) Log.i("SoulHook102", "settings web theme synchronized; dark=" + dark);
                        });
                    }
                }
                content.postDelayed(this, 750);
            }
        };
        content.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View view) { content.removeCallbacks(tick); content.post(tick); }
            public void onViewDetachedFromWindow(View view) { content.removeCallbacks(tick); }
        });
        if (content.isAttachedToWindow()) content.post(tick);
    }

    static String script(boolean dark) {
        // Exact page scope; do not recolor other documents sharing H5Activity.
        return "(()=>{if(location.origin!=='https://app.soulapp.cn'||location.pathname!='/setting/'||!document.body)return false;"
                + "const want='" + (dark ? "theme-night" : "theme-daytime") + "';"
                + "const other='" + (dark ? "theme-daytime" : "theme-night") + "';"
                + "const b=document.body;const changed=!b.classList.contains(want)||b.classList.contains(other);"
                + "if(changed){b.classList.remove(other);b.classList.add(want);}return changed;})()";
    }
}
