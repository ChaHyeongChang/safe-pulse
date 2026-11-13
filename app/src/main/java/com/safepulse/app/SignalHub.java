package com.safepulse.app;

import com.safepulse.core.Frame;
import com.safepulse.core.Gear;
import com.safepulse.core.Scenario;

/**
 * 여러 신호 출처(AAOS 차량 속성, Pleos SDK, 워치, 데모 시나리오)의 최신 값을 모은다.
 * 신호 콜백은 여러 스레드에서 오므로 모든 접근을 잠금으로 보호한다.
 * 데모 시나리오가 재생 중이면 시나리오 값이 실제 신호보다 우선한다.
 */
final class SignalHub {

    private final Object lock = new Object();

    private float speedKmh;
    private Boolean brake;
    private Gear gear = Gear.UNKNOWN;
    private Boolean parkingBrake;
    private boolean cruise;
    private Float heartRate;
    private Float grip;
    private long wearableAt = -1;
    private boolean wearableAllowed = true;

    private Scenario.Row scenarioRow;

    void setSpeedKmh(float v) { synchronized (lock) { speedKmh = v; } }
    void setBrake(Boolean v) { synchronized (lock) { brake = v; } }
    void setGear(Gear v) { synchronized (lock) { gear = v; } }
    void setParkingBrake(Boolean v) { synchronized (lock) { parkingBrake = v; } }
    void setCruise(boolean v) { synchronized (lock) { cruise = v; } }

    void setWearable(Float hr, Float gripScore, long measuredAt) {
        synchronized (lock) {
            heartRate = hr;
            grip = gripScore;
            wearableAt = measuredAt;
        }
    }

    /** 사용자가 설정에서 생체 신호 사용을 끄면 판별에 넘기지 않는다. */
    void setWearableAllowed(boolean allowed) { synchronized (lock) { wearableAllowed = allowed; } }

    void setScenarioRow(Scenario.Row row) { synchronized (lock) { scenarioRow = row; } }

    Frame snapshot(long now) {
        synchronized (lock) {
            if (scenarioRow != null) {
                Frame f = scenarioRow.toFrameAt(now);
                if (!wearableAllowed) {
                    return Frame.builder(now).speedKmh(f.speedKmh).brake(f.brakePressed).gear(f.gear)
                            .parkingBrake(f.parkingBrakeOn).cruise(f.cruiseActive).build();
                }
                return f;
            }
            Frame.Builder b = Frame.builder(now).speedKmh(speedKmh).brake(brake).gear(gear)
                    .parkingBrake(parkingBrake).cruise(cruise);
            if (wearableAllowed && heartRate != null) b.wearable(heartRate, grip, wearableAt);
            return b.build();
        }
    }
}
