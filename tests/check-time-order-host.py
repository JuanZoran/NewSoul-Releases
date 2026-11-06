#!/usr/bin/env python3
from pathlib import Path
import sys
checks = 0
parser = Path('tests/check-follow-disguise-host.py').read_text().split('\ndef desc')[0]
for version in ['6.37', '6.38']:
    sys.argv = ['scan', str(Path('../shared/host-analysis/research/soul-6.38-audit') / version)]
    ns = {}; exec(parser, ns); classes = ns['classes']
    def check(name, fields=(), methods=()):
        global checks
        fs, ms = classes['L' + name.replace('.', '/') + ';']
        for f in fields: assert f in fs, (version, name, f); checks += 1
        for m in methods: assert m in ms, (version, name, m); checks += 1
    check('cn.soulapp.android.square.post.bean.Post', fields=[('createTime', 'J')])
    check('cn.soulapp.android.component.square.main.squarepost.SquareAdapter', methods=[('onAttachedToRecyclerView', '(Landroidx/recyclerview/widget/RecyclerView;)V')])
    check('com.chad.library.adapter.base.BaseQuickAdapter', fields=[('data','Ljava/util/List;')], methods=[('setList','(Ljava/util/Collection;)V'),('addData','(Ljava/util/Collection;)V'),('setNewInstance','(Ljava/util/List;)V'),('getHeaderLayoutCount','()I')])
    check('androidx.recyclerview.widget.RecyclerView', methods=[('getAdapter','()Landroidx/recyclerview/widget/RecyclerView$Adapter;'),('isComputingLayout','()Z'),('getScrollState','()I')])
    check('androidx.recyclerview.widget.RecyclerView$Adapter', methods=[('notifyDataSetChanged','()V'),('notifyItemRangeInserted','(II)V'),('notifyItemRangeRemoved','(II)V'),('notifyItemMoved','(II)V'),('notifyItemRangeChanged','(II)V')])
print(f'Time order: {checks} exact host signature checks passed')
