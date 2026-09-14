import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { summarizeRuns } from "../benchmarks/ux-loop/metrics";

const repository = resolve(import.meta.dir, "..");
const input = resolve(process.argv[2] ?? join(repository, "artifacts/ab/pilot-2026-09-14-app-cli"));
const output = resolve(process.argv[3] ?? join(repository, "docs/benchmarks/pilot-2026-09-14"));
const manifest = JSON.parse(readFileSync(join(input, "manifest.json"), "utf8"));
const completion = JSON.parse(readFileSync(join(input, "completion.json"), "utf8"));
const runs = JSON.parse(readFileSync(join(input, "results.json"), "utf8"));
assert.equal(completion.status, "COMPLETE", "Do not present an unfinished experiment as complete");
assert.equal(completion.recorded, completion.expected);
const summary = summarizeRuns(runs);
const control = summary.control; const harness = summary.harness;
const format = (n: number | null, digits = 1) => n === null ? "미수집" : n.toLocaleString("en-US", { maximumFractionDigits: digits, minimumFractionDigits: digits });
const pct = (n: number | null) => n === null ? "미측정" : `${format(n * 100)}%`;
const delta = (a: number | null, b: number | null) => a === null || b === null ? "미측정" : `${b >= a ? "+" : ""}${format(b - a)}`;
const relativeChange = (a: number | null, b: number | null) => a === null || b === null || a === 0 ? "미측정" : `${b >= a ? "+" : ""}${format((b / a - 1) * 100)}%`;
const nonCached = (arm: any) => arm.meanInputTokens === null || arm.meanCachedInputTokens === null ? null : arm.meanInputTokens - arm.meanCachedInputTokens;
const taskNames = { editor: "비동기 폼 상태·복구", form: "웹 폼 접근성·반응형", gates: "검증 증거·결과 집계" };
const sanitizedRuns = runs.map(run => ({
  runId: run.runId, task: run.task, arm: run.arm, repetition: run.repetition,
  agentStatus: run.agentStatus, elapsedSeconds: run.elapsedSeconds, events: run.events,
  integrityViolations: run.integrityViolations,
  checks: run.checks.map(check => ({ ...check, detail: check.detail.replaceAll(run.workspace, "<workspace>").replaceAll(homedir(), "<home>") })),
  evaluatorError: run.evaluatorError, promptHash: run.promptHash, baselineHashes: run.baselineHashes,
  afterHashes: run.afterHashes,
  rawEvidenceSha256: Object.fromEntries(["events.jsonl", "stderr.txt", "changes.diff", "final-response.md"]
    .filter(name => existsSync(join(input, run.runId, name)))
    .map(name => [name, createHash("sha256").update(readFileSync(join(input, run.runId, name))).digest("hex")])),
}));
const paired = [];
for (const task of Object.keys(taskNames)) {
  for (let repetition = 1; repetition <= manifest.repetitions; repetition++) {
    const a = runs.find(r => r.arm === "control" && r.task === task && r.repetition === repetition);
    const b = runs.find(r => r.arm === "harness" && r.task === task && r.repetition === repetition);
    paired.push({ task, repetition, checkDifference: b.checks.filter(c => c.pass).length - a.checks.filter(c => c.pass).length,
      elapsedSecondsDifference: b.elapsedSeconds - a.elapsedSeconds,
      totalTokenDifference: a.events.usage && b.events.usage ?
        b.events.usage.input_tokens + b.events.usage.output_tokens - a.events.usage.input_tokens - a.events.usage.output_tokens : null });
  }
}
mkdirSync(dirname(output), { recursive: true });
writeFileSync(`${output}.json`, JSON.stringify({ manifest, completion, summary, paired, runs: sanitizedRuns }, null, 2) + "\n");

const scoreDifference = harness.checkPassRate - control.checkPassRate;
const finding = scoreDifference === 0 ? "이번 표본에서 하네스 적용에 따른 행동 검사 통과율 향상은 확인되지 않았습니다." :
  scoreDifference > 0 ? "이번 표본에서는 하네스 적용군의 행동 검사 통과율이 더 높았습니다." : "이번 표본에서는 하네스 적용군의 행동 검사 통과율이 더 낮았습니다.";
