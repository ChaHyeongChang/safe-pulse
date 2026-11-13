# Claude Code 프롬프트 — 로컬 빌드·검증·제출 (11/12~11/15)

- 권장 effort: **high** (Pleos SDK 실물 확인과 빌드 오류 수정이 섞여 있어 판단이 필요한 작업. max까지는 필요 없음)
- 실행 위치: Android Studio와 Pleos Connect Emulator 2.1.0이 설치된 **본인 PC**, 이 저장소 루트
- Plan 모드로 시작해 계획을 확인한 뒤 진행

아래부터 그대로 붙여 넣기 ↓

```
이 저장소는 현대차그룹 차량용 앱 공모전 출품작 "세이프 펄스" MVP다. README.md를 먼저 읽어.
core/ 는 순수 Java 판별·코칭 엔진(테스트 17개), app/ 은 Pleos Connect(AAOS) 앱이다.
코드는 Android SDK·Pleos Nexus 없이 작성됐기 때문에, 이 PC에서 실제 빌드와 에뮬레이터 동작을 확인하고 고치는 것이 목표다.
마감은 2026-11-15(일). 먼저 계획을 보여 주고 승인 받은 뒤 진행해.

규칙
- core/ 의 판별 로직을 바꾸면 반드시 ./gradlew :core:test 를 돌려 17개 모두 통과시킨다. 테스트를 지우거나 기대값을 느슨하게 바꾸지 않는다.
- 차량 제어 API는 쓰지 않는다(읽기 전용 + 안내만). 매니페스트에 CONTROL_ 권한을 추가하지 않는다.
- Pleos API 이름은 지어내지 말고, Android Studio 자동완성·SDK 클래스·Logcat("SafePulse/Pleos")으로 확인한다.

작업
1. Gradle Sync와 ./gradlew :core:test assembleDebug. 빌드 오류가 나면 원인을 설명하고 최소 수정으로 고친다.
   (useLibrary("android.car"), AGP 8.8.2, Gradle 8.10.2, Vehicle SDK 2.3.2 기준. 버전 문제면 공식 샘플 Pleos-Playground/pleos-sample-vehicle-app 과 맞춘다.)
2. Pleos Connect v2.1.0 AVD에 installDebug 후 실행. 권한(차속·알림) 허용.
   - Virtual Console에서 기어 P→D, 차속, 주차 브레이크를 바꿔 홈 화면 값이 바뀌는지 확인한다.
   - 안 바뀌면 CarSignalSource 의 구독 결과 로그(SafePulse/Car)를 보고 원인을 찾는다.
3. Logcat "SafePulse/Pleos"에서 Brake 도메인 메서드 목록과 브레이크 콜백 값을 확인한다.
   - Virtual Console에서 브레이크를 밟았을 때 앱의 "브레이크" 표시가 "밟음"으로 바뀌어야 한다.
   - 콜백 인자 형태가 PleosVehicleBridge.interpret() 해석과 다르면 그 부분만 고친다.
   - 가능하면 리플렉션을 SDK의 타입 있는 호출(공식 샘플의 Door Position 방식)로 바꾸되, safepulse.pleosSdk=false 빌드도 계속 되게 한다.
4. 앱 홈 → 데모 시나리오 1~7, R을 하나씩 재생해 README "검증 결과" 표와 같은 동작인지 확인한다. 음성이 안 나오면 TTS 엔진·한국어 음성을 확인한다.
5. scripts/vhal_parking_misapplication.sh 를 실행해 실시간 모드에서도 개입·코칭·정차 화면이 나오는지 확인한다. 주입 형식이 다르면 스크립트를 고친다.
6. ./gradlew exportReleaseApk -Psafepulse.teamName=MONDAI 으로 apk/MONDAI_SafePulse_release.apk 를 만들고, 에뮬레이터에서 기존 앱을 지운 뒤 이 APK로 다시 설치해 실행 확인.
7. README의 "실행 방법·테스트 환경" 이 실제와 다른 부분을 고치고, 확인 결과(어떤 신호가 실제로 들어왔는지)를 README에 짧게 적는다.
8. git 커밋(한국어 메시지). GitHub 원격 저장소 주소를 물어본 뒤 push. 마지막에 최종 커밋 해시를 알려 줘(접수 메일에 적어야 함).
```
