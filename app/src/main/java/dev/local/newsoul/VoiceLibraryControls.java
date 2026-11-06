package dev.local.newsoul;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Local voice library for ordinary private chats; only the explicit send button uploads. */
final class VoiceLibraryControls {
    static boolean ready;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final WeakHashMap<View, Boolean> bound = new WeakHashMap<>();
    private Method account, sendLocal;
    private HostBindings.Chat chat;
    private Session active;

    static VoiceLibraryControls install(XposedModule module, ClassLoader loader) {
        VoiceLibraryControls controls = new VoiceLibraryControls();
        try {
            controls.account = HostBindings.account(loader);
            controls.chat = new HostBindings.Chat(loader);
            controls.sendLocal = HostBindings.voiceSend(loader);
            Method resume = HostBindings.lifecycle(loader,true);
            module.hook(resume).setId("new-soul/voice-library-entry")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        WeakReference<Object> ref = new WeakReference<>(chain.getThisObject());
                        for (long delay : new long[]{0, 350, 1000}) controls.main.postDelayed(() -> {
                            try { Object owner = ref.get(); if (owner != null) controls.bind(owner); }
                            catch (Throwable error) { Log.w("SoulHook102", "voice library entry unavailable", error); }
                        }, delay);
                        return result;
                    });
            Method pause = HostBindings.lifecycle(loader,false);
            module.hook(pause).setId("new-soul/voice-library-close")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        if (controls.active != null && controls.active.fragment == chain.getThisObject())
                            controls.active.close();
                        return chain.proceed();
                    });
            module.deoptimize(resume); module.deoptimize(pause);
            ready = true;
            Log.i("SoulHook102", "voice library installed; local storage and native audio upload");
        } catch (Throwable error) { Log.e("SoulHook102", "voice library unavailable", error); }
        return controls;
    }

    private void bind(Object fragment) throws Exception {
        Activity activity = (Activity) chat.activity.invoke(fragment);
        if (activity == null || !(boolean) chat.resumed.invoke(fragment)) return;
        View root = (View) call(fragment, "getView");
        if (root == null) return;
        for (String name : new String[]{"menu_tab_voice_inner_outside", "menu_tab_voice_inner", "menu_tab_voice"}) {
            int id = activity.getResources().getIdentifier(name, "id", activity.getPackageName());
            View voice = id == 0 ? null : root.findViewById(id);
            if (voice == null || bound.containsKey(voice) || voice.hasOnLongClickListeners()) continue;
            WeakReference<Object> owner = new WeakReference<>(fragment);
            voice.setOnLongClickListener(view -> {
                try {
                    Object current = owner.get();
                    if (current == null) return false;
                    Session session = new Session(current);
                    replace(session);
                    session.library();
                } catch (Throwable error) { failure(activity, "暂时无法打开语音库", error); }
                return true;
            });
            // Soul 6.37 routes this icon's entire touch stream into its hold-to-speak
            // handler before View can dispatch our long click. Keep the host click
            // listener, but reserve this icon's long press for the library. Never
            // change the actual input/press-to-speak view's touch listener.
            if ("menu_tab_voice_inner_outside".equals(name)) voice.setOnTouchListener(null);
            bound.put(voice, true);
        }
    }

    static void showUsage(Activity activity, boolean dark) {
        SettingsUi.MenuPanel p = new SettingsUi.MenuPanel(activity, dark);
        p.body.addView(SettingsUi.label(p.context, usageText(), 14, p.foreground));
        p.show("语音库使用说明", "知道了");
    }

    static String usageText() {
        return "长按语音消息可存入语音库\n"
                + "普通私聊中，长按输入区语音图标可打开\n"
                + "点条目可试听、编辑或删除\n"
                + "仅存本机，按账号分别管理";
    }

    void save(Object fragment, Object audio) throws Exception {
        Session session = new Session(fragment);
        String url = (String) field(audio, "url");
        int duration = (int) field(audio, "duration");
        if (!RepeatMessageRules.mediaUrl(url) || duration <= 0) throw new IllegalArgumentException("Invalid voice");
        replace(session);
        session.editor("保存到语音库", "", note -> session.download(url, duration, note));
    }

    private void replace(Session session) {
        if (active != null) active.close();
        active = session;
    }

    private interface NoteAction { void run(String note) throws Exception; }
    private interface Work { void run() throws Exception; }

    private final class Session {
        final Object fragment, conversation;
        final Activity activity;
        final String own, peer;
        final VoiceLibraryStore store;
        final boolean dark;
        SettingsUi.MenuPanel panel, libraryPanel;
        int libraryScrollY;
        String libraryQuery = "";
        MediaPlayer player;
        boolean closed, busy, writing;
        TextView playButton;

        Session(Object fragment) throws Exception {
            this.fragment = fragment;
            activity = (Activity) chat.activity.invoke(fragment);
            own = (String) account.invoke(null);
            peer = (String) chat.peer.get(fragment);
            conversation = chat.current(fragment);
            validate();
            store = new VoiceLibraryStore(activity.getFilesDir(), own);
            View title = find(activity, "tv_title");
            dark = title instanceof TextView ? Color.luminance(((TextView) title).getCurrentTextColor()) > .45
                    : (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;
        }

        void validate() throws Exception {
            if (closed || activity == null || activity.isFinishing() || activity.isDestroyed()
                    || !(boolean) chat.resumed.invoke(fragment) || !own.equals(account.invoke(null))
                    || peer == null || peer.isEmpty() || own.equals(peer) || !peer.equals(chat.peer.get(fragment))
                    || conversation == null || conversation != chat.current(fragment)
                    || (int) chat.chatType.invoke(conversation) != 0
                    || !peer.equals(chat.recipient.invoke(conversation)))
                throw new IllegalStateException("聊天或账号已变化，请重新打开语音库");
        }

        void close() {
            if (closed) return;
            closed = true; stop();
            if (panel != null && panel.dialog != null) panel.dialog.dismiss();
            if (active == this) active = null;
        }

        SettingsUi.MenuPanel next() {
            stop();
            if (panel != null && panel.dialog != null) {
                if (panel == libraryPanel && panel.body.getParent() instanceof ScrollView)
                    libraryScrollY = ((ScrollView) panel.body.getParent()).getScrollY();
                panel.dialog.setOnCancelListener(null);
                panel.dialog.setOnDismissListener(null); panel.dialog.dismiss();
            }
            playButton = null;
            panel = new SettingsUi.MenuPanel(activity, dark);
            return panel;
        }

        void watch(Work back) {
            SettingsUi.MenuPanel p = panel;
            // Back and the explicit close action dismiss without side effects.
            p.dialog.setCanceledOnTouchOutside(false);
            p.dialog.setOnCancelListener(dialog -> {
                if (closed || panel != p) return;
                if (busy) { closeWhileBusy(); return; }
                try { validate(); back.run(); }
                catch (Throwable error) { close(); }
            });
            p.dialog.setOnDismissListener(dialog -> {
                // A queued dismiss from a previous page must not close its replacement.
                if (panel == p) close();
            });
        }

        void closeWhileBusy() {
            if (writing) toast(activity, "操作仍在后台进行，请稍后打开语音库查看结果");
            close();
        }

        void library() throws Exception {
            validate();
            SettingsUi.MenuPanel p = next();
            p.help = usageText();
            p.body.addView(SettingsUi.label(p.context, "正在读取语音库…", 14, p.secondary));
            libraryPanel = p;
            p.show("语音库", "关闭"); watch(this::close);
            io.execute(() -> {
                try {
                    List<VoiceLibraryStore.Entry> entries = store.list();
                    VoiceLibraryStore.PendingFiles pending = store.pendingFiles();
                    main.post(() -> {
                        if (closed || panel != p) return;
                        try { validate(); } catch (Exception stale) { close(); return; }
                        p.body.removeAllViews();
                        pendingAction(p, pending);
                        EditText search = new EditText(p.context);
                        search.setTextColor(p.foreground);
                        search.setHintTextColor(p.secondary);
                        search.setHint("搜索备注");
                        search.setContentDescription("搜索语音备注");
                        search.setSingleLine(true);
                        search.setMinHeight(SettingsUi.dp(p.context, 48));
                        search.setTextSize(15);
                        search.setPadding(SettingsUi.dp(p.context, 14), SettingsUi.dp(p.context, 10),
                                SettingsUi.dp(p.context, 14), SettingsUi.dp(p.context, 10));
                        p.appearance.apply(search, UiAppearance.Role.INPUT);
                        android.graphics.drawable.Drawable searchIcon = p.context.getDrawable(android.R.drawable.ic_menu_search).mutate();
                        searchIcon.setTint(p.secondary);
                        int iconSize = SettingsUi.dp(p.context, 20);
                        searchIcon.setBounds(0, 0, iconSize, iconSize);
                        search.setCompoundDrawablesRelative(searchIcon, null, null, null);
                        search.setCompoundDrawablePadding(SettingsUi.dp(p.context, 8));
                        search.setText(libraryQuery);
                        search.setSelection(search.length());
                        p.body.addView(search, new LinearLayout.LayoutParams(-1, -2));
                        LinearLayout rows = new LinearLayout(p.context);
                        rows.setOrientation(LinearLayout.VERTICAL);
                        rows.setPadding(0, SettingsUi.dp(p.context, 12), 0, 0);
                        p.body.addView(rows, new LinearLayout.LayoutParams(-1, -2));
                        renderEntries(p, rows, entries);
                        search.addTextChangedListener(new TextWatcher() {
                            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
                            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                                if (closed || panel != p) return;
                                libraryQuery = value.toString();
                                libraryScrollY = 0;
                                renderEntries(p, rows, entries);
                                if (p.body.getParent() instanceof ScrollView)
                                    ((ScrollView) p.body.getParent()).scrollTo(0, 0);
                            }
                            @Override public void afterTextChanged(Editable value) {}
                        });
                        // Returning from details should preserve the list position without opening the keyboard.
                        rows.setFocusableInTouchMode(true);
                        rows.requestFocus();
                        if (p.body.getParent() instanceof ScrollView) {
                            ScrollView scroll = (ScrollView) p.body.getParent();
                            final int restoreScrollY = libraryScrollY;
                            scroll.post(() -> {
                                if (!closed && panel == p) scroll.scrollTo(0, restoreScrollY);
                            });
                        }
                        Log.i("SoulHook102", "voice library shown; count=" + entries.size());
                    });
                } catch (Throwable error) {
                    Log.w("SoulHook102", "voice library read failed", error);
                    VoiceLibraryStore.PendingFiles leftover = null;
                    try { leftover = store.pendingFiles(); }
                    catch (IOException scanError) { Log.w("SoulHook102", "voice library recovery scan failed", scanError); }
                    final VoiceLibraryStore.PendingFiles pending = leftover;
                    main.post(() -> {
                        if (closed || panel != p) return;
                        try { validate(); } catch (Exception stale) { close(); return; }
                        p.body.removeAllViews();
                        p.body.addView(SettingsUi.label(p.context, "读取语音库失败，请重试", 14, p.foreground));
                        p.body.addView(action(p, "重试", this::library));
                        pendingAction(p, pending);
                    });
                }
            });
        }

        void pendingAction(SettingsUi.MenuPanel p, VoiceLibraryStore.PendingFiles pending) {
            if (pending != null && pending.count > 0)
                p.body.addView(action(p, "处理未完成文件", () -> confirmRemoval("未完成文件",
                        "删除 " + pending.count + " 个未完成文件（"
                                + android.text.format.Formatter.formatShortFileSize(p.context, pending.bytes)
                                + "）？\n删除后无法恢复",
                        () -> store.discardPending(pending), this::library)));
        }

        void renderEntries(SettingsUi.MenuPanel p, LinearLayout rows, List<VoiceLibraryStore.Entry> entries) {
            rows.removeAllViews();
            String query = libraryQuery.trim().toLowerCase(Locale.ROOT);
            int matches = 0;
            for (VoiceLibraryStore.Entry entry : entries) {
                if (!entry.note.toLowerCase(Locale.ROOT).contains(query)) continue;
                TextView row = action(p, entry.note + "\n" + entry.duration + " 秒", () -> details(entry));
                android.text.SpannableString text = new android.text.SpannableString(row.getText());
                text.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, entry.note.length(), 0);
                text.setSpan(new android.text.style.ForegroundColorSpan(p.secondary), entry.note.length()+1, text.length(), 0);
                text.setSpan(new android.text.style.RelativeSizeSpan(.87f), entry.note.length()+1, text.length(), 0);
                row.setText(text);
                row.setTextSize(16);
                row.setLineSpacing(SettingsUi.dp(p.context, 4), 1f);
                SettingsUi.addListItem(p, rows, row);
                matches++;
            }
            if (matches == 0) rows.addView(SettingsUi.label(p.context,
                    entries.isEmpty() ? "还没有保存语音" : "没有匹配的语音，请尝试其他备注", 14, p.secondary));
        }

        void details(VoiceLibraryStore.Entry entry) throws Exception {
            validate();
            SettingsUi.MenuPanel p = next();
            TextView note = SettingsUi.label(p.context, entry.note, 17, p.foreground);
            p.help = "仅点“发送到当前聊天”才发送";
            note.setGravity(android.view.Gravity.CENTER);
            note.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            note.setPadding(0, SettingsUi.dp(p.context, 20), 0, SettingsUi.dp(p.context, 8));
            p.body.addView(note, new LinearLayout.LayoutParams(-1, -2));
            TextView duration = SettingsUi.label(p.context, entry.duration + " 秒 · 已保存在本机", 13, p.secondary);
            duration.setGravity(android.view.Gravity.CENTER);
            duration.setPadding(0, 0, 0, SettingsUi.dp(p.context, 24));
            p.body.addView(duration, new LinearLayout.LayoutParams(-1, -2));
            playButton = action(p, "试听", () -> play(entry));
            TextView rename = action(p, "修改备注", () -> editor("修改备注", entry.note, value -> {
                background(() -> store.rename(entry.id, value), () -> library());
            }, () -> details(entry)));
            playButton.setTextColor(p.accentText);
            rename.setTextColor(p.accentText);
            SettingsUi.addInfoActionPair(p, p.body, playButton, rename);
            TextView delete = action(p, "删除", () -> confirmDelete(entry));
            delete.setTextColor(dark ? 0xffffa3a3 : 0xffb3261e);
            delete.setGravity(android.view.Gravity.CENTER);
            p.body.addView(delete, new LinearLayout.LayoutParams(-1, -2));
            TextView send = action(p, "发送到当前聊天", () -> send(entry));
            send.setTextColor(p.accentText);
            send.setGravity(android.view.Gravity.CENTER);
            p.appearance.apply(send, UiAppearance.Role.PRIMARY);
            LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(-1, -2);
            sendParams.topMargin = SettingsUi.dp(p.context, 16);
            sendParams.bottomMargin = SettingsUi.dp(p.context, 12);
            p.body.addView(send, sendParams);
            p.show("语音详情", "返回语音库"); watch(this::library);
            TextView back = p.dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            back.setTextColor(p.accentText);
            back.setMinHeight(SettingsUi.dp(p.context, 48));
            back.setOnClickListener(view -> {
                if (busy) { closeWhileBusy(); return; }
                try { library(); } catch (Throwable error) { close(); }
            });
        }

        void editor(String title, String initial, NoteAction callback) {
            editor(title, initial, callback, this::close);
        }

        void editor(String title, String initial, NoteAction callback, Work back) {
            SettingsUi.MenuPanel p = next();
            EditText input = new EditText(p.context);
            input.setTextColor(p.foreground); input.setHintTextColor(p.secondary);
            p.appearance.apply(input, UiAppearance.Role.INPUT);
            input.setPadding(SettingsUi.dp(p.context,12),SettingsUi.dp(p.context,10),SettingsUi.dp(p.context,12),SettingsUi.dp(p.context,10));
            input.setHint("给这条语音写个备注"); input.setText(initial);
            input.setMinLines(2); input.setMaxLines(4);
            input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(120)});
            p.body.addView(input, new LinearLayout.LayoutParams(-1, -2));
            p.builder.setNegativeButton("取消", (dialog, which) -> {});
            p.show(title, "保存"); watch(back);
            p.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view -> {
                if (busy) { closeWhileBusy(); return; }
                try { validate(); back.run(); }
                catch (Throwable error) { close(); }
            });
            p.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                if (busy) return;
                try { validate(); callback.run(VoiceLibraryStore.note(input.getText().toString())); }
                catch (Throwable error) { input.setError(error instanceof IllegalArgumentException ? error.getMessage() : "保存失败，请重试"); }
            });
        }

        void download(String url, int duration, String note) throws Exception {
            validate();
            Context context = activity.getApplicationContext();
            background(() -> {
                File temp = File.createTempFile("newsoul-voice-", ".audio", context.getCacheDir());
                try {
                    fetch(url, temp);
                    // Refuse HTML/error bodies even when a CDN responds 200.
                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    try {
                        retriever.setDataSource(temp.getAbsolutePath());
                        String millis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                        if (millis == null || Long.parseLong(millis) <= 0) throw new IOException("音频无法读取");
                    } finally { retriever.release(); }
                    try (InputStream input = new FileInputStream(temp)) { store.add(input, note, duration); }
                } finally { java.nio.file.Files.deleteIfExists(temp.toPath()); }
            }, () -> { toast(activity, "已保存到语音库"); library(); });
        }

        void confirmDelete(VoiceLibraryStore.Entry entry) {
            confirmRemoval("删除语音", "删除「" + entry.note + "」？",
                    () -> store.delete(entry.id), () -> details(entry));
        }

        void confirmRemoval(String title, String summary, Work removal, Work back) {
            SettingsUi.MenuPanel p = next();
            p.body.addView(SettingsUi.label(p.context, summary, 16, p.foreground));
            p.builder.setNegativeButton("取消", (dialog, which) -> {});
            p.show(title, "删除"); watch(back);
            p.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view -> {
                if (busy) { closeWhileBusy(); return; }
                try { back.run(); } catch (Throwable error) { close(); }
            });
            p.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                if (busy) return;
                try { validate(); background(removal, this::library); }
                catch (Throwable error) { failure(activity, "删除失败", error); }
            });
        }

        void background(Work work, Work done) {
            if (busy) return;
            busy = true;
            writing = true;
            SettingsUi.MenuPanel p = panel;
            // Dismissal stays available, but cannot undo a write already in progress.
            TextView status = SettingsUi.label(p.context,
                    "操作已提交，关闭后仍会继续；请稍后打开语音库查看结果", 13, p.secondary);
            p.body.addView(status);
            if (p.dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null)
                p.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText("关闭");
            if (p.dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                p.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                p.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("处理中…");
            }
            io.execute(() -> {
                Throwable failure = null;
                try { work.run(); } catch (Throwable error) { failure = error; }
                final Throwable error = failure;
                main.post(() -> {
                    busy = false;
                    writing = false;
                    if (closed || panel != p) return;
                    p.body.removeView(status);
                    if (p.dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null)
                        p.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText("取消");
                    if (p.dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                        p.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        p.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("重试");
                    }
                    try { validate(); if (error != null) throw error; done.run(); }
                    catch (Throwable problem) {
                        if (problem instanceof VoiceLibraryStore.StateException)
                            storageFailure(p, (VoiceLibraryStore.StateException) problem);
                        else failure(activity, "操作未完成，请重试", problem);
                    }
                });
            });
        }

        void storageFailure(SettingsUi.MenuPanel p, VoiceLibraryStore.StateException error) {
            Log.w("SoulHook102", "voice library requires a fresh scan", error);
            TextView message = SettingsUi.label(p.context, "", 14, p.foreground);
            p.body.addView(message);
            SettingsUi.setLiveSummary(message, error.getMessage());
            TextView check = p.dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (check != null) {
                check.setText("重新检查");
                check.setOnClickListener(view -> {
                    if (busy || closed || panel != p) return;
                    try { validate(); library(); }
                    catch (Throwable problem) { failure(activity, "读取语音库失败，请重新打开", problem); }
                });
            }
        }

        void send(VoiceLibraryStore.Entry entry) throws Exception {
            if (busy) return;
            validate(); stop(); busy = true;
            // Separate copy: deleting a library entry must not remove a pending/sent message's audio.
            io.execute(() -> {
                File outgoing = null;
                try {
                    VoiceLibraryStore.Entry current = store.read(entry.id);
                    outgoing = VoiceOutgoingStore.copy(activity.getCacheDir(), current.audio);
                    File path = outgoing;
                    if (!main.post(() -> {
                        boolean submitted = false;
                        try {
                            validate();
                            Object sender = chat.sender.get(fragment);
                            if (sender == null) throw new IllegalStateException("聊天尚未准备好");
                            // Do not retry an ambiguous native submission; it might already have been queued.
                            submitted = true;
                            sendLocal.invoke(sender, path.getAbsolutePath(), current.duration, null);
                            toast(activity, "已提交发送，请查看聊天中的消息状态");
                            Log.i("SoulHook102", "voice library send submitted explicitly");
                        } catch (Throwable error) { failure(activity, "发送未完成，请查看消息状态", error); }
                        finally { if (!submitted) path.delete(); close(); }
                    })) throw new IOException("Chat event loop is unavailable");
                } catch (Throwable error) {
                    if (outgoing != null) outgoing.delete();
                    main.post(() -> { busy = false; if (!closed) failure(activity, error instanceof VoiceOutgoingStore.FullException
                            ? error.getMessage() : "读取语音失败", error); });
                }
            });
        }

        void play(VoiceLibraryStore.Entry entry) throws Exception {
            validate();
            if (player != null) { stop(); return; }
            VoiceLibraryStore.Entry current = store.read(entry.id);
            MediaPlayer next = new MediaPlayer(); player = next;
            next.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            next.setOnPreparedListener(value -> {
                if (closed || player != value) return;
                try { validate(); value.start(); if (playButton != null) playButton.setText("停止试听"); }
                catch (Throwable error) { stop(); }
            });
            next.setOnCompletionListener(value -> stop());
            next.setOnErrorListener((value, what, extra) -> { stop(); toast(activity, "暂时无法试听"); return true; });
            try { next.setDataSource(current.audio.getAbsolutePath()); next.prepareAsync(); }
            catch (Throwable error) { stop(); throw error; }
        }

        void stop() {
            if (player != null) { player.release(); player = null; }
            if (playButton != null) playButton.setText("试听");
        }
    }

    private static TextView action(SettingsUi.MenuPanel panel, String label, Work click) {
        TextView view = SettingsUi.label(panel.context, label, 15, panel.foreground);
        view.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int padding = SettingsUi.dp(panel.context, 12);
        view.setPadding(padding, padding, padding, padding);
        view.setMinHeight(SettingsUi.dp(panel.context, 48));
        view.setSingleLine(false);
        view.setEllipsize(null);
        SettingsUi.buttonSemantics(view);
        panel.appearance.apply(view, UiAppearance.Role.ACTION);
        view.setOnClickListener(v -> {
            try { click.run(); } catch (Throwable error) { failure(panel.activity, "操作未完成，请重新打开语音库", error); }
        });
        return view;
    }

    private static void fetch(String url, File file) throws IOException {
        String current = url;
        for (int redirect = 0; redirect < 6; redirect++) {
            if (!RepeatMessageRules.mediaUrl(current)) throw new IOException("Invalid audio address");
            HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
            connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(false);
            try {
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Invalid redirect");
                    current = new URL(new URL(current), location).toString(); continue;
                }
                if (code != 200) throw new IOException("音频下载失败（" + code + "）");
                if (connection.getContentLengthLong() > VoiceLibraryStore.MAX_BYTES) throw new IOException("语音文件过大");
                try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(file)) {
                    VoiceLibraryStore.copyBounded(in, out);
                }
                return;
            } finally { connection.disconnect(); }
        }
        throw new IOException("Too many redirects");
    }

    private static void failure(Context context, String message, Throwable error) {
        Log.w("SoulHook102", "voice library operation failed; no automatic send retry", error);
        toast(context, error instanceof VoiceLibraryStore.StateException ? error.getMessage() : message);
    }
    private static void toast(Context context, String message) { Toast.makeText(context, message, Toast.LENGTH_SHORT).show(); }
    private static View find(Activity activity, String name) {
        int id = activity.getResources().getIdentifier(name, "id", activity.getPackageName());
        return id == 0 ? null : activity.findViewById(id);
    }
    private static Object call(Object object, String name) throws Exception { return HostBindings.messageMethod(object.getClass(), name).invoke(object); }
    private static Object field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
