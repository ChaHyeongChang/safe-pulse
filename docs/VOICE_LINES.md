# 음성 안내 파일 목록

핵심 지시는 SDK나 네트워크와 상관없이 재생돼야 하므로, 아래 문장의 음성 파일을
`app/src/main/res/raw/` 에 넣으면 앱이 TTS 대신 이 파일을 우선 재생한다.
파일이 없으면 Android 기본 TTS가 같은 문장을 읽는다. 단, Pleos Connect 에뮬레이터 2.1.0에는 Android TTS로 쓸 수 있는 엔진이 없다
(유일한 엔진 `ai.pleos.playground.caas` 의 인텐트 필터에 CATEGORY_DEFAULT가 없음). 그래서 이 파일들이 있어야 소리가 난다.

## 지금 들어 있는 파일 (2026-11-14)

**Windows 내장 한국어 TTS(Microsoft Heami Desktop, System.Speech)로 만든 WAV 12개**가 들어 있다.
에뮬레이터에서 데모 1·5·6·R과 실시간 리허설 중단으로 **12개 모두** 재생되는 것을 Logcat(`SafePulse/Voice: play clip vo_…`)과
`dumpsys audio`(MediaPlayer `state:started`)로 확인했다. 실제 소리(발음·속도)는 사람이 들어 봐야 한다.

다시 만들기(문장은 `Prompt.java` 에서 읽는다):

```powershell
powershell -ExecutionPolicy Bypass -File tools\gen_voice_windows_tts.ps1
```

- 44.1kHz · 16bit · 모노 WAV, 앞 약 0.15초 · 뒤 약 0.23초 무음(재생 시작·끝에서 잘리지 않게).
- 문장 사이 쉼은 엔진 기본(약 0.5초) 대신 마침표 0.18초 · 쉼표 0.1초로 줄였다.
- 위기 코칭은 앞 안내를 끊고 말하므로, 엔진 시간표보다 긴 문장은 말 속도를 올려 맞췄다(아래 "길이 상한").
- **라이선스:** 상용 TTS 음성 출력물을 공모전 영상·APK에 써도 되는지 대회 규정과 Microsoft 약관을 확인한다.
  팀원 목소리로 녹음해 같은 이름으로 바꾸는 것이 가장 안전하다.

| 파일 이름 | 문장 | 쓰이는 곳 | 길이(무음 포함) | 말 속도 |
| --- | --- | --- | --- | --- |
| `vo_brake.wav` | 발 떼고, 두 발로 브레이크! | 코칭 1단계 · 리허설 | 1.98초 | +2 |
| `vo_brake_good.wav` | 좋아요, 계속 밟으세요. | 감속 확인 | 2.04초 | 0 |
| `vo_neutral.wav` | 기어 N! | 코칭 2단계 · 리허설 | 0.86초 | 0 |
| `vo_epb.wav` | 주차 브레이크 당기고 유지! | 코칭 3단계 · 리허설 | 2.01초 | +1 |
| `vo_stopped.wav` | 멈췄습니다. 기어 P, 비상등 켜세요. | 정차 | 2.43초 | +4 |
| `vo_safe.wav` | 안전하게 정차했습니다. 괜찮으세요? | 안부 확인 | 3.18초 | 0 |
| `vo_guardian.wav` | 응답이 없어 보호자에게 알렸습니다. | 보호자 알림 | 2.89초 | 0 |
| `vo_cancel.wav` | 안내를 멈춥니다. | 운전자 취소 | 1.62초 | 0 |
| `vo_rehearsal_start.wav` | 리허설을 시작합니다. 실제 상황과 같은 안내가 나옵니다. | 리허설 | 2.93초 | +5 |
| `vo_back_to_p.wav` | 기어를 P로 돌려 주세요. | 리허설 | 1.92초 | 0 |
| `vo_rehearsal_done.wav` | 리허설 끝. 잘하셨어요. | 리허설 | 2.20초 | 0 |
| `vo_rehearsal_abort.wav` | 차가 움직여서 리허설을 멈춥니다. | 리허설 | 2.71초 | 0 |

## 길이 상한 (녹음으로 바꿀 때도 지킨다)

엔진(`RiskConfig`)은 코칭 안내를 정해진 간격으로 내고, 새 안내가 재생 중인 안내를 끊는다. 그래서 아래보다 길면 끝이 잘린다.

| 파일 | 상한(앞 무음 + 말소리) | 이유 |
| --- | --- | --- |
| brake · brake_good · neutral · epb | 1.9초 | `promptRepeatMs` 2초: 확인될 때까지 2초마다 같은 지시를 다시 낸다 |
| stopped | 2.4초 | `stoppedToSafeMs` 2.5초 뒤 안부 확인 안내가 이어진다 |
| rehearsal_start | 2.85초(뒤 무음까지 3초 안) | `rehearsalIntroMs` 3초 뒤 첫 동작 안내와 반응 시간 측정이 시작된다 |

## 녹음으로 바꿀 때

- 낮고 침착한 목소리, 일정한 속도. 위 길이 상한을 지킨다.
- 모노, 44.1kHz 또는 48kHz. 앞뒤 무음은 0.1~0.2초.
- `.ogg`·`.mp3`·`.wav` 모두 된다. 파일 이름은 소문자와 밑줄만 쓴다.
  **같은 이름의 `.wav` 를 지우고 넣는다**(예: `vo_brake.wav` 와 `vo_brake.ogg` 가 같이 있으면 리소스 이름이 겹쳐 빌드가 실패한다).
- 문장을 바꾸면 `core/src/main/java/com/safepulse/core/Prompt.java` 의 문장도 같이 바꾼다(TTS 대체용·생성 스크립트 입력).
