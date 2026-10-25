package dev.android.dynamicisland.island.view;

import dev.android.dynamicisland.island.graphics.Easing;

/** Shared, reversible crossfade: compact and expanded content always have complementary weights. */
final class ContentHandoff {
    private static final long DURATION_MS = 220;
    final boolean expanding;
    final float fromExpanded;
    private final long start;
    private final long duration;

    ContentHandoff(ContentHandoff previous, boolean expanding, long now) {
        this.expanding = expanding;
        fromExpanded = previous == null ? (expanding ? 0 : 1) : previous.expanded(now);
        start = now;
        duration = Math.max(1, Math.round(DURATION_MS * Math.abs((expanding ? 1 : 0) - fromExpanded)));
    }

    float expanded(long now) {
        float progress = Easing.easeInOut(IslandView.clamp01((now - start) / (float)duration));
        return Easing.lerp(fromExpanded, expanding ? 1 : 0, progress);
    }

    float compact(long now) { return 1 - expanded(now); }
    boolean finished(long now) { return now - start >= duration; }
}
