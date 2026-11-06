#!/usr/bin/env python3
"""Read-only signature/semantic check against the locally disassembled host samples."""
from pathlib import Path
import re
base = Path('../shared/host-analysis/research/soul-6.38-audit')
checks = 0
for version in ('637', '638'):
    root = base / f'smali-{version}'
    def read(name):
        return (root / (name + '.smali')).read_text()
    def has(text, regex):
        global checks
        assert re.search(regex, text, re.M), (version, regex)
        checks += 1
    listener = 'Lcn/soulapp/android/lib/location/LocationListener;'
    data = 'Lcn/soulapp/android/lib/location/model/LocationData;'
    option = 'Lcn/soulapp/android/lib/location/model/LocationOption;'
    for cls in ('cn.soulapp.android.lib.location.bdmap.BDMapLocationServiceImpl', 'cn.soulapp.android.lib.lbs.bd.SoulBaiduLocationClient'):
        text = read(cls)
        signatures = [f'{name}(){data}' for name in ('getLastLocation', 'getInnerLastLocation', 'getMLastLocation')]
        signatures += [f'{name}({listener})V' for name in ('getLocationWithCache', 'stopLocation')]
        signatures += [f'callback2Listener(I{data})V']
        if cls.endswith('SoulBaiduLocationClient'):
            signatures += [f'startLocation({option}{listener})V', f'startLocation({option}{listener}Z)V', f'callbackCachedLocation({listener})V']
        else:
            signatures += [f'startLocation({listener})V']
        for signature in signatures:
            has(text, r'^\.method .* ' + re.escape(signature) + '$')
        has(text, r'"bd09ll"')
    for fragment_name, adapter_name in (('main.SquareFragment', 'SquarePagerAdapter'), ('main.newui.SquareFragmentV2', 'SquarePagerAdapterV2')):
        fragment = read('cn.soulapp.android.component.square.' + fragment_name)
        for field in ('n:Lcn/soulapp/android/component/square/main/' + adapter_name + ';', 'o:Lcom/google/android/material/tabs/TabLayout;'):
            has(fragment, r'^\.field .* ' + re.escape(field) + '$')
        for method in ('initTabLayout()V','onResume()V'):
            has(fragment, r'^\.method .* ' + re.escape(method) + '$')
        has(read('cn.soulapp.android.component.square.main.' + adapter_name), r'^\.method .* b\(I\)Lcn/soulapp/android/component/square/bean/SquareMainTab;$')
    has(read('cn.soulapp.android.component.square.bean.SquareMainTab'), r'^\.method .* isSameCityTab\(\)Z$')
    package = 'cn.soulapp.android.component.publish.ui.position.'
    has(read(package + 'NewPoiActivityB'), r'^\.method .* bindEvent\(\)V$')
    presenter = read(package + 'b')
    has(presenter, r'^\.method .* A\(\)V$')
    has(presenter, r'^\.method .* F\(Lcn/soulapp/android/lib/common/location/bean/Poi;\)V$')
    has(presenter, r'MetaSelectCityEvent')
    for cls, method in (('PoiChatAdapterB', 'e'), ('PoiChatSearchAdapterB', 'd')):
        signature = method+'(L'+(package+cls).replace('.','/')+'$OnPoiItemClickListener;)V'
        has(read(package + cls), r'^\.method .* ' + re.escape(signature) + '$')
    has(read('cn.soulapp.imlib.j0'), r'^\.method .* static g\(\)Ljava/lang/String;$')
print(f'Location host signatures: {checks} checks passed across 6.37/6.38; static evidence only')
