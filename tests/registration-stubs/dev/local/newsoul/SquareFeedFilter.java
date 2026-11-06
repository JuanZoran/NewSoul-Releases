package dev.local.newsoul;
final class SquareFeedFilter {
 static final String ENABLED="feed_filter_enabled";
 static final java.util.List<String> updates=new java.util.ArrayList<>();
 static void refresh(){throw new AssertionError("lookup must not globally refresh feed");}
 static void metadataResolved(String key,boolean registration){updates.add(key+":"+registration);}
}
