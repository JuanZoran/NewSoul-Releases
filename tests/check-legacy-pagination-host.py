#!/usr/bin/env python3
"""Verify the exact 4.88 eligibility branch and callback ABI against the supplied host."""
from pathlib import Path
import sys
root = Path(sys.argv[1] if len(sys.argv) > 1 else '../shared/host-analysis/research/soul-4.88-audit')
parser = Path('tests/check-follow-disguise-host.py').read_text().split('\ndef desc')[0]
sys.argv = ['scan', str(root)]
ns = {}
exec(parser, ns)
classes = ns['classes']
page = 'Lcn/soulapp/android/component/square/recommend/TimeLineFragment;'
adapter = 'Lcn/soulapp/android/component/square/main/squarepost/SquareAdapter;'
checks = 0

def check(condition, reason):
    global checks
    assert condition, reason
    checks += 1

fields, methods = classes[page]
for field in [('l', adapter), ('S', 'I')]:
    check(field in fields, f'Pagination field changed: {field}')
for method in [('H2', '()V'), ('e3', '(Z)V'), ('w', '(' + page + ')V')]:
    check(method in methods, f'Pagination method changed: {method}')
for owner, method in [
    ('Lcn/soulapp/android/component/square/recommend/s1;', ('onLoadMore', '()V')),
    ('Lgx/b$c;', ('run', '()V')),
    ('Lgx/b;', ('n', '()V')),
]:
    check(method in classes[owner][1], f'Pagination caller changed: {owner} {method}')
source = (root / 'smali/cn.soulapp.android.component.square.recommend.TimeLineFragment.smali').read_text()
body = source.split('.method private synthetic H2()V', 1)[1].split('.end method', 1)[0]
for fragment in ['->getData()Ljava/util/List;', '->size()I', 'const/4 v1, 0x5',
                 'if-ge v0, v1, :cond_1e', 'const-string v1, "7"',
                 ':cond_1e\n    const/4 v0, 0x3', '->S:I',
                 'const/4 v0, 0x0', '->e3(Z)V']:
    check(fragment in body, f'Eligibility branch changed: {fragment}')
bridge = source.split('.method public static synthetic w(', 1)[1].split('.end method', 1)[0]
check('->H2()V' in bridge, 'Callback bridge changed')
listener = (root / 'smali/cn.soulapp.android.component.square.recommend.s1.smali').read_text()
check('->w(' + page + ')V' in listener, 'Listener bridge changed')
print(f'Legacy pagination: {checks} real DEX and native eligibility checks passed')
