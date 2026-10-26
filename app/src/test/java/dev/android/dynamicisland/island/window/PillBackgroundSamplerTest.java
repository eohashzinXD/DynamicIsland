package dev.android.dynamicisland.island.window;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Looper;
import android.view.Display;
import android.view.SurfaceControl;
import android.view.View;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

/** Exercises the direct sampler's lifecycle without loading its native listener bridge. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class PillBackgroundSamplerTest {
    private ActivityController<Activity> activity;
    private FrameLayout host;
    private View view;
    private Rect area;
    private Point displaySize;
    private FakeSurfaces surfaces;
    private FakeFactory factory;
    private final List<Float> samples = new ArrayList<>();
    private PillBackgroundSampler sampler;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup();
        host = new FrameLayout(activity.get());
        view = new View(activity.get());
        host.addView(view, new FrameLayout.LayoutParams(200, 80));
        activity.get().setContentView(host);
        activity.visible();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(view.isAttachedToWindow());
        assertTrue(view.isShown());
        assertEquals(View.VISIBLE, view.getWindowVisibility());
        assertNotNull(view.getDisplay());
        assertEquals(Display.DEFAULT_DISPLAY, view.getDisplay().getDisplayId());
        displaySize = new Point();
        view.getDisplay().getRealSize(displaySize);
        assertTrue(displaySize.x > 16 && displaySize.y > 16);
        area = new Rect(8, 8, Math.min(208, displaySize.x), Math.min(88, displaySize.y));
        surfaces = new FakeSurfaces();
        factory = new FakeFactory();
        sampler = new PillBackgroundSampler(view, samples::add, factory, surfaces);
        assertEquals(Arrays.asList((Float) null), samples);
    }

    @After public void tearDown() {
        if (sampler != null) sampler.destroy();
        if (activity != null) activity.pause().stop().destroy();
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void disabledOrHiddenDoesNotEvenCreateABackend() {
        sampler.update(area, false);
        view.setVisibility(View.INVISIBLE);
        sampler.update(area, true);
        view.setVisibility(View.GONE);
        sampler.update(area, true);
        assertEquals(0, factory.attempts);
        assertEquals(0, surfaces.copies.size());
        assertEquals(Arrays.asList((Float) null), samples);
        view.setVisibility(View.VISIBLE);
        activate();
    }

    @Test public void validSamplesRemainRawIncludingTrueBlackAndWhite() {
        FakeBackend backend = activate();
        backend.sample.accept(0f);
        backend.sample.accept(0.003f);
        backend.sample.accept(0.5f);
        backend.sample.accept(1f);
        assertEquals(Arrays.asList(null, 0f, 0.003f, 0.5f, 1f), samples);
        assertNotNull(backend.stopLayer);
        assertNotSame(surfaces.root, backend.stopLayer);
        assertTrue(surfaces.same(surfaces.root, backend.stopLayer));
        assertEquals(area, backend.bounds);
        assertEquals(1, backend.starts);
        assertEquals(1, backend.stops); // Defensive unregister before the first register.
    }

    @Test public void invalidCallbackValuesAreUnknownNotClampedToBlack() {
        FakeBackend backend = activate();
        for (Float raw : Arrays.asList(null, Float.NaN, Float.NEGATIVE_INFINITY,
                Float.POSITIVE_INFINITY, -0.01f, 1.01f)) {
            backend.sample.accept(0.4f);
            backend.sample.accept(raw);
            assertNull(samples.get(samples.size() - 1));
        }
        assertFalse(samples.contains(0f));
        int count = samples.size();
        backend.sample.accept(null);
        backend.sample.accept(Float.NaN);
        assertEquals("unchanged unknown notifications are coalesced", count, samples.size());
    }

    @Test public void invalidAreasNeverCreateABackendOrReportBlack() {
        Rect[] invalid = {null, new Rect(), new Rect(5, 5, 5, 9),
                new Rect(9, 5, 5, 9), new Rect(5, 9, 9, 5),
                new Rect(-1, 0, 20, 20), new Rect(0, -1, 20, 20),
                new Rect(0, 0, displaySize.x + 1, 20),
                new Rect(0, 0, 20, displaySize.y + 1)};
        for (Rect rect : invalid) sampler.update(rect, true);
        assertEquals(0, factory.attempts);
        assertEquals(0, surfaces.copies.size());
        assertEquals(Arrays.asList((Float) null), samples);
    }

    @Test public void areaLeavingDisplayUnregistersAndChangesKnownLumaToUnknown() {
        FakeBackend backend = activate();
        backend.sample.accept(0.6f);
        sampler.update(new Rect(0, 0, displaySize.x + 1, 20), true);
        assertReleased(backend);
        assertEquals(Arrays.asList(null, 0.6f, null), samples);
        backend.sample.accept(0f);
        assertEquals(Arrays.asList(null, 0.6f, null), samples);
    }

    @Test public void disablingReleasesOnlyOwnedCopyAndIsIdempotent() {
        FakeBackend backend = activate();
        backend.sample.accept(0f);
        sampler.update(area, false);
        assertReleased(backend);
        assertEquals(Arrays.asList(null, 0f, null), samples);
        sampler.update(area, false);
        assertReleased(backend);
        assertEquals(1, factory.attempts);
        assertEquals(Arrays.asList(null, 0f, null), samples);
    }

    @Test public void destroyReleasesOnceAndPermanentlyIgnoresUpdatesAndCallbacks() {
        FakeBackend backend = activate();
        backend.sample.accept(0.8f);
        sampler.destroy();
        sampler.destroy();
        sampler.update(area, true);
        backend.sample.accept(0f);
        assertReleased(backend);
        assertEquals(1, factory.attempts);
        assertEquals(Arrays.asList(null, 0.8f, null), samples);
    }

    @Test public void detachingUnregistersImmediatelyAndReattachingUsesAFreshCopy() {
        FakeBackend old = activate();
        old.sample.accept(0.8f);
        host.removeView(view);
        assertFalse(view.isAttachedToWindow());
        assertReleased(old);
        old.sample.accept(0f);
        assertEquals(Arrays.asList(null, 0.8f, null), samples);
        host.addView(view, new FrameLayout.LayoutParams(200, 80));
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(view.isAttachedToWindow());
        sampler.update(area, true);
        assertEquals(2, factory.attempts);
        FakeBackend next = factory.backends.get(1);
        assertNotSame(old.stopLayer, next.stopLayer);
        old.sample.accept(0f);
        next.sample.accept(0.2f);
        assertEquals(Arrays.asList(null, 0.8f, null, 0.2f), samples);
    }

    @Test public void queuedOldGenerationCannotOverwriteNewSession() throws Exception {
        FakeBackend old = activate();
        old.sample.accept(0.7f);
        Thread worker = new Thread(() -> old.sample.accept(0f));
        worker.start();
        worker.join(); // Delivery is now queued on the paused main looper.
        sampler.update(area, false);
        sampler.update(area, true);
        FakeBackend next = factory.backends.get(1);
        next.sample.accept(0.3f);
        old.sample.accept(1f);
        shadowOf(Looper.getMainLooper()).idle();
        assertReleased(old);
        assertEquals(Arrays.asList(null, 0.7f, null, 0.3f), samples);
        assertEquals(1, next.starts);
        assertEquals(0, next.destroys);
    }

    @Test public void replacementRootRestartsListenerWithANewOwnedCopy() {
        FakeBackend old = activate();
        SurfaceControl original = surfaces.root;
        surfaces.root = surfaces.newSurface();
        sampler.update(area, true);
        assertReleased(old);
        assertTrue(surfaces.valid(original));
        assertEquals(2, factory.attempts);
        FakeBackend next = factory.backends.get(1);
        assertNotSame(old.stopLayer, next.stopLayer);
        assertTrue(surfaces.same(surfaces.root, next.stopLayer));
        old.sample.accept(0f);
        next.sample.accept(0.4f);
        assertEquals(Arrays.asList(null, 0.4f), samples);
    }

    @Test public void sameJavaRootWithChangedNativeIdentityAlsoRestarts() {
        FakeBackend old = activate();
        SurfaceControl sameWrapper = surfaces.root;
        int checks = surfaces.sameChecks;
        surfaces.handles.put(sameWrapper, ++surfaces.nextHandle);
        sampler.update(area, true);
        assertSame(sameWrapper, surfaces.root);
        assertTrue("native identity must be checked rather than Java reference equality",
                surfaces.sameChecks > checks);
        assertReleased(old);
        assertEquals(2, factory.attempts);
        FakeBackend next = factory.backends.get(1);
        assertTrue(surfaces.same(sameWrapper, next.stopLayer));
        old.sample.accept(0f);
        next.sample.accept(0.9f);
        assertEquals(Arrays.asList(null, 0.9f), samples);
    }

    @Test public void invalidRootNeverRegistersAndInvalidationDuringCallbackReleases() {
        SurfaceControl original = surfaces.root;
        surfaces.handles.remove(original);
        sampler.update(area, true);
        assertEquals(0, factory.attempts);
        assertEquals(Arrays.asList((Float) null), samples);
        surfaces.root = surfaces.newSurface();
        FakeBackend backend = activate();
        backend.sample.accept(0.5f);
        surfaces.handles.remove(surfaces.root);
        backend.sample.accept(0f);
        assertReleased(backend);
        assertEquals(Arrays.asList(null, 0.5f, null), samples);
    }

    @Test public void unavailableBackendReportsUnknownOnceAndNeverRetriesPerFrame() {
        factory.unavailable = true;
        sampler.update(area, true);
        for (int i = 0; i < 20; i++) sampler.update(area, true);
        sampler.update(area, false);
        surfaces.root = surfaces.newSurface();
        sampler.update(area, true);
        assertEquals(1, factory.attempts);
        assertTrue(factory.backends.isEmpty());
        assertEquals(1, surfaces.copies.size());
        assertEquals(Integer.valueOf(1), surfaces.releases.get(surfaces.copies.get(0)));
        assertFalse(surfaces.releases.containsKey(surfaces.root));
        assertEquals(Arrays.asList((Float) null), samples);
    }

    @Test public void startFailureDestroysBackendAndReleasesCopyWithoutRetry() {
        factory.failStart = true;
        sampler.update(area, true);
        FakeBackend backend = factory.backends.get(0);
        assertReleased(backend);
        for (int i = 0; i < 10; i++) sampler.update(area, true);
        backend.sample.accept(0f);
        assertEquals(1, factory.attempts);
        assertEquals(Arrays.asList((Float) null), samples);
    }

    @Test public void cleanupStillReleasesSurfaceIfStopAndDestroyThrow() {
        FakeBackend backend = activate();
        backend.failCleanup = true;
        sampler.update(area, false);
        assertReleased(backend);
        sampler.destroy();
        assertReleased(backend);
    }

    @Test public void unchangedUpdatesDoNotReregisterButChangedBoundsDo() {
        FakeBackend backend = activate();
        Rect firstRegistration = backend.bounds;
        for (int i = 0; i < 20; i++) sampler.update(new Rect(area), true);
        assertEquals(1, factory.attempts);
        assertEquals(1, backend.starts);
        assertEquals(1, backend.stops);
        Rect changed = new Rect(area);
        changed.left++;
        sampler.update(changed, true);
        assertEquals(1, factory.attempts);
        assertEquals(2, backend.starts);
        assertEquals(2, backend.stops);
        assertEquals(changed, backend.bounds);
        assertEquals("previous backend arguments must stay immutable", area, firstRegistration);
        assertEquals(1, surfaces.copies.size());
    }

    @Test public void inputRectIsCopiedBeforePostingToMainThread() throws Exception {
        Rect input = new Rect(area);
        Thread worker = new Thread(() -> sampler.update(input, true));
        worker.start();
        worker.join();
        assertEquals(0, factory.attempts);
        input.set(-50, -50, -1, -1);
        shadowOf(Looper.getMainLooper()).idle();
        FakeBackend backend = factory.backends.get(0);
        assertEquals(area, backend.bounds);
        sampler.update(new Rect(area), true);
        assertEquals(1, backend.starts);
        assertNotSame(input, backend.bounds);
    }

    private FakeBackend activate() {
        sampler.update(area, true);
        assertEquals(1, factory.attempts);
        assertEquals(1, factory.backends.size());
        FakeBackend backend = factory.backends.get(0);
        assertEquals(1, backend.starts);
        return backend;
    }

    private void assertReleased(FakeBackend backend) {
        assertEquals(2, backend.stops);
        assertEquals(1, backend.destroys);
        assertNotNull(backend.stopLayer);
        assertEquals(Integer.valueOf(1), surfaces.releases.get(backend.stopLayer));
        for (SurfaceControl root : surfaces.roots) {
            assertFalse("ViewRoot surfaces are borrowed, never released", surfaces.releases.containsKey(root));
        }
    }

    /** Every surface is a harmless Java wrapper; identity/validity live only in these maps. */
    private static final class FakeSurfaces implements PillBackgroundSampler.SurfaceAccess {
        final Map<SurfaceControl, Long> handles = new IdentityHashMap<>();
        final Map<SurfaceControl, Integer> releases = new IdentityHashMap<>();
        final List<SurfaceControl> copies = new ArrayList<>();
        final List<SurfaceControl> roots = new ArrayList<>();
        long nextHandle;
        int sameChecks;
        SurfaceControl root = newSurface();

        SurfaceControl newSurface() {
            SurfaceControl surface = ReflectionHelpers.newInstance(SurfaceControl.class);
            handles.put(surface, ++nextHandle);
            roots.add(surface);
            return surface;
        }

        @Override public SurfaceControl current(View ignored) { return root; }
        @Override public boolean valid(SurfaceControl surface) {
            return surface != null && handles.containsKey(surface);
        }
        @Override public SurfaceControl copy(SurfaceControl surface) {
            SurfaceControl owned = ReflectionHelpers.newInstance(SurfaceControl.class);
            handles.put(owned, handles.get(surface));
            copies.add(owned);
            return owned;
        }
        @Override public boolean same(SurfaceControl a, SurfaceControl b) {
            sameChecks++;
            return valid(a) && valid(b) && Objects.equals(handles.get(a), handles.get(b));
        }
        @Override public void release(SurfaceControl surface) {
            releases.put(surface, releases.getOrDefault(surface, 0) + 1);
            handles.remove(surface);
        }
    }

    private static final class FakeFactory implements PillBackgroundSampler.BackendFactory {
        final List<FakeBackend> backends = new ArrayList<>();
        int attempts;
        boolean unavailable, failStart;

        @Override public PillBackgroundSampler.Backend create(Executor executor, Consumer<Float> sample) {
            attempts++;
            assertNotNull(executor);
            assertNotNull(sample);
            if (unavailable) throw new LinkageError("composition sampling bridge unavailable");
            FakeBackend backend = new FakeBackend(sample, failStart);
            backends.add(backend);
            return backend;
        }
    }

    private static final class FakeBackend implements PillBackgroundSampler.Backend {
        final Consumer<Float> sample;
        final boolean failStart;
        int starts, stops, destroys;
        boolean failCleanup;
        SurfaceControl stopLayer;
        Rect bounds;

        FakeBackend(Consumer<Float> sample, boolean failStart) {
            this.sample = sample;
            this.failStart = failStart;
        }

        @Override public void start(SurfaceControl stopLayer, Rect bounds) {
            starts++;
            assertNotNull("never sample without an island stop layer", stopLayer);
            assertNotNull(bounds);
            assertFalse(bounds.isEmpty());
            this.stopLayer = stopLayer;
            this.bounds = bounds; // Keep the actual argument to detect subsequent mutation.
            if (failStart) throw new IllegalStateException("register failed");
        }
        @Override public void stop() {
            stops++;
            if (failCleanup) throw new IllegalStateException("unregister failed");
        }
        @Override public void destroy() {
            destroys++;
            if (failCleanup) throw new IllegalStateException("destroy failed");
        }
    }
}
