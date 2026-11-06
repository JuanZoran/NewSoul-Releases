package dev.local.newsoul;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;

/** Search reuses the actual setting controls; location guidance never mutates a preference. */
final class SettingsSearch {
    interface Host {
        void prepare();
        List<Entry> entries();
        void select(int category);
        View tab(int category);
        ScrollView scroll(int category);
        void lens(int category, boolean moving);
        void searchProgress(float progress);
    }
    static final class Entry {
        final String title, path;
        final int category;
        final View target;
        final Checkable control;
        Entry(String title,String path,int category,View target) {
            this.title=title; this.path=path; this.category=category; this.target=target;
            this.control=target instanceof Checkable ? (Checkable)target : null;
        }
    }
    final FrameLayout header;
    final FrameLayout body;
    private final SettingsUi.MenuPanel panel;
    private final Host host;
    private final View pages, tabs;
    private final View pageShade;
    private final FrameLayout searchOverlay;
    private final LinearLayout floatingPanel;
    private int panelHeightLimit;
    private int imeInset;
    private int restingHeight;
    private int restingGravity, restingY;
    private final android.view.ViewTreeObserver.OnGlobalLayoutListener imeLayout = () -> refreshImeBounds();
    private View backgroundRoot;
    private final GlassTabTrack searchButton;
    private final Icon searchIcon;
    private final LinearLayout normal, results;
    private final ScrollView resultScroll;
    private final EditText input;
    private ValueAnimator transition;
    private float progress;
    private boolean searching, guiding, disposed;
    private int generation;
    private int guideCategory=-1;
    private final List<Runnable> pending = new ArrayList<>();
    private Spotlight spotlight;
    private View lifted;

