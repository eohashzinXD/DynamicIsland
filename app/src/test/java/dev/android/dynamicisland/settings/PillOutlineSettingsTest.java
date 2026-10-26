package dev.android.dynamicisland.settings;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.LocaleList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import dev.android.dynamicisland.R;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
public class PillOutlineSettingsTest {
    private SharedPreferences store() {
        SharedPreferences prefs = ModuleSettings.openStore(RuntimeEnvironment.getApplication());
        prefs.edit().clear().commit();
        return prefs;
    }

    @Test public void absentPreferenceDefaultsToAutoWithoutWritingAValue() {
        SharedPreferences prefs = store();
        assertEquals("pill_outline", ModuleSettings.KEY_PILL_OUTLINE);
        assertEquals(0, ModuleSettings.PILL_OUTLINE_AUTO);
        assertEquals(1, ModuleSettings.PILL_OUTLINE_ALWAYS);
        assertEquals(2, ModuleSettings.PILL_OUTLINE_NONE);
        assertEquals(ModuleSettings.PILL_OUTLINE_AUTO, ModuleSettings.readPillOutlineMode(prefs));
        ModuleSettings.configurePreview(prefs);
        assertEquals(ModuleSettings.PILL_OUTLINE_AUTO, ModuleSettings.pillOutlineMode());
        assertFalse(prefs.contains(ModuleSettings.KEY_PILL_OUTLINE));
    }

    @Test public void allThreeModesPersistAndReachPreviewAndProviderReads() {
        SharedPreferences prefs = store();
        for (int mode = 0; mode <= 2; mode++) {
            assertTrue(prefs.edit().putInt(ModuleSettings.KEY_PILL_OUTLINE, mode).commit());
            SharedPreferences reopened = ModuleSettings.openStore(RuntimeEnvironment.getApplication());
            assertEquals(mode, reopened.getInt(ModuleSettings.KEY_PILL_OUTLINE, -1));
            assertEquals(mode, ModuleSettings.readPillOutlineMode(reopened));
            assertEquals(mode, ModuleSettings.readPillOutlineMode(new MapPrefs(new HashMap<>(reopened.getAll()))));
            ModuleSettings.configurePreview(reopened);
            assertEquals(mode, ModuleSettings.pillOutlineMode());
        }
    }

    @Test public void corruptRangeOrTypeReturnsAutoRatherThanClampingOrKeepingOldMode() {
        SharedPreferences prefs = store();
        for (int invalid : new int[]{Integer.MIN_VALUE, -1, 3, Integer.MAX_VALUE}) {
            prefs.edit().putInt(ModuleSettings.KEY_PILL_OUTLINE, invalid).commit();
            assertEquals(ModuleSettings.PILL_OUTLINE_AUTO, ModuleSettings.readPillOutlineMode(prefs));
            assertEquals(ModuleSettings.PILL_OUTLINE_AUTO,
                    ModuleSettings.readPillOutlineMode(new MapPrefs(new HashMap<>(prefs.getAll()))));
            ModuleSettings.configurePreview(prefs);
            assertEquals(ModuleSettings.PILL_OUTLINE_AUTO, ModuleSettings.pillOutlineMode());
        }
        for (Object invalid : new Object[]{"1", true, 1L, 1f, new HashSet<>(Arrays.asList("1"))}) {
            prefs.edit().putInt(ModuleSettings.KEY_PILL_OUTLINE, ModuleSettings.PILL_OUTLINE_ALWAYS).commit();
            ModuleSettings.configurePreview(prefs);
            assertEquals(ModuleSettings.PILL_OUTLINE_ALWAYS, ModuleSettings.pillOutlineMode());
            SharedPreferences.Editor editor = prefs.edit();
            if (invalid instanceof String) editor.putString(ModuleSettings.KEY_PILL_OUTLINE, (String) invalid);
            else if (invalid instanceof Boolean) editor.putBoolean(ModuleSettings.KEY_PILL_OUTLINE, (Boolean) invalid);
            else if (invalid instanceof Long) editor.putLong(ModuleSettings.KEY_PILL_OUTLINE, (Long) invalid);
            else if (invalid instanceof Float) editor.putFloat(ModuleSettings.KEY_PILL_OUTLINE, (Float) invalid);
            else editor.putStringSet(ModuleSettings.KEY_PILL_OUTLINE, new HashSet<>(Arrays.asList("1")));
            editor.commit();
            assertEquals(ModuleSettings.PILL_OUTLINE_AUTO, ModuleSettings.readPillOutlineMode(prefs));
            Map<String, Object> remote = new HashMap<>();
            remote.put(ModuleSettings.KEY_PILL_OUTLINE, invalid);
            assertEquals(ModuleSettings.PILL_OUTLINE_AUTO, ModuleSettings.readPillOutlineMode(new MapPrefs(remote)));
            ModuleSettings.configurePreview(prefs);
            assertEquals(ModuleSettings.PILL_OUTLINE_AUTO, ModuleSettings.pillOutlineMode());
        }
    }

    @Test public void outlineAloneChangesLiveSwitchFingerprintInBothAnimationStyles() {
        SharedPreferences prefs = store();
        for (boolean legacy : new boolean[]{false, true}) {
            prefs.edit().putBoolean(ModuleSettings.KEY_LEGACY_ANIMATION, legacy).commit();
            HashSet<String> signatures = new HashSet<>();
            for (int mode = 0; mode <= 2; mode++) {
                prefs.edit().putInt(ModuleSettings.KEY_PILL_OUTLINE, mode).commit();
                ModuleSettings.configurePreview(prefs);
                String signature = ReflectionHelpers.callStaticMethod(ModuleSettings.class, "switchSignature");
                assertTrue("each outline mode must invalidate live settings", signatures.add(signature));
                ModuleSettings.configurePreview(prefs);
                assertEquals(signature, ReflectionHelpers.callStaticMethod(ModuleSettings.class, "switchSignature"));
                assertEquals(legacy, ModuleSettings.legacyAnimation());
            }
        }
    }

