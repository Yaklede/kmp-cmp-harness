# 검증 CLI

```sh
./harness validate
./harness scenario --fixture fixtures/payment-success.json
./harness scenario --fixture fixtures/payment-response-lost.json
./harness scenario --fixture fixtures/payment-declined.json
./harness scenario --fixture fixtures/payment-offline.json
./harness scenario --require-all-gates
./harness inspect-ui --observation fixtures/ui/valid-confirm.json
./harness inspect-ui --observation fixtures/ui/keyboard-obscured.json
```

`harness`는 실행 전에 CLI를 빌드한다. JVM만 필요한 작업에도 Gradle 설정을 위해 Android SDK 경로가 필요할 수 있다.
반복 실행은 `harness-cli/build/install/harness-cli/bin/harness-cli`를 직접 사용한다.

## 결과 의미

- `scenario` exit 0: 요청한 L0 시나리오만 통과. 전체 판정은 모바일/시각 driver 미실행으로 `NOT_RUN`.
- `--require-all-gates` exit 2: 필수 gate가 남아 있음. 전체 합격으로 취급하지 않는다.
- exit 1: 계약 또는 관측 검사 실패, 시나리오 검증 실패.
- `validate` exit 2: 미확정 정책이 있어 사람의 검토 필요.
- `inspect-ui` exit 0: 제공한 관측 데이터에서 구현된 규칙 위반이 없음. 실기기 접근성/UX 통과를 뜻하지 않는다.

CLI 입력은 Kotlin serialization으로 엄격히 읽으며 미지원 schema/pattern, 중복 node, 잘못된 bounds를 거부한다.
UI 관측 좌표는 Android/Desktop dp, iOS pt이다. 캡처의 pixel을 그대로 전달하지 않는다.

## 현재 검사

필수 정보 존재와 잘림, 터치 크기·겹침, 표시 문구와 접근성 label, viewport/overlay 가림,
응답 불명 상태에서 재결제 유도 및 결과 조회 누락을 검사한다. 정책 변경은 D, 흐름 변경은 C로 기록한다.
현재는 보정안을 실행하지 않으므로 `automaticFixAllowed`는 false다.

`fixtures/ui`는 의도적으로 만든 **합성 관측 데이터**다. native tree나 screenshot에서 관측을 수집하는 driver는 아직 없다.
정상 fixture와 결함 fixture를 비교하면 검출기의 작동을 확인할 수 있지만 실제 키보드 결함 검출까지 입증한 것은 아니다.
접근성 가이드의 수치만으로 합격시키지 않고 향후 native 수집/스크린리더 검증을 추가한다.

## 증거

`artifacts/<runId>`에 manifest, contract/fixture 사본, state, JSONL 사건, Mock 서버 결과, patch, source.zip, report.md를 쓴다.
소스 hash는 HEAD뿐 아니라 tracked 변경·삭제·미추적 파일까지 포함한다. gitignore에 포함된 산출물은 제외한다.
실행 시작과 종료, snapshot 쓰기 뒤에 소스 hash를 비교하며, 완료 manifest를 마지막에 쓴다. 기존 PASS를 재사용하지 않는다.
`source.zip`에는 미커밋 추가 파일까지 보존한다. git 이력은 포함되지 않으므로 재실행할 때 저장소를 초기화하고 snapshot을 커밋하거나 원래 저장소 위에 복원한다.
실행 artifact는 개발자 로컬 파일이고 baseline 승인을 뜻하지 않는다. CLI는 외부 모델·실결제·네트워크 제어 endpoint를 호출하지 않는다.

## 다음 연결점

관측 수집 driver, JSON-RPC handshake/dispatch/await, UX-observer 모드, 파일/device lock, immutable acceptance suite,
Codex/Vision adapter, 제한된 수정 루프는 후속 단계다. 초기 CLI를 자율 수정 루프 완성으로 취급하지 않는다.
