package com.safepulse.core;

/**
 * 0~4단계 위험 판별 상태 머신(기획서 6장).
 *
 * <ul>
 *   <li>1 주의: 브레이크 OFF + 가속 ≥ 0.3g + 크루즈 아님 + 주행 기어. 무음.</li>
 *   <li>2 개입(저속·출차 구간): 1단계가 1.5초 이어지거나, 손목 경직·심박 급상승이 함께 있을 때.</li>
 *   <li>2 개입(일반 주행 구간): 웨어러블 패닉 신호가 있고 가속이 3초 이어질 때만. 워치가 없으면 기록만.</li>
 *   <li>3 긴급: 브레이크 ON인데 속도 증가가 1초 이어질 때.</li>
 *   <li>4 사후: 개입 중 정차(1초) + 기어 P 또는 EPB 체결.</li>
 * </ul>
 * 개입이 시작되면 가속이 멈춰도 정차할 때까지 유지(래치)한다. 운전자가 발을 뗀 뒤에도 정차까지 안내해야 하기 때문이다.
 */
public final class RiskEngine {

    public interface Listener {
        void onLevelChanged(RiskLevel from, RiskLevel to, String reason, long timeMs);
    }

    private final RiskConfig cfg;
    private final AccelerationEstimator accel;
    private Listener listener;

    private RiskLevel level = RiskLevel.MONITOR;
    private Gear lastGear = Gear.UNKNOWN;
    private long shiftToMovingGearAt = Long.MIN_VALUE / 4;
    private long watchSince = -1;
    private long watchLostSince = -1;
    private long criticalSince = -1;
    private long stoppedSince = -1;
    private long cooldownUntil = Long.MIN_VALUE / 4;
    private Float heartRateBaseline;

    private double lastAccelG;
    private boolean lastPanic;
    private boolean lastStopped;
    private boolean lastLowSpeedContext;
    private String engageReason = "";

