package com.example.hidedevopts;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.libxposed.service.XposedService;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "default";
    private static final String KEY_HIDDEN = "hidden_packages";

    private List<AppInfo> apps;
    private Set<String> selectedPackages = new HashSet<>();
    private AppListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        loadSelectedPackages();
        apps = loadInstalledApps();

        ListView listView = findViewById(R.id.app_list);
        adapter = new AppListAdapter(this, apps, selectedPackages, this);
        listView.setAdapter(adapter);

        if (App.getService() == null) {
            Toast.makeText(this, "LSPosed 框架未连接，请确认模块已在 LSPosed 中启用",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void loadSelectedPackages() {
        XposedService service = App.getService();
        if (service == null) {
            return;
        }
        try {
            SharedPreferences prefs = service.getRemotePreferences(PREFS_NAME);
            Set<String> saved = prefs.getStringSet(KEY_HIDDEN, null);
            if (saved != null) {
                selectedPackages.addAll(saved);
            }
        } catch (Throwable t) {
            Toast.makeText(this, "读取配置失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private List<AppInfo> loadInstalledApps() {
        List<AppInfo> result = new ArrayList<>();
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> installed = pm.getInstalledApplications(0);
        for (ApplicationInfo info : installed) {
            String pkg = info.packageName;
            String label = info.loadLabel(pm).toString();
            result.add(new AppInfo(pkg, label, info.loadIcon(pm)));
        }
        Collections.sort(result, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo a, AppInfo b) {
                return a.appName.compareToIgnoreCase(b.appName);
            }
        });
        return result;
    }

    public void onPackageChecked(String packageName, boolean checked) {
        if (checked) {
            selectedPackages.add(packageName);
        } else {
            selectedPackages.remove(packageName);
        }
        saveSelectedPackages();
    }

    private void saveSelectedPackages() {
        XposedService service = App.getService();
        if (service == null) {
            Toast.makeText(this, "框架未连接，无法保存", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            service.getRemotePreferences(PREFS_NAME)
                    .edit()
                    .putStringSet(KEY_HIDDEN, new HashSet<>(selectedPackages))
                    .apply();
        } catch (Throwable t) {
            Toast.makeText(this, "保存失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}