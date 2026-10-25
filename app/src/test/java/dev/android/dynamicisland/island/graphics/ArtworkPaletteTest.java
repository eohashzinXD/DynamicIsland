package dev.android.dynamicisland.island.graphics;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Random;
import org.junit.Test;

/** Plain JUnit: no Android or Robolectric, also executable directly with javac/JUnitCore. */
public class ArtworkPaletteTest {
    @Test
    public void nullEmptyBlackAndGrayscaleStayNeutral() {
        ArtworkPalette fallback = ArtworkPalette.fromPixels(null);
        assertEquals(fallback, ArtworkPalette.fromPixels(new int[0]));
        assertEquals(fallback, ArtworkPalette.fromPixels(new int[]{0xFF000000, 0xFF060606}));
        assertEquals(fallback, ArtworkPalette.fromPixels(new int[]{0xFFFFFFFF, 0xFF777777, 0xFFDADADA}));
        assertEquals(0xFFFFFFFF, fallback.primary);
        for (int color : stops(fallback)) {
            assertEquals((color >>> 16) & 255, (color >>> 8) & 255);
            assertEquals(color & 255, (color >>> 8) & 255);
            assertVisible(color);
        }
    }

    @Test
    public void transparentAndLowAlphaPixelsNeverVote() {
        int[] clean = repeated(80, 0xFF4BAF99, 0xFF58B47E, 0xFF459FC0);
        int[] dirty = Arrays.copyOf(clean, clean.length + 900);
        Arrays.fill(dirty, clean.length, clean.length + 300, 0x00FF0000);
        Arrays.fill(dirty, clean.length + 300, clean.length + 600, 0x01FF00FF);
        Arrays.fill(dirty, clean.length + 600, dirty.length, 0x7F0000FF);
        assertEquals(ArtworkPalette.fromPixels(clean), ArtworkPalette.fromPixels(dirty));
        assertEquals(ArtworkPalette.fromPixels(null),
                ArtworkPalette.fromPixels(new int[]{0x00FF0000, 0x7FFF00FF}));
    }

    @Test
    public void acceptedAlphaIsWeightedAndAllOutputsAreOpaque() {
        ArtworkPalette palette = ArtworkPalette.fromPixels(repeated(40, 0x80CC7444, 0xFFB85454));
        assertNotEquals(ArtworkPalette.fromPixels(null), palette);
        for (int color : stops(palette)) {
            assertEquals(255, color >>> 24);
        }
    }

    @Test
    public void neighboringArtworkColorsProduceRealDistinctStops() {
        ArtworkPalette palette = ArtworkPalette.fromPixels(
                repeated(100, 0xFF4499CC, 0xFF44CCBB, 0xFF4488CC));
        assertNotEquals(palette.first, palette.last);
        assertNotEquals(palette.first, palette.middle);
        assertNotEquals(palette.middle, palette.last);
        assertTrue(hue(palette.first) < hue(palette.middle));
        assertTrue(hue(palette.middle) < hue(palette.last));
        assertTrue(distance(hue(palette.first), hue(0xFF44CCBB)) < 2);
        assertTrue(distance(hue(palette.middle), hue(0xFF4499CC)) < 2);
        assertTrue(distance(hue(palette.last), hue(0xFF4488CC)) < 6);
        assertTrue(channelDistance(palette.first, palette.last) > 130);
        for (int color : stops(palette)) {
            assertVisible(color);
        }
    }

    @Test
    public void twoArtworkColorsUseSmoothInterpolatedMiddle() {
        ArtworkPalette palette = ArtworkPalette.fromPixels(repeated(100, 0xFF4499CC, 0xFF44CCBB));
        assertNotEquals(palette.first, palette.last);
        assertNotEquals(palette.first, palette.middle);
        assertNotEquals(palette.middle, palette.last);
        assertEquals(half(palette.first, palette.last), palette.middle);
    }

    @Test
    public void primaryKeepsLegacyDominantBucketBoost() {
        ArtworkPalette palette = ArtworkPalette.fromPixels(repeated(40, 0xFF4499CC));
        // Old 12-bucket vote, saturation * value, S * 1.15 and V >= .85.
        assertEquals(0xFF339AD9, palette.primary);
        ArtworkPalette green = ArtworkPalette.fromPixels(concat(
                repeated(90, 0xFF40BA70), repeated(20, 0xFFFF8040)));
        assertTrue(distance(hue(green.primary), hue(0xFF40BA70)) < 2);
    }

