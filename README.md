# TextBot Android — Full Screen Translation

기존 TextBot HTML을 Android WebView 앱으로 패키징하고 Android MediaProjection을 실제 화면 캡처와 연결한 프로젝트입니다.

## 구현 내용

- URL 입력 및 모바일 WebView 표시
- 기존 이미지 캡처 업로드 번역
- Gemini / OpenAI / Grok 호출 구조 유지
- 효과음 번역 토글 유지
  - 켜짐: `효과음 번역 ✅️`
  - 꺼짐: `효과음 번역 ☑️`
- Android MediaProjection 권한 요청
- Android 14+ foreground service `mediaProjection` 사용
- 화면 캡처 프레임을 ImageReader로 수집
- `현재 화면 번역` 버튼을 누르면 최신 화면을 JPEG로 캡처
- 캡처 이미지를 WebView JavaScript에 전달
- 기존 Vision 번역 로직으로 좌표 기반 번역 오버레이 생성
- GitHub Actions에서 APK 자동 빌드
- 녹색 사각형 + 🇯🇵 🇰🇷 🇬🇧 🇨🇳 2×2 앱 아이콘

## GitHub에서 APK 만들기

1. 이 폴더의 모든 파일을 GitHub 저장소에 업로드합니다.
2. 저장소의 `Actions` 탭을 엽니다.
3. `Build TextBot APK`를 선택합니다.
4. `Run workflow`를 누릅니다.
5. 완료되면 `TextBot-APKs` artifact를 내려받습니다.

## Android 사용 순서

1. 앱 실행
2. URL 입력
3. `이동`
4. `화면 공유`를 누릅니다.
5. Android 시스템 화면 캡처 권한에서 허용합니다.
6. 웹페이지가 표시된 상태에서 `현재 화면 번역`을 누릅니다.
7. 앱이 현재 화면을 캡처하여 Vision API로 보내고 번역문을 화면 위에 표시합니다.

## API 키

API 키는 기존 HTML의 localStorage 설정을 그대로 사용합니다. 앱 내부에 API 키를 하드코딩하지 않습니다.

## 주의

MediaProjection은 Android 시스템 화면을 캡처하므로 상태바/앱 UI 등 화면에 보이는 영역도 캡처될 수 있습니다. 번역 프롬프트는 화면의 실제 텍스트만 대상으로 하도록 구성되어 있습니다.

Google Play 배포용 서명 APK를 만들려면 별도의 signing key/Gradle signing 설정이 필요합니다. GitHub Actions에서는 현재 debug APK와 unsigned release APK를 생성합니다.
