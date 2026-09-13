# Design intent and UX

- Keep original design inputs immutable. Identify their version or content hash when available.
  A generated screen or the current implementation is not automatically an approved baseline.
  Keep any approved baseline separate and scoped to platform, viewport, theme, locale, and font scale.
- Describe a screen by user goal, information priority, available actions, navigation role, and
  applicable risks. Component names alone are not a UX contract. Record where each important
  assumption came from and which decisions remain unknown.
- Choose patterns from observed project flows. Do not impose one prominent action, exit
  confirmation, a payment step, or another pattern on every screen without a matching user need.
- Complete the relevant state matrix: initial, loading, content, empty, disabled/submitting,
  validation failure, request failure, offline, and recovery. Include slow responses, repeated
  actions, cancellation, navigation, and restoration when the flow can encounter them.
  Mark irrelevant states with a reason instead of generating unnecessary screens.
- Preserve input during recoverable errors. For ambiguous remote outcomes, follow the service's
  established status/retry semantics; a timeout is not proof that the operation failed.
  Do not invent idempotency guarantees or automatically repeat irreversible actions.
- Check semantic roles and labels, reading/focus order, contrast, text scaling, reachable controls,
  keyboard/inset occlusion, localization, and motion requirements as applicable. Use the target
  platform's documented units and the project's accessibility standard; raw screenshot pixels
  are not interchangeable with logical touch-target units.

Classify a proposed correction by its effect:

| Class | Constraint |
| --- | --- |
| A: presentation | May fix spacing, clipping, semantics, or reachability within existing intent and immutable requirements. Verify the result. |
| B: missing state | May implement feedback or recovery using already established product behavior; do not invent new semantics. |
| C: flow | A change to navigation, information priority, or available actions needs a documented product basis or explicit user direction. Prepare a concrete proposal if that decision is missing. |
| D: policy | Prices, fees, eligibility, legal consent, privacy, and transaction/retry policy must come from an authoritative project source or the user. Never infer them from visual design. |

Existing user authorization satisfies the corresponding decision; do not ask for it again.
Acceptance criteria should name the state, visible action, expected behavior, and evidence needed.
Visual similarity alone cannot establish usability, and a passing logical assertion cannot establish
that users can find or activate a control.
