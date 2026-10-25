package dev.android.dynamicisland.island;

import android.content.Context;
import java.util.Locale;

/** Module-owned text drawn inside System UI, where the module's string resources are unavailable. */
public final class IslandText {
    private IslandText() { }

    /** Follow the host context's configuration, including a locale change without a process restart. */
    public static String select(Context context, String ko, String en, String tr) {
        return select(context.getResources().getConfiguration().getLocales().get(0), ko, en, tr);
    }

    /** Explicit translations at each call site; unsupported languages keep the English fallback. */
    public static String select(Locale locale, String ko, String en, String tr) {
        String language = locale == null ? "" : locale.getLanguage();
        switch (language) {
            case "ko":
                return ko;
            case "tr":
                return tr;
            default:
                return en;
        }
    }

    /** Preserve existing Russian translations at call sites that already supplied them. */
    public static String select(Locale locale, String ko, String en, String tr, String ru) {
        return locale != null && "ru".equals(locale.getLanguage()) ? ru : select(locale, ko, en, tr);
    }
}
