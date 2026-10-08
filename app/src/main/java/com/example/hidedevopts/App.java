package com.example.hidedevopts;

import android.app.Application;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public class App extends Application implements XposedServiceHelper.OnServiceListener {

    private static volatile XposedService service;

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(this);
    }

    @Override
    public void onServiceBind(XposedService service) {
        App.service = service;
    }

    @Override
    public void onServiceDied(XposedService service) {
        App.service = null;
    }

    public static XposedService getService() {
        return service;
    }
}