package dev.local.newsoul;

/** Pure direction/settling rules shared by the touch pager and its regression tests. */
final class MenuPagerPolicy {
    static final int UNDECIDED = 0, HORIZONTAL = 1, VERTICAL = 2;

    static int direction(float dx, float dy, float slop) {
        float x = Math.abs(dx), y = Math.abs(dy);
        if (y > slop && y >= x * .8f) return VERTICAL;
        if (x > slop && x > y * 1.5f) return HORIZONTAL;
        // Ambiguous diagonal motion belongs to the vertical settings list.
        if (Math.max(x, y) > slop * 2) return VERTICAL;
        return UNDECIDED;
    }

    static int neighbor(int selected, int count, float dx) {
        if (dx == 0) return selected;
        return Math.max(0, Math.min(count - 1, selected + (dx < 0 ? 1 : -1)));
    }

    static int destination(int selected, int count, float dx, float width,
            float velocityX, float minimumFling, boolean canceled) {
        if (canceled || width <= 0) return selected;
        int neighbor = neighbor(selected, count, dx);
        if (neighbor == selected) return selected;
        float fraction = Math.min(1, Math.abs(dx) / width);
        float toward = velocityX * (dx < 0 ? -1 : 1);
        return advances(fraction, toward, minimumFling, false) ? neighbor : selected;
    }

    static boolean advances(float fraction, float toward, float minimumFling, boolean canceled) {
        if (canceled || toward <= -minimumFling) return false;
        if (toward >= minimumFling && fraction >= .02f) return true;
        return fraction >= .28f;
    }
}
