package com.safepulse.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pleos Vehicle SDK 연결부. 브레이크 페달 상태는 AAOS 표준 속성에 없어 Vehicle SDK의 Brake 도메인으로 받는다.
 *
 * <p>공식 샘플(Pleos-Playground/pleos-sample-vehicle-app) 기준 호출 순서:
 * {@code Vehicle(context) → initialize() → getBrake() → register…(listener) → … → unregister…(listener) → release()}.
 * Brake 도메인의 공개 API는 4개(capability 확인·조회·등록·해제)다.
 *
 * <p>리스너의 콜백 이름·매개변수 형태는 SDK 버전에 따라 달라질 수 있어 리플렉션 + 동적 프록시로 붙인다.
 * 그래서 SDK가 없거나(safepulse.pleosSdk=false) 버전이 바뀌어도 앱 빌드와 데모는 깨지지 않는다.
 * 실제 차량/에뮬레이터에서 Logcat 태그 "SafePulse/Pleos"로 SDK 메서드 목록과 콜백 값을 확인할 수 있다.
 * 형태가 확인되면 타입 있는 호출로 바꿔도 된다(README의 "Pleos SDK 연결" 참고).
 */
final class PleosVehicleBridge {

    private static final String TAG = "SafePulse/Pleos";
    private static final String VEHICLE_CLASS = "ai.pleos.playground.vehicle.Vehicle";
    private static final int MAX_ATTEMPTS = 6;

    interface BrakeListener {
        void onBrakePedal(Boolean pressed);
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Object vehicle;
    private Object brakeDomain;
    private Object brakeListenerProxy;
    private Method unregisterMethod;
    private boolean connected;
    private int attempts;

    PleosVehicleBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    void start(BrakeListener listener) {
        if (!BuildConfig.PLEOS_SDK) {
            Log.i(TAG, "Pleos SDK disabled at build time");
            return;
        }
        try {
            Class<?> vehicleClass = Class.forName(VEHICLE_CLASS);
            vehicle = vehicleClass.getConstructor(Context.class).newInstance(context);
            invokeInitialize(vehicleClass);
            brakeDomain = vehicleClass.getMethod("getBrake").invoke(vehicle);
            logPublicMethods("Brake", brakeDomain);
            scheduleRegister(listener, 500);
        } catch (ClassNotFoundException e) {
            Log.i(TAG, "Vehicle SDK not on classpath");
        } catch (Throwable t) {
            Log.w(TAG, "Vehicle SDK init failed", t);
        }
    }

    /** initialize()는 오버로드나 기본 인자가 있을 수 있어 가장 짧은 시그니처를 찾아 호출한다. */
    private void invokeInitialize(Class<?> vehicleClass) throws Exception {
        Method best = null;
        for (Method m : vehicleClass.getMethods()) {
            if (m.getName().equals("initialize") && (best == null || m.getParameterCount() < best.getParameterCount())) {
                best = m;
            }
        }
        if (best == null) throw new NoSuchMethodException("initialize");
        Object[] args = new Object[best.getParameterCount()];
        Class<?>[] types = best.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            args[i] = types[i].isInterface() ? loggingProxy(types[i], "initialize") : null;
        }
        best.invoke(vehicle, args);
    }

    private void scheduleRegister(BrakeListener listener, long delayMs) {
        handler.postDelayed(() -> {
            attempts++;
            if (tryRegisterBrake(listener)) {
                connected = true;
                Log.i(TAG, "brake pedal listener registered");
            } else if (attempts < MAX_ATTEMPTS) {
                // initialize()는 IPC로 비동기 연결된다. 서비스 연결 전이면 잠시 후 다시 시도.
                scheduleRegister(listener, 1000);
            } else {
                Log.w(TAG, "brake pedal listener not registered; check Logcat method list");
            }
        }, delayMs);
    }

    private boolean tryRegisterBrake(BrakeListener listener) {
        if (brakeDomain == null) return false;
        Method register = null;
        for (Method m : brakeDomain.getClass().getMethods()) {
            String n = m.getName();
            if (n.startsWith("register") && n.contains("Brake") && n.contains("Pedal")) {
                register = m;
                break;
            }
        }
        if (register == null) {
            Log.w(TAG, "no register…BrakePedal… method on Brake domain");
            return false;
        }
        try {
            Class<?>[] types = register.getParameterTypes();
            Object[] args = new Object[types.length];
            for (int i = 0; i < types.length; i++) {
                if (types[i].isInterface()) {
                    brakeListenerProxy = brakeProxy(types[i], listener);
                    args[i] = brakeListenerProxy;
                } else {
                    args[i] = defaultArea(types[i]);
                }
            }
            register.invoke(brakeDomain, args);
            unregisterMethod = findMethod(brakeDomain, register.getName().replaceFirst("register", "unregister"));
            Log.i(TAG, "called " + register.getName() + Arrays.toString(types));
            return true;
        } catch (Throwable t) {
            Log.w(TAG, register.getName() + " failed: " + t);
            return false;
        }
    }