const report = `# UX Loop Harness A/B 예비 실험

${finding} 세 과제를 두 번씩 반복한 결과이며, 일반 프로젝트 전체의 효과로 해석할 수 없습니다.

## 적용·미적용 비교

| 지표 | 미적용 | 적용 | 차이(적용 − 미적용) |
| --- | ---: | ---: | ---: |
| 행동 검사 통과 | ${control.passedChecks}/${control.totalChecks} (${pct(control.checkPassRate)}) | ${harness.passedChecks}/${harness.totalChecks} (${pct(harness.checkPassRate)}) | ${delta(control.checkPassRate * 100, harness.checkPassRate * 100)}%p |
| 모든 검사 통과·기준 보존·기한 내 완료 | ${control.completeTasks}/${control.attempts} | ${harness.completeTasks}/${harness.attempts} | ${delta(control.completeTasks, harness.completeTasks)}건 |
| 회당 실행 시간 중앙값 | ${format(control.medianSeconds)}초 | ${format(harness.medianSeconds)}초 | ${relativeChange(control.medianSeconds, harness.medianSeconds)} |
| 회당 실행 시간 평균 | ${format(control.meanSeconds)}초 | ${format(harness.meanSeconds)}초 | ${relativeChange(control.meanSeconds, harness.meanSeconds)} |
| 회당 입력+출력 토큰 평균 | ${format(control.meanTotalTokens, 0)} | ${format(harness.meanTotalTokens, 0)} | ${relativeChange(control.meanTotalTokens, harness.meanTotalTokens)} |
| 회당 입력 토큰 평균(캐시 포함) | ${format(control.meanInputTokens, 0)} | ${format(harness.meanInputTokens, 0)} | ${relativeChange(control.meanInputTokens, harness.meanInputTokens)} |
| 회당 캐시 입력 토큰 평균 | ${format(control.meanCachedInputTokens, 0)} | ${format(harness.meanCachedInputTokens, 0)} | ${relativeChange(control.meanCachedInputTokens, harness.meanCachedInputTokens)} |
| 회당 비캐시 입력 토큰 평균 | ${format(nonCached(control), 0)} | ${format(nonCached(harness), 0)} | ${relativeChange(nonCached(control), nonCached(harness))} |
| 회당 출력 토큰 평균 | ${format(control.meanOutputTokens, 0)} | ${format(harness.meanOutputTokens, 0)} | ${relativeChange(control.meanOutputTokens, harness.meanOutputTokens)} |
| 회당 완료된 shell 호출 평균 | ${format(control.meanCommandCalls)} | ${format(harness.meanCommandCalls)} | ${delta(control.meanCommandCalls, harness.meanCommandCalls)}회 |
| 제한 시간 초과 | ${control.timeouts}/${control.attempts} | ${harness.timeouts}/${harness.attempts} | ${delta(control.timeouts, harness.timeouts)}건 |
| 보호한 기준 파일 수정 | ${control.integrityViolations} | ${harness.integrityViolations} | ${delta(control.integrityViolations, harness.integrityViolations)}건 |
| 사용량 수집 완료 | ${control.usageAccountedRuns}/${control.attempts} | ${harness.usageAccountedRuns}/${harness.attempts} | — |

시간은 CLI 시작부터 종료까지의 wall time이며 모델·네트워크·도구·로컬 스케줄링이 포함됩니다.
TIMEOUT의 시간은 제한에서 절단된 관찰값으로, 실제 작업 완료 시간을 뜻하지 않습니다.
표의 평균·중앙값에는 이 절단값이 포함되므로 일반적인 작업 완료 속도로 해석하지 않습니다.
토큰은 실제 CLI 완료 이벤트 값입니다. 캐시 입력은 입력 토큰의 부분집합이며 중복 합산하지 않았습니다.
누적 토큰 증가에는 캐시 토큰 증가가 포함됩니다. 비캐시 입력은 오히려 감소했으므로 이를 실제 청구 비용 증가로 해석할 수 없습니다.
토큰 수를 유료 청구액으로 환산하지 않았습니다. Shell 호출 수는 수정 횟수나 재작업 횟수가 아닙니다.
시간 초과 시 남은 코드는 채점하되 완전 성공으로 집계하지 않습니다.
미수집 사용량은 제외했으므로 토큰 평균은 수집 완료 건에 한정되며, 전체 시도 비용의 평균이 아닙니다.

## 과제별 결과

| 과제 | 미적용 | 적용 |
| --- | ---: | ---: |
${Object.entries(taskNames).map(([task, name]) => `| ${name} | ${control.byTask[task].passed}/${control.byTask[task].total} | ${harness.byTask[task].passed}/${harness.byTask[task].total} |`).join("\n")}

## 개별 실행

| 과제 | 반복 | 조건 | 검사 통과 | 시간(초) | 입력+출력 토큰 | 상태 |
| --- | ---: | --- | ---: | ---: | ---: | --- |
${runs.map(r => `| ${taskNames[r.task]} | ${r.repetition} | ${r.arm === "harness" ? "적용" : "미적용"} | ${r.checks.filter(c => c.pass).length}/${r.checks.length} | ${format(r.elapsedSeconds)} | ${r.events.usage ? format(r.events.usage.input_tokens + r.events.usage.output_tokens, 0) : "미수집"} | ${r.agentStatus} |`).join("\n")}

## 재현 조건과 한계

- 모델: ${manifest.model}, reasoning ${manifest.effort}; CLI: ${manifest.codexVersion}; Bun: ${manifest.bunVersion}.
- 시작: ${manifest.startedAt}. ${manifest.hostPlatform}/${manifest.hostArchitecture}, 한 쌍씩 동시 실행, 회당 ${manifest.timeoutSeconds}초 제한.
- 실험 코드 commit: \`${manifest.sourceCommit}\`; 시작 시 미커밋 변경: ${manifest.sourceDirty}.
- 하네스는 \`fdf8bbd\`의 제약 문서를 그대로 사용했습니다. 양쪽에 같은 제품 요구사항과 공개 smoke test를 제공했습니다.
- 자동 전역 지침 로딩을 양쪽 모두 끄고, 공통 프롬프트에서 프로젝트 지침을 명시적으로 읽게 했습니다. 새 세션·새 작업 폴더를 사용했습니다.
- 사전에 고정한 과제·규칙·채점기 해시와 개별 체크 결과는 [기계 판독 결과](./${output.split("/").pop()}.json)에 있습니다.
- 채점기에는 변경이 전달되지 않았고, 에이전트 종료 후 별도 과정에서 검사했습니다. 폼은 실제 Chromium 레이아웃·클릭·키보드 입력으로 확인했습니다.
- 단 3개 과제 × 2반복입니다. 한 실행의 10개 체크는 서로 독립적인 표본 10개가 아닙니다. 통계적 유의성이나 일반적인 품질 향상을 주장하지 않습니다.
- 과제는 제약조건이 다루는 문제를 의도적으로 포함하며, 증거 집계 과제는 하네스 주제와 특히 가깝습니다. 실제 KMP/iOS 앱, 운영 프로젝트, 화면 읽기 도구와 사용자 연구를 검증하지 않았습니다.
- 최초 시스템 CLI 0.149.1에서 모델 실행 전 접속 실패 2건이 있었습니다. 작업 결과·사용량 없이 거절돼 점수에서 제외하고 별도 기록했습니다. 위 표는 호환 CLI에서의 ${runs.length}회입니다.
- 사용자의 추가 요청으로 시스템 CLI도 0.154.0으로 업데이트했습니다. 측정 프로세스는 처음 선택한 앱 내장 실행 파일을 계속 사용했습니다. 업데이트 작업이 실험 일부와 겹쳤으므로 wall time에는 호스트 작업 부하의 영향도 있을 수 있습니다.

실험 절차: [PROTOCOL.md](../../benchmarks/ux-loop/PROTOCOL.md).
원본 JSONL, 제출 코드 diff, 응답과 브라우저 스크린샷은 로컬 \`artifacts/ab/\`에 보존합니다.
Hub 제출은 수행하지 않았으며 벤치마크 코드와 데이터는 OpenDock 배포 payload에 포함되지 않습니다.
`;
writeFileSync(`${output}.md`, report);
console.log(`${output}.md`);
