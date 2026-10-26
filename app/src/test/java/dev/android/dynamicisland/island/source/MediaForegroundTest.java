package dev.android.dynamicisland.island.source;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.media.session.MediaController;
import android.os.Handler;
import android.os.Looper;
import dev.android.dynamicisland.island.model.IslandItem;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

/** Collection/Handler integration; all sessions stay playing, so no paused-player ticks are counted. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class MediaForegroundTest {
    private static final String PLAYER = "com.music";
    private static final String OTHER = "com.android.launcher";
    private static final long RETRY_MS = MediaForegroundGate.RECHECK_MS;
    private final Application app = RuntimeEnvironment.getApplication();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicInteger changes = new AtomicInteger();
    private final MediaSource source = new MediaSource(app, main, changes::incrementAndGet);

    private static final class Snapshot {
        final List<IslandItem> cards = new ArrayList<>();
        final Set<String> hidden = new HashSet<>();
        final Set<String> packages = new HashSet<>();
    }

    private MediaController controller(String title) throws Exception {
        return MediaRecordingTest.controller(app, PLAYER, 0, title);
    }

    private void sync(MediaController controller) {
        source.sync(Collections.singletonList(controller));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private Snapshot collect(String foreground, boolean confirmed) {
        Snapshot result = new Snapshot();
        source.collect(result.cards, foreground, confirmed, result.hidden, result.packages);
        return result;
    }

    private void advance(long millis) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
    }

    private void assertRetryScheduled(boolean expected) {
        Runnable retry = ReflectionHelpers.getField(source, "foregroundRetry");
        assertEquals(expected, main.hasCallbacks(retry));
    }

    private void assertHidden(Snapshot result) {
        assertTrue(result.cards.isEmpty());
        assertEquals(Collections.singleton("media:" + PLAYER), result.hidden);
        assertEquals(Collections.singleton(PLAYER), result.packages);
    }

    private void assertShown(Snapshot result, String title) {
        assertEquals(1, result.cards.size());
        IslandItem card = result.cards.get(0);
        assertEquals(PLAYER, card.packageName);
        assertEquals("media:" + PLAYER, card.key);
        assertEquals(title, card.title.toString());
        assertTrue(card.playing);
        assertTrue(result.hidden.isEmpty());
        assertEquals(Collections.singleton(PLAYER), result.packages);
    }

    private void assertEmpty(Snapshot result) {
        assertTrue(result.cards.isEmpty());
        assertTrue(result.hidden.isEmpty());
        assertTrue(result.packages.isEmpty());
    }

    @Test
    public void confirmedMatchingAppIsHiddenOnTheFirstCollectedFrame() throws Exception {
        sync(controller("Song"));
        int baseline = changes.get(); // sync itself announces a change
        assertHidden(collect(PLAYER, true));
        advance(RETRY_MS * 2);
        assertEquals(baseline, changes.get());
        assertHidden(collect(PLAYER, true));
    }

    @Test
    public void confirmedOtherAppAppearsImmediatelyWithoutGenericDelay() throws Exception {
        sync(controller("Song"));
        int baseline = changes.get();
        assertShown(collect(OTHER, true), "Song");
        advance(RETRY_MS * 2);
        assertEquals(baseline, changes.get());
    }

    @Test
    public void cachedMatchingAppStaysHiddenWithoutTimeoutAdmission() throws Exception {
        sync(controller("Song"));
        int baseline = changes.get();
        assertHidden(collect(PLAYER, false));
        advance(RETRY_MS * 3);
        assertHidden(collect(PLAYER, false));
        assertEquals(baseline, changes.get());
    }

    @Test
    public void uncertainNewSessionWaitsUntilDeadlineThenHasOnlyOneRetry() throws Exception {
        sync(controller("Song"));
        int baseline = changes.get();
        assertHidden(collect(null, false));
        advance(RETRY_MS - 1);
        assertEquals(baseline, changes.get());
        assertHidden(collect(null, false)); // must not move the original deadline
        advance(1);
        assertEquals(1, changes.get() - baseline);
        // onChange requests a fresh gather; it must never replay the previously hidden card.
        assertShown(collect(null, false), "Song");
        advance(RETRY_MS * 4);
        assertEquals(1, changes.get() - baseline);
        assertShown(collect(null, false), "Song");
    }

    @Test
    public void cachedOtherAppAlsoDefersANewSessionUntilTheBoundedRetry() throws Exception {
        sync(controller("Song"));
        int baseline = changes.get();
        assertHidden(collect(OTHER, false));
        advance(RETRY_MS);
        assertEquals(1, changes.get() - baseline);
        assertShown(collect(OTHER, false), "Song");
        advance(RETRY_MS * 2);
        assertEquals(1, changes.get() - baseline);
    }

    @Test
    public void matchingForegroundUpdateBeforeDeadlineCancelsPendingRetry() throws Exception {
        sync(controller("Song"));
        int baseline = changes.get();
        assertHidden(collect(null, false));
        advance(60);
        assertHidden(collect(PLAYER, true));
        advance(RETRY_MS * 2);
        assertEquals(baseline, changes.get());
        assertHidden(collect(PLAYER, true));
    }

    @Test
    public void cachedMatchingUpdateAlsoCancelsPendingRetry() throws Exception {
        sync(controller("Song"));
        int baseline = changes.get();
        assertHidden(collect(null, false));
        advance(60);
        assertHidden(collect(PLAYER, false));
        advance(RETRY_MS * 2);
        assertEquals(baseline, changes.get());
        assertHidden(collect(PLAYER, false));
    }

    @Test
    public void alreadyAdmittedBackgroundSessionDoesNotFlickerOnUnknownSnapshot() throws Exception {
        sync(controller("Song"));
        int baseline = changes.get();
        Snapshot admitted = collect(OTHER, true);
        assertShown(admitted, "Song");
        Snapshot unknown = collect(null, false);
        assertShown(unknown, "Song");
        assertSame(admitted.cards.get(0), unknown.cards.get(0));
        advance(RETRY_MS * 2);
        assertShown(collect(null, false), "Song");
        assertEquals(baseline, changes.get());
    }

    @Test
    public void confirmedOtherUpdateCancelsDeferralAndShowsTheCardImmediately() throws Exception {
        sync(controller("Song"));
        int baseline = changes.get();
        assertHidden(collect(null, false));
        advance(60);
        assertShown(collect(OTHER, true), "Song");
        advance(RETRY_MS * 2);
        assertEquals(baseline, changes.get());
    }

    @Test
    public void switchingTokensWithinTheSamePackageRestartsTheDeadlineAndCancelsOldRetry() throws Exception {
        MediaController first = controller("First");
        MediaController second = controller("Second");
        assertNotEquals(first.getSessionToken(), second.getSessionToken());
        sync(first);
        assertHidden(collect(null, false));
        advance(60);
        sync(second);
        int baseline = changes.get(); // excludes both sync notifications
        assertHidden(collect(null, false));
        advance(RETRY_MS - 60); // the old session's deadline
        assertEquals(baseline, changes.get());
        assertHidden(collect(null, false));
        advance(59);
        assertEquals(baseline, changes.get());
        advance(1); // the new session's own deadline
        assertEquals(1, changes.get() - baseline);
        assertShown(collect(null, false), "Second");
        advance(RETRY_MS * 2);
        assertEquals(1, changes.get() - baseline);
    }

    @Test
    public void syncEmptyCancelsStaleRetryAndCannotCollectAGhostCard() throws Exception {
        sync(controller("Song"));
        assertHidden(collect(null, false));
        assertRetryScheduled(true);
        advance(60);
        source.sync(Collections.emptyList());
        shadowOf(Looper.getMainLooper()).idle();
        assertRetryScheduled(false);
        int baseline = changes.get(); // removal is allowed to announce a change
        advance(RETRY_MS * 2); // do not collect first: cancellation must happen in sync itself
        assertEquals(baseline, changes.get());
        assertEmpty(collect(null, false));
        assertEmpty(collect(OTHER, true));
        // itemForSession may retain a retired shade tile; collect must not resurrect it.
    }

    @Test
    public void sessionDestructionCancelsStaleRetryAndCannotCollectAGhostCard() throws Exception {
        MediaController controller = controller("Song");
        sync(controller);
        assertHidden(collect(null, false));
        assertRetryScheduled(true);
        advance(60);
        // Deliver the real registered Session callback on its Handler. The shared fixture stubs
        // Binder transport, so destruction is injected at the callback boundary instead of IPC.
        Map<?, ?> sessions = ReflectionHelpers.getField(source, "sessions");
        MediaController.Callback callback = (MediaController.Callback) sessions.get(controller.getSessionToken());
        assertNotNull(callback);
        int beforeDestruction = changes.get();
        main.post(callback::onSessionDestroyed);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, changes.get() - beforeDestruction);
        assertRetryScheduled(false);
        int baseline = changes.get(); // excludes the legitimate destruction notification
        advance(RETRY_MS * 2); // no collect call may mask a missing destruction cancellation
        assertEquals(baseline, changes.get());
        assertEmpty(collect(null, false));
        assertEmpty(collect(OTHER, true));
    }

    @Test
    public void backgroundAndPipPlaybackRemainAllowedWhenAnotherAppIsActuallyForeground() throws Exception {
        sync(controller("Video"));
        int baseline = changes.get();
        // Background playback on the launcher, then PiP while a different activity owns focus.
        assertShown(collect(OTHER, true), "Video");
        assertShown(collect("com.browser", true), "Video");
        advance(RETRY_MS * 2);
        assertShown(collect("com.browser", true), "Video");
        assertEquals(baseline, changes.get());
    }

    @Test
    public void legacyFourArgumentCollectBypassesUnknownGatingButStillHidesMatchingApp() throws Exception {
        sync(controller("Song"));
        int baseline = changes.get();
        Snapshot unfiltered = new Snapshot();
        source.collect(unfiltered.cards, null, unfiltered.hidden, unfiltered.packages);
        assertShown(unfiltered, "Song");
        Snapshot matching = new Snapshot();
        source.collect(matching.cards, PLAYER, matching.hidden, matching.packages);
        assertHidden(matching);
        advance(RETRY_MS * 2);
        assertEquals(baseline, changes.get());
    }
}
