#!/usr/bin/env bash
# 실시간 모드(데모 시나리오가 아닌 실제 차량 속성) 시연용: 주차장 출차 중 페달 오조작을
# 에뮬레이터 VHAL에 차속·기어·EPB 값을 넣어 재현한다.
#
# 형식은 Pleos 튜토리얼의 VHAL 주입 방식을 따른다:
#   adb shell "echo 'propId: <id> areaId: 0 values: {<값>}' > /data/vendor/vsomeip/vhal_fifo"
# 에뮬레이터 버전에 따라 형식이 다를 수 있으니 처음 한 번은 값이 앱 화면(차속·기어)에 반영되는지 확인한다.
# 반영되지 않으면 Pleos Connect Virtual Console에서 같은 순서로 조작하거나, 앱의 "데모 시나리오"를 쓴다.
#
# 브레이크 페달은 AAOS 표준 속성이 아니라 Pleos Vehicle SDK 값이라 이 스크립트로 넣을 수 없다.
# (Virtual Console에서 조작. 브레이크 신호가 없으면 앱은 "브레이크 OFF"로 보고 판별한다.)
set -euo pipefail

PERF_VEHICLE_SPEED=291504647   # float, m/s
GEAR_SELECTION=289408000       # int: N=1, R=2, P=4, D=8
PARKING_BRAKE_ON=287310850     # bool: 0/1

setp() {
  adb shell "echo 'propId: $1 areaId: 0 values: {$2}' > /data/vendor/vsomeip/vhal_fifo"
}

kmh() { # km/h → m/s
  python3 -c "print(round($1/3.6, 3))"
}

echo "1) P단 정차"
setp $PARKING_BRAKE_ON 0
setp $GEAR_SELECTION 4
setp $PERF_VEHICLE_SPEED 0
sleep 2

echo "2) D 변속"
setp $GEAR_SELECTION 8
sleep 0.5

echo "3) 브레이크 대신 가속 페달: 0.38g(약 13km/h/s)로 가속 2.2초"
# 워치 패닉 신호도 같이 보내 두면 개입이 더 빨라진다(./scripts/wearable.sh stream panic 8 &)
v=0
for i in $(seq 1 22); do
  v=$(python3 -c "print(round($v + 1.34, 2))")
  setp $PERF_VEHICLE_SPEED "$(kmh "$v")"
  sleep 0.1
done

echo "4) 발을 뗌(0.5초 유지)"
sleep 0.5

echo "5) 두 발 제동: 0.45g(약 16km/h/s)로 감속"
while python3 -c "import sys; sys.exit(0 if $v > 0 else 1)"; do
  v=$(python3 -c "print(max(0.0, round($v - 1.59, 2)))")
  setp $PERF_VEHICLE_SPEED "$(kmh "$v")"
  sleep 0.1
done

echo "6) 정차 1초 후 P단"
sleep 1.2
setp $GEAR_SELECTION 4
echo "완료: 앱이 '안전하게 정차했습니다' 화면을 띄우는지 확인"
