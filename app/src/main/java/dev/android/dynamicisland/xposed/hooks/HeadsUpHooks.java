package dev.android.dynamicisland.xposed.hooks;

import android.service.notification.StatusBarNotification;
import de.robv.android.xposed.XC_MethodHook;
import dev.android.dynamicisland.island.IslandController;
import dev.android.dynamicisland.xposed.Refl;
import dev.android.dynamicisland.xposed.SafeHook;
import dev.android.dynamicisland.xposed.XLog;

/**
 * Takes over the stock heads-up banner. {@code HeadsUpManagerImpl.shouldHeadsUpBecomePinned} is the
 * single place that decides whether a heads-up notification is actually shown on top of the screen
 * (it is the same switch System UI flips when the shade is open); answering "not pinned" keeps the
 * notification in the shade and keeps its sound, but no banner appears, because the island shows it.
 * The island only claims notifications it is ready to display right now, everything else stays stock.
 */
public final class HeadsUpHooks {
    private static final String MANAGER = "com.android.systemui.statusbar.notification.headsup.HeadsUpManagerImpl|com.android.systemui.statusbar.phone.HeadsUpManagerPhone";

    /** The heads-up manager, kept so that a banner that already came up can be taken away again. */
    private static volatile Object manager;

    private HeadsUpHooks() {
    }

    static void install(ClassLoader cl) {
        Refl.hookAll(cl, MANAGER, "shouldHeadsUpBecomePinned", SafeHook.after("heads-up to island", HeadsUpHooks::afterPinDecision));
    }

    /** Removes the stock banner of {@code key} when it is up: the island shows the notification now. */
    public static void dismissStock(String key) {
        Object heads = manager;
        if (heads == null || key == null) {
            return;
        }
        try {
            if (Refl.method(heads.getClass(), "removeNotification", 2) != null) {
                Refl.call(heads, "removeNotification", key, Boolean.TRUE);
            } else if (Refl.method(heads.getClass(), "removeNotification", 3) != null) {
                Refl.call(heads, "removeNotification", key, Boolean.TRUE, "dynamic island");
            }
        } catch (Throwable t) {
            XLog.once("HeadsUpHooks.dismissStock", t);
        }
    }

    private static void afterPinDecision(XC_MethodHook.MethodHookParam p) {
        manager = p.thisObject;
        if (p.hasThrowable() || !Boolean.TRUE.equals(p.getResult()) || p.args.length < 1 || p.args[0] == null) {
            return;
        }
        Object sbn = Refl.get(p.args[0], "mSbn");
        IslandController controller = IslandController.get();
        if (controller != null && sbn instanceof StatusBarNotification && controller.peekHeadsUp((StatusBarNotification) sbn)) {
            p.setResult(false);
        }
    }
}
