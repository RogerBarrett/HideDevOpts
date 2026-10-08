# HideDevOpts
一个 [LSPosed](https://github.com/LSPosed/LSPosed) 模块，针对你指定的应用**隐藏开发者选项**。
当被勾选的应用读取开发者选项相关开关（`development_settings_enabled`、`adb_enabled`、`adb_wifi_enabled` 等）时，本模块会返回"已关闭/未开启"的伪造值，让应用误以为设备没有开启开发者选项。其他未勾选的应用不受任何影响。
   ✨ 特性
-  **精准定向**：只对你在 App 里勾选的应用生效，其余应用完全不受干扰。
-  **不注入目标应用**：仅在 `system_server`（系统框架）进程内拦截 `SettingsProvider`，不 hook 目标应用本身，可避开目标应用的反 hook 检测。
- **图形化配置界面**：应用列表（图标 + 名称 + 包名）、勾选即保存、实时搜索、显示/隐藏系统应用。
- **配置持久化**：基于 libxposed Remote Preferences 跨进程同步，重启不丢失。
##  工作原理
开发者选项的开关值存储在系统设置（`Settings.Global` / `Settings.Secure`）中。应用读取这些值时，最终会跨进程调用 `SettingsProvider`。
本模块注入 `system_server` 进程，hook 其中的 `SettingsProvider` 的 `query` / `call` 方法：
1. 识别当前发起读取的调用者（通过 `Binder.getCallingUid()` 反查包名）；
2. 若调用者是你勾选的目标应用，且读取的 key 是开发者选项相关键值；
3. 则返回伪造值（`development_settings_enabled=0`、`adb_enabled=0`、`adb_port=-1`、`adb_wifi_enabled=0` 等）。
被拦截的键值：

| Key | 伪造值 | 含义 |
| :--- | :--- | :--- |
| `development_settings_enabled` | `0` | 开发者选项总开关 |
| `adb_enabled` | `0` | USB 调试 |
| `adb_port` | `-1` | 无线调试端口 |
| `adb_wifi_enabled` | `0` | 无线调试（Android 11+） |

## 📥 安装与使用
1. 安装 LSPosed（需要 Magisk/KernelSU 等 root 方案，并启用 Zygisk/LSPosed）。
2. 安装本模块 APK，并在 LSPosed 管理器中启用本模块。
3. 在 LSPosed 中确认作用域包含「系统框架 / System Framework」（`system`）。
4. 打开 HideDevOpts App，勾选需要隐藏开发者选项的应用。
5. 重启手机（模块注入的是 system_server，更新后需要重启生效）。
> 提示：目标应用若在勾选前已经读取过开发者选项，可能因自身内存缓存而短暂失效，冷启动目标应用即可。
##  构建
本项目通过 GitHub Actions 云端编译（无需本地 Android 环境）。
依赖版本：
- libxposed `api` / `service`：`102.0.0`
- compileSdk `37`，minSdk `26`，targetSdk `34`
- AGP `8.13.0`，Gradle `8.13`
本地构建：
```bash
gradle assembleRelease
```
产物位于 `app/build/outputs/apk/release/`。
## 📄 致谢
- [libxposed](https://github.com/libxposed/api) —— 现代 Xposed API
- [LSPosed](https://github.com/LSPosed/LSPosed) —— Xposed 框架
- [IAmNotADeveloper](https://github.com/xfqwdsj/IAmNotADeveloper) —— 同类项目的实现参考
