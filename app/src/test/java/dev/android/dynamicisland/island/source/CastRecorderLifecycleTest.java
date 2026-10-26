package dev.android.dynamicisland.island.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CastRecorderLifecycleTest {
    private static final String RECORDER = "com.android.systemui";
    private static final String SAMSUNG = "com.samsung.android.app.smartcapture";
    private final CastSource source = new CastSource(RuntimeEnvironment.getApplication());

    public static final class Active {
        public final String managingPackageName;

        Active(String pkg) { managingPackageName = pkg; }
    }

    public static final class Inactive { }

    public static final class Bundle {
        public final Object screenRecord;
        public final Object shareToApp;

        Bundle(Object record, Object share) {
            screenRecord = record;
            shareToApp = share;
        }
    }

    private void record(String pkg) {
        source.onBundle(new Bundle(new Active(pkg), new Inactive()));
    }

    @Test
    public void uninterruptedSameOwnerKeepsPositiveSessionAndStartTokens() {
        record(RECORDER);
        long session = source.recorderSession(RECORDER);
        long start = source.recorderStart(RECORDER);
        assertTrue(session > 0L);
        assertTrue(start > 0L);
        for (int i = 0; i < 3; i++) {
            record(RECORDER);
            assertEquals(session, source.recorderSession(RECORDER));
            assertEquals(start, source.recorderStart(RECORDER));
        }
        assertEquals(0L, source.recorderSession(null));
        assertEquals(0L, source.recorderSession("com.example.other"));
    }

    @Test
    public void unknownAndNullModelsInvalidateContinuityWithoutAdvancingStart() {
        record(RECORDER);
        long session = source.recorderSession(RECORDER);
        long start = source.recorderStart(RECORDER);
        Object[] unknown = {
                null,
                new Bundle(null, null),
                new Bundle(new Object(), new Object()),
                new Bundle(new Inactive(), null),
                new Bundle(null, new Inactive()),
                new Bundle(new Inactive(), new Object()),
                new Bundle(new Active(null), new Inactive())
        };
        for (Object model : unknown) {
            source.onBundle(model);
            assertEquals(0L, source.recorderSession(RECORDER));
            assertEquals(0L, source.recorderStart(RECORDER));
            record(RECORDER);
            assertTrue(source.recorderSession(RECORDER) > session);
            assertEquals(start, source.recorderStart(RECORDER));
            session = source.recorderSession(RECORDER);
        }
    }

    @Test
    public void explicitInactiveBothSlotsAdvancesStartWhenSameOwnerReturns() {
        record(RECORDER);
        long session = source.recorderSession(RECORDER);
        long start = source.recorderStart(RECORDER);
        source.onBundle(new Bundle(new Inactive(), new Inactive()));
        assertEquals(0L, source.recorderSession(RECORDER));
        assertEquals(0L, source.recorderStart(RECORDER));
        source.onBundle(null); // A later read failure must not erase the confirmed stop.
        record(RECORDER);
        assertTrue(source.recorderSession(RECORDER) > session);
        assertEquals(start + 1L, source.recorderStart(RECORDER));
    }

    @Test
    public void differentRecorderOwnerAdvancesStartAndInvalidatesOldOwner() {
        record(RECORDER);
        long session = source.recorderSession(RECORDER);
        long start = source.recorderStart(RECORDER);
        record(SAMSUNG);
        assertEquals(0L, source.recorderSession(RECORDER));
        assertEquals(0L, source.recorderStart(RECORDER));
        assertTrue(source.recorderSession(SAMSUNG) > session);
        assertEquals(start + 1L, source.recorderStart(SAMSUNG));
    }

    @Test
    public void explicitOtherShareOwnerEndsThePreviousRecorderGeneration() {
        record(RECORDER);
        long start = source.recorderStart(RECORDER);
        source.onBundle(new Bundle(new Inactive(), new Active("com.example.share")));
        assertEquals(0L, source.recorderSession(RECORDER));
        record(RECORDER);
        assertEquals(start + 1L, source.recorderStart(RECORDER));
    }

    @Test
    public void unrelatedAndLookalikeShareOwnersAreNotSamsungRecording() {
        for (String pkg : new String[] {
                "com.example.share", "com.samsung.android.app.notes",
                SAMSUNG + ".unrelated", "unrelated." + SAMSUNG, null
        }) {
            source.onBundle(new Bundle(new Inactive(), new Active(SAMSUNG)));
            source.onBundle(new Bundle(new Inactive(), new Active(pkg)));
            assertEquals(0L, source.recorderSession(SAMSUNG));
            assertEquals(0L, source.recorderStart(SAMSUNG));
            assertEquals(0L, source.recorderSession(pkg));
            assertEquals(0L, source.recorderStart(pkg));
        }
    }

    @Test
    public void samsungSmartcaptureActiveShareIsRecognizedAndStable() {
        source.onBundle(new Bundle(new Inactive(), new Active(SAMSUNG)));
        long session = source.recorderSession(SAMSUNG);
        long start = source.recorderStart(SAMSUNG);
        assertTrue(session > 0L);
        assertTrue(start > 0L);
        source.onBundle(new Bundle(new Inactive(), new Active(SAMSUNG)));
        assertEquals(session, source.recorderSession(SAMSUNG));
        assertEquals(start, source.recorderStart(SAMSUNG));
    }
}
