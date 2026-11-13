package com.safepulse.app;

import android.content.Context;
import android.util.Log;

/**
 * Pleos Vehicle SDK 없이 빌드할 때(-Psafepulse.pleosSdk=false) 쓰는 빈 구현.
 * 브레이크·크루즈 신호만 빠지고, AAOS 차량 속성·워치·데모 시나리오는 그대로 동작한다.
 * SDK를 쓰는 실제 구현은 src/pleos/java 에 있다.
 */
final class PleosVehicleBridge {

    interface BrakeListener {
        void onBrakePedal(Boolean pressed);
    }

    interface CruiseListener {
        void onCruiseActive(boolean active);
    }

    PleosVehicleBridge(Context context) {
    }

    void start(BrakeListener brakeListener, CruiseListener cruiseListener) {
        Log.i("SafePulse/Pleos", "Pleos SDK disabled at build time");
    }

    boolean isConnected() {
        return false;
    }

    void stop() {
    }
}
