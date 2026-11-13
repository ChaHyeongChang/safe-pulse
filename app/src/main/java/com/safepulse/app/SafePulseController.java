package com.safepulse.app;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.safepulse.core.CoachingOrchestrator;
import com.safepulse.core.EventRecorder;
import com.safepulse.core.Frame;
import com.safepulse.core.Prompt;
import com.safepulse.core.RehearsalSession;
import com.safepulse.core.RiskConfig;
import com.safepulse.core.RiskLevel;
import com.safepulse.core.SafePulseCore;
import com.safepulse.core.Scenario;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * 앱의 중심. 신호 출처 → SignalHub → (100ms마다) SafePulseCore → 음성·화면·기록으로 연결한다.
 * 판별 엔진은 전용 스레드 하나에서만 돌린다. 화면 명령도 이 스레드로 넘겨 처리한다.
 */
public final class SafePulseController {

    private static final String TAG = "SafePulse";
    private static final long TICK_MS = 100;

    public interface Listener {
        void onState(UiState state);
        void onNotice(String message);
    }

    private final Context context;
    private final SignalHub hub = new SignalHub();
    private final Prefs prefs;
    private final EventLogStore logStore;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Handler engine;
    private final SafePulseCore core;

    private VoiceOutput voice;
    private CarSignalSource car;
    private PleosVehicleBridge pleos;
    private volatile Listener listener;
    private volatile boolean uiVisible;
    private volatile UiState lastState = new UiState();
    private boolean started;

    private Scenario scenario;
    private long scenarioStartedAt;
    private int scenarioIndex;

