# 제출용 APK

`./gradlew exportReleaseApk` 를 실행하면 이 폴더에 `MONDAI_SafePulse_release.apk` 가 만들어진다(공모전 파일명 규칙 `팀명_앱이름_release.apk`).
팀명은 `gradle.properties` 의 `safepulse.teamName=MONDAI` 에서 읽는다.

- 서명: 루트에 `keystore.properties` 가 없으면 디버그 키로 서명한다(에뮬레이터 설치·시연에는 문제없음).
- 크기: 약 8MB. 100MB를 넘으면 GitHub Releases에 올리고 README에 링크를 적는다.
- 설치: `adb install -r apk/MONDAI_SafePulse_release.apk`
