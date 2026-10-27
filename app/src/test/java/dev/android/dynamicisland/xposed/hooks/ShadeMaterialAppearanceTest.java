package dev.android.dynamicisland.xposed.hooks;

import static org.junit.Assert.*;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class ShadeMaterialAppearanceTest {
    @Test public void hidesOnlyOwnedShellAndRestoresItAfterLastClient() {
        Context context=RuntimeEnvironment.getApplication();
        ExpandableNotificationRow row=new ExpandableNotificationRow(context);row.setClipToOutline(true);
        View first=new View(context),second=new View(context);row.addView(first);row.addView(second);
        row.mBackgroundNormal.setAlpha(.7f);row.mBackgroundDimmed.setAlpha(.4f);
        ShadeMaterialAppearance a=new ShadeMaterialAppearance(),b=new ShadeMaterialAppearance();
        a.sync(first,true);b.sync(second,true);
        assertEquals(0,row.mBackgroundNormal.getAlpha(),0);assertFalse(row.getClipToOutline());
        a.clear();assertEquals(0,row.mBackgroundNormal.getAlpha(),0);
        b.clear();assertEquals(.7f,row.mBackgroundNormal.getAlpha(),0);assertEquals(.4f,row.mBackgroundDimmed.getAlpha(),0);
        assertTrue(row.getClipToOutline());
    }
    static final class ExpandableNotificationRow extends FrameLayout {
        final View mBackgroundNormal,mBackgroundDimmed;
        ExpandableNotificationRow(Context c){super(c);mBackgroundNormal=new View(c);mBackgroundDimmed=new View(c);}
    }
}
