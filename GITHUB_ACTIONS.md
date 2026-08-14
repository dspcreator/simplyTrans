# GitHub Actions APK 빌드

이 버전은 `gradlew`가 필요하지 않습니다.

GitHub Actions가 `gradle/actions/setup-gradle@v4`를 이용해 **Gradle 8.9를 직접 설치**한 뒤 다음 명령으로 APK를 빌드합니다.

    gradle --no-daemon assembleDebug
    gradle --no-daemon assembleRelease

## 반드시 확인할 저장소 구조

저장소 최상위에 다음처럼 있어야 합니다.

    .github/
      workflows/
        build-apk.yml
    app/
      build.gradle
      src/
    build.gradle
    settings.gradle
    gradle.properties

`.github/workflows/build-apk.yml`은 반드시 GitHub 저장소에 commit되어 있어야 합니다.

## APK 받기

1. GitHub → Actions
2. `Build TextBot APK`
3. `Run workflow`
4. 초록색 체크가 뜬 실행을 클릭
5. 아래 `Artifacts`
6. `TextBot-APKs` 다운로드
7. 압축을 풀고 `app-debug.apk` 설치

이 workflow에서는 `./gradlew` 또는 `chmod +x ./gradlew`를 사용하지 않습니다.
