# 현재 디자인 기준

사용자가 제공한 Figma frame 또는 원본 디자인 이미지가 아직 없다. 이 UI는 검증용 샘플이며 승인 baseline이 아니다.
`contracts/payment-confirm.json`은 Mock 시나리오에 한정한다. 실제 수수료·재시도·결제수단 정책은 포함하지 않는다.

## 토큰과 컴포넌트

`sharedUI/.../design/Tokens.kt`가 색상, typography, spacing, radius를 정의한다.
옅은 배경, 짙은 녹색 주요 행동, 흰 카드, 충분한 텍스트 대비를 사용하는 임시 테마다.
현재는 Kotlin 토큰이며 DTCG importer는 아직 없다.

기본 컴포넌트 8종: Button, TextField, ListRow, Card, AppBar, Loading, EmptyState, ErrorState.
금액 표시는 보조 primitive `HarnessAmount`로, 좁은 폭에서 숫자 중간 줄바꿈을 피하도록 20–36sp 내에서 조정한다.
2배 글씨/360dp 폭에서 숫자와 통화가 한 줄에 보이는 것을 테스트한다. 임의의 모든 금액·글씨 배율을 검증한 것은 아니다.

- Button: 최소 56dp, disabled/loading 표현, 여러 줄 label, 중복 제출은 core에서도 차단
- TextField: 실제 편집 노드에 modifier/testTag 적용, label·error 제공, 입력 값은 Store 소유
- ListRow: label과 값을 세로 배치해 긴 한국어 문구에 대응
- 화면: safe drawing inset, IME inset, 스크롤 가능한 본문, 하단 행동 분리
- 상태: 금액·수취인·결제수단, 입력 보존, 응답 불명 시 결과 조회, 완료 후 제출 제거

```sh
./gradlew :desktopApp:run --args='--gallery'
./gradlew :desktopApp:run --args='--scenario=ResponseLost'
./gradlew :desktopApp:run --args='--scenario=Declined'
./gradlew :sharedUI:jvmTest
```

화면은 `design` 컴포넌트와 stateless screen을 조합하며, 업무 로직은 `core`에 둔다.
새 컨트롤은 먼저 상태와 접근성 계약을 정의하고 gallery와 적절한 테스트를 추가한다.
Android back은 공통 상태 전이에 연결했지만 iOS swipe navigation, process death 복원과 실기기 접근성은 후속 검증 대상이다.
