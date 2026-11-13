# 세이프 펄스 (Safe Pulse)

**패닉의 순간, 차가 운전자에게 "지금 할 한 동작"을 말해 주는 긴급 대응 코치.**
의도치 않은 급가속(페달 오조작 포함)을 Pleos 차량 신호와 워치 생체 신호로 감지합니다.
운전자에게 한 번에 한 동작씩 음성으로 지시하고, 그 동작이 실제로 수행됐는지는 차량 신호로 확인한 뒤 다음 지시로 넘어갑니다.

현대자동차그룹 차량용 앱 개발 공모전(2026) 출품작 · 대학생 부문 · 스마트 드라이빙 & 안전

---

## 공모전 제출 정보

| 항목 | 내용 |
| --- | --- |
| 앱 이름 | 세이프 펄스 (Safe Pulse) |
| 실행 환경 | Pleos Connect Emulator **2.1.0** (Android 14, API 34), Automotive AVD |
| 화면 규격 | 2560 × 1440 (Pleos Connect AVD 기본 디스플레이) |
| 개발 환경 | Android Studio 2026.1.4, Gradle 8.10.2(래퍼 포함), AGP 8.8.2, compileSdk 35, minSdk 28, Java 17 소스 |
| 빌드 JDK | JDK 17~21 (확인: JBR 21.0.11). JDK 25는 Gradle 8.10.2가 지원하지 않습니다. |
| Pleos SDK | Vehicle SDK 2.3.2 (공식 샘플 `pleos-sample-vehicle-app`과 같은 버전) — Brake·DrivingMode 도메인 |
| APK | `apk/MONDAI_SafePulse_release.apk` (약 8MB, 디버그 키 서명) |

## 실행 방법

### 1. 에뮬레이터 준비
1. Android Studio → SDK Manager → SDK Update Sites에 Pleos Connect System Image를 추가합니다.
   - Windows/Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
   - Mac: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
2. SDK Platforms → API 34 → Pleos Connect System Image를 설치합니다.
3. Device Manager → Automotive → **Pleos Connect v2.1.0** AVD를 만듭니다(2560×1440, Cold boot, RAM 4GB 권장).

### 2. APK로 바로 실행
```bash
adb install -r apk/MONDAI_SafePulse_release.apk
```
앱 목록에서 **세이프 펄스**를 실행하고 권한 대화상자 두 개를 **허용**합니다.
- 차속 권한(`CAR_SPEED`)은 AAOS에서 **"위치 정보에 액세스"** 대화상자로 표시됩니다. 허용해야 차속을 받습니다.
- 그다음 알림 권한을 허용합니다.
- adb로 미리 허용하려면(에뮬레이터의 운전자 사용자는 10번):
  ```bash
  adb shell pm grant --user 10 com.safepulse.app android.car.permission.CAR_SPEED
  adb shell pm grant --user 10 com.safepulse.app android.permission.POST_NOTIFICATIONS
  ```

### 3. 소스에서 빌드
```bash
./gradlew :core:test           # 판별 엔진 테스트 17개(Android SDK 없이도 실행)
./gradlew installDebug         # 에뮬레이터에 설치
./gradlew exportReleaseApk     # apk/MONDAI_SafePulse_release.apk 생성(팀명은 gradle.properties의 safepulse.teamName)
```
- `JAVA_HOME`은 JDK 17~21이어야 합니다(Android Studio 내장 JDK가 25면 Gradle 8.10.2 빌드가 실패합니다).
- Pleos Nexus에 접속할 수 없는 환경에서는 `-Psafepulse.pleosSdk=false`로 빌드합니다.
  이 경우 Pleos 신호(브레이크 페달·크루즈 상태)만 빠지고, 나머지 기능과 데모는 그대로 동작합니다.
- 서명 키를 쓰려면 루트에 `keystore.properties`(storeFile, storePassword, keyAlias, keyPassword)를 둡니다.
  없으면 디버그 키로 서명합니다.