    SettingsSearch(SettingsUi.MenuPanel panel,View tabs,View pages,Host host) {
        this.panel=panel; this.tabs=tabs; this.pages=pages; this.host=host;
        header=new FrameLayout(panel.context) {
            float x,y; boolean swipe;
            @Override public void requestDisallowInterceptTouchEvent(boolean disallow) {
                // A fitting tab strip has no horizontal scroll of its own; retain its left swipe.
                super.requestDisallowInterceptTouchEvent(disallow && (searching || tabs.canScrollHorizontally(1)));
            }
            @Override public boolean onInterceptTouchEvent(MotionEvent e) {
                if(guiding || searching) return false;
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN) { x=e.getX(); y=e.getY(); swipe=false; }
                if(e.getActionMasked()==MotionEvent.ACTION_MOVE) {
                    float dx=e.getX()-x,dy=e.getY()-y;
                    if(dx < -SettingsUi.dp(getContext(),32) && Math.abs(dx)>Math.abs(dy)*1.5f
                            && !tabs.canScrollHorizontally(1)) { swipe=true; return true; }
                }
                return false;
            }
            @Override public boolean onTouchEvent(MotionEvent e) {
                if(!swipe) return super.onTouchEvent(e);
                if(e.getActionMasked()==MotionEvent.ACTION_UP) { swipe=false; open(); }
                if(e.getActionMasked()==MotionEvent.ACTION_CANCEL) swipe=false;
                return true;
            }
        };
        header.setMinimumHeight(SettingsUi.dp(panel.context,56));
        normal=new LinearLayout(panel.context); normal.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout capsule=new FrameLayout(panel.context);
        capsule.addView(tabs,new FrameLayout.LayoutParams(-1,-2));
        normal.addView(capsule,new LinearLayout.LayoutParams(0,-2,1));
        searchButton=new GlassTabTrack(panel.context,panel.appearance,true);
        searchButton.useActionCapsule();
        searchIcon=new Icon(panel,"搜索设置",false);
        searchButton.addView(searchIcon,new FrameLayout.LayoutParams(SettingsUi.dp(panel.context,48),SettingsUi.dp(panel.context,48)));
        searchIcon.setOnClickListener(v->{ if(guiding) return; if(searching) close(); else open(); });
        LinearLayout.LayoutParams searchBounds=new LinearLayout.LayoutParams(-2,-2);
        searchBounds.leftMargin=SettingsUi.dp(panel.context,6);
        normal.addView(searchButton,searchBounds);
        header.addView(normal,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER_VERTICAL));
        // Keep the original track and action; only their contents change.
        input=new EditText(panel.context); input.setSingleLine(true); input.setTextSize(16);
        input.setTextColor(panel.foreground); input.setHintTextColor(panel.secondary);
        input.setHint("搜索设置"); input.setContentDescription("搜索设置");
        panel.appearance.apply(input,UiAppearance.Role.CONTENT);
        input.setMinHeight(SettingsUi.dp(panel.context,52));
        input.setPadding(SettingsUi.dp(panel.context,14),0,SettingsUi.dp(panel.context,8),0);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams inputBounds=new FrameLayout.LayoutParams(-1,-1);
        inputBounds.leftMargin=inputBounds.rightMargin=SettingsUi.dp(panel.context,4);
        capsule.addView(input,inputBounds);
        searchOverlay=new FrameLayout(panel.context) {
            @Override protected void onMeasure(int widthSpec,int heightSpec) {
                int[] anchor=new int[2],root=new int[2];
                header.getLocationOnScreen(anchor);
                if(getParent() instanceof View)((View)getParent()).getLocationOnScreen(root);
                android.graphics.Rect visible=new android.graphics.Rect();
                header.getWindowVisibleDisplayFrame(visible);
                FrameLayout.LayoutParams bounds=(FrameLayout.LayoutParams)floatingPanel.getLayoutParams();
                int pad=panel.appearance.searchSurfacePadding();
                bounds.leftMargin=anchor[0]-root[0]-pad;
                bounds.topMargin=anchor[1]-root[1]-pad;
                bounds.width=header.getWidth()+2*pad;
                FrameLayout.LayoutParams row=(FrameLayout.LayoutParams)normal.getLayoutParams();
                row.leftMargin=anchor[0]-root[0];
                row.topMargin=anchor[1]-root[1];
                row.width=header.getWidth();
                row.height=header.getHeight();
                floatingPanel.setPadding(pad,pad+header.getHeight(),pad,pad);
                panelHeightLimit=Math.max(SettingsUi.dp(getContext(),64),
                        Math.min(MeasureSpec.getSize(heightSpec)-bounds.topMargin,
                        visible.bottom-anchor[1])-SettingsUi.dp(getContext(),12));
                super.onMeasure(widthSpec,heightSpec);
            }
        };
        floatingPanel=new LinearLayout(panel.context) {
            @Override protected void onMeasure(int widthSpec,int heightSpec) {
                super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(panelHeightLimit,MeasureSpec.AT_MOST));
            }
        };
        floatingPanel.setOrientation(LinearLayout.VERTICAL);
        int surfacePad=panel.appearance.searchSurfacePadding();
        floatingPanel.setPadding(surfacePad,surfacePad,surfacePad,surfacePad);
        floatingPanel.setClickable(true);
        panel.appearance.searchSurface(floatingPanel);
        panel.appearance.searchPanelDepth(floatingPanel);
        body=new FrameLayout(panel.context); body.addView(pages,new FrameLayout.LayoutParams(-1,-1));
        pageShade=new View(panel.context) {
            @Override protected void onDraw(Canvas canvas) { canvas.drawColor(panel.dark ? 0x880b0d19 : 0x44000000); }
        };
        pageShade.setClickable(true);
        pageShade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        pageShade.setOnClickListener(v->close());
        searchOverlay.addView(pageShade,new FrameLayout.LayoutParams(-1,-1));
        searchOverlay.addView(floatingPanel,new FrameLayout.LayoutParams(-1,-2));
        searchOverlay.setVisibility(View.GONE);
        resultScroll=new ScrollView(panel.context);
        results=new LinearLayout(panel.context); results.setOrientation(LinearLayout.VERTICAL);
        int pad=SettingsUi.dp(panel.context,4); results.setPadding(pad,pad,pad,pad);
        resultScroll.addView(results);
        floatingPanel.addView(resultScroll,new LinearLayout.LayoutParams(-1,-2,1));
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int count,int after) {}
            public void onTextChanged(CharSequence s,int st,int before,int count) { if(searching) render(); }
            public void afterTextChanged(Editable e) {}
        });
        input.setOnEditorActionListener((v,action,event)-> { hideKeyboard(); return true; });
        header.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) {}
            public void onViewDetachedFromWindow(View v) { dispose(); }
        });
    }
    boolean isSearchSurfaceVisible() { return progress > 0; }

    void attach() {
        ViewGroup decor=(ViewGroup)panel.dialog.getWindow().getDecorView();
        backgroundRoot=decor.findViewById(android.R.id.content);
        decor.addView(searchOverlay,new ViewGroup.LayoutParams(-1,-1));
        // AlertDialog inspected the original content before this editor was attached.
        // Its no-editor flag otherwise prevents IME use even when EditText has focus.
        panel.dialog.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        panel.dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        searchOverlay.setOnApplyWindowInsetsListener((view,insets)-> {
            if(android.os.Build.VERSION.SDK_INT>=30) {
                int bottom=insets.isVisible(android.view.WindowInsets.Type.ime()) ? insets.getInsets(android.view.WindowInsets.Type.ime()).bottom : 0;
                if(imeInset!=bottom) { imeInset=bottom; view.requestLayout(); }
            }
            return insets;
        });
        searchOverlay.requestApplyInsets();
        decor.getViewTreeObserver().addOnGlobalLayoutListener(imeLayout);
        searchOverlay.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        panel.addPreferenceRefresher(()->{ if(searching) render(); });
        panel.dialog.setOnKeyListener((d,key,event)-> {
            if(key!=KeyEvent.KEYCODE_BACK || (!searching&&!guiding)) return false;
            if(event.getAction()==KeyEvent.ACTION_UP) { if(guiding) stopGuide(); else close(); }
            return true;
        });
    }
    private void open() {
        if(disposed||guiding||searching) return;
        android.view.Window window=panel.dialog.getWindow();
        if(restingHeight==0) {
            restingHeight=window.getDecorView().getHeight();
            android.view.WindowManager.LayoutParams attrs=window.getAttributes();
            restingGravity=attrs.gravity; restingY=attrs.y;
            int[] position=new int[2]; window.getDecorView().getLocationOnScreen(position);
            int topInset=0;
            if(android.os.Build.VERSION.SDK_INT>=30) topInset=panel.activity.getWindowManager().getCurrentWindowMetrics()
                    .getWindowInsets().getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars()).top;
            else { android.graphics.Rect frame=new android.graphics.Rect(); header.getWindowVisibleDisplayFrame(frame); topInset=frame.top; }
            attrs.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;
            attrs.y=Math.max(0,position[1]-topInset);
            window.setAttributes(attrs);
        }
        window.setLayout(window.getAttributes().width,restingHeight);
        ViewGroup.LayoutParams overlayBounds=searchOverlay.getLayoutParams();
        overlayBounds.height=restingHeight;
        searchOverlay.setLayoutParams(overlayBounds);
        // Reopening during the closing animation reuses the clean snapshot rather than
        // capturing the search overlay into its own backdrop.
        if (progress == 0) {
            int[] backdropOrigin = new int[2], decorOrigin = new int[2];
            header.getLocationOnScreen(backdropOrigin);
            View backdropRoot = window.getDecorView();
            backdropRoot.getLocationOnScreen(decorOrigin);
            int backdropPad = panel.appearance.searchSurfacePadding();
            panel.appearance.prepareSearchBackdrop(floatingPanel, panel.activity, backdropRoot,
                    backdropOrigin[0] - backdropPad, backdropOrigin[1] - backdropPad,
                    header.getWidth() + 2 * backdropPad,
                    Math.max(1, decorOrigin[1] + backdropRoot.getHeight() - backdropOrigin[1] + backdropPad));
        }
        host.prepare(); input.setText(""); searching=true; render();
        resultScroll.scrollTo(0,0);
        pages.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        resultScroll.setVisibility(View.VISIBLE);
        morph(true,()-> { if(searching&&!disposed) { input.requestFocus();
            ((InputMethodManager)panel.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                    .showSoftInput(input,InputMethodManager.SHOW_IMPLICIT); } });
    }
    private void close() {
        searching=false; hideKeyboard(); input.clearFocus();
        pages.setVisibility(View.VISIBLE);
        morph(false,()->restoreWindow());
    }
    private void restoreWindow() {
        if(disposed || restingHeight==0 || panel.dialog.getWindow()==null) return;
        android.view.Window window=panel.dialog.getWindow();
        android.view.WindowManager.LayoutParams attrs=window.getAttributes();
        attrs.gravity=restingGravity; attrs.y=restingY; attrs.height=ViewGroup.LayoutParams.WRAP_CONTENT;
        window.setAttributes(attrs);
        restingHeight=0;
    }
    private void refreshImeBounds() {
        if(disposed || panel.dialog.getWindow()==null) return;
        View decor=panel.dialog.getWindow().getDecorView();
        android.view.WindowInsets insets=decor.getRootWindowInsets();
        if(android.os.Build.VERSION.SDK_INT>=30 && insets!=null) {
            int bottom=insets.isVisible(android.view.WindowInsets.Type.ime())
                    ? insets.getInsets(android.view.WindowInsets.Type.ime()).bottom : 0;
            if(bottom!=imeInset) { imeInset=bottom; searchOverlay.requestLayout(); }
        }
    }
    private void hideKeyboard() {
        ((InputMethodManager)panel.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(input.getWindowToken(),0);
    }
    private void morph(boolean opening,Runnable done) {
        if(transition!=null) { ValueAnimator old=transition; transition=null; old.cancel(); }
        if(normal.getParent()==header) {
            // The placeholder preserves the page layout while the same row is lifted above the shade.
            header.setMinimumHeight(header.getHeight());
            header.removeView(normal);
            searchOverlay.addView(normal,new FrameLayout.LayoutParams(header.getWidth(),header.getHeight()));
            normal.setElevation(SettingsUi.dp(panel.context,4));
        }
        input.setVisibility(View.VISIBLE);
        searchOverlay.setVisibility(View.VISIBLE);
        searchOverlay.requestLayout();
        tabs.setImportantForAccessibility(opening?View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS:View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        input.setImportantForAccessibility(opening?View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        float end=opening?1:0;
        if(!ValueAnimator.areAnimatorsEnabled()) { paintProgress(end); finishMorph(opening); done.run(); return; }
        ValueAnimator next=ValueAnimator.ofFloat(progress,end); transition=next;
        next.setDuration(280); next.setInterpolator(new android.view.animation.DecelerateInterpolator());
        next.addUpdateListener(a->{if(transition==a) paintProgress((float)a.getAnimatedValue());});
        next.addListener(new android.animation.AnimatorListenerAdapter(){
            public void onAnimationEnd(android.animation.Animator a) {
                if(transition!=a) return; transition=null; paintProgress(end);
                finishMorph(opening); done.run();
            }
        }); next.start();
    }
    private void finishMorph(boolean opening) {
        if(opening) return;
        if(normal.getParent()==searchOverlay) {
            searchOverlay.removeView(normal);
            normal.setElevation(0);
            header.addView(normal,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER_VERTICAL));
        }
        input.setVisibility(View.INVISIBLE);
        searchOverlay.setVisibility(View.GONE);
        panel.appearance.clearSearchBackdrop(floatingPanel);
    }
    private void paintProgress(float value) {
        progress=value;
        // Tab labels slide away beneath the anchored search surface; closing restores them.
        host.searchProgress(value);
        searchIcon.morphGlyph(value);
        input.setTranslationX((1-value)*tabs.getWidth());
        input.setAlpha(value);
        // Keep the lifted row visible even at the first/last frame; restore its parent only on completion.
        searchOverlay.setVisibility(View.VISIBLE);
        pageShade.setAlpha(value);
        floatingPanel.setAlpha(value);
        if(backgroundRoot!=null) backgroundRoot.setImportantForAccessibility(value>0
                ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        resultScroll.setAlpha(value);
        resultScroll.setVisibility(value>0 ? View.VISIBLE : View.GONE);
        pages.setImportantForAccessibility(value>0 ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }
    private void render() {
        results.removeAllViews(); String query=input.getText().toString();
        if(SettingsSearchRules.normalize(query).isEmpty()) return;
        List<Entry> matches=new ArrayList<>();
        for(Entry e:host.entries()) if(SettingsSearchRules.score(query,e.title,e.path)>=0) matches.add(e);
        matches.sort(Comparator.comparingInt(e->SettingsSearchRules.score(query,e.title,e.path)));
        if(matches.isEmpty()) { results.addView(SettingsUi.label(panel.context,"未找到相关设置",14,panel.secondary)); return; }
        for(Entry e:matches) {
            LinearLayout row=new LinearLayout(panel.context); row.setGravity(Gravity.CENTER_VERTICAL);
            if(results.getChildCount()>0) {
                View divider=new View(panel.context); panel.appearance.searchResultDivider(divider);
                results.addView(divider,new LinearLayout.LayoutParams(-1,SettingsUi.dp(panel.context,1)));
            }
            row.setPadding(SettingsUi.dp(panel.context,12),SettingsUi.dp(panel.context,6),SettingsUi.dp(panel.context,4),SettingsUi.dp(panel.context,6));
            SettingsCardIcon symbol=new SettingsCardIcon(panel.context,e.title,panel.secondary);
            LinearLayout.LayoutParams symbolBounds=new LinearLayout.LayoutParams(SettingsUi.dp(panel.context,22),SettingsUi.dp(panel.context,22));
            symbolBounds.rightMargin=SettingsUi.dp(panel.context,10);
            row.addView(symbol,symbolBounds);
            LinearLayout labels=new LinearLayout(panel.context); labels.setOrientation(LinearLayout.VERTICAL);
            labels.addView(SettingsUi.label(panel.context,e.title,15,panel.foreground));
            labels.addView(SettingsUi.label(panel.context,e.path+(e.target.isEnabled()?"":" · 未就绪"),12,panel.secondary));
            row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
            if(e.control!=null) {
                SettingsUi.MenuSwitch toggle=new SettingsUi.MenuSwitch(panel);
                toggle.setMinWidth(SettingsUi.dp(panel.context,48)); toggle.setMinHeight(SettingsUi.dp(panel.context,48));
                toggle.setContentDescription(e.title); toggle.setEnabled(e.target.isEnabled()); toggle.setChecked(e.control.isChecked());
                toggle.setOnCheckedChangeListener((b,checked)-> {
                    if(!e.target.isEnabled()) return;
                    e.control.setChecked(checked); panel.refreshPreferenceViews();
                    toggle.setChecked(e.control.isChecked());
                });
                row.addView(toggle,new LinearLayout.LayoutParams(-2,-2));
            }
            Icon locate=new Icon(panel,"定位到"+e.title,true);
            locate.setOnClickListener(v->locate(e));
            row.addView(locate,new LinearLayout.LayoutParams(SettingsUi.dp(panel.context,48),SettingsUi.dp(panel.context,48)));
            LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(-1,-2); bounds.topMargin=SettingsUi.dp(panel.context,4);
            results.addView(row,bounds);
        }
    }
    private void locate(Entry entry) {
        stopGuide(); guiding=true; searching=false; hideKeyboard(); input.clearFocus();
        pages.setVisibility(View.VISIBLE);
        // Keep the search window's fixed bounds until the guide overlay is removed.
        // A MATCH_PARENT spotlight must not measure a centered WRAP_CONTENT dialog.
        morph(false,()->beginGuide(entry));
    }
    private void beginGuide(Entry entry) {
        if(!guiding||disposed||!panel.dialog.isShowing()) return;
        android.view.accessibility.AccessibilityManager access=(android.view.accessibility.AccessibilityManager)
                panel.context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE);
        if(!ValueAnimator.areAnimatorsEnabled() || access.isTouchExplorationEnabled()) {
            host.select(entry.category); later(350,()->{scrollTo(entry); entry.target.announceForAccessibility(entry.path+"，"+entry.title); stopGuide();}); return;
        }
        ViewGroup decor=(ViewGroup)panel.dialog.getWindow().getDecorView();
        spotlight=new Spotlight(); decor.addView(spotlight,new ViewGroup.LayoutParams(-1,-1));
        spotlight.focus(header);
        guideCategory=entry.category;
        host.select(entry.category);
        later(350,()-> {
            View tab=host.tab(entry.category); lifted=tab;
            tab.animate().scaleX(1.10f).scaleY(1.10f).setDuration(180).start();
            host.lens(entry.category,true);
        });
        later(850,()-> {
            resetLift(); host.lens(entry.category,false); scrollTo(entry);
        });
        later(1350,()-> {
            View row=locationView(entry);
            if(!row.isShown()) { stopGuide(); return; }
            if(row != entryView(entry)) Toast.makeText(panel.context,
                    "已定位到所属分组，当前条件下该设置未显示",Toast.LENGTH_SHORT).show();
            spotlight.focus(row); lifted=row;
            row.animate().scaleX(1.025f).scaleY(1.025f).setDuration(170).withEndAction(()-> {
                if(guiding) row.animate().scaleX(1).scaleY(1).setDuration(260)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(.8f)).start();
            }).start();
        });
        later(2350,this::stopGuide);
    }
    private void scrollTo(Entry e) {
        ScrollView scroll=host.scroll(e.category);
        View target=locationView(e);
        android.graphics.Rect rect=new android.graphics.Rect(0,0,target.getWidth(),target.getHeight());
        ((ViewGroup)scroll.getChildAt(0)).offsetDescendantRectToMyCoords(target,rect);
        scroll.scrollTo(0,Math.max(0,rect.top-(scroll.getHeight()-rect.height())/2));
    }
    private View entryView(Entry e) {
        return e.control!=null ? (View)e.target.getParent() : e.target;
    }
    private View locationView(Entry e) {
        View target=entryView(e);
        for(View v=target;v!=host.scroll(e.category)&&v.getParent() instanceof View;v=(View)v.getParent())
            if(v.getVisibility()!=View.VISIBLE) target=(View)v.getParent();
        return target;
    }
    private void later(long delay,Runnable action) {
        int token=generation;
        Runnable task=()->{if(!disposed&&guiding&&generation==token)action.run();};
        pending.add(task); header.postDelayed(task,delay);
    }
    private void resetLift() { if(lifted!=null) { lifted.animate().cancel(); lifted.setScaleX(1); lifted.setScaleY(1); lifted=null; } }
    private void stopGuide() {
        boolean wasGuiding=guiding;
        guiding=false; generation++;
        if(guideCategory>=0) { if(!disposed) host.lens(guideCategory,false); guideCategory=-1; }
        for(Runnable r:pending)header.removeCallbacks(r); pending.clear(); resetLift();
        if(spotlight!=null) { Spotlight old=spotlight; spotlight=null;
            if(!disposed && old.getParent() instanceof ViewGroup)((ViewGroup)old.getParent()).removeView(old); }
        if(wasGuiding) restoreWindow();
    }
    private void dispose() {
        if(disposed) return;
        disposed=true; stopGuide();
        if(panel.dialog.getWindow()!=null) panel.dialog.getWindow().getDecorView()
                .getViewTreeObserver().removeOnGlobalLayoutListener(imeLayout);
        panel.appearance.clearSearchBackdrop(floatingPanel);
        if(transition!=null){ValueAnimator old=transition;transition=null;old.cancel();}
        // Called from a descendant's detach callback while DecorView is traversing
        // its children. Removing an overlay here mutates that traversal's array and
        // can abort window teardown. Both overlays leave with the owning window.
    }
    private final class Spotlight extends View {
        private View target;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Spotlight(){ super(panel.context); setContentDescription("设置定位引导，点击结束"); setClickable(true); setOnClickListener(v->stopGuide()); }
        void focus(View view){target=view;invalidate();}
        @Override protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            if(visibility!=VISIBLE && guiding) stopGuide();
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas); if(target==null||!target.isAttachedToWindow())return;
            int[] a=new int[2],b=new int[2]; target.getLocationOnScreen(a);getLocationOnScreen(b);
            float pad=SettingsUi.dp(getContext(),3);
            RectF rect=new RectF(a[0]-b[0]-pad,a[1]-b[1]-pad,a[0]-b[0]+target.getWidth()+pad,a[1]-b[1]+target.getHeight()+pad);
            Path hole=new Path();hole.addRoundRect(rect,SettingsUi.dp(getContext(),14),SettingsUi.dp(getContext(),14),Path.Direction.CW);
            int save=canvas.save();canvas.clipOutPath(hole);canvas.drawColor(0xa6000000);canvas.restoreToCount(save);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(SettingsUi.dp(getContext(),1));paint.setColor(panel.accentText);
            canvas.drawRoundRect(rect,SettingsUi.dp(getContext(),14),SettingsUi.dp(getContext(),14),paint);
            if(guiding)postInvalidateOnAnimation();
        }
    }
    private static final class Icon extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); final boolean locate;
        private float morph;
        void morphGlyph(float value) {
            morph=value; setContentDescription(value>=.5f ? "取消搜索" : "搜索设置"); invalidate();
        }
        Icon(SettingsUi.MenuPanel panel,String label,boolean locate) {
            super(panel.context);this.locate=locate;p.setColor(panel.secondary);p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);setContentDescription(label);SettingsUi.buttonSemantics(this);
            panel.appearance.apply(this,UiAppearance.Role.ICON,999);
            setForeground(null); // discard the rectangular theme ripple installed by button semantics
            if (!locate) panel.appearance.searchActionFeedback(this);
        }
        @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();c.translate(getWidth()/2f,getHeight()/2f);float d=getResources().getDisplayMetrics().density;c.scale(d,d);
            if(locate){c.drawCircle(0,0,6.5f,p);c.drawCircle(0,0,2,p);c.drawLine(-10,0,-6.5f,0,p);c.drawLine(6.5f,0,10,0,p);c.drawLine(0,-10,0,-6.5f,p);c.drawLine(0,6.5f,0,10,p);}
            else {
                p.setAlpha(Math.round(255*(1-morph)));
                c.drawCircle(-1.5f,-1.5f,6,p);c.drawLine(3,3,8,8,p);
                p.setAlpha(Math.round(255*morph));
                c.drawLine(-6,-6,6,6,p);c.drawLine(6,-6,-6,6,p);
                p.setAlpha(255);
            }c.restore();}
    }
}
