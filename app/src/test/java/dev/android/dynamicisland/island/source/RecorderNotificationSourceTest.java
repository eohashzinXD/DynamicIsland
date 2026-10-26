package dev.android.dynamicisland.island.source;

import static org.junit.Assert.assertEquals;
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

}