## 테스트 환경 및 시연 방법

| 방법 | 쓰는 곳 | 방법 |
| --- | --- | --- |
| **데모 시나리오** (권장) | 시연 영상, PT | 앱 홈 → 데모 시나리오 → 1~7, R 선택. 100ms 단위 주행 데이터를 재생해 매번 같은 결과가 나옵니다. |
| 실시간 모드 시연 | 실제 차량 신호로 확인 | `scripts/vhal_parking_misapplication.sh` (워치 신호까지: `--watch`). 차속·기어·**브레이크 페달**을 에뮬레이터에 넣어 개입 → 코칭 → 정차 화면까지 재현합니다. |
| 신호 하나씩 넣기 | 홈 화면 값 확인 | `scripts/vconsole.sh gear D` / `speed 20` / `brake 60` / `reset` / `state` |
| 워치 신호 주입 | 생체 신호 확인 | `scripts/wearable.sh panic` / `calm` / `off` / `stream panic 10` |
| Virtual Console 화면 | 손으로 조작 | 에뮬레이터의 Virtual Console 앱. 또는 `adb forward tcp:18088 tcp:8088` 후 PC 브라우저에서 `http://127.0.0.1:18088` |

- 스크립트는 Pleos Connect v2.1.0 에뮬레이터에 들어 있는 **Virtual Console HTTP API**(포트 8088)로 신호를 넣습니다.
  이 이미지에는 예전 방식의 `/data/vendor/vsomeip/vhal_fifo`가 없고, `cmd car_service inject-vhal-event`는 user 빌드라 막혀 있습니다.
  Windows에서는 Git Bash로 실행합니다(필요: adb, curl).
- **주차 브레이크(`PARKING_BRAKE_ON`)는 Virtual Console에 항목이 없어 실시간으로 넣을 수 없습니다.**
  실시간 모드의 정차 확인은 "차속 0 + 기어 P"로 하고, 주차 브레이크 단계는 데모 시나리오 2·R로 보여 줍니다.
- 음성: Pleos Connect 에뮬레이터 2.1.0에는 Android TTS로 쓸 수 있는 엔진이 없습니다. 소리를 내려면
  [docs/VOICE_LINES.md](docs/VOICE_LINES.md)의 녹음 파일을 `app/src/main/res/raw/`에 넣고 다시 빌드합니다(없으면 화면 안내만 나옴).

시연 영상 촬영 순서는 [docs/DEMO_SCRIPT.md](docs/DEMO_SCRIPT.md)에 있습니다.

## 에뮬레이터 확인 결과 (2026-11-14, Pleos Connect v2.1.0)

| 신호 | 앱이 받는 경로 | 결과 |
| --- | --- | --- |
| 차속 | AAOS `PERF_VEHICLE_SPEED` | ✅ Virtual Console 값이 홈 화면에 실시간 반영(정수 km/h) |
| 기어 | AAOS `GEAR_SELECTION` | ✅ P→D→P 반영 |
| 브레이크 페달 | Pleos SDK `Brake.registerBrakePedalStatus` | ✅ `onBrakePedalStatusUpdated(true/false)`, capability `READ`, 홈 화면 "밟음" 표시 |
| 크루즈 상태 | Pleos SDK `DrivingMode.registerCruiseControlState` | ✅ `ENABLED`/`ACTIVATED` 수신. ACTIVATED 중 0.38g 가속은 감지하지 않음(데모 4와 같은 동작) |
| 주차 브레이크 | AAOS `PARKING_BRAKE_ON` | ⚠️ 구독·현재값(해제) 수신. 값을 바꿀 수단이 없음 |
| 워치 | `WearableInputReceiver`(adb broadcast) | ✅ `--watch` 실시간 시연에서 손목 경직으로 즉시 개입 |
| 음성 | 녹음 파일 → Android TTS | ⚠️ TTS 엔진 없음. 녹음 파일 재생 경로(오디오 포커스·MediaPlayer)는 동작 확인 |

