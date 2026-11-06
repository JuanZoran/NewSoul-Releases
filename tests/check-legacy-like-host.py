#!/usr/bin/env python3
"""Check 4.88 social actions against actual DEX members, without invoking host actions."""
from pathlib import Path
import sys
root=Path(sys.argv[1] if len(sys.argv)>1 else '../shared/host-analysis/research/soul-4.88-audit')
parser=Path('tests/check-follow-disguise-host.py').read_text().split('\ndef desc')[0]
sys.argv=['scan',str(root)]
ns={};exec(parser,ns);classes=ns['classes']
checks=0

def desc(name):return 'L'+name.replace('.','/')+';'
def check(name,fields=(),methods=()):
    global checks
    fs,ms=classes[desc(name)]
    for value in fields:assert value in fs,(name,'field',value);checks+=1
    for value in methods:assert value in ms,(name,'method',value);checks+=1

post=desc('cn.soulapp.android.square.post.bean.Post')
user=desc('com.soul.component.componentlib.service.user.bean.User')
footer=desc('cn.soulapp.android.component.square.main.squarepost.footer.Footer')
config=desc('cn.soulapp.android.component.square.main.squarepost.config.Config')
check('cn.soulapp.android.square.post.bean.Post',[('id','J'),('authorIdEcpt','Ljava/lang/String;'),('liked','Z')],[('n','()Z'),('c','()Ljava/lang/Object;'),('g','()Ljava/lang/String;')])
check('cn.soulapp.android.component.square.post.q0',methods=[('<init>','('+post+')V'),('h','(I)Lf60/g;')])
check('f60.g',methods=[('subscribe','(Lio/reactivex/functions/Consumer;Lio/reactivex/functions/Consumer;)Lio/reactivex/disposables/Disposable;')])
check('com.chad.library.adapter.base.BaseQuickAdapter',methods=[('getData','()Ljava/util/List;')])
check('com.lufficc.lightadapter.LightAdapter',methods=[('j','()Ljava/util/List;'),('getHeaders','()Ljava/util/List;')])
check('com.jude.easyrecyclerview.adapter.RecyclerArrayAdapter',methods=[('getAllData','()Ljava/util/List;')])
check('cn.soulapp.android.component.square.main.PostVH',[('post',post),('config',config)],[('getLayoutPositionProxy','()I'),('onBind','('+post+'I)V')])
check('cn.soulapp.android.component.square.main.squarepost.config.Config',methods=[('k','()'+footer)])
check('cn.soulapp.android.component.square.main.squarepost.footer.Footer',methods=[('getPost','()'+post),('onBindViewHolder','(I'+post+')V')])
check('com.soul.component.componentlib.service.user.bean.User',[('userIdEcpt','Ljava/lang/String;')])
check('l9.a',methods=[('t','()Ljava/lang/String;')])
for page,field in [('cn.soulapp.android.component.home.user.fragment.PageUserHomeFragment','z0'),('cn.soulapp.android.component.home.meta.user.MetaPageUserHomeFragment','y')]:
    check(page,[(field,user)],[(name,'()V') for name in ('onResume','onPause','onDestroyView')])
check('cn.soulapp.android.component.square.widget.SquareFloatingButton',methods=[('<init>','(Landroid/content/Context;Landroid/util/AttributeSet;I)V'),('onVisibilityChanged','(Landroid/view/View;I)V')])
check('cn.soulapp.android.component.startup.main.MainActivity',methods=[('onCreate','(Landroid/os/Bundle;)V')])
check('cn.soulapp.android.component.home.user.UserHomeActivity',methods=[('J','(Ljava/lang/String;Ljava/lang/String;)V')])
base='cn.soulapp.android.square.BaseSeedsDialogFragment';sheet='cn.soulapp.android.square.SeedsShareDialogFragment';op=desc(base+'$Operate')
check(base,[('n','Ljava/util/List;'),('o',desc(base+'$onSubmitListener'))])
check(sheet,[('A',user),('B',post),('z','I'),('L','I'),('v','Luo/d;')],[('initViews','(Landroid/view/View;)V'),('l0','('+op+'Landroid/view/View;I)Z')])
check(base+'$Operate',[('a','I'),('b','Ljava/lang/String;'),('f','I')],[('<init>','(I[Lcn/soulapp/android/square/bean/ReasonEntry;)V')])
check('uo.d$a',[('a','Landroid/widget/TextView;'),('c','Landroid/widget/ImageView;')],[('a','('+op+')V')])
for callback,page in [('cn.soulapp.android.component.home.user.fragment.t0','cn.soulapp.android.component.home.user.fragment.PageUserHomeFragment'),('cn.soulapp.android.component.home.meta.user.c','cn.soulapp.android.component.home.meta.user.MetaPageUserHomeFragment')]:
    fs,_=classes[desc(callback)]
    assert sum(value==desc(base) for _,value in fs)==1,(callback,'dialog capture')
    assert sum(value==desc(page) for _,value in fs)==1,(callback,'page capture')
    checks+=2
for absent in ['cn.soulapp.android.square.api.user.UserHomePostFragment','cn.soulapp.android.component.square.post.SquareUserAdapter','cn.soulapp.android.component.square.location.PostLightAdapter','cn.soulapp.android.square.post.viewholder.PostAttachmentViewHolder']:
    assert desc(absent) not in classes,('unexpected modern page',absent);checks+=1
print(f'Legacy social ABI: {checks} exact DEX checks passed; modern pages absent, old home/city present')
