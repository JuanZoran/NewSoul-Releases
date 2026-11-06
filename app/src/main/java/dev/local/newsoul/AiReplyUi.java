package dev.local.newsoul;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;
import org.json.*;

/** User initiated generation; explicit candidate taps send through the host text path. */
final class AiReplyUi {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORK = Executors.newFixedThreadPool(2);
    private static final Map<Activity, Session> ACTIVE = new WeakHashMap<>();
    private static final Map<Activity, AutomaticSend> AUTOMATIC_SENDS = new WeakHashMap<>();
    private static final int ACCENT = 0xff00b5a2;

    static void observe(Activity activity) {
        AiReplyEntry.observe(activity);
        AiOfficialSuggestionTrigger.observe(activity);
    }

    static void open(Activity activity) {
        if(gone(activity)) return;
        AiAutomaticSuggestionCard.closeFor(activity);
        Session existing=ACTIVE.get(activity);
        if(existing != null) {existing.close();return;}
        boolean dark=BottomSettingsEntry.dark(activity);
        if(!AiReplyConfig.hasSelectedKey(activity)) { showSettings(activity,dark);return; }
        try {
            android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)
                    activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if(keyboard!=null) keyboard.hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(),0);
            Session session=new Session(activity,dark);
            ACTIVE.put(activity,session);session.start();
        } catch(Exception ignored) {
            cancelFor(activity);toast(activity,"当前聊天输入区域未就绪，请重新进入会话");
        }
    }

    static void cancelFor(Activity activity) {
        AUTOMATIC_SENDS.remove(activity);
        AiAutomaticSuggestionCard.closeFor(activity);
        AiKeyTestControl.cancelFor(activity);
        AiOfficialSuggestionTrigger.cancelFor(activity);
        AiAutomaticSuggestions.endVisit(activity);
        Session session=ACTIVE.remove(activity);
        if(session!=null) session.close();
    }

    /** Called only by an explicit tap on a plugin-owned automatic recommendation. */
    static void sendAutomaticSuggestion(Activity activity,AiChatContext.Snapshot snapshot,String text,
                                        java.util.function.Consumer<String> finished) {
        if(gone(activity)||AUTOMATIC_SENDS.containsKey(activity))return;
        Session visible=ACTIVE.get(activity);
        if(visible!=null&&visible.sending){
            if(finished!=null)finished.accept("上一组回复仍在发送，请稍候");return;
        }
        AutomaticSend send=new AutomaticSend(activity,snapshot,text,finished);
        AUTOMATIC_SENDS.put(activity,send);
        WORK.execute(()->{
            boolean current;
            try{current=AiChatContext.isCurrent(activity,snapshot);}
            catch(Exception ignored){current=false;}
            boolean verified=current;
            MAIN.post(()->{
                if(!send.alive())return;
                if(!verified){send.finish("聊天已有变化，请重新生成回复");return;}
                send.start();
            });
        });
    }

    private static final class AutomaticSend {
        final Activity activity;
        final AiChatContext.Snapshot snapshot;
        final String text;
        final java.util.function.Consumer<String> finished;
        AutomaticSend(Activity activity,AiChatContext.Snapshot snapshot,String text,
                      java.util.function.Consumer<String> finished) {
            this.activity=activity;this.snapshot=snapshot;this.text=text;this.finished=finished;
        }
        boolean alive(){
            if(gone(activity)||AUTOMATIC_SENDS.get(activity)!=this)return false;
            EditText input=composer(activity);
            if(input==null||input.length()!=0){finish("输入框已有变化，发送已停止，请核对聊天记录");return false;}
            return true;
        }
        void start(){
            try{
                if(!alive())return;
                if(!AiChatContext.sameVisibleConversation(activity,snapshot)){
                    finish("聊天已有变化，请重新生成回复");return;
                }
                List<String> parts=AiSendRules.parts(text);
                AiReplySender sender=new AiReplySender(activity,snapshot);
                new AiSendSequence(sender,new AiSendSequence.Clock(){
                    public long now(){return android.os.SystemClock.uptimeMillis();}
                    public void later(Runnable action,long delay){MAIN.postDelayed(action,delay);}
                },this::alive,new AiSendSequence.Listener(){
                    public void progress(int index,int total){}
                    public void completed(int total){finish("已发送 "+total+" 条回复");}
                    public void stopped(int confirmed,int total,String reason){
                        android.util.Log.w("SoulHook102","AI automatic candidate send stopped: "+reason
                                +"; confirmed="+confirmed+"; total="+total);
                        finish("发送已停止 · 已确认 "+confirmed+" / "+total+" 条\n请先核对聊天记录，避免重复发送");
                    }
                },parts).start();
            }catch(Exception error){
                android.util.Log.w("SoulHook102","AI automatic candidate prepare failed: "+AiReplySender.failureCode(error));
                finish("发送准备失败，尚未提交消息；请重新生成回复");
            }
        }
        void finish(String message){
            if(AUTOMATIC_SENDS.get(activity)!=this)return;
            AUTOMATIC_SENDS.remove(activity);
            if(!gone(activity)&&finished!=null)finished.accept(message);
        }
    }

    /** Compact controls embedded in the shared chat settings panel. */
    static void addSettingsRows(SettingsUi.MenuPanel panel, LinearLayout body) {
        Activity activity = panel.activity;
        LinearLayout configuration = new LinearLayout(panel.context);
        configuration.setOrientation(LinearLayout.VERTICAL);
        configuration.setTag(panel);
        // Keep configuration visible and editable even while AI replies are disabled.
        body.addView(configuration, new LinearLayout.LayoutParams(-1, -2));

        AiModelConfig[] models = {AiModelConfig.DEEPSEEK, AiModelConfig.MIMO, AiModelConfig.CUSTOM};
        TextView[] names = new TextView[models.length];
        RadioButton[] choices = new RadioButton[models.length];
        LinearLayout[] rows = new LinearLayout[models.length];
        TextView[] tests = new TextView[models.length];
        AiKeyTestControl[] testControls = new AiKeyTestControl[models.length];
        Runnable refresh = () -> {
            AiModelConfig selected = AiReplyConfig.selectedModel(activity);
            for (int i = 0; i < models.length; i++) {
                if(testControls[i]==null)continue;
                AiKeyTestControl test = testControls[i];
                test.refresh();
                String state = test.status();
                android.text.SpannableString text = new android.text.SpannableString(models[i].displayName + "\n" + state);
                int start = models[i].displayName.length() + 1;
                int color=test.passed()?panel.appearance.successColor()
                        :test.failed()?panel.appearance.errorColor():panel.secondary;
                text.setSpan(new android.text.style.ForegroundColorSpan(color), start, text.length(), 0);
                text.setSpan(new android.text.style.RelativeSizeSpan(.85f), start, text.length(), 0);
                names[i].setText(text);
                tests[i].setVisibility(AiReplyConfig.ready(activity,models[i])?View.VISIBLE:View.GONE);
                tests[i].setText(test.running()?"测试中":"测试");
                tests[i].setEnabled(!test.running());
                tests[i].setTextColor(test.running()?panel.secondary:panel.accentText);
                choices[i].setChecked(selected.selectionId.equals(models[i].selectionId));
                rows[i].setSelected(selected.selectionId.equals(models[i].selectionId));
                rows[i].setContentDescription(models[i].displayName + "，" + state
                        + (models[i].isCustom()?"，点击选择，长按配置接口":"，点击选择，长按管理密钥"));
            }
        };
        for (int i = 0; i < models.length; i++) {
            final AiModelConfig model = models[i];
            LinearLayout row = new LinearLayout(panel.context);
            rows[i] = row;
            panel.appearance.apply(row,UiAppearance.Role.RADIO_ROW);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(panel.context, 56));
            TextView name = label(panel, "", 15);
            names[i] = name;
            name.setPadding(0, dp(panel.context, 8), dp(panel.context, 8), dp(panel.context, 8));
            name.setSingleLine(false);
            name.setEllipsize(null);
            name.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            final int index=i;
            TextView test=settingsButton(panel,"测试",()->testControls[index].start());
            tests[i]=test;
            test.setGravity(Gravity.CENTER);
            test.setPadding(dp(panel.context,8),0,dp(panel.context,8),0);
            test.setMinWidth(dp(panel.context,64));
            test.setContentDescription("测试 "+model.displayName+" 密钥可用性");
            row.addView(test,new LinearLayout.LayoutParams(-2,-2));
            RadioButton choice = new RadioButton(panel.context);
            choices[i] = choice;
            panel.appearance.checkControl(choice);
            choice.setMinWidth(dp(panel.context, 48));
            choice.setMinHeight(dp(panel.context, 48));
            choice.setContentDescription(model.displayName);
            row.addView(choice, new LinearLayout.LayoutParams(-2, -2));
            View.OnClickListener select = v -> {
                if (!AiReplyConfig.selectedModel(activity).selectionId.equals(model.selectionId))
                    PluginPreferences.get(activity).edit().putString(AiReplyConfig.MODEL, model.selectionId).apply();
                refresh.run();
            };
            View.OnLongClickListener edit = v -> {
                showKeyEditor(activity, panel.dark, model, refresh);
                return true;
            };
            row.setOnClickListener(select); row.setOnLongClickListener(edit);
            name.setOnClickListener(select); name.setOnLongClickListener(edit);
            choice.setOnClickListener(select); choice.setOnLongClickListener(edit);
            testControls[i]=new AiKeyTestControl(activity,row,model,refresh);
            configuration.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        refresh.run();
        panel.addPreferenceRefresher(refresh);
        SettingsUi.MenuSwitch automatic=SettingsUi.addSwitch(configuration,PluginPreferences.get(activity),
                AiReplyConfig.AUTO_SUGGESTIONS,"自动建议",
                "在官方出现开场或复聊建议时请求当前模型\n会提供聊天资料、我的资料及按规则选中的图片\n候选需手动发送",
                panel.foreground,panel.secondary);
        SettingsUi.bindAvailability(panel,automatic,AiOfficialSuggestionTrigger::ready);
        TextView profile = settingsButton(panel, "我的资料", () -> showProfileEditor(activity, panel.dark));
        profile.setContentDescription("我的资料，点击编辑");
        TextView images = settingsButton(panel, "图片选择规则", () -> showImageSettings(activity, panel.dark));
        images.setContentDescription("图片选择规则，点击设置");
        SettingsUi.addInfoActionPair(panel, configuration, profile, images);
    }

    private static TextView settingsButton(SettingsUi.MenuPanel panel, String text, Runnable action) {
        TextView view = button(panel, text, action);
        panel.appearance.apply(view, UiAppearance.Role.ACTION);
        SettingsUi.buttonSemantics(view);
        return view;
    }

    /** First-time setup opens the selected provider's configuration editor directly. */
    static void showSettings(Activity activity, boolean dark) {
        showKeyEditor(activity, dark, AiReplyConfig.selectedModel(activity));
    }

    private static void showKeyEditor(Activity activity, boolean dark, AiModelConfig model) {
        showKeyEditor(activity, dark, model, () -> {});
    }

    private static void showKeyEditor(Activity activity, boolean dark, AiModelConfig model, Runnable changed) {
        SettingsUi.MenuPanel panel = new SettingsUi.MenuPanel(activity, dark);
        panel.help = model.isCustom() ? "密钥在本机加密保存"
                : "保存后生效，留空保留原密钥\n密钥在本机加密保存";
        panel.body.addView(label(panel, model.displayName, 15));
        SharedPreferences prefs = PluginPreferences.get(activity);
        EditText endpoint = model.isCustom() ? field(panel, "接口地址，如 http://192.168.1.2:1234/v1", true) : null;
        EditText modelName = model.isCustom() ? field(panel, "模型名", true) : null;
        if (model.isCustom()) {
            panel.help += "\n地址支持基础路径或完整 chat/completions 路径";
            endpoint.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            endpoint.setContentDescription("接口地址");
            endpoint.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2048)});
            endpoint.setText(prefs.getString(AiReplyConfig.CUSTOM_ENDPOINT, ""));
            modelName.setContentDescription("模型名");
            modelName.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256)});
            modelName.setText(prefs.getString(AiReplyConfig.CUSTOM_MODEL, ""));
        }
        String initialEndpoint = endpoint == null ? "" : endpoint.getText().toString();
        String initialModel = modelName == null ? "" : modelName.getText().toString();
        EditText key = keyField(panel, AiReplyConfig.hasKey(activity, model)
                ? "已保存；留空保留，填写则替换" : model.isCustom() ? "API Key（可选）" : "填写 " + model.serviceName() + " 密钥（API Key）");
        String initialKey = key.getText().toString();
        TextView status = label(panel, "", 12);
        status.setVisibility(View.GONE);
        panel.body.addView(status);
        final boolean[] clearOnSave = {false};
        TextView clear = button(panel, "清除已保存的密钥", () -> {
            clearOnSave[0] = true;
            key.setText("");
            key.setHint(model.isCustom() ? "API Key（可选）" : "填写 " + model.serviceName() + " 密钥（API Key）");
            status.setText("保存后清除密钥；填写新密钥则替换\n返回可取消");
            status.setVisibility(View.VISIBLE);
        });
        SettingsUi.buttonSemantics(clear);
        panel.body.addView(clear);
        panel.protectDraft(() -> clearOnSave[0] || !initialKey.equals(key.getText().toString())
                || (endpoint != null && (!initialEndpoint.equals(endpoint.getText().toString())
                || !initialModel.equals(modelName.getText().toString()))));
        panel.show(model.isCustom() ? "自定义接口" : model.serviceName() + " 密钥", "保存");
        panel.dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        panel.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String entered = key.getText().toString().trim();
            try {
                if (model.isCustom()) {
                    try { AiModelConfig.normalizeEndpoint(endpoint.getText().toString()); }
                    catch (IllegalArgumentException error) { endpoint.setError(error.getMessage()); return; }
                    try { AiModelConfig.custom(endpoint.getText().toString(), modelName.getText().toString()); }
                    catch (IllegalArgumentException error) { modelName.setError(error.getMessage()); return; }
                    AiReplyConfig.saveCustom(activity, endpoint.getText().toString(),
                            modelName.getText().toString(), entered, clearOnSave[0]);
                } else if (!entered.isEmpty()) AiReplyConfig.saveKey(activity, model, entered);
                else if (clearOnSave[0]) AiReplyConfig.clearKey(activity, model);
                changed.run();
                key.setText("");
                panel.dialog.dismiss();
                toast(activity, model.isCustom() ? "接口已保存" : !entered.isEmpty() ? "密钥已保存"
                        : clearOnSave[0] ? "密钥已清除" : "设置已保留");
            } catch (Exception error) {
                key.setError("密钥格式不正确或保存失败，请检查后重试");
            }
        });
        panel.dialog.setOnDismissListener(d -> key.setText(""));
    }

    private static void showProfileEditor(Activity activity, boolean dark) {
        SettingsUi.MenuPanel panel = new SettingsUi.MenuPanel(activity, dark);
        panel.help = "可填兴趣、经历、关系目标和说话习惯，不清楚可留空\n"
                + "生成回复时提供给当前模型";
        EditText profile = field(panel, "填写我的资料", false);
        profile.setText(PluginPreferences.get(activity).getString(AiReplyConfig.PROFILE, ""));
        String initialProfile = profile.getText().toString();
        profile.setMinLines(4);
        profile.setMaxLines(8);
        profile.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8000)});
        panel.protectDraft(() -> !initialProfile.equals(profile.getText().toString()));
        panel.show("我的资料", "保存");
        panel.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (PluginPreferences.get(activity).edit()
                    .putString(AiReplyConfig.PROFILE, profile.getText().toString().trim()).commit()) {
                panel.dialog.dismiss();
                toast(activity, "我的资料已保存");
            } else profile.setError("保存失败，请重试");
        });
    }

    private static String imageRuleLabel(int value) {
        if (value == 0) return "不自动选择";
        if (value == 2) return "仅有 1 张时自动选中";
        return "不超过 " + (value - 1) + " 张时自动全选";
    }

    private static void showImageSettings(Activity activity, boolean dark) {
        SettingsUi.MenuPanel panel = new SettingsUi.MenuPanel(activity, dark);
        panel.help = "可选图片总数不超过上限时自动选中，生成前可调整\n"
                + "包括表情包、聊天、帖子和主页图片";
        RadioGroup options = new RadioGroup(panel.context);
        int current = AiReplyConfig.autoImageThreshold(activity);
        for (int value : new int[]{0, 2, 3, 4, 5}) {
            RadioButton choice = new RadioButton(panel.context);
            choice.setId(View.generateViewId());
            choice.setTag(value);
            choice.setText(imageRuleLabel(value));
            choice.setTextSize(14);
            choice.setTextColor(panel.foreground);
            choice.setMinHeight(dp(panel.context, 48));
            panel.appearance.checkControl(choice);
            options.addView(choice, new RadioGroup.LayoutParams(-1, -2));
            choice.setChecked(value == current);
        }
        panel.body.addView(options);
        TextView error = label(panel, "", 12);
        panel.body.addView(error);
        panel.protectDraft(() -> {
            View selected = options.findViewById(options.getCheckedRadioButtonId());
            return selected != null && (Integer) selected.getTag() != current;
        });
        panel.show("图片选择规则", "保存");
        panel.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            View selected = options.findViewById(options.getCheckedRadioButtonId());
            if (selected == null) return;
            int value = (Integer) selected.getTag();
            if (PluginPreferences.get(activity).edit()
                    .putInt(AiReplyConfig.AUTO_IMAGE_THRESHOLD, value).commit()) {
                panel.dialog.dismiss();
                toast(activity, "图片设置已保存");
            } else error.setText("保存失败，请重试");
        });
    }

    private static final class Session {
        final Activity activity;
        final SettingsUi.MenuPanel panel;
        final TextView status, generate, imageButton, detailsButton, intentButton;
        final EditText intent;
        final LinearLayout resultBody, footer, content;
        AiReplyDock dock;
        final AiReplyBoard board;
        AiWritingRules.Mode mode=AiWritingRules.Mode.REPLY;
        AiWritingRules.Style replyStyle=AiWritingRules.Style.DEFAULT, rewriteStyle=AiWritingRules.Style.CONSIDERATE;
        String undoDraft, appliedDraft, sentComposerDraft;
        boolean sendBlocked;
        boolean sending;
        final EditText hostComposer;
        final android.text.TextWatcher draftWatcher;
        final Set<String> selected=new LinkedHashSet<>();
        final List<View> controls=new ArrayList<>();
        final List<AlertDialog> children=new ArrayList<>();
        String capturedDraft;
        String imageDelivery="";
        final AiMaterialRules.Selection materials=new AiMaterialRules.Selection();
        String lastProvidedProfile;
        JSONObject lastProvidedContext;
        List<AiMaterialRules.Entry> lastProvidedMaterials=java.util.Collections.emptyList();
        AiChatContext.Handle collection;
        AiChatContext.Snapshot snapshot;
        AiReplyClient.Cancellation request;
        volatile boolean closed;
        boolean busy;
        int revision;
        AiReplyRules.Result lastResult;

        Session(Activity activity,boolean dark) {
            this.activity=activity;panel=new SettingsUi.MenuPanel(activity,dark);
            capturedDraft=composer(activity).getText().toString();
            panel.help="点“换一组”时向当前模型提供聊天资料、我的资料及所选图片和素材\n"
                    +"短候选点按发送，截断候选先看全文，长按可编辑，多段逐条发送\n点击回复思路查看互动分析";
            status=label(panel,"正在读取聊天…",12);
            status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            status.setPadding(dp(activity,12),0,dp(activity,12),dp(activity,6));
            status.setMaxLines(2);
            resultBody=new LinearLayout(panel.context);resultBody.setOrientation(LinearLayout.VERTICAL);
            emptyState("准备一组适合当前话题的回复", "点击右上角换一组；补充想法在设置中");
            // Unattached editor retains the intention; its dialog is opened only when requested.
            intent=new EditText(panel.context);
            footer=new LinearLayout(panel.context);footer.setGravity(Gravity.CENTER_VERTICAL);
            footer.setPadding(dp(activity,6),0,dp(activity,6),0);
            imageButton=chip(panel,"图片 · 0",this::showImages);
            detailsButton=chip(panel,"回复思路",this::showDetails);
            intentButton=chip(panel,"补充想法",this::showIntent);
            TextView refresh=chip(panel,"更新资料",()->{if(!busy)load();});
            footer.addView(imageButton);footer.addView(detailsButton);footer.addView(refresh);
            controls.add(imageButton);controls.add(detailsButton);controls.add(refresh);controls.add(intentButton);
            generate=primary(panel,"生成回复",this::generate);generate.setTextSize(12);generate.setEnabled(false);generate.setAlpha(.5f);
            board=new AiReplyBoard(panel,resultBody,status,generate,new AiReplyBoard.Actions() {
                public void mode(AiWritingRules.Mode value){changeMode(value);}
                public void style(AiWritingRules.Style value){changeStyle(value);}
                public void settings(){showBoardSettings();}
                public void details(){showDetails();}
                public void restore(){restoreDraft();}
                public void clear(){EditText input=composer(activity);if(!busy&&input!=null)input.setText("");}
                public void send(){EditText input=composer(activity);if(snapshot!=null&&input!=null)sendReply(input.getText().toString(),snapshot,true);}
            });
            content=board;
            hostComposer=composer(activity);
            draftWatcher=new android.text.TextWatcher(){
                public void beforeTextChanged(CharSequence value,int start,int count,int after){}
                public void onTextChanged(CharSequence value,int start,int before,int count){updateDraftActions();}
                public void afterTextChanged(android.text.Editable value){}
            };
            hostComposer.addTextChangedListener(draftWatcher);updateDraftActions();
        }
        void start() {
            dock=AiReplyDock.attach(activity,content,this::close);
            if(dock==null) {
                showCompactFallback(panel,content);
                panel.dialog.setOnDismissListener(d -> close());
            }
            load();
        }
        void changeMode(AiWritingRules.Mode value) {
            if(closed||busy||mode==value)return;
            mode=value;lastResult=null;board.result(null);board.mode(mode,currentStyle());
            emptyState("点击右上角换一组", "");
            status.setText(mode==AiWritingRules.Mode.REWRITE?"在输入框写下原话，选择风格后点击换一组":"");
            updateDraftActions();
        }
        AiWritingRules.Style currentStyle(){return mode==AiWritingRules.Mode.REPLY?replyStyle:rewriteStyle;}
        void changeStyle(AiWritingRules.Style value) {
            if(closed||busy)return;
            if(mode==AiWritingRules.Mode.REPLY)replyStyle=value;else rewriteStyle=value;
            lastResult=null;board.result(null);board.selected(value);
            emptyState("点击右上角换一组", "");
            status.setText("已选"+value.label+"，点击换一组"+(mode==AiWritingRules.Mode.REWRITE?"改写":"生成"));
            updateDraftActions();
        }
        void updateDraftActions() {
            if(board==null)return;EditText input=composer(activity);String value=input==null?"":input.getText().toString();
            board.draftActions(undoDraft!=null && appliedDraft!=null && appliedDraft.equals(value),
                    !value.trim().isEmpty(),snapshot!=null&&!sendBlocked&&!value.trim().isEmpty(),busy);
        }
        void restoreDraft() {
            if(closed||busy)return;EditText input=composer(activity);
            if(input!=null&&appliedDraft!=null&&appliedDraft.equals(input.getText().toString())) {
                input.setText(undoDraft);input.setSelection(input.length());appliedDraft=null;undoDraft=null;updateDraftActions();
            }
        }
        void showBoardSettings() {
            if(closed||busy)return;
            SettingsUi.MenuPanel settings=new SettingsUi.MenuPanel(activity,panel.dark);
            settings.help="选择方式或风格后，点“换一组”生成\n候选下方的理由与观察仅供参考，不随候选发送";
            LinearLayout body=SettingsUi.section(settings,"本轮输入");
            SettingsUi.addInfoActionPair(settings,body,chip(settings,"补充想法",()->{settings.dialog.dismiss();showIntent();}),
                    chip(settings,"图片 · "+selected.size(),()->{settings.dialog.dismiss();showImages();}));
            SettingsUi.addInfoActionPair(settings,body,chip(settings,"本轮资料",()->{settings.dialog.dismiss();showSources();}),
                    chip(settings,"更新资料",()->{settings.dialog.dismiss();load();}));
            LinearLayout materialActions=SettingsUi.section(settings,"素材");
            SettingsUi.addInfoActionPair(settings,materialActions,
                    chip(settings,"真实素材 · "+materials.size(),()->{settings.dialog.dismiss();showMaterials(AiMaterialRules.Kind.REAL);}),
                    chip(settings,"灵感草稿",()->{settings.dialog.dismiss();showMaterials(AiMaterialRules.Kind.INSPIRATION);}));
            LinearLayout config=SettingsUi.section(settings,"模型与我的资料");
            addSettingsRows(settings,config);
            settings.show("灵感回复设置","关闭");children.add(settings.dialog);
            settings.dialog.setOnDismissListener(d->children.remove(settings.dialog));
        }
        void showMaterials(AiMaterialRules.Kind kind) {
            if(closed||busy)return;
            AiMaterialUi.show(panel,kind,materials,()->{
                if(closed||kind!=AiMaterialRules.Kind.REAL)return;
                try {materials.reconcile(AiMaterialStore.read(activity,AiMaterialRules.Kind.REAL));}catch(Exception ignored){}
                lastResult=null;board.result(null);emptyState("资料已更新，点击换一组","");
            },dialog->children.add(dialog),()->!closed&&!busy);
        }
        void showIntent() {
            if(closed||busy)return;
            SettingsUi.MenuPanel detail=new SettingsUi.MenuPanel(activity,panel.dark);
            addQuestions(detail);
            LinearLayout card=infoCard(detail,"这轮想怎么聊","");
            EditText editor=new EditText(detail.context);
            editor.setHint("例如：接着聊刚才的旅行，或补充真实的时间安排");
            editor.setTextSize(14);editor.setTextColor(detail.foreground);editor.setHintTextColor(detail.secondary);
            editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            detail.appearance.apply(editor,UiAppearance.Role.CONTENT);editor.setPadding(0,dp(activity,8),0,dp(activity,8));
            card.addView(editor,new LinearLayout.LayoutParams(-1,-2));
            editor.setMinLines(3);editor.setMaxLines(6);
            editor.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});
            editor.setText(intent.getText());editor.setSelection(editor.length());
            TextView save=chip(detail,"保存想法",()->{

                intent.setText(editor.getText());
                intentButton.setText(intent.length()==0?"补充想法":"已补充想法");
                detail.dialog.dismiss();
            });
            detail.showAiBottom("补充想法",save);children.add(detail.dialog);
            detail.dialog.setOnDismissListener(d->children.remove(detail.dialog));
        }
        void addQuestions(SettingsUi.MenuPanel detail) {
            if(lastResult==null||lastResult.questions.isEmpty())return;
            infoCard(detail,"需要你补充",String.join("\n\n",lastResult.questions));
        }
        void load() {
            if(closed || busy) return;
            if(collection!=null) collection.cancel();
            EditText current=composer(activity);
            if(current==null){
                revision++;snapshot=null;lastResult=null;
                generate.setEnabled(false);generate.setAlpha(.5f);
                status.setText("原输入框已不可用，请重新打开聊天");
                emptyState("当前聊天不可用", "关闭面板，重新进入聊天后再打开灵感回复");
                return;
            }
            capturedDraft=current.getText().toString();
            materials.clear();lastProvidedProfile=null;lastProvidedContext=null;lastProvidedMaterials=java.util.Collections.emptyList();
            closeChildren();
            int token=++revision;snapshot=null;selected.clear();lastResult=null;imageDelivery="";sendBlocked=false;undoDraft=null;appliedDraft=null;updateDraftActions();
            intentButton.setText(intent.length()==0?"补充想法":"已补充想法");
            imageButton.setText("图片 · 0");generate.setEnabled(false);generate.setAlpha(.5f);generate.setText("");
            emptyState("正在读取聊天", "读取完成后可以生成回复，也可以先补充你的想法");
            status.setText("正在读取聊天…");
            collection=AiChatContext.collect(activity,new AiChatContext.Callback(){
                public void onReady(AiChatContext.Snapshot captured) {
                    if(!alive(token))return;
                    snapshot=captured;
                    // Read-only preflight: diagnose sender binding before the user attempts a send.
                    try {new AiReplySender(activity,captured);android.util.Log.i("SoulHook102","AI send readiness ok");}
                    catch(Exception error){android.util.Log.w("SoulHook102","AI send readiness failed: "+AiReplySender.failureCode(error));}
                    selected.clear();
                    if(AiImageSelectionRules.autoSelect(captured.images.size(),AiReplyConfig.autoImageThreshold(activity)))
                        for(AiChatContext.Media media:captured.images)selected.add(media.id);
                    imageButton.setText("图片 · "+selected.size());
                    status.setText(mode==AiWritingRules.Mode.REWRITE?"选择风格后，点击换一组改写输入框原话":"");
                    emptyState("点击右上角换一组", "");
                    board.result(null);updateDraftActions();
                    generate.setEnabled(true);generate.setAlpha(1f);
                    if(mode==AiWritingRules.Mode.REPLY && currentStyle()==AiWritingRules.Style.DEFAULT
                            && intent.length()==0 && materials.size()==0
                            && AiAutomaticSuggestions.onPanelReady(activity,captured,new AiAutomaticSuggestions.Callback(){
                        public void ready(AiReplyRequest.Output output,AiChatContext.Snapshot verifiedSnapshot) {
                            if(!alive(token))return;
                            snapshot=verifiedSnapshot;
                            setBusy(false);
                            imageDelivery=output.delivery;
                            lastProvidedContext=output.context;
                            JSONObject self=output.context.optJSONObject("self_profile");
                            lastProvidedProfile=self==null?"":self.optString("text");
                            lastProvidedMaterials=java.util.Collections.emptyList();
                            renderResult(output.result,verifiedSnapshot);
                        }
                        public void failed(String message) {
                            if(!alive(token))return;
                            setBusy(false);status.setText(message);
                            emptyState("点击右上角换一组","");
                        }
                    })) {
                        setBusy(true);status.setText("正在准备自动建议，关闭面板可取消…");
                        emptyState("正在想回复…","");
                    }
                }
                public void onError(String message) {
                    if(alive(token)) {
                        status.setText("读取失败 · 尚未请求 " + AiReplyConfig.selectedModel(activity).serviceName());
                        emptyState("暂时无法读取聊天", message + "\n请点击“更新资料”重试，读取成功后即可生成");
                        resultBody.addView(button(panel, "更新资料", Session.this::load));
                    }
                }
            });
        }
        void showImages() {
            if(closed||busy)return;
            if(snapshot==null){toast(activity,"正在读取聊天，请稍候");return;}
            SettingsUi.MenuPanel images=new SettingsUi.MenuPanel(activity,panel.dark);
            images.help="勾选后点击生成，才会提供给当前模型\nGIF 使用原文件";
            if(snapshot.images.isEmpty())images.body.addView(label(images,"当前没有可选择的普通图片或表情包",14));
            for(AiChatContext.Media media:snapshot.images) {
                LinearLayout row=new LinearLayout(images.context);row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(activity,8),dp(activity,2),dp(activity,8),dp(activity,2));
                UiAppearance.bind(row, AiReplyComponents.cardBackground(images));
                CheckBox check=new CheckBox(images.context);check.setText(media.label);check.setTextColor(images.foreground);
                check.setTextSize(13);images.appearance.checkControl(check);
                check.setChecked(selected.contains(media.id));
                row.addView(check,new LinearLayout.LayoutParams(0,dp(activity,52),1));
                check.setOnCheckedChangeListener((button,checked)->{
                    if(checked && selected.size()>=AiReplyRules.MAX_IMAGES) {
                        button.setChecked(false);toast(activity,"最多选择 "+AiReplyRules.MAX_IMAGES+" 张图片");return;
                    }
                    if(checked)selected.add(media.id);else selected.remove(media.id);
                    imageButton.setText("图片 · "+selected.size());
                });
                row.addView(chip(images,"预览",()->preview(this,media.httpsUrl)));
                LinearLayout.LayoutParams spacing=new LinearLayout.LayoutParams(-1,-2);spacing.bottomMargin=dp(activity,8);
                images.body.addView(row,spacing);
            }
            images.showAiBottom("图片与表情 · 最多 4 张",null);
            children.add(images.dialog);images.dialog.setOnDismissListener(d->children.remove(images.dialog));
        }
        void showDetails() {
            if(closed)return;
            SettingsUi.MenuPanel details=new SettingsUi.MenuPanel(activity,panel.dark);
            thoughtCards(details,lastResult);
            TextView sources=chip(details,"查看本轮资料",this::showSources);
            details.showAiBottom("回复思路",sources);
            children.add(details.dialog);details.dialog.setOnDismissListener(d->children.remove(details.dialog));
        }
        void showSources() {
            if(closed)return;
            SettingsUi.MenuPanel details=new SettingsUi.MenuPanel(activity,panel.dark);
            if(lastProvidedProfile!=null) {
                infoCard(details,"最近一次生成的资料",lastProvidedProfile.isEmpty()?"未填写":lastProvidedProfile);
                infoCard(details,"最近一次选用素材",materialNames(lastProvidedMaterials));
            }
            try {
                List<AiMaterialRules.Entry> next=mode==AiWritingRules.Mode.REPLY&&materials.size()>0?materials.snapshot(AiMaterialStore.read(activity,AiMaterialRules.Kind.REAL)):java.util.Collections.emptyList();
                infoCard(details,"下次选用素材",materialNames(next));
            }catch(Exception failure){infoCard(details,"选用素材需更新","请重新打开真实素材选择");}
            infoCard(details,lastProvidedContext==null?"已读取的聊天与主页":"最近一次提供的聊天与主页",
                    snapshot==null?"正在读取…":contextSummary(lastProvidedContext==null?snapshot.context:lastProvidedContext));
            infoCard(details,"图片与表情","已选 "+selected.size()+" 张"
                    +(imageDelivery.isEmpty()?"":"\n"+imageDelivery));
            details.showAiBottom("本轮资料",null);
            children.add(details.dialog);details.dialog.setOnDismissListener(d->children.remove(details.dialog));
        }
        String materialNames(List<AiMaterialRules.Entry> entries) {
            if(entries.isEmpty())return "未选用";
            List<String> names=new ArrayList<>();for(AiMaterialRules.Entry e:entries)names.add(e.title);
            return String.join("、",names);
        }
        void emptyState(String title,String subtitle) {
            resultBody.removeAllViews();
            TextView heading=label(panel,title,14);heading.setPadding(0,dp(activity,8),0,dp(activity,6));
            resultBody.addView(heading);
            TextView note=label(panel,subtitle,12);note.setPadding(0,0,0,dp(activity,12));resultBody.addView(note);
        }
        void setBusy(boolean value) {
            busy=value;generate.setEnabled(!value&&snapshot!=null);intent.setEnabled(!value);
            for(View view:controls)view.setEnabled(!value);
            generate.setAlpha(value?.5f:1f);
            generate.setText("");generate.setContentDescription(value?"生成中":"换一组");
            updateDraftActions();
        }
        void generate() {
            if(closed||busy||snapshot==null)return;
            AiAutomaticSuggestions.cancelFor(activity);
            final int token=++revision;
            final AiChatContext.Snapshot captured=snapshot;
            final String instruction=intent.getText().toString().trim();
            final AiWritingRules.Mode requestMode=mode;
            final AiWritingRules.Style requestStyle=currentStyle();
            EditText input=composer(activity);
            final String draft=input==null?"":input.getText().toString();
            if(requestMode==AiWritingRules.Mode.REWRITE&&draft.trim().isEmpty()) {
                status.setText("先在聊天输入框写下要改写的原话");return;
            }
            final String profile;
            final List<AiMaterialRules.Entry> materialSnapshot;
            try {
                materialSnapshot=requestMode==AiWritingRules.Mode.REPLY&&materials.size()>0?materials.snapshot(AiMaterialStore.read(activity,AiMaterialRules.Kind.REAL)):java.util.Collections.emptyList();
                profile=AiMaterialRules.profileForRequest(PluginPreferences.get(activity).getString(AiReplyConfig.PROFILE,""),materialSnapshot,requestMode);
            }catch(Exception failure){
                String message=failure instanceof IllegalArgumentException?failure.getMessage():"素材读取失败或已变更，请重新选用";
                lastResult=null;board.result(null);emptyState("素材需要更新",message);status.setText(message);return;
            }
            final AiModelConfig model=AiReplyConfig.selectedModel(activity);
            final List<AiChatContext.Media> chosenImages=new ArrayList<>();
            for(AiChatContext.Media media:captured.images)if(selected.contains(media.id)) {
                chosenImages.add(media);
            }
            closeChildren();lastResult=null;
            intentButton.setText(intent.length()==0?"补充想法":"已补充想法");
            setBusy(true);board.result(null);emptyState(requestMode==AiWritingRules.Mode.REWRITE?"正在改写…":"正在想回复…","");status.setText("核对当前聊天…");
            request=new AiReplyClient.Cancellation();final AiReplyClient.Cancellation cancel=request;
            WORK.execute(()->{
                try {
                    if(!AiChatContext.isCurrent(activity,captured))throw new IllegalStateException("会话已更新，请重新读取再生成");
                    if(cancel.isCancelled())return;
                    String apiKey=AiReplyConfig.readKey(activity,model);
                    MAIN.post(()->{if(alive(token))status.setText(model.serviceName()+" 正在生成，关闭面板可取消…");});
                    AiReplyRequest.Output output=AiReplyRequest.run(captured,profile,instruction,draft,
                            requestMode,requestStyle,chosenImages,model,apiKey,cancel);
                    AiReplyRules.Result result=output.result;
                    boolean fresh=AiChatContext.isCurrent(activity,captured);
                    MAIN.post(()->{
                        if(!alive(token))return;
                        setBusy(false);
                        if(!fresh||!sameVisible(captured)){
                            status.setText("生成期间聊天有新变化");
                            emptyState("先更新资料，再接着聊", "刚才的结果已过期，请在设置中点击“更新资料”");return;
                        }
                        imageDelivery=output.delivery;lastProvidedContext=output.context;lastProvidedProfile=profile;lastProvidedMaterials=materialSnapshot;
                        if(requestMode==AiWritingRules.Mode.REWRITE) {
                            lastResult=result;board.result(result);
                            if(result.candidates.size()!=1) {
                                status.setText(!result.questions.isEmpty()?"需补充信息，请查看回复思路":"本轮暂不改写，请查看回复思路");return;
                            }
                            EditText current=composer(activity);
                            if(current==null||!AiWritingRules.canApplyRewrite(draft,current.getText().toString())) {
                                status.setText("输入框已变化，未覆盖原话；请重新生成");return;
                            }
                            undoDraft=draft;appliedDraft=result.candidates.get(0).text;
                            current.setText(appliedDraft);current.setSelection(current.length());
                            updateDraftActions();status.setText("已改写，可还原或继续编辑");
                        } else renderResult(result,captured);
                    });
                } catch(Exception error) {
                    MAIN.post(()->{if(alive(token)){
                        setBusy(false);status.setText(requestMode==AiWritingRules.Mode.REWRITE?friendly(error):"本轮没有生成回复");
                        emptyState("暂时没生成",friendly(error));
                    }});
                }
            });
        }
        void renderResult(AiReplyRules.Result result,AiChatContext.Snapshot captured) {
            lastResult=result;generate.setText("");board.result(result);resultBody.removeAllViews();
            if(!result.questions.isEmpty()) {
                emptyState("需要补充的信息",String.join("\n\n",result.questions));
                status.setText("需要补充信息，请在设置中点“补充想法”");
                return;
            }
            for(AiReplyRules.Candidate candidate:result.candidates) {
                resultBody.addView(AiReplyComponents.boardCandidate(panel,candidate,()->sendReply(candidate.text,captured),()->editReply(candidate.text,captured)));
            }
            if(result.candidates.isEmpty()) {
                status.setText("");
                emptyState("STOP".equals(result.action)?"这轮停止联系":"这轮先留一点空间",result.goal);
            } else status.setText("");
        }
        void editReply(String reply,AiChatContext.Snapshot captured) {
            if(closed||busy)return;
            AlertDialog editor=AiReplyEditor.show(activity,panel.dark,reply,false,
                    edited->sendReply(edited,captured,false,false));
            children.add(editor);
            editor.setOnDismissListener(d->children.remove(editor));
        }
        void sendReply(String reply,AiChatContext.Snapshot captured) {sendReply(reply,captured,false);}
        void sendReply(String reply,AiChatContext.Snapshot captured,boolean fromComposer) {
            sendReply(reply,captured,fromComposer,true);
        }
        void sendReply(String reply,AiChatContext.Snapshot captured,boolean fromComposer,boolean inferBoundaries) {
            if(closed||busy||sendBlocked)return;
            if(AUTOMATIC_SENDS.containsKey(activity)){
                status.setText("上一组回复仍在发送，请稍候");return;
            }
            final List<String> parts;
            final AiReplySender sender;
            try {
                parts=inferBoundaries?AiSendRules.parts(reply):AiSendRules.explicitParts(reply);
                sender=new AiReplySender(activity,captured);
            } catch(Exception error) {
                android.util.Log.w("SoulHook102","AI send prepare failed: "+AiReplySender.failureCode(error));
                status.setText("发送准备失败，尚未提交消息；请重新打开面板");return;
            }
            sentComposerDraft=fromComposer?reply:null;
            sending=true;
            setBusy(true);generate.setContentDescription("发送中");
            // Consume these candidates before dispatch: timeout/partial failure must not replay them.
            resultBody.removeAllViews();lastResult=null;
            final int token=revision;
            new AiSendSequence(sender,new AiSendSequence.Clock() {
                public long now(){return android.os.SystemClock.uptimeMillis();}
                public void later(Runnable action,long delay){MAIN.postDelayed(action,delay);}
            },()->alive(token),new AiSendSequence.Listener() {
                public void progress(int index,int total){status.setText("正在发送第 "+(index+1)+" / "+total+" 条…");}
                public void completed(int total) {
                    EditText input=composer(activity);
                    if(sentComposerDraft!=null&&input!=null&&sentComposerDraft.equals(input.getText().toString()))input.setText("");
                    close();toast(activity,"已发送 "+total+" 条回复");
                }
                public void stopped(int confirmed,int total,String reason){sendStopped(confirmed,total,reason);}
            },parts).start();
        }
        void sendStopped(int confirmed,int total,String reason) {
            android.util.Log.w("SoulHook102","AI send stopped: "+reason+"; confirmed="+confirmed+"; total="+total);
            sending=false;sendBlocked=true;setBusy(false);generate.setEnabled(false);generate.setAlpha(.5f);
            status.setText("发送已停止 · 已确认 "+confirmed+" / "+total+" 条");
            emptyState("请先核对聊天记录","当前一条可能已提交，其余未继续发送\n请用 Soul 原消息重试；更新资料后再生成，避免重复发送");
        }
        boolean sameVisible(AiChatContext.Snapshot captured) {
            try {return AiChatContext.sameVisibleConversation(activity,captured);}catch(Exception ignored){return false;}
        }
        void closeChildren() {
            for(AlertDialog child:new ArrayList<>(children))if(child.isShowing())child.dismiss();
            children.clear();
        }
        boolean alive(int token){return !closed&&!gone(activity)&&revision==token;}
        void close() {
            if(closed)return;closed=true;revision++;materials.clear();
            AiAutomaticSuggestions.cancelFor(activity);
            hostComposer.removeTextChangedListener(draftWatcher);
            if(collection!=null)collection.cancel();if(request!=null)request.cancel();
            closeChildren();
            if(ACTIVE.get(activity)==this)ACTIVE.remove(activity);
            if(dock!=null){dock.close();dock=null;}
            if(panel.dialog!=null&&panel.dialog.isShowing())panel.dialog.dismiss();
        }
    }

    /** Retain the exact settings view and scroll offset while the preview owns focus. */
    static void showPreview(SettingsUi.MenuPanel parent) {
        if (gone(parent.activity) || parent.dialog == null || !parent.dialog.isShowing()) return;
        AlertDialog owner = parent.dialog;
        if (owner.getWindow() == null) return;
        View ownerDecor = owner.getWindow().getDecorView();
        Runnable restore = () -> {
            // hide() makes isShowing() false but keeps the decor attached; dismiss() detaches it.
            // Only restore this existing window, never resurrect a dismissed settings dialog.
            if (!gone(parent.activity) && parent.dialog == owner && owner.getWindow() != null
                    && owner.getWindow().getDecorView() == ownerDecor && ownerDecor.isAttachedToWindow())
                owner.show();
        };
        owner.hide();
        try {
            showPreview(parent.activity, parent.dark, restore);
        } catch (RuntimeException failure) {
            restore.run();
            toast(parent.activity, "预览暂时无法打开，请重试");
        }
    }

    static void showPreview(Activity activity,boolean dark) {
        showPreview(activity, dark, () -> {});
    }

    /** Example data only: never connected to context, model or composer. */
    private static void showPreview(Activity activity,boolean dark,Runnable onClosed) {
        SettingsUi.MenuPanel panel=new SettingsUi.MenuPanel(activity,dark);
        Runnable example=()->toast(activity,"这是样式预览，未请求、填入或发送");
        LinearLayout rows=new LinearLayout(panel.context);rows.setOrientation(LinearLayout.VERTICAL);
        final AlertDialog[] editor={null};
        AiReplyRules.Result previewResult;
        try {
            previewResult=new AiReplyRules.Result(
                    new JSONObject().put("stage","20-39").put("frame","steady").put("confidence","low")
                            .put("trend","stable").put("summary","示例：对方愿意分享日常，当前话题有自然的接话空间。"),
                    new JSONObject().put("type","CONNECT").put("goal","接住一个具体细节，让对话自然延续。").put("frame_goal",""),
                    new JSONObject().put("positive","顺着对方主动补充的细节接着聊。")
                            .put("unclear","回到轻松的普通话题。").put("negative","尊重对方的节奏，不继续追问。"),
                    Collections.emptyList(),Collections.emptyList());
        } catch(JSONException impossible) {throw new IllegalStateException(impossible);}
        Runnable thoughts=()->{
            if(editor[0]!=null)editor[0].dismiss();
            SettingsUi.MenuPanel detail=new SettingsUi.MenuPanel(activity,dark);
            thoughtCards(detail,previewResult);
            detail.showAiBottom("回复思路 · 示例",null);editor[0]=detail.dialog;
        };
        for(String reply:new String[]{"这家店你最推荐哪一道","这条街看着挺舒服\n你拍的是哪一段",
                "我也喜欢这样慢慢走一走，不赶着去下一个地方，偶尔拐进一条没走过的小巷，看见有意思的小店就停下来。\n你这次有没有遇到哪家让你想再去一次的店？"})
            rows.addView(AiReplyComponents.boardCandidate(panel,new AiReplyRules.Candidate("preview",reply,
                    "接住对方分享的具体内容", "主动补充细节就接着聊，不延伸就不追问"),example,()->{
                if(editor[0]!=null)editor[0].dismiss();
                editor[0]=AiReplyEditor.show(activity,dark,reply,true,ignored->example.run());
            }));
        TextView status=label(panel,"样式预览 · 长回复点击查看全文，长按编辑",12);
        status.setPadding(dp(activity,12),0,dp(activity,12),dp(activity,6));
        TextView refresh=primary(panel,"换一组",example);
        final AiReplyBoard[] holder=new AiReplyBoard[1];
        AiReplyBoard content=new AiReplyBoard(panel,rows,status,refresh,new AiReplyBoard.Actions(){
            public void mode(AiWritingRules.Mode value){holder[0].mode(value,value==AiWritingRules.Mode.REPLY?AiWritingRules.Style.DEFAULT:AiWritingRules.Style.CONSIDERATE);}
            public void style(AiWritingRules.Style value){holder[0].selected(value);}
            public void settings(){example.run();}
            public void details(){thoughts.run();}
            public void restore(){example.run();}
            public void clear(){example.run();}
            public void send(){example.run();}
        });
        holder[0]=content;content.result(previewResult);
        try {
            showCompactFallback(panel,content);
        } catch (RuntimeException failure) {
            if (panel.dialog != null && panel.dialog.isShowing()) panel.dialog.dismiss();
            throw failure;
        }
        panel.dialog.setOnDismissListener(d->{
            if(editor[0]!=null)editor[0].dismiss();
            onClosed.run();
        });
    }

    private static TextView scoreView(SettingsUi.MenuPanel panel,AiReplyRules.Result result,Runnable open) {
        TextView score=label(panel,result.interestLabel(),12);
        score.setPadding(0,dp(panel.context,2),0,dp(panel.context,8));
        score.setMinHeight(dp(panel.context,48));
        score.setGravity(Gravity.CENTER_VERTICAL);
        score.setFocusable(true);score.setOnClickListener(v->open.run());
        score.setContentDescription(result.interestLabel()+"，查看回复思路");
        return score;
    }

    static LinearLayout infoCard(SettingsUi.MenuPanel panel,String heading,String body) {
        LinearLayout card=new LinearLayout(panel.context);card.setOrientation(LinearLayout.VERTICAL);
        UiAppearance.bind(card, AiReplyComponents.cardBackground(panel));
        card.setPadding(dp(panel.context,12),dp(panel.context,4),dp(panel.context,12),dp(panel.context,8));
        TextView title=label(panel,heading,12);
        title.setTextColor(panel.dark ? 0xffb7a1ec : 0xff7050b0);
        card.addView(title);
        if(!body.isEmpty()) {
            TextView text=label(panel,body,14);
            text.setTextColor(AiReplyComponents.hostColor(panel,"color_s_03",panel.foreground));
            text.setPadding(0,0,0,dp(panel.context,4));
            text.setLineSpacing(dp(panel.context,3),1f);
            card.addView(text);
        }
        LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(-1,-2);bounds.bottomMargin=dp(panel.context,8);
        panel.body.addView(card,bounds);return card;
    }

    private static void thoughtCards(SettingsUi.MenuPanel panel,AiReplyRules.Result result) {
        if(result==null) {infoCard(panel,"还没有本轮分析","生成回复后可查看");return;}
        String confidence=result.confidence.equals("high")?"较高":result.confidence.equals("medium")?"中等":"较低";
        String trend=result.trend.equals("up")?"升温":result.trend.equals("down")?"降温":result.trend.equals("stable")?"平稳":"尚不明确";
        infoCard(panel,result.interestLabel(),"判断可信度："+confidence+" · 互动趋势："+trend+"\n兴趣判断不代表本次邀约或亲密行为的意愿");
        infoCard(panel,"互动分析",result.summary);
        infoCard(panel,"回复方向",result.goal+(result.frameGoal.isEmpty()?"":"\n\n"+result.frameGoal));
        if(!result.questions.isEmpty())infoCard(panel,"需要你补充",String.join("\n\n",result.questions));
        List<String> follow=new ArrayList<>();
        if(!result.positive.isEmpty())follow.add("回应积极时\n"+result.positive);
        if(!result.unclear.isEmpty())follow.add("回应不明确时\n"+result.unclear);
        if(!result.negative.isEmpty())follow.add("冷淡或拒绝时\n"+result.negative);
        if(!follow.isEmpty())infoCard(panel,"后续观察",String.join("\n\n",follow));
    }

    /** Content-sized sheet for settings preview and unsupported host layouts. */
    private static void showCompactFallback(SettingsUi.MenuPanel panel,View content) {
        panel.showBottomContent(content,320);
        if(panel.dialog.getWindow()!=null)
            panel.dialog.getWindow().setLayout(-1,ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static TextView chip(SettingsUi.MenuPanel panel,String text,Runnable action) {
        TextView button=label(panel,text,12);button.setGravity(Gravity.CENTER);button.setTextColor(panel.secondary);
        button.setPadding(dp(panel.context,10),0,dp(panel.context,10),0);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(panel.context,48));
        lp.rightMargin=dp(panel.context,6);button.setLayoutParams(lp);button.setMinWidth(dp(panel.context,54));
        panel.appearance.apply(button,UiAppearance.Role.ACTION,20);
        button.setFocusable(true);button.setOnClickListener(v->action.run());return button;
    }
    private static TextView primary(SettingsUi.MenuPanel panel,String text,Runnable action) {
        TextView button=label(panel,text,14);button.setPadding(0,0,0,0);button.setGravity(Gravity.CENTER);
        button.setTypeface(null,android.graphics.Typeface.BOLD);button.setTextColor(0xff003c36);
        panel.appearance.apply(button,UiAppearance.Role.PRIMARY);button.setOnClickListener(v->action.run());button.setFocusable(true);
        return button;
    }
    private static String compactSummary(JSONObject context) {
        JSONObject history=context.optJSONObject("history");
        int messages=history==null?0:history.optInt("includedCount");
        JSONObject section=context.optJSONObject("homepagePosts");
        JSONObject posts=section==null?null:section.optJSONObject("data");
        String summary="已读取 "+messages+" 条聊天"+(posts==null?"":" · "+posts.optInt("includedCount")+" 篇帖子");
        if(history!=null&&history.optBoolean("truncated"))summary+=" · 仅近期";
        for(String key:new String[]{"homepageProfile","homepageTags","homepagePosts"}) {
            JSONObject source=context.optJSONObject(key);
            String status=source==null?"":source.optString("status");
            if(!status.equals("ok")&&!status.equals("empty")){summary+=" · 部分资料未取得";break;}
        }
        return summary;
    }

    private static String contextSummary(JSONObject context) {
        StringBuilder summary=new StringBuilder();
        JSONObject history=context.optJSONObject("history");
        if(history!=null) {
            summary.append("聊天：").append(history.optInt("includedCount")).append(" 条");
            int omitted=history.optInt("omittedOlderCount")+history.optInt("requestOmittedCount");
            if(omitted>0)summary.append("，另有 ").append(omitted).append(" 条未提供、未摘要");
            JSONArray quoted=history.optJSONArray("quotedMessages");
            if(quoted!=null&&quoted.length()>0)summary.append("\n另保留 ").append(quoted.length()).append(" 条被引用的旧消息节选");
            summary.append("\n仅本地记录，未请求云端漫游");
        }
        if(history!=null) {
            JSONArray rows=history.optJSONArray("messages");int cards=0,details=0;
            if(rows!=null)for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.optJSONObject(i),post=row==null?null:row.optJSONObject("sharedPost");
                if(post!=null){cards++;if("ok".equals(post.optString("detailStatus")))details++;}
            }
            if(cards>0)summary.append("\n聊天帖子：解析 ").append(cards).append(" 张卡片，读到详情 ")
                    .append(details).append(" 张，其余保留可用卡片内容并标记详情缺失");
        }
        for(String[] source:new String[][]{{"chatProfile","聊天资料"},{"homepageProfile","主页简介"},
                {"homepageTags","个人标签"},{"homepagePosts","主页帖子"}}) {
            JSONObject section=context.optJSONObject(source[0]);
            if(section==null)continue;
            summary.append("\n").append(source[1]).append("：").append(sourceStatus(section.optString("status")));
            if(source[0].equals("homepagePosts")) {
                JSONObject posts=section.optJSONObject("data");
                if(posts!=null)summary.append("，提供 ").append(posts.optInt("includedCount")).append(" 篇（仅首页）");
            } else if(source[0].equals("homepageTags")) {
                JSONArray tags=section.optJSONArray("data");
                if(tags!=null)summary.append("，").append(tags.length()).append(" 项");
            }
        }
        return summary.toString();
    }
    private static String sourceStatus(String status) {
        switch(status) {
            case "ok": return "已读取";
            case "empty": return "无内容";
            case "timeout": return "读取超时";
            case "no_usable_fields": return "暂无可用资料";
            case "not_available": return "未取得";
            case "error": case "parse_error": return "读取失败";
            default: return "未读取";
        }
    }

    private static void preview(Session session,String url) {
        if(session.closed)return;
        Activity activity=session.activity;
        SettingsUi.MenuPanel panel=new SettingsUi.MenuPanel(activity,session.panel.dark);
        panel.help="返回后勾选，点击生成时才会提供给当前模型";
        TextView status=label(panel,"读取图片预览…",13);panel.body.addView(status);
        ImageView image=new ImageView(panel.context);image.setAdjustViewBounds(true);
        image.setContentDescription("可选图片预览");panel.body.addView(image,new LinearLayout.LayoutParams(-1,-2));
        panel.showAiBottom("图片预览 · 未提供给 AI",null);
        session.children.add(panel.dialog);
        final HttpsURLConnection[] running={null};final boolean[] stopped={false};
        panel.dialog.setOnDismissListener(d->{
            session.children.remove(panel.dialog);
            synchronized(running){stopped[0]=true;if(running[0]!=null)running[0].disconnect();}
        });
        WORK.execute(()->{
            HttpsURLConnection connection=null;
            try {
                URL remote=new URL(url);if(!"https".equals(remote.getProtocol()))throw new IllegalStateException();
                connection=(HttpsURLConnection)remote.openConnection();connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(10000);connection.setReadTimeout(15000);
                synchronized(running){if(stopped[0])return;running[0]=connection;}
                if(connection.getResponseCode()!=200)throw new IllegalStateException();
                byte[] bytes;
                try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    byte[] chunk=new byte[8192];int n;
                    while((n=in.read(chunk))!=-1){if(out.size()+n>8*1024*1024)throw new IllegalStateException();out.write(chunk,0,n);}
                    bytes=out.toByteArray();
                }
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
                if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IllegalStateException();
                BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
                while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>1200)options.inSampleSize*=2;
                Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if(bitmap==null)throw new IllegalStateException();
                MAIN.post(()->{if(!gone(activity)&&panel.dialog.isShowing()){image.setImageBitmap(bitmap);status.setVisibility(View.GONE);}});
            } catch(Exception ignored) {MAIN.post(()->{if(!gone(activity)&&panel.dialog.isShowing())status.setText("预览不可用，可返回原聊天或帖子核对图片");});}
            finally {if(connection!=null)connection.disconnect();synchronized(running){running[0]=null;}}
        });
    }

    private static String friendly(Exception error) {
        if(error instanceof AiReplyClient.ClientException)return error.getMessage();
        if(error instanceof JSONException)return "回复结果不可用，请重新生成\n未填入或发送";
        if(error instanceof IllegalStateException)return error.getMessage()==null?"当前状态不可用，请重新读取":error.getMessage();
        return "AI 请求未完成，请检查网络或配置后重试；未填入或发送";
    }
    private static View find(Activity activity,String name){int id=activity.getResources().getIdentifier(name,"id",activity.getPackageName());return id==0?null:activity.findViewById(id);}
    static EditText composer(Activity activity){View v=find(activity,"et_sendmessage");return v instanceof EditText?(EditText)v:null;}
    private static boolean gone(Activity activity){return activity.isFinishing()||activity.isDestroyed();}
    private static void toast(Activity activity,String s){if(!gone(activity))Toast.makeText(activity,s,Toast.LENGTH_LONG).show();}
    private static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static TextView label(SettingsUi.MenuPanel p,String text,int size){TextView v=new TextView(p.context);v.setText(text);v.setTextSize(size);v.setTextColor(size<=13?p.secondary:p.foreground);v.setPadding(0,dp(p.context,8),0,dp(p.context,8));return v;}
    private static TextView button(SettingsUi.MenuPanel p,String text,Runnable action){TextView v=label(p,text,15);p.appearance.apply(v,UiAppearance.Role.ACTION);v.setTextColor(p.accentText);v.setMinHeight(dp(p.context,48));v.setGravity(Gravity.CENTER_VERTICAL);v.setFocusable(true);v.setOnClickListener(w->action.run());return v;}
    private static RadioButton radio(SettingsUi.MenuPanel p,String text){RadioButton v=new RadioButton(p.context);v.setText(text);v.setTextSize(14);v.setTextColor(p.foreground);p.appearance.checkControl(v);v.setMinHeight(dp(p.context,44));return v;}
    private static EditText keyField(SettingsUi.MenuPanel p,String hint){EditText v=field(p,hint,true);v.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);v.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1024)});v.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);return v;}
    static EditText field(SettingsUi.MenuPanel p,String hint,boolean single){EditText v=new EditText(p.context);v.setTextSize(15);v.setTextColor(p.foreground);v.setHintTextColor(p.secondary);v.setHint(hint);v.setSingleLine(single);if(!single)v.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);p.appearance.apply(v,UiAppearance.Role.INPUT);v.setPadding(dp(p.context,12),dp(p.context,10),dp(p.context,12),dp(p.context,10));v.setMinHeight(dp(p.context,48));p.body.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;}
}
