package com.safepulse.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** 기획서 10장 검증 시나리오를 그대로 자동화한 테스트. */
public class ScenarioTest {

    private static ScenarioSimulation.Result run(String id) throws Exception {
        return ScenarioSimulation.run(Scenario.load(id), new RiskConfig());
    }

    private static void assertInOrder(List<?> actual, Object... expected) {
        int i = 0;
        for (Object a : actual) {
            if (i < expected.length && a == expected[i]) i++;
        }
        assertEquals("expected order " + Arrays.toString(expected) + " in " + actual, expected.length, i);
    }

    @Test
    public void allScenariosLoad() throws Exception {
        for (String id : Scenario.IDS) {
            Scenario s = Scenario.load(id);
            assertTrue(id + " has rows", s.rows.size() > 10);
            assertFalse(id + " has title", s.title.isEmpty());
        }
    }

    @Test
    public void s1_parkingMisapplication_withWatch_intervenesEarlyAndCoachesToSafeStop() throws Exception {
        ScenarioSimulation.Result r = run("s1_parking_misapplication");
        assertEquals(RiskLevel.INTERVENE, r.maxLevel);
        assertTrue("wrist grip should trigger before the 1.5 s sustain rule: " + r.firstEngagedAt,
                r.firstEngagedAt > 1600 && r.firstEngagedAt < 3000);
        assertInOrder(r.steps, CoachingOrchestrator.Step.BRAKE, CoachingOrchestrator.Step.STOPPED, CoachingOrchestrator.Step.SAFE);
        assertTrue(r.prompts.contains(Prompt.BRAKE_GOOD));
        assertTrue("first voice prompt within 1 s of engage", r.promptLatencyMs() >= 0 && r.promptLatencyMs() <= 1000);
        assertEquals("driver answered, so no guardian alert", -1, r.guardianAlertAt);
        assertEquals(RiskLevel.MONITOR, r.finalLevel);
        assertEquals(1, r.eventLogs.size());
        assertTrue(r.eventLogs.get(0).contains("\"outcome\":\"stopped_and_secured\""));
    }

    @Test
    public void s6_noWatch_stillIntervenesBySustainRule_andAlertsGuardian() throws Exception {
        ScenarioSimulation.Result withWatch = run("s1_parking_misapplication");
        ScenarioSimulation.Result r = run("s6_no_watch");
        assertEquals(RiskLevel.INTERVENE, r.maxLevel);
        assertTrue("vehicle-only mode engages later than with watch", r.firstEngagedAt > withWatch.firstEngagedAt);
        assertTrue("engages within ~1.5 s of acceleration: " + r.firstEngagedAt, r.firstEngagedAt < 3800);
        assertTrue("guardian alert after 30 s without response", r.guardianAlertAt > 30000);
        assertTrue(r.prompts.contains(Prompt.GUARDIAN));
    }

    @Test
    public void s2_brakeIneffective_goesCriticalAndSkipsToNeutralAndEpb() throws Exception {
        ScenarioSimulation.Result r = run("s2_brake_ineffective");
        assertEquals(RiskLevel.CRITICAL, r.maxLevel);
        assertEquals(CoachingOrchestrator.Step.NEUTRAL, r.steps.get(0));
        assertInOrder(r.steps, CoachingOrchestrator.Step.NEUTRAL, CoachingOrchestrator.Step.PARKING_BRAKE,
                CoachingOrchestrator.Step.STOPPED, CoachingOrchestrator.Step.SAFE);
        assertTrue(r.promptLatencyMs() >= 0 && r.promptLatencyMs() <= 1000);
    }

    @Test
    public void s3_overtake_isNotInterrupted() throws Exception {
        ScenarioSimulation.Result r = run("s3_overtake");
        assertTrue("max " + r.maxLevel, r.maxLevel.code <= RiskLevel.WATCH.code);
        assertTrue("no voice prompts: " + r.prompts, r.prompts.isEmpty());
    }

    @Test
    public void s4_cruiseAcceleration_isIgnoredEvenWithTightGrip() throws Exception {
        ScenarioSimulation.Result r = run("s4_cruise");
        assertEquals(RiskLevel.MONITOR, r.maxLevel);
        assertTrue(r.prompts.isEmpty());
    }

    @Test
    public void s5_driverCancel_stopsCoachingImmediately_andDoesNotReengage() throws Exception {
        ScenarioSimulation.Result r = run("s5_false_positive_cancel");
        assertTrue(r.firstEngagedAt > 0 && r.firstEngagedAt < r.cancelledAt);
        assertTrue(r.prompts.contains(Prompt.CANCELLED));
        int cancelIdx = r.prompts.indexOf(Prompt.CANCELLED);
        assertTrue("cancel prompt within 1 s", r.promptTimes.get(cancelIdx) - r.cancelledAt <= 1000);
        assertFalse("10 s cooldown after cancel", r.reengagedAfterCancel);
        assertEquals(RiskLevel.MONITOR, r.finalLevel);
        assertEquals(1, r.eventLogs.size());
        assertTrue(r.eventLogs.get(0).contains("\"outcome\":\"cancelled_by_driver\""));
    }

    @Test
    public void s7_highwayPanic_intervenesOnlyWithWatch() throws Exception {
        Scenario s = Scenario.load("s7_highway_panic");
        ScenarioSimulation.Result withWatch = ScenarioSimulation.run(s, new RiskConfig());
        assertEquals(RiskLevel.INTERVENE, withWatch.maxLevel);
        assertTrue("needs 3 s of acceleration at speed: " + withWatch.firstEngagedAt, withWatch.firstEngagedAt >= 5000);

        ScenarioSimulation.Result noWatch = ScenarioSimulation.run(s.withoutWearable(), new RiskConfig());
        assertTrue("without watch, highway acceleration is only logged: " + noWatch.maxLevel,
                noWatch.maxLevel.code <= RiskLevel.WATCH.code);
    }

    @Test
    public void r1_rehearsal_measuresReactionTimes() throws Exception {
        ScenarioSimulation.Result r = run("r1_rehearsal");
        assertNull(r.rehearsalAbort);
        assertNotNull(r.rehearsal);
        assertEquals(1300, r.rehearsal.brakeMs, 100);
        assertEquals(1600, r.rehearsal.neutralMs, 100);
        assertEquals(1200, r.rehearsal.parkingBrakeMs, 100);
        assertFalse(r.rehearsal.anyManual);
        assertEquals(RehearsalSession.Step.DONE, r.rehearsalSteps.get(r.rehearsalSteps.size() - 1));
        assertEquals("rehearsal never triggers crisis coaching", RiskLevel.MONITOR, r.maxLevel);
    }
}
