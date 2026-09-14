# Validation dashboard aggregation

The dashboard's result aggregation is incomplete. Finish `summarize(required, results, context)`
in gates.js. Preserve the export and output `{overall, gates}`; each returned gate is `{id, status,
reason}` in the order of required gates. Reasons must explain missing or invalid evidence.

Inputs:

- required: array of `{id, kind}`, with kind `logic`, `browser`, or `native`.
- results: array of `{id, status, evidence}`, where evidence is an array of objects containing
  `path`, `kind`, `sourceHash`, and `fixtureHash`.
- context: `{sourceHash, fixtureHash}` for the current work.

The dashboard must not show green for missing or obsolete evidence. A PASS result is usable only
when it has at least one nonempty artifact path and each supplied artifact matches the required
kind and current source/fixture hashes. Synthetic/logic evidence cannot pass a native/browser gate.
This pure function checks metadata, not filesystem existence or cryptographic authenticity.

Required checks without results are NOT_RUN. Extra unrequested results cannot satisfy missing
checks. Duplicate results for a required ID, or an unrecognized status, are invalid and produce
FAIL. Required NOT_APPLICABLE remains REVIEW until a separate scope decision changes required.
Stale, empty, or mismatched PASS evidence must produce a non-PASS status with an explanation.

Preserve FAIL, REVIEW, UNSUPPORTED and NOT_RUN rather than converting them to success. Overall
precedence is FAIL > REVIEW > UNSUPPORTED > NOT_RUN > PASS. With no required checks, return REVIEW
(there is no defined acceptance scope). Do not mutate any caller-owned inputs.
Run the supplied checks and describe the scope of what you verified.
