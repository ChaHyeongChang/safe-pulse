package com.safepulse.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 이벤트 기록 열람(정차 중에만). 감지 전 30초 ~ 정차 후 30초 타임라인을 보여 준다. */
public class EventLogActivity extends Activity {

    private EventLogStore store;
    private TextView detail;
    private File[] files = new File[0];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_log);
        store = SafePulseApp.controller(this).logStore();
        detail = findViewById(R.id.logDetail);
        ListView list = findViewById(R.id.logsList);
        list.setOnItemClickListener((parent, view, position, id) -> showDetail(files[position]));
    }

    @Override
    protected void onResume() {
        super.onResume();
        files = store.list();
        List<String> rows = new ArrayList<>();
        for (File f : files) rows.add(summaryLine(f));
        ListView list = findViewById(R.id.logsList);
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows));
        findViewById(R.id.logsEmpty).setVisibility(files.length == 0 ? View.VISIBLE : View.GONE);
        if (files.length > 0) showDetail(files[0]);
    }

    private String summaryLine(File f) {
        String when = f.getName().replace("event_", "").replace(".json", "");
        try {
            JSONObject o = new JSONObject(store.read(f));
            return String.format(Locale.KOREA, "%s · %s · %s", pretty(when),
                    levelLabel(o.optString("maxLevel")), outcomeLabel(o.optString("outcome")));
        } catch (Exception e) {
            return when;
        }
    }

    private void showDetail(File f) {
        String raw = store.read(f);
        try {
            JSONObject o = new JSONObject(raw);
            StringBuilder sb = new StringBuilder();
            sb.append("파일: ").append(f.getName()).append('\n');
            sb.append("시작 원인: ").append(o.optString("trigger")).append('\n');
            sb.append("최고 단계: ").append(levelLabel(o.optString("maxLevel"))).append('\n');
            sb.append("결과: ").append(outcomeLabel(o.optString("outcome"))).append("\n\n");
            sb.append("[단계 전이]\n");
            JSONArray tr = o.optJSONArray("transitions");
            for (int i = 0; tr != null && i < tr.length(); i++) {
                JSONObject t = tr.getJSONObject(i);
                sb.append(String.format(Locale.US, "%6.1fs  %s → %s  (%s)\n",
                        t.optLong("t") / 1000.0, t.optString("from"), t.optString("to"), t.optString("reason")));
            }
            sb.append("\n[음성 지시]\n");
            JSONArray pr = o.optJSONArray("prompts");
            for (int i = 0; pr != null && i < pr.length(); i++) {
                JSONObject p = pr.getJSONObject(i);
                sb.append(String.format(Locale.US, "%6.1fs  %s\n", p.optLong("t") / 1000.0, p.optString("text")));
            }
            JSONArray samples = o.optJSONArray("samples");
            sb.append("\n[신호 샘플] ").append(samples == null ? 0 : samples.length()).append("개 (100ms 간격)\n");
            sb.append("\n").append(o.toString(2));
            detail.setText(sb.toString());
        } catch (Exception e) {
            detail.setText(raw);
        }
    }

    private static String pretty(String stamp) {
        // 20261112_013000 → 11/12 01:30:00
        if (stamp.length() < 15) return stamp;
        return stamp.substring(4, 6) + "/" + stamp.substring(6, 8) + " "
                + stamp.substring(9, 11) + ":" + stamp.substring(11, 13) + ":" + stamp.substring(13, 15);
    }

    private static String levelLabel(String level) {
        switch (level) {
            case "WATCH": return "주의";
            case "INTERVENE": return "개입";
            case "CRITICAL": return "긴급";
            default: return level;
        }
    }

    private static String outcomeLabel(String outcome) {
        switch (outcome) {
            case "stopped_and_secured": return "정차 완료";
            case "cancelled_by_driver": return "운전자 취소";
            case "timeout": return "기록 시간 초과";
            default: return outcome;
        }
    }
}
