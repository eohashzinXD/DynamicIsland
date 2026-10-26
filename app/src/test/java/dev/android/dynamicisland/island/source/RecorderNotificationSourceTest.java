package dev.android.dynamicisland.island.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.service.notification.StatusBarNotification;
import dev.android.dynamicisland.island.model.IslandItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The screen recorder's notification becomes a recording card unless the chip model already gave one. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RecorderNotificationSourceTest {
    private final Application app = RuntimeEnvironment.getApplication();

    private RecorderNotificationSource source() {
        return source(new long[]{10000L, 20000L});
    }

    private RecorderNotificationSource source(long[] time) {
        return new RecorderNotificationSource(app, () -> { }, () -> time[0], () -> time[1]);
    }

    private IslandItem card(RecorderNotificationSource source) {
        List<IslandItem> out = new ArrayList<>();
        source.collect(out);
        assertEquals(1, out.size());
        return out.get(0);
    }

    private StatusBarNotification note(String pkg, String channel, boolean ongoing) {
        return note(pkg, channel, ongoing, false);
    }

    private StatusBarNotification note(String pkg, String channel, boolean ongoing, boolean withIntent) {
        return note(pkg, channel, ongoing, withIntent, true, 1, null, 5000L);
    }

    private StatusBarNotification note(String pkg, String channel, boolean ongoing, boolean withIntent,
            boolean usesChronometer, int id, String tag, long postTime) {
        return note(pkg, channel, ongoing, withIntent, usesChronometer, id, tag, postTime, 1000L);
    }

    private StatusBarNotification note(String pkg, String channel, boolean ongoing, boolean withIntent,
            boolean usesChronometer, int id, String tag, long postTime, long when) {
        NotificationManager nm = app.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(channel, channel, NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(app, channel).setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Screen recording").setContentText("Tap to stop recording").setOngoing(ongoing)
                .setUsesChronometer(usesChronometer).setWhen(when)
                .setContentIntent(withIntent ? android.app.PendingIntent.getBroadcast(app, 1, new android.content.Intent("stop"), android.app.PendingIntent.FLAG_IMMUTABLE) : null).build();
        return new StatusBarNotification(pkg, pkg, id, tag, 1, 1, 0, n, android.os.Process.myUserHandle(), postTime);
    }

    @Test
    public void recorderChannelIsRecognised() {
        assertTrue(RecorderNotificationSource.isRecorder(note("com.android.systemui", "screen_record", true)));
        assertTrue(RecorderNotificationSource.isRecorder(note("com.samsung.android.app.smartcapture", "capture", true)));
        assertFalse(RecorderNotificationSource.isRecorder(note("com.example", "downloads", true)));
        assertFalse(RecorderNotificationSource.isRecorder(note("com.android.systemui", "screen_record", false)));
    }

    @Test
    public void cardCarriesTheChronometer() {
        RecorderNotificationSource source = source();
        source.update(Collections.singletonList(note("com.android.systemui", "screen_record", true)));
        List<IslandItem> out = new ArrayList<>();
        source.collect(out);
        assertEquals(1, out.size());
        assertEquals(IslandItem.Kind.RECORDING, out.get(0).kind);
        assertEquals(1000L, out.get(0).chronoBase);
        assertTrue(source.isShownOnIsland(out.get(0).sourceKey));
        assertEquals(0, out.get(0).actions.size()); // the test notification has no intent to stop with
    }

    @Test
    public void chipBasedRecordingCardWins() {
        RecorderNotificationSource source = source();
        source.update(Collections.singletonList(note("com.android.systemui", "screen_record", true)));
        List<IslandItem> out = new ArrayList<>();
        IslandItem chip = new IslandItem("cast:record");
        chip.kind = IslandItem.Kind.RECORDING;
        out.add(chip);
        source.collect(out);
        assertEquals(1, out.size());
    }

    @Test
    public void fallbackTimerSurvivesNotificationKeyAndPostTimeChanges() {
        RecorderNotificationSource source = source();
        StatusBarNotification first = note("com.samsung.android.app.smartcapture", "capture", true,
                false, false, 1, null, 5000L);
        source.update(Collections.singletonList(first));
        List<IslandItem> initial = new ArrayList<>();
        source.collect(initial);

        StatusBarNotification repost = note("com.samsung.android.app.smartcapture", "capture", true,
                false, false, 2, "replacement", 6000L);
        source.update(Collections.singletonList(repost));
        List<IslandItem> updated = new ArrayList<>();
        source.collect(updated);

        assertEquals(5000L, updated.get(0).chronoBase);
        assertEquals(initial.get(0).key, updated.get(0).key);
        assertEquals(repost.getKey(), updated.get(0).sourceKey);
    }

    @Test
    public void fallbackTimerResetsAfterRecorderNotificationDisappears() {
        RecorderNotificationSource source = source();
        source.update(Collections.singletonList(note("com.samsung.android.app.smartcapture", "capture", true,
                false, false, 1, null, 5000L)));
        source.update(Collections.emptyList());
        source.update(Collections.singletonList(note("com.samsung.android.app.smartcapture", "capture", true,
                false, false, 1, null, 9000L)));

        List<IslandItem> out = new ArrayList<>();
        source.collect(out);
        assertEquals(9000L, out.get(0).chronoBase);
    }

    @Test
    public void systemChronometerStillUsesItsProvidedBase() {
        RecorderNotificationSource source = source();
        source.update(Collections.singletonList(note("com.android.systemui", "screen_record", true)));
        List<IslandItem> out = new ArrayList<>();
        source.collect(out);
        assertEquals(1000L, out.get(0).chronoBase);
    }

    private StatusBarNotification recording(int id, long post, long when, boolean systemClock) {
        return note("com.samsung.android.app.smartcapture", "CHANNEL_ID_RECORDING_SCREEN", true,
                false, systemClock, id, null, post, when);
    }

    @Test
    public void rollingSystemTimestampDoesNotResetTheTimer() {
        long[] time = {10000L, 20000L};
        RecorderNotificationSource source = source(time);
        source.update(Collections.singletonList(recording(1, time[0], 1000L, true)));
        String key = card(source).key;
        for (int i = 0; i < 5; i++) {
            time[0] += 1000L;
            time[1] += 1000L;
            source.update(Collections.singletonList(recording(i + 2, time[0], time[0], true)));
            assertEquals(1000L, card(source).chronoBase);
            assertEquals(key, card(source).key);
        }
    }

    @Test
    public void summaryCannotWinOverRecordingRegardlessOfListOrder() {
        RecorderNotificationSource source = source();
        StatusBarNotification summary = recording(1, 10000L, 10000L, true);
        summary.getNotification().flags |= Notification.FLAG_GROUP_SUMMARY;
        StatusBarNotification child = recording(2, 5000L, 1000L, true);
        assertFalse(RecorderNotificationSource.isRecorder(summary));
        source.update(java.util.Arrays.asList(summary, child));
        assertEquals(child.getKey(), card(source).sourceKey);
        source.update(java.util.Arrays.asList(child, summary));
        assertEquals(child.getKey(), card(source).sourceKey);
        assertEquals(1000L, card(source).chronoBase);
    }

    @Test
    public void stableSystemStartBridgesShortNotificationGapWithoutKeepingStaleCard() {
        long[] time = {10000L, 20000L};
        RecorderNotificationSource source = source(time);
        source.update(Collections.singletonList(recording(1, 5000L, 1000L, true)));
        String key = card(source).key;
        source.update(Collections.emptyList());
        List<IslandItem> absent = new ArrayList<>();
        source.collect(absent);
        assertTrue(absent.isEmpty());
        time[0] += 500L;
        time[1] += 500L;
        source.update(Collections.singletonList(recording(2, time[0], 1000L, true)));
        assertEquals(key, card(source).key);
        assertEquals(1000L, card(source).chronoBase);
    }

    @Test
    public void unknownFallbackGapStartsANewSessionRatherThanGuessingContinuity() {
        long[] time = {10000L, 20000L};
        RecorderNotificationSource source = source(time);
        source.update(Collections.singletonList(recording(1, 5000L, 0L, false)));
        String key = card(source).key;
        source.update(Collections.emptyList());
        time[0] += 500L;
        time[1] += 500L;
        source.update(Collections.singletonList(recording(1, time[0], 0L, false)));
        assertFalse(key.equals(card(source).key));
        assertEquals(time[0], card(source).chronoBase);
    }

    @Test
    public void confirmedLifecycleBridgesFallbackGapButNewStartIsNotMerged() {
        long[] time = {10000L, 20000L};
        long[] signal = {1L, 1L};
        RecorderNotificationSource source = source(time);
        source.setLifecycleSession(pkg -> signal[0] == 0L ? null : new CastSource.RecorderLifecycle(pkg, signal[0], signal[1]));
        source.update(Collections.singletonList(recording(1, 5000L, 0L, false)));
        String key = card(source).key;
        source.update(Collections.emptyList());
        time[0] += 5000L;
        time[1] += 5000L;
        source.update(Collections.singletonList(recording(2, time[0], 0L, false)));
        assertEquals(key, card(source).key);
        assertEquals(5000L, card(source).chronoBase);
        signal[0]++;
        signal[1]++;
        time[0] += 1000L;
        time[1] += 1000L;
        source.update(Collections.singletonList(recording(2, time[0], 0L, false)));
        assertFalse(key.equals(card(source).key));
        assertEquals(time[0], card(source).chronoBase);
    }

    @Test
    public void unknownLifecycleReadDoesNotResetAnOtherwiseContinuousRecorder() {
        long[] time = {10000L, 20000L};
        long[] signal = {1L, 1L};
        RecorderNotificationSource source = source(time);
        source.setLifecycleSession(pkg -> signal[0] == 0L ? null : new CastSource.RecorderLifecycle(pkg, signal[0], signal[1]));
        source.update(Collections.singletonList(recording(1, 5000L, 0L, false)));
        String key = card(source).key;
        signal[0] = 0L;
        signal[1] = 0L;
        time[0] += 1000L;
        time[1] += 1000L;
        source.update(Collections.singletonList(recording(1, time[0], 0L, false)));
        signal[0] = 2L;
        signal[1] = 1L;
        source.update(Collections.singletonList(recording(1, time[0], 0L, false)));
        assertEquals(key, card(source).key);
        assertEquals(5000L, card(source).chronoBase);
    }

    @Test
    public void recordingChannelWinsOverPackageOnlyFallback() {
        RecorderNotificationSource source = source();
        StatusBarNotification broad = note("com.samsung.android.app.smartcapture", "capture", true);
        StatusBarNotification real = recording(2, 5000L, 1000L, true);
        source.update(java.util.Arrays.asList(broad, real));
        assertEquals(real.getKey(), card(source).sourceKey);
    }

    @Test
    public void tapToStopBecomesAStopButton() {
        RecorderNotificationSource source = source();
        source.update(Collections.singletonList(note("com.android.systemui", "screen_record", true, true)));
        List<IslandItem> out = new ArrayList<>();
        source.collect(out);
        IslandItem card = out.get(0);
        assertEquals(1, card.actions.size());
        assertEquals("Stop", card.actions.get(0).title.toString());
        assertEquals("Recording", card.subtitle.toString());
        org.junit.Assert.assertNull(card.contentIntent);
    }
}
