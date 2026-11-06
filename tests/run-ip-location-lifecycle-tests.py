#!/usr/bin/env python3
"""Exercise the production observer lifecycle with Android's window/floating split."""
from pathlib import Path
import subprocess
import tempfile

source = Path('app/src/main/java/dev/local/newsoul/SquareIpLocation.java').read_text()
fields = source[source.index('        ViewTreeObserver observer;'):source.index('        Binding(ViewGroup names')]
lifecycle = source[source.index('        void watch()'):source.index('        void layout()')]
remove_start = source.index('        void remove()')
remove = source[remove_start:source.rindex('    }\n}')]
layout_guard = source[source.index('            if (removed || label.getParent() != names)'):].splitlines()[0]

harness = r'''
import java.util.*;
class IpLocationLifecycleTest {
 static int checks;
 static void check(boolean value, String message) {
  checks++;
  if (!value) throw new AssertionError(message);
 }
 static class View {
  interface OnAttachStateChangeListener {
   void onViewAttachedToWindow(View view);
   void onViewDetachedFromWindow(View view);
  }
  ViewGroup parent;
  Object getParent() { return parent; }
 }
 static class TextView extends View {}
 static class ViewTreeObserver {
  interface OnGlobalLayoutListener { void onGlobalLayout(); }
  boolean alive = true;
  final List<OnGlobalLayoutListener> listeners = new ArrayList<>();
  boolean isAlive() { return alive; }
  void addOnGlobalLayoutListener(OnGlobalLayoutListener listener) {
   if (!alive) throw new IllegalStateException("dead observer");
   listeners.add(listener);
  }
  void removeOnGlobalLayoutListener(OnGlobalLayoutListener listener) {
   if (!alive) throw new IllegalStateException("dead observer");
   listeners.remove(listener);
  }
  void fire() {
   for (OnGlobalLayoutListener listener : new ArrayList<>(listeners)) listener.onGlobalLayout();
  }
 }
 static class ViewGroup extends View {
  boolean attached;
  ViewTreeObserver window, floating;
  final List<OnAttachStateChangeListener> attachments = new ArrayList<>();
  final List<View> children = new ArrayList<>();
  boolean isAttachedToWindow() { return attached; }
  ViewTreeObserver getViewTreeObserver() {
   if (attached) return window;
   if (floating == null) floating = new ViewTreeObserver();
   return floating;
  }
  void addOnAttachStateChangeListener(OnAttachStateChangeListener listener) { attachments.add(listener); }
  void removeOnAttachStateChangeListener(OnAttachStateChangeListener listener) { attachments.remove(listener); }
  void addView(View view) { children.add(view); view.parent = this; }
  void removeView(View view) { children.remove(view); view.parent = null; }
  void attach(ViewTreeObserver observer) {
   window = observer;
   attached = true;
   // Android merges the floating observer before delivering the attach callback.
   if (floating != null) {
    window.listeners.addAll(floating.listeners);
    floating.alive = false;
    floating = null;
   }
   for (OnAttachStateChangeListener listener : new ArrayList<>(attachments)) listener.onViewAttachedToWindow(this);
  }
  void detach() {
   // Android clears mAttachInfo only after delivering the detach callback.
   for (OnAttachStateChangeListener listener : new ArrayList<>(attachments)) listener.onViewDetachedFromWindow(this);
   attached = false;
  }
 }
 static class Binding {
  final ViewGroup names;
  final TextView label = new TextView();
  int layouts;
''' + fields + r'''
  Binding(ViewGroup names) { this.names = names; names.addView(label); watch(); layout(); }
''' + lifecycle + '        void layout() {\n' + layout_guard + r'''
            layouts++;
        }
''' + remove + r'''
 }
 public static void main(String[] args) {
  ViewGroup row = new ViewGroup();
  ViewTreeObserver firstWindow = new ViewTreeObserver();
  row.attach(firstWindow);
  Binding binding = new Binding(row);
  check(firstWindow.listeners.size() == 1, "attached row observes the window once");
  firstWindow.fire();
  check(binding.layouts == 2, "layout listener refreshes the binding");
  binding.observe();
  check(firstWindow.listeners.size() == 1, "repeated registration does not duplicate the listener");
  row.detach();
  check(firstWindow.listeners.isEmpty(), "detach releases the still-live window observer");
  check(binding.observer == null, "detach drops the saved observer");
  check(row.getViewTreeObserver().listeners.isEmpty(), "detached row has no floating layout listener");
  check(row.attachments.size() == 1 && binding.label.getParent() == row, "detached reusable row retains its binding locally");
  int before = binding.layouts;
  firstWindow.fire();
  check(binding.layouts == before, "old window cannot refresh a detached row");
  ViewTreeObserver nextWindow = new ViewTreeObserver();
  row.attach(nextWindow);
  check(binding.layouts == before + 1, "reattach refreshes without waiting for a new bind");
  check(nextWindow.listeners.size() == 1, "reattach observes the new window once");
  nextWindow.fire();
  check(binding.layouts == before + 2, "reattached row keeps reacting to width changes");
  row.detach();
  binding.remove();
  binding.remove();
  check(row.attachments.isEmpty() && binding.label.getParent() == null, "recycle while detached removes local retention and label");
  check(nextWindow.listeners.isEmpty(), "detached recycle does not leak the previous window");
  row.attach(nextWindow);
  check(nextWindow.listeners.isEmpty() && binding.layouts == before + 2, "removed binding cannot revive on attach");
  binding.listener.onGlobalLayout();
  check(binding.layouts == before + 2, "a stale layout callback is inert after removal");

  ViewGroup detached = new ViewGroup();
  Binding waiting = new Binding(detached);
  check(detached.getViewTreeObserver().listeners.isEmpty(), "binding before attach does not register a floating listener");
  ViewTreeObserver laterWindow = new ViewTreeObserver();
  detached.attach(laterWindow);
  check(laterWindow.listeners.size() == 1 && waiting.layouts == 2, "initial attach registers and refreshes a waiting binding");
  waiting.remove();
  check(laterWindow.listeners.isEmpty() && detached.attachments.isEmpty(), "recycle while attached releases both listeners");

  ViewGroup missedDetach = new ViewGroup();
  ViewTreeObserver saved = new ViewTreeObserver();
  missedDetach.attach(saved);
  Binding savedBinding = new Binding(missedDetach);
  missedDetach.attached = false;
  savedBinding.remove();
  check(saved.listeners.isEmpty(), "saved observer is removed even after getViewTreeObserver has changed");

  ViewGroup transferred = new ViewGroup();
  ViewTreeObserver dead = new ViewTreeObserver();
  transferred.attach(dead);
  Binding transferredBinding = new Binding(transferred);
  ViewTreeObserver merged = new ViewTreeObserver();
  merged.listeners.addAll(dead.listeners);
  dead.alive = false;
  transferred.window = merged;
  transferredBinding.remove();
  check(merged.listeners.isEmpty(), "observer transfer removes the listener from the live current observer");
  System.out.println("IP location observer lifecycle: " + checks + " checks passed");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='newsoul-ip-lifecycle-') as directory:
    file = Path(directory) / 'IpLocationLifecycleTest.java'
    file.write_text(harness)
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', directory, str(file)], check=True)
    subprocess.run(['java', '-cp', directory, 'IpLocationLifecycleTest'], check=True)