    @Test
    public void contrastingMinorityAndNearbySpeckDoNotBecomeStops() {
        int[] family = repeated(100, 0xFF4BBF88, 0xFF4BBFAB, 0xFF6ABF4B);
        ArtworkPalette clean = ArtworkPalette.fromPixels(family);
        int[] withLogo = concat(family, repeated(60, 0xFFFF315B));
        assertEquals(clean, ArtworkPalette.fromPixels(withLogo));
        ArtworkPalette speck = ArtworkPalette.fromPixels(concat(family, new int[]{0xFFBFB44B}));
        assertEquals(clean, speck);
        for (int color : stops(clean)) {
            assertTrue(distance(hue(color), hue(clean.primary)) <= 43);
        }
    }

    @Test
    public void redWraparoundUsesShortArcNotGreenOrCyan() {
        ArtworkPalette palette = ArtworkPalette.fromPixels(
                repeated(70, 0xFFCC4466, 0xFFCC4444, 0xFFCC6644));
        assertNotEquals(palette.first, palette.last);
        assertTrue(hue(palette.first) > 330);
        assertTrue(hue(palette.last) < 30);
        for (int i = 0; i <= 100; i++) {
            assertTrue(distance(hue(palette.colorAt(i / 100f)), 0) < 25);
        }
    }

    @Test
    public void monochromeArtUsesVisibleButBoundedAnalogousTones() {
        for (int source : new int[]{0xFF9865CA, 0xFFCC0033, 0xFF55AAC8}) {
            ArtworkPalette palette = ArtworkPalette.fromPixels(repeated(100, source));
            assertNotEquals(palette.first, palette.last);
            for (int color : stops(palette)) {
                assertTrue(distance(hue(color), hue(palette.primary)) < 24);
                assertVisible(color);
            }
            // Red's visibility lift compresses RGB distance; the >=40-degree analogous
            // arc plus >110 channel steps still guarantees a clearly non-flat gradient.
            assertTrue(channelDistance(palette.first, palette.last) > 110);
            assertTrue(distance(hue(palette.first), hue(palette.last)) >= 40);
        }
    }

    @Test
    public void substantialMutedVioletIsRecoveredButGrayscaleLogoIsNot() {
        ArtworkPalette muted = ArtworkPalette.fromPixels(repeated(100, 0xFF62596A, 0xFF443D49));
        assertNotEquals(ArtworkPalette.fromPixels(null), muted);
        assertTrue(distance(hue(muted.primary), 280) < 25);
        assertTrue(channelDistance(muted.first, muted.last) > 130);
        for (int color : stops(muted)) {
            assertVisible(color);
        }
        int[] grayWithTinyMutedLogo = concat(repeated(500, 0xFFDDDDDD), repeated(5, 0xFF62596A));
        assertEquals(ArtworkPalette.fromPixels(null), ArtworkPalette.fromPixels(grayWithTinyMutedLogo));
    }

    @Test
    public void darkAndDesaturatedPixelsCannotOverruleColor() {
        int[] family = repeated(80, 0xFF4E9AB9, 0xFF4EB99A);
        assertEquals(ArtworkPalette.fromPixels(family), ArtworkPalette.fromPixels(concat(family,
                repeated(200, 0xFF222222, 0xFFDAD5D1, 0xFF330000, 0xFF003300))));
    }

    @Test
    public void deterministicAndDoesNotMutateOrRetainInput() {
        int[] pixels = repeated(100, 0xFFB54A90, 0xFF934AB5, 0xFFB54A62);
        int[] saved = pixels.clone();
        ArtworkPalette first = ArtworkPalette.fromPixels(pixels);
        assertArrayEquals(saved, pixels);
        assertEquals(first, ArtworkPalette.fromPixels(pixels));
        shuffle(pixels, new Random(18));
        ArtworkPalette shuffled = ArtworkPalette.fromPixels(pixels);
        assertEquals(first, shuffled);
        assertEquals(first.hashCode(), shuffled.hashCode());
        Arrays.fill(pixels, 0xFF00FF00);
        assertEquals(first, ArtworkPalette.fromPixels(saved));
    }

