#!/usr/bin/env bash
# Pleos Connect Virtual Console(에뮬레이터 안 HTTP 서버, 포트 8088)로 차량 신호를 넣는다.
# Pleos Connect v2.1.0 에뮬레이터에서 확인한 방식이다(2026-11-14).
#   - 이 이미지에는 /data/vendor/vsomeip/vhal_fifo 가 없고, user 빌드라 `cmd car_service inject-vhal-event` 도 막혀 있다.
#   - Virtual Console이 차속·기어·브레이크 페달을 DDS → VHAL/Pleos Vehicle 서비스로 넣는다.
#   - 브라우저 세션(1초마다 POST /browser/session)이 살아 있을 때만 값을 받는다. 없으면 {"error":"emulator_inactive"}.
#   - 주차 브레이크(PARKING_BRAKE_ON)는 Virtual Console에 항목이 없어 넣을 수 없다(계기판 경고등만 있음).
#
# 사용법
#   ./scripts/vconsole.sh gear P|R|N|D        # 기어(앱: GEAR_SELECTION)
#   ./scripts/vconsole.sh speed <km/h>        # 차속(앱: PERF_VEHICLE_SPEED). 정수만 받는다(소수면 invalid_speed)
#   ./scripts/vconsole.sh brake <0~100>       # 브레이크 페달 %(앱: Pleos Vehicle SDK BRAKE_PEDAL_STATUS, 0보다 크면 밟음)
#   ./scripts/vconsole.sh set <km/h> <브레이크%> [가속%]
#   ./scripts/vconsole.sh reset               # P단, 0km/h, 페달 0
#   ./scripts/vconsole.sh state               # 현재 값 출력
#
# 다른 스크립트에서 `source scripts/vconsole.sh` 로 함수만 가져다 쓸 수 있다(vc_begin/vc_set/vc_gear/vc_end).
# 여러 에뮬레이터가 붙어 있으면 ANDROID_SERIAL=emulator-5554 처럼 지정한다.

VC_PORT="${VCONSOLE_PORT:-18088}"
VC_BASE="http://127.0.0.1:${VC_PORT}"
VC_CLIENT="safepulse-$$"
VC_HB_PID=""
VC_SPEED=0
VC_BRAKE=0
VC_ACCEL=0

# VC_NOW 에 현재 시각(ms)을 넣는다. bash 5의 EPOCHREALTIME을 쓰면 외부 프로세스를 띄우지 않아 빠르다
# (Windows Git Bash에서는 프로세스 하나 띄우는 데 0.05~0.15초가 걸려 차속 갱신이 계단처럼 거칠어진다).
vc_clock() {
  if [ -n "${EPOCHREALTIME:-}" ]; then
    local u=${EPOCHREALTIME//[.,]/}
    VC_NOW=$(( 10#$u / 1000 ))
  else
    VC_NOW=$(vc_now_ms)
  fi
}

vc_now_ms() {
  local t
  t=$(date +%s%3N 2>/dev/null)
  case "$t" in
    ''|*N) python3 -c 'import time;print(int(time.time()*1000))' 2>/dev/null \
             || python -c 'import time;print(int(time.time()*1000))' 2>/dev/null \
             || perl -MTime::HiRes=time -e 'printf("%d\n", time()*1000)' ;;
    *) echo "$t" ;;
  esac
}

vc_put() { # $1 = 경로와 쿼리
  local out
  out=$(curl -s -m 3 -X PUT "${VC_BASE}$1") || { echo "Virtual Console 요청 실패: $1" >&2; return 1; }
  case "$out" in *'"error"'*) echo "Virtual Console 오류: $1 -> $(echo "$out" | head -c 200)" >&2; return 1 ;; esac
}

vc_begin() {
  command -v curl >/dev/null || { echo "curl이 필요합니다." >&2; exit 1; }
  adb forward "tcp:${VC_PORT}" tcp:8088 >/dev/null || { echo "adb forward 실패: 에뮬레이터가 켜져 있는지 확인하세요." >&2; exit 1; }
  ( while :; do curl -s -m 2 -X POST "${VC_BASE}/browser/session?client=${VC_CLIENT}" >/dev/null; sleep 0.8; done ) &
  VC_HB_PID=$!
  trap vc_end EXIT INT TERM
  local i
  for i in $(seq 1 30); do
    if curl -s -m 2 "${VC_BASE}/vehicle/state" | grep -q '"emulator_active": *true'; then return 0; fi
    sleep 0.2
  done
  echo "Virtual Console 세션이 활성화되지 않았습니다(포트 8088 응답 확인)." >&2
  exit 1
}

vc_end() {
  if [ -n "$VC_HB_PID" ]; then
    kill "$VC_HB_PID" 2>/dev/null
    wait "$VC_HB_PID" 2>/dev/null
    VC_HB_PID=""
    curl -s -m 2 -X POST "${VC_BASE}/browser/session?client=${VC_CLIENT}&closing=1" >/dev/null
  fi
}

# vc_set <km/h> <브레이크%> [가속%]  (모두 정수로 반올림해 보낸다)
vc_set() {
  printf -v VC_SPEED '%.0f' "$1"
  printf -v VC_BRAKE '%.0f' "$2"
  printf -v VC_ACCEL '%.0f' "${3:-0}"
  vc_put "/vehicle/controls?speed=${VC_SPEED}&accelerator=${VC_ACCEL}&brake=${VC_BRAKE}"
}

vc_gear() {
  local g
  case "$1" in
    P|p) g=Park ;; R|r) g=Reverse ;; N|n) g=Neutral ;; D|d) g=Drive ;;
    *) echo "기어는 P/R/N/D 중 하나: $1" >&2; return 1 ;;
  esac
  vc_put "/vehicle/gear?gear=${g}"
}

vc_state() {
  curl -s -m 3 "${VC_BASE}/vehicle/state" | tr ',' '\n' \
    | grep -E '"(value|speed_kph|brake_pct|accelerator_pct|emulator_active)"' | head -6
}

# 직접 실행할 때만 아래 명령 처리(source로 불러오면 함수만 정의).
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  set -uo pipefail
  case "${1:-}" in
    gear) vc_begin; vc_gear "$2" ;;
    speed) vc_begin; vc_set "$2" 0 0 ;;
    brake) vc_begin
           s=$(curl -s -m 3 "${VC_BASE}/vehicle/state" | tr ',' '\n' | grep '"speed_kph"' | head -1 | sed 's/[^0-9.]//g')
           vc_set "${s:-0}" "$2" 0 ;;
    set) vc_begin; vc_set "$2" "$3" "${4:-0}" ;;
    reset) vc_begin; vc_set 0 0 0 && sleep 0.3 && vc_gear P ;;
    state) adb forward "tcp:${VC_PORT}" tcp:8088 >/dev/null; vc_state; exit 0 ;;
    *) sed -n '2,19p' "$0"; exit 1 ;;
  esac
  sleep 0.5
  vc_state
fi
