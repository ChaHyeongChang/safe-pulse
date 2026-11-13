package com.safepulse.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 워치 생체 신호 입력 경로.
 * 실서비스: 갤럭시워치(Wear OS Data Layer)·애플워치(WatchConnectivity) → 폰 앱 → 차량 앱으로 이 인텐트를 보낸다.
 * 데모:  adb shell am broadcast -a com.safepulse.app.WEARABLE_SAMPLE -n com.safepulse.app/.WearableInputReceiver --ef hr 112 --ef grip 0.85
 * 원시 생체 신호는 폰에서 처리하고 차량에는 심박·경직 점수만 보낸다(기획서 5장).
 */
public class WearableInputReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        Float hr = intent.hasExtra("hr") ? intent.getFloatExtra("hr", 0f) : null;
        Float grip = intent.hasExtra("grip") ? clamp(intent.getFloatExtra("grip", 0f)) : null;
        if (intent.getBooleanExtra("disconnect", false)) {
            hr = null;
            grip = null;
        }
        SafePulseApp.controller(context).onWearableSample(hr, grip);
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
