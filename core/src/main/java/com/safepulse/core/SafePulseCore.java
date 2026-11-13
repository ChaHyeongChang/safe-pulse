package com.safepulse.core;

/**
 * 판별 엔진·코칭·기록·리허설을 하나로 묶은 진입점.
 * Android 앱과 단위 테스트, 데모 시나리오가 모두 이 클래스를 같은 방식으로 호출한다.
 * 스레드 안전하지 않으므로 한 스레드(앱에서는 엔진 스레드)에서만 호출한다.
 */
public final class SafePulseCore {

    /** 앱이 받는 출력. 음성 재생, 화면 갱신, 파일 저장은 앱이 맡는다. */
    public interface Output {
        void onLevelChanged(RiskLevel from, RiskLevel to, String reason, long timeMs);
        /** interrupt=true면 재생 중인 안내를 끊고 바로 말한다(위기 코칭). */
        void onPrompt(Prompt prompt, boolean interrupt, long timeMs);
        void onStepChanged(CoachingOrchestrator.Step from, CoachingOrchestrator.Step to, long timeMs);
        void onGuardianAlert(long timeMs);
        void onEventLog(String json, EventRecorder.Summary summary);
        void onRehearsalStep(RehearsalSession.Step step, long timeMs);
        void onRehearsalFinished(RehearsalSession.Result result, long timeMs);
        void onRehearsalAborted(String reason, long timeMs);
    }

    /** 아무것도 하지 않는 기본 구현. 필요한 메서드만 재정의해서 쓴다. */
    public static class OutputAdapter implements Output {
        @Override public void onLevelChanged(RiskLevel from, RiskLevel to, String reason, long timeMs) { }
        @Override public void onPrompt(Prompt prompt, boolean interrupt, long timeMs) { }
        @Override public void onStepChanged(CoachingOrchestrator.Step from, CoachingOrchestrator.Step to, long timeMs) { }
        @Override public void onGuardianAlert(long timeMs) { }
        @Override public void onEventLog(String json, EventRecorder.Summary summary) { }
        @Override public void onRehearsalStep(RehearsalSession.Step step, long timeMs) { }
        @Override public void onRehearsalFinished(RehearsalSession.Result result, long timeMs) { }
        @Override public void onRehearsalAborted(String reason, long timeMs) { }
    }

    private final RiskConfig cfg;
    private final RiskEngine engine;
    private final CoachingOrchestrator coach;
    private final EventRecorder recorder;
    private final Output out;
    private RehearsalSession rehearsal;
    private Frame lastFrame;

    public SafePulseCore(RiskConfig cfg, Output output) {
        this.cfg = cfg;
        this.out = output;
        this.engine = new RiskEngine(cfg);
        this.coach = new CoachingOrchestrator(cfg);
        this.recorder = new EventRecorder(cfg);

        engine.setListener((from, to, reason, t) -> {
            recorder.onLevelChanged(from, to, reason, t);
            out.onLevelChanged(from, to, reason, t);
        });
        coach.setListener(new CoachingOrchestrator.Listener() {
            @Override public void onPrompt(Prompt prompt, long t) {
                recorder.onPrompt(prompt, t);
                out.onPrompt(prompt, true, t);
            }
            @Override public void onStepChanged(CoachingOrchestrator.Step from, CoachingOrchestrator.Step to, long t) {
                out.onStepChanged(from, to, t);
            }
            @Override public void onGuardianAlert(long t) {
                out.onGuardianAlert(t);
            }
        });
        recorder.setListener(out::onEventLog);
    }

    /** 100ms마다 호출. */
    public void tick(Frame f) {
        lastFrame = f;
        if (rehearsal != null && rehearsal.isActive()) {
            // 리허설 중에는 위기 판별을 멈춘다(P단 정차 중에만 실행되므로 안전).
            rehearsal.update(f);
            if (!rehearsal.isActive()) rehearsal = null;
            recorder.add(f, engine.lastAccelG(), engine.level(), coach.step());
            return;
        }
        RiskLevel level = engine.update(f);
        coach.update(f, level, engine.lastAccelG(), engine.isStopped());
        recorder.add(f, engine.lastAccelG(), engine.level(), coach.step());
    }

    /** 화면의 큰 비상 버튼(수동 비상 모드). */
    public void manualEmergency(long t) {
        stopRehearsal(t);
        engine.manualTrigger(t);
        if (lastFrame != null) {
            coach.update(withTime(lastFrame, t), engine.level(), engine.lastAccelG(), engine.isStopped());
        }
    }

    /** 운전자 취소("괜찮아요" 버튼 또는 STT). */
    public void cancel(long t) {
        boolean wasActive = engine.level().isEngaged() || engine.level() == RiskLevel.WATCH;
        engine.cancel(t);
        coach.reset(t);
        if (wasActive) {
            recorder.onPrompt(Prompt.CANCELLED, t);
            out.onPrompt(Prompt.CANCELLED, true, t);
        }
    }

    /** 사후 단계 안부 확인에 대한 응답. */
    public void respondOk(long t) {
        coach.respondOk();
        engine.acknowledgePost(t);
    }

    public boolean startRehearsal(long t) {
        if (lastFrame == null || engine.level() != RiskLevel.MONITOR) return false;
        Frame f = withTime(lastFrame, t);
        rehearsal = RehearsalSession.start(cfg, f, new RehearsalSession.Listener() {
            @Override public void onPrompt(Prompt prompt, long time) { out.onPrompt(prompt, false, time); }
            @Override public void onStepChanged(RehearsalSession.Step step, long time) { out.onRehearsalStep(step, time); }
            @Override public void onFinished(RehearsalSession.Result result, long time) { out.onRehearsalFinished(result, time); }
            @Override public void onAborted(String reason, long time) { out.onRehearsalAborted(reason, time); }
        });
        return rehearsal != null;
    }

    public void confirmRehearsalStepManually() {
        if (rehearsal != null) rehearsal.confirmManually();
    }

    public void stopRehearsal(long t) {
        if (rehearsal != null) {
            rehearsal.stop(t);
            rehearsal = null;
        }
    }

    public void flush() {
        recorder.flush();
    }

    private static Frame withTime(Frame f, long t) {
        return Frame.builder(t).speedKmh(f.speedKmh).brake(f.brakePressed).gear(f.gear)
                .parkingBrake(f.parkingBrakeOn).cruise(f.cruiseActive)
                .wearable(f.heartRateBpm, f.gripScore, f.wearableTimeMs).build();
    }

    // ---- 화면 표시용 조회
    public RiskLevel level() { return engine.level(); }
    public CoachingOrchestrator.Step step() { return coach.step(); }
    public Prompt lastPrompt() { return coach.lastPrompt(); }
    public double accelG() { return engine.lastAccelG(); }
    public boolean panicSignal() { return engine.lastPanic(); }
    public Float heartRateBaseline() { return engine.heartRateBaseline(); }
    public String engageReason() { return engine.engageReason(); }
    public boolean isRehearsing() { return rehearsal != null && rehearsal.isActive(); }
    public RehearsalSession.Step rehearsalStep() { return rehearsal == null ? null : rehearsal.step(); }
    public long wellnessRemainingMs(long now) { return coach.wellnessRemainingMs(now); }
    public boolean guardianAlerted() { return coach.guardianAlerted(); }
    public boolean isRecording() { return recorder.isCapturing(); }
    public RiskConfig config() { return cfg; }
}
