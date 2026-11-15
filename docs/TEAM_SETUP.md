# 팀원용 실행 환경 설정 가이드 (세이프 펄스 · 팀 MONDAI)

이 문서를 따라 하면 자기 PC에서 Pleos Connect 에뮬레이터를 띄우고 세이프 펄스 앱을 실행할 수 있다.
걸리는 시간은 1~2시간이고, 대부분은 다운로드 시간이다(에뮬레이터 이미지 3.8GB).
팀장의 노트북에서 실제로 겪은 문제와 해결법을 함께 적었다.

## 0. 준비물

| 항목 | 기준 |
|---|---|
| PC | Windows 10/11 64비트(Intel·AMD) 권장. Mac(Apple Silicon)도 가능(3단계 주소만 다름) |
| 메모리 | 16GB 권장 (에뮬레이터가 4GB 사용) |
| 저장 공간 | C 드라이브 여유 20GB 이상 |
| 네트워크 | 빠른 Wi-Fi 또는 유선. 느린 네트워크에서는 다운로드가 몇 시간 걸리거나 시간 초과가 난다 |
| 계정 | GitHub 계정(저장소 초대 수락), Git 설치 |

> **Windows 사용자 이름이 한글이면** 꼭 4단계의 `ANDROID_AVD_HOME` 설정을 먼저 한다. 한글 경로에 가상 기기가 만들어지면 에뮬레이터가 켜지지 않는 경우가 많다.

## 1. 저장소 받기

1. 이메일로 온 GitHub 초대를 **수락**한다.
2. PowerShell에서 한글이 없는 경로로 받는다.
   ```powershell
   git clone https://github.com/mondai-safepulse/safe-pulse.git C:\dev\safe-pulse
   ```
   처음이면 GitHub 로그인 창이 뜬다 → **Sign in with your browser** → 로그인 → Authorize.

## 2. Android Studio 설치

1. developer.android.com/studio 에서 최신 Android Studio를 설치한다.
2. 첫 실행 마법사에서 **SDK 위치를 `C:\Android\Sdk`** 처럼 한글·공백이 없는 경로로 정한다.
3. 프로젝트 열기: ☰ → File → Open → `C:\dev\safe-pulse` → **This Window** → **Trust Project**.

### Java 버전 (가장 많이 막히는 부분)
- 이 프로젝트는 Gradle 8.10.2를 쓰고, **Java 21**이 필요하다. 최신 Android Studio에 들어 있는 Java는 25라서 그대로는 Sync가 실패한다.
- 프로젝트를 열 때 "Please Select Gradle JVM … **Use JVM 21**"이 뜨면 그걸 누른다.
- 안 뜨면: ☰ → Settings → Build, Execution, Deployment → Build Tools → **Gradle** → **Gradle JDK** → 목록에서 21 선택. 없으면 **Download JDK…** → Version **21** → 다운로드.
- **AGP 업그레이드 / New Gradle Version 알림은 누르지 않는다.** 버전은 공식 샘플과 맞춰 둔 것이다.

### Sync
- 오른쪽 위 **코끼리 아이콘**(Sync Project with Gradle Files)을 누른다. 처음에는 10분 이상 걸릴 수 있다.
- 아래 Build 창에 초록 체크가 뜨고 왼쪽에 `app`, `core`가 보이면 성공.
- `Read timed out`, `SocketTimeoutException`이 나면 네트워크 문제다. 휴대폰 핫스팟이나 다른 Wi-Fi로 바꾸고 다시 Sync.

## 3. Pleos 에뮬레이터 이미지 설치

1. ☰ → Tools → **SDK Manager** → **SDK Update Sites** 탭 → **+**
   - Name: `Pleos Connect System Image`
   - URL (Windows): `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
   - URL (Mac): `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
   - OK → **Apply**
2. **SDK Platforms** 탭 → 오른쪽 아래 **Show Package Details** 체크
   - **Android 14.0** 묶음의 **Pleos Connect v2.1.0 APIs … System Image** 체크
   - **Android 15.0** 묶음의 **Android SDK Platform 35** 체크 (`35-ext…`가 아니라 API Level이 정확히 `35`인 것)
3. OK → OK → 라이선스 **Accept** → 다운로드(3.8GB) → **Finish** → Android Studio 재시작.
   - 목록에 Pleos 이미지가 안 보이면 SDK Manager를 닫았다 다시 열거나, **Hide incompatible system images** 체크를 풀어 본다.

## 4. 환경 변수 (Windows, 한 번만)

