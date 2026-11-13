package com.safepulse.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RiskEngineTest {

    private static final double KMH_PER_G_PER_TICK = AccelerationEstimator.G * 3.6 * 0.1;

    /** 일정한 가속도(g)로 속도를 적분하며 엔진에 넣는다. */
    private static float drive(RiskEngine e, long fromMs, long toMs, float speed, double g,
                               Boolean brake, Gear gear, Float hr, Float grip, long wearableAgeMs) {
        for (long t = fromMs; t < toMs; t += 100) {
            speed = (float) Math.max(0, speed + g * KMH_PER_G_PER_TICK);
            e.update(Frame.builder(t).speedKmh(speed).brake(brake).gear(gear).parkingBrake(false)
                    .wearable(hr, grip, t - wearableAgeMs).build());
        }
        return speed;
    }

    @Test
    public void staleWearableSignal_isIgnored() {
        RiskConfig cfg = new RiskConfig();
        RiskEngine e = new RiskEngine(cfg);
        // 70km/h 정속, 워치 값은 5초 전 측정값(오래됨) → 패닉으로 쓰지 않아야 한다
        float v = drive(e, 0, 2000, 70, 0, false, Gear.DRIVE, 75f, 0.1f, 5000);
        drive(e, 2000, 7000, v, 0.32, false, Gear.DRIVE, 120f, 0.95f, 5000);
        assertTrue("stale panic must not intervene at speed: " + e.level(), e.level().code <= RiskLevel.WATCH.code);
    }

    @Test
    public void freshWearablePanic_atSpeed_intervenesAfterThreeSeconds() {
        RiskEngine e = new RiskEngine(new RiskConfig());
        float v = drive(e, 0, 2000, 70, 0, false, Gear.DRIVE, 75f, 0.1f, 0);
        v = drive(e, 2000, 4500, v, 0.32, false, Gear.DRIVE, 120f, 0.95f, 0);
        assertNotEquals("not yet: under 3 s", RiskLevel.INTERVENE, e.level());
        drive(e, 4500, 6000, v, 0.32, false, Gear.DRIVE, 120f, 0.95f, 0);
        assertEquals(RiskLevel.INTERVENE, e.level());
    }

    @Test
    public void downhillLightBraking_isNotCritical() {
        RiskEngine e = new RiskEngine(new RiskConfig());
        // 브레이크를 밟은 채 내리막에서 0.05g로 속도가 조금 오른다 → 0.1g 미만이라 긴급 아님
        drive(e, 0, 5000, 30, 0.05, true, Gear.DRIVE, null, null, 0);
        assertEquals(RiskLevel.MONITOR, e.level());
    }

    @Test
    public void manualTrigger_thenStopAndPark_goesToPost() {
        RiskEngine e = new RiskEngine(new RiskConfig());
        drive(e, 0, 1000, 40, 0, false, Gear.DRIVE, null, null, 0);
        e.manualTrigger(1000);
        assertEquals(RiskLevel.INTERVENE, e.level());
        drive(e, 1000, 4000, 40, -0.45, true, Gear.DRIVE, null, null, 0);
        assertEquals("stopped in D: still coaching", RiskLevel.INTERVENE, e.level());
        drive(e, 4000, 5000, 0, 0, true, Gear.PARK, null, null, 0);
        assertEquals(RiskLevel.POST, e.level());
        e.acknowledgePost(5000);
        assertEquals(RiskLevel.MONITOR, e.level());
    }

    @Test
    public void engagedLevel_latchesAfterFootLifted() {
        RiskEngine e = new RiskEngine(new RiskConfig());
        drive(e, 0, 500, 0, 0, true, Gear.PARK, null, null, 0);
        float v = drive(e, 500, 3500, 0, 0.38, false, Gear.DRIVE, null, null, 0);
        assertEquals(RiskLevel.INTERVENE, e.level());
        // 발을 떼 가속이 멈춰도 정차 전까지는 개입 유지
        drive(e, 3500, 5000, v, -0.02, false, Gear.DRIVE, null, null, 0);
        assertEquals(RiskLevel.INTERVENE, e.level());
    }

    @Test
    public void appStartedWhileDriving_isNotTreatedAsFreshShift() {
        // 앱이 70km/h 주행 중에 켜져 처음 받은 기어가 D인 경우: 출차 직후 규칙을 적용하면 안 된다
        RiskEngine e = new RiskEngine(new RiskConfig());
        drive(e, 0, 5000, 70, 0.32, false, Gear.DRIVE, null, null, 0);
        assertTrue("got " + e.level(), e.level().code <= RiskLevel.WATCH.code);
    }

    @Test
    public void shiftFromParkToDrive_opensLowSpeedWindow() {
        RiskEngine e = new RiskEngine(new RiskConfig());
        drive(e, 0, 500, 0, 0, true, Gear.PARK, null, null, 0);
        drive(e, 500, 600, 0, 0, true, Gear.DRIVE, null, null, 0);
        assertTrue(e.isLowSpeedContext());
    }

    @Test
    public void accelerationEstimator_matchesConstantAccel() {
        AccelerationEstimator est = new AccelerationEstimator(500);
        double g = 0;
        for (int i = 0; i <= 20; i++) {
            double mps = 0.3 * AccelerationEstimator.G * (i * 0.1);
            g = est.update(i * 100L, mps);
        }
        assertEquals(0.3, g, 0.01);
    }
}
