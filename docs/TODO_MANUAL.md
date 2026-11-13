# 사람이 해야 하는 확인·작업

2026-11-14 자리 비움 세션에서 자동으로 할 수 없었거나, 눈·귀로 봐야 해서 남겨 둔 일이다. 위에서부터 순서대로.

## 1. 제출 전에 꼭 할 일

### 1-1. GitHub push (자동 push 실패)
- 실패 메시지: `remote: Invalid username or token. Password authentication is not supported for Git operations.`
  → PC에 저장된 GitHub 자격 증명이 만료됐거나 비밀번호로 저장된 것으로 보인다.
- 해결:
  1. Windows "자격 증명 관리자" → Windows 자격 증명 → `git:https://github.com` 항목이 있으면 제거
  2. 저장소 폴더에서 `git push -u origin main` → 뜨는 GitHub 로그인 창에서 로그인(브라우저 인증 또는 PAT)
- push 후 GitHub → Actions에서 `core-tests` 가 통과하는지 확인한다.
- 접수 메일에 적을 커밋 해시: `git log -1 --format=%H`

### 1-2. 음성 녹음 12개 (없으면 시연 영상에 소리가 없다)
- Pleos Connect 에뮬레이터 2.1.0에는 Android TTS로 쓸 수 있는 엔진이 없다(앱 Logcat: `TTS init failed: -1`).
  유일한 엔진 `ai.pleos.playground.caas` 는 인텐트 필터에 CATEGORY_DEFAULT가 없어 TextToSpeech가 찾지 못한다.
- [VOICE_LINES.md](VOICE_LINES.md)의 12개 문장을 녹음해 `app/src/main/res/raw/vo_*.ogg`(또는 .mp3/.wav)로 넣는다.
- `./gradlew exportReleaseApk` → `apk/MONDAI_SafePulse_release.apk` 갱신 → 커밋·push.
- 확인: 데모 1 재생 → "발 떼고, 두 발로 브레이크!"가 PC 스피커로 들리는지. Logcat `SafePulse/Voice` 에 `no voice output` 이 없어야 한다.
  (녹음 파일 재생 경로 자체는 시험 음으로 확인했다: 오디오 포커스 획득·MediaPlayer 재생 정상.)

### 1-3. 시연 영상 촬영
- [DEMO_SCRIPT.md](DEMO_SCRIPT.md) 순서대로. OBS로 에뮬레이터 창 + PC 소리 녹화.
- 설정 → 보호자 이름 입력(예: "딸 지은"). 한글은 adb로 넣을 수 없어 직접 입력해야 한다.

## 2. 눈·귀로 확인할 것 (자동으로 확인하지 못함)

- [ ] 실제 음성 크기, 재생 중 음악이 멈추는지(오디오 포커스 `GAIN_TRANSIENT_EXCLUSIVE` 획득은 로그로 확인함)
- [ ] 데모 메뉴 대화상자: 흰 배경에 흰 글자로 안 보이던 문제를 어두운 테마·24sp로 고쳤다. 영상에서 잘 보이는지
- [ ] 다른 앱이 앞에 있을 때 위기 감지 → 긴급 알림(전체 화면 인텐트)으로 앱이 뜨는지
- [ ] 119 / 112 버튼 → 전화 앱이 열리는지
- [ ] 정차 후 화면의 "사고 후 가이드" 펼치기, 고령 모드(큰 글씨·느린 음성)
- [ ] Virtual Console을 PC 브라우저에서 여는 방법(`adb forward tcp:18088 tcp:8088` → `http://127.0.0.1:18088`): HTML 응답만 확인했고 화면은 보지 않았다

## 3. 에뮬레이터 한계 (실차·다른 버전에서 확인 필요)

- **주차 브레이크(`PARKING_BRAKE_ON`)**: Virtual Console에 항목이 없어 값을 바꿀 수 없다(구독·현재값 "해제" 수신까지만 확인).
  실시간 리허설의 주차 브레이크 단계는 "신호 없음 · 다음" 버튼으로 넘겨야 한다. 주차 브레이크 단계는 데모 2·R로 보여 준다.
- **크루즈 상태**: Pleos SDK `DrivingMode.registerCruiseControlState` 로 받는다. Virtual Console의 Cruise Control State는
  Full autonomous → ACTIVATED(2), Lateral·Ready → ENABLED(1), Not ready → -6(SDK가 null로 줌), Longitudinal → 변화 없음이었다.
  앱은 ACTIVATED일 때만 크루즈 가속으로 본다. 실차에서 적응형 크루즈 작동 중 값이 ACTIVATED인지 확인 필요.
- **차속 해상도**: Virtual Console은 정수 km/h만 받는다(소수면 `invalid_speed`). 실차 PERF_VEHICLE_SPEED는 더 촘촘하다.

## 4. 선택

- 서명 키: 지금 제출 APK는 디버그 키 서명이다(에뮬레이터 설치·시연에는 문제없음). 필요하면 keystore를 만들고
  루트에 `keystore.properties` 를 둔다. **keystore.properties·*.jks 는 커밋하지 않는다(.gitignore 처리됨).**
- 음성을 녹음 대신 Gleo TTS(Pleos SDK)로 내는 확장은 SDK·API를 확인하지 않아 손대지 않았다.
