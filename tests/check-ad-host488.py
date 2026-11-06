#!/usr/bin/env python3
"""Check ad ABI and placement evidence against the user's 4.88 DEX audit."""
from pathlib import Path
import json
import sys

root=Path(sys.argv[1] if len(sys.argv)>1 else '../shared/host-analysis/research/soul-4.88-audit')
classes={}
for folder in ['smali','extra6','extra7']:
    data=json.loads((root/folder/'index.json').read_text())
    for name,value in data.items():
        classes.setdefault(name,{}).update(value)
parser=Path('tests/check-follow-disguise-host.py').read_text().split('\ndef desc')[0]
sys.argv=['ad-check',str(root)]
symbols={}
exec(parser,symbols)
members=symbols['classes']
def desc(name):return 'L'+name.replace('.','/')+';'
def method(owner,signature):
    split=signature.index('(')
    assert (signature[:split],signature[split:]) in members[desc(owner)][1],(owner,signature)
def field(owner,name,kind):
    assert (name,kind) in members[desc(owner)][0],(owner,name,kind)
checks=0
for owner,sf,lf,cf in [('c4.c','a','b','e'),('g4.d','a','b','f'),('e4.g','b','c','a')]:
    field(owner,sf,'Ld3/a;')
    listener='Lcn/soulapp/android/ad/core/services/plaforms/listener/'+('SoulAdRewardLoaderListener;' if owner=='e4.g' else 'SoulAdRequestListener;')
    field(owner,lf,listener)
    field(owner,cf,'Ljava/lang/ref/WeakReference;')
    method(owner,'loadAds()V');checks+=4
method('d3.a','c()Ljava/lang/String;');checks+=1
for owner,name,sig in [
 ('cn.soulapp.android.component.square.tag.TagSquareFragment','Z1','()V'),
 ('cn.soulapp.android.component.square.videoplay.VideoPlayPreviewFragmentB','N0','(ZLio/reactivex/SingleEmitter;)V'),
 ('cn.soulapp.android.component.square.recommend.TimeLineFragment','u2','(ZLio/reactivex/ObservableEmitter;)V'),
 ('cn.soulapp.android.component.square.post.base.detail.PostDetailActivity','t3','()V'),
 ('cn.soulapp.android.component.square.search.SearchResultFragmentA','s','(Ljava/lang/String;I)V'),
 ('ka.d','h','(ZLio/reactivex/SingleEmitter;)V'),
 ('c3.a','d','(Ljava/lang/String;Lcn/soulapp/android/ad/callback/SimpleCallBack;)V'),
 ('c3.c','e','(Ljava/lang/String;)V'),
 ('cn.soulapp.android.ad.h5.moduel.RewardModule','preloadRewardAd','(Ljava/lang/String;Ljava/lang/String;)V'),
 ('y4.c','action_event_requestRewardAd','(Ljava/lang/Object;Lcn/soulapp/android/miniprogram/core/bridge/CompletionHandler;)V'),
 ('j3.a','v0','(Landroidx/fragment/app/FragmentManager;Landroid/content/Context;)V')
]:
    method(owner,name+sig);checks+=1
# No modern placement renderer or reward-page owner exists in this full class index.
for name in ['FeedWebExpressView','GroupBannerExpressView','PlantGameExpressView','PlantGameTagExpressView',
             'MeCellExpressView','EnvelopeMessageFeedExpressView','SystemNoticeFeedExpressView']:
    assert desc('cn.soulapp.android.ad.soulad.ad.views.express.render.'+name) not in classes,name;checks+=1
for name in ['cn.soulapp.android.component.chat.limitdialog.RewardAdHelper',
             'cn.soulapp.android.component.square.video.drama.o0']:
    assert desc(name) not in classes,name;checks+=1
# The older slot 7/8 paths show a native dialog; slot 9 alone constructs the search top banner.
tag=(root/'smali/cn.soulapp.android.component.square.tag.TagSquareFragment$n.smali').read_text()
search=(root/'smali/cn.soulapp.android.component.square.search.SearchResultFragmentA$c.smali').read_text()
assert 'Lj3/a;->v0(Landroidx/fragment/app/FragmentManager;Landroid/content/Context;)V' in tag
assert 'SearchBannerAdView;-><init>' in search and 'Lj3/a;->v0(' in search
checks+=2
print(f'{checks} Soul 4.88 ad ABI and placement checks passed')
