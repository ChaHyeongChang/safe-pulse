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
| 개발 환경 | Android Studio, Gradle 8.10.2(래퍼 포함), AGP 8.8.2, Java 17, minSdk 28 |
| Pleos SDK | Vehicle SDK 2.3.2 (공식 샘플 `pleos-sample-vehicle-app`과 같은 버전) |
| APK | `apk/<팀명>_SafePulse_release.apk` (100MB 초과 시 GitHub Releases 링크로 대체) |

## 실행 방법

### 1. 에뮬레이터 준비
1. Android Studio → SDK Manager → SDK Update Sites에 Pleos Connect System Image를 추가합니다.
   - Windows/Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
   - Mac: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
2. SDK Platforms → API 34 → Pleos Connect System Image를 설치합니다.
3. Device Manager → Automotive → **Pleos Connect v2.1.0** AVD를 만듭니다(Cold boot, RAM 4GB 권장).

### 2. APK로 바로 실행
```bash
adb install -r apk/<팀명>_SafePulse_release.apk
```
앱 목록에서 **세이프 펄스**를 실행하고 권한(차속, 알림)을 허용합니다.

### 3. 소스에서 빌드
```bash
./gradlew :core:test                         # 판별 엔진 테스트(Android SDK 없이도 실행)
./gradlew installDebug                       # 에뮬레이터에 설치
./gradlew exportReleaseApk -Psafepulse.teamName=<팀명>   # apk/ 폴더에 제출용 APK 생성
```
- Pleos Nexus에 접속할 수 없는 환경에서는 `-Psafepulse.pleosSdk=false`로 빌드합니다.
  이 경우 브레이크 신호만 빠지고, 나머지 기능과 데모는 그대로 동작합니다.
- 서명 키를 쓰려면 루트에 `keystore.properties`(storeFile, storePassword, keyAlias, keyPassword)를 둡니다.
  없으면 디버그 키로 서명합니다.

## 테스트 환경 및 시연 방법

| 방법 | 쓰는 곳 | 방법 |
| --- | --- | --- |
| **데모 시나리오** (권장) | 시연 영상, PT | 앱 홈 → 데모 시나리오 → 1~7, R 선택. 100ms 단위 주행 데이터를 재생해 매번 같은 결과가 나옵니다. |
| VHAL 주입 | 실시간 모드 확인 | `scripts/vhal_parking_misapplication.sh` (차속·기어·EPB를 에뮬레이터에 주입) |
| 워치 신호 주입 | 생체 신호 확인 | `scripts/wearable.sh panic` / `calm` / `off` / `stream panic 10` |
| Virtual Console | 브레이크 페달 | Pleos Connect Virtual Console에서 조작(브레이크는 Vehicle SDK 값이라 VHAL 스크립트로는 넣을 수 없음) |

시연 영상 촬영 순서는 [docs/DEMO_SCRIPT.md](docs/DEMO_SCRIPT.md)에 있습니다.

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
| 크루즈 상태 | AAOS `CRUISE_CONTROL_STATE` (지원 차량만) | 읽기 |
| 브레이크 페달 | **Pleos Vehicle SDK – Brake** (`Vehicle.getBrake()` → register…BrakePedal…) | 읽기 |
| 음성 안내 | 녹음 음성(기본) → Android TTS 대체 (Gleo TTS ON_DEVICE는 확장 예정) | — |
| 미디어 정지 | AAOS Audio Focus (`AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`) | — |
| 운전자 주의 분산 | `distractionOptimized` 선언은 메인 화면만. 리허설·기록·설정은 정차 중에만 열림 | — |

### Pleos SDK 연결 방식
`PleosVehicleBridge`는 공식 샘플의 호출 순서를 따릅니다: `Vehicle(context) → initialize() → getBrake() → register → unregister → release()`.
리스너 콜백의 형태가 SDK 버전마다 달라질 수 있어 리플렉션과 동적 프록시로 연결합니다. 그래서 SDK가 없거나 버전이 달라도 빌드와 데모가 깨지지 않습니다.
Logcat 태그 `SafePulse/Pleos`에서 Brake 도메인의 메서드 목록과 실제 콜백 값을 볼 수 있습니다.

## 검증 결과 (판별 엔진 단위 테스트 17개, 모두 통과)

| 시나리오 | 결과 |
| --- | --- |
| 1. 주차장 출차 중 페달 오조작(워치) | 2.5초에 개입(손목 경직) → 브레이크 → 정차 → 안부 확인 |
| 6. 같은 상황, 워치 없음 | 3.4초에 개입(1.5초 지속 규칙) → 30초 무응답 → 보호자 알림 |
| 2. 제동 무효 의심 | 긴급 단계 → 기어 N → 주차 브레이크 → 정차 |
| 3. 고속도로 추월 가속 | 개입 없음(무음 기록만) |
| 4. 크루즈 가속(운전대 꽉 잡음) | 개입 없음 |
| 5. 의도된 급출발 후 취소 | 취소 즉시 안내 중단, 10초간 재개입 없음 |
| 7. 일반 주행 가속 + 패닉 | 워치 있으면 개입, 워치 없으면 기록만 |
| R. 골든타임 리허설 | 브레이크 1.3초 · N 1.6초 · 주차 브레이크 1.2초 측정 |

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
  PleosVehicleBridge  Pleos Vehicle SDK(Brake) 연결
  SafePulseController 신호 → 엔진 → 음성·화면·기록
  VoiceOutput         녹음 음성 → TTS, 오디오 포커스
  MainActivity        홈·긴급 코칭·정차 후 화면
scripts/              VHAL·워치 신호 주입 스크립트
docs/                 시연 대본, 녹음 음성 목록
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
