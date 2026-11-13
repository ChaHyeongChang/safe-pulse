package com.safepulse.core;

/**
 * 폐루프 음성 코칭(기획서 4장 ③).
 * 한 번에 한 동작만 지시하고, 차량 신호로 수행을 확인한 뒤 다음 단계로 넘어간다.
 * 확인되지 않으면 같은 지시를 2초마다 반복한다.
 *
 * <pre>
 * 1 BRAKE          "발 떼고, 두 발로 브레이크!"   확인: 브레이크 ON + 감속 → "좋아요, 계속 밟으세요"
 *                                                  브레이크 ON인데 2초간 감속 없음 → 2단계
 * 2 NEUTRAL        "기어 N!"                      확인: 기어 N
 * 3 PARKING_BRAKE  "주차 브레이크 당기고 유지!"     확인: EPB ON → 정차까지 유지 안내
 * 4 STOPPED        "멈췄습니다. 기어 P, 비상등…"    확인: 기어 P 또는 EPB → 사후
 * 5 SAFE           "안전하게 정차했습니다. 괜찮으세요?"  30초 무응답 → 보호자 알림
 * </pre>
 */
public final class CoachingOrchestrator {

    public enum Step {
        IDLE(0, "대기"), BRAKE(1, "브레이크"), NEUTRAL(2, "기어 N"), PARKING_BRAKE(3, "주차 브레이크"),
        STOPPED(4, "정차"), SAFE(5, "안전 확인");

        public final int number;
        public final String label;

        Step(int number, String label) {
            this.number = number;
            this.label = label;
        }
    }

    public interface Listener {
        void onPrompt(Prompt prompt, long timeMs);
        void onStepChanged(Step from, Step to, long timeMs);
        void onGuardianAlert(long timeMs);
    }

    private final RiskConfig cfg;
    private Listener listener;

    private Step step = Step.IDLE;
    private long lastPromptAt = Long.MIN_VALUE / 4;
    private Prompt lastPrompt;
    private long brakeOnNoDecelSince = -1;
    private int unknownGearRepeats;
    private long wellnessSince = -1;
    private boolean respondedOk;
    private boolean guardianAlerted;

