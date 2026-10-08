package com.example.hidedevopts;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.CancellationSignal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String SETTINGS_PROVIDER =
            "com.android.providers.settings.SettingsProvider";

    // ============ 可配置区域 ============
    // 目标应用包名（建议改成 SharedPreferences 配置，这里示例先写死）
    private static final Set<String> TARGET_PACKAGES = new HashSet<>(Arrays.asList(
            "com.example.targetapp"
    ));

    // 要伪装成"关闭"的开发者选项 key -> 伪装值
    private static final Map<String, String> HIDDEN_KEYS = new HashMap<>();
    static {
        HIDDEN_KEYS.put("development_settings_enabled", "0"); // 开发者选项总开关
        HIDDEN_KEYS.put("adb_enabled", "0");                  // USB 调试
        HIDDEN_KEYS.put("adb_port", "-1");                    // 无线调试端口
        HIDDEN_KEYS.put("adb_wifi_enabled", "0");             // 无线调试开关(Android 11+)
    }
    // ====================================

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        // 只在系统框架(system_server)里 hook
        if (!"android".equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("[HideDevOpts] hooked into system framework");

        try {
            Class<?> cls = XposedHelpers.findClass(SETTINGS_PROVIDER, lpparam.classLoader);
            hookQuery(cls);
            hookCall(cls);
        } catch (Throwable t) {
            XposedBridge.log("[HideDevOpts] init failed: " + t);
        }
    }

    // ---------------- query：Settings.Global/Secure 读取走这里 ----------------
    // Android 8.0+ 签名: query(Uri, String[], Bundle, CancellationSignal)
    private void hookQuery(Class<?> cls) {
        XposedHelpers.findAndHookMethod(cls, "query",
                Uri.class, String[].class, Bundle.class, CancellationSignal.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        String name = extractNameFromQuery(
                                (Uri) param.args[0],
                                (Bundle) param.args[2]);

                        // 只处理开发者选项相关 key，避免拖慢其他设置读取
                        if (name == null || !HIDDEN_KEYS.containsKey(name)) {
                            return;
                        }
                        if (!isTargetCaller()) {
                            return;
                        }

                        String fakeValue = HIDDEN_KEYS.get(name);
                        String[] projection = (String[]) param.args[1];

                        param.setResult(buildFakeCursor(name, fakeValue, projection));
                        XposedBridge.log("[HideDevOpts] query blocked: " + name);
                    }
                });
    }

    // ---------------- call：GET_global / GET_secure 等走这里 ----------------
    private void hookCall(Class<?> cls) {
        XposedHelpers.findAndHookMethod(cls, "call",
                String.class, String.class, Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        String method = (String) param.args[0];
                        String name = (String) param.args[1];

                        if (method == null || !method.startsWith("GET_")) return;
                        if (!HIDDEN_KEYS.containsKey(name)) return;
                        if (!isTargetCaller()) return;

                        Bundle fake = new Bundle();
                        fake.putString("value", HIDDEN_KEYS.get(name));
                        param.setResult(fake);
                        XposedBridge.log("[HideDevOpts] call blocked: " + method + "/" + name);
                    }
                });
    }

    // 从 uri 或 queryArgs 里解析出 setting name
    private String extractNameFromQuery(Uri uri, Bundle queryArgs) {
        // 形式1: content://settings/global/adb_enabled
        if (uri != null && uri.getPathSegments() != null
                && uri.getPathSegments().size() >= 2) {
            String last = uri.getLastPathSegment();
            if (HIDDEN_KEYS.containsKey(last)) {
                return last;
            }
        }
        // 形式2: queryArgs 里 name=?
        if (queryArgs != null) {
            String selection = queryArgs.getString("android:query-arg-sql-selection");
            if (selection != null && selection.contains("name=?")) {
                String[] args = queryArgs.getStringArray("android:query-arg-sql-selection-args");
                if (args != null && args.length > 0) {
                    return args[0];
                }
            }
        }
        return null;
    }

    // 按 projection 构造伪造 Cursor，保证 getString(0) 返回伪值
    private Cursor buildFakeCursor(String name, String fakeValue, String[] projection) {
        String[] cols;
        Object[] row;

        if (projection == null) {
            cols = new String[]{"_id", "name", "value"};
            row = new Object[]{1, name, fakeValue};
        } else {
            cols = projection;
            row = new Object[projection.length];
            for (int i = 0; i < projection.length; i++) {
                String c = projection[i];
                if ("value".equals(c)) row[i] = fakeValue;
                else if ("name".equals(c)) row[i] = name;
                else if ("_id".equals(c)) row[i] = 1;
                else row[i] = null;
            }
        }

        MatrixCursor cursor = new MatrixCursor(cols);
        cursor.addRow(row);
        return cursor;
    }

    // 通过 Binder UID 识别调用者
    private boolean isTargetCaller() {
        int uid = Binder.getCallingUid();
        String[] pkgs = getPackagesForUid(uid);
        if (pkgs == null) return false;

        for (String p : pkgs) {
            if (TARGET_PACKAGES.contains(p)) return true;
        }
        return false;
    }

    // UID -> 包名
    private String[] getPackagesForUid(int uid) {
        try {
            Class<?> appGlobals = XposedHelpers.findClass("android.app.AppGlobals", null);
            Object pm = XposedHelpers.callStaticMethod(appGlobals, "getPackageManager");
            try {
                return (String[]) XposedHelpers.callMethod(pm, "getPackagesForUid", uid);
            } catch (Throwable e) {
                // Android 11+ 某些版本可能是双参重载
                return (String[]) XposedHelpers.callMethod(pm, "getPackagesForUid", uid, 0);
            }
        } catch (Throwable t) {
            XposedBridge.log("[HideDevOpts] resolve uid " + uid + " failed: " + t);
            return null;
        }
    }
}
