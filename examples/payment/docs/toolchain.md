# 도구 체인

고정 조합: Kotlin 2.3.21, Compose Multiplatform 1.11.1, Gradle 9.1.0, AGP 9.0.0.
JVM bytecode / toolchain 17, Android compile/target SDK 36, min SDK 24, iOS deployment 16.
호스트: Apple Silicon, Xcode 26.2, JBR 21.0.7 설치 확인.

Compose compiler는 Kotlin 버전과 동일하게 고정한다. Material은 CMP stable 배포를 사용해 초기 UI에 alpha Material3 의존성을 넣지 않는다.
Hot Reload MCP는 Desktop 전용 실험 기능으로 후속 검증한다. 설치된 JBR 버전에서 모든 reload가 된다고 가정하지 않는다.

공식 근거 (2026-09-13 확인):

- [KMP 버전 호환성](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html)
- [CMP 호환성](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)
- [CMP 1.12 변경 사항](https://kotlinlang.org/docs/multiplatform/whats-new-compose-112.html)
- [Android KMP 플러그인](https://developer.android.com/kotlin/multiplatform/plugin)
- [Compose Hot Reload](https://kotlinlang.org/docs/multiplatform/compose-hot-reload.html)

CMP 1.12.0 / Kotlin 2.4.20 후보로 먼저 빌드했으나 Android AAR metadata 검사가 SDK 37 및 AGP 9.1.0 이상을 요구했다.
설치된 SDK 36 / Xcode 26.2에서 검증 가능한 범위를 우선해 CMP 1.11.1 / Kotlin 2.3.21로 내렸다.
CMP 1.12 및 Hot Reload MCP 업그레이드는 SDK/AGP/Xcode 조합과 함께 별도 체크포인트에서 검증한다.
버전 표만으로 호환성을 확정하지 않고 실제 Android / iOS / JVM 컴파일·링크 결과를 기록한다.
