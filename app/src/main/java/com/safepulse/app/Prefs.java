package com.safepulse.app;

import android.content.Context;
import android.content.SharedPreferences;

import com.safepulse.core.RehearsalSession;

import java.util.Locale;

/** 사용자 설정과 리허설 기록. */
final class Prefs {

    private final SharedPreferences sp;

    Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences("safepulse", Context.MODE_PRIVATE);
    }

    String guardianName() { return sp.getString("guardian_name", ""); }
    String guardianPhone() { return sp.getString("guardian_phone", ""); }
    boolean wearableConsent() { return sp.getBoolean("wearable_consent", true); }
    boolean elderMode() { return sp.getBoolean("elder_mode", false); }

    void save(String guardianName, String guardianPhone, boolean wearableConsent, boolean elderMode) {
        sp.edit()
                .putString("guardian_name", guardianName)
                .putString("guardian_phone", guardianPhone)
                .putBoolean("wearable_consent", wearableConsent)
                .putBoolean("elder_mode", elderMode)
                .apply();
    }

    void saveRehearsal(RehearsalSession.Result r) {
        int count = sp.getInt("rehearsal_count", 0) + 1;
        long best = sp.getLong("rehearsal_best_ms", Long.MAX_VALUE);
        sp.edit()
                .putInt("rehearsal_count", count)
                .putLong("rehearsal_last_ms", r.totalMs())
                .putLong("rehearsal_best_ms", Math.min(best, r.totalMs()))
                .putString("rehearsal_last_detail", String.format(Locale.KOREA,
                        "브레이크 %.1f초 · N %.1f초 · 주차 브레이크 %.1f초",
                        r.brakeMs / 1000.0, r.neutralMs / 1000.0, r.parkingBrakeMs / 1000.0))
                .apply();
    }

    String rehearsalSummary() {
        int count = sp.getInt("rehearsal_count", 0);
        if (count == 0) return "아직 리허설 기록이 없습니다. 정차 중에 한 번 연습해 두세요.";
        return String.format(Locale.KOREA, "리허설 %d회 · 최근 %.1f초 · 최고 %.1f초 (%s)",
                count,
                sp.getLong("rehearsal_last_ms", 0) / 1000.0,
                sp.getLong("rehearsal_best_ms", 0) / 1000.0,
                sp.getString("rehearsal_last_detail", ""));
    }
}
