package dev.local.newsoul;
final class HostBindings {
    static boolean uses643Bindings(ClassLoader loader) { return false; }
    static boolean legacyChat(ClassLoader loader) {
        return ((HostAdBindingsTest.FakeLoader) loader).legacy;
    }
}
