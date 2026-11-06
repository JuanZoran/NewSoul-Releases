#!/usr/bin/env python3
"""Exercise shared header selection against native modern/time/medal structures."""
from pathlib import Path
import subprocess
import tempfile
source = Path('app/src/main/java/dev/local/newsoul/FeedHeaderViews.java').read_text()
source = '\n'.join(line for line in source.splitlines() if not line.startswith(('package ', 'import ')))
harness = r'''
import java.util.*;
class Resources { int getIdentifier(String name,String type,String pkg){return name.hashCode();} }
class View {
 static final int VISIBLE=0,GONE=8;int visibility,id,width=222,left,paddingLeft,paddingRight;ViewGroup parent;Object tag;
 final Map<Integer,View> ids=new HashMap<>();
 Resources getResources(){return new Resources();}
 View findViewById(int id){return ids.get(id);}
 int getVisibility(){return visibility;}Object getParent(){return parent;}Object getTag(){return tag;}
 int getId(){return id;}int getWidth(){return width;}int getLeft(){return left;}int getPaddingLeft(){return paddingLeft;}int getPaddingRight(){return paddingRight;}
 void name(String name,View view){view.id=name.hashCode();ids.put(view.id,view);}
}
class ViewGroup extends View {
 final List<View> children=new ArrayList<>();
 void add(View view){children.add(view);view.parent=this;}
 int getChildCount(){return children.size();}View getChildAt(int i){return children.get(i);}
}
class TextView extends View { String text="native";CharSequence getText(){return text;} }
class FeedHeaderTest {
 static int checks;static void check(boolean result,String message){checks++;if(!result)throw new AssertionError(message);}
 public static void main(String[] args){
  View root=new View();ViewGroup strip=new ViewGroup();TextView status=new TextView();strip.add(status);root.name("tvStatus",status);
  check(FeedHeaderViews.time(root)==status,"ordinary time remains native text");
  ViewGroup namesOld=new ViewGroup(),titleParent=new ViewGroup(),frame=new ViewGroup(),column=new ViewGroup();
  TextView title=new TextView();titleParent.add(title);namesOld.add(titleParent);frame.add(namesOld);column.add(frame);column.width=705;
  root.name("header_top",namesOld);root.name("tv_title",title);
  check(FeedHeaderViews.names(root)==namesOld,"legacy IP uses name row instead of status row");
  check(FeedHeaderViews.nameAnchor(root,namesOld)==title,"ordinary IP borrows native title style");
  check(FeedHeaderViews.layoutAnchor(namesOld,title)==titleParent,"IP params come from native horizontal row");
  ViewGroup medal=new ViewGroup();medal.add(new View());strip.add(medal);status.visibility=View.GONE;
  check(FeedHeaderViews.time(root)==strip,"hidden native time falls back to existing medal strip");
  check(FeedHeaderViews.firstText(strip)==null,"image medal does not invent time");
  TextView emptySchool=new TextView();emptySchool.text="";strip.add(emptySchool);
  check(FeedHeaderViews.firstText(strip)==null,"empty visible school label must not swallow medal registration days");
  check(FeedHeaderViews.nameAnchor(root,namesOld)==title,"medal does not move IP off the name row");
  check(FeedHeaderViews.availableWidth(namesOld)==705,"wrap-content name uses allocated column width");
  TextView days=new TextView(),ip=new TextView();days.tag=ip.tag=FeedHeaderViews.METADATA_TAG;strip.add(days);strip.add(ip);
  check(FeedHeaderViews.firstText(strip)==null,"plugin badges never become native time anchor");
  check(FeedHeaderViews.nameAnchor(root,namesOld)==title,"days badge cannot replace host style anchor");
  check(FeedHeaderViews.layoutAnchor(namesOld,title)==titleParent,"plugin badge cannot replace host name container");
  View alternate=new View();ViewGroup actualMedalStrip=new ViewGroup(),actualMedal=new ViewGroup();actualMedal.add(new View());actualMedalStrip.add(actualMedal);
  alternate.name("llWornMedal",actualMedal);
  check(FeedHeaderViews.time(alternate)==actualMedalStrip,"medal-only header without tvStatus uses visible native strip");
  TextView detachedStatus=new TextView();ViewGroup hiddenTimeStrip=new ViewGroup();hiddenTimeStrip.visibility=View.GONE;hiddenTimeStrip.add(detachedStatus);detachedStatus.visibility=View.GONE;alternate.name("tvStatus",detachedStatus);
  check(FeedHeaderViews.time(alternate)==actualMedalStrip,"visible medal strip wins over hidden separate time container");
  status.visibility=View.VISIBLE;
  check(FeedHeaderViews.time(root)==status,"recycled ordinary row restores native time path");
  check(FeedHeaderViews.firstText(strip)==status,"visible native text wins over metadata");
  root.name("tv_title",new View());status.visibility=View.GONE;
  check(FeedHeaderViews.nameAnchor(root,namesOld)==null,"missing native title fails safely");
  View modern=new ViewGroup();ViewGroup names=new ViewGroup();TextView nickname=new TextView();names.add(nickname);
  root.name("vHeaderCommonLabel",modern);root.name("vHeaderUserInfoLabel",names);
  check(FeedHeaderViews.time(root)==modern,"modern label strip unchanged");
  check(FeedHeaderViews.names(root)==names,"modern nickname strip unchanged");
  check(FeedHeaderViews.nameAnchor(root,names)==nickname,"modern text anchor unchanged");
  check(FeedHeaderViews.layoutAnchor(names,nickname)==nickname,"modern params unchanged");
  namesOld.left=10;frame.left=5;column.paddingRight=20;check(FeedHeaderViews.availableWidth(namesOld)==670,"name space respects column padding and offsets");
  names.width=300;check(FeedHeaderViews.availableWidth(names)==300,"modern available width unchanged");
  names.visibility=View.GONE;check(FeedHeaderViews.firstText(names)==null,"hidden groups do not lend visible descendants");
  check(FeedHeaderViews.firstText(null)==null,"missing strip is safe");
  System.out.println("Feed headers: "+checks+" production helper checks passed");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='newsoul-header-') as tmp:
    file = Path(tmp)/'FeedHeaderTest.java';file.write_text(harness+'\n'+source)
    subprocess.run(['javac','-encoding','UTF-8','-d',tmp,str(file)],check=True)
    subprocess.run(['java','-cp',tmp,'FeedHeaderTest'],check=True)
