package com.safepulse.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.safepulse.core.RehearsalSession;

/**
 * 골든타임 리허설. P단 정차 중에만 열린다(DO 미선언 → 주행 중 플랫폼이 실행을 막음).
 * 실제 위기 상황과 같은 음성으로 브레이크 → N → 주차 브레이크 → P 복귀를 지시하고,
 * 차량 신호로 동작을 확인해 반응 시간을 기록한다.
 */
public class RehearsalActivity extends Activity implements SafePulseController.Listener {

    static final String EXTRA_DEMO = "demo";

    private SafePulseController controller;
    private TextView stepView, instructionView, signalsView, resultView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rehearsal);
        controller = SafePulseApp.controller(this);
        stepView = findViewById(R.id.rehearsalStep);
        instructionView = findViewById(R.id.rehearsalInstruction);
        signalsView = findViewById(R.id.rehearsalSignals);
        resultView = findViewById(R.id.rehearsalResult);

        boolean demo = getIntent().getBooleanExtra(EXTRA_DEMO, false);
        View start = findViewById(R.id.rehearsalStart);
        start.setOnClickListener(v -> {
            resultView.setVisibility(View.GONE);
            controller.startRehearsal();
        });
        if (demo) start.setEnabled(false); // 데모 시나리오가 스스로 시작한다
        findViewById(R.id.rehearsalNext).setOnClickListener(v -> controller.confirmRehearsalStep());
        findViewById(R.id.rehearsalStop).setOnClickListener(v -> {
            controller.stopRehearsal();
            finish();
        });
        instructionView.setText("준비되면 시작을 누르세요");
    }

    @Override
    protected void onResume() {
        super.onResume();
        controller.setListener(this);
        controller.setUiVisible(true);
    }

    @Override
    protected void onPause() {
        controller.setUiVisible(false);
        controller.clearListener(this);
        super.onPause();
    }

    @Override
    public void onState(UiState s) {
        RehearsalSession.Step step = s.rehearsalStep;
        if (s.rehearsing && step != null) {
            stepView.setText(stepIndex(step));
            instructionView.setText(instruction(step));
        }
        signalsView.setText(String.format("브레이크 %s · 기어 %s · 주차 브레이크 %s",
                s.brake == null ? "신호 없음" : (s.brake ? "밟음" : "–"),
                s.gear.symbol,
                s.parkingBrake == null ? "신호 없음" : (s.parkingBrake ? "체결" : "해제")));
    }

    @Override
    public void onNotice(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        if (message.startsWith("리허설 완료")) {
            stepView.setText("완료");
            instructionView.setText("잘하셨어요");
            resultView.setText(controller.prefs().rehearsalSummary());
            resultView.setVisibility(View.VISIBLE);
        } else if (message.startsWith("리허설을 멈췄")) {
            stepView.setText("중단");
            instructionView.setText("차가 움직이거나 중단되었습니다");
        }
    }

    private static String stepIndex(RehearsalSession.Step step) {
        switch (step) {
            case BRAKE: return "1 / 3 · " + step.label;
            case NEUTRAL: return "2 / 3 · " + step.label;
            case PARKING_BRAKE: return "3 / 3 · " + step.label;
            default: return step.label;
        }
    }

    private static String instruction(RehearsalSession.Step step) {
        switch (step) {
            case INTRO: return "실제 상황과 같은 안내가 나옵니다";
            case BRAKE: return "두 발로 브레이크를 끝까지";
            case NEUTRAL: return "기어를 N으로";
            case PARKING_BRAKE: return "주차 브레이크를 당기세요";
            case RETURN_P: return "기어를 P로 돌려 주세요";
            case DONE: return "잘하셨어요";
            default: return "";
        }
    }
}