- 실시간 모드(`vhal_parking_misapplication.sh`): 감시 → 주의 → **개입(가속 1.5초 지속)** → 브레이크 코칭 → 실제 브레이크 신호 + 감속 확인 → 정차 → P → 안부 확인까지 7회 실행 모두 같은 흐름(워치 없이 5회, `--watch` 2회).
- AAOS `CRUISE_CONTROL_STATE`는 `READ_ADAS_STATES`(signature|privileged) 권한이 필요하고 이 에뮬레이터 VHAL에도 없어, Pleos SDK로 받습니다.

## 핵심 기능

1. **폐루프 음성 코칭.** 한 번에 한 동작만 지시하고, 차량 신호로 수행을 확인한 뒤 다음 단계로 넘어갑니다.
   지시 순서는 한국교통안전공단 권고(두 발 제동 → 멈추지 않으면 기어 N·주차 브레이크)를 따릅니다.

   | 단계 | 음성 지시 | 수행 확인 신호 |
   | --- | --- | --- |
   | 1 | 발 떼고, 두 발로 브레이크! | 브레이크 ON + 감속 → "좋아요, 계속 밟으세요." |
   | 2 | 기어 N! | GEAR_SELECTION = N |
   | 3 | 주차 브레이크 당기고 유지! | PARKING_BRAKE_ON |
   | 4 | 멈췄습니다. 기어 P, 비상등 켜세요. | 차속 0 + 기어 P 또는 EPB |

2. **골든타임 리허설.** P단에 정차해 있을 때만 실행됩니다. 실제 위기와 같은 음성으로 브레이크 → N → 주차 브레이크 → P를 연습하고, 단계별 반응 시간을 차량 신호로 측정합니다.
3. **복합 이상 감지.** 0~4단계 상태 머신으로 판별합니다. 아래 "판별 로직" 참고.
4. **수동 비상 모드.** 홈 화면의 큰 **비상** 버튼을 누르면 감지를 기다리지 않고 바로 코칭이 시작됩니다.
5. **사후 조치.**
   - 정차하면 안부를 확인하고, 30초 동안 응답이 없으면 보호자에게 알립니다(MVP에서는 실제 발송 없이 모의 처리).
   - 119·112 버튼과 사고 후 안전 가이드를 띄웁니다.
   - 감지 전 30초부터 정차 후 30초까지를 이벤트 기록으로 남깁니다.

앱은 **차량을 제어하지 않습니다.** 읽기 전용 신호를 쓰고 음성과 화면으로 안내만 합니다. 자동 신고도 하지 않습니다.

## 판별 로직

| 단계 | 진입 조건 (초기값, `RiskConfig`에서 조정) | 동작 |
| --- | --- | --- |
| 0 감시 | 시동 ON | 최근 30초 신호를 메모리에만 유지 |
| 1 주의 | 브레이크 OFF + 종방향 가속도 ≥ 0.3g + 크루즈 아님 + D·R | 무음, 기록 표시 |
| 2 개입 | 저속(≤20km/h) 또는 D·R 변속 후 5초 이내에 다음 중 하나: 1단계가 1.5초 지속 / 손목 경직 / 심박 급상승 | 미디어 정지, 음성 코칭, 긴급 화면 |
| 2 개입 | 일반 주행(>20km/h): 워치 패닉 신호 + 가속 3초 지속일 때만(워치가 없으면 기록만) | 〃 |
| 3 긴급 | 브레이크 ON인데 속도 증가(≥0.1g)가 1초 지속 | 코칭을 N·주차 브레이크 단계로 바로 전환 |
| 4 사후 | 개입 중 정차 1초 + 기어 P 또는 EPB | 정차 안내, 안부 확인, 기록 저장 |

- 가속 페달 개도량과 RPM은 서드파티 앱에 공개되지 않습니다. 그래서 **차속 변화율**과 브레이크 상태가 서로 맞지 않는 가속을 봅니다.
- 2초보다 오래된 워치 값은 판별에서 뺍니다.
- 개입이 시작되면 가속이 멈춰도 정차할 때까지 안내를 이어 갑니다.
- 운전자가 취소하면 10초 동안 다시 개입하지 않습니다.

