package com.safepulse.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

/** 보호자 연락처, 생체 신호 동의, 고령 모드, 기록 삭제(정차 중에만). */
public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        SafePulseController controller = SafePulseApp.controller(this);
        Prefs prefs = controller.prefs();

        EditText name = findViewById(R.id.guardianName);
        EditText phone = findViewById(R.id.guardianPhone);
        Switch wearable = findViewById(R.id.wearableSwitch);
        Switch elder = findViewById(R.id.elderSwitch);
        name.setText(prefs.guardianName());
        phone.setText(prefs.guardianPhone());
        wearable.setChecked(prefs.wearableConsent());
        elder.setChecked(prefs.elderMode());

        findViewById(R.id.saveButton).setOnClickListener(v -> {
            prefs.save(name.getText().toString().trim(), phone.getText().toString().trim(),
                    wearable.isChecked(), elder.isChecked());
            controller.applySettings();
            Toast.makeText(this, "저장했습니다.", Toast.LENGTH_SHORT).show();
            finish();
        });
        findViewById(R.id.deleteLogsButton).setOnClickListener(v -> {
            int n = controller.logStore().deleteAll();
            Toast.makeText(this, "기록 " + n + "개를 삭제했습니다.", Toast.LENGTH_SHORT).show();
        });
    }
}
