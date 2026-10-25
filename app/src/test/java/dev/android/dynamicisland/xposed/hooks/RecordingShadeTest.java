package dev.android.dynamicisland.xposed.hooks;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Looper;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;
import dev.android.dynamicisland.island.IslandController;
import dev.android.dynamicisland.island.source.MediaRecordingTest;
import dev.android.dynamicisland.island.source.MediaSource;
import dev.android.dynamicisland.settings.ModuleSettings;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RecordingShadeTest {
    private final Application app = RuntimeEnvironment.getApplication();
    private final AtomicBoolean recording = new AtomicBoolean(true);
    private IslandController old;
    private boolean oldStarted;
    private boolean oldShade;
    private MediaSource media;
    private MediaController recorder;
    private View player;
    private TestExpandableNotificationRow row;
    private MediaPlayerBinding binding;
    private View recordingControls;
    private Runnable oldRulesListener;

    public static class Panel { public MediaController mController; }
    public static class TestExpandableNotificationRow extends FrameLayout {
        public TestExpandableNotificationRow(Context context) { super(context); }
    }

    @Before
    public void setup() throws Exception {
        old = ReflectionHelpers.getStaticField(IslandController.class, "instance");
        oldStarted = ReflectionHelpers.getStaticField(ModuleSettings.class, "started");
        oldShade = ReflectionHelpers.getStaticField(ModuleSettings.class, "shadeMedia");
        oldRulesListener = ReflectionHelpers.getStaticField(ModuleSettings.class, "onRulesChanged");
        ReflectionHelpers.setStaticField(ModuleSettings.class, "started", true);
        ReflectionHelpers.setStaticField(ModuleSettings.class, "shadeMedia", true);
        IslandController island = ReflectionHelpers.callConstructor(IslandController.class,
                ClassParameter.from(Context.class, app), ClassParameter.from(ClassLoader.class, getClass().getClassLoader()));
        ReflectionHelpers.setStaticField(IslandController.class, "instance", island);
        media = ReflectionHelpers.getField(ReflectionHelpers.getField(island, "sources"), "media");
        media.setRecordingFilter((pkg, user) -> recording.get() && "com.sec.android.app.voicenote".equals(pkg) && user == 0);
        recorder = MediaRecordingTest.controller(app, "com.sec.android.app.voicenote", 0, "Recording");
        media.sync(Collections.singletonList(recorder));
        Panel panel = new Panel();
        panel.mController = recorder;
        player = new View(app);
        row = new TestExpandableNotificationRow(app);
        row.addView(player);
        FrameLayout stack = new FrameLayout(app);
        stack.addView(row);
        recordingControls = new View(app);
        stack.addView(recordingControls);
        binding = new MediaPlayerBinding(player, panel);
        // Retain the panel through this test: the production binding correctly holds it weakly.
        player.setTag(panel);
        ShadeMediaPlayer.players().put(player, binding);
    }

    @After
    public void cleanup() {
        ShadeMediaPlayer.players().clear();
        ShadeMediaPlayer.refreshRecordingRows();
        ReflectionHelpers.setStaticField(IslandController.class, "instance", old);
        ReflectionHelpers.setStaticField(ModuleSettings.class, "started", oldStarted);
        ReflectionHelpers.setStaticField(ModuleSettings.class, "shadeMedia", oldShade);
        ReflectionHelpers.setStaticField(ModuleSettings.class, "onRulesChanged", oldRulesListener);
    }

    @Test
    public void recordingRowDisappearsAndFreshPlaybackRestoresItWithoutADrawCallback() {
        assertTrue(binding.recording(player));
        assertNull(binding.item(player));
        ShadeMediaPlayer.refreshRecordingRows();
        assertEquals(View.GONE, row.getVisibility());
        assertEquals(View.VISIBLE, recordingControls.getVisibility());
        recording.set(false);
        shadowOf(recorder).executeOnMetadataChanged(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Saved recording").build());
        shadowOf(recorder).executeOnPlaybackStateChanged(new PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, 0, 1).build());
        shadowOf(Looper.getMainLooper()).idle();
        ShadeMediaPlayer.refreshRecordingRows();
        assertEquals(View.VISIBLE, row.getVisibility());
        assertNotNull(binding.item(player));
    }

    @Test
    public void turningOffTheShadeOverrideRestoresTheStockRow() {
        ShadeMediaPlayer.refreshRecordingRows();
        assertEquals(View.GONE, row.getVisibility());
        ReflectionHelpers.setStaticField(ModuleSettings.class, "shadeMedia", false);
        ShadeMediaPlayer.refreshRecordingRows();
        assertEquals(View.VISIBLE, row.getVisibility());
        assertFalse(binding.recording(player));
    }

    @Test
    public void rebindingTheSameRowToAnotherMusicAppRestoresIt() throws Exception {
        ShadeMediaPlayer.refreshRecordingRows();
        assertEquals(View.GONE, row.getVisibility());
        MediaController music = MediaRecordingTest.controller(app, "com.music", 0, "Song");
        media.sync(java.util.Arrays.asList(recorder, music));
        ((Panel) player.getTag()).mController = music;
        ShadeMediaPlayer.refreshRecordingRows();
        assertEquals(View.VISIBLE, row.getVisibility());
        assertNotNull(binding.item(player));
    }

    @Test
    public void touchExplorationRestoresTheAccessibleStockPlayer() {
        ShadeMediaPlayer.refreshRecordingRows();
        assertEquals(View.GONE, row.getVisibility());
        AccessibilityManager manager = app.getSystemService(AccessibilityManager.class);
        shadowOf(manager).setTouchExplorationEnabled(true);
        try {
            ShadeMediaPlayer.refreshRecordingRows();
            assertEquals(View.VISIBLE, row.getVisibility());
            assertFalse(binding.recording(player));
            assertNull(binding.item(player));
        } finally {
            shadowOf(manager).setTouchExplorationEnabled(false);
        }
    }

    @Test
    public void recordingPlayerIsMeasuredAtZeroHeightRatherThanRestoringStockContent() {
        assertEquals(Integer.valueOf(0), MediaMeasure.playerHeight(player, binding, 360));
        ReflectionHelpers.setStaticField(ModuleSettings.class, "shadeMedia", false);
        assertNull(MediaMeasure.playerHeight(player, binding, 360));
    }

    @Test
    public void visibilityStateSurvivesSharedRowsAndRebinding() {
        RecordingRows rows = new RecordingRows();
        View other = new View(app);
        other.setVisibility(View.INVISIBLE);
        rows.sync(new HashSet<>(java.util.Arrays.asList(row, other)));
        assertEquals(View.GONE, row.getVisibility());
        rows.sync(Collections.singleton(row));
        assertEquals(View.INVISIBLE, other.getVisibility());
        assertEquals(View.GONE, row.getVisibility());
        rows.sync(Collections.emptySet());
        assertEquals(View.VISIBLE, row.getVisibility());
    }
}
