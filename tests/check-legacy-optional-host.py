#!/usr/bin/env python3
"""Distinguish missing 4.88 routes from renamed hooks using every supplied DEX."""
from pathlib import Path
import sys

root = Path('../shared/host-analysis/research/soul-4.88-audit')
assert len(list(root.glob('*.dex'))) == 21, 'Incomplete legacy DEX input'
parser = Path('tests/check-follow-disguise-host.py').read_text().split('\ndef desc')[0]
sys.argv = ['scan', str(root)]
scope = {}
exec(parser, scope)
classes = scope['classes']
checks = 0

def check(condition, reason):
    global checks
    assert condition, reason
    checks += 1

for name in ('cn/soulapp/android/component/chat/business/g$a',
             'com/soulapp/soulgift/api/IActivityOpenApi',
             'cn/soulapp/android/component/chat/snapchat/PrivacyProtectPreviewActivity',
             'cn/soulapp/android/component/chat/snapchat/SnapChatActivity',
             'cn/soulapp/android/component/home/lovebell/bean/LoveBellProConfig',
             'cn/soulapp/android/component/home/lovebell/LoveBellProGiftDialog'):
    check('L' + name + ';' not in classes, f'Unexpected legacy route: {name}')
for name in ('relationCheckNotice', 'getShowPopup'):
    check(not any(method[0] == name for _, methods in classes.values() for method in methods),
          f'Potential replacement method requires review: {name}')
check(not any(name.startswith('Lcn/soulapp/android/component/chat/snapchat/') for name in classes),
      'Potential privacy preview requires review')
need_pop = [name for name, (fields, _) in classes.items() if ('needPop', 'Ljava/lang/Boolean;') in fields]
check(need_pop == ['Lcn/soulapp/cpnt_voiceparty/bean/FlowCardBean;'], 'needPop moved into another scene')
service = classes['Lcn/android/lib/increase/service/IncreaseService;'][1]
check(('showSecrectInviteDialog', '(Lcn/android/lib/increase/secretmoment/bean/AuthorizeBean;Ljava/lang/String;)V') in service,
      'Old secret-moment invitation contract changed')
increase = (root / 'smali/cn.soulapp.android.component.increase.IncreaseServiceImpl.smali').read_text()
check('SecrectFriendInviteDialog$a;->a(Lcn/android/lib/increase/secretmoment/bean/AuthorizeBean;Ljava/lang/String;)' in increase,
      'Secret-moment invitation route changed')
ordinary = (root / 'smali/cn.soulapp.android.component.chat.widget.SPicPreviewActivity.smali').read_text()
check('limitViewTimes' not in ordinary and 'snapChat' not in ordinary,
      'Ordinary image preview needs privacy-route review')
print(f'Legacy optional scenes: {checks} absence/scope checks passed over {len(classes)} classes in 21 DEX files')
