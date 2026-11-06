package dev.local.newsoul;

import android.content.SharedPreferences;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** Numbers and slider live in the original settings card, with no nested page. */
final class RegistrationRangeUi extends FrameLayout {
    private final SharedPreferences prefs;
    private final EditText low, high;
    private final LinearLayout numbers;
    private final RegistrationRangeView slider;
    private boolean binding;
    private boolean sliderGesture;
    private final int unitColor;
    RegistrationRangeUi(SettingsUi.MenuPanel panel, SharedPreferences preferences) {
        super(panel.context); prefs=preferences; unitColor=panel.secondary;
        setFocusableInTouchMode(true);
        numbers = new LinearLayout(panel.context);
        numbers.setGravity(Gravity.CENTER_VERTICAL);
        low = number(panel, "最少注册天数"); high=number(panel,"最多注册天数，留空表示不限");
        low.setHint("1 天"); high.setHint("+∞ 天");
        low.setHintTextColor(RegistrationRangeView.valueColor(panel)); high.setHintTextColor(RegistrationRangeView.valueColor(panel));
        addNumberCell(numbers, low, Gravity.START);
        View space=new View(panel.context); numbers.addView(space,new LinearLayout.LayoutParams(SettingsUi.dp(panel.context,24),1));
        addNumberCell(numbers, high, Gravity.END);
        slider=new RegistrationRangeView(panel);
        addView(slider,new FrameLayout.LayoutParams(-1,SettingsUi.dp(panel.context,48)));
        // The text targets sit above the slider in drawing/hit order. Their invisible
        // margins can overlap without stealing touches on the visible track.
        addView(numbers,new FrameLayout.LayoutParams(-1,-2));
        slider.onChange((min,max,commit)->{
            binding=true; low.clearFocus(); high.clearFocus(); binding=false;
            display(min,max); if(commit)save(min,max);
        });
        for(EditText input:new EditText[]{low,high}) {
            input.setOnFocusChangeListener((view,focus)->{
                if (binding) return;
                if (focus) {
                    try {
                        int value = RegistrationRangeRules.parse(input.getText().toString(), input == high);
                        input.setText(input == high && value == 0 ? "" : String.valueOf(value)); input.selectAll();
                    } catch (NumberFormatException ignored) { /* Preserve an invalid draft for correction. */ }
                } else commitInput();
            });
            input.setOnEditorActionListener((view,action,event)->{
                if(action==EditorInfo.IME_ACTION_DONE) {
                    if(commitInput()) {
                        android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                        if(keyboard!=null)keyboard.hideSoftInputFromWindow(input.getWindowToken(),0);
                        binding=true;input.clearFocus();binding=false;requestFocus();
                        display(Math.max(1,prefs.getInt(RegistrationDaysFilter.MIN,1)),prefs.getInt(RegistrationDaysFilter.MAX,0));
                    }
                    return true;
                }
                return false;
            });
        }
        Runnable refresh=()->{
            if(low.hasFocus() || high.hasFocus())return;
            int min=Math.max(1,prefs.getInt(RegistrationDaysFilter.MIN,1)), max=prefs.getInt(RegistrationDaysFilter.MAX,0);
            display(min,max);slider.setRange(min,max);
        };
        panel.addPreferenceRefresher(refresh);refresh.run();
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int childWidth = Math.max(0,width-getPaddingLeft()-getPaddingRight());
        numbers.measure(MeasureSpec.makeMeasureSpec(childWidth,MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
        slider.measure(MeasureSpec.makeMeasureSpec(childWidth,MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(SettingsUi.dp(getContext(),48),MeasureSpec.EXACTLY));
        int height = numbers.getMeasuredHeight()+SettingsUi.dp(getContext(),20)+getPaddingTop()+getPaddingBottom();
        setMeasuredDimension(resolveSize(width,widthSpec),resolveSize(height,heightSpec));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
        int left=getPaddingLeft(), top=getPaddingTop(), width=numbers.getMeasuredWidth();
        int trackTop=top+numbers.getMeasuredHeight()-SettingsUi.dp(getContext(),28);
        slider.layout(left,trackTop,left+width,trackTop+slider.getMeasuredHeight());
        numbers.layout(left,top,left+width,top+numbers.getMeasuredHeight());
    }
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
            // In the shared blank margin, a track-side press must not focus the number
            // sitting above it. Keep the selected gesture owner until up/cancel.
            sliderGesture = event.getY() >= slider.getTop()+SettingsUi.dp(getContext(),18);
        }
        if (!sliderGesture) return super.dispatchTouchEvent(event);
        android.view.MotionEvent local = android.view.MotionEvent.obtain(event);
        local.offsetLocation(-slider.getLeft(),-slider.getTop());
        boolean handled = slider.dispatchTouchEvent(local);
        local.recycle();
        if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP
                || event.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) sliderGesture=false;
        return handled;
    }
    private EditText number(SettingsUi.MenuPanel panel,String description) {
        EditText input=new EditText(panel.context); input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);input.setSingleLine(true);input.setSelectAllOnFocus(true);
        input.setTextSize(15);input.setTextColor(RegistrationRangeView.valueColor(panel));input.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        input.setMinHeight(SettingsUi.dp(panel.context,48));
        input.setIncludeFontPadding(false);
        input.setPadding(SettingsUi.dp(panel.context,12),0,SettingsUi.dp(panel.context,12),0);
        input.setMinEms(4);
        panel.appearance.clearBackground(input);
        // A full-size input target remains even though no box is painted.
        input.setContentDescription(description);
        return input;
    }
    private void addNumberCell(LinearLayout row, EditText input, int gravity) {
        input.setGravity(gravity | Gravity.CENTER_VERTICAL);
        FrameLayout cell = new FrameLayout(getContext());
        cell.addView(input, new FrameLayout.LayoutParams(-2, -2, gravity | Gravity.CENTER_VERTICAL));
        cell.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob) -> {
            int width = r-l;
            if (width > 0 && input.getMaxWidth() != width) input.setMaxWidth(width);
        });
        row.addView(cell, new LinearLayout.LayoutParams(0,-2,1));
    }
    private boolean commitInput() {
        int min,max;
        try { min=RegistrationRangeRules.parse(low.getText().toString(),false); }
        catch(NumberFormatException error) { low.setError("请输入至少为 1 的整数");return false; }
        try { max=RegistrationRangeRules.parse(high.getText().toString(),true); }
        catch(NumberFormatException error) { high.setError("请输入至少为 1 的整数，留空为不限");return false; }
        if(!AccountAutomationRules.validRange(min,max)){high.setError("上限不能小于下限");return false;}
        low.setError(null);high.setError(null);save(min,max);slider.setRange(min,max);
        if(!low.hasFocus())low.setText(styledValue(min,false));
        if(!high.hasFocus())high.setText(styledValue(max,true));
        return true;
    }
    private void display(int min,int max) {
        low.setError(null);high.setError(null);low.setText(styledValue(min,false));high.setText(styledValue(max,true));
    }
    private CharSequence styledValue(int value, boolean upper) {
        String text = RegistrationRangeRules.label(value,upper);
        android.text.SpannableString styled = new android.text.SpannableString(text);
        styled.setSpan(new android.text.style.ForegroundColorSpan(unitColor),text.length()-1,text.length(),
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return styled;
    }
    private void save(int min,int max) {
        if(min==Math.max(1,prefs.getInt(RegistrationDaysFilter.MIN,1)) && max==prefs.getInt(RegistrationDaysFilter.MAX,0))return;
        java.util.Map<String, Object> updates = new java.util.LinkedHashMap<>();
        updates.put(RegistrationDaysFilter.MIN, min);
        updates.put(RegistrationDaysFilter.MAX, max);
        PreferenceChanges.applyUpdates(prefs, updates);
    }
}
