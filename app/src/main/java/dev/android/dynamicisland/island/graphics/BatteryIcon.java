package dev.android.dynamicisland.island.graphics;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

/** Only battery events use the bundled pill vectors. All other pictograms keep their Lottie renderer. */
public final class BatteryIcon {
    private static Resources resources;

    private BatteryIcon() { }

    public static Drawable load(Context host, int percent, boolean charging) {
        int level = Math.max(0, Math.min(100, percent));
        try {
            if (resources == null) resources = host.createPackageContext("dev.android.dynamicisland", 0).getResources();
            int id = resources.getIdentifier("ic_battery_pill_" + level, "drawable", "dev.android.dynamicisland");
            if (id != 0) {
                dev.android.dynamicisland.xposed.Diagnostics.fact("battery icon source", "module vector");
                return resources.getDrawable(id, null).mutate();
            }
        } catch (Exception ignored) {
            // A host unable to read module resources keeps the existing functional battery pictogram.
        }
        dev.android.dynamicisland.xposed.Diagnostics.fact("battery icon source", "lottie fallback");
        return GlyphIcon.battery(level / 100f, charging);
    }
}