    public CoachingOrchestrator(RiskConfig cfg) {
        this.cfg = cfg;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void update(Frame f, RiskLevel level, double accelG, boolean stopped) {
        final long t = f.timeMs;

        if (level == RiskLevel.MONITOR || level == RiskLevel.WATCH) {
            if (step != Step.IDLE) setStep(Step.IDLE, t);
            return;
        }

        if (level == RiskLevel.POST) {
            if (step != Step.SAFE && step != Step.STOPPED) {
                // 주차 브레이크로 바로 고정된 경우에도 "기어 P, 비상등" 안내는 빠뜨리지 않는다
                setStep(Step.STOPPED, t);
                say(Prompt.STOPPED, t);
            } else if (step == Step.STOPPED) {
                if (due(t, cfg.stoppedToSafeMs)) {
                    setStep(Step.SAFE, t);
                    say(Prompt.SAFE, t);
                    wellnessSince = t;
                }
            } else if (!respondedOk && !guardianAlerted && t - wellnessSince >= cfg.wellnessTimeoutMs) {
                guardianAlerted = true;
                say(Prompt.GUARDIAN, t);
                if (listener != null) listener.onGuardianAlert(t);
            }
            return;
        }

        // ---- 개입·긴급 단계
        if (step == Step.IDLE || step == Step.SAFE) {
            respondedOk = false;
            guardianAlerted = false;
            Step first = level == RiskLevel.CRITICAL ? Step.NEUTRAL : Step.BRAKE;
            setStep(first, t);
            say(first == Step.NEUTRAL ? Prompt.NEUTRAL : Prompt.BRAKE, t);
            return;
        }

        if (stopped && step != Step.STOPPED) {
            setStep(Step.STOPPED, t);
            say(Prompt.STOPPED, t);
            return;
        }

        if (level == RiskLevel.CRITICAL && step == Step.BRAKE) {
            setStep(Step.NEUTRAL, t);
            say(Prompt.NEUTRAL, t);
            return;
        }

        switch (step) {
            case BRAKE:
                handleBrakeStep(f, accelG, t);
                break;
            case NEUTRAL:
                if (f.gear == Gear.NEUTRAL) {
                    setStep(Step.PARKING_BRAKE, t);
                    say(Prompt.PARKING_BRAKE, t);
                } else if (due(t, cfg.promptRepeatMs)) {
                    if (f.gear == Gear.UNKNOWN && ++unknownGearRepeats >= 2) {
                        // 기어 신호가 없는 차량: 두 번 안내한 뒤 다음 단계로
                        setStep(Step.PARKING_BRAKE, t);
                        say(Prompt.PARKING_BRAKE, t);
                    } else {
                        say(Prompt.NEUTRAL, t);
                    }
                }
                break;
            case PARKING_BRAKE:
                if (due(t, f.isParkingBrakeOn() ? cfg.holdRepeatMs : cfg.promptRepeatMs)) {
                    say(Prompt.PARKING_BRAKE, t);
                }
                break;
            case STOPPED:
                if (!stopped) {
                    // 정차 직후 다시 움직이면 브레이크부터 다시 안내
                    setStep(Step.BRAKE, t);
                    say(Prompt.BRAKE, t);
                } else if (due(t, cfg.holdRepeatMs)) {
                    say(Prompt.STOPPED, t);
                }
                break;
            default:
                break;
        }
    }

    private void handleBrakeStep(Frame f, double accelG, long t) {
        boolean decelerating = accelG <= cfg.decelConfirmG;
        boolean brakeUnknown = f.brakePressed == null;
        boolean confirmed = decelerating && (f.isBrakeOn() || brakeUnknown);
        boolean standing = f.speedKmh <= cfg.stopSpeedKmh;

        if (standing && (f.isBrakeOn() || brakeUnknown)) {
            // 이미 멈춰 브레이크를 밟고 있다: 정차 확정(STOPPED)까지 조용히 기다린다
            brakeOnNoDecelSince = -1;
            return;
        }

        if (confirmed) {
            brakeOnNoDecelSince = -1;
            if (lastPrompt != Prompt.BRAKE_GOOD || due(t, cfg.holdRepeatMs)) {
                say(Prompt.BRAKE_GOOD, t);
            }
        } else if (f.isBrakeOn()) {
            if (brakeOnNoDecelSince < 0) brakeOnNoDecelSince = t;
            if (t - brakeOnNoDecelSince >= cfg.brakeNoDecelMs) {
                setStep(Step.NEUTRAL, t);
                say(Prompt.NEUTRAL, t);
            } else if (due(t, cfg.promptRepeatMs)) {
                say(Prompt.BRAKE, t);
            }
        } else {
            brakeOnNoDecelSince = -1;
            if (due(t, cfg.promptRepeatMs)) say(Prompt.BRAKE, t);
        }
    }

    /** 운전자가 사후 단계에서 "괜찮아요"를 눌렀을 때. */
    public void respondOk() {
        respondedOk = true;
    }

    /** 취소·종료 시 상태 초기화. */
    public void reset(long t) {
        if (step != Step.IDLE) setStep(Step.IDLE, t);
        brakeOnNoDecelSince = -1;
        unknownGearRepeats = 0;
        wellnessSince = -1;
        lastPrompt = null;
        lastPromptAt = Long.MIN_VALUE / 4;
    }

    private boolean due(long t, long interval) {
        return t - lastPromptAt >= interval;
    }

    private void say(Prompt p, long t) {
        lastPrompt = p;
        lastPromptAt = t;
        if (listener != null) listener.onPrompt(p, t);
    }

    private void setStep(Step to, long t) {
        if (to == step) return;
        Step from = step;
        step = to;
        brakeOnNoDecelSince = -1;
        if (to != Step.NEUTRAL) unknownGearRepeats = 0;
        if (listener != null) listener.onStepChanged(from, to, t);
    }

    public Step step() { return step; }
    public Prompt lastPrompt() { return lastPrompt; }
    public boolean isAwaitingWellnessResponse() {
        return step == Step.SAFE && !respondedOk && !guardianAlerted;
    }
    public long wellnessRemainingMs(long now) {
        if (!isAwaitingWellnessResponse() || wellnessSince < 0) return -1;
        return Math.max(0, cfg.wellnessTimeoutMs - (now - wellnessSince));
    }
    public boolean guardianAlerted() { return guardianAlerted; }
}
