#!/usr/bin/env python3
from pathlib import Path
checks=0
for version in ('637','638'):
    root=Path('../shared/host-analysis/research/soul-6.38-audit')/f'smali-{version}'
    def check(name,token):
        global checks
        assert token in (root/(name+'.smali')).read_text(),(version,name,token)
        checks+=1
    check('cn.soulapp.android.component.square.location.PostLightAdapter','.super Landroidx/recyclerview/widget/RecyclerView$Adapter;')
    check('cn.soulapp.android.component.square.location.PostLightAdapter','.method public getDatas()Ljava/util/List;')
    check('cn.soulapp.android.component.square.location.PostLightAdapter','Lcn/soulapp/android/square/post/viewholder/PostAttachmentViewHolder;')
    check('cn.soulapp.android.square.post.viewholder.PostAttachmentViewHolder','R$id;->key_item_post:I')
    check('cn.soulapp.android.square.post.viewholder.PostAttachmentViewHolder','.method public final getPost()Lcn/soulapp/android/square/post/bean/Post;')
    check('cn.soulapp.android.square.post.viewholder.PostAttachmentViewHolder','.method public final updateLikeState(Lcn/soulapp/android/square/post/bean/Post;)V')
    check('cn.soulapp.android.component.square.location.LocationSquareFragment','requireView().messageButton')
    check('cn.soulapp.android.component.square.location.LocationSquareFragment','.method public getMessageButton()Lcn/soulapp/android/component/square/widget/SquareFloatingButton;')
    check('cn.soulapp.android.component.square.widget.SquareFloatingButton','.method public onVisibilityChanged(Landroid/view/View;I)V')
print(f'City likes host: {checks} static checks passed across 6.37/6.38')