    private View find(View view, String label) {
        if (view instanceof TextView && label.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private AlertDialog outlineDialog(SettingsActivity activity) {
        View title = find(activity.page(), activity.getString(R.string.pill_outline));
        assertNotNull("outline belongs on the display page, not the accent editor", title);
        ((View) title.getParent().getParent()).performClick();
        return ShadowAlertDialog.getLatestAlertDialog();
    }

    private void openDisplay(SettingsActivity activity) {
        activity.open(() -> ReflectionHelpers.callInstanceMethod(activity, "displayPage"));
    }

    @Test public void displayChoicePersistsEveryModeAndSurvivesPageReopening() {
        SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class).setup().get();
        SharedPreferences prefs = ReflectionHelpers.getField(activity, "prefs");
        prefs.edit().clear().commit();
        openDisplay(activity);
        assertNotNull(find(activity.page(), activity.getString(R.string.pos_title)));
        assertNotNull(find(activity.page(), activity.getString(R.string.pill_outline_desc)));
        assertEquals(0, outlineDialog(activity).getListView().getCheckedItemPosition());
        ShadowAlertDialog.getLatestAlertDialog().dismiss();
        int[] labels = {R.string.pill_outline_auto, R.string.pill_outline_always, R.string.pill_outline_none};
        for (int mode : new int[]{1, 2, 0}) {
            AlertDialog dialog = outlineDialog(activity);
            assertEquals(3, dialog.getListView().getAdapter().getCount());
            for (int i = 0; i < labels.length; i++) {
                assertEquals(activity.getString(labels[i]), dialog.getListView().getAdapter().getItem(i));
            }
            dialog.getListView().performItemClick(null, mode, mode);
            assertEquals(mode, prefs.getInt(ModuleSettings.KEY_PILL_OUTLINE, -1));
            ModuleSettings.configurePreview(prefs);
            assertEquals(mode, ModuleSettings.pillOutlineMode());
            activity.goBack();
            openDisplay(activity);
            assertNotNull(find(activity.page(), activity.getString(labels[mode])));
            assertEquals(mode, outlineDialog(activity).getListView().getCheckedItemPosition());
            ShadowAlertDialog.getLatestAlertDialog().dismiss();
        }
    }

    @Test public void invalidUiPreferenceShowsAutoAndCancellingDoesNotOverwriteIt() {
        SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class).setup().get();
        SharedPreferences prefs = ReflectionHelpers.getField(activity, "prefs");
        for (int invalid : new int[]{-1, 3, Integer.MAX_VALUE}) {
            prefs.edit().putInt(ModuleSettings.KEY_PILL_OUTLINE, invalid).commit();
            openDisplay(activity);
            AlertDialog dialog = outlineDialog(activity);
            assertEquals(0, dialog.getListView().getCheckedItemPosition());
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            assertEquals(invalid, prefs.getInt(ModuleSettings.KEY_PILL_OUTLINE, 0));
            activity.goBack();
        }
        prefs.edit().putString(ModuleSettings.KEY_PILL_OUTLINE, "corrupt").commit();
        openDisplay(activity);
        AlertDialog dialog = outlineDialog(activity);
        assertEquals(0, dialog.getListView().getCheckedItemPosition());
        dialog.getListView().performItemClick(null, 1, 1);
        assertEquals(ModuleSettings.PILL_OUTLINE_ALWAYS, prefs.getInt(ModuleSettings.KEY_PILL_OUTLINE, -1));
    }

    @Test @Config(qualifiers = "ko-rKR-w360dp-h800dp-xxhdpi")
    public void koreanDisplayUsesRequestedLabels() {
        SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class).setup().get();
        openDisplay(activity);
        AlertDialog dialog = outlineDialog(activity);
        for (int i = 0; i < 3; i++) {
            assertEquals(new String[]{"자동", "항상", "없음"}[i], dialog.getListView().getAdapter().getItem(i));
        }
        assertEquals("자동은 알약과 실제 배경의 대비가 낮을 때만 외곽선을 표시합니다.",
                activity.getString(R.string.pill_outline_desc));
        dialog.dismiss();
    }

    @Test public void everySupportedLocaleHasNativeOutlineLabels() {
        Context app = RuntimeEnvironment.getApplication();
        String[][] expected = {
                {"en", "Auto", "Always", "None"}, {"ko", "자동", "항상", "없음"},
                {"ja", "自動", "常に表示", "なし"}, {"ru", "Авто", "Всегда", "Нет"},
                {"tr", "Otomatik", "Her zaman", "Yok"}, {"zh", "自动", "始终", "无"}
        };
        for (String[] row : expected) {
            Configuration configuration = new Configuration(app.getResources().getConfiguration());
            configuration.setLocales(new LocaleList(Locale.forLanguageTag(row[0])));
            Context localized = app.createConfigurationContext(configuration);
            assertEquals(row[1], localized.getString(R.string.pill_outline_auto));
            assertEquals(row[2], localized.getString(R.string.pill_outline_always));
            assertEquals(row[3], localized.getString(R.string.pill_outline_none));
            assertFalse(localized.getString(R.string.pill_outline).isBlank());
            assertFalse(localized.getString(R.string.pill_outline_desc).isBlank());
        }
    }
}
