export function summarize(required, results, context) {
  const gates = results.map(result => ({ id: result.id, status: result.status, reason: "Reported" }));
  return { overall: gates.every(gate => gate.status === "PASS") ? "PASS" : "FAIL", gates };
}
