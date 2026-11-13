package com.safepulse.app;

import android.content.Context;
import android.util.Log;

import ai.pleos.playground.vehicle.Vehicle;
import ai.pleos.playground.vehicle.api.Brake;
import ai.pleos.playground.vehicle.api.DrivingMode;
import ai.pleos.playground.vehicle.constant.control.CruiseControl;
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener;
import ai.pleos.playground.vehicle.listener.CruiseControlStateListener;
import kotlin.Unit;

/**
 * Pleos Vehicle SDK 연결부(safepulse.pleosSdk=true 빌드). 읽기 전용이며 차량 제어 API는 쓰지 않는다.
 * <ul>
 *   <li>브레이크 페달: AAOS 표준 속성에 없어 Brake 도메인으로 받는다.</li>
 *   <li>크루즈 상태: AAOS CRUISE_CONTROL_STATE는 READ_ADAS_STATES(signature|privileged) 권한이 필요해
 *       서드파티 앱이 읽을 수 없다. 그래서 DrivingMode 도메인(CAR_DRIVE, normal 권한)으로 받는다.</li>
 * </ul>
 *
 * <p>Vehicle SDK 2.3.2 소스(Gradle 캐시)와 에뮬레이터 Logcat으로 확인한 API:
 * <ul>
 *   <li>{@code Vehicle(context) → initialize() → getBrake()/getDrivingMode() → … → release()}</li>
 *   <li>{@code Brake.registerBrakePedalStatus(BrakePedalStatusListener)}:
 *       {@code onBrakePedalStatusUpdated(Boolean?)} true = 페달 밟음, {@code onFailed(Exception)}</li>
 *   <li>{@code Brake.getCurrentBrakePedalStatus(onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)}</li>
 *   <li>{@code DrivingMode.registerCruiseControlState(CruiseControlStateListener)}:
 *       {@code onCruiseControlStateUpdated(CruiseControl?)} — ACTIVATED일 때만 크루즈가 속도를 제어 중</li>
 * </ul>
 * initialize()는 서비스에 비동기로 붙지만, SDK가 연결 전 호출을 쌓아 두었다가 연결되면 보낸다. 그래서 바로 등록한다.
 * 리스너는 값이 바뀔 때만 불리므로 등록 직후 현재 값을 한 번 읽어 둔다.
 *
 * <p>SDK 없이 빌드하면(safepulse.pleosSdk=false) src/noPleos의 같은 이름 클래스가 대신 들어간다.
 */
final class PleosVehicleBridge {

    private static final String TAG = "SafePulse/Pleos";

    interface BrakeListener {
        void onBrakePedal(Boolean pressed);
    }

    interface CruiseListener {
        void onCruiseActive(boolean active);
    }

    private final Context context;
    private Vehicle vehicle;
    private Brake brake;
    private BrakePedalStatusListener brakePedalListener;
    private DrivingMode drivingMode;
    private CruiseControlStateListener cruiseStateListener;
    private volatile boolean connected;

    PleosVehicleBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 첫 register 호출이 SDK 내부 권한 확인 때문에 에뮬레이터에서 약 2~3초 걸린다(2026-11-14 측정).
     * 화면 시작을 막지 않도록 별도 스레드에서 연결한다. 콜백은 SDK 바인더 스레드에서 온다.
     */
    void start(BrakeListener brakeListener, CruiseListener cruiseListener) {
        new Thread(() -> setUp(brakeListener, cruiseListener), "safepulse-pleos").start();
    }

    private synchronized void setUp(BrakeListener brakeListener, CruiseListener cruiseListener) {
        try {
            vehicle = new Vehicle(context);
            vehicle.initialize();
            Log.i(TAG, "Vehicle SDK initialized (typed API)");
        } catch (Throwable t) {
            Log.w(TAG, "Vehicle SDK init failed", t);
            vehicle = null;
            return;
        }
        setUpBrake(brakeListener);
        setUpCruise(cruiseListener);
    }

    private void setUpBrake(BrakeListener listener) {
        try {
            brake = vehicle.getBrake();
            brakePedalListener = new BrakePedalStatusListener() {
                @Override
                public void onBrakePedalStatusUpdated(Boolean status) {
                    Log.d(TAG, "brake pedal status " + status);
                    if (status != null) listener.onBrakePedal(status);
                }

                @Override
                public void onFailed(Exception e) {
                    Log.w(TAG, "brake pedal listener failure: " + e);
                }
            };
            brake.registerBrakePedalStatus(brakePedalListener);
            connected = true;
            Log.i(TAG, "brake pedal listener registered");

            brake.getCurrentBrakePedalStatus(status -> {
                Log.d(TAG, "brake pedal current " + status);
                if (status != null) listener.onBrakePedal(status);
                return Unit.INSTANCE;
            }, e -> {
                Log.w(TAG, "brake pedal current failed: " + e);
                return Unit.INSTANCE;
            });
            brake.checkBrakePedalStatusCapabilities(config -> {
                Log.i(TAG, "brake pedal capability " + config.getAccessType());
                return Unit.INSTANCE;
            }, e -> {
                Log.w(TAG, "brake pedal capability failed: " + e);
                return Unit.INSTANCE;
            });
        } catch (Throwable t) {
            // 권한 누락·서비스 부재 등. 브레이크 신호만 빠지고 나머지 감지와 데모는 계속 동작한다.
            Log.w(TAG, "brake pedal setup failed", t);
            brakePedalListener = null;
            connected = false;
        }
    }

    private void setUpCruise(CruiseListener listener) {
        try {
            drivingMode = vehicle.getDrivingMode();
            cruiseStateListener = new CruiseControlStateListener() {
                @Override
                public void onCruiseControlStateUpdated(CruiseControl state) {
                    Log.d(TAG, "cruise control state " + state);
                    // 알 수 없는 값(null)도 "크루즈 아님"으로 본다. 이전 ACTIVATED가 남아 감지를 막지 않게 한다.
                    listener.onCruiseActive(state == CruiseControl.ACTIVATED);
                }

                @Override
                public void onFailed(Exception e) {
                    Log.w(TAG, "cruise control listener failure: " + e);
                }
            };
            drivingMode.registerCruiseControlState(cruiseStateListener);
            Log.i(TAG, "cruise control state listener registered");

            drivingMode.getCruiseControlState(state -> {
                Log.d(TAG, "cruise control current " + state);
                listener.onCruiseActive(state == CruiseControl.ACTIVATED);
                return Unit.INSTANCE;
            }, e -> {
                Log.w(TAG, "cruise control current failed: " + e);
                return Unit.INSTANCE;
            });
        } catch (Throwable t) {
            // 크루즈 상태가 없으면 '크루즈 아님'으로 보고 판별한다(기존 동작과 같음).
            Log.w(TAG, "cruise control setup failed", t);
            cruiseStateListener = null;
        }
    }

    boolean isConnected() {
        return connected;
    }

    synchronized void stop() {
        try {
            if (brake != null && brakePedalListener != null) brake.unregisterBrakePedalStatus(brakePedalListener);
            if (drivingMode != null && cruiseStateListener != null) {
                drivingMode.unregisterCruiseControlState(cruiseStateListener);
            }
            if (vehicle != null) vehicle.release();
        } catch (Throwable t) {
            Log.w(TAG, "release failed", t);
        }
        connected = false;
        brakePedalListener = null;
        cruiseStateListener = null;
        brake = null;
        drivingMode = null;
        vehicle = null;
    }
}