    private String lastStateLine;
    private long lastStateLogAt;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.elapsedRealtime();
            advanceScenario(now);
            Frame f = hub.snapshot(now);
            core.tick(f);
            publish(f, now);
            engine.postDelayed(this, TICK_MS);
        }
    };

    SafePulseController(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = new Prefs(this.context);
        this.logStore = new EventLogStore(this.context);
        HandlerThread thread = new HandlerThread("safepulse-engine");
        thread.start();
        this.engine = new Handler(thread.getLooper());
        this.core = new SafePulseCore(new RiskConfig(), new CoreOutput());
    }

    // ------------------------------------------------------------------ 수명 주기

    public synchronized void start() {
        if (started) return;
        started = true;
        voice = new VoiceOutput(context);
        applySettings();
        car = new CarSignalSource(context, hub);
        car.start();
        pleos = new PleosVehicleBridge(context);
        pleos.start(hub::setBrake);
        engine.post(tick);
        Log.i(TAG, "controller started");
    }

    public synchronized void stop() {
        if (!started) return;
        started = false;
        engine.removeCallbacks(tick);
        engine.post(core::flush);
        if (car != null) car.stop();
        if (pleos != null) pleos.stop();
        if (voice != null) voice.shutdown();
    }

    /** 런타임 권한(CAR_SPEED)이 허용된 직후 다시 구독한다. */
    public void restartCarSignals() {
        main.post(() -> {
            if (car != null) car.start();
        });
    }

    public void applySettings() {
        hub.setWearableAllowed(prefs.wearableConsent());
        if (voice != null) voice.setSlow(prefs.elderMode());
    }

    public void setListener(Listener l) { listener = l; }
    /** 화면이 멈출 때 자기 자신일 때만 해제한다(다음 화면이 이미 등록했을 수 있음). */
    public void clearListener(Listener l) { if (listener == l) listener = null; }
    public void setUiVisible(boolean visible) { uiVisible = visible; }
    /** 가장 최근에 그린 상태. 화면 진입 조건(정차 여부) 확인용. */
    public UiState lastState() { return lastState; }

    // ------------------------------------------------------------------ 화면 명령

    public void manualEmergency() {
        engine.post(() -> core.manualEmergency(SystemClock.elapsedRealtime()));
    }

    public void cancel() {
        engine.post(() -> core.cancel(SystemClock.elapsedRealtime()));
    }

    public void respondOk() {
        engine.post(() -> core.respondOk(SystemClock.elapsedRealtime()));
    }

    public void startRehearsal() {
        engine.post(() -> {
            boolean ok = core.startRehearsal(SystemClock.elapsedRealtime());
            if (!ok) notice("P단에 정차한 상태에서만 리허설을 시작할 수 있습니다.");
        });
    }

    public void confirmRehearsalStep() {
        engine.post(core::confirmRehearsalStepManually);
    }

    public void stopRehearsal() {
        engine.post(() -> core.stopRehearsal(SystemClock.elapsedRealtime()));
    }

    /** 데모 시나리오 재생. withWatch=false면 같은 주행에서 워치 신호만 뺀다. */
    public void startScenario(String id, boolean withWatch) {
        engine.post(() -> {
            try {
                Scenario s = Scenario.load(id);
                if (!withWatch) s = s.withoutWearable();
                core.cancel(SystemClock.elapsedRealtime());
                core.stopRehearsal(SystemClock.elapsedRealtime());
                scenario = s;
                scenarioStartedAt = SystemClock.elapsedRealtime();
                scenarioIndex = 0;
                notice("데모 재생: " + s.title);
            } catch (Exception e) {
                Log.w(TAG, "scenario load failed", e);
                notice("시나리오를 불러오지 못했습니다: " + id);
            }
        });
    }

    public void stopScenario() {
        engine.post(this::finishScenario);
    }

    /** 워치 → 폰 브릿지(또는 adb 데모 입력)에서 들어온 값. */
    public void onWearableSample(Float heartRate, Float grip) {
        hub.setWearable(heartRate, grip, SystemClock.elapsedRealtime());
    }

    // ------------------------------------------------------------------ 내부

    private void advanceScenario(long now) {
        if (scenario == null) return;
        long elapsed = now - scenarioStartedAt;
        List<Scenario.Row> rows = scenario.rows;
        while (scenarioIndex < rows.size() && rows.get(scenarioIndex).t <= elapsed) {
            Scenario.Row row = rows.get(scenarioIndex++);
            hub.setScenarioRow(row);
            applyScenarioAction(row.action, now);
        }
        if (elapsed > scenario.durationMs() + 3000 && !core.level().isEngaged()) {
            finishScenario();
        }
    }

    private void applyScenarioAction(String action, long now) {
        if (action == null || action.isEmpty()) return;
        switch (action) {
            case "cancel": core.cancel(now); break;
            case "respond": core.respondOk(now); break;
            case "manual": core.manualEmergency(now); break;
            case "rehearsal": core.startRehearsal(now); break;
            case "confirm": core.confirmRehearsalStepManually(); break;
            default: break;
        }
    }

    private void finishScenario() {
        if (scenario == null) return;
        String title = scenario.title;
        scenario = null;
        hub.setScenarioRow(null);
        notice("데모 종료: " + title + " · 실시간 차량 신호로 돌아갑니다.");
    }

    private void publish(Frame f, long now) {
        UiState s = new UiState();
        s.speedKmh = f.speedKmh;
        s.gear = f.gear;
        s.brake = f.brakePressed;
        s.parkingBrake = f.parkingBrakeOn;
        s.cruise = f.cruiseActive;
        s.heartRate = f.heartRateBpm;
        s.grip = f.gripScore;
        s.wearableFresh = f.hasWearable() && now - f.wearableTimeMs <= core.config().wearableStaleMs;
        s.accelG = core.accelG();
        s.panic = core.panicSignal();
        s.level = core.level();
        s.step = core.step();
        s.lastPrompt = core.lastPrompt();
        s.engageReason = core.engageReason();
        s.wellnessRemainingMs = core.wellnessRemainingMs(now);
        s.guardianAlerted = core.guardianAlerted();
        s.recording = core.isRecording();
        s.rehearsing = core.isRehearsing();
        s.rehearsalStep = core.rehearsalStep();
        s.scenarioRunning = scenario != null;
        if (scenario != null) {
            s.scenarioTitle = scenario.title;
            long dur = Math.max(1, scenario.durationMs());
            s.scenarioProgressPct = (int) Math.min(100, (now - scenarioStartedAt) * 100 / dur);
        }
        s.carApiConnected = car != null && car.isConnected();
        s.pleosBrakeConnected = pleos != null && pleos.isConnected();
        lastState = s;
        logState(s, now);
        if (listener == null) return;
        main.post(() -> {
            Listener current = listener;
            if (current != null) current.onState(s);
        });
    }

    /** 실시간 신호 확인용(Logcat 태그 SafePulse/State). 값이 바뀔 때만, 최대 0.25초에 한 번 남긴다. */
    private void logState(UiState s, long now) {
        String line = String.format(Locale.US, "%s v=%.1f gear=%s brake=%s epb=%s a=%+.2fg level=%s step=%s",
                s.scenarioRunning ? "demo" : "live", s.speedKmh, s.gear.symbol, s.brake, s.parkingBrake,
                s.accelG, s.level, s.step);
        if (line.equals(lastStateLine) || now - lastStateLogAt < 250) return;
        lastStateLine = line;
        lastStateLogAt = now;
        Log.d(TAG + "/State", line);
    }

    private void notice(String message) {
        Log.i(TAG, message);
        main.post(() -> {
            Listener current = listener;
            if (current != null) current.onNotice(message);
        });
    }

    Prefs prefs() { return prefs; }
    EventLogStore logStore() { return logStore; }

    /** SafePulseCore의 출력을 음성·알림·저장으로 연결한다. 엔진 스레드에서 호출된다. */
    private final class CoreOutput extends SafePulseCore.OutputAdapter {
        @Override
        public void onLevelChanged(RiskLevel from, RiskLevel to, String reason, long t) {
            Log.i(TAG, "level " + from + " -> " + to + " (" + reason + ")");
            if (to.isEngaged() && !uiVisible) {
                SafePulseService.showAlert(context, "세이프 펄스 긴급 안내", "음성 안내를 따라 주세요.");
            }
        }

        @Override
        public void onPrompt(Prompt prompt, boolean interrupt, long t) {
            if (voice != null) voice.speak(prompt, interrupt);
        }

        @Override
        public void onStepChanged(CoachingOrchestrator.Step from, CoachingOrchestrator.Step to, long t) {
            Log.i(TAG, "coaching step " + from + " -> " + to);
        }

        @Override
        public void onGuardianAlert(long t) {
            String name = prefs.guardianName().isEmpty() ? "등록된 보호자" : prefs.guardianName();
            notice(name + "에게 현재 위치와 함께 알림을 보냈습니다. (데모: 실제 발송은 하지 않음)");
            SafePulseService.showAlert(context, "보호자 알림", name + "에게 알림을 보냈습니다.");
        }

        @Override
        public void onEventLog(String json, EventRecorder.Summary summary) {
            File f = logStore.save(json);
            notice(String.format(Locale.KOREA, "이벤트 기록 저장 · %s · 최고 %.0fkm/h%s",
                    summary.maxLevel.label, summary.peakSpeedKmh, f == null ? " (저장 실패)" : ""));
        }

        @Override
        public void onRehearsalFinished(RehearsalSession.Result result, long t) {
            prefs.saveRehearsal(result);
            notice(String.format(Locale.KOREA, "리허설 완료 · 3동작 %.1f초%s",
                    result.totalMs() / 1000.0, result.anyManual ? " (일부 수동 확인)" : ""));
        }

        @Override
        public void onRehearsalAborted(String reason, long t) {
            notice("리허설을 멈췄습니다.");
        }
    }
}
