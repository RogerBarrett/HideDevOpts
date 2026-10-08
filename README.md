HideDevOpts

An "LSPosed" (https://github.com/LSPosed/LSPosed) module that hides Developer Options from specified apps.

When a selected app reads Developer Options-related switches ("development_settings_enabled", "adb_enabled", "adb_wifi_enabled", etc.), this module returns spoofed values indicating that they are "disabled/not enabled", making the app believe that Developer Options are not enabled on the device. Other apps that are not selected are completely unaffected.

✨ Features

- Precise targeting: Only takes effect for apps you select in the app. All other apps remain completely unaffected.
- No injection into target apps: Only intercepts "SettingsProvider" inside the "system_server" process and does not hook the target apps themselves, helping avoid anti-hook detection.
- Graphical configuration interface: App list with icons, names, and package names; checkboxes are saved immediately; real-time search; option to show/hide system apps.
- Persistent configuration: Uses libxposed Remote Preferences for cross-process synchronization. Settings are preserved across reboots.

How It Works

Developer Options switches are stored in system settings ("Settings.Global" / "Settings.Secure"). When an app reads these values, the request ultimately crosses the process boundary to "SettingsProvider".

This module injects into the "system_server" process and hooks the "query" / "call" methods of "SettingsProvider":

1. Identifies the caller making the request using "Binder.getCallingUid()" and resolves the corresponding package name;
2. If the caller is a selected target app and the requested key is related to Developer Options;
3. Returns a spoofed value ("development_settings_enabled=0", "adb_enabled=0", "adb_port=-1", "adb_wifi_enabled=0", etc.).

The intercepted keys:

Key| Spoofed Value| Meaning
"development_settings_enabled"| "0"| Developer Options master switch
"adb_enabled"| "0"| USB debugging
"adb_port"| "-1"| Wireless debugging port
"adb_wifi_enabled"| "0"| Wireless debugging (Android 11+)

📥 Installation & Usage

1. Install LSPosed (requires a root solution such as Magisk/KernelSU, with Zygisk/LSPosed enabled).
2. Install the module APK and enable the module in LSPosed Manager.
3. Make sure the LSPosed scope includes 「System Framework / system」.
4. Open the HideDevOpts app and select the apps for which you want to hide Developer Options.
5. Reboot your phone (the module is injected into "system_server", so a reboot is required for changes to take effect).

«Tip: If the target app has already read the Developer Options status before being selected, it may temporarily continue using its cached value. Cold-start the target app to apply the change.»

Build

This project is built in the cloud using GitHub Actions (no local Android environment required).

Dependency versions:

- libxposed "api" / "service": "102.0.0"
- compileSdk "37", minSdk "26", targetSdk "34"
- AGP "8.13.0", Gradle "8.13"

Local build:

gradle assembleRelease

The output is located at:

"app/build/outputs/apk/release/"

📄 Acknowledgements

- "libxposed" (https://github.com/libxposed/api) — Modern Xposed API
- "LSPosed" (https://github.com/LSPosed/LSPosed) — Xposed framework
- "IAmNotADeveloper" (https://github.com/xfqwdsj/IAmNotADeveloper) — Reference implementation for a similar project
