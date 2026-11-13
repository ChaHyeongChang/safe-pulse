package com.safepulse.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * 이벤트 로그(기획서 4장 ⑤). 평소에는 최근 30초만 메모리에 두고 저장하지 않는다.
 * 2단계 이상이 되면 감지 전 30초부터 정차 후 30초까지를 하나의 타임라인(JSON)으로 묶는다.
 */
public final class EventRecorder {

    public interface Listener {
        void onEventCompleted(String json, Summary summary);
    }

    public static final class Summary {
        public final long startedAtMs;
        public final String trigger;
        public final RiskLevel maxLevel;
        public final String outcome;
        public final float peakSpeedKmh;
        public final int sampleCount;

        Summary(long startedAtMs, String trigger, RiskLevel maxLevel, String outcome, float peakSpeedKmh, int sampleCount) {
            this.startedAtMs = startedAtMs;
            this.trigger = trigger;
            this.maxLevel = maxLevel;
            this.outcome = outcome;
            this.peakSpeedKmh = peakSpeedKmh;
            this.sampleCount = sampleCount;
        }
    }

    private static final class Sample {
        final Frame f;
        final double accelG;
        final RiskLevel level;
        final CoachingOrchestrator.Step step;

        Sample(Frame f, double accelG, RiskLevel level, CoachingOrchestrator.Step step) {
            this.f = f;
            this.accelG = accelG;
            this.level = level;
            this.step = step;
        }
    }

    private final RiskConfig cfg;
    private Listener listener;
    private final ArrayDeque<Sample> ring = new ArrayDeque<>();
    private final List<Sample> capture = new ArrayList<>();
    private final List<String> transitions = new ArrayList<>();
    private final List<String> prompts = new ArrayList<>();
    private boolean capturing;
    private long captureStartAt;
    private long engagedAt;
    private long finishAt = -1;
    private String trigger = "";
    private String outcome = "timeout";
    private RiskLevel maxLevel = RiskLevel.MONITOR;

    public EventRecorder(RiskConfig cfg) {
        this.cfg = cfg;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void add(Frame f, double accelG, RiskLevel level, CoachingOrchestrator.Step step) {
        Sample s = new Sample(f, accelG, level, step);
        ring.addLast(s);
        while (!ring.isEmpty() && f.timeMs - ring.peekFirst().f.timeMs > cfg.recordPreMs) {
            ring.removeFirst();
        }
        if (capturing) {
            capture.add(s);
            if (level != RiskLevel.POST && level.code > maxLevel.code) maxLevel = level;
            boolean timedOut = f.timeMs - engagedAt >= cfg.recordMaxMs;
            if ((finishAt >= 0 && f.timeMs >= finishAt) || timedOut) {
                finish();
            }
        }
    }

    public void onLevelChanged(RiskLevel from, RiskLevel to, String reason, long t) {
        if (to.isEngaged() && !capturing) {
            capturing = true;
            engagedAt = t;
            trigger = reason;
            maxLevel = to;
            outcome = "timeout";
            finishAt = -1;
            capture.clear();
            transitions.clear();
            prompts.clear();
            capture.addAll(ring);
            captureStartAt = capture.isEmpty() ? t : capture.get(0).f.timeMs;
        }
        if (!capturing) return;
        if (to != RiskLevel.POST && to.code > maxLevel.code) maxLevel = to;
        transitions.add(String.format(Locale.US, "{\"t\":%d,\"from\":\"%s\",\"to\":\"%s\",\"reason\":\"%s\"}",
                t - captureStartAt, from.name(), to.name(), escape(reason)));
        if (to == RiskLevel.POST) {
            outcome = "stopped_and_secured";
            finishAt = t + cfg.recordPostMs;
        } else if (to == RiskLevel.MONITOR && from.isEngaged()) {
            outcome = "cancelled_by_driver";
            finishAt = t + 5000;
        }
    }

    public void onPrompt(Prompt p, long t) {
        if (!capturing) return;
        prompts.add(String.format(Locale.US, "{\"t\":%d,\"key\":\"%s\",\"text\":\"%s\"}",
                t - captureStartAt, p.key, escape(p.text)));
    }

    /** 앱 종료 등으로 강제로 마무리할 때. */
    public void flush() {
        if (capturing) finish();
    }

    public boolean isCapturing() {
        return capturing;
    }

    private void finish() {
        capturing = false;
        float peak = 0;
        StringBuilder sb = new StringBuilder(capture.size() * 96 + 512);
        sb.append("{\"version\":1");
        sb.append(",\"trigger\":\"").append(escape(trigger)).append('"');
        sb.append(",\"outcome\":\"").append(outcome).append('"');
        sb.append(",\"maxLevel\":\"").append(maxLevel.name()).append('"');
        sb.append(",\"engagedAtMs\":").append(engagedAt - captureStartAt);
        sb.append(",\"transitions\":[").append(String.join(",", transitions)).append(']');
        sb.append(",\"prompts\":[").append(String.join(",", prompts)).append(']');
        sb.append(",\"samples\":[");
        Iterator<Sample> it = capture.iterator();
        boolean first = true;
        while (it.hasNext()) {
            Sample s = it.next();
            Frame f = s.f;
            peak = Math.max(peak, f.speedKmh);
            if (!first) sb.append(',');
            first = false;
            sb.append(String.format(Locale.US,
                    "{\"t\":%d,\"kmh\":%.1f,\"g\":%.3f,\"brake\":%s,\"gear\":\"%s\",\"epb\":%s,\"cruise\":%s,\"hr\":%s,\"grip\":%s,\"level\":%d,\"step\":%d}",
                    f.timeMs - captureStartAt, f.speedKmh, s.accelG,
                    jsonBool(f.brakePressed), f.gear.symbol, jsonBool(f.parkingBrakeOn), f.cruiseActive,
                    f.heartRateBpm == null ? "null" : String.format(Locale.US, "%.0f", f.heartRateBpm),
                    f.gripScore == null ? "null" : String.format(Locale.US, "%.2f", f.gripScore),
                    s.level.code, s.step.number));
        }
        sb.append("]}");
        Summary summary = new Summary(engagedAt, trigger, maxLevel, outcome, peak, capture.size());
        capture.clear();
        if (listener != null) listener.onEventCompleted(sb.toString(), summary);
    }

    private static String jsonBool(Boolean b) {
        return b == null ? "null" : b.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