    @Test
    public void solidClampsAndInterpolatesOpaque() {
        ArtworkPalette solid = ArtworkPalette.solid(0x01123456);
        assertEquals(0xFF123456, solid.primary);
        for (int color : stops(solid)) {
            assertEquals(0xFF123456, color);
        }
        assertEquals(solid, ArtworkPalette.solid(0xFF123456));
        assertEquals(solid.hashCode(), ArtworkPalette.solid(0xFF123456).hashCode());
        assertNotEquals(solid, ArtworkPalette.solid(0xFF123457));
        assertNotEquals(solid, null);
        assertNotEquals(solid, "palette");
    }

    @Test
    public void interpolationPassesStopsAndClampsInvalidPositions() {
        ArtworkPalette palette = ArtworkPalette.fromPixels(
                repeated(100, 0xFF4499CC, 0xFF44CCBB, 0xFF4488CC));
        assertEquals(palette.first, palette.colorAt(0));
        assertEquals(palette.middle, palette.colorAt(.5f));
        assertEquals(palette.last, palette.colorAt(1));
        assertEquals(palette.first, palette.colorAt(-1));
        assertEquals(palette.last, palette.colorAt(2));
        assertEquals(palette.first, palette.colorAt(Float.NaN));
        assertEquals(palette.first, palette.colorAt(Float.NEGATIVE_INFINITY));
        assertEquals(palette.last, palette.colorAt(Float.POSITIVE_INFINITY));
        assertEquals(half(palette.first, palette.middle), palette.colorAt(.25f));
        assertEquals(half(palette.middle, palette.last), palette.colorAt(.75f));
    }

    @Test
    public void randomArtworkAlwaysStaysVisibleInOneShortHueFamily() {
        Random random = new Random(45);
        for (int round = 0; round < 200; round++) {
            int[] pixels = new int[576];
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = 0xFF000000 | random.nextInt(0x1000000);
            }
            ArtworkPalette palette = ArtworkPalette.fromPixels(pixels);
            for (int color : stops(palette)) {
                assertVisible(color);
                assertTrue(distance(hue(palette.primary), hue(color)) <= 44);
            }
            float arc = (hue(palette.last) - hue(palette.first) + 360) % 360;
            assertTrue("Stops must stay on one short arc: " + arc, arc <= 86 || arc >= 358);
        }
    }

    private static int[] repeated(int count, int... colors) {
        int[] pixels = new int[count * colors.length];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = colors[i % colors.length];
        }
        return pixels;
    }

    private static int[] concat(int[] a, int[] b) {
        int[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static int[] stops(ArtworkPalette palette) {
        return new int[]{palette.primary, palette.first, palette.middle, palette.last};
    }

    private static float hue(int color) {
        float r = (color >>> 16) & 255;
        float g = (color >>> 8) & 255;
        float b = color & 255;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        if (max == min) {
            return 0;
        }
        float value = max == r ? ((g - b) / (max - min)) % 6
                : max == g ? (b - r) / (max - min) + 2 : (r - g) / (max - min) + 4;
        return (value * 60 + 360) % 360;
    }

    private static float distance(float a, float b) {
        return Math.abs((b - a + 540) % 360 - 180);
    }

    private static int channelDistance(int a, int b) {
        int sum = 0;
        for (int shift : new int[]{0, 8, 16}) {
            sum += Math.abs(((a >>> shift) & 255) - ((b >>> shift) & 255));
        }
        return sum;
    }

    private static int half(int a, int b) {
        int result = 0xFF000000;
        for (int shift : new int[]{0, 8, 16}) {
            result |= Math.round((((a >>> shift) & 255) + ((b >>> shift) & 255)) / 2f) << shift;
        }
        return result;
    }

    private static void assertVisible(int color) {
        assertEquals(255, color >>> 24);
        double luminance = .2126 * linear((color >>> 16) & 255)
                + .7152 * linear((color >>> 8) & 255) + .0722 * linear(color & 255);
        assertTrue("Not visible: " + Integer.toHexString(color), luminance >= .22);
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= .04045 ? c / 12.92 : Math.pow((c + .055) / 1.055, 2.4);
    }

    private static void shuffle(int[] pixels, Random random) {
        for (int i = pixels.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = pixels[i];
            pixels[i] = pixels[j];
            pixels[j] = swap;
        }
    }
}
