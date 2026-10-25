package dev.android.dynamicisland.island;

import static org.junit.Assert.assertEquals;

import java.util.Locale;
import org.junit.Test;

/** Locale selection only: no notification classification or Android resources are involved. */
public class IslandTextTest {
    @Test
    public void selectsKoreanEnglishAndTurkishIncludingRegionalVariants() {
        assertEquals("한국어", text("ko-KR"));
        assertEquals("한국어", text("ko"));
        assertEquals("English", text("en-US"));
        assertEquals("Türkçe", text("tr-TR"));
        assertEquals("Türkçe", text("tr-CY"));
        assertEquals("Türkçe", text("tr"));
    }

    @Test
    public void unsupportedAndNullLocalesKeepEnglishFallback() {
        for (String tag : new String[] {"ru-RU", "ja-JP", "zh-CN", "de-DE", "und"}) {
            assertEquals("English", text(tag));
        }
        assertEquals("English", IslandText.select((Locale) null, "한국어", "English", "Türkçe"));
    }

    @Test
    public void optionalRussianPreservesExistingCastTranslation() {
        assertEquals("Запись экрана", cast("ru-RU"));
        assertEquals("Ekran kaydı", cast("tr-TR"));
        assertEquals("Screen recording", cast("ko-KR"));
        assertEquals("Screen recording", cast("fr-FR"));
        assertEquals("Screen recording", IslandText.select((Locale) null,
                "Screen recording", "Screen recording", "Ekran kaydı", "Запись экрана"));
    }

    @Test
    public void explicitLocaleDoesNotDependOnTheJvmDefault() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("English", text("en-US"));
            assertEquals("한국어", text("ko-KR"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    private static String text(String tag) {
        return IslandText.select(Locale.forLanguageTag(tag), "한국어", "English", "Türkçe");
    }

    private static String cast(String tag) {
        return IslandText.select(Locale.forLanguageTag(tag),
                "Screen recording", "Screen recording", "Ekran kaydı", "Запись экрана");
    }
}
