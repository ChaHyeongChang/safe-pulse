# 작업 보고 — 2026-11-14 (자리 비움 세션)

진행 순서: 1 → 2 → 6 → 8(첫 커밋) → 7 → 3 → 4 → 5 → 다시 커밋. 사람이 할 일은 [TODO_MANUAL.md](TODO_MANUAL.md).

## 한눈에 보기

| 항목 | 결과 |
| --- | --- |
| `:core:test` | ✅ 17개 모두 통과(core 판별 로직은 바꾸지 않음) |
| `assembleDebug` (SDK 사용 / `-Psafepulse.pleosSdk=false`) | ✅ 둘 다 성공 |
| `exportReleaseApk` | ✅ `apk/MONDAI_SafePulse_release.apk` 약 7.9MB, 디버그 키 서명 |
| 에뮬레이터 설치·실행 | ✅ 기존 앱 삭제 → 제출 APK 설치 → 권한 대화상자 허용 → 실행 |
| 실시간 신호(차속·기어·브레이크 페달·크루즈) | ✅ 홈 화면 반영. 주차 브레이크만 주입 수단 없음 |
| 데모 1~7, 비교, R | ✅ README 표와 같은 동작(버그 하나 고친 뒤) |
| 실시간 모드 시연 스크립트 | ✅ 개입 → 코칭 → 정차 → 안부 확인, 7회 실행 모두 같은 흐름 |
| 음성 | ✅ (후속 작업) Windows 한국어 TTS로 만든 WAV 12개를 넣어 12개 모두 재생 확인. 귀로 듣는 확인은 남음 |
| GitHub push | ❌ 인증 실패. 커밋까지만 함 |

## 단계별로 한 일

### 1. 빌드
- 수정 없이 `./gradlew :core:test assembleDebug` 성공. JDK는 `JAVA_HOME`(JBR 21.0.11, 한글 경로)을 그대로 썼고 문제없어
  `C:\Android\jdk-21` 복사는 하지 않았다. Gradle 캐시는 `GRADLE_USER_HOME=C:\gradle`.

### 2. 설치·실행·신호 확인
- `installDebug` 후 `pm grant --user 10`(AAOS 운전자 사용자 10)으로 CAR_SPEED·알림 허용.
- **신호 주입 방식이 지시와 달랐다.** 이 이미지에는 `/data/vendor/vsomeip/vhal_fifo` 가 없고, root 불가, `cmd car_service inject-vhal-event` 는
  "requires non-user build"로 막혀 있다. 대신 에뮬레이터 안 Virtual Console(`ai.pleos.hardware.vehicle.emulator` 가 띄우는 `http://localhost:8088`)의
  HTTP API를 찾았다: `PUT /vehicle/controls?speed=&accelerator=&brake=`, `PUT /vehicle/gear?gear=Park|Drive…`,
  `PUT /vehicle/driving-mode?ad_mode_state=…`. 브라우저 세션 하트비트(`POST /browser/session`)가 있어야 값을 받고, 차속은 정수 km/h만 받는다.
- 확인: 기어 P→D, 차속 20km/h, 브레이크 "밟음"이 홈 화면에 반영(스크린샷·Logcat). AAOS 구독 결과: 차속·기어·EPB 성공, 크루즈는
  `READ_ADAS_STATES`(signature|privileged) 권한이고 VHAL 설정에도 없어 실패.
- 같이 고친 것: 매니페스트 `<queries>`(TTS_SERVICE), Logcat `SafePulse/State` 상태 로그 추가.

### 6. 제출 APK
- `exportReleaseApk` → 기존 앱 삭제 → APK 설치 → 첫 실행에서 권한 대화상자 두 개 확인(차속 권한은 "위치 정보"로 표시됨) → 허용 후 차속 재구독 성공.
- 마지막 코드 변경 뒤 다시 만들어 재설치·데모 1·실시간 시연까지 확인했다(시작 시간 1.35초).

### 8. git
- `git init -b main`, `.gitattributes`(셸 스크립트 LF) 추가, 스크립트 실행 권한 표시, 첫 커밋 `6c627a4`.
- `origin` = https://github.com/ChaHyeongChang/safe-pulse.git 추가. push는 `Invalid username or token` 으로 실패 → 커밋까지만.
- keystore.properties·*.jks 는 .gitignore에 걸리는 것을 확인했고 커밋하지 않았다.

