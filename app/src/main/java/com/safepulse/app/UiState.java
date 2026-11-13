package com.safepulse.app;

import com.safepulse.core.CoachingOrchestrator;
import com.safepulse.core.Gear;
import com.safepulse.core.Prompt;
import com.safepulse.core.RehearsalSession;
import com.safepulse.core.RiskLevel;

/** 화면에 그릴 값의 스냅샷. 엔진 스레드에서 만들어 메인 스레드로 넘긴다. */
public final class UiState {
    public float speedKmh;
    public Gear gear = Gear.UNKNOWN;
    public Boolean brake;
    public Boolean parkingBrake;
    public boolean cruise;
    public Float heartRate;
    public Float grip;
    public boolean wearableFresh;
    public double accelG;
    public boolean panic;

    public RiskLevel level = RiskLevel.MONITOR;
    public CoachingOrchestrator.Step step = CoachingOrchestrator.Step.IDLE;
    public Prompt lastPrompt;
    public String engageReason = "";
    public long wellnessRemainingMs = -1;
    public boolean guardianAlerted;
    public boolean recording;

    public boolean rehearsing;
    public RehearsalSession.Step rehearsalStep;

    public boolean scenarioRunning;
    public String scenarioTitle = "";
    public int scenarioProgressPct;

    public boolean carApiConnected;
    public boolean pleosBrakeConnected;

    public boolean isParked() {
        return gear == Gear.PARK && speedKmh < 1f;
    }
}
