package dev.android.dynamicisland.xposed.hooks;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;

public class HeadsUpStatusTest {
    public static class Unknown { }
    public static class Direct {
        Object entry;
        public Object getHeadsUpEntry(String key) { return entry; }
    }
    public static class UnreadableList {
        public List<Object> getAllEntries() { return List.of(new Object()); }
    }
    @Test public void unknownOrUnreadableStateIsNeverClaimedToBeRemoved() {
        assertNull(HeadsUpHooks.status(new Unknown(), "key"));
        assertNull(HeadsUpHooks.status(new UnreadableList(), "key"));
    }
    @Test public void aReadableAccessorDistinguishesPresentFromRemoved() {
        Direct manager = new Direct();
        assertEquals(Boolean.FALSE, HeadsUpHooks.status(manager, "key"));
        manager.entry = new Object();
        assertEquals(Boolean.TRUE, HeadsUpHooks.status(manager, "key"));
    }
}