### 7. 문서
- README·apk/README.md·docs의 팀명 자리표시 → MONDAI.
- README: 개발 환경(JDK 17~21), 권한 대화상자 설명, adb 권한 명령, 빌드 명령, 시연 방법 표, **에뮬레이터 확인 결과**(실제로 들어온 신호), 검증 결과에 앱 재생 열 추가.

### 3. Pleos SDK
- Gradle 캐시의 `Vehicle-2.3.2-sources.jar` 로 API 확인: `Brake.registerBrakePedalStatus(BrakePedalStatusListener)`,
  콜백 `onBrakePedalStatusUpdated(Boolean?)` / `onFailed(Exception)`. 기존 `interpret()` 해석(Boolean)과 같았다.
- 리플렉션 → **타입 있는 호출**로 교체. `app/src/pleos`(SDK) / `app/src/noPleos`(빈 구현)를 `safepulse.pleosSdk` 로 골라 SDK 없는 빌드도 유지.
- 첫 register가 SDK 권한 확인에 2~3초 걸려 메인 스레드를 막던 것을 별도 스레드로 옮겼다.
- 크루즈 상태를 `DrivingMode.registerCruiseControlState` 로 추가(같은 CAR_DRIVE 권한). 알 수 없는 값(null)은 "크루즈 아님"으로 처리.
- Logcat 확인: 브레이크 capability READ, 현재값·변경 콜백 true/false, 크루즈 ENABLED/ACTIVATED. 크루즈 ACTIVATED 중 0.38g 가속은 감지하지 않음.

### 4. 데모 시나리오
- **버그**: 처음 재생한 데모 1이 개입하지 않았다. `startScenario()` 가 진행 중인 안내를 끄려고 `core.cancel()` 을 부르는데,
  이것이 "운전자 취소 후 10초 재개입 금지"를 걸어 데모 첫 10초 동안 판별이 막혔다(단위 테스트는 새 엔진으로 돌려서 통과).
  → 데모마다 새 `SafePulseCore` 로 재생하도록 앱만 고쳤다(core 로직 변경 없음).
- **버그**: 데모 메뉴 글자가 흰 배경에 흰색으로 거의 보이지 않았다 → 앱 전용 어두운 대화상자 테마, 목록 글자 24sp.
- 결과(앱 기준 시간): 1 2.5초 개입(손목 경직) · 6 3.5초 개입 → 39.8초 보호자 알림 · 2 3.2초 긴급 → N → 주차 브레이크 → 정차 ·
  3 주의만 · 4 감시 유지 · 5 3.5초 개입 → 취소 → 재개입 없음 · 7 5.4초 개입 / 워치 없으면 주의만 · R 1.2·1.6·1.2초(합계 4.1초).
- **음성**: `TTS init failed: -1`. 원인: 에뮬레이터의 유일한 TTS 엔진(`ai.pleos.playground.caas` SpeechService)의 인텐트 필터에
  CATEGORY_DEFAULT가 없어 TextToSpeech(MATCH_DEFAULT_ONLY 조회)가 찾지 못함(`query-services -c DEFAULT` → No services found).
  녹음 파일 경로는 시험 음(vo_brake.wav, 확인 뒤 삭제)으로 오디오 포커스·MediaPlayer 재생을 확인했다.
  docs/VOICE_LINES.md가 상용 TTS 라이선스를 경고하고 있어 이 세션에서는 PC TTS로 음성을 만들지 않았다
  → 이후 사용자 요청으로 만들었다(아래 "후속 작업: 음성 파일").

### 5. 실시간 모드
- `scripts/vhal_parking_misapplication.sh` 를 Virtual Console 방식으로 다시 썼다. 처음에는 개입하지 않았는데,
  Windows에서 반복마다 프로세스를 여러 개 띄워(약 0.3초) 차속이 5km/h 계단으로 올라가 가속도 추정이 0.3g 아래로 흔들렸기 때문이다.
  → 경과 시간 기반 정수 연산(bash `EPOCHREALTIME`)으로 반복마다 curl 하나만 띄우게 하고, 가속 시간을 2.6초로 늘려 여유를 뒀다.
