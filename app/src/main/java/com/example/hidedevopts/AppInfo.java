package com.example.hidedevopts;

import android.graphics.drawable.Drawable;

public class AppInfo {
    public final String packageName;
    public final String appName;
    public final Drawable icon;
    public final boolean isSystem;

    public AppInfo(String packageName, String appName, Drawable icon, boolean isSystem) {
        this.packageName = packageName;
        this.appName = appName;
        this.icon = icon;
        this.isSystem = isSystem;
    }
}