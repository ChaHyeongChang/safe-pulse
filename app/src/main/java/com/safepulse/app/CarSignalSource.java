package com.safepulse.app;

import android.car.Car;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.util.Log;

import com.safepulse.core.Gear;

/**
 * AAOS 표준 차량 속성 구독(읽기 전용).
 * Pleos 문서 기준 서드파티 앱이 읽을 수 있는 속성: PERF_VEHICLE_SPEED, GEAR_SELECTION, PARKING_BRAKE_ON, IGNITION_STATE.
 * (CURRENT_GEAR·ENGINE_RPM은 Pleos 차량에서 제공되지 않는다.)
 * 크루즈 상태는 지원 차량에서만 받는다. 없으면 조용히 건너뛴다.
 */
final class CarSignalSource {

    private static final String TAG = "SafePulse/Car";

    private final Context context;
    private final SignalHub hub;
    private Car car;
    private CarPropertyManager cpm;
    private boolean connected;
    private int cruiseStateId = -1;
    private int cruiseActivatedValue = 2;

    private final CarPropertyManager.CarPropertyEventCallback callback =
            new CarPropertyManager.CarPropertyEventCallback() {
                @Override
                public void onChangeEvent(CarPropertyValue value) {
                    handle(value);
                }

                @Override
                public void onErrorEvent(int propId, int zone) {
                    Log.w(TAG, "property error " + propId + " zone " + zone);
                }
            };

    CarSignalSource(Context context, SignalHub hub) {
        this.context = context.getApplicationContext();
        this.hub = hub;
    }

    /** 권한이 새로 허용되면 다시 호출해도 된다. */
    void start() {
        try {
            if (car == null) {
                car = Car.createCar(context);
                if (car == null) {
                    Log.w(TAG, "Car service not available");
                    return;
                }
                cpm = (CarPropertyManager) car.getCarManager(Car.PROPERTY_SERVICE);
            }
            if (cpm == null) return;
            boolean any = false;
            any |= subscribe(VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_FAST);
            any |= subscribe(VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE);
            any |= subscribe(VehiclePropertyIds.PARKING_BRAKE_ON, CarPropertyManager.SENSOR_RATE_ONCHANGE);
            subscribeCruiseIfSupported();
            connected = any;
        } catch (Throwable t) {
            // 자동차가 아닌 기기, 라이브러리 부재 등. 데모 시나리오로는 계속 동작한다.
            Log.w(TAG, "Car API unavailable", t);
            connected = false;
        }
    }

    private boolean subscribe(int propId, float rate) {
        try {
            boolean ok = cpm.registerCallback(callback, propId, rate);
            Log.i(TAG, "subscribe " + propId + " -> " + ok);
            return ok;
        } catch (SecurityException e) {
            Log.w(TAG, "permission missing for " + propId + ": " + e.getMessage());
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "property not supported " + propId);
        } catch (Throwable t) {
            Log.w(TAG, "subscribe failed " + propId, t);
        }
        return false;
    }

    /** CRUISE_CONTROL_STATE는 Android 14에 추가됐다. 상수가 없는 플랫폼에서도 컴파일·실행되도록 리플렉션으로 찾는다. */
    private void subscribeCruiseIfSupported() {
        try {
            cruiseStateId = VehiclePropertyIds.class.getField("CRUISE_CONTROL_STATE").getInt(null);
            try {
                Class<?> states = Class.forName("android.car.hardware.property.CruiseControlState");
                cruiseActivatedValue = states.getField("ACTIVATED").getInt(null);
            } catch (Throwable ignored) {
                cruiseActivatedValue = 2;
            }
            if (!subscribe(cruiseStateId, CarPropertyManager.SENSOR_RATE_ONCHANGE)) cruiseStateId = -1;
        } catch (Throwable t) {
            cruiseStateId = -1;
        }
    }

    private void handle(CarPropertyValue<?> value) {
        if (value == null || value.getValue() == null) return;
        int id = value.getPropertyId();
        Object v = value.getValue();
        if (id == VehiclePropertyIds.PERF_VEHICLE_SPEED && v instanceof Float) {
            // m/s → km/h. 후진 시 음수가 올 수 있어 절댓값.
            hub.setSpeedKmh(Math.abs((Float) v) * 3.6f);
        } else if (id == VehiclePropertyIds.GEAR_SELECTION && v instanceof Integer) {
            hub.setGear(Gear.fromVhal((Integer) v));
        } else if (id == VehiclePropertyIds.PARKING_BRAKE_ON && v instanceof Boolean) {
            hub.setParkingBrake((Boolean) v);
        } else if (id == cruiseStateId && v instanceof Integer) {
            hub.setCruise((Integer) v == cruiseActivatedValue);
        }
    }

    boolean isConnected() {
        return connected;
    }

    void stop() {
        try {
            if (cpm != null) cpm.unregisterCallback(callback);
            if (car != null) car.disconnect();
        } catch (Throwable t) {
            Log.w(TAG, "stop failed", t);
        }
        cpm = null;
        car = null;
        connected = false;
    }
}
