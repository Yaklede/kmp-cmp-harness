# UX Loop paired pilot — protocol v1

## Question and frozen treatment

Measure whether installing the current instruction-only dock changes outcomes on three small
engineering tasks. The treatment is the unchanged `dock/` payload from repository commit
`fdf8bbd2401c7f23e468cb8ee9d208f155d55946`. Package contents, fixtures, evaluator, prompts, model,
reasoning effort, execution deadline, and run order are recorded before the first model call.
No rule changes, prompt tuning, or evaluator changes are permitted after inspecting arm outcomes.
Infrastructure fixes must be disclosed and affected results excluded or identified, not silently replaced.

## Design

- Three tasks, two arms, two repetitions = 12 independent Codex CLI sessions, authorized by the user.
- Tasks: asynchronous profile-editor state, accessible responsive profile form, and evidence-aware
  gate aggregation. Each has ten independently evaluated behavioral checks.
- Both arms receive identical task prompts, detailed product requirements, source, public smoke tests,
  tools, model settings, and execution deadline. The control has ordinary project instructions.
  The treatment additionally gets the exact dock file mappings, including the entry-point block.
- Global automatic project instructions are disabled for both arms; the common prompt explicitly
  asks the agent to read the project's AGENTS.md and README.md. Memories, plugins/apps, web search,
  and further subagents are disabled. Remaining common built-in skill metadata is disclosed.
- No conversation history, hidden evaluator, reference solution, or the other arm's output is passed
  to a session. Each works in a newly initialized temporary Git repository. Hidden tests live outside
  the agent workspace and run only after the model process exits. This is procedural test isolation,
  not a claim that a local agent cannot read any other host file.
- Same model as the current local configuration: `gpt-6-astra`, reasoning `xhigh`.
- Deadline: 240 seconds per run. Two arms may execute concurrently as one pair; pairs execute
  sequentially, and launch order reverses between repetitions. Wall time includes model/network
  latency, tool work, and local scheduling; it is not a pure model-speed measurement.
- No human hints or post-grading repairs. A timeout or failure remains part of the attempted sample.
  The benchmark does not automatically retry a model call or publish to any external registry.

## Outcomes

Report task/check pass counts with denominators, complete-task success, elapsed seconds, input,
cached-input and output tokens as provided by CLI events, and completed shell/tool-call counts.
Missing usage remains missing, never zero. Do not estimate dollar cost from subscription usage.
Protect reference designs, original public tests, package files, and project instructions by hash;
mutations are reported as integrity violations and invalidate complete-task success.

Primary quality summary: total passing hidden checks / 60 checks per arm. Also report each task
separately so a strong result on the gate-reporting task cannot mask an unrelated UI regression.
Time/token summaries include every completed run; timeouts show the cap and missing token accounting.
Matched differences use the same task/repetition. No significance or general-population claim is
justified by two repetitions of three purpose-built tasks.

These cases intentionally exercise failure modes addressed by the rules. They are not a random
sample of real projects, not a KMP/iOS device benchmark, and not a user usability study. The HTML task
uses actual Chromium DOM, layout, clicks and keyboard input, but no screen-reader or human visual audit.
The gate task is especially aligned with the package's subject matter. Report ties and regressions
as clearly as improvements. A null result is a valid outcome.

## Reproduce

```sh
cd benchmarks/ux-loop
bun install --frozen-lockfile
bunx playwright install chromium
bun test tests
bun run run.ts --repetitions 2 --timeout-seconds 240 --model gpt-6-astra --effort xhigh
```

The last command starts model sessions and consumes usage; run it only when authorized. Logs,
workspaces, output diffs, per-check results and checksums are kept under the chosen output directory
in `artifacts/ab/`. Only reviewed aggregate results and reproducibility metadata belong in Git.
Benchmark tooling is outside `dock/` and cannot enter the OpenDock payload.

CLI usage event reference: [official non-interactive mode documentation](https://learn.chatgpt.com/docs/non-interactive-mode).
Configuration reference: [official configuration documentation](https://learn.chatgpt.com/docs/config-file/config-reference).

## Infrastructure amendment before any completed model task

The initial system CLI 0.149.1 rejected Astra with an HTTP 400 requiring a newer CLI. Both attempted
sessions ended without a completed model turn or usage record and were not graded. They remain in
`artifacts/ab/pilot-2026-09-14` and are excluded from the 12 measured runs, not counted as product
failures or zero-token executions. No task output was available for prompt/evaluator tuning.
The runner now checks the CLI's bundled model catalog before launching, and accepts `--codex-bin`.
The measured pilot uses the existing app's CLI 0.154.0-alpha.6.2 via this option. No global CLI
installation, model, treatment, task requirement, evaluator, or 240-second budget was changed.
