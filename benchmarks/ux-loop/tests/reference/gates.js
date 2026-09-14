export function summarize(required, results, context) {
  const order = ["FAIL", "REVIEW", "UNSUPPORTED", "NOT_RUN", "PASS"];
  const gates = required.map(expected => {
    const found = results.filter(result => result.id === expected.id);
    let status = found[0]?.status ?? "NOT_RUN";
    let reason = found.length ? "Reported outcome" : "Missing result";
    if (found.length > 1 || (found.length && ![...order, "NOT_APPLICABLE"].includes(status))) {
      status = "FAIL"; reason = "Invalid duplicate or status";
    } else if (status === "NOT_APPLICABLE") { status = "REVIEW"; reason = "Required scope needs review"; }
    else if (status === "PASS") {
      const evidence = found[0].evidence;
      if (!evidence?.length || evidence.some(item => !item.path?.trim() || item.kind !== expected.kind ||
        item.sourceHash !== context.sourceHash || item.fixtureHash !== context.fixtureHash)) {
        status = "NOT_RUN"; reason = "Missing, mismatched or stale evidence";
      }
    }
    return { id: expected.id, status, reason };
  });
  return { overall: gates.length ? order.find(status => gates.some(gate => gate.status === status)) : "REVIEW", gates };
}
