package dev.android.dynamicisland.island;

import static org.junit.Assert.*;
import dev.android.dynamicisland.island.model.IslandItem;
import java.util.List;
import org.junit.Test;

public class AutoOpenCardsTest {
    @Test public void optInDoesNotOpenTheBacklogOrReopenProgressUpdates() {
        AutoOpenCards policy = new AutoOpenCards();
        IslandItem old = new IslandItem("task:old"), fresh = new IslandItem("task:new");
        assertNull(policy.next(List.of(old), false, 0));
        assertNull(policy.next(List.of(old), true, 1));
        assertSame(fresh, policy.next(List.of(old, fresh), true, 2));
        policy.opened(fresh);
        fresh.progress = 0.5f;
        assertNull(policy.next(List.of(old, fresh), true, 3));
    }

    @Test public void pendingCardsExpireAndRemovedCardsCanReturnAsNew() {
        AutoOpenCards policy = new AutoOpenCards();
        policy.next(List.of(), true, 0);
        IslandItem item = new IslandItem("rec:new");
        assertSame(item, policy.next(List.of(item), true, 10));
        assertNull(policy.next(List.of(item), true, 9000));
        policy.next(List.of(), true, 10000);
        assertSame(item, policy.next(List.of(item), true, 10001));
    }

    @Test public void eventsMediaAndAlreadyHandledPopupsAreNotAutoOpened() {
        AutoOpenCards policy = new AutoOpenCards();
        policy.next(List.of(), true, 0);
        assertNull(policy.next(List.of(new IslandItem("event:charge"), new IslandItem("media:play"),
                new IslandItem("peek:message")), true, 1));
    }
}
