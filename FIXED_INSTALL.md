# 앱이 열리지 않는 문제 수정본

이전 패키지에서 Android Java 소스가 누락될 수 있는 문제가 있었습니다.
이 수정본에는 MainActivity.java와 ScreenCaptureService.java가 포함되어 있습니다.

GitHub에 기존 파일을 덮어쓴 뒤:
Actions → Build TextBot APK → Run workflow

로 새 APK를 빌드하세요.

테스트 설치는 `app-debug.apk`를 사용하세요.
`app-release-unsigned.apk`는 서명되지 않았으므로 일반적인 배포/설치용으로 사용하지 마세요.
