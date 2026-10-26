package dev.android.dynamicisland.island;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ForegroundAppTest {
    private static final String SPOTIFY = "com.spotify.music";
    private static final String YOUTUBE = "com.google.android.youtube";
    private static final String OTHER = "com.example.other";

    private static ForegroundApp.Task task(String pkg, Boolean focused, boolean visible, boolean pinned) {
        return new ForegroundApp.Task(pkg, focused, visible, pinned);
    }

    private static ForegroundApp.State read(String cached, ForegroundApp.Task... tasks) {
        return new ForegroundApp(() -> Arrays.asList(tasks), () -> cached).read();
    }

    private static void confirmed(String pkg, ForegroundApp.State state) {
        assertEquals(pkg, state.packageName);
        assertTrue(state.confirmed);
    }

    private static void uncertain(String pkg, ForegroundApp.State state) {
        assertEquals(pkg, state.packageName);
        assertFalse(state.confirmed);
    }

    private static ActivityManager.RunningTaskInfo actualTask(String top, String base)
            throws ReflectiveOperationException {
        ActivityManager.RunningTaskInfo info = new ActivityManager.RunningTaskInfo();
        info.topActivity = top == null ? null : new ComponentName(top, top + ".Player");
        info.baseActivity = new ComponentName(base, base + ".Main");
        info.getClass().getField("isFocused").setBoolean(info, true);
        info.getClass().getField("isVisible").setBoolean(info, true);
        return info;
    }

    private static ForegroundApp platformReader(String cached,
            ActivityManager.RunningTaskInfo... tasks) {
        Context context = RuntimeEnvironment.getApplication();
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        Shadows.shadowOf(manager).setTasks(Arrays.asList(tasks));
        return new ForegroundApp(context, () -> cached);
    }

    @Test
    public void topActivityOverridesStaleBaseAndCacheWhetherCacheMatchesOrDiffers()
            throws ReflectiveOperationException {
        ActivityManager.RunningTaskInfo info = actualTask(SPOTIFY, OTHER);
        confirmed(SPOTIFY, platformReader(OTHER, info).read());
        confirmed(SPOTIFY, platformReader(SPOTIFY, info).read());
        confirmed(YOUTUBE, platformReader(SPOTIFY, actualTask(YOUTUBE, SPOTIFY)).read());
    }

    @Test
    public void missingTopActivityDoesNotSelectBaseActivity() throws ReflectiveOperationException {
        uncertain(null, platformReader(null, actualTask(null, SPOTIFY)).read());
    }

    @Test
    public void platformPinnedModeIsExcludedEvenIfFocused() throws ReflectiveOperationException {
        ActivityManager.RunningTaskInfo info = actualTask(YOUTUBE, YOUTUBE);
        Object configuration = info.getClass().getField("configuration").get(info);
        Object window = configuration.getClass().getField("windowConfiguration").get(configuration);
        window.getClass().getMethod("setWindowingMode", int.class).invoke(window, 2);
        uncertain(null, platformReader(YOUTUBE, info).read());
    }

    @Test
    public void platformExplicitlyInvisibleTaskIsSkipped() throws ReflectiveOperationException {
        ActivityManager.RunningTaskInfo info = actualTask(SPOTIFY, SPOTIFY);
        info.getClass().getField("isVisible").setBoolean(info, false);
        uncertain(null, platformReader(null, info).read());
    }

    @Test
    public void focusedSplitScreenTaskWinsOverFirstVisibleTask() {
        confirmed(YOUTUBE, read(SPOTIFY,
                task(SPOTIFY, false, true, false), task(YOUTUBE, true, true, false)));
    }

    @Test
    public void pinnedTaskNeverWinsOverFocusedFullscreenTask() {
        confirmed(OTHER, read(YOUTUBE,
                task(YOUTUBE, true, true, true), task(OTHER, true, true, false)));
    }

    @Test
    public void noFocusedTaskLeavesEvenMatchingCacheUnconfirmed() {
        uncertain(SPOTIFY, read(SPOTIFY, task(SPOTIFY, false, true, false)));
        uncertain(OTHER, read(OTHER, task(SPOTIFY, false, true, false)));
        uncertain(null, read(null, task(SPOTIFY, false, true, false)));
    }

    @Test
    public void missingFocusFlagsUseFirstEligibleTaskInOrder() {
        confirmed(SPOTIFY, read(OTHER,
                task(YOUTUBE, null, true, true), task(SPOTIFY, null, true, false),
                task(OTHER, null, true, false)));
    }

    @Test
    public void partialFocusInformationDoesNotPromoteUnknownRecentTask() {
        uncertain(OTHER, read(OTHER,
                task(SPOTIFY, null, true, false), task(YOUTUBE, false, true, false)));
    }

    @Test
    public void invisibleNullTopAndNullTaskAreSkipped() {
        confirmed(OTHER, read(SPOTIFY, null,
                task(SPOTIFY, true, false, false), task(null, true, true, false),
                task(OTHER, true, true, false)));
        confirmed(OTHER, read(SPOTIFY, null,
                task(SPOTIFY, null, false, false), task(null, null, true, false),
                task(OTHER, null, true, false)));
    }

    @Test
    public void queryFailureFallsBackUncertainThenNextReadRecovers() {
        AtomicInteger calls = new AtomicInteger();
        ForegroundApp reader = new ForegroundApp(() -> {
            if (calls.getAndIncrement() == 0) throw new SecurityException("task permission");
            return Collections.singletonList(task(SPOTIFY, true, true, false));
        }, () -> OTHER);
        uncertain(OTHER, reader.read());
        confirmed(SPOTIFY, reader.read());
        assertEquals(2, calls.get());
    }

    @Test
    public void unavailableApiFallsBackWithoutConfirmingCache() {
        ForegroundApp reader = new ForegroundApp(() -> {
            throw new NoSuchMethodError("getRunningTasks");
        }, () -> SPOTIFY);
        uncertain(SPOTIFY, reader.read());
    }

    @Test
    public void cachedPipPackageIsNotForegroundEvenWhenInvisible() {
        uncertain(null, read(YOUTUBE, task(YOUTUBE, true, true, true)));
        uncertain(null, read(YOUTUBE, task(YOUTUBE, false, false, true)));
        uncertain(OTHER, read(OTHER, task(YOUTUBE, true, true, true)));
    }

    @Test
    public void pinnedFocusDoesNotPromoteUnknownBackgroundTask() {
        uncertain(null, read(YOUTUBE,
                task(YOUTUBE, true, true, true), task(OTHER, null, true, false)));
    }

    @Test
    public void emptyOrNullQueryUsesOnlyUnconfirmedCache() {
        uncertain(SPOTIFY, read(SPOTIFY));
        uncertain(null, read(null));
        uncertain(null, read(""));
        uncertain(SPOTIFY, new ForegroundApp(() -> null, () -> SPOTIFY).read());
    }

    @Test
    public void cacheFailureIsUnknownAndCacheIsNotReadWhenActualTaskWins() {
        ForegroundApp reader = new ForegroundApp(Collections::emptyList, () -> {
            throw new IllegalStateException("cache unavailable");
        });
        ForegroundApp.State state = reader.read();
        assertNull(state.packageName);
        assertFalse(state.confirmed);
        List<ForegroundApp.Task> tasks = Collections.singletonList(task(SPOTIFY, true, true, false));
        confirmed(SPOTIFY, new ForegroundApp(() -> tasks, () -> {
            throw new AssertionError("actual task must bypass stale cache");
        }).read());
    }
}
