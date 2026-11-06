#!/usr/bin/env python3
"""Verify the host members used by the native switch adapter in both supported APKs."""
from pathlib import Path
import sys
parser = Path('tests/check-follow-disguise-host.py').read_text().split('\ndef desc')[0]
for version in ('6.37', '6.38'):
    sys.argv = ['check', '../shared/host-analysis/research/soul-6.38-audit/' + version]
    ns = {}
    exec(parser, ns)
    _, methods = ns['classes']['Lcn/android/lib/soul_view/switchcompat/SoulSwitchCompat;']
    for member in [('<init>', '(Landroid/content/Context;)V'),
                   ('setCheckedImmediatelyNoEvent', '(Z)V'), ('setChecked', '(Z)V'),
                   ('setTintColor', '(I)V'),
                   ('setOnCheckedChangeListener', '(Landroid/widget/CompoundButton$OnCheckedChangeListener;)V')]:
        assert member in methods, (version, member)
    print(version + ': native switch constructor, state, tint and callback signatures passed')
