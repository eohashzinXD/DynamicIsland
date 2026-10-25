package dev.android.dynamicisland.xposed.hooks;

import static org.junit.Assert.*;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import com.android.systemui.statusbar.phone.ongoingactivity.OngoingActivityDataHelper;
import dev.android.dynamicisland.island.IslandController;
import dev.android.dynamicisland.island.model.IslandItem;
import dev.android.dynamicisland.island.source.OaCardIconTest;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class ShadeCardRestoreTest {
    public static class Layout extends FrameLayout {
        public View mContractedChild, mExpandedChild, mHeadsUpChild;
        public int mSmallHeight = 50, mMinContractedHeight = 50, mHeadsUpHeight = 70;
        public boolean mExpandable = true;
        Layout(Context context) { super(context); setContractedChild(new View(context)); }
        public void setContractedChild(View view) {
            if (mContractedChild != null) removeView(mContractedChild);
            mContractedChild = view;
            if (view != null && view.getParent() == null) addView(view);
        }
        public void setExpandedChild(View view) { mExpandedChild = view; }
        public void setHeadsUpChild(View view) { mHeadsUpChild = view; }
        public void updateExpandButtons(boolean enabled) { }
        public void selectLayout(boolean animate, boolean force) { }
    }
    public static class Row extends FrameLayout {
        public boolean mExpandable = true;
        Row(Context context) { super(context); }
        public void notifyHeightChanged(String reason, boolean animate) { }
    }
    @After public void clear() {
        OngoingActivityDataHelper.mOngoingActivityLists.clear();
        ReflectionHelpers.setStaticField(IslandController.class, "instance", null);
    }
    @Test public void restoringDuringARebindCannotReinstallTheDuplicateAfterwards() throws Throwable {
        Context app = RuntimeEnvironment.getApplication();
        OngoingActivityDataHelper.mOngoingActivityLists.add(new OaCardIconTest.ActivityData());
        IslandController controller = IslandController.init(app, getClass().getClassLoader());
        IslandItem item = controller.itemForKey("map");
        assertNotNull(item);
        Layout layout = new Layout(app);
        View original = layout.mContractedChild;
        CardRowState state = new CardRowState(new Row(app), layout, "map", item);
        state.install();
        assertSame(state, CardRowState.installedIn(layout));
        state.exposeOriginals();
        assertSame(state, CardRowState.exposedIn(layout));
        state.restore();
        assertNull(CardRowState.exposedIn(layout));
        assertSame(original, layout.mContractedChild);
        state.concealOriginals();
        assertSame(original, layout.mContractedChild);
        assertNull(CardRowState.installedIn(layout));
    }
}
