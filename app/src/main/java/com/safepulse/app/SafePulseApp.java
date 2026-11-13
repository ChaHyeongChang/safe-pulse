package com.safepulse.app;

import android.app.Application;

/** 앱 전체에서 하나만 있는 컨트롤러를 들고 있는다. */
public class SafePulseApp extends Application {

    private SafePulseController controller;

    @Override
    public void onCreate() {
        super.onCreate();
        controller = new SafePulseController(this);
    }

    public SafePulseController controller() {
        return controller;
    }

    public static SafePulseController controller(android.content.Context context) {
        return ((SafePulseApp) context.getApplicationContext()).controller();
    }
}