PowerShell에 붙여 넣는다. 가상 기기 저장 위치를 영어 경로로 바꾸고, `adb` 명령을 쓸 수 있게 한다.

```powershell
New-Item -ItemType Directory -Force C:\Android\avd | Out-Null
[Environment]::SetEnvironmentVariable("ANDROID_AVD_HOME", "C:\Android\avd", "User")
$p = [Environment]::GetEnvironmentVariable("Path", "User")
if ($p -notlike "*C:\Android\Sdk\platform-tools*") { [Environment]::SetEnvironmentVariable("Path", "$p;C:\Android\Sdk\platform-tools", "User") }
```

설정 후 **Android Studio를 완전히 껐다 다시 켠다**(☰ → File → Exit). 새로 연 PowerShell 창부터 적용된다.

> 터미널에서 `./gradlew`로 직접 빌드할 사람만: `JAVA_HOME`을 JDK 21 폴더로 지정해야 한다(Android Studio가 받은 JDK는 보통 `%USERPROFILE%\.jdks\` 아래에 있다).

## 5. 가상 차량(AVD) 만들기

1. ☰ → Tools → **Device Manager** → **+** → **Create Virtual Device**
2. Form Factor **Automotive** → **Pleos Connect v2.1.0** (2560×1440) → Next
3. System image: **Pleos Connect v2.1.0 APIs Intel x86_64 Atom System Image** (API 34)
4. **Additional settings** 탭: Default boot **Cold**, RAM **4 GB** → **Finish**
5. 목록의 **▶** → 첫 부팅 3~5분. 자동차 이미지가 있는 홈 화면이 뜨면 성공.
   - "virtualization", "hypervisor" 오류가 나면 BIOS의 가상화(VT-x/AMD-V)와 Windows 기능의 "Windows 하이퍼바이저 플랫폼"을 켠다.

## 6. 앱 실행

방법 A (Android Studio에서 바로 실행)
- 위쪽 실행 구성이 `app`, 기기가 `Pleos Connect v2.1.0`인지 확인하고 **▶ Run**.

방법 B (제출용 APK 설치)
```powershell
cd C:\dev\safe-pulse
adb devices
adb install -r apk\MONDAI_SafePulse_release.apk
```
에뮬레이터 앱 목록에서 **세이프 펄스**를 실행하고, 권한(차속·알림)을 허용한다.

## 7. 시연해 보기

- 앱 홈 → **데모 메뉴** → 시나리오 1~7, R(리허설)을 하나씩 재생한다. 기대 동작은 `README.md`의 "검증 결과" 표 참고.
- 실시간 신호(기어·차속·브레이크)를 직접 넣어 보려면 **Git Bash**에서 `scripts/` 폴더의 스크립트를 쓴다(`vconsole.sh`, `vhal_parking_misapplication.sh`). 사용법은 각 스크립트 맨 위 주석과 `docs/SESSION_REPORT.md` 참고. 에뮬레이터의 Virtual Console 화면에서 손으로 조작해도 된다.
- 영상 대본은 `docs/DEMO_SCRIPT.md`.

## 8. 자주 막히는 곳 요약

| 증상 | 원인 | 해결 |
|---|---|---|
| Sync 시 JVM 25 관련 오류 | Java 25는 Gradle 8.10.2 미지원 | Gradle JDK를 21로 (2단계) |
| Gradle/이미지 다운로드 시간 초과 | 네트워크 | 다른 네트워크·핫스팟 |
| 에뮬레이터가 안 켜짐 | 한글 사용자 경로 / 가상화 꺼짐 | 4단계 `ANDROID_AVD_HOME`, BIOS 가상화 |
| Pleos 이미지가 목록에 없음 | 업데이트 사이트 미반영 | SDK Manager 재실행, Hide incompatible 해제 |
| `adb`를 찾을 수 없음 | Path 미적용 | 4단계 후 PowerShell 새로 열기 |
| 노트북이 잠들어 작업이 멈춤 | 절전 | 설정 → 시스템 → 전원 → 전원 연결 시 절전 "안 함" |

## 9. 주의

- `main` 브랜치에 바로 push하기 전에 팀장과 이야기한다. 마감(11/15) 직전에는 **최종 커밋 해시**가 접수 메일에 들어간다.
- `keystore.properties`, `*.jks` 같은 서명 키 파일은 절대 커밋하지 않는다.
- 차량 제어 API는 쓰지 않는다(읽기 전용 + 안내만).
