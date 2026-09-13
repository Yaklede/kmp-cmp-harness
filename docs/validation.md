# 초기 구현 검증 · 2026-09-13

## 확인한 범위

| 검사 | 결과 | 증명하는 범위 |
|---|---|---|
| core JVM | 10 tests PASS | 상태 전이, 중복 클릭, 알려진 거절, 응답 유실/오프라인/타임아웃, snapshot 복원 |
| core iOS Simulator ARM64 | 같은 10 tests PASS | 공통 로직의 Kotlin/Native 실행 |
| harness CLI | 10 tests PASS | 계약, 합성 UX 결함, 미실행 gate, 소스 hash/증거 생성 |
| CMP Desktop UI | 3 tests PASS | 실제 UI action 연결, 응답 유실→조회, 실패 후 입력 보존, 큰 글씨·금액/버튼 잘림 |
| Android debug/release | 빌드 PASS | SDK 36 앱 빌드, release 의존성에 harness-cli 없음 |
| iOS UI | 컴파일·링크·XCUITest 1 test PASS | iPhone 17 / iOS 26.2에서 보이는 버튼으로 3화면 진행 |
| Android smoke | PASS | API 36 emulator에서 3화면, 메모 입력, IME 위 CTA, 완료 후 입력 보존 |
| CLI 시나리오 4개 | 각각 L0 PASS | success / declined / offline / response-lost의 Store와 Mock 서버 결과 일치 |
| 전체 CLI gate | NOT_RUN | 아직 L1/L2 driver 결과를 해당 run manifest에 연결하지 않음 |

Android 자동화는 UIAutomator의 텍스트와 실제 클릭 가능한 부모의 bounds를 사용한다.
IME window가 UI tree에서 빠지는 경우를 고려해 OS의 visible IME inset을 함께 읽고, 근거가 없으면 실패시킨다.
소스 코드의 내부 결제 action을 직접 호출하지 않는다. 화면 녹화나 스크린리더 검사는 포함하지 않는다.

## 수정하며 검출한 문제

- CMP 1.12 Android 의존성이 설치되지 않은 SDK 37 / AGP 9.1을 요구해 검증 조합을 1.11.1로 고정했다.
- 텍스트 입력 tag가 비편집 wrapper에 있어 UI 테스트가 실패했다. tag/modifier를 실제 입력 노드에 연결했다.
- iOS generic simulator 빌드가 미지원 x86_64를 요청해 host의 지원 아키텍처를 ARM64로 명시했다.
- iOS launch 시 고주사율 설정 누락을 CMP runtime이 검출했다. boolean 값이 있는 명시적 Info.plist를 추가했고 XCUITest가 통과했다.
- 2배 글씨·360dp 폭에서 금액의 숫자가 두 줄로 갈라졌다. 금액 primitive와 실제 text layout assertion으로 보정했다.

## 재현

```sh
./gradlew :core:jvmTest :core:iosSimulatorArm64Test :harness-cli:test :sharedUI:jvmTest
./gradlew :androidApp:assembleDebug :androidApp:assembleRelease :sharedUI:compileKotlinIosSimulatorArm64
./gradlew :androidApp:dependencies --configuration releaseRuntimeClasspath
./harness scenario --fixture fixtures/payment-response-lost.json
./harness scenario --require-all-gates  # 현재 기대 exit 2
./harness inspect-ui --observation fixtures/ui/keyboard-obscured.json  # 기대 exit 1
```

Android SDK 경로와 명시적으로 선택한 emulator serial을 사용한다. `android-smoke.py`는 생성된 debug APK를 설치하고
샘플 앱을 cold start한다. font scale을 1.0으로 바꾸어 실행한 뒤 원래 값을 복원한다.

```sh
python3 scripts/android-smoke.py --serial emulator-5580
```

실행 중인 iOS Simulator ID를 `xcrun simctl list devices available`로 확인한다. Intel simulator target은 구성하지 않았다.

```sh
xcodegen generate --spec iosApp/project.yml
xcodebuild -project iosApp/HarnessSample.xcodeproj -scheme HarnessSample \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -configuration Debug -derivedDataPath iosApp/build CODE_SIGNING_ALLOWED=NO test
```

JVM 테스트 XML은 각 모듈의 `build/test-results`, Desktop 캡처는 `sharedUI/build/qa`에 있다.
Android 캡처·UI tree·IME 정보는 `artifacts/native-smoke/android`, iOS XCTest 첨부는 `iosApp/build/Logs/Test/*.xcresult`에 있다.
CLI는 매번 UUID별 별도 artifact를 만든다. 이 디렉터리들은 gitignore 대상이며 원본 디자인/승인 baseline이 아니다.

## 아직 확인하지 않은 범위

Hot Reload MCP, 디자인/Figma import, 승인 baseline 비교, 범용 UI driver, UX-observer 에이전트, Codex 자동 수정 루프,
실제 API/PG, 실제 휴대폰, VoiceOver/TalkBack, iOS swipe back, 앱 프로세스 종료 후 영속 복원은 미구현 또는 미검증이다.
Store의 snapshot 복원 테스트는 있지만 앱 host의 durable 저장/복원은 아직 연결하지 않았다.
모든 앱은 프로세스 내 Mock ledger만 쓰며, 샘플 앱의 cold start는 새로운 fixture로 시작한다.

iOS deployment target은 16.0이지만 실제 실행한 OS는 26.2뿐이다. Skiko의 ICU data object에서 iOS 18.5 대상으로 빌드됐다는
링커 경고가 있어 이전 OS 지원은 별도 검증이 필요하다. 현재 테스트를 iOS 16 실기기 지원 보장으로 해석하지 않는다.

합성 UX fixture에서 검출한 6개 규칙은 실제 native 화면에서 모두 검출했다고 주장하지 않는다.
초기 native smoke 결과도 전체 L2 접근성·수명주기·오류 복구 gate를 대신하지 않는다.
