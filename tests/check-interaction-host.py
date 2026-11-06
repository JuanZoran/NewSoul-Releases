#!/usr/bin/env python3
"""Exact DEX signatures for the reference-derived controls; no device effects."""
from pathlib import Path
import sys
checks=0
parser=Path('tests/check-follow-disguise-host.py').read_text().split('\ndef desc')[0]
for version in ['6.37','6.38']:
    sys.argv=['scan',str(Path('../shared/host-analysis/research/soul-6.38-audit')/version)]
    ns={};exec(parser,ns);classes=ns['classes']
    def check(c,fields=(),methods=()):
        global checks
        fs,ms=classes['L'+c.replace('.','/')+';']
        for f in fields: assert f in fs,(version,c,f);checks+=1
        for m in methods: assert m in ms,(version,c,m);checks+=1
    check('cn.soulapp.imlib.ChatManager',methods=[('p0','(Lcn/soulapp/imlib/msg/ImMessage;Z)V'),('o0','(Lcn/soulapp/imlib/msg/ImMessage;)V')])
    check('cn.soulapp.imlib.msg.ImMessage',methods=[('getTo','()Ljava/lang/String;'),('getMsgType','()I'),('getChatMessage','()Lcn/soulapp/imlib/msg/chat/ChatMessage;'),('getTransCmd','()Lcn/soulapp/imlib/msg/transcmd/TransCmdMsg;')])
    check('cn.soulapp.imlib.msg.transcmd.TransCmdMsg',fields=[('messageType','Ljava/lang/String;')],methods=[('getParamJson','()Ljava/lang/String;')])
    check('cn.soulapp.imlib.msg.chat.ChatMessage',methods=[('getMsgType','()I')])
    check('cn.soulapp.android.component.chat.business.g$a',methods=[('a','(Lcom/soulapp/soulgift/bean/CommonDataBo;)V')])
    check('cn.soulapp.android.lib.photopicker.manager.AlbumConfig',methods=[('getMaxSelectNum','()I'),('getMaxVideoSelectNum','()I')])
    check('cn.soulapp.android.square.post.bean.Post',fields=[('superVIP','Z')])
    check('cn.soulapp.android.square.api.user.UserHomePostFragment',methods=[('onResume','()V'),('onPause','()V')])
    root=Path('../shared/host-analysis/research/soul-6.38-audit')/('smali-'+version.replace('.',''))
    text=(root/'cn.soulapp.android.square.api.user.UserHomePostFragment.smali').read_text()
    assert 'Lcn/soulapp/android/component/square/post/SquareUserAdapter;' in text;checks+=1
    text=(root/'cn.soulapp.android.component.square.post.SquareUserAdapter.smali').read_text()
    assert '.super Lcn/soulapp/android/square/component/BaseSquareAdapter;' in text;checks+=1
print(f'Interaction controls: {checks} exact host checks passed across 6.37/6.38')