- 결과: 감시 → 주의 → 개입(가속 1.5초 지속) → "발 떼고, 두 발로 브레이크!" → 실제 브레이크 신호 + 감속 → "좋아요" → 정차 → P → 안부 확인.
  워치 없이 5회, `--watch` 2회(손목 경직으로 즉시 개입) 모두 같은 흐름.
- 신호 하나씩 넣는 `scripts/vconsole.sh`(gear/speed/brake/set/reset/state)를 추가했다.

## 바꾼 파일

- 앱: `SafePulseController`(데모 새 엔진, 상태 로그, 크루즈 연결), `PleosVehicleBridge`(src/pleos·src/noPleos로 분리, 타입 호출),
  `VoiceOutput`(TTS 실패 안내 로그), `AndroidManifest.xml`(TTS queries), `themes.xml`(대화상자 테마), `app/build.gradle.kts`(소스 폴더 선택)
- 스크립트: `vconsole.sh`(새로), `vhal_parking_misapplication.sh`(재작성)
- 문서: README, apk/README.md, docs/DEMO_SCRIPT.md, docs/VOICE_LINES.md, docs/NEXT_STEPS_PROMPT.md(팀명), docs/TODO_MANUAL.md(새로), 이 보고서
- 바꾸지 않은 것: core/ 판별 로직과 테스트, AGP·Gradle 버전, gradle.properties, gradle-wrapper.properties, 매니페스트 권한(CONTROL_ 없음)

## 후속 작업: 음성 파일 (2026-11-14, 사용자 요청)

- System.Speech에서 한국어 음성 **Microsoft Heami Desktop (ko-KR)** 을 찾았다(그 밖에는 영어 Zira뿐).
- `tools/gen_voice_windows_tts.ps1` 추가: `Prompt.java` 의 12개 문장을 읽어 `app/src/main/res/raw/vo_{key}.wav`
  (44.1kHz·16bit·모노)로 만든다. 엔진이 넣는 앞뒤 무음을 잘라 내고 앞 0.12초·뒤 0.2초 무음을 다시 붙인다(측정: 앞 약 0.15초, 뒤 약 0.23초).
- 처음 기본 속도로 만들었더니 `brake` 2.76초, `stopped` 4.5초, `rehearsal_start` 5.3초였다. 코칭 안내는 앞 안내를 끊고 나오므로
  `promptRepeatMs`(2초)·`stoppedToSafeMs`(2.5초)·`rehearsalIntroMs`(3초)보다 길면 끝이 잘리거나 리허설 측정이 늦어진다.
  → 구절마다 합성해 짧은 쉼으로 잇고, 상한을 넘는 문장만 말 속도를 올렸다(brake +2, epb +1, stopped +4, rehearsal_start +5).
- `VoiceOutput` 에 `play clip vo_…` 로그 추가. `exportReleaseApk` 로 다시 만든 APK(약 10MB, WAV는 압축 없이 저장, 리소스 이름 `raw/vo_*` 12개 확인)를
  설치하고 데모 1·5·6·R과 실시간 리허설 중단을 재생해 **12개 모두** 재생되는 것을 확인했다(`dumpsys audio` 에서 MediaPlayer `state:started`).
  데모 1에서 `stopped` 는 다음 안내 전까지 2.59초를 받아 끝까지 재생된다.
- 데모 판별 결과는 그대로다(데모 1: 2.5초 개입, R: 3동작 4.1초). `:core:test` 17개 통과.

## 남은 일

[TODO_MANUAL.md](TODO_MANUAL.md) 참고. 핵심: ① GitHub push(필요하면) ② 음성 들어 보기·라이선스 확인 ③ 시연 영상 촬영.

## 커밋

| 커밋 | 내용 |
| --- | --- |
| `6c627a4da2425f348df51b0df77a33b83b0f08cc` | 첫 커밋(빌드·에뮬레이터 1차 확인) |
| `cbf8bdad2fbb1e262ba7a3fac07bd6fcf6077d84` | 코드·APK·문서 최종(데모 버그 수정, SDK 타입 호출, 실시간 주입) |
| `abf89838c52e96d32195f2c9f12aa3d35a9f3137` | 작업 보고서 추가 |
| 음성 파일 커밋 | 최종 HEAD — `git log -1 --format=%H` 로 확인(보고서는 자기 해시를 담을 수 없음) |

접수 메일에는 push된 HEAD 해시를 적는다.
