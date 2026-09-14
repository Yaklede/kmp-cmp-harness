import assert from "node:assert/strict";
import { spawn, execFileSync } from "node:child_process";
import { cpSync, closeSync, existsSync, mkdirSync, mkdtempSync, openSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { inspectBundle, dockRoot, sha256 } from "../../scripts/dock-package";
import { readEvents, summarizeRuns } from "./metrics";

const benchmarkRoot = import.meta.dir;
const repo = resolve(benchmarkRoot, "../..");
function option(name: string, fallback: string) {
  const index = process.argv.indexOf(`--${name}`);
  return index === -1 ? fallback : process.argv[index + 1];
}
const repetitions = Number(option("repetitions", "2"));
const timeoutSeconds = Number(option("timeout-seconds", "240"));
const model = option("model", "gpt-6-astra");
const effort = option("effort", "xhigh");
const codex = option("codex-bin", "codex");
const catalog = JSON.parse(execFileSync(codex, ["debug", "models", "--bundled"], { encoding: "utf8" }));
assert(catalog.models.some(m => m.slug === model), "The selected CLI does not list this model; use a compatible --codex-bin before starting any sessions");
assert(Number.isInteger(repetitions) && repetitions >= 1 && repetitions <= 5);
assert(Number.isFinite(timeoutSeconds) && timeoutSeconds > 0 && timeoutSeconds <= 600);
const output = resolve(option("output", join(repo, "artifacts/ab", new Date().toISOString().replaceAll(":", "-"))));
assert(!existsSync(output), "Choose a new output directory; benchmark attempts are immutable");
mkdirSync(output, { recursive: true });
const bundle = inspectBundle();

function fileHashes(root: string, parent = ""): Record<string, string> {
  const hashes: Record<string, string> = {};
  for (const entry of readdirSync(join(root, parent), { withFileTypes: true })) {
    if ([".git", "node_modules"].includes(entry.name)) continue;
    const path = join(parent, entry.name);
    if (entry.isDirectory()) Object.assign(hashes, fileHashes(root, path));
    else if (entry.isFile()) hashes[path.replaceAll("\\", "/")] = sha256(readFileSync(join(root, path)));
  }
  return hashes;
}
const manifest = {
  startedAt: new Date().toISOString(), model, effort, repetitions, timeoutSeconds, concurrency: 2,
  sourceCommit: execFileSync("git", ["rev-parse", "HEAD"], { cwd: repo, encoding: "utf8" }).trim(),
  sourceDirty: Boolean(execFileSync("git", ["status", "--porcelain"], { cwd: repo, encoding: "utf8" }).trim()),
  codexVersion: execFileSync(codex, ["--version"], { encoding: "utf8" }).trim(),
  bunVersion: Bun.version, hostPlatform: process.platform, hostArchitecture: process.arch,
  protocol: sha256(readFileSync(join(benchmarkRoot, "PROTOCOL.md"))),
  benchmarkInputs: fileHashes(benchmarkRoot), dockInputs: fileHashes(dockRoot),
  independentRuns: repetitions * 6, design: "Matched task/repetition, two arms per pair, reversed launch order",
  notes: ["Fresh sessions, no inherited conversation", "Built-in skill metadata may remain common to both arms",
    "Hidden grader is not provided to sessions; host read isolation is procedural", "Not a general-project or human usability benchmark"],
};
writeFileSync(join(output, "manifest.json"), JSON.stringify(manifest, null, 2));

// Make a separate copy of public browser tooling. Its parent does not contain the grader or fixtures.
const browserTools = mkdtempSync(join(tmpdir(), "ux-loop-browser-tools-"));
mkdirSync(join(browserTools, "node_modules"));
for (const name of ["playwright", "playwright-core"]) cpSync(join(benchmarkRoot, "node_modules", name), join(browserTools, "node_modules", name), { recursive: true, dereference: true });
const browserModule = join(browserTools, "node_modules/playwright/index.mjs");
const commonPrompt = `현재 작업 폴더의 AGENTS.md와 README.md를 읽고, README에 설명된 기존 프로젝트 기능을 완성하세요. 기존 스택과 API를 유지하세요. 구현하고 가능한 검사를 실행한 뒤 실제 확인한 결과와 남은 한계를 짧게 보고하세요. 사용자에게 추가 응답을 기다릴 수 없는 작업이므로 명시된 요구사항 안에서 완성하세요.\n작업 범위는 현재 프로젝트뿐입니다. 다른 작업 폴더나 평가 코드, 다른 세션의 결과를 탐색하지 마세요. 외부 게시, Git commit/push, 패키지 설치, 추가 에이전트 생성은 이 작업 범위에 없습니다.\nHTML 과제에서 필요하면 이미 설치된 Playwright를 import(${JSON.stringify(browserModule)})로 사용할 수 있으며 Chromium도 준비되어 있습니다. browser 실행에는 로컬 서버를 사용하고 작업 후 종료하세요.\n`;
writeFileSync(join(output, "prompt.txt"), commonPrompt);

async function agentRun(task: string, arm: string, repetition: number) {
  const runId = `${task}-${repetition}-${arm}`;
  const runDir = join(output, runId); mkdirSync(runDir);
  const workspace = mkdtempSync(join(tmpdir(), "ux-loop-trial-"));
  cpSync(join(benchmarkRoot, "fixtures", task), workspace, { recursive: true });
  writeFileSync(join(workspace, ".gitignore"), "node_modules/\n");
  if (arm === "harness") {
    for (const { from, to } of bundle.manifest.files) {
      const target = join(workspace, to); mkdirSync(dirname(target), { recursive: true });
      const original = existsSync(target) ? readFileSync(target, "utf8") + "\n" : "";
      const block = `<!-- OPENDOCK:START id=files-${to} dock=benchmark/ux-loop path=${to} -->\n${readFileSync(join(dockRoot, from), "utf8").trimEnd()}\n<!-- OPENDOCK:END id=files-${to} dock=benchmark/ux-loop path=${to} -->\n`;
      writeFileSync(target, original + block);
    }
  }
  const baselineHashes = fileHashes(workspace);
  const editable = task === "editor" ? ["editor.js"] : task === "form" ? ["index.html", "app.js"] : ["gates.js"];
  const protectedPaths = Object.keys(baselineHashes).filter(p => !editable.includes(p));
  execFileSync("git", ["init", "-q"], { cwd: workspace });
  execFileSync("git", ["add", "."], { cwd: workspace });
  execFileSync("git", ["-c", "user.name=Benchmark Fixture", "-c", "user.email=fixture@example.invalid", "commit", "-qm", "initial fixture"], { cwd: workspace });
  const args = ["exec", "--ignore-user-config", "--ephemeral", "--json", "--color", "never", "--sandbox", "workspace-write",
    "--model", model, "-c", `model_reasoning_effort=${JSON.stringify(effort)}`, "-c", 'approval_policy="never"',
    "-c", "project_doc_max_bytes=0", "-c", 'web_search="disabled"',
    ...["apps", "plugins", "memories", "multi_agent", "hooks", "browser_use", "computer_use", "workspace_dependencies", "image_generation", "remote_plugin", "recommended_plugins"].flatMap(f => ["--disable", f]),
    "--output-last-message", join(workspace, "FINAL_RESPONSE.md"), "-"];
  const outPath = join(runDir, "events.jsonl"); const errPath = join(runDir, "stderr.txt");
  const stdout = openSync(outPath, "w"); const stderr = openSync(errPath, "w");
  const started = performance.now(); let timedOut = false;
  console.log(`START ${runId}`);
  const child = spawn(codex, args, { cwd: workspace, stdio: ["pipe", stdout, stderr], detached: process.platform !== "win32" });
  let hardStop: ReturnType<typeof setTimeout> | undefined;
  const stop = () => {
    timedOut = true;
    try { process.platform === "win32" ? child.kill("SIGTERM") : process.kill(-child.pid!, "SIGTERM"); } catch {}
    hardStop = setTimeout(() => {
      try { process.platform === "win32" ? child.kill("SIGKILL") : process.kill(-child.pid!, "SIGKILL"); } catch {}
    }, 3000);
  };
  const timer = setTimeout(stop, timeoutSeconds * 1000);
  const exit = await new Promise<{ code: number | null; error: string | null }>(resolveExit => {
    child.on("error", error => resolveExit({ code: null, error: String(error) }));
    child.on("close", code => resolveExit({ code, error: null }));
    child.stdin?.on("error", () => {});
    child.stdin?.end(commonPrompt);
  });
  clearTimeout(timer); if (hardStop) clearTimeout(hardStop); closeSync(stdout); closeSync(stderr);
  const elapsedSeconds = (performance.now() - started) / 1000;
  const events = readEvents(readFileSync(outPath, "utf8"));
  const after = fileHashes(workspace);
  const integrityViolations = protectedPaths.filter(path => after[path] !== baselineHashes[path]);
  const agentStatus = timedOut ? "TIMEOUT" : exit.code === 0 && events.completed ? "COMPLETED" : "ERROR";
  let checks = null; let evaluatorError = null;
  // A transport/auth failure is not a measurement of the instruction effect.
  if (agentStatus !== "ERROR") {
    try {
      const grade = Bun.spawn([process.execPath, join(benchmarkRoot, "grade.ts"), task, workspace, join(runDir, "screenshots")], { stdout: "pipe", stderr: "pipe" });
      const timeout = setTimeout(() => grade.kill(), 60000);
      const [text, errors, code] = await Promise.all([new Response(grade.stdout).text(), new Response(grade.stderr).text(), grade.exited]);
      clearTimeout(timeout);
      if (code !== 0) throw new Error(errors || `Evaluator exit ${code}`);
      checks = JSON.parse(text); assert.equal(checks.length, 10);
    } catch (error) { evaluatorError = String(error); }
  }
  const result = { runId, task, arm, repetition, workspace, agentStatus, exit, elapsedSeconds, events,
    integrityViolations, checks, evaluatorError, promptHash: sha256(commonPrompt), baselineHashes, afterHashes: after, command: [codex, ...args] };
  writeFileSync(join(runDir, "result.json"), JSON.stringify(result, null, 2));
  writeFileSync(join(runDir, "changes.diff"), execFileSync("git", ["diff", "HEAD"], { cwd: workspace }));
  if (existsSync(join(workspace, "FINAL_RESPONSE.md"))) cpSync(join(workspace, "FINAL_RESPONSE.md"), join(runDir, "final-response.md"));
  console.log(`END ${runId}: ${agentStatus}; checks=${checks?.filter(c => c.pass).length ?? "unavailable"}/10; seconds=${elapsedSeconds.toFixed(1)}`);
  return result;
}

const results: any[] = [];
for (let repetition = 1; repetition <= repetitions; repetition++) {
  for (const task of repetition % 2 ? ["editor", "form", "gates"] : ["gates", "form", "editor"]) {
    assert.deepEqual(fileHashes(benchmarkRoot), manifest.benchmarkInputs, "Benchmark changed after the experiment was frozen");
    assert.deepEqual(fileHashes(dockRoot), manifest.dockInputs, "Treatment changed after the experiment was frozen");
    const arms = repetition % 2 ? ["control", "harness"] : ["harness", "control"];
    const pair = await Promise.all(arms.map(arm => agentRun(task, arm, repetition)));
    results.push(...pair);
    writeFileSync(join(output, "results.json"), JSON.stringify(results, null, 2));
    writeFileSync(join(output, "summary.json"), JSON.stringify(summarizeRuns(results), null, 2));
    if (pair.some(r => r.agentStatus === "ERROR" || r.evaluatorError)) {
      console.error("Stopped: infrastructure error. Preserve attempts; investigate before any further calls.");
      process.exitCode = 1; break;
    }
  }
  if (process.exitCode) break;
}
writeFileSync(join(output, "completion.json"), JSON.stringify({ finishedAt: new Date().toISOString(), expected: repetitions * 6, recorded: results.length,
  status: process.exitCode ? "BLOCKED_BY_INFRASTRUCTURE" : "COMPLETE" }, null, 2));
console.log(`Results: ${output}`);
