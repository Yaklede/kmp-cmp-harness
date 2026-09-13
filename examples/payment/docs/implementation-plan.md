> 이 문서는 결제 예제의 최초 구현 계획과 이력입니다. 범용 배포물은 저장소 루트의 `dock/` 제약조건 패키지이며, 예제 앱과 CLI는 그 배포물에 포함되지 않습니다.

# 구현 계획과 체크포인트

참조: ChatGPT 대화 `6aa6a13a-1038-83ee-a276-4f91be11cd54`, 「Shopify 네이티브 전환 분석」의 마지막 상세 계획.

## 변경하지 않는 원칙

1. 앱과 engineering 하네스는 동일 Store / UseCase를 실행한다.
2. 응답 불명은 실패가 아니다. 같은 operation을 조회하고 재결제를 유도하지 않는다.
3. 실제 사용자 입력 검증과 내부 action 검증 결과를 구분한다.
4. PASS / FAIL / REVIEW / UNSUPPORTED / NOT_RUN을 구분한다. 필수 검사 미실행은 합격이 아니다.
5. 원본 디자인, 승인 baseline, 실행 증거를 구분한다. 초기 샘플 UI는 승인된 디자인이 아니다.
6. 중요 정책은 제품 소유자가 결정한다. 자동 보정 A/B, 흐름 변경 C, 중요 정책 D를 구분한다.
7. 개발 제어 기능은 앱 release 의존성에 넣지 않는다.

## 첫 구현 범위

| 커밋 단위 | 구현 | 확인 방법 |
|---|---|---|
| 환경 | Gradle wrapper, KMP core, CMP UI, 3개 host | Android/JVM/iOS 빌드 |
| 업무 core | 순수 reducer, 직렬화 state, 주입 가능한 repository / ID / scope, mock 시나리오 | 정상, 중복 제출, 응답 유실, 알려진 실패, 조회 재시도 테스트 |
| 검증 하네스 | 계약, UX 관측 검사, headless CLI, 상태/사건 증거 | 심은 결함 탐지, 누락 gate가 PASS가 되지 않음 |
| 화면 통합 | 토큰·8개 컴포넌트·3화면·gallery | 공통 컴파일, 가능한 모바일 실행·캡처 |

커밋은 각 단위의 구현과 검증 뒤 진행한다. 작업 완료 기준은 검증, 단위별 커밋, main 병합,
origin/main push 및 로컬·원격 commit 일치 확인까지다. 로컬 작업 브랜치의 커밋만으로 완료 처리하지 않는다.

## 후속 단계

초기 구현 상태: 환경/업무 core/계약 CLI/3화면을 각기 커밋했다. JVM·Native 공통 테스트, Desktop UI 테스트,
Android 실행, iOS XCUITest까지 확인했으며 자세한 근거는 [검증 결과](validation.md)에 기록한다.
자동 코드 수정 루프와 전체 모바일 UX gate는 아직 완료되지 않았다.

- P0 잔여: Hot Reload MCP로 입력/캡처/실제 reload 검증
- P2: 원본 디자인 입력, 큰 글씨·모든 상태 gallery 검토, 플랫폼별 baseline 승인
- P3: 버전 handshake, revision, await, fixture, events 프로토콜 및 실제 UI driver; engineering / UX-observer 권한 분리
- P4: native bounds/semantics 수집, 키보드/가림/잘림/접근성, 이미지 worker
- P5: Codex/Vision adapter, worktree writer/device lock, hash 보호, 최대 3회 수정/같은 실패 2회 중단, immutable acceptance suite
- P6: native back, 프로세스 재시작 복원, 키보드, 오프라인, 느린 응답을 3화면과 연결
- P7: Maestro / XCUITest / 실기기 / VoiceOver / TalkBack / clean release 검증
- P8: Figma snapshot, Ktor sandbox API, Coil, 영속 저장소, 네이티브 SDK

이번 단계는 전체 디자인 생성기나 자율 코드 수정기를 완성했다고 주장하지 않는다. 없는 driver 결과는 NOT_RUN 또는 UNSUPPORTED로 남긴다.
