package dev.local.newsoul;
import android.app.Activity;import java.util.function.IntConsumer;
final class GamePicker {static IntConsumer selection;static boolean preview;static int opens;static void show(Activity a,boolean dice,boolean dry,IntConsumer action,Runnable dismiss){selection=v->{dismiss.run();action.accept(v);};preview=dry;opens++;}}
