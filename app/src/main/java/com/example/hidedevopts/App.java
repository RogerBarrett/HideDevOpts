package com.example.hidedevopts;

import android.app.Application;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public class App extends Application implements XposedServiceHelper.OnServiceListener {

    public interface ServiceListener {
        void onServiceReady();
    }

    private static volatile XposedService service;
    private static final List<ServiceListener> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(this);
    }

    @Override
    public void onServiceBind(XposedService service) {
        App.service = service;
        for (ServiceListener l : listeners) {
            l.onServiceReady();
        }
    }

    @Override
    public void onServiceDied(XposedService service) {
        App.service = null;
    }

    public static void addServiceListener(ServiceListener l) {
        listeners.add(l);
        if (service != null) {
            l.onServiceReady();
        }
    }

    public static void removeServiceListener(ServiceListener l) {
        listeners.remove(l);
    }

    public static XposedService getService() {
        return service;
    }
}