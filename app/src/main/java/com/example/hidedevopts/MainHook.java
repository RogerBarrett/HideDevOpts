package com.example.hidedevopts;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;

public class MainHook extends XposedModule {

    private static final String TAG = "HideDevOpts";
    private static final String SETTINGS_PROVIDER =
            "com.android.providers.settings.SettingsProvider";
    private static final String PREFS_NAME = "default";
    private static final String KEY_HIDDEN = "hidden_packages";

    // 要伪装成"关闭"的开发者选项 key -> 伪装值
    private static final Map<String, String> HIDDEN_KEYS = new HashMap<>();
    static {
        HIDDEN_KEYS.put("development_settings_enabled", "0"); // 开发者选项总开关
        HIDDEN_KEYS.put("adb_enabled", "0");                  // USB 调试
        HIDDEN_KEYS.put("adb_port", "-1");                    // 无线调试端口
        HIDDEN_KEYS.put("adb_wifi_enabled", "0");             // 无线调试开关(Android 11+)
    }

    private volatile Set<String> targetPackages = new HashSet<>();
    private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;

    private Object packageManager;
    private Method getPackagesForUid;

    // 必须保留此签名的双参构造函数（框架通过反射调用它实例化入口类）
    public MainHook(XposedInterface base, ModuleLoadedParam param) {
        super(base, param);
        String initMsg = "MainHook init: process=" + param.getProcessName();
        Log.i(TAG, "=== " + initMsg + " ===");
        try {
            log(initMsg);
        } catch (Throwable ignored) {
        }
    }

    // 双通道日志：系统 logcat + LSPosed 管理器日志
    private void logInfo(String msg) {
        Log.i(TAG, msg);
        try {
            log(msg);
        } catch (Throwable ignored) {
        }
    }

    private void logError(String msg, Throwable t) {
        Log.e(TAG, msg, t);
        try {
            log(msg + ": " + t, t);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        logInfo("=== onSystemServerStarting ===");
        ClassLoader cl = param.getClassLoader();

        // 1. 加载配置（失败不影响后续 hook）
        try {
            loadConfig();
        } catch (Throwable t) {
            logError("loadConfig failed", t);
        }

        // 2. 准备 PackageManager（用于 uid -> 包名反查）
        try {
            preparePackageManager(cl);
        } catch (Throwable t) {
            logError("preparePackageManager failed", t);
        }

        // 3. Hook SettingsProvider
        try {
            Class<?> providerClass = cl.loadClass(SETTINGS_PROVIDER);
            logInfo("loaded class: " + providerClass.getName());
            hookQuery(providerClass);
            hookCall(providerClass);
            logInfo("=== hooked SettingsProvider OK ===");
        } catch (Throwable t) {
            logError("hook failed", t);
        }
    }

    private void loadConfig() {
        SharedPreferences prefs = getRemotePreferences(PREFS_NAME);
        targetPackages = new HashSet<>(prefs.getStringSet(KEY_HIDDEN, new HashSet<>()));

        // 监听配置变化，实时更新；listener 需要强引用避免被 GC
        prefsListener = (sp, key) -> {
            if (KEY_HIDDEN.equals(key)) {
                targetPackages = new HashSet<>(sp.getStringSet(KEY_HIDDEN, new HashSet<>()));
                logInfo("config updated, hidden packages: " + targetPackages.size());
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(prefsListener);

        logInfo("loaded hidden packages: " + targetPackages.size());
    }

    private void preparePackageManager(ClassLoader cl) throws Throwable {
        Class<?> appGlobals = cl.loadClass("android.app.AppGlobals");
        Object pm = appGlobals.getMethod("getPackageManager").invoke(null);
        this.packageManager = pm;
        try {
            this.getPackagesForUid = pm.getClass().getMethod("getPackagesForUid", int.class);
            logInfo("getPackagesForUid(uid) found");
        } catch (NoSuchMethodException e) {
            this.getPackagesForUid = pm.getClass().getMethod("getPackagesForUid", int.class, int.class);
            logInfo("getPackagesForUid(uid, userId) found");
        }
    }

    private void hookQuery(Class<?> cls) {
        Method m = null;
        try {
            m = cls.getDeclaredMethod("query",
                    Uri.class, String[].class, Bundle.class, CancellationSignal.class);
        } catch (NoSuchMethodException e1) {
            try {
                m = cls.getDeclaredMethod("query",
                        Uri.class, String[].class, String.class, String[].class, String.class, CancellationSignal.class);
            } catch (NoSuchMethodException e2) {
                logError("no query method found", e2);
                return;
            }
        }
        logInfo("hooking query: " + m);
        hook(m)
                .setId("hide_devopts_query")
                .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                .intercept(chain -> {
                    Uri uri = (Uri) chain.getArg(0);
                    Bundle queryArgs = (Bundle) chain.getArg(2);

                    String name = extractNameFromQuery(uri, queryArgs);
                    if (name == null || !HIDDEN_KEYS.containsKey(name)) {
                        return chain.proceed();
                    }
                    if (!isTargetCaller()) {
                        return chain.proceed();
                    }

                    String[] projection = (String[]) chain.getArg(1);
                    Cursor fake = buildFakeCursor(name, HIDDEN_KEYS.get(name), projection);
                    logInfo("query blocked: " + name);
                    return fake;
                });
    }

    private void hookCall(Class<?> cls) throws NoSuchMethodException {
        Method m = cls.getDeclaredMethod("call",
                String.class, String.class, Bundle.class);
        logInfo("hooking call: " + m);
        hook(m)
                .setId("hide_devopts_call")
                .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                .intercept(chain -> {
                    String method = (String) chain.getArg(0);
                    String name = (String) chain.getArg(1);

                    if (method == null || !method.startsWith("GET_")) {
                        return chain.proceed();
                    }
                    if (!HIDDEN_KEYS.containsKey(name)) {
                        return chain.proceed();
                    }
                    if (!isTargetCaller()) {
                        return chain.proceed();
                    }

                    Bundle fake = new Bundle();
                    fake.putString("value", HIDDEN_KEYS.get(name));
                    logInfo("call blocked: " + method + "/" + name);
                    return fake;
                });
    }

    private String extractNameFromQuery(Uri uri, Bundle queryArgs) {
        if (uri != null && uri.getPathSegments() != null
                && uri.getPathSegments().size() >= 2) {
            String last = uri.getLastPathSegment();
            if (HIDDEN_KEYS.containsKey(last)) {
                return last;
            }
        }
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

    private boolean isTargetCaller() {
        if (targetPackages.isEmpty()) {
            return false;
        }
        int uid = Binder.getCallingUid();
        if (packageManager == null || getPackagesForUid == null) {
            logInfo("isTargetCaller: packageManager not ready");
            return false;
        }
        try {
            Object result;
            if (getPackagesForUid.getParameterCount() == 1) {
                result = getPackagesForUid.invoke(packageManager, uid);
            } else {
                result = getPackagesForUid.invoke(packageManager, uid, 0);
            }
            if (result instanceof String[]) {
                for (String p : (String[]) result) {
                    if (targetPackages.contains(p)) {
                        logInfo("target caller matched: uid=" + uid + " pkg=" + p);
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            logError("resolve uid " + uid + " failed", t);
        }
        return false;
    }
}