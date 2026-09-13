# KMP / CMP 결제 예제

이 디렉터리는 하네스 제약조건을 설명하기 위한 선택적 예제입니다. OpenDock 배포에 포함되지 않습니다.
`harness-cli`는 이 결제 모델에 결합된 예제 검증기이며 다른 프로젝트용 범용 CLI가 아닙니다.
디자인 의도와 UX 계약을 분리하고, 같은 공통 로직을 앱과 headless 검증에서 실행합니다.
첫 샘플은 **계약 상세 → 결제 확인 → 결제 결과**이며 실제 결제나 운영 API를 호출하지 않습니다.

## 구조

- `core`: UI와 플랫폼을 모르는 State / Action / Effect, Mock repository
- `sharedUI`: 공통 디자인 토큰, 컴포넌트, 화면; Android / iOS / JVM
- `androidApp`, `iosApp`, `desktopApp`: 플랫폼 진입점
- `harness-cli`: 개발 전용 계약 검사·시나리오·증거 기록. 앱이 이 모듈을 의존하지 않음
- `contracts`, `fixtures`: 검증 입력. 생성 화면을 승인 baseline으로 취급하지 않음

## 실행

저장소 루트에서 먼저 `cd examples/payment`를 실행합니다. 아래 명령은 모두 예제 루트 기준입니다.

JDK 17+, Android SDK 36, iOS 빌드에는 macOS + Xcode가 필요합니다.
Android SDK 위치를 `local.properties`의 `sdk.dir` 또는 `ANDROID_HOME`으로 설정합니다.

```sh
./gradlew :desktopApp:run
./gradlew :androidApp:assembleDebug
./gradlew :core:jvmTest :harness-cli:test
./gradlew :sharedUI:jvmTest
./gradlew :sharedUI:compileKotlinIosSimulatorArm64
cd iosApp
xcodegen generate
xcodebuild -project HarnessSample.xcodeproj -scheme HarnessSample -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO build
```

```sh
./harness scenario --fixture fixtures/payment-response-lost.json
./harness inspect-ui --observation fixtures/ui/keyboard-obscured.json
```

첫 명령은 L0 로직 검증과 증거를 생성합니다. 두 번째는 심어 둔 결함을 검출하고 exit 1로 끝나는 것이 기대 결과입니다.
전체 native/visual 판정은 CLI driver가 연결되지 않아 `NOT_RUN`입니다.
시나리오 증거의 `artifacts/`와 소스 해시는 이 예제를 포함한 Git 저장소 루트를 기준으로 기록됩니다.

설계와 단계별 상태는 [구현 계획](docs/implementation-plan.md), 버전 선택은 [도구 체인](docs/toolchain.md)을 참고합니다.
[검증 CLI](docs/harness.md), [디자인 시스템](docs/design-system.md), [검증 결과와 재현](docs/validation.md)도 함께 제공합니다.
