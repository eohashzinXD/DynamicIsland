package dev.android.dynamicisland.settings;

import static org.junit.Assert.*;
import android.content.SharedPreferences;
import dev.android.dynamicisland.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=35,qualifiers="ko-rKR-w411dp-h891dp-xxhdpi")
public class AmbientGlowSettingsTest {
    @Test public void defaultsAreOptInAndLowPerformancePreservesTheChosenGlow() throws Exception {
        SettingsActivity activity=Robolectric.buildActivity(SettingsActivity.class).setup().get();
        SharedPreferences prefs=ReflectionHelpers.getField(activity,"prefs");ModuleSettings.configurePreview(prefs);assertFalse(ModuleSettings.ambientGlow());
        prefs.edit().putBoolean(ModuleSettings.KEY_AMBIENT_GLOW,true).putInt(ModuleSettings.KEY_GLOW_STRENGTH,45).apply();ModuleSettings.configurePreview(prefs);
        assertTrue(ModuleSettings.ambientGlow());assertTrue(ModuleSettings.glowAlbum());assertEquals(45,ModuleSettings.glowStrength());
        OneUi ui=ReflectionHelpers.getField(activity,"ui");activity.open(()->new AmbientGlowScreen(activity,ui,prefs).open());
        SettingsRenderTest.render(activity,"settings_ko_ambientGlow");
        prefs.edit().putBoolean(ModuleSettings.KEY_LOW_PERFORMANCE,true).apply();ModuleSettings.configurePreview(prefs);assertFalse(ModuleSettings.ambientGlow());
        assertTrue(prefs.getBoolean(ModuleSettings.KEY_AMBIENT_GLOW,false));
        prefs.edit().putBoolean(ModuleSettings.KEY_LOW_PERFORMANCE,false).apply();ModuleSettings.configurePreview(prefs);assertTrue(ModuleSettings.ambientGlow());
        prefs.edit().clear().apply();ModuleSettings.configurePreview(prefs);
    }
    @Test public void colourModesFollowThemeChangesAndRestoreTheSeparateColour() throws Exception {
        SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class).setup().get();
        SharedPreferences prefs = ReflectionHelpers.getField(activity, "prefs");
        OneUi ui = ReflectionHelpers.getField(activity, "ui");
        try {
            prefs.edit().putBoolean(ModuleSettings.KEY_AMBIENT_GLOW, true)
                    .putInt(ModuleSettings.KEY_GLOW_COLOUR, 0xFFE0A97D)
                    .putBoolean(ModuleSettings.KEY_ACCENT_CUSTOM, true)
                    .putInt(ModuleSettings.KEY_ACCENT_COLOR, 0xFF75B6FF).apply();
            activity.open(() -> new AmbientGlowScreen(activity, ui, prefs).open());
            ModuleSettings.configurePreview(prefs);
            assertEquals(0xFFE0A97D, ModuleSettings.glowColour());
            choose(activity, 0);
            assertTrue(prefs.getBoolean(ModuleSettings.KEY_GLOW_FOLLOW_THEME, false));
            ModuleSettings.configurePreview(prefs);
            assertEquals(0xFF75B6FF, ModuleSettings.glowColour());
            assertNull(find(activity.page(), activity.getString(R.string.accent_custom)));
            assertNotNull(find(activity.page(), activity.getString(R.string.glow_theme_colour)));
            SettingsRenderTest.render(activity, "settings_ko_ambientGlowTheme");
            prefs.edit().putInt(ModuleSettings.KEY_ACCENT_COLOR, 0xFFB8A1FF).apply();
            ModuleSettings.configurePreview(prefs);
            assertEquals(0xFFB8A1FF, ModuleSettings.glowColour());
            prefs.edit().putBoolean(ModuleSettings.KEY_ACCENT_CUSTOM, false).apply();
            ModuleSettings.configurePreview(prefs);
            assertEquals(ModuleSettings.DEFAULT_ACCENT, ModuleSettings.glowColour());
            choose(activity, 1);
            ModuleSettings.configurePreview(prefs);
            assertEquals(0xFFE0A97D, ModuleSettings.glowColour());
            assertNotNull(find(activity.page(), activity.getString(R.string.accent_custom)));
            assertTrue(ModuleSettings.glowAlbum());
            assertEquals(0xFFE0A97D, prefs.getInt(ModuleSettings.KEY_GLOW_COLOUR, 0));
            SettingsRenderTest.render(activity, "settings_ko_ambientGlowIndividual");
        } finally { prefs.edit().clear().apply(); ModuleSettings.configurePreview(prefs); }
    }

    private android.view.View find(android.view.View view, String text) {
        if (view instanceof android.widget.TextView && text.contentEquals(((android.widget.TextView) view).getText())) return view;
        if (view instanceof android.view.ViewGroup) for (int i = 0; i < ((android.view.ViewGroup) view).getChildCount(); i++) {
            android.view.View found = find(((android.view.ViewGroup) view).getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }
    private void choose(SettingsActivity activity, int mode) {
        android.view.View label = find(activity.page(), activity.getString(R.string.glow_colour_source));
        ((android.view.View) label.getParent().getParent()).performClick();
        org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null, mode, mode);
    }

}
