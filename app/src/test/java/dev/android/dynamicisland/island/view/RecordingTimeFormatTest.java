package dev.android.dynamicisland.island.view;

import static org.junit.Assert.assertEquals;

import android.os.SystemClock;
import dev.android.dynamicisland.island.model.IslandItem;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RecordingTimeFormatTest {
    @Test
    public void recordingTicksWithoutRecollectionAndIgnoresWallClockBase() {
        IslandItem item = new IslandItem("rec:test");
        item.chronoUsesElapsedRealtime = true;
        item.chronoElapsedBase = SystemClock.elapsedRealtime() - 5000L;
        item.chronoBase = System.currentTimeMillis() + 3600000L;
        assertEquals("0:05", TimeFormat.chronometer(item));
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1));
        item.chronoBase -= 7200000L; // no collect/update: the renderer must not consult this wall base
        assertEquals("0:06", TimeFormat.chronometer(item));
    }

    @Test
    public void ordinaryNativeChronometersKeepTheirWallClockPath() {
        IslandItem item = new IslandItem("timer:test");
        item.chronoBase = System.currentTimeMillis() - 5000L;
        item.chronoElapsedBase = SystemClock.elapsedRealtime() + 3600000L;
        assertEquals("0:05", TimeFormat.chronometer(item));
        item.chronoCountDown = true;
        item.chronoBase = System.currentTimeMillis() + 5000L;
        assertEquals("0:05", TimeFormat.chronometer(item));
    }
}
