HideDevOpts
An LSPosed module that hides Developer Options from specified applications.
When a selected app queries Developer Options flags (development_settings_enabled, adb_enabled, adb_wifi_enabled, etc.), this module returns spoofed values indicating they are "disabled/off," misleading the app into believing Developer Options are turned off on the device. Unselected apps remain completely unaffected.
✨ Features
 * Targeted Scope: Only takes effect on apps selected in the manager app; all other applications remain completely untouched.
 * No Injection into Target Apps: Intercepts SettingsProvider strictly within the system_server (system framework) process. It does not hook the target app itself, bypassing target-side anti-hook detection mechanisms.
 * Graphical Management UI: Application list (icon + app name + package name), auto-save on toggle, instant search, and show/hide system apps option.
 * Persistent Configuration: Inter-process synchronization powered by libxposed Remote Preferences; settings persist across reboots.
⚙️ How It Works
Developer Options state values are stored in system settings (Settings.Global / Settings.Secure). When an application queries these values, it makes cross-process IPC calls to SettingsProvider.
This module injects into the system_server process and hooks the query / call methods of SettingsProvider:
 * Identifies the caller initiating the request (resolving the package name via Binder.getCallingUid());
 * Checks whether the caller is in your selected target list and whether the requested key matches Developer Options settings;
 * Returns spoofed values (development_settings_enabled=0, adb_enabled=0, adb_port=-1, adb_wifi_enabled=0, etc.).
Intercepted keys:
| Key | Spoofed Value | Description |
|---|---|---|
| development_settings_enabled | 0 | Master switch for Developer Options |
| adb_enabled | 0 | USB Debugging |
| adb_port | -1 | Wireless Debugging port |
| adb_wifi_enabled | 0 | Wireless Debugging (Android 11+) |
📥 Installation & Usage
 * Install LSPosed (requires a root solution such as Magisk or KernelSU with Zygisk/LSPosed enabled).
 * Install this module's APK and enable it in the LSPosed Manager.
 * Ensure the module scope in LSPosed includes "System Framework" (system).
 * Open the HideDevOpts app and select the target apps from which you want to hide Developer Options.
 * Reboot your device (the module is injected into system_server, so a reboot is required to take effect).
> Note: If a target application has already read the developer options before being selected, it may temporarily retain the status in its own in-memory cache. Force-stopping or cold-restarting the target app resolves this.
> 
🔨 Building
This project is built in the cloud via GitHub Actions (no local Android SDK setup required).
Dependency versions:
 * libxposed api / service: 102.0.0
 * compileSdk 37, minSdk 26, targetSdk 34
 * AGP 8.13.0, Gradle 8.13
Local build:
gradle assembleRelease

Build outputs are located in app/build/outputs/apk/release/.
📄 Acknowledgments
 * libxposed — Modern Xposed API
 * LSPosed — Xposed Framework
 * IAmNotADeveloper — Implementation reference for similar projects
