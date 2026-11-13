package com.safepulse.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 데모·테스트용 주행 시나리오. CSV 한 줄이 100ms 한 프레임이다.
 *
 * <pre>
 * # title: 주차장 출차 중 페달 오조작
 * # description: ...
 * # expect: INTERVENE
 * t_ms,speed_kmh,brake,gear,epb,cruise,hr,grip,action
 * 0,0.0,1,P,0,0,74,0.10,
 * </pre>
 * brake·epb·hr·grip에 '-'를 쓰면 "신호 없음"이다. action은 cancel·respond·manual·rehearsal·confirm 중 하나.
 */
public final class Scenario {

    /** 앱 데모 메뉴와 테스트가 쓰는 시나리오 목록(표시 순서). */
    public static final String[] IDS = {
            "s1_parking_misapplication",
            "s2_brake_ineffective",
            "s3_overtake",
            "s4_cruise",
            "s5_false_positive_cancel",
            "s6_no_watch",
            "s7_highway_panic",
            "r1_rehearsal"
    };

    public static final class Row {
        public final long t;
        public final float speedKmh;
        public final Boolean brake;
        public final Gear gear;
        public final Boolean epb;
        public final boolean cruise;
        public final Float hr;
        public final Float grip;
        public final String action;

        Row(long t, float speedKmh, Boolean brake, Gear gear, Boolean epb, boolean cruise, Float hr, Float grip, String action) {
            this.t = t;
            this.speedKmh = speedKmh;
            this.brake = brake;
            this.gear = gear;
            this.epb = epb;
            this.cruise = cruise;
            this.hr = hr;
            this.grip = grip;
            this.action = action;
        }

        /** 주어진 시각의 Frame으로 바꾼다. 웨어러블 측정 시각도 같은 시각으로 둔다. */
        public Frame toFrameAt(long time) {
            return Frame.builder(time).speedKmh(speedKmh).brake(brake).gear(gear).parkingBrake(epb)
                    .cruise(cruise).wearable(hr, grip, time).build();
        }
    }

    public final String id;
    public final String title;
    public final String description;
    public final String expect;
    public final List<Row> rows;

    private Scenario(String id, String title, String description, String expect, List<Row> rows) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.expect = expect;
        this.rows = Collections.unmodifiableList(rows);
    }

    /** 같은 주행에서 워치 신호만 뺀 시나리오. "워치가 있을 때와 없을 때" 비교 데모에 쓴다. */
    public Scenario withoutWearable() {
        List<Row> copy = new ArrayList<>(rows.size());
        for (Row r : rows) {
            copy.add(new Row(r.t, r.speedKmh, r.brake, r.gear, r.epb, r.cruise, null, null, r.action));
        }
        return new Scenario(id + "_no_watch", title + " (워치 없음)", description, expect, copy);
    }

    public long durationMs() {
        return rows.isEmpty() ? 0 : rows.get(rows.size() - 1).t;
    }

    /** 클래스패스(/scenarios/{id}.csv)에서 읽는다. JVM 테스트와 Android APK 모두에서 동작한다. */
    public static Scenario load(String id) throws IOException {
        InputStream in = Scenario.class.getResourceAsStream("/scenarios/" + id + ".csv");
        if (in == null) throw new IOException("scenario not found: " + id);
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return parse(id, r);
        }
    }

    public static Scenario parse(String id, Reader reader) throws IOException {
        BufferedReader br = new BufferedReader(reader);
        String title = id, description = "", expect = "";
        List<Row> rows = new ArrayList<>();
        String line;
        boolean header = true;
        int lineNo = 0;
        while ((line = br.readLine()) != null) {
            lineNo++;
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                String body = line.substring(1).trim();
                int colon = body.indexOf(':');
                if (colon > 0) {
                    String key = body.substring(0, colon).trim();
                    String value = body.substring(colon + 1).trim();
                    if (key.equals("title")) title = value;
                    else if (key.equals("description")) description = value;
                    else if (key.equals("expect")) expect = value;
                }
                continue;
            }
            if (header) {
                header = false;
                if (line.startsWith("t_ms")) continue;
            }
            String[] c = line.split(",", -1);
            if (c.length < 8) throw new IOException(id + ": line " + lineNo + " has " + c.length + " columns");
            try {
                rows.add(new Row(
                        Long.parseLong(c[0].trim()),
                        Float.parseFloat(c[1].trim()),
                        parseBool(c[2]),
                        Gear.parse(c[3]),
                        parseBool(c[4]),
                        Boolean.TRUE.equals(parseBool(c[5])),
                        parseFloat(c[6]),
                        parseFloat(c[7]),
                        c.length > 8 ? c[8].trim() : ""));
            } catch (NumberFormatException e) {
                throw new IOException(id + ": line " + lineNo + " " + e.getMessage(), e);
            }
        }
        return new Scenario(id, title, description, expect, rows);
    }

    private static Boolean parseBool(String s) {
        s = s.trim();
        if (s.isEmpty() || s.equals("-")) return null;
        return s.equals("1") || s.equalsIgnoreCase("true");
    }

    private static Float parseFloat(String s) {
        s = s.trim();
        if (s.isEmpty() || s.equals("-")) return null;
        return Float.parseFloat(s);
    }
}
