package dev.local.newsoul;
final class HostBindings {
    static boolean legacyChat(ClassLoader loader) {
        return ((HostAdBindingsTest.FakeLoader) loader).legacy;
    }
}
