#!/usr/bin/env python3
"""Run actual attachment/resume/pause methods against native-parent lifecycle fixtures."""
from pathlib import Path
import subprocess
import tempfile

source = Path('app/src/main/java/dev/local/newsoul/SquareLikeControls.java').read_text()


def method(signature):
    start = source.index(signature)
    body = source.index('{', start)
    depth = 1
    end = body + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


outer = '\n'.join(method(name) for name in (
    '    private static boolean supported(Activity activity)',
    '    private static String envelopeName(Activity activity)',
    '    private static Controller controllerFor(View envelope)',
    '    private static void attachSafely(View envelope)',
)).replace('android.content.ContextWrapper', 'ContextWrapper')
controller_methods = method('        void resume()') + '\n' + method('        void pause()')
attachment = source[source.index('            button.addOnAttachStateChangeListener('):source.index('            button.setOnClickListener(')]

harness = r'''
import java.util.*;
class SquareLikeAttachmentTest {
 static final String MAIN = Activity.class.getName(), CITY = CityActivity.class.getName(), TAG = "new.soul.square.like";
 static boolean legacy;
 static int checks;
 static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
 static class Resources {
  int getIdentifier(String name, String type, String pkg) { return name.equals("message_button") ? 11 : name.equals("messageButton") ? 12 : 0; }
 }
 static class Context {
  final Resources resources = new Resources();
  Resources getResources() { return resources; }
  String getPackageName() { return "cn.soulapp.android"; }
 }
 static class ContextWrapper extends Context {
  final Context base; ContextWrapper(Context base) { this.base = base; }
  Context getBaseContext() { return base; }
 }
 static class Activity extends Context {}
 static class CityActivity extends Activity {}
 static class OtherActivity extends Activity {}
 static class Log { static void i(String tag, String message) {} static void e(String tag, String message, Throwable error) {} }
 static class Gravity { static final int LEFT = 3; }
 static int dp(Context context, int value) { return value; }
 static class ViewTreeObserver {
  interface OnPreDrawListener { boolean onPreDraw(); }
  final List<OnPreDrawListener> listeners = new ArrayList<>();
  boolean alive = true;
  boolean isAlive() { return alive; }
  void addOnPreDrawListener(OnPreDrawListener listener) { listeners.add(listener); }
  void removeOnPreDrawListener(OnPreDrawListener listener) { listeners.remove(listener); }
 }
 static class View {
  static final int GONE = 8;
  interface OnAttachStateChangeListener { void onViewAttachedToWindow(View view); void onViewDetachedFromWindow(View view); }
  final Context context;
  final List<OnAttachStateChangeListener> attachments = new ArrayList<>();
  final List<Runnable> queue = new ArrayList<>();
  ViewTreeObserver observer = new ViewTreeObserver();
  ViewGroup parent;
  ViewGroup.LayoutParams params;
  Object tag, clickHandler = new Object();
  boolean attached, marked;
  int id, visibility;
  View(Context context) { this.context = context; }
  Context getContext() { return context; }
  Object getParent() { return parent; }
  int getId() { return id; }
  boolean isAttachedToWindow() { return attached; }
  ViewTreeObserver getViewTreeObserver() { return observer; }
  void setTag(Object tag) { this.tag = tag; }
  View findViewWithTag(Object tag) { return Objects.equals(this.tag, tag) ? this : null; }
  void addOnAttachStateChangeListener(OnAttachStateChangeListener listener) { attachments.add(listener); }
  void attach() { attached = true; for (var listener : new ArrayList<>(attachments)) listener.onViewAttachedToWindow(this); }
  void detach() { for (var listener : new ArrayList<>(attachments)) listener.onViewDetachedFromWindow(this); attached = false; }
  void setVisibility(int value) { visibility = value; }
  void removeCallbacks(Runnable callback) { queue.removeIf(value -> value == callback); }
  void post(Runnable callback) { queue.add(callback); }
 }
 static class ViewGroup extends View {
  static class LayoutParams { final int width, height; LayoutParams(int width, int height) { this.width = width; this.height = height; } }
  final List<View> children = new ArrayList<>();
  ViewGroup(Context context) { super(context); attached = true; }
  int indexOfChild(View view) { return children.indexOf(view); }
  void addView(View view, LayoutParams params) { addView(view, children.size(), params); }
  void addView(View view, int index, LayoutParams params) {
   children.add(index, view); view.parent = this; view.params = params;
   if (attached) view.attach();
  }
  void removeView(View view) { view.detach(); children.remove(view); view.parent = null; }
  @Override View findViewWithTag(Object tag) {
   View own = super.findViewWithTag(tag); if (own != null) return own;
   for (View child : children) { View found = child.findViewWithTag(tag); if (found != null) return found; }
   return null;
  }
 }
 static class RelativeLayout extends ViewGroup {
  static final int ABOVE = 2, ALIGN_RIGHT = 7;
  static class LayoutParams extends ViewGroup.LayoutParams {
   final Map<Integer, Integer> rules = new HashMap<>();
   LayoutParams(int width, int height) { super(width, height); }
   void addRule(int rule, int anchor) { rules.put(rule, anchor); }
  }
  RelativeLayout(Context context) { super(context); }
 }
 static class LinearLayout extends ViewGroup {
  static final int HORIZONTAL = 0, VERTICAL = 1;
  int orientation = VERTICAL;
  int getOrientation() { return orientation; }
  static class LayoutParams extends ViewGroup.LayoutParams {
   int gravity; LayoutParams(int width, int height) { super(width, height); }
  }
  LinearLayout(Context context) { super(context); }
 }
 static class FailingLinearLayout extends LinearLayout {
  FailingLinearLayout(Context context) { super(context); }
  @Override void addView(View view, int index, ViewGroup.LayoutParams params) {
   super.addView(view, index, params);
   if (view instanceof ActionView) throw new IllegalStateException("partial add failure");
  }
 }
 static class PluginActionMark { static void set(View view, boolean value) { view.marked = value; } }
 static class ActionView extends View {
  final Controller controller; ActionView(Activity activity, Controller controller) { super(activity); this.controller = controller; }
 }
 static class Controller {
  static int created;
  static boolean failResume;
  final Activity activity;
  final View envelope;
  final ActionView button;
  ViewTreeObserver observer;
  boolean active;
  final Runnable ticker = () -> {};
  final ViewTreeObserver.OnPreDrawListener beforeDraw = () -> { syncVisibility(); return true; };
  Controller(Activity activity, View envelope) {
   created++; this.activity = activity; this.envelope = envelope;
   button = new ActionView(activity, this);
   PluginActionMark.set(button, true);
   button.setVisibility(View.GONE);
''' + attachment + r'''
  }
  void syncVisibility() { if (failResume) throw new IllegalStateException("resume failure"); button.setVisibility(active && envelope.attached ? 0 : View.GONE); }
  void finish(boolean notify) {}
''' + controller_methods + r'''
 }
''' + outer + r'''
 static View envelope(ViewGroup parent, Context context) {
  View view = new View(context); view.id = context instanceof CityActivity ? 12 : 11;
  parent.addView(view, new ViewGroup.LayoutParams(72, 78)); return view;
 }
 static void fixture(boolean linear) {
  legacy = linear;
  Activity activity = new Activity();
  ViewGroup parent = linear ? new LinearLayout(activity) : new RelativeLayout(activity);
  View first = new View(activity); parent.addView(first, new ViewGroup.LayoutParams(1, 1));
  View envelope = envelope(parent, activity);
  View last = new View(activity); parent.addView(last, new ViewGroup.LayoutParams(1, 1));
  Object originalParams = envelope.params, originalHandler = envelope.clickHandler;
  attachSafely(envelope);
  Controller controller = controllerFor(envelope);
  check(controller != null && controller.active, "supported parent attaches and activates existing ActionView");
  check(controller.button.marked && controller.button.params.width == 68 && controller.button.params.height == 68, "existing 68dp action and shared plugin mark are retained");
  check(envelope.parent == parent && envelope.params == originalParams && envelope.clickHandler == originalHandler, "native envelope params handler and parent are unchanged");
  check(parent.children.get(0) == first && parent.children.get(parent.children.size()-1) == (linear ? last : controller.button), "existing sibling order remains stable");
  if (linear) {
   check(parent.indexOfChild(controller.button) + 1 == parent.indexOfChild(envelope), "linear action is inserted immediately before envelope");
   check(((LinearLayout.LayoutParams) controller.button.params).gravity == Gravity.LEFT, "legacy linear action aligns with the native left-aligned envelope content");
  } else {
   var params = (RelativeLayout.LayoutParams) controller.button.params;
   check(params.rules.get(RelativeLayout.ABOVE) == envelope.id && params.rules.get(RelativeLayout.ALIGN_RIGHT) == envelope.id, "modern relative rules preserve envelope anchor");
  }
  int created = Controller.created;
  attachSafely(envelope); attachSafely(envelope);
  check(Controller.created == created && parent.children.size() == 4, "repeated attachment reuses the controller without duplicate buttons");
  check(envelope.observer.listeners.size() == 1 && controller.button.queue.size() == 1, "repeated resume keeps one pre-draw listener and ticker");
  controller.button.detach();
  check(!controller.active && envelope.observer.listeners.isEmpty() && controller.button.queue.isEmpty(), "detaching action pauses observers and queued work");
  controller.button.attach(); attachSafely(envelope);
  check(controller.active && controller.button.visibility == 0 && envelope.observer.listeners.size() == 1, "reattach restores the same action and observer");
  check(Controller.created == created && envelope.params == originalParams, "reattach does not replace native envelope or controller");
 }
 public static void main(String[] args) {
  fixture(true); fixture(false);
  Activity activity = new Activity();
  LinearLayout modernLinear = new LinearLayout(activity); View modernEnvelope = envelope(modernLinear, activity);
  int created = Controller.created; attachSafely(modernEnvelope);
  check(Controller.created == created && modernLinear.children.size() == 1 && modernLinear.findViewWithTag(TAG) == null, "modern linear parent remains untouched");
  legacy = true;
  LinearLayout horizontal = new LinearLayout(activity); horizontal.orientation = LinearLayout.HORIZONTAL;
  View horizontalEnvelope = envelope(horizontal, activity); attachSafely(horizontalEnvelope);
  check(Controller.created == created && horizontal.children.size() == 1 && horizontal.findViewWithTag(TAG) == null, "legacy horizontal parent remains untouched");
  ViewGroup unsupported = new ViewGroup(activity); View unsupportedEnvelope = envelope(unsupported, activity);
  attachSafely(unsupportedEnvelope);
  check(Controller.created == created && unsupported.children.size() == 1 && unsupported.findViewWithTag(TAG) == null, "unsupported parent is untouched");
  LinearLayout detached = new LinearLayout(activity); View detachedEnvelope = envelope(detached, activity); detachedEnvelope.detach();
  attachSafely(detachedEnvelope); attachSafely(null);
  check(Controller.created == created && detached.children.size() == 1, "detached and missing envelope do not create UI");
  detachedEnvelope.attach(); detachedEnvelope.id = 99; attachSafely(detachedEnvelope);
  check(Controller.created == created && detached.children.size() == 1, "unrelated envelope ID is untouched");
  OtherActivity other = new OtherActivity(); LinearLayout otherParent = new LinearLayout(other); attachSafely(envelope(otherParent, other));
  check(Controller.created == created && otherParent.children.size() == 1, "unsupported activity is untouched");
  LinearLayout wrapped = new LinearLayout(activity); View wrappedEnvelope = envelope(wrapped, new ContextWrapper(activity)); attachSafely(wrappedEnvelope);
  check(controllerFor(wrappedEnvelope) != null, "wrapped activity context still attaches");
  CityActivity city = new CityActivity(); LinearLayout cityParent = new LinearLayout(city); View cityEnvelope = envelope(cityParent, city); attachSafely(cityEnvelope);
  check(controllerFor(cityEnvelope) != null, "city native envelope name remains supported");
  FailingLinearLayout failed = new FailingLinearLayout(activity); View failedEnvelope = envelope(failed, activity); Object params = failedEnvelope.params;
  attachSafely(failedEnvelope);
  check(failed.children.size() == 1 && failed.findViewWithTag(TAG) == null && failedEnvelope.params == params, "partial add failure rolls back only the plugin button");
  check(failedEnvelope.observer.listeners.isEmpty(), "partial add rollback pauses the created controller");
  LinearLayout resumeFailed = new LinearLayout(activity); View resumeEnvelope = envelope(resumeFailed, activity);
  Controller.failResume = true; attachSafely(resumeEnvelope); Controller.failResume = false;
  check(resumeFailed.children.size() == 1 && resumeFailed.findViewWithTag(TAG) == null && resumeEnvelope.observer.listeners.isEmpty(), "resume failure rolls back UI and observer");
  attachSafely(resumeEnvelope);
  check(controllerFor(resumeEnvelope) != null && resumeFailed.children.size() == 2, "attachment can retry after a clean failure");
  System.out.println("Square like native-parent attachment: " + checks + " checks passed; no real clicks or requests");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='newsoul-like-attachment-') as directory:
    file = Path(directory) / 'SquareLikeAttachmentTest.java'
    file.write_text(harness)
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', directory, str(file)], check=True)
    subprocess.run(['java', '-cp', directory, 'SquareLikeAttachmentTest'], check=True)
