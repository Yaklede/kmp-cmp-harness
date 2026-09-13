# Implementation and adapter boundaries

- Integrate with the project's existing architecture. Share its state transitions and domain
  behavior between app execution and engineering tests; do not create a second implementation
  solely to make the harness pass. Keep state, events, and external effects distinguishable.
- Keep reusable rules and orchestration independent of app-specific models, routes, test IDs,
  commands, fixture data, and absolute paths. Resolve those through project configuration or
  adapters when a runner is actually part of the requested implementation.
- Use existing design tokens and components. Add reusable primitives only when justified by
  repeated behavior; preserve compatibility while migrating callers. Do not replace a design
  system or move business logic merely to match the layout of a sample repository.
- If implementing an adapter, expose capabilities and protocol version, running build identity,
  observation revision, and action completion separately from action acknowledgement. Reject stale
  observations/build mismatches and use bounded waits for observable completion.
- An engineering adapter may inspect internal state or dispatch domain actions. A UX observer
  must exercise visible controls or accessibility affordances. Internal dispatch is not evidence
  of discoverability, focus, hit testing, or successful platform input.
- Normalize observation units and identify the platform, viewport, insets, and node semantics.
  Missing observations or unsupported actions must have explicit outcomes, not empty success data.
- Keep mock fixtures, privileged inspection endpoints, and debug control code out of production
  behavior and release dependencies. Use the project's established test environment; fixture
  execution must not contact production services or trigger real transactions by default.
