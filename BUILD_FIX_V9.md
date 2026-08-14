SimplyTsL v9 build/runtime fix.

- Keeps the v8 hamburger-only overlay UI.
- Requests overlay permission on first launch when missing.
- Translation permission is requested only after tapping 번역하기.
- MediaProjection foreground-service mode is enabled only after Android grants the projection token.
- GitHub Actions prints the full Gradle stacktrace and builds `:app:assembleDebug`.
