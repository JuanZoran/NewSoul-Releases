package dev.local.newsoul;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AiSendRules {
    // Protect punctuation belonging to addresses, numbers, paths and quoted text.
    private static final Pattern TOKEN = Pattern.compile("(?i)(?:https?://|www\\.)[^\\s，。！？；<>]+|[^\\s，。！？；<>]*[@/\\\\][^\\s，。！？；<>]*|[0-9]+(?:[.:][0-9]+)+");
    static List<String> parts(String text) {
        List<String> result=new ArrayList<>();
        for(String line:explicitParts(text))sentences(line,result);
        return result;
    }
    /** An edited card is one message: never re-split the user's explicit grouping. */
    static List<String> explicitParts(String text) {
        if (text == null || text.length() > 500) throw new IllegalArgumentException("Invalid reply");
        List<String> result = new ArrayList<>();
        for (String part : text.split("\\r\\n|[\\r\\n]", -1)) {
            if (!part.trim().isEmpty()) result.add(part.trim());
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Empty reply");
        return result;
    }

    private static void sentences(String line, List<String> out) {
        // The model supplies semantic newlines. Fallback only at complete sentence ends:
        // never guess a clause boundary from commas, spaces or a character count.
        boolean[] protectedAt = new boolean[line.length()];
        Matcher matcher=TOKEN.matcher(line);
        while(matcher.find())for(int i=matcher.start();i<matcher.end();i++)protectedAt[i]=true;
        String closers=""; int start=0, splitsForLine=0;
        for(int i=0;i<line.length();i++) {
            char c=line.charAt(i);
            if(protectedAt[i])continue;
            if(!closers.isEmpty()&&c==closers.charAt(closers.length()-1)) {closers=closers.substring(0,closers.length()-1);continue;}
            int opening="（([【《「『“‘\"`".indexOf(c);
            if(opening>=0){closers+="）)]】》」』”’\"`".charAt(opening);continue;}
            if(c=='\''&&(i==0||!Character.isLetterOrDigit(line.charAt(i-1)))){closers+='\'';continue;}
            if(!closers.isEmpty())continue;
            if("。！？!?".indexOf(c)<0)continue;
            int end=i+1;
            while(end<line.length()&&"。！？!?".indexOf(line.charAt(end))>=0)end++;
            // Keep trailing emoji/symbols with the sentence they decorate. If ambiguous,
            // retain the whole line rather than orphaning an emoji in another bubble.
            int next=end;while(next<line.length()&&Character.isWhitespace(line.charAt(next)))next++;
            if(next>=line.length()||!Character.isLetterOrDigit(line.codePointAt(next))) {i=end-1;continue;}
            if(end-start<6||line.length()-next<4){i=end-1;continue;}
            // At most three automatically inferred bubbles per explicit paragraph.
            // Explicit model/user newlines are always preserved.
            out.add(line.substring(start,end).trim());start=next;i=next-1;
            if(++splitsForLine==2)break;
        }
        out.add(line.substring(start).trim());
    }
}
