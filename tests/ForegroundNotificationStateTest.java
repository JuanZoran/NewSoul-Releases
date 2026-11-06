package dev.local.newsoul;

public final class ForegroundNotificationStateTest {
    private static int checks;
    private static void check(boolean result, String message) {
        checks++; if (!result) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        ForegroundNotificationState state = new ForegroundNotificationState();
        Object first = new Object(), second = new Object(), rotated = new Object();
        check(state.start(first), "cold start enters foreground");
        check(!state.start(first), "duplicate start ignored");
        check(!state.start(second), "internal page transition does not clear");
        state.stop(first, false);
        check(!state.start(first), "return navigation while another activity visible does not clear");
        state.stop(second, false); state.stop(first, false);
        check(state.start(first), "return from background clears again");
        state.stop(first, true);
        check(!state.start(rotated), "configuration recreation does not clear");
        state.stop(rotated, false);
        check(state.start(second), "background return after rotation still clears");
        state.stop(new Object(), false);
        check(!state.start(first), "unknown stop does not reset foreground");
        state.stop(first, true);
        check(!state.start(rotated), "rotation with another visible activity is not a foreground transition");
        state.stop(second, false);state.stop(rotated, false);state.stop(rotated, true);
        check(state.start(first), "duplicate stop does not suppress next foreground");
        System.out.println("Foreground notifications: " + checks + " lifecycle checks passed");
    }
}
