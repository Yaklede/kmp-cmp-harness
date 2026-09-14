export function readEvents(jsonl: string) {
  let usage: { input_tokens: number; cached_input_tokens: number; output_tokens: number } | null = null;
  let completed = false;
  let commandCalls = 0;
  let toolCalls = 0;
  let fileChangeEvents = 0;
  let failedCommands = 0;
  let malformedLines = 0;
  for (const line of jsonl.split("\n").filter(Boolean)) {
    let event;
    try { event = JSON.parse(line); } catch { malformedLines++; continue; }
    if (event.type === "turn.completed") {
      completed = true;
      if (event.usage && ["input_tokens", "cached_input_tokens", "output_tokens"].every(k => Number.isFinite(event.usage[k]))) usage = event.usage;
    }
    if (event.type === "item.completed") {
      if (event.item?.type === "command_execution") {
        commandCalls++; toolCalls++;
        if (typeof event.item.exit_code === "number" && event.item.exit_code !== 0) failedCommands++;
      } else if (["mcp_tool_call", "web_search"].includes(event.item?.type)) toolCalls++;
      else if (event.item?.type === "file_change") fileChangeEvents++;
    }
  }
  return { usage, completed, commandCalls, toolCalls, failedCommands, fileChangeEvents, malformedLines };
}

export const mean = (values: number[]) => values.length ? values.reduce((a, b) => a + b, 0) / values.length : null;
export const median = (values: number[]) => {
  if (!values.length) return null;
  const sorted = [...values].sort((a, b) => a - b); const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
};

export function summarizeRuns(runs: any[]) {
  return Object.fromEntries(["control", "harness"].map(arm => {
    const selected = runs.filter(r => r.arm === arm);
    const graded = selected.filter(r => Array.isArray(r.checks));
    const passed = graded.reduce((sum, r) => sum + r.checks.filter(c => c.pass).length, 0);
    const total = graded.reduce((sum, r) => sum + r.checks.length, 0);
    const accounted = selected.filter(r => r.events.usage !== null);
    return [arm, {
      attempts: selected.length, gradedRuns: graded.length,
      passedChecks: passed, totalChecks: total, checkPassRate: total ? passed / total : null,
      completeTasks: selected.filter(r => r.agentStatus === "COMPLETED" && r.checks?.every(c => c.pass) && r.integrityViolations.length === 0).length,
      timeouts: selected.filter(r => r.agentStatus === "TIMEOUT").length,
      infrastructureErrors: selected.filter(r => r.agentStatus === "ERROR" || r.evaluatorError).length,
      integrityViolations: selected.reduce((sum, r) => sum + r.integrityViolations.length, 0),
      medianSeconds: median(selected.map(r => r.elapsedSeconds)),
      meanSeconds: mean(selected.map(r => r.elapsedSeconds)),
      usageAccountedRuns: accounted.length,
      meanInputTokens: mean(accounted.map(r => r.events.usage.input_tokens)),
      meanCachedInputTokens: mean(accounted.map(r => r.events.usage.cached_input_tokens)),
      meanOutputTokens: mean(accounted.map(r => r.events.usage.output_tokens)),
      meanTotalTokens: mean(accounted.map(r => r.events.usage.input_tokens + r.events.usage.output_tokens)),
      meanCommandCalls: mean(selected.map(r => r.events.commandCalls)),
      byTask: Object.fromEntries(["editor", "form", "gates"].map(task => {
        const group = graded.filter(r => r.task === task);
        return [task, { passed: group.reduce((n, r) => n + r.checks.filter(c => c.pass).length, 0),
          total: group.reduce((n, r) => n + r.checks.length, 0), runs: group.length }];
      })),
    }];
  }));
}
