#!/usr/bin/env python3
"""데모·테스트 시나리오 CSV 생성기.

가속도 프로파일(g)을 적분해 차속을 만들기 때문에 시나리오의 속도·가속이 물리적으로 맞다.
값을 바꾸고 싶으면 아래 시나리오 함수를 고친 뒤 다시 실행한다.

    python3 tools/gen_scenarios.py
"""
import os

G_KMH_PER_S = 9.80665 * 3.6  # 1g = 35.3 km/h/s
STEP = 100
OUT = os.path.join(os.path.dirname(__file__), "..", "core", "src", "main", "resources", "scenarios")


def lerp(t, t0, t1, v0, v1):
    if t <= t0:
        return v0
    if t >= t1:
        return v1
    return v0 + (v1 - v0) * (t - t0) / (t1 - t0)


def b(v):
    if v is None:
        return "-"
    return "1" if v else "0"


def f(v, fmt="{:.0f}"):
    return "-" if v is None else fmt.format(v)


def write(sid, title, description, expect, end_ms, accel, start_kmh, brake, gear, epb,
          cruise=lambda t: False, hr=lambda t: None, grip=lambda t: None, actions=None):
    actions = actions or {}
    lines = [f"# title: {title}", f"# description: {description}", f"# expect: {expect}",
             "t_ms,speed_kmh,brake,gear,epb,cruise,hr,grip,action"]
    v = start_kmh
    for t in range(0, end_ms + STEP, STEP):
        if t > 0:
            v = max(0.0, v + accel(t) * G_KMH_PER_S * STEP / 1000.0)
        lines.append(",".join([
            str(t), f"{v:.1f}", b(brake(t)), gear(t), b(epb(t)), b(cruise(t)),
            f(hr(t)), f(grip(t), "{:.2f}"), actions.get(t, ""),
        ]))
    path = os.path.join(OUT, sid + ".csv")
    with open(path, "w", encoding="utf-8") as fp:
        fp.write("\n".join(lines) + "\n")
    print("wrote", path, len(lines) - 4, "rows")


def parking_profile(t):
    """주차장 출차: 1.6초부터 0.38g 가속(브레이크로 착각) → 3.8초 발 뗌 → 4.3초부터 0.45g 제동."""
    if 1600 <= t < 3800:
        return 0.38
    if 3800 <= t < 4300:
        return -0.02
    if t >= 4300:
        return -0.45
    return 0.0


def parking_gear(t):
    return "P" if t < 1000 or t >= 7200 else "D"


def parking_brake(t):
    return (t < 1500) or (t >= 4300 and t < 7500)


