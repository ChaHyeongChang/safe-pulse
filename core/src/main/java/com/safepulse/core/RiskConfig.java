package com.safepulse.core;

/**
 * 판별·코칭 임계값. 기획서 6장의 초기값이며, 시뮬레이션과 실차 데이터로 보정한다.
 * 한곳에 모아 두어 팀원이 값만 바꿔 실험할 수 있게 했다.
 */
public final class RiskConfig {
    // ---- 1단계(주의) 진입
    /** 종방향 가속도 기준(g). */
    public double watchAccelG = 0.30;
    /** 저속 구간 상한(km/h). */
    public float lowSpeedKmh = 20f;
    /** D·R 변속 직후 저속 규칙을 적용하는 시간(ms). */
    public long shiftWindowMs = 5000;
    /** 조건이 잠깐 끊겨도 같은 가속으로 보는 허용 간격(ms). */
    public long conditionGapMs = 300;
    /** 주의 조건이 사라진 뒤 0단계로 돌아가기까지(ms). */
    public long watchReleaseMs = 1000;

    // ---- 2단계(개입) 진입
    /** 저속 구간: 가속이 이만큼 이어지면 개입(ms). */
    public long interveneSustainMs = 1500;
    /** 일반 주행 구간: 패닉 신호와 함께 이만큼 이어지면 개입(ms). */
    public long highSpeedSustainMs = 3000;
    /** 손목 경직·떨림 점수 기준(0~1). */
    public float gripThreshold = 0.7f;
    /** 심박 개인 기준선 대비 상승폭(bpm). */
    public float heartRateRiseBpm = 25f;
    /** 이보다 오래된 웨어러블 값은 판별에서 제외(ms). */
    public long wearableStaleMs = 2000;

    // ---- 3단계(긴급)
    /** 브레이크 ON 상태에서 이 이상 가속하면 제동 무효 의심(g). 내리막 오탐을 피하려 0.1g. */
    public double criticalAccelG = 0.10;
    public long criticalSustainMs = 1000;
    public float criticalMinSpeedKmh = 3f;

    // ---- 정차·사후
    public float stopSpeedKmh = 1f;
    public long stopConfirmMs = 1000;
    /** 사후 단계에서 이 속도를 넘으면 주행 재개로 보고 0단계로. */
    public float resumeSpeedKmh = 10f;
    /** 사용자가 취소한 뒤 재개입을 막는 시간(ms). */
    public long cancelCooldownMs = 10000;

    // ---- 가속도 추정
    public long accelWindowMs = 500;

    // ---- 코칭
    /** 수행 확인이 안 되면 같은 지시를 반복하는 간격(ms). */
    public long promptRepeatMs = 2000;
    /** "좋아요, 계속 밟으세요" 등 유지 안내 간격(ms). */
    public long holdRepeatMs = 3000;
    /** 브레이크 ON인데 감속이 없으면 N 단계로 넘어가는 시간(ms). */
    public long brakeNoDecelMs = 2000;
    /** 이 값 이하로 감속하면 브레이크가 듣는다고 본다(g). */
    public double decelConfirmG = -0.05;
    /** "멈췄습니다…" 안내 뒤 안부 확인으로 넘어가기까지(ms). 앞 안내가 끝까지 들리게 한다. */
    public long stoppedToSafeMs = 2500;
    /** 정차 후 응답이 없으면 보호자에게 알리는 시간(ms). */
    public long wellnessTimeoutMs = 30000;

    // ---- 기록
    public long recordPreMs = 30000;
    public long recordPostMs = 30000;
    public long recordMaxMs = 180000;

    // ---- 리허설
    public long rehearsalIntroMs = 3000;
    public long rehearsalRepeatMs = 4000;
}
