import { expect, test } from "bun:test";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { grade } from "../grade";
import { readEvents, summarizeRuns } from "../metrics";

test("usage is counted only from real completion events, missing values remain null", () => {
  const out = readEvents([
    { type: "item.started", item: { type: "command_execution" } },
    { type: "item.completed", item: { type: "command_execution", exit_code: 1 } },
    { type: "turn.completed", usage: { input_tokens: 100, cached_input_tokens: 80, output_tokens: 20 } },
  ].map(e => JSON.stringify(e)).join("\n"));
  expect(out.commandCalls).toBe(1); expect(out.failedCommands).toBe(1);
  expect(out.usage?.input_tokens).toBe(100);
  expect(readEvents('{"type":"turn.failed"}\n').usage).toBeNull();
});

test("infrastructure failure is not counted as an evaluated zero-score run", () => {
  const base = { task: "editor", arm: "control", integrityViolations: [], elapsedSeconds: 10,
    events: readEvents(""), evaluatorError: null };
  const summary = summarizeRuns([
    { ...base, agentStatus: "ERROR", checks: null },
    { ...base, agentStatus: "TIMEOUT", checks: [{ pass: true }, { pass: false }] },
  ]).control;
  expect(summary.attempts).toBe(2); expect(summary.gradedRuns).toBe(1);
  expect(summary.totalChecks).toBe(2); expect(summary.passedChecks).toBe(1);
  expect(summary.completeTasks).toBe(0); expect(summary.usageAccountedRuns).toBe(0);
  expect(summary.meanTotalTokens).toBeNull();
});

for (const task of ["editor", "gates", "form"]) {
  test(`grader accepts a reference solution and detects seeded defects: ${task}`, async () => {
    const temp = mkdtempSync(join(tmpdir(), "ux-loop-grader-test-"));
    try {
      const reference = await grade(task, join(import.meta.dir, "reference"), join(temp, "reference"));
      expect(reference.filter(c => !c.pass)).toEqual([]);
      const seeded = await grade(task, join(import.meta.dir, "../fixtures", task), join(temp, "seeded"));
      expect(seeded).toHaveLength(10);
      expect(seeded.filter(c => !c.pass).length).toBeGreaterThanOrEqual(5);
    } finally { rmSync(temp, { recursive: true, force: true }); }
  }, 60000);
}