    /** 리스너 콜백의 첫 Boolean/숫자 인자를 브레이크 페달 눌림 여부로 해석한다. 실패 콜백은 로그만 남긴다. */
    private Object brakeProxy(Class<?> iface, BrakeListener listener) {
        InvocationHandler h = (proxy, method, args) -> {
            Object objectResult = handleObjectMethod(proxy, method, args);
            if (objectResult != null) return objectResult;
            Log.d(TAG, "brake callback " + method.getName() + " " + Arrays.toString(args));
            if (args != null) {
                for (Object a : args) {
                    if (a instanceof Throwable) {
                        Log.w(TAG, "brake listener failure: " + a);
                        return defaultReturn(method);
                    }
                }
                for (Object a : args) {
                    Boolean pressed = interpret(a);
                    if (pressed != null) {
                        listener.onBrakePedal(pressed);
                        break;
                    }
                }
            }
            return defaultReturn(method);
        };
        return Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, h);
    }

    private static Boolean interpret(Object a) {
        if (a instanceof Boolean) return (Boolean) a;
        if (a instanceof Number) return ((Number) a).floatValue() > 0f;
        if (a instanceof Enum) {
            String n = ((Enum<?>) a).name().toUpperCase();
            if (n.contains("NOT") || n.contains("OFF") || n.contains("RELEASE")) return false;
            if (n.contains("ON") || n.contains("PRESS") || n.contains("APPLIED")) return true;
        }
        return null;
    }

    private Object loggingProxy(Class<?> iface, String tag) {
        InvocationHandler h = (proxy, method, args) -> {
            Object objectResult = handleObjectMethod(proxy, method, args);
            if (objectResult != null) return objectResult;
            Log.d(TAG, tag + " callback " + method.getName() + " " + Arrays.toString(args));
            return defaultReturn(method);
        };
        return Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, h);
    }

    private static Object handleObjectMethod(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "toString": return "SafePulseProxy";
            case "hashCode": return System.identityHashCode(proxy);
            case "equals": return args != null && args.length == 1 && proxy == args[0];
            default: return null;
        }
    }

    private static Object defaultReturn(Method method) {
        Class<?> r = method.getReturnType();
        if (r == boolean.class) return false;
        if (r == int.class) return 0;
        if (r == long.class) return 0L;
        if (r == short.class) return (short) 0;
        if (r == byte.class) return (byte) 0;
        if (r == float.class) return 0f;
        if (r == double.class) return 0d;
        if (r == char.class) return '\0';
        if (r.getName().equals("kotlin.Unit")) {
            try {
                return r.getField("INSTANCE").get(null);
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    /** 영역(area) 인자가 필요한 시그니처면 GLOBAL 같은 상수나 첫 enum 값을 쓴다. */
    private static Object defaultArea(Class<?> type) {
        try {
            return type.getField("GLOBAL").get(null);
        } catch (Throwable ignored) {
        }
        if (type.isEnum() && type.getEnumConstants().length > 0) return type.getEnumConstants()[0];
        if (type == int.class) return 0;
        return null;
    }

    private static Method findMethod(Object target, String name) {
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(name)) return m;
        }
        return null;
    }

    private static void logPublicMethods(String domain, Object obj) {
        if (obj == null) return;
        List<String> names = new ArrayList<>();
        for (Method m : obj.getClass().getMethods()) {
            if (m.getDeclaringClass() == Object.class) continue;
            names.add(m.getName() + Arrays.toString(m.getParameterTypes()));
        }
        Log.i(TAG, domain + " methods: " + names);
    }

    boolean isConnected() {
        return connected;
    }

    void stop() {
        handler.removeCallbacksAndMessages(null);
        try {
            if (unregisterMethod != null && brakeListenerProxy != null) {
                Class<?>[] types = unregisterMethod.getParameterTypes();
                Object[] args = new Object[types.length];
                for (int i = 0; i < types.length; i++) {
                    args[i] = types[i].isInterface() ? brakeListenerProxy : defaultArea(types[i]);
                }
                unregisterMethod.invoke(brakeDomain, args);
            }
            if (vehicle != null) vehicle.getClass().getMethod("release").invoke(vehicle);
        } catch (Throwable t) {
            Log.w(TAG, "release failed", t);
        }
        connected = false;
        vehicle = null;
        brakeDomain = null;
    }
}