## Pleos SDK·API 활용

| 기능 | API | 접근 |
| --- | --- | --- |
| 차속(가속도 계산) | AAOS `PERF_VEHICLE_SPEED` | 읽기 |
| 기어 | AAOS `GEAR_SELECTION` (Pleos 차량은 `CURRENT_GEAR` 미제공) | 읽기 |
| 주차 브레이크 | AAOS `PARKING_BRAKE_ON` | 읽기 |
| 크루즈 상태 | **Pleos Vehicle SDK – DrivingMode** (`registerCruiseControlState`, ACTIVATED일 때 크루즈 가속으로 봄). AAOS `CRUISE_CONTROL_STATE`는 지원 플랫폼에서만 보조로 구독 | 읽기 |
| 브레이크 페달 | **Pleos Vehicle SDK – Brake** (`registerBrakePedalStatus`, `getCurrentBrakePedalStatus`) | 읽기 |
| 음성 안내 | 녹음 음성(기본) → Android TTS 대체 (Gleo TTS ON_DEVICE는 확장 예정). 에뮬레이터 2.1.0은 TTS 엔진이 없어 녹음 파일 필요 | — |
| 미디어 정지 | AAOS Audio Focus (`AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`) | — |
| 운전자 주의 분산 | `distractionOptimized` 선언은 메인 화면만. 리허설·기록·설정은 정차 중에만 열림 | — |

### Pleos SDK 연결 방식
`PleosVehicleBridge`는 공식 샘플의 호출 순서를 따라 SDK 타입으로 직접 호출합니다:
`Vehicle(context) → initialize() → getBrake()/getDrivingMode() → register… → unregister… → release()`.
- API 이름은 Gradle 캐시의 Vehicle-2.3.2 소스와 에뮬레이터 Logcat으로 확인했습니다.
- SDK 사용 여부에 따라 소스 폴더를 고릅니다: `app/src/pleos`(SDK 호출) / `app/src/noPleos`(빈 구현, `-Psafepulse.pleosSdk=false`).
- 첫 register가 SDK 권한 확인 때문에 약 2~3초 걸려, 화면 시작을 막지 않도록 별도 스레드에서 연결합니다.
- Logcat 태그 `SafePulse/Pleos`에서 등록 결과와 콜백 값을, `SafePulse/State`에서 앱이 본 신호(차속·기어·브레이크·EPB·가속도·단계)를 볼 수 있습니다.

## 검증 결과 (판별 엔진 단위 테스트 17개 모두 통과 + 에뮬레이터 앱 재생)

| 시나리오 | 단위 테스트 | 에뮬레이터 앱(데모 메뉴, 2026-11-14) |
| --- | --- | --- |
| 1. 주차장 출차 중 페달 오조작(워치) | 2.5초에 개입(손목 경직) → 브레이크 → 정차 → 안부 확인 | 2.5초 개입 → 브레이크 → 정차 → 안부 확인 ✅ |
| 6. 같은 상황, 워치 없음 | 3.4초에 개입(1.5초 지속 규칙) → 30초 무응답 → 보호자 알림 | 3.5초 개입 → 39.8초 보호자 알림 ✅ |
| 2. 제동 무효 의심 | 긴급 단계 → 기어 N → 주차 브레이크 → 정차 | 3.2초 긴급 → N → 주차 브레이크 → 정차 ✅ |
| 3. 고속도로 추월 가속 | 개입 없음(무음 기록만) | 주의 단계만, 개입 없음 ✅ |
| 4. 크루즈 가속(운전대 꽉 잡음) | 개입 없음 | 감시 유지 ✅ |
| 5. 의도된 급출발 후 취소 | 취소 즉시 안내 중단, 10초간 재개입 없음 | 3.5초 개입 → 0.3초 뒤 취소, 재개입 없음 ✅ |
| 7. 일반 주행 가속 + 패닉 | 워치 있으면 개입, 워치 없으면 기록만 | 워치 있음 5.4초 개입 / 없음 주의 단계만 ✅ |
| R. 골든타임 리허설 | 브레이크 1.3초 · N 1.6초 · 주차 브레이크 1.2초 측정 | 브레이크 1.2초 · N 1.6초 · 주차 브레이크 1.2초(합계 4.1초) ✅ |

