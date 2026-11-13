package com.safepulse.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 시나리오를 실제 시간 없이 빠르게 돌리는 헤드리스 실행기.
 * 앱과 같은 SafePulseCore를 100ms 간격으로 호출하므로, 테스트 결과가 앱 동작과 같다.
 */
public final class ScenarioSimulation {

    public static final long TICK_MS = 100;

    public static final class Result {
        public final List<String> transitions = new ArrayList<>();
        public final List<Prompt> prompts = new ArrayList<>();
        public final List<Long> promptTimes = new ArrayList<>();
        public final List<CoachingOrchestrator.Step> steps = new ArrayList<>();
        public final List<RehearsalSession.Step> rehearsalSteps = new ArrayList<>();
        public final List<String> eventLogs = new ArrayList<>();
        /** 도달한 가장 높은 위기 단계(사후 단계 POST는 제외). */
        public RiskLevel maxLevel = RiskLevel.MONITOR;
        public boolean reachedPost;
        public RiskLevel finalLevel = RiskLevel.MONITOR;
        public long firstEngagedAt = -1;
        public long firstPromptAfterEngageAt = -1;
        public long cancelledAt = -1;
        public long guardianAlertAt = -1;
        public RehearsalSession.Result rehearsal;
        public String rehearsalAbort;
        /** 취소 이후 다시 개입한 적이 있는지. */
        public boolean reengagedAfterCancel;

        /** 2단계 진입부터 첫 음성 지시까지(ms). */
        public long promptLatencyMs() {
            return firstEngagedAt < 0 || firstPromptAfterEngageAt < 0 ? -1 : firstPromptAfterEngageAt - firstEngagedAt;
        }
    }

    public static Result run(Scenario s, RiskConfig cfg) {
        return run(s, cfg, 3000);
    }

    /** @param tailMs 마지막 프레임 이후에도 그 값을 유지하며 더 돌릴 시간 */
    public static Result run(Scenario s, RiskConfig cfg, long tailMs) {
        final Result r = new Result();
        final SafePulseCore core = new SafePulseCore(cfg, new SafePulseCore.OutputAdapter() {
            @Override public void onLevelChanged(RiskLevel from, RiskLevel to, String reason, long t) {
                r.transitions.add(t + ":" + from + "->" + to + "(" + reason + ")");
                if (to == RiskLevel.POST) r.reachedPost = true;
                else if (to.code > r.maxLevel.code) r.maxLevel = to;
                if (to.isEngaged() && r.firstEngagedAt < 0) r.firstEngagedAt = t;
                if (to.isEngaged() && r.cancelledAt >= 0) r.reengagedAfterCancel = true;
            }
            @Override public void onPrompt(Prompt p, boolean interrupt, long t) {
                r.prompts.add(p);
                r.promptTimes.add(t);
                if (r.firstEngagedAt >= 0 && r.firstPromptAfterEngageAt < 0 && interrupt) r.firstPromptAfterEngageAt = t;
            }
            @Override public void onStepChanged(CoachingOrchestrator.Step from, CoachingOrchestrator.Step to, long t) {
                r.steps.add(to);
            }
            @Override public void onGuardianAlert(long t) {
                r.guardianAlertAt = t;
            }
            @Override public void onEventLog(String json, EventRecorder.Summary summary) {
                r.eventLogs.add(json);
            }
            @Override public void onRehearsalStep(RehearsalSession.Step step, long t) {
                r.rehearsalSteps.add(step);
            }
            @Override public void onRehearsalFinished(RehearsalSession.Result result, long t) {
                r.rehearsal = result;
            }
            @Override public void onRehearsalAborted(String reason, long t) {
                r.rehearsalAbort = reason;
            }
        });

        List<Scenario.Row> rows = s.rows;
        int idx = 0;
        Scenario.Row current = rows.get(0);
        long end = s.durationMs() + tailMs;
        for (long t = 0; t <= end; t += TICK_MS) {
            while (idx < rows.size() && rows.get(idx).t <= t) {
                current = rows.get(idx);
                idx++;
                applyAction(core, current.action, t, r);
            }
            core.tick(current.toFrameAt(t));
        }
        core.flush();
        r.finalLevel = core.level();
        return r;
    }

    private static void applyAction(SafePulseCore core, String action, long t, Result r) {
        if (action == null || action.isEmpty()) return;
        switch (action) {
            case "cancel": core.cancel(t); r.cancelledAt = t; break;
            case "respond": core.respondOk(t); break;
            case "manual": core.manualEmergency(t); break;
            case "rehearsal": core.startRehearsal(t); break;
            case "confirm": core.confirmRehearsalStepManually(); break;
            default: break;
        }
    }
}
