# 제약조건 패키지 검증

2026-09-14, macOS Apple Silicon, Bun 1.3.14에서 실행했습니다.

| 검사 | 결과와 범위 |
| --- | --- |
| `bun run check` | PASS: 정책 검사와 패키징 회귀 테스트 14개 |
| `bun run test:opendock <upstream>` | PASS: 공식 parser / deploy archive / installer, 12개 로컬 lifecycle 조합 |
| `bun run release:prepare` | 배포 폴더와 파일별 SHA-256 생성; 실제 결과는 생성된 `dist/opendock.json` 참고 |
| 설치 payload | 지침 10개와 MIT 라이선스 1개; 설치 task / runtime / tool / dependency 0개 |
| 플랫폼 옵션 | macos, windows, linux 모두 같은 제약 문서 설치 |
| 대상 프로젝트 | 빈 디렉터리, KMP 폴더, 웹 package.json, Python pyproject.toml; 빌드·소스 파일 보존 |
| 기존 지침 | CRLF, 마지막 개행 없는 파일, 같은 경로의 사용자 메모 보존 |
| 업데이트와 삭제 | 재설치 중복 없음, 버전 업데이트 교체, 관리 블록 수정 충돌 차단, 삭제 후 기존 내용 복원 |
| 외부 게시 | NOT_RUN: Hub 인증·검토 제출·공개 승인 없음 |
| 실제 에이전트의 규칙 준수 | NOT_RUN: 패키지 설치 검증은 모델의 행동이나 UX 품질을 보장하지 않음 |

공식 구현 pin과 재현 명령은 [배포 안내](publishing.md)에 있습니다.
세 플랫폼 옵션은 macOS에서 실행한 코드 경로 검사이며 OS별 실제 사용자 환경 테스트와 구분합니다.

## 내용 검토 기준

다음은 제약 문서를 검토할 때 사용한 사례입니다. 자동 에이전트 실행 시험 결과로 취급하지 않습니다.

| 상황 | 문서가 요구하는 처리 |
| --- | --- |
| 웹 프로젝트에 설치 | 기존 stack 유지; KMP/CMP 문서는 적용하지 않음 |
| 백엔드 로직만 수정 | 해당 로직 검사 선택; 스크린샷과 모바일 gate 강제 안 함 |
| 이미지에 수수료·재시도 정책이 없음 | 관찰·추론·미확정 구분; 제품 정책을 생성하지 않음 |
| iOS 대상이 있으나 검사 환경 없음 | Android 통과만으로 공통 플랫폼 통과를 주장하지 않음 |
| synthetic UI fixture 통과 | 검사기 자체의 검증으로 보고; 실제 UI gate를 통과시키지 않음 |
| 실패 후 새 스크린샷이 더 좋아 보임 | 구현자가 이를 승인 baseline으로 자동 승격하지 않음 |
| 이미 main 병합·push를 요청받음 | 검증 후 해당 전달까지 완료; 동일 승인 재요청 안 함 |
| 배포 요청 없는 프로젝트에 설치 | 설치 사실만으로 commit·branch 변경·게시를 시작하지 않음 |

## 예제 디렉터리 이동

공통 앱과 전용 CLI는 `examples/payment/`로 이동했습니다. 앱 기능 코드는 변경하지 않았습니다.
예제 루트에서 다음 검사에 성공했습니다. Gradle의 정상적인 build cache 재사용을 포함합니다.

```sh
./gradlew :core:jvmTest :harness-cli:test :sharedUI:jvmTest \
  :androidApp:assembleDebug :sharedUI:compileKotlinIosSimulatorArm64 --console=plain
```

JVM core 10개, 예제 CLI 10개, Compose UI 3개 테스트와 Android debug 빌드, iOS simulator 컴파일을
확인했습니다. 로컬 Android SDK 경로는 환경변수 `ANDROID_HOME`으로 전달했습니다.
이동 직후 첫 시도는 SDK 경로 누락으로 실패했고 환경변수를 설정한 재실행이 성공했습니다.
네이티브 UI smoke 테스트를 이번 문서/경로 이동에서 다시 수행하지는 않았습니다.
이전 앱 기능 검증은 [예제 검증 이력](../examples/payment/docs/validation.md)에 별도로 보존했습니다.
