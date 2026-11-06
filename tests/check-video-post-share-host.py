#!/usr/bin/env python3
"""Check native media and post-card dispatch contracts against both local Soul DEX disassemblies."""
from pathlib import Path
import re

checks = 0

def check(ok, message):
    global checks
    assert ok, message
    checks += 1

for version in ('637', '638'):
    root = Path(f'../shared/host-analysis/research/soul-6.38-audit/smali-{version}')
    def read(name): return (root / (name + '.smali')).read_text()
    def method(text, name, signature):
        match = re.search(r'^\.method[^\n]* ' + re.escape(name + signature) + r'\n(.*?)^\.end method', text, re.M | re.S)
        check(match is not None, f'{version}: missing {name}{signature}')
        return match[1]
    video = read('cn.soulapp.imlib.msg.chat.VideoMsg')
    method(video, '<init>', '(Ljava/lang/String;Ljava/lang/String;II)V')
    for name, descriptor in [('url','Ljava/lang/String;'),('localUrl','Ljava/lang/String;'),('width','I'),('height','I')]:
        check(f'.field public {name}:{descriptor}' in video, f'{version}: video {name}')
    sender = read('cn.soulapp.android.component.chat.utils.n6')
    post = 'Lcn/soulapp/android/square/post/bean/Post;'
    body = method(sender, 'g', '(' + post + 'Ljava/lang/String;)V')
    for marker in ('StringMsg;-><init>(Ljava/lang/String;)', 'imlib/utils/j;->b(Ljava/lang/Object;)',
                   'setMsgType(I)', 'const/16 v0, 0x20', '"postIdEcpt"', '"MSG_TYPE_KEY"', '"POST"', '"MSG_TRANS_DATA"'):
        check(marker in body, f'{version}: native post card changed: {marker}')
    h = method(sender, 'h', '(Lcn/soulapp/imlib/msg/ImMessage;)V')
    check('ServerMessageSender;->n' in h, f'{version}: native server dispatch changed')
    check('.field public static final a:Lcn/soulapp/android/component/chat/utils/n6;' in sender, f'{version}: singleton')
    serializer = read('cn.soulapp.imlib.utils.j')
    method(serializer, 'b', '(Ljava/lang/Object;)Ljava/lang/String;')
    method(serializer, 'd', '(Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;')
    picker = read('cn.soulapp.android.component.chat.SelectConversationActivity')
    remote = method(picker, 'U', '(Ljava/lang/String;Ljava/util/Map;Ljava/util/Map;)V')
    check('VideoMsg;-><init>(Ljava/lang/String;Ljava/lang/String;II)V' in remote, f'{version}: remote video constructor')
    check('ChatManager;->q0' in remote and 'setMsgType(I)' in remote, f'{version}: remote video dispatch')
    menu = read('cn.soulapp.android.square.SeedsShareDialogFragment')
    method(menu, 'P0', '(Lcn/soulapp/android/square/BaseSeedsDialogFragment$Operate;Landroid/view/View;I)Z')
    method(menu, 'initViews', '(Landroid/view/View;)V')
    for marker in ('.field private E:'+post, '.field private C:I'):
        check(marker in menu, f'{version}: post panel {marker}')
    holder = read('cn.soulapp.android.square.adapter.TypeAdapter$a')
    method(holder, 'a', '(Lcn/soulapp/android/square/BaseSeedsDialogFragment$Operate;)V')
    check('R$string;->p:I' in read('cn.soulapp.android.square.BaseSeedsDialogFragment$Operate'), f'{version}: native forward operation title')
    print(f'Soul {version}: video payload, native post sender and menu contracts passed')

# The 4.88 contracts have different obfuscated owners and a different relay gate.
version = '488'
root = Path('../shared/host-analysis/research/soul-4.88-audit')
def read(name):
    hits = [p / (name + '.smali') for p in (root / 'smali', root / 'extra3', root / 'extra4', root / 'extra5')]
    found = [p for p in hits if p.is_file()]
    check(len(found) == 1, f'{version}: missing or ambiguous disassembly {name}')
    return found[0].read_text()
