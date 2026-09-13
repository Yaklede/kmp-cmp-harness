# OpenDock 배포 안내

배포 단위는 `dock/`입니다. 준비 명령은 검증된 파일만 `dist/opendock/`에 복사하고,
SHA-256 목록을 `dist/opendock.json`에 기록합니다. `dist/`는 Git에서 제외하며 CI에서도 생성합니다.
앱 예제, Kotlin 코드, Bun 도구, 테스트, CI 설정은 이 배포 디렉터리에 들어가지 않습니다.

## 준비

저장소 유지보수 환경에서 Bun을 사용합니다. 설치 대상 프로젝트에는 Bun 요구사항이 없습니다.

```sh
bun install --frozen-lockfile
bun run check
bun run release:prepare
```

이미 출력 디렉터리가 있으면 덮어쓰지 않고 실패합니다. 이전에 생성한 디렉터리를 확인 후 제거하거나,
새 경로를 지정합니다: `bun scripts/prepare-release.ts dist/opendock-review-2`.
패키지에는 명시적으로 매핑한 Markdown과 라이선스만 허용하며 설치 작업이나 런타임 요구사항을
추가하면 검증이 실패합니다. 이 정책 변경은 별도의 배포 설계 변경으로 검토해야 합니다.

## 공식 구현과 호환성 검사

로컬 정책 검사와 별도로 공식 OpenDock의 manifest parser, deploy packager, installer를 사용합니다.
검증 기준은 OpenDock 소스 **8c41853fc6af8130d06fc9be713d462104438b87** (CLI package 0.2.12)입니다.
검사 스크립트는 다른 commit이면 실패합니다. 소스 pin을 바꿀 때 호환성 검사를 다시 수행합니다.

```sh
OPENDOCK_SOURCE="$(mktemp -d)"
git -C "$OPENDOCK_SOURCE" init
git -C "$OPENDOCK_SOURCE" remote add origin https://github.com/JeongYunSung/OpenDock.git
git -C "$OPENDOCK_SOURCE" fetch --depth 1 origin 8c41853fc6af8130d06fc9be713d462104438b87
git -C "$OPENDOCK_SOURCE" checkout --detach FETCH_HEAD
bun install --cwd "$OPENDOCK_SOURCE" --frozen-lockfile --ignore-scripts --filter opendock
bun run test:opendock "$OPENDOCK_SOURCE"
```

이 검사는 네트워크 Registry resolver만 임시 로컬 resolver로 대체합니다. 공식 코드가 만든 실제
압축파일을 풀어 설치하고, 재설치·버전 업데이트·로컬 수정 충돌·삭제를 확인합니다. task 실행 경로도
활성화하지만 manifest에 task가 없으므로 실행 명령과 런타임·도구·의존성 기록은 모두 0개입니다.

빈 프로젝트와 기존 KMP·웹·Python 파일이 있는 프로젝트에서 같은 규칙을 사용합니다.
세 플랫폼 옵션의 로직을 검사하며, 이것을 Windows·Linux 실기기 실행 검증으로 해석하지 않습니다.
Registry 인증·서명·다운로드·Hub 심사는 이 로컬 검사 범위 밖입니다.

## Hub에 제출할 때

현재 산출물은 **게시 준비 상태**입니다. `owner`는 실제 OpenDock 계정의 소유자 이름을 사용합니다.
GitHub 저장소 소유자 이름과 같다고 가정하지 않습니다. 예시 `your-hub-owner`는 교체해야 합니다.
아래 명령은 제출자가 실행하는 실제 외부 게시 단계이며 패키징 스크립트나 CI는 실행하지 않습니다.

```sh
cd dist/opendock
opendock auth login
opendock auth status
OPENDOCK_OWNER='your-hub-owner'
opendock deploy "$OPENDOCK_OWNER/ux-loop@0.1.0" --platform macos --file dock.yml
opendock deploy "$OPENDOCK_OWNER/ux-loop@0.1.0" --platform windows --file dock.yml
opendock deploy "$OPENDOCK_OWNER/ux-loop@0.1.0" --platform linux --file dock.yml
```

OpenDock은 플랫폼을 생략하면 현재 OS로 제출합니다. 이 패키지는 OS 명령을 포함하지 않으므로
세 플랫폼에 **동일한 manifest**를 제출합니다. `all` 같은 문서에 없는 플랫폼 값을 사용하지 않습니다.
버전은 제출 참조에서 지정하므로 manifest에 `id`, `version`, `platform` 필드를 추가하지 않습니다.

`deploy`는 Hub 검토를 요청합니다. 제출 성공과 공개 승인 상태를 구분하세요.
`DOCK.md`는 Hub 소개로 별도 전달되며 대상 프로젝트에는 복사되지 않습니다.
공식 CLI가 만든 압축파일은 `dock.yml`과 `files`의 매핑 원본만 포함합니다.

## 승인 후 사용자 설치

임시 프로젝트에서 승인된 참조로 먼저 확인한 뒤 실제 프로젝트에 설치합니다.

```sh
opendock install owner/ux-loop@0.1.0
opendock doctor owner/ux-loop
opendock list --json
```

에이전트가 `AGENTS.md`, `CLAUDE.md`, `GEMINI.md` 중 해당하는 지침을 읽는 환경이어야 합니다.
그 외 에이전트에서는 `.harness/ux-loop/README.md`를 작업 요청에 직접 지정할 수 있습니다.
이 패키지는 에이전트 프로그램이나 자동 실행 서비스를 설치하지 않습니다.

프로젝트별 요구사항은 관리 블록 **밖**에 유지합니다. 기존 지침과 사용자 결정이 우선하며,
하네스 규칙이 다른 프로젝트에 이 저장소의 결제 정책, SDK 버전, `main` push 정책을 강제하지 않습니다.
규칙이 필요 없어지면 `opendock uninstall owner/ux-loop`로 제거합니다. 로컬 수정 충돌은 내용을
검토해 해결하며 이 안내는 `--force` 사용을 요구하지 않습니다.

## 근거

- [OpenDock 공식 문서](https://opendock.app/docs/?lang=ko&theme=dark): manifest, 관리 블록, deploy, 플랫폼별 릴리스
- [검증한 공식 manifest 구현](https://github.com/JeongYunSung/OpenDock/blob/8c41853fc6af8130d06fc9be713d462104438b87/packages/cli/src/core/domain/manifest.ts)
- [검증한 공식 배포 packager](https://github.com/JeongYunSung/OpenDock/blob/8c41853fc6af8130d06fc9be713d462104438b87/packages/cli/src/deploy-package.ts)
- [검증한 공식 managed block 구현](https://github.com/JeongYunSung/OpenDock/blob/8c41853fc6af8130d06fc9be713d462104438b87/packages/cli/src/core/files/managed-block.ts)

확인일: 2026-09-14. 설치된 로컬 CLI는 0.2.0이었으며 이를 전역 업데이트하거나 실제 Hub에 제출하지
않았습니다. 호환성 결과는 위에 고정한 공식 소스를 대상으로 한 결과입니다.
