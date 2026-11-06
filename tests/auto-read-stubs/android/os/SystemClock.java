package android.os; public class SystemClock { public static long elapsedRealtime(){return System.nanoTime()/1_000_000;} }
