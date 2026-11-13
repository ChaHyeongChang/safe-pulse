package com.safepulse.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.safepulse.core.CoachingOrchestrator;
import com.safepulse.core.RiskLevel;
import com.safepulse.core.Scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 주행 중에도 쓰는 유일한 화면(Distraction Optimized).
 * 홈: 차량·워치 상태와 큰 비상 버튼. 위기 시: 정적 코칭 화면(아이콘 1개 + 한 줄 + 버튼 2개).
 * 정차 후: 안부 확인, 119·112, 사고 후 가이드.
 */
public class MainActivity extends Activity implements SafePulseController.Listener {

    private static final int REQ_PERMISSIONS = 10;
    private static final String PERM_CAR_SPEED = "android.car.permission.CAR_SPEED";

    private SafePulseController controller;
    private Prefs prefs;

    private View home, coachOverlay, postOverlay;
    private TextView speedValue, gearValue, brakeValue, epbValue, watchValue, levelValue;
    private TextView modeBadge, statusDetail, rehearsalSummary;
    private TextView coachStepIndex, coachIcon, coachText, coachDetail;
    private TextView postDetail, postGuideText;
    private Toast toast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        controller = SafePulseApp.controller(this);
        prefs = controller.prefs();

        home = findViewById(R.id.home);
        coachOverlay = findViewById(R.id.coachOverlay);
        postOverlay = findViewById(R.id.postOverlay);
        speedValue = findViewById(R.id.speedValue);
        gearValue = findViewById(R.id.gearValue);
        brakeValue = findViewById(R.id.brakeValue);
        epbValue = findViewById(R.id.epbValue);
        watchValue = findViewById(R.id.watchValue);
        levelValue = findViewById(R.id.levelValue);
        modeBadge = findViewById(R.id.modeBadge);
        statusDetail = findViewById(R.id.statusDetail);
        rehearsalSummary = findViewById(R.id.rehearsalSummary);
        coachStepIndex = findViewById(R.id.coachStepIndex);
        coachIcon = findViewById(R.id.coachIcon);
        coachText = findViewById(R.id.coachText);
        coachDetail = findViewById(R.id.coachDetail);
        postDetail = findViewById(R.id.postDetail);
        postGuideText = findViewById(R.id.postGuideText);

