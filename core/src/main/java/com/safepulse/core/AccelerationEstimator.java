package com.safepulse.core;

import java.util.ArrayDeque;

/**
 * 차속 변화율로 종방향 가속도(g)를 구한다.
 * 서드파티 앱에는 가속 페달·RPM이 공개되지 않으므로 이 값이 감지의 핵심 입력이다.
 * 짧은 창(기본 500ms)의 처음과 끝 속도 차이를 써서 센서 잡음을 줄인다.
 */
public final class AccelerationEstimator {
    public static final double G = 9.80665;

    private final long windowMs;
    private final ArrayDeque<double[]> samples = new ArrayDeque<>();
    private double lastG;

    public AccelerationEstimator(long windowMs) {
        this.windowMs = windowMs;
    }

    /** @return 현재 종방향 가속도(g). 양수=가속, 음수=감속. */
    public double update(long timeMs, double speedMps) {
        samples.addLast(new double[]{timeMs, speedMps});
        while (samples.size() > 2 && timeMs - samples.peekFirst()[0] > windowMs) {
            samples.removeFirst();
        }
        double[] first = samples.peekFirst();
        double dtSec = (timeMs - first[0]) / 1000.0;
        if (dtSec >= 0.15) {
            lastG = (speedMps - first[1]) / dtSec / G;
        }
        return lastG;
    }

    public double lastG() {
        return lastG;
    }

    public void reset() {
        samples.clear();
        lastG = 0;
    }
}
