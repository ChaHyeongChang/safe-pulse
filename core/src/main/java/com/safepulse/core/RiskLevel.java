package com.safepulse.core;

/** 판별 단계. 기획서 6장의 0~4단계와 같다. */
public enum RiskLevel {
    /** 0 감시: 신호 수집만. */
    MONITOR(0, "감시"),
    /** 1 주의: 무음. 감시 강화와 로그 표시만. */
    WATCH(1, "주의"),
    /** 2 개입: 음성 코칭 시작. */
    INTERVENE(2, "개입"),
    /** 3 긴급: 브레이크를 밟았는데도 가속. N·EPB 단계로 바로. */
    CRITICAL(3, "긴급"),
    /** 4 사후: 정차 완료 후 안부 확인과 기록. */
    POST(4, "사후");

    public final int code;
    public final String label;

    RiskLevel(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean isEngaged() {
        return this == INTERVENE || this == CRITICAL;
    }
}
