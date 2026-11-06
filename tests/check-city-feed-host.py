#!/usr/bin/env python3
"""Check the city page -> list -> adapter contract against local host samples."""
from pathlib import Path
import re
checks=0
for version in ('637','638'):
    root=Path('../shared/host-analysis/research/soul-6.38-audit')/f'smali-{version}'
    def read(name):return (root/(name+'.smali')).read_text()
    def contains(text,needle):
        global checks
        assert needle in text,(version,needle)
        checks+=1
    package='cn.soulapp.android.component.square.location.'
    contains(read(package+'LocationSquareActivity'),'LocationSquareFragment;')
    contains(read(package+'LocationSquareFragment$PagerAdapter'),'PositionPostListFragment;')
    contains(read(package+'PositionPostListFragment'),'PostLightAdapter;-><init>(Landroid/content/Context;Z)V')
    text=read(package+'PostLightAdapter')
    contains(text,'.method public onBindViewHolder(Landroidx/recyclerview/widget/RecyclerView$ViewHolder;I)V')
    contains(text,'.method public i(I)Ljava/lang/Object;')
    item=text.split('.method public i(I)Ljava/lang/Object;',1)[1].split('.end method',1)[0]
    contains(item,'PostLightAdapter;->v(I)I')
    contains(item,'const/4 p1, 0x0')
    contains(text,'instance-of v2, p2, Lcn/soulapp/android/square/post/bean/Post;')
print(f'City feed host: {checks} static checks passed across 6.37/6.38')
