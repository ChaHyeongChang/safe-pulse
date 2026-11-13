package com.safepulse.core;

/**
 * 음성 지시문. key는 녹음 음성 파일 이름(res/raw/vo_{key}.ogg 등)과 연결된다.
 * 한 문장 한 동작, 2초 안에 끝나는 길이, 부정문 대신 긍정문을 쓴다(기획서 7장).
 */
public enum Prompt {
    BRAKE("brake", "발 떼고, 두 발로 브레이크!"),
    BRAKE_GOOD("brake_good", "좋아요, 계속 밟으세요."),
    NEUTRAL("neutral", "기어 N!"),
    PARKING_BRAKE("epb", "주차 브레이크 당기고 유지!"),
    STOPPED("stopped", "멈췄습니다. 기어 P, 비상등 켜세요."),
    SAFE("safe", "안전하게 정차했습니다. 괜찮으세요?"),
    GUARDIAN("guardian", "응답이 없어 보호자에게 알렸습니다."),
    CANCELLED("cancel", "안내를 멈춥니다."),
    REHEARSAL_START("rehearsal_start", "리허설을 시작합니다. 실제 상황과 같은 안내가 나옵니다."),
    REHEARSAL_RETURN_P("back_to_p", "기어를 P로 돌려 주세요."),
    REHEARSAL_DONE("rehearsal_done", "리허설 끝. 잘하셨어요."),
    REHEARSAL_ABORT("rehearsal_abort", "차가 움직여서 리허설을 멈춥니다.");

    public final String key;
    public final String text;

    Prompt(String key, String text) {
        this.key = key;
        this.text = text;
    }
}
