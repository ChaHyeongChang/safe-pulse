#!/usr/bin/env bash
# 워치 생체 신호를 에뮬레이터 앱에 넣는다(데모·테스트용).
# 실서비스에서는 갤럭시워치/애플워치 → 폰 앱이 같은 인텐트를 보낸다.
#
#   ./scripts/wearable.sh calm          # 심박 74, 경직 0.1
#   ./scripts/wearable.sh panic         # 심박 112, 경직 0.85
#   ./scripts/wearable.sh set 98 0.4    # 직접 지정
#   ./scripts/wearable.sh off           # 워치 연결 끊김
#   ./scripts/wearable.sh stream panic 10   # 0.5초마다 10초 동안 계속 보냄(2초 지나면 오래된 값으로 무시되므로)
set -euo pipefail

ACTION=com.safepulse.app.WEARABLE_SAMPLE
COMPONENT=com.safepulse.app/.WearableInputReceiver

send() {
  adb shell am broadcast -a "$ACTION" -n "$COMPONENT" --ef hr "$1" --ef grip "$2" >/dev/null
  echo "sent hr=$1 grip=$2"
}

preset() {
  case "$1" in
    calm) echo "74 0.1" ;;
    panic) echo "112 0.85" ;;
    *) echo "unknown preset: $1" >&2; exit 1 ;;
  esac
}

case "${1:-}" in
  calm|panic) send $(preset "$1") ;;
  set) send "$2" "$3" ;;
  off) adb shell am broadcast -a "$ACTION" -n "$COMPONENT" --ez disconnect true >/dev/null; echo "sent disconnect" ;;
  stream)
    read -r hr grip <<<"$(preset "${2:-panic}")"
    end=$(( $(date +%s) + ${3:-10} ))
    while [ "$(date +%s)" -lt "$end" ]; do send "$hr" "$grip"; sleep 0.5; done ;;
  *) sed -n '2,12p' "$0"; exit 1 ;;
esac
