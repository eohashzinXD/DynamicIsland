package dev.android.dynamicisland.island.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Turkish changes display labels, not notification detection or the other languages. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TurkishLabelsTest {
    @Test
    public void everyBuiltInStageHasATurkishLabel() throws Exception {
        RuleDictionary rules = RuleStore.build(new RuleConfig(null));
        JSONObject turkish;
        try (java.io.InputStream in = RuleStore.class.getResourceAsStream(
                "/dev/android/dynamicisland/rules/builtin-translations.json")) {
            assertNotNull(in);
            turkish = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .getJSONObject("status_labels");
        }
        for (String file : new String[]{"builtin-en", "builtin-ko"}) {
            try (java.io.InputStream in = RuleStore.class.getResourceAsStream(
                    "/dev/android/dynamicisland/rules/" + file + ".json")) {
                assertNotNull(in);
                JSONObject labels = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                        .getJSONObject("status_labels");
                java.util.Iterator<String> ids = labels.keys();
                while (ids.hasNext()) {
                    String id = ids.next();
                    JSONObject languages = labels.getJSONObject(id);
                    JSONObject stages = languages.getJSONObject(languages.keys().next());
                    java.util.Iterator<String> keys = stages.keys();
                    while (keys.hasNext()) {
                        int stage = Integer.parseInt(keys.next());
                        String actual = rules.statusText(id, stage, Locale.forLanguageTag("tr-TR"));
                        String expected = turkish.getJSONObject(id).getJSONObject("tr")
                                .getString(String.valueOf(stage));
                        assertTrue(id + "/" + stage, !expected.trim().isEmpty());
                        assertEquals(expected, actual);
                        assertEquals(actual, rules.statusText(id, stage, Locale.forLanguageTag("tr")));
                    }
                }
            }
        }
        assertEquals("Hazırlanıyor", rules.statusText("food", 1, Locale.forLanguageTag("tr-TR")));
        assertEquals("Dağıtıma çıktı", rules.statusText("parcel", 3, Locale.forLanguageTag("tr")));
        assertEquals("Çalışıyor", rules.statusText("relay", 1, Locale.forLanguageTag("tr")));
        assertEquals("İşleniyor", rules.statusText("assistant", 1, Locale.forLanguageTag("tr")));
    }

    @Test
    public void legacyUserLabelsRemainAboveNewTurkishDefaults() {
        String custom = "{\"status_labels\":{\"food\":{\"en\":{\"1\":\"My preparation\",\"3\":\"At my door\"},"
                + "\"tr\":{\"1\":\"Özel hazırlık\"}}}}";
        RuleDictionary rules = RuleStore.build(new RuleConfig(custom));
        Locale turkish = Locale.forLanguageTag("tr-TR");
        assertEquals("Özel hazırlık", rules.statusText("food", 1, turkish));
        assertEquals("At my door", rules.statusText("food", 3, turkish));
        assertEquals("Teslim edildi", rules.statusText("food", 4, turkish));
        assertEquals("My preparation", rules.statusText("food", 1, Locale.ENGLISH));
        assertEquals("조리 중", rules.statusText("food", 1, Locale.KOREAN));
    }

    @Test
    public void translationsDoNotAddOrChangeDetectionRules() throws Exception {
        RuleDictionary original = RuleDictionary.empty();
        for (String file : new String[]{"builtin-ko", "builtin-en"}) {
            try (java.io.InputStream in = RuleStore.class.getResourceAsStream(
                    "/dev/android/dynamicisland/rules/" + file + ".json")) {
                original = original.mergedWith(RuleDictionary.fromJson(
                        new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            }
        }
        RuleDictionary translated = RuleStore.build(new RuleConfig(null));
        assertEquals(original.smartRules.size(), translated.smartRules.size());
        for (String id : original.statusLabels.keySet()) {
            for (int stage = 0; stage <= 9; stage++) {
                for (Locale locale : new Locale[]{Locale.ENGLISH, Locale.KOREAN, Locale.GERMAN}) {
                    assertEquals(id + "/" + stage + "/" + locale,
                            original.statusText(id, stage, locale), translated.statusText(id, stage, locale));
                }
            }
        }
        assertTrue(translated.rule("food").packageHints.contains("com.mcdonalds"));
    }
}
