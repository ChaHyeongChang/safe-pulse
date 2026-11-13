#!/usr/bin/env bash
# 실시간 모드(데모 시나리오가 아닌 실제 차량 신호) 시연: 주차장 출차 중 페달 오조작을 재현한다.
#
#   ./scripts/vhal_parking_misapplication.sh            # 워치 없이(차량 단독: 가속 1.5초 지속 규칙으로 개입)
#   ./scripts/vhal_parking_misapplication.sh --watch    # 워치 패닉 신호도 함께(손목 경직으로 더 빨리 개입)
#
# 신호 주입 방식(Pleos Connect v2.1.0 에뮬레이터에서 확인, 2026-11-14):
#   예전 방식인 /data/vendor/vsomeip/vhal_fifo 는 이 이미지에 없고, `cmd car_service inject-vhal-event` 는 user 빌드라 막혀 있다.
#   그래서 에뮬레이터에 들어 있는 Pleos Connect Virtual Console의 HTTP API(scripts/vconsole.sh)로 넣는다.
#     차속  → PERF_VEHICLE_SPEED(AAOS)          기어 → GEAR_SELECTION(AAOS)
#     브레이크 페달 → BRAKE_PEDAL_STATUS(Pleos Vehicle SDK)
#   주차 브레이크(PARKING_BRAKE_ON)는 Virtual Console에 없어 넣지 않는다. 정차 확인은 "차속 0 + 기어 P"로 한다.
#
# 차속은 루프 횟수가 아니라 경과 시간으로 계산한다. Windows에서는 curl 한 번에 0.1~0.2초가 걸려
# 횟수로 올리면 실제 가속도가 0.3g 기준보다 낮아져 감지되지 않는다.
#
# 실행 전: 앱을 열어 홈 화면이 "실시간 차량"이고 상태가 "감시"인지 확인한다(데모 재생 중이면 "실시간 차량으로" 선택).
# 필요: adb, curl, bash (Windows는 Git Bash). Windows에서는 bash 5 이상이면 더 매끄럽다(EPOCHREALTIME).
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=vconsole.sh
source "$DIR/vconsole.sh"

ACCEL_MG=380      # 가속 페달 오조작 가속도(밀리g): 0.38g
ACCEL_MS=2600     # 가속 시간(약 35km/h까지). 개입(가속 1.5초 지속)까지 여유를 둔다
BRAKE_MG=450      # 두 발 제동 감속도(밀리g): 0.45g
WITH_WATCH=0
[ "${1:-}" = "--watch" ] && WITH_WATCH=1

# ramp <시작 km/h(정수)> <가속도 밀리g, 감속이면 음수> <시간 ms, 0이면 정지할 때까지> <브레이크%> <가속%>
# 정수 연산만 써서 반복마다 curl 하나만 띄운다(갱신 간격 약 0.15초).
ramp() {
  local v0=$1 mg=$2 dur=$3 brk=$4 acc=$5 t0 el vm v done_flag=0
  vc_clock; t0=$VC_NOW
  while :; do
    vc_clock; el=$(( VC_NOW - t0 ))
    if [ "$dur" -gt 0 ] && [ "$el" -ge "$dur" ]; then el=$dur; done_flag=1; fi
    vm=$(( v0 * 1000 + mg * 35304 * el / 1000000 ))   # 1g = 35.304 km/h/s, vm 단위 0.001km/h
    if [ "$vm" -le 0 ]; then vm=0; [ "$mg" -lt 0 ] && done_flag=1; fi
    v=$(( (vm + 500) / 1000 ))                         # Virtual Console은 정수 km/h만 받는다
    vc_set "$v" "$brk" "$acc" || exit 1
    [ "$done_flag" = 1 ] && break
  done
}

vc_begin

echo "1) P단 정차"
vc_set 0 0 0 || exit 1
vc_gear P || exit 1
sleep 2

echo "2) 브레이크 밟고 D 변속"
vc_set 0 40 0 || exit 1
sleep 0.3
vc_gear D || exit 1
sleep 0.5

WATCH_PID=""
if [ "$WITH_WATCH" = 1 ]; then
  echo "   (워치 패닉 신호 스트리밍 8초)"
  bash "$DIR/wearable.sh" stream panic 8 >/dev/null &
  WATCH_PID=$!
fi

echo "3) 브레이크 대신 가속 페달: 0.${ACCEL_MG}g로 $((ACCEL_MS / 1000)).$((ACCEL_MS % 1000 / 100))초 가속"
ramp 0 "$ACCEL_MG" "$ACCEL_MS" 0 60
echo "   → ${VC_SPEED}km/h (앱: 개입 → '발 떼고, 두 발로 브레이크!')"

echo "4) 발을 뗌(0.5초)"
vc_set "$VC_SPEED" 0 0 || exit 1
sleep 0.5

echo "5) 두 발 제동: 0.${BRAKE_MG}g로 감속 (브레이크 페달 80%)"
ramp "$VC_SPEED" "-$BRAKE_MG" 0 80 0
echo "   → 정차 (앱: '좋아요, 계속 밟으세요' → '멈췄습니다. 기어 P, 비상등 켜세요.')"

echo "6) 정차 1.2초 후 P단, 브레이크 뗌"
sleep 1.2
vc_gear P || exit 1
sleep 0.3
vc_set 0 0 0 || exit 1

[ -n "$WATCH_PID" ] && kill "$WATCH_PID" 2>/dev/null
echo "완료: 앱이 '안전하게 정차했습니다' 화면과 안부 확인(30초)을 띄우는지 확인하세요."
