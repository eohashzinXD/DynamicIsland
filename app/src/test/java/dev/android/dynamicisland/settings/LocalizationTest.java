package dev.android.dynamicisland.settings;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import dev.android.dynamicisland.R;
import dev.android.dynamicisland.island.IslandText;
import dev.android.dynamicisland.island.rules.RuleConfig;
import dev.android.dynamicisland.island.rules.RuleStore;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi")
public class LocalizationTest {
    @Test public void resourcesAndSystemUiShareTranslationsAndFormatArgumentsAcrossAllLocales() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        for (String language : IslandText.languages()) {
            Configuration configuration = new Configuration(app.getResources().getConfiguration());
            configuration.setLocales(new LocaleList(Locale.forLanguageTag(language)));
            Context localized = app.createConfigurationContext(configuration);
            for (java.lang.reflect.Field field : R.string.class.getFields()) {
                String key = field.getName();
                if (key.startsWith("island_") || key.startsWith("stage_label_")) {
                    assertEquals(language + "/" + key, localized.getString(field.getInt(null)), IslandText.get(localized, key));
                }
            }
            assertEquals(localized.getString(R.string.island_battery_remaining, 54), IslandText.get(localized, "island_battery_remaining", 54));
            assertEquals(localized.getString(R.string.island_more_apps, 3), IslandText.get(localized, "island_more_apps", 3));
            assertEquals(localized.getString(R.string.island_reply_to, "Minji"), IslandText.get(localized, "island_reply_to", "Minji"));
            assertFalse(localized.getString(R.string.experimental_recovery_desc).isBlank());
            assertTrue(localized.getString(R.string.hub_credits_text).contains("@UnkilBonki"));
            assertFalse(localized.getString(R.string.rules_status, "example").contains("%1$s"));
        }
    }
    @Test public void newLanguagesHaveNativeStageLabelsAndPreserveCustomEnglishOverrides() {
        var rules = RuleStore.build(new RuleConfig(null));
        assertEquals("準備中", rules.statusText("food", 1, Locale.JAPANESE));
        assertEquals("准备中", rules.statusText("food", 1, Locale.CHINESE));
        assertEquals("Готовится", rules.statusText("food", 1, Locale.forLanguageTag("ru")));
        var custom = RuleStore.build(new RuleConfig("{\"status_labels\":{\"food\":{\"en\":{\"1\":\"My preparation\"}}}}"));
        for (String language : new String[]{"ja", "zh", "ru", "tr"}) {
            assertEquals("My preparation", custom.statusText("food", 1, Locale.forLanguageTag(language)));
        }
    }
    private void render(String prefix) throws Exception {
        SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class).setup().get();
        SettingsRenderTest.renderAll(activity, prefix);
        AppearanceScreen appearance = ReflectionHelpers.getField(activity, "appearance");
        activity.open(appearance::open);
        SettingsRenderTest.render(activity, prefix + "colours");
    }
    @Test @Config(qualifiers = "ja-rJP-w411dp-h891dp-xxhdpi") public void japaneseSettingsRender() throws Exception { render("settings_ja_"); }
    @Test @Config(qualifiers = "zh-rCN-w411dp-h891dp-xxhdpi") public void chineseSettingsRender() throws Exception { render("settings_zh_"); }
    @Test @Config(qualifiers = "ru-rRU-w411dp-h891dp-xxhdpi") public void russianSettingsRender() throws Exception { render("settings_ru_"); }
}
