package dev.android.dynamicisland.settings;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import dev.android.dynamicisland.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Check Android resource selection and formatting, not only the raw XML translation. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "tr-rTR")
public class TurkishResourcesTest {
    @Test
    public void selectsTurkishSettingsAndFormatsArguments() {
        Context context = RuntimeEnvironment.getApplication();
        assertEquals("tr", context.getResources().getConfiguration().getLocales().get(0).getLanguage());
        assertEquals("Ayarlar", context.getString(R.string.hub_settings));
        assertEquals("Kaydet", context.getString(R.string.save));
        assertEquals("Geri", context.getString(R.string.back));
        assertEquals("3 uygulama seçildi", context.getString(R.string.apps_status, 3));
        assertEquals("İzin verilenler listesi · 3 uygulama", context.getString(
                R.string.apps_summary_format, context.getString(R.string.apps_whitelist_short), 3));
        assertEquals("Dynamic Island", context.getString(R.string.app_name));
    }
}
