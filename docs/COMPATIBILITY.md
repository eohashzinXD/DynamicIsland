# System UI compatibility

The module hooks Samsung's System UI by name, so each One UI version has to be checked. This page says what is checked and how to add a version.

## Status

| One UI | Android | Status |
|---|---|---|
| 9 | 17 (SDK 37) | the code has branches for it; not confirmed on a device |
| 8 | 16 (SDK 36) | the module was written against it; tested on one device only |
| 7 | 15 (SDK 35) | being added: symbols are checked against the System UI files with `scripts/check_systemui.py` |

"Tested" means used on a real phone. Nothing here is tested on more than the author's own device.

## How a build is checked

1. Get the System UI of that One UI version (`/system_ext/priv-app/SystemUI/SystemUI.apk`, or its dex files).
2. Run `python3 scripts/check_systemui.py <SystemUI.apk>`. It lists the classes, methods and fields the module touches that the build does not have, with the source file that uses each one.
3. A missing class means a hook group will not attach on that build. Each group is installed on its own (`SystemUiHooks.install`), so one missing group does not take the others down; the diagnostics report lists them under **hooks not attached**.
4. For each miss, either find the name the build uses instead and hook that as well (the hooks are written so that a missing target is skipped, not fatal), or leave the feature off on that version.
5. The report of **Tools → Diagnostics** shows the One UI version (`one ui`), the System UI build (`system ui`) and the hooks that did not attach. Ask testers on a new version to send it.

Names are matched by name only (any class), so a hit from the script is a hint, not a proof; view ids are not in the dex and are checked with `aapt2 dump resources`, as the script prints.
