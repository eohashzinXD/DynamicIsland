package dev.android.dynamicisland.xposed.hooks;

import android.app.Notification;
import android.service.notification.StatusBarNotification;
import de.robv.android.xposed.XC_MethodHook;
import dev.android.dynamicisland.xposed.Refl;
import dev.android.dynamicisland.xposed.SafeHook;
import dev.android.dynamicisland.xposed.XLog;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the Google app's notifications out of notification groups. Folded into a group such as "Sports from Google"
 * the cards are drawn blank while the group opens and closes, and the island shows the same cards twice (the group and
 * its members). Each notification is posted on its own instead, and the group's summary, which only carries the
 * heading, is dropped. No attempt is made to tell sports from the rest: the app's channel names are not known, and a
 * guess that misses would silently leave the cards grouped. The decision is taken where System UI receives a posted
 * notification, before it is grouped.
 */
final class UngroupGoogleSports {
    static final String GOOGLE_APP = "com.google.android.googlequicksearchbox";
    private static final String COALESCER = "com.android.systemui.statusbar.notification.collection.coalescer.GroupCoalescer$1";

    /** Groups already written to the diagnostics, so that every one is listed once. */
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    private UngroupGoogleSports() {
    }

    static void install(ClassLoader cl) {
        Refl.hookAll(cl, COALESCER, "onNotificationPosted", SafeHook.before("ungroup google sports", UngroupGoogleSports::onPosted));
    }

    private static void onPosted(XC_MethodHook.MethodHookParam param) {
        Object arg = param.args.length > 0 ? param.args[0] : null;
        if (!(arg instanceof StatusBarNotification)) {
            return;
        }
        StatusBarNotification sbn = (StatusBarNotification) arg;
        if (!GOOGLE_APP.equals(sbn.getPackageName())) {
            return;
        }
        Notification n = sbn.getNotification();
        String group = n.getGroup();
        if (group == null) {
            return;
        }
        boolean summary = (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
        if (LOGGED.add(group + "|" + summary)) {
            XLog.event("google notification group=" + group + " channel=" + n.getChannelId() + " category=" + n.category
                    + " summary=" + summary + " -> ungrouped");
        }
        if (summary) {
            param.setResult(null);
        } else {
            Refl.call(sbn, "setOverrideGroupKey", sbn.getKey());
        }
    }
}
