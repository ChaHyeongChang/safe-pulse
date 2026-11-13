package com.safepulse.core;

/**
 * 골든타임 리허설(기획서 4장 ①). P단에 정차해 있을 때만 실행한다.
 * 실제 코칭과 같은 문장으로 브레이크 → N → EPB → P 복귀를 지시하고,
 * 차량 신호로 수행을 확인하며 단계별 반응 시간을 잰다. 차가 움직이면 즉시 중단한다.
 */
public final class RehearsalSession {

    public enum Step {
        INTRO("안내"), BRAKE("두 발 브레이크"), NEUTRAL("기어 N"), PARKING_BRAKE("주차 브레이크"),
        RETURN_P("기어 P 복귀"), DONE("완료"), ABORTED("중단");

        public final String label;

        Step(String label) {
            this.label = label;
        }
    }

    public static final class Result {
        public final long brakeMs;
        public final long neutralMs;
        public final long parkingBrakeMs;
        public final boolean anyManual;

        Result(long brakeMs, long neutralMs, long parkingBrakeMs, boolean anyManual) {
            this.brakeMs = brakeMs;
            this.neutralMs = neutralMs;
            this.parkingBrakeMs = parkingBrakeMs;
            this.anyManual = anyManual;
        }

        /** 위기 대응 3동작(브레이크·N·EPB)에 걸린 시간 합계. */
        public long totalMs() {
            return brakeMs + neutralMs + parkingBrakeMs;
        }
    }

    public interface Listener {
        void onPrompt(Prompt prompt, long timeMs);
        void onStepChanged(Step step, long timeMs);
        void onFinished(Result result, long timeMs);
        void onAborted(String reason, long timeMs);
    }

    private final RiskConfig cfg;
    private final Listener listener;
    private Step step = Step.INTRO;
    private long stepStartedAt;
    private long lastPromptAt;
    private long introEndsAt;
    private long brakeMs = -1, neutralMs = -1, parkingBrakeMs = -1;
    private boolean anyManual;
    private boolean manualConfirmRequested;

    private RehearsalSession(RiskConfig cfg, Listener listener) {
        this.cfg = cfg;
        this.listener = listener;
    }

    /** P단·정차 상태가 아니면 null을 돌려준다. */
    public static RehearsalSession start(RiskConfig cfg, Frame f, Listener listener) {
        if (!canStart(f)) return null;
        RehearsalSession s = new RehearsalSession(cfg, listener);
        s.introEndsAt = f.timeMs + cfg.rehearsalIntroMs;
        s.stepStartedAt = f.timeMs;
        s.lastPromptAt = f.timeMs;
        listener.onStepChanged(Step.INTRO, f.timeMs);
        listener.onPrompt(Prompt.REHEARSAL_START, f.timeMs);
        return s;
    }

    public static boolean canStart(Frame f) {
        return f.gear == Gear.PARK && f.speedKmh <= 0.5f;
    }

    public void update(Frame f) {
        final long t = f.timeMs;
        if (step == Step.DONE || step == Step.ABORTED) return;

        if (f.speedKmh > 1f) {
            setStep(Step.ABORTED, t);
            listener.onPrompt(Prompt.REHEARSAL_ABORT, t);
            listener.onAborted("vehicle_moving", t);
            return;
        }

        boolean manual = manualConfirmRequested;
        manualConfirmRequested = false;

        switch (step) {
            case INTRO:
                if (t >= introEndsAt) next(Step.BRAKE, Prompt.BRAKE, t);
                break;
            case BRAKE:
                if (f.isBrakeOn() || (manual && f.brakePressed == null)) {
                    anyManual |= manual && !f.isBrakeOn();
                    brakeMs = t - stepStartedAt;
                    next(Step.NEUTRAL, Prompt.NEUTRAL, t);
                } else repeat(Prompt.BRAKE, t);
                break;
            case NEUTRAL:
                if (f.gear == Gear.NEUTRAL || (manual && f.gear == Gear.UNKNOWN)) {
                    anyManual |= manual && f.gear != Gear.NEUTRAL;
                    neutralMs = t - stepStartedAt;
                    next(Step.PARKING_BRAKE, Prompt.PARKING_BRAKE, t);
                } else repeat(Prompt.NEUTRAL, t);
                break;
            case PARKING_BRAKE:
                if (f.isParkingBrakeOn() || (manual && f.parkingBrakeOn == null)) {
                    anyManual |= manual && !f.isParkingBrakeOn();
                    parkingBrakeMs = t - stepStartedAt;
                    next(Step.RETURN_P, Prompt.REHEARSAL_RETURN_P, t);
                } else repeat(Prompt.PARKING_BRAKE, t);
                break;
            case RETURN_P:
                if (f.gear == Gear.PARK || manual) {
                    setStep(Step.DONE, t);
                    listener.onPrompt(Prompt.REHEARSAL_DONE, t);
                    listener.onFinished(new Result(brakeMs, neutralMs, parkingBrakeMs, anyManual), t);
                } else repeat(Prompt.REHEARSAL_RETURN_P, t);
                break;
            default:
                break;
        }
    }

    /** 신호가 없는 항목(예: 브레이크 SDK 미연결)을 화면 버튼으로 확인할 때. 신호가 있으면 무시된다. */
    public void confirmManually() {
        manualConfirmRequested = true;
    }

    public void stop(long t) {
        if (step != Step.DONE && step != Step.ABORTED) {
            setStep(Step.ABORTED, t);
            listener.onAborted("stopped_by_user", t);
        }
    }

    private void next(Step to, Prompt prompt, long t) {
        setStep(to, t);
        stepStartedAt = t;
        lastPromptAt = t;
        listener.onPrompt(prompt, t);
    }

    private void repeat(Prompt prompt, long t) {
        if (t - lastPromptAt >= cfg.rehearsalRepeatMs) {
            lastPromptAt = t;
            listener.onPrompt(prompt, t);
        }
    }

    private void setStep(Step to, long t) {
        step = to;
        listener.onStepChanged(to, t);
    }

    public Step step() { return step; }
    public boolean isActive() { return step != Step.DONE && step != Step.ABORTED; }
    public long elapsedInStepMs(long now) { return now - stepStartedAt; }
}