post = 'Lcn/soulapp/android/square/post/bean/Post;'
chat = 'Lcn/soulapp/imlib/msg/chat/ChatMessage;'
im = 'Lcn/soulapp/imlib/msg/ImMessage;'
op = 'Lcn/soulapp/android/square/BaseSeedsDialogFragment$Operate;'
base = 'Lcn/soulapp/android/component/chat/fragment/BaseConversationFragment;'
view = 'Landroid/view/View;'
sender = read('cn.soulapp.android.component.chat.utils.t3')
body = method(sender, 'g', '(' + post + 'Ljava/lang/String;)V')
for marker in ('StringMsg;-><init>(Ljava/lang/String;)', 'Lks/i;->b(Ljava/lang/Object;)',
               'ChatMessage;->A(I)', 'const/16 v0, 0x20', '"postIdEcpt"', '"MSG_TYPE_KEY"',
               '"POST"', '"MSG_TRANS_DATA"', 'ImMessage;->c(', 'ServerMessageSender;->l'):
    check(marker in body, f'{version}: native post card changed: {marker}')
check('.field public static final a:Lcn/soulapp/android/component/chat/utils/t3;' in sender, f'{version}: singleton')
serializer = read('ks.i')
method(serializer, 'b', '(Ljava/lang/Object;)Ljava/lang/String;')
method(serializer, 'd', '(Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;')
menu = read('cn.soulapp.android.square.SeedsShareDialogFragment')
method(menu, 'l0', '(' + op + view + 'I)Z')
method(menu, 'initViews', '(' + view + ')V')
gate = method(menu, 'P0', '()Z')
check('Post;->relay:Z' in gate and 'xor-int/2addr v0, v1' in gate, f'{version}: relay=true means restricted')
renderer = method(menu, 'j0', '(' + view + ')V')
check(renderer.count('SeedsShareDialogFragment;->P0()Z') == 2, f'{version}: native recipient/platform row gates')
for marker in ('.field private B:' + post, '.field private z:I', '.field private A:Lcom/soul/component/componentlib/service/user/bean/User;'):
    check(marker in menu, f'{version}: post panel {marker}')
operation = read('cn.soulapp.android.square.BaseSeedsDialogFragment$Operate')
method(operation, '<init>', '(I[Lcn/soulapp/android/square/bean/ReasonEntry;)V')
for marker in ('.field public static final h:[I', '.field public static final i:[Ljava/lang/String;', '.field public a:I', '.field public b:Ljava/lang/String;', '.field public c:Z'):
    check(marker in operation, f'{version}: operation {marker}')
method(read('uo.d$a'), 'a', '(' + op + ')V')
picker = read('cn.soulapp.android.component.chat.SelectConversationActivity')
for name, sig in [('F','(I)V'),('E','(' + view + ')V'),('S','()V'),('D','(Ljava/lang/Object;)V'),
                  ('onClick','(' + view + ')V'),('init','(Landroid/os/Bundle;)V'),
                  ('loadConversationList','(Ljava/util/List;Ljava/util/List;Z)V')]:
    method(picker, name, sig)
for marker in ('.field private b:Lcn/soulapp/android/component/chat/adapter/ConversationAdapter;', '.field private e:Landroid/widget/TextView;'):
    check(marker in picker, f'{version}: picker {marker}')
presenter = read('ab.f1')
toggle = method(presenter, 's1', '(I)V')
check('Lab/f1;->p:Ljava/util/concurrent/CopyOnWriteArrayList;' in toggle, f'{version}: toggle uses original presenter index')
method(presenter, 'Z', '()Ljava/util/Map;')
helper = read('cn.soulapp.android.component.chat.utils.o')
method(helper, 'd', '(' + base + view + im + ')Ljava/util/List;')
method(helper, 'f', '(' + im + 'Landroid/content/Context;' + base + 'Lab/a0;' + view + 'I' + view
       + 'Lcn/soulapp/android/component/chat/window/ChatLongClickPopUp$a;I)V')
message = read('cn.soulapp.imlib.msg.ImMessage')
method(message, 'c', '(' + chat + 'Ljava/lang/String;)' + im)
ordinary = method(read('cn.soulapp.imlib.ChatManager'), 'b0', '(' + im + ')V')
check('onMessagePreSend' in ordinary and 'onMessageSend' in ordinary, f'{version}: ordinary send owns listener gates')
sender = read('cn.soulapp.android.component.chat.utils.t2')
repeat = method(sender, 'a1', '(' + im + ')V')
check('t2;->b1(' in repeat, f'{version}: repeat forwards to native pre-send')
pre_send = method(sender, 'b1', '(' + im + 'Z)V')
check('ChatManager;->b0(' in pre_send, f'{version}: native pre-send forwards to ordinary sender')
print('Soul 488: message/post sender, recipient picker, native menus and relay contracts passed')
print(f'{checks} host contract checks passed')