- 에뮬레이터 시간은 앱의 100ms 틱 위치에 따라 ±0.1초 차이가 납니다.
- 모든 개입에서 감지부터 첫 음성 지시까지 1초 이내입니다(엔진 기준 같은 100ms 틱).
- 이 밖에 오래된 워치 값 제외, 내리막 가벼운 제동 오탐 방지, 주행 중 앱 실행 시 출차 규칙 미적용, 개입 유지를 별도로 테스트합니다.
- `.github/workflows/core-tests.yml`이 push마다 이 테스트를 실행합니다.

## 프로젝트 구조

```
core/                 판별·코칭 엔진(순수 Java, Android 의존 없음)
  RiskEngine          0~4단계 상태 머신
  CoachingOrchestrator 폐루프 음성 코칭
  RehearsalSession    골든타임 리허설
  EventRecorder       이벤트 기록(JSON)
  SafePulseCore       위 넷을 묶은 진입점(앱·테스트가 같은 코드를 씀)
  resources/scenarios 데모·테스트 시나리오 CSV (tools/gen_scenarios.py로 생성)
app/                  Pleos Connect 앱
  CarSignalSource     AAOS CarPropertyManager 구독
  PleosVehicleBridge  Pleos Vehicle SDK(Brake·DrivingMode) 연결 — src/pleos(SDK), src/noPleos(빈 구현)
  SafePulseController 신호 → 엔진 → 음성·화면·기록
  VoiceOutput         녹음 음성 → TTS, 오디오 포커스
  MainActivity        홈·긴급 코칭·정차 후 화면
scripts/
  vconsole.sh                    Virtual Console로 차속·기어·브레이크 넣기
  vhal_parking_misapplication.sh 실시간 모드 시연(주차장 페달 오조작)
  wearable.sh                    워치 신호 주입
docs/                 시연 대본, 녹음 음성 목록, 수동 확인 목록(TODO_MANUAL), 작업 보고(SESSION_REPORT)
```

## 웨어러블 연동 계획

| 모드 | 수집 | 전달 |
| --- | --- | --- |
| 갤럭시워치 | Wear OS Health Services 운동 세션(심박), 가속도 센서(손목 경직·떨림) | 워치 → 안드로이드 폰(Wear OS Data Layer) → 차량 앱 |
| 애플워치 | HKWorkoutSession(심박), CoreMotion(손목 움직임) | 워치 → 아이폰(WatchConnectivity) → 차량 앱 |
| 차량 단독 | 없음 | 저속·출차 구간 감지, 리허설, 코칭, 사후 조치 모두 동작 |

- 차량 앱의 입력 경로는 `WearableInputReceiver` 하나입니다.
- MVP에서는 시나리오와 `scripts/wearable.sh`가 이 경로로 값을 넣습니다.
- 원시 생체 신호는 폰에서 처리하고, 차량에는 심박과 경직 점수만 보냅니다.

## 안전·개인정보

- 생체 신호는 판별 보조로만 쓰고 진단에 쓰지 않습니다. 설정에서 끌 수 있으며, 꺼도 핵심 기능은 동작합니다.
- 평소 신호는 저장하지 않습니다. 감지 전후 구간만 앱 내부 저장소에 기록하고, 설정에서 모두 지울 수 있습니다.
- 음성 문구는 한국교통안전공단 권고 대처법을 바탕으로 했습니다. 앱은 차량 안전 기능(PMSA 등)을 대신하지 않는 보조 기능입니다.
