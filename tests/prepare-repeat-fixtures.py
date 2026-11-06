#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1])
root.mkdir(parents=True,exist_ok=True)
fixtures={
'TextMsg':'public String text; public TextMsg(String value) { text=value; }',
'ImgMsg':'public String imageUrl, imageLocalPath; public int imageW, imageH;',
'ImgMsgs':'public java.util.List<ImgMsg> imgMsgList; public ImgMsgs(java.util.List<ImgMsg> value) { imgMsgList=value; }',
'VideoMsg':'public String url,localUrl; public int width,height; public VideoMsg(String u,String l,int w,int h){url=u;localUrl=l;width=w;height=h;}',
'AudioMsg':'public String url,localUrl,word; public int duration; public AudioMsg(String u,String l,int d,String w) {url=u;localUrl=l;duration=d;word=w;}',
'ChatMessage':'public int type; public Object body; public int getMsgType(){return type;} public Object getMsgContent(){return body;}'
}
for name,body in fixtures.items():
 (root/(name+'.java')).write_text('package cn.soulapp.imlib.msg.chat; public class '+name+' implements java.io.Serializable {'+body+'}')
