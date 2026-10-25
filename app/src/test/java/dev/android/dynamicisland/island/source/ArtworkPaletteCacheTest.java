package dev.android.dynamicisland.island.source;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.os.Handler;
import android.os.Looper;
import dev.android.dynamicisland.island.graphics.ArtworkPalette;
import dev.android.dynamicisland.island.model.IslandItem;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.ReflectionHelpers;

/** Bitmap readback/cache integration. Native graphics tests require a supported desktop host. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ArtworkPaletteCacheTest {
    private static final String PKG = "com.music";
    private final Application app = RuntimeEnvironment.getApplication();

    @Test
    public void nullAndRecycledArtUseNeutralFallback() {
        assertEquals(ArtworkPalette.fromPixels(null), CoverAccent.palette(null));
        assertEquals(-1, CoverAccent.of(null));
        Bitmap bitmap = art(24, 0xFF9977BB);
        bitmap.recycle();
        assertEquals(ArtworkPalette.fromPixels(null), CoverAccent.palette(bitmap));
    }

    @Test
    public void exactSizeInputIsNotRecycledOrMutated() {
        Bitmap bitmap = art(24, 0xFF9977BB);
        int[] before = pixels(bitmap);
        assertEquals(ArtworkPalette.fromPixels(before), CoverAccent.palette(bitmap));
        assertEquals(CoverAccent.palette(bitmap).primary, CoverAccent.of(bitmap));
        assertFalse(bitmap.isRecycled());
        assertArrayEquals(before, pixels(bitmap));
    }

    @Test
    public void largeInputSurvivesTemporarySampleCleanup() {
        Bitmap bitmap = art(96, 0xFF4499CC);
        ArtworkPalette palette = CoverAccent.palette(bitmap);
        assertNotEquals(ArtworkPalette.fromPixels(null), palette);
        assertFalse(bitmap.isRecycled());
        assertEquals(0xFF4499CC, bitmap.getPixel(95, 95));
        assertEquals(palette, CoverAccent.palette(bitmap));
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.LEGACY)
    public void modeledHardwareArtIsReadBackWithoutRecyclingInput() {
        // Desktop native Skia can coerce copy(HARDWARE) back to software. The legacy shadow
        // explicitly models the HARDWARE config so this test exercises the readback branch;
        // real GPU-backed readback still needs device verification. Native software sampling
        // and cleanup remain covered by the other tests in this class.
        Bitmap software = art(24, 0xFF9977BB);
        Bitmap hardware = software.copy(Bitmap.Config.HARDWARE, false);
        assertNotNull(hardware);
        assertEquals(Bitmap.Config.HARDWARE, hardware.getConfig());
        try {
            assertEquals(CoverAccent.palette(software), CoverAccent.palette(hardware));
            assertFalse(hardware.isRecycled());
            assertFalse(software.isRecycled());
        } finally {
            hardware.recycle();
            software.recycle();
        }
    }

    @Test
    public void resolvedIdenticalAndGraceRetainedArtShareOnePalette() {
        MediaSource source = source();
        Bitmap first = art(24, 0xFF9977BB);
        assertSame(first, source.resolveArt(PKG, first, "one"));
        ArtworkPalette initial = cached(source);
        assertNotNull(initial);
        assertSame(first, source.resolveArt(PKG, first, "one"));
        assertSame(initial, cached(source));
        Bitmap identical = first.copy(Bitmap.Config.ARGB_8888, false);
        assertSame(first, source.resolveArt(PKG, identical, "two"));
        assertSame(initial, cached(source));
        assertFalse(identical.isRecycled());
        assertSame(first, source.resolveArt(PKG, null, "two"));
        assertSame(first, source.resolveArt(PKG, null, "three"));
        ShadowSystemClock.advanceBy(Duration.ofMillis(1499));
        assertSame(first, source.resolveArt(PKG, null, "three"));
        ShadowSystemClock.advanceBy(Duration.ofMillis(1));
        assertNull(source.resolveArt(PKG, null, "three"));
        assertSame(initial, cached(source));
        // After grace expiry the old cover is still remembered, not reanalysed if it returns.
        assertSame(first, source.resolveArt(PKG, identical, "three"));
        assertSame(initial, cached(source));
        Bitmap changed = art(24, 0xFF44BB88);
        assertSame(changed, source.resolveArt(PKG, changed, "four"));
        assertNotSame(initial, cached(source));
    }

    @Test
    public void rebuiltSessionCardsReusePaletteAndKeepPrimaryAccent() throws Exception {
        MediaSource source = source();
        MediaController controller = MediaRecordingTest.controller(app, PKG, 0, "Song");
        Bitmap bitmap = art(24, 0xFF9977BB);
        shadowOf(controller).setMetadata(metadata("Song", bitmap));
        source.sync(Collections.singletonList(controller));
        IslandItem first = source.itemForSession(controller.getSessionToken());
        assertSame(cached(source), first.mediaPalette);
        assertEquals(first.mediaPalette.primary, first.accent);
        assertSame(first, source.itemForSession(controller.getSessionToken()));
        shadowOf(controller).executeOnMetadataChanged(metadata("Song", bitmap));
        shadowOf(Looper.getMainLooper()).idle();
        IslandItem rebuilt = source.itemForSession(controller.getSessionToken());
        assertNotSame(first, rebuilt);
        assertSame(first.mediaPalette, rebuilt.mediaPalette);
        Bitmap duplicate = bitmap.copy(Bitmap.Config.ARGB_8888, false);
        shadowOf(controller).executeOnMetadataChanged(metadata("Next", duplicate));
        shadowOf(Looper.getMainLooper()).idle();
        IslandItem next = source.itemForSession(controller.getSessionToken());
        assertSame(bitmap, next.art);
        assertSame(first.mediaPalette, next.mediaPalette);
        shadowOf(controller).executeOnMetadataChanged(metadata("No cover", null));
        shadowOf(Looper.getMainLooper()).idle();
        assertSame(first.mediaPalette, source.itemForSession(controller.getSessionToken()).mediaPalette);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1520));
        IslandItem expired = source.itemForSession(controller.getSessionToken());
        assertNull(expired.art);
        assertNull(expired.mediaPalette);
        assertEquals(-1, expired.accent);
    }

    private MediaSource source() {
        return new MediaSource(app, new Handler(Looper.getMainLooper()), () -> { });
    }

    private static MediaMetadata metadata(String title, Bitmap art) {
        return new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art).build();
    }

    private static ArtworkPalette cached(MediaSource source) {
        Map<?, ?> covers = ReflectionHelpers.getField(source, "covers");
        return ReflectionHelpers.getField(covers.get(PKG), "palette");
    }

    private static Bitmap art(int size, int color) {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return bitmap;
    }

    private static int[] pixels(Bitmap bitmap) {
        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        return pixels;
    }
}