        findViewById(R.id.emergencyButton).setOnClickListener(v -> controller.manualEmergency());
        findViewById(R.id.coachCancel).setOnClickListener(v -> controller.cancel());
        findViewById(R.id.coachCall119).setOnClickListener(v -> dial("119"));
        findViewById(R.id.postOk).setOnClickListener(v -> controller.respondOk());
        findViewById(R.id.post119).setOnClickListener(v -> dial("119"));
        findViewById(R.id.post112).setOnClickListener(v -> dial("112"));
        findViewById(R.id.postGuide).setOnClickListener(v ->
                postGuideText.setVisibility(postGuideText.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        findViewById(R.id.rehearsalButton).setOnClickListener(v -> openWhenParked(RehearsalActivity.class));
        findViewById(R.id.logsButton).setOnClickListener(v -> openWhenParked(EventLogActivity.class));
        findViewById(R.id.settingsButton).setOnClickListener(v -> openWhenParked(SettingsActivity.class));
        findViewById(R.id.demoButton).setOnClickListener(v -> showDemoMenu());

        startMonitoring();
        requestNeededPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        controller.setListener(this);
        controller.setUiVisible(true);
        controller.applySettings();
        applyElderMode(prefs.elderMode());
        rehearsalSummary.setText(prefs.rehearsalSummary());
    }

    @Override
    protected void onPause() {
        controller.setUiVisible(false);
        controller.clearListener(this);
        super.onPause();
    }

    private void startMonitoring() {
        controller.start();
        try {
            startForegroundService(new Intent(this, SafePulseService.class));
        } catch (Exception e) {
            // 서비스가 막혀도 앱이 열려 있는 동안은 감시가 계속된다.
            toast("백그라운드 감시를 시작하지 못했습니다. 앱을 열어 둔 동안만 감시합니다.");
        }
    }

    private void requestNeededPermissions() {
        List<String> missing = new ArrayList<>();
        if (checkSelfPermission(PERM_CAR_SPEED) != PackageManager.PERMISSION_GRANTED) missing.add(PERM_CAR_SPEED);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), REQ_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS) controller.restartCarSignals();
    }

    // ------------------------------------------------------------------ 상태 그리기

    @Override
    public void onState(UiState s) {
        speedValue.setText(String.format(Locale.US, "%.0f", s.speedKmh));
        gearValue.setText(s.gear.symbol);
        brakeValue.setText(s.brake == null ? "신호 없음" : (s.brake ? "밟음" : "–"));
        epbValue.setText(s.parkingBrake == null ? "–" : (s.parkingBrake ? "체결" : "해제"));
        watchValue.setText(watchText(s));
        levelValue.setText(s.level.label);
        levelValue.setTextColor(getColor(levelColor(s.level)));

        modeBadge.setText(s.scenarioRunning
                ? String.format(Locale.KOREA, "데모 %d%% · %s", s.scenarioProgressPct, s.scenarioTitle)
                : "실시간 차량");
        statusDetail.setText(String.format(Locale.KOREA, "가속도 %+.2fg · 차량 신호 %s · Pleos 브레이크 %s%s%s",
                s.accelG,
                s.carApiConnected ? "연결" : "없음",
                s.pleosBrakeConnected ? "연결" : "미연결",
                s.cruise ? " · 크루즈 작동 중" : "",
                s.recording ? " · 기록 중" : ""));

        boolean engaged = s.level.isEngaged() && !s.rehearsing;
        coachOverlay.setVisibility(engaged ? View.VISIBLE : View.GONE);
        postOverlay.setVisibility(s.level == RiskLevel.POST ? View.VISIBLE : View.GONE);
        home.setVisibility(engaged || s.level == RiskLevel.POST ? View.INVISIBLE : View.VISIBLE);
        if (s.level != RiskLevel.POST) postGuideText.setVisibility(View.GONE);

        if (engaged) renderCoaching(s);
        if (s.level == RiskLevel.POST) renderPost(s);
    }

    private void renderCoaching(UiState s) {
        CoachingOrchestrator.Step step = s.step;
        int n = Math.max(1, Math.min(4, step.number));
        coachStepIndex.setText(String.format(Locale.KOREA, "%d / 4 · %s%s", n, step.label,
                s.level == RiskLevel.CRITICAL ? " · 긴급" : ""));
        coachIcon.setText(iconText(step));
        coachText.setText(s.lastPrompt != null ? s.lastPrompt.text : "");
        coachDetail.setText("감지: " + reasonText(s.engageReason));
    }

    private void renderPost(UiState s) {
        String guardian = prefs.guardianName().isEmpty() ? "등록된 보호자" : prefs.guardianName();
        if (s.guardianAlerted) {
            postDetail.setText(guardian + "에게 현재 위치와 함께 알렸습니다.");
        } else if (s.wellnessRemainingMs >= 0) {
            postDetail.setText(String.format(Locale.KOREA,
                    "괜찮으시면 '괜찮아요'를 눌러 주세요.\n%d초 동안 응답이 없으면 %s에게 알립니다.",
                    (s.wellnessRemainingMs + 999) / 1000, guardian));
        } else {
            postDetail.setText("기어 P, 비상등을 켜 주세요.");
        }
    }

    private static String iconText(CoachingOrchestrator.Step step) {
        switch (step) {
            case BRAKE: return "두 발\n브레이크";
            case NEUTRAL: return "N";
            case PARKING_BRAKE: return "주차\n브레이크";
            case STOPPED: return "P";
            default: return "!";
        }
    }

    private static String reasonText(String reason) {
        if (reason == null) return "";
        switch (reason) {
            case "wrist_grip": return "저속 급가속 + 손목 경직";
            case "heart_rate_spike": return "저속 급가속 + 심박 급상승";
            case "sustained_low_speed_acceleration": return "저속 급가속 1.5초 지속";
            case "panic_with_sustained_acceleration": return "가속 3초 지속 + 패닉 신호";
            case "brake_on_but_accelerating": return "브레이크를 밟았는데도 가속";
            case "manual": return "비상 버튼";
            default: return reason;
        }
    }

    private static String watchText(UiState s) {
        if (s.heartRate == null) return "미연결";
        if (!s.wearableFresh) return "신호 지연";
        String grip = s.grip == null ? "" : String.format(Locale.KOREA, " · 경직 %.1f", s.grip);
        return String.format(Locale.KOREA, "♥%.0f%s", s.heartRate, grip);
    }

    private static int levelColor(RiskLevel level) {
        switch (level) {
            case WATCH: return R.color.warn;
            case INTERVENE:
            case CRITICAL: return R.color.danger;
            case POST: return R.color.ok;
            default: return R.color.text;
        }
    }

    private void applyElderMode(boolean elder) {
        coachText.setTextSize(elder ? 72 : 60);
        coachIcon.setTextSize(elder ? 64 : 56);
        postDetail.setTextSize(elder ? 34 : 28);
    }

    // ------------------------------------------------------------------ 메뉴·이동

    private void openWhenParked(Class<? extends Activity> target) {
        UiState s = controller.lastState();
        if (!s.isParked() && !s.scenarioRunning) {
            toast(getString(R.string.parked_only));
            return;
        }
        startActivity(new Intent(this, target));
    }

    private void showDemoMenu() {
        UiState s = controller.lastState();
        if (!s.isParked() && !s.scenarioRunning) {
            toast(getString(R.string.parked_only));
            return;
        }
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        int i = 1;
        for (String id : Scenario.IDS) {
            String title;
            try {
                title = Scenario.load(id).title;
            } catch (Exception e) {
                title = id;
            }
            final String scenarioId = id;
            labels.add((id.startsWith("r") ? "R" : String.valueOf(i++)) + ". " + title);
            actions.add(() -> {
                controller.startScenario(scenarioId, true);
                if (scenarioId.startsWith("r")) {
                    startActivity(new Intent(this, RehearsalActivity.class).putExtra(RehearsalActivity.EXTRA_DEMO, true));
                }
            });
        }
        labels.add("비교. 7번과 같은 주행, 워치 없이");
        actions.add(() -> controller.startScenario("s7_highway_panic", false));
        labels.add(getString(R.string.btn_live));
        actions.add(() -> controller.stopScenario());

        new AlertDialog.Builder(this)
                .setTitle(R.string.btn_demo)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    private void dial(String number) {
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)));
        } catch (ActivityNotFoundException e) {
            toast("전화 앱을 열 수 없습니다. 휴대폰으로 " + number + "에 연락하세요.");
        }
    }

    @Override
    public void onNotice(String message) {
        toast(message);
    }

    private void toast(String message) {
        if (toast != null) toast.cancel();
        toast = Toast.makeText(this, message, Toast.LENGTH_LONG);
        toast.show();
    }
}