    public RiskEngine(RiskConfig cfg) {
        this.cfg = cfg;
        this.accel = new AccelerationEstimator(cfg.accelWindowMs);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public RiskLevel update(Frame f) {
        final long t = f.timeMs;
        final double g = accel.update(t, f.speedKmh / 3.6);
        lastAccelG = g;

        // 변속 추적: P·N → D·R로 바뀐 시각(출차 직후 구간 판단용).
        // 기어를 처음 알게 된 순간(UNKNOWN → D)은 변속으로 치지 않는다. 주행 중 앱이 켜졌을 때의 오탐 방지.
        if (f.gear != lastGear) {
            boolean fromStill = lastGear == Gear.PARK || lastGear == Gear.NEUTRAL;
            if (f.gear.isMovingGear() && fromStill) {
                shiftToMovingGearAt = t;
            }
            lastGear = f.gear;
        }

        // 웨어러블: 2초보다 오래된 값은 쓰지 않는다
        boolean wearableFresh = f.hasWearable() && (t - f.wearableTimeMs) <= cfg.wearableStaleMs;
        if (level == RiskLevel.MONITOR && wearableFresh && Math.abs(g) < 0.15) {
            float hr = f.heartRateBpm;
            heartRateBaseline = heartRateBaseline == null ? hr : heartRateBaseline * 0.95f + hr * 0.05f;
        }
        boolean gripPanic = wearableFresh && f.gripScore != null && f.gripScore >= cfg.gripThreshold;
        boolean hrPanic = wearableFresh && heartRateBaseline != null
                && f.heartRateBpm - heartRateBaseline >= cfg.heartRateRiseBpm;
        boolean panic = gripPanic || hrPanic;
        lastPanic = panic;

        // 정차 판정
        if (f.speedKmh <= cfg.stopSpeedKmh) {
            if (stoppedSince < 0) stoppedSince = t;
        } else {
            stoppedSince = -1;
        }
        boolean stopped = stoppedSince >= 0 && t - stoppedSince >= cfg.stopConfirmMs;
        lastStopped = stopped;

        boolean lowSpeedContext = f.speedKmh <= cfg.lowSpeedKmh
                || (t - shiftToMovingGearAt) <= cfg.shiftWindowMs;
        lastLowSpeedContext = lowSpeedContext;

        switch (level) {
            case POST:
                if (f.speedKmh > cfg.resumeSpeedKmh) {
                    setLevel(RiskLevel.MONITOR, "driving_resumed", t);
                }
                break;

            case INTERVENE:
            case CRITICAL:
                if (level == RiskLevel.INTERVENE && updateCritical(f, g, t)) {
                    setLevel(RiskLevel.CRITICAL, "brake_on_but_accelerating", t);
                }
                if (stopped && (f.gear == Gear.PARK || f.isParkingBrakeOn())) {
                    setLevel(RiskLevel.POST, "stopped_and_secured", t);
                }
                break;

            case MONITOR:
            case WATCH:
            default:
                if (t < cooldownUntil) {
                    resetTimers();
                    if (level == RiskLevel.WATCH) setLevel(RiskLevel.MONITOR, "cooldown", t);
                    break;
                }
                if (updateCritical(f, g, t)) {
                    engageReason = "brake_on_but_accelerating";
                    setLevel(RiskLevel.CRITICAL, engageReason, t);
                    break;
                }
                evaluateWatch(f, g, t, lowSpeedContext, panic, gripPanic);
                break;
        }
        return level;
    }

    private boolean updateCritical(Frame f, double g, long t) {
        boolean cond = f.isBrakeOn() && g >= cfg.criticalAccelG && f.speedKmh >= cfg.criticalMinSpeedKmh;
        if (cond) {
            if (criticalSince < 0) criticalSince = t;
            return t - criticalSince >= cfg.criticalSustainMs;
        }
        criticalSince = -1;
        return false;
    }

    private void evaluateWatch(Frame f, double g, long t, boolean lowSpeedContext, boolean panic, boolean gripPanic) {
        boolean watchCond = !f.isBrakeOn()
                && g >= cfg.watchAccelG
                && !f.cruiseActive
                && f.gear != Gear.PARK
                && f.gear != Gear.NEUTRAL;

        if (watchCond) {
            if (watchSince < 0) watchSince = t;
            watchLostSince = -1;
        } else if (watchSince >= 0) {
            if (watchLostSince < 0) watchLostSince = t;
            if (t - watchLostSince > cfg.conditionGapMs) {
                watchSince = -1;
            }
        }

        if (watchSince >= 0) {
            if (level == RiskLevel.MONITOR) {
                setLevel(RiskLevel.WATCH, lowSpeedContext ? "low_speed_acceleration" : "high_speed_acceleration", t);
            }
            long duration = t - watchSince;
            if (lowSpeedContext) {
                if (panic) {
                    engageReason = gripPanic ? "wrist_grip" : "heart_rate_spike";
                    setLevel(RiskLevel.INTERVENE, engageReason, t);
                } else if (duration >= cfg.interveneSustainMs) {
                    engageReason = "sustained_low_speed_acceleration";
                    setLevel(RiskLevel.INTERVENE, engageReason, t);
                }
            } else if (panic && duration >= cfg.highSpeedSustainMs) {
                engageReason = "panic_with_sustained_acceleration";
                setLevel(RiskLevel.INTERVENE, engageReason, t);
            }
        } else if (level == RiskLevel.WATCH && watchLostSince >= 0 && t - watchLostSince > cfg.watchReleaseMs) {
            setLevel(RiskLevel.MONITOR, "condition_cleared", t);
            watchLostSince = -1;
        }
    }

    /** 운전자가 비상 버튼을 눌렀을 때(수동 비상 모드). */
    public void manualTrigger(long t) {
        if (!level.isEngaged()) {
            engageReason = "manual";
            cooldownUntil = Long.MIN_VALUE / 4;
            setLevel(RiskLevel.INTERVENE, engageReason, t);
        }
    }

    /** 운전자가 취소했을 때. 10초 동안 재개입하지 않는다. */
    public void cancel(long t) {
        cooldownUntil = t + cfg.cancelCooldownMs;
        resetTimers();
        if (level != RiskLevel.MONITOR) {
            setLevel(RiskLevel.MONITOR, "cancelled_by_driver", t);
        }
    }

    /** 사후 단계에서 운전자가 "괜찮아요"를 눌렀을 때. */
    public void acknowledgePost(long t) {
        if (level == RiskLevel.POST) {
            setLevel(RiskLevel.MONITOR, "driver_ok", t);
        }
    }

    private void resetTimers() {
        watchSince = -1;
        watchLostSince = -1;
        criticalSince = -1;
    }

    private void setLevel(RiskLevel to, String reason, long t) {
        if (to == level) return;
        RiskLevel from = level;
        level = to;
        if (to == RiskLevel.MONITOR) {
            // WATCH로 갈 때 초기화하면 가속 지속 시간이 사라진다. 0단계로 돌아갈 때만 초기화.
            resetTimers();
        }
        if (listener != null) listener.onLevelChanged(from, to, reason, t);
    }

    public RiskLevel level() { return level; }
    public double lastAccelG() { return lastAccelG; }
    public boolean lastPanic() { return lastPanic; }
    public boolean isStopped() { return lastStopped; }
    public boolean isLowSpeedContext() { return lastLowSpeedContext; }
    public Float heartRateBaseline() { return heartRateBaseline; }
    public String engageReason() { return engageReason; }
}
