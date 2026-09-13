# Verification and evidence

Select the checks that justify the requested change before editing. Reuse existing suites and
project CI requirements. Add behavior tests for material logic changes or regressions; do not add
tests that merely repeat constants or the shape of a trivial reversible edit.

| Evidence level | What it can establish |
| --- | --- |
| L0: logic/headless | State transitions, validation, and adapter contract behavior |
| L1: rendered preview/desktop/browser | Rendering and interactions actually exercised in that environment |
| L2: target runtime/emulator/simulator | Behavior exercised in the supported platform runtime |
| L3: physical device | Device-specific integration and behavior actually observed |
| Human review | Product decisions, accessibility audits, or usability research explicitly performed |

Use applicable levels, not a mandatory four-level suite for every repository. A backend change
does not require screenshots. A desktop preview cannot satisfy an iOS or Android interaction gate.
Compilation does not satisfy runtime gates. Automated checks do not constitute user research.

- Use explicit outcomes: PASS (assertions satisfied with evidence), FAIL (assertions violated),
  NOT_RUN (not executed), UNSUPPORTED (capability absent), REVIEW (a decision or human judgment
  remains), or NOT_APPLICABLE (outside the defined scope, with a reason). Infrastructure errors
  must be distinguishable from product failures and cannot produce PASS.
- Every required gate needs its own outcome and evidence. Do not average failures away or turn
  missing checks into PASS. Overall completion requires every required gate to pass or an explicit
  recorded scope decision; retain the original missing/failed outcome even when scope changes.
- Bind evidence to the actual source/build, contract, fixtures, baseline, environment, runner,
  and relevant device configuration. Record uncommitted changes when present. Reuse artifacts
  only when identity and relevant inputs still match; screenshots alone do not prove that match.
- For an executable runner, use unique run IDs, bounded execution, and a completion marker written
  only after artifacts are finalized. A failed or interrupted rerun must not leave a stale PASS.
- Report which observations are synthetic fixtures, real rendered UI, native input, or manual
  review. Synthetic observations can test a checker but cannot pass a real UI gate.
- Test relevant failure/recovery paths and user-visible behavior, not only happy paths. For UI
  changes include representative constrained layouts and accessibility settings appropriate to
  the target. Record the settings and what was actually exercised.
- Collect only needed evidence; exclude secrets and personal data from fixtures, snapshots,
  source archives, and reports. Share or upload evidence only within the user's authorized scope.
