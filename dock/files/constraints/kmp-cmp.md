# Conditional Kotlin / Compose Multiplatform constraints

Read only when the affected code uses KMP or CMP. These constraints do not choose a framework,
toolchain version, percentage of shared UI, or module layout for other projects.

- Inspect actual source sets, declared targets, Gradle plugins, app hosts, and dependency direction
  before editing. Keep platform-only APIs out of common code. Prefer an injectable common interface
  for platform behavior; use expect/actual when that is the appropriate stable boundary.
- Reuse shared business contracts and state handling; keep platform storage, permissions,
  notifications, lifecycle, secure services, and native views behind the project's adapters.
  Do not change wire formats or domain policy as a side effect of a UI refactor.
- Keep Compose components compatible with the project's API style: state flows down, events flow
  up, modifiers and slots remain composable, and effects use explicit lifecycle ownership.
  Preserve design tokens and existing wrappers until callers and relevant checks have migrated.
- CMP rendering does not imply that SwiftUI/UIKit components or platform conventions are
  automatically provided. Verify native text input, focus, accessibility, back navigation, insets,
  lifecycle, and restoration on the supported targets when the change affects them.
- For shared changes targeting both Android and iOS, run both compile gates using the project's
  real tasks. If a target cannot be checked, state the command/capability and reason and preserve
  NOT_RUN or UNSUPPORTED. Do not call Android-only success complete shared-platform validation.
- Use desktop/preview/hot reload tools only if available and compatible with the pinned toolchain.
  Discover their actual capabilities; do not assume an experimental UI API or MCP driver exists.
  Desktop results do not replace mobile runtime checks.
- Keep development control or inspection code out of release dependency graphs. When adding such
  integration, verify release packaging using the project's platform build tools.