def main():
    os.makedirs(OUT, exist_ok=True)

    # 1. 주차장 출차 중 페달 오조작 (웨어러블 있음)
    write("s1_parking_misapplication",
          "주차장 출차 중 페달 오조작",
          "D로 바꾼 직후 브레이크 대신 가속 페달을 밟아 0.38g로 가속. 손목 경직이 함께 감지되어 빠르게 개입하고, 두 발 제동으로 정차한다.",
          "INTERVENE; steps BRAKE>STOPPED>SAFE; respond",
          14000, parking_profile, 0.0, parking_brake, parking_gear, lambda t: False,
          hr=lambda t: lerp(t, 2000, 4000, 74, 112) if t < 9000 else lerp(t, 9000, 14000, 112, 96),
          grip=lambda t: lerp(t, 1800, 2600, 0.10, 0.85) if t < 8000 else 0.30,
          actions={11500: "respond"})

    # 2. 제동 무효 의심 (브레이크 ON인데 가속)
    def s2_accel(t):
        if 2000 <= t < 4500:
            return 0.15
        if 4500 <= t < 5500:
            return -0.15
        if t >= 5500:
            return -0.50
        return 0.0
    write("s2_brake_ineffective",
          "제동 무효 의심",
          "15km/h 주행 중 브레이크를 밟았는데도 0.15g로 가속. 3단계(긴급)로 바로 넘어가 기어 N과 주차 브레이크를 지시한다.",
          "CRITICAL; steps NEUTRAL>PARKING_BRAKE>STOPPED>SAFE; respond",
          14000, s2_accel, 15.0,
          brake=lambda t: t >= 2000 and t < 8500,
          gear=lambda t: "D" if t < 4500 else ("N" if t < 8000 else "P"),
          epb=lambda t: t >= 5500,
          hr=lambda t: lerp(t, 2500, 4500, 76, 115),
          grip=lambda t: lerp(t, 2500, 3200, 0.10, 0.80),
          actions={12500: "respond"})

    # 3. 추월 가속 (정상)
    write("s3_overtake",
          "고속도로 추월 가속(정상)",
          "80km/h에서 0.32g로 3초간 추월 가속. 운전자 생체 신호가 안정적이라 개입하지 않고 무음 기록만 한다.",
          "no INTERVENE",
          10000, lambda t: 0.32 if 2000 <= t < 5000 else 0.0, 80.0,
          brake=lambda t: False, gear=lambda t: "D", epb=lambda t: False,
          hr=lambda t: 78, grip=lambda t: 0.10)

    # 4. 크루즈 가속 (정상)
    write("s4_cruise",
          "크루즈 컨트롤 가속(정상)",
          "크루즈가 켜진 상태에서 설정 속도 복귀로 0.33g 가속. 운전대를 세게 잡고 있어도 크루즈 가속은 감지 대상에서 뺀다.",
          "MONITOR only",
          8000, lambda t: 0.33 if 2000 <= t < 4000 else 0.0, 60.0,
          brake=lambda t: False, gear=lambda t: "D", epb=lambda t: False,
          cruise=lambda t: True, hr=lambda t: 80, grip=lambda t: 0.90)

    # 5. 의도된 급출발 → 오탐 → 운전자 취소
    def s5_accel(t):
        if 1600 <= t < 4500:
            return 0.35
        if 4500 <= t < 7000:
            return -0.10
        return 0.0
    write("s5_false_positive_cancel",
          "의도된 급출발과 취소",
          "운전자가 일부러 0.35g로 급출발해 개입이 시작되지만, '괜찮아요'를 눌러 바로 멈춘다. 이후 10초 동안 다시 개입하지 않는다.",
          "INTERVENE then cancel; no re-engage",
          12000, s5_accel, 0.0,
          brake=lambda t: t < 1500, gear=lambda t: "P" if t < 1000 else "D", epb=lambda t: False,
          hr=lambda t: 72, grip=lambda t: 0.20,
          actions={3800: "cancel"})

    # 6. 워치 없음 (차량 단독 모드)
    write("s6_no_watch",
          "워치 없이 페달 오조작(차량 단독 모드)",
          "1번과 같은 상황이지만 워치가 없다. 가속이 1.5초 이어진 시점에 개입하고, 정차 후 응답이 없어 보호자 알림까지 진행한다.",
          "INTERVENE; guardian alert",
          40000, parking_profile, 0.0, parking_brake, parking_gear, lambda t: False)

    # 7. 일반 주행 중 의도치 않은 가속 + 패닉 (웨어러블이 개입을 가능하게 하는 경우)
    def s7_accel(t):
        if 2000 <= t < 5800:
            return 0.32
        if 5800 <= t < 6300:
            return 0.0
        if t >= 6300:
            return -0.45
        return 0.0
    write("s7_highway_panic",
          "일반 주행 중 의도치 않은 가속과 패닉",
          "70km/h에서 0.32g 가속이 이어지고 손목 경직·심박 급상승이 함께 감지된다. 3초 지속 시 개입한다. 워치가 없으면 이 구간은 기록만 한다.",
          "INTERVENE; steps BRAKE>STOPPED>SAFE; respond",
          23000, s7_accel, 70.0,
          brake=lambda t: t >= 6300 and t < 17500,
          gear=lambda t: "D" if t < 17000 else "P",
          epb=lambda t: False,
          hr=lambda t: lerp(t, 2500, 4500, 75, 118),
          grip=lambda t: lerp(t, 2200, 2800, 0.10, 0.90),
          actions={21500: "respond"})

    # r1. 골든타임 리허설 (P단 정차 중)
    write("r1_rehearsal",
          "골든타임 리허설",
          "P단 정차 중 리허설. 브레이크 → N → 주차 브레이크 → P 복귀를 차량 신호로 확인하며 반응 시간을 잰다.",
          "rehearsal DONE",
          11000, lambda t: 0.0, 0.0,
          brake=lambda t: 4800 <= t < 9500,
          gear=lambda t: "N" if 6400 <= t < 9000 else "P",
          epb=lambda t: t >= 7600,
          actions={500: "rehearsal"})


if __name__ == "__main__":
    main()
