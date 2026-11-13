package com.safepulse.core;

/**
 * 한 시점의 차량·웨어러블 신호 묶음. 판별 엔진은 100ms마다 이 값을 받는다.
 * null은 "신호 없음"(미지원·미연결)을 뜻한다.
 */
public final class Frame {
    public final long timeMs;
    public final float speedKmh;
    public final Boolean brakePressed;
    public final Gear gear;
    public final Boolean parkingBrakeOn;
    public final boolean cruiseActive;
    public final Float heartRateBpm;
    public final Float gripScore;
    /** 웨어러블 값이 측정된 시각. 신호가 없으면 -1. */
    public final long wearableTimeMs;

    private Frame(Builder b) {
        this.timeMs = b.timeMs;
        this.speedKmh = b.speedKmh;
        this.brakePressed = b.brakePressed;
        this.gear = b.gear;
        this.parkingBrakeOn = b.parkingBrakeOn;
        this.cruiseActive = b.cruiseActive;
        this.heartRateBpm = b.heartRateBpm;
        this.gripScore = b.gripScore;
        this.wearableTimeMs = b.wearableTimeMs;
    }

    public static Builder builder(long timeMs) {
        return new Builder(timeMs);
    }

    public boolean isBrakeOn() {
        return Boolean.TRUE.equals(brakePressed);
    }

    public boolean isParkingBrakeOn() {
        return Boolean.TRUE.equals(parkingBrakeOn);
    }

    public boolean hasWearable() {
        return heartRateBpm != null && wearableTimeMs >= 0;
    }

    public static final class Builder {
        private final long timeMs;
        private float speedKmh;
        private Boolean brakePressed;
        private Gear gear = Gear.UNKNOWN;
        private Boolean parkingBrakeOn;
        private boolean cruiseActive;
        private Float heartRateBpm;
        private Float gripScore;
        private long wearableTimeMs = -1;

        private Builder(long timeMs) {
            this.timeMs = timeMs;
        }

        public Builder speedKmh(float v) { this.speedKmh = v; return this; }
        public Builder brake(Boolean v) { this.brakePressed = v; return this; }
        public Builder gear(Gear v) { this.gear = v == null ? Gear.UNKNOWN : v; return this; }
        public Builder parkingBrake(Boolean v) { this.parkingBrakeOn = v; return this; }
        public Builder cruise(boolean v) { this.cruiseActive = v; return this; }

        public Builder wearable(Float heartRateBpm, Float gripScore, long measuredAtMs) {
            this.heartRateBpm = heartRateBpm;
            this.gripScore = gripScore;
            this.wearableTimeMs = heartRateBpm == null ? -1 : measuredAtMs;
            return this;
        }

        public Frame build() {
            return new Frame(this);
        }
    }
}
