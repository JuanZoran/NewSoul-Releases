#!/usr/bin/env python3
"""Verify exact runtime member names against Soul DEX, ignoring JADX/Kotlin display aliases."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path('../shared/host-analysis').resolve()))
from dex_members import read_members

classes = read_members(sys.argv[1] if len(sys.argv) > 1 else '../shared/host-analysis/research/soul-6.37-dex')

def desc(name): return 'L' + name.replace('.', '/') + ';'
def check(name, fields=(), methods=()):
    actual_fields, actual_methods = classes[desc(name)]
    for member in fields: assert member in actual_fields, (name, 'field', member)
    for member in methods: assert member in actual_methods, (name, 'method', member)
    print(name.rsplit('.', 1)[-1] + ': runtime symbols verified')

version = sys.argv[2] if len(sys.argv) > 2 else '6.37'
assert version in ('6.37', '6.38', '6.39'), version
follow_method, days_method = ('S1', 'Q1') if version == '6.37' else (('V1', 'T1') if version == '6.39' else ('T1', 'R1'))
base = 'cn.soulapp.android.component.home.'
user = desc('com.soul.component.componentlib.service.user.bean.User')
count = desc(base + 'api.user.user.bean.UserFollowCount')
holder = desc(base + 'revision.RevisionProfileProvider$ViewHolder')
binding = desc(base + 'databinding.CUsrLayoutRevisionProfileBinding')
meta_binding = desc(base + 'databinding.CUsrMetaFollowFansContainerBinding')
check(base + 'api.user.user.bean.UserFollowCount', [('followNum', 'I'), ('beFollowNum', 'I'), ('beViewNum', 'I')])
check(base + 'revision.RevisionProfileProvider', [('b', 'I'), ('d', count)], [(follow_method, '(' + user + holder + ')V')])
check(base + 'revision.RevisionProfileProvider$ViewHolder', methods=[('b', '()' + binding)])
check(base + 'databinding.CUsrLayoutRevisionProfileBinding', [('o', 'Landroid/widget/TextView;'), ('q', 'Landroid/widget/TextView;'), ('g0', 'Landroid/widget/TextView;'), ('h0', 'Landroid/widget/TextView;')])
check(base + 'meta.view.MetaFollowFansContainerView', [('k', 'I'), ('b', count)], [('w', '(' + user + meta_binding + ')V')])
check(base + 'databinding.CUsrMetaFollowFansContainerBinding', [('e', 'Landroidx/appcompat/widget/AppCompatTextView;'), ('g', 'Landroidx/appcompat/widget/AppCompatTextView;'), ('q', 'Landroidx/appcompat/widget/AppCompatTextView;'), ('r', 'Landroidx/appcompat/widget/AppCompatTextView;')])
check(base + 'me.UserHomeHeaderHelper', [('M', 'I'), ('j', 'Landroid/widget/TextView;'), ('l', 'Landroid/widget/TextView;'), ('n', 'Landroid/widget/TextView;'), ('o', 'Landroid/widget/TextView;'), ('q', 'Landroid/widget/TextView;'), ('r', 'Landroid/widget/TextView;')], [('setUserFollowData', '(' + count + ')V')])
check('cn.soulapp.android.client.component.middle.platform.utils.' + ('r1' if version == '6.39' else 'm2'), methods=[('e', '(I)Ljava/lang/String;'), ('f', '(I)Ljava/lang/String;')])

check('com.soul.component.componentlib.service.user.bean.User', [('registerDay', 'Ljava/lang/String;')])
check(base + 'revision.RevisionProfileProvider', [('b', 'I')], [(days_method, '(' + user + holder + ')V')])
check(base + 'databinding.CUsrLayoutRevisionProfileBinding', [('V', 'Landroid/widget/TextView;')])
day_binding = desc(base + 'databinding.CUsrLayoutMeInfoNewBinding')
check(base + 'meta.me2.userinfo.UserInfoProvider', [('b', 'I')], [('t0', '(' + user + day_binding + ')V')])
check(base + 'databinding.CUsrLayoutMeInfoNewBinding', [('y', 'Landroidx/appcompat/widget/AppCompatTextView;')])
