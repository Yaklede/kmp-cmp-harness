# UX Loop Harness rules

Use these constraints within the user's requested work. Keep existing project conventions and
explicit user decisions. Read this directory's documents in the following situations:

| Document | When to read |
| --- | --- |
| [project.md](project.md) | At the start of an applicable task; establish scope and available capabilities |
| [design-ux.md](design-ux.md) | When interpreting designs, changing UI, or reviewing UX |
| [architecture.md](architecture.md) | When implementing or connecting code, state, or an execution adapter |
| [verification.md](verification.md) | Before selecting checks or making a quality claim |
| [repair-delivery.md](repair-delivery.md) | When repairing failures and preparing delivery |
| [kmp-cmp.md](kmp-cmp.md) | Only when the affected project uses Kotlin Multiplatform or Compose Multiplatform |

Scale the work to the change: record a small contract and results in the task or existing project
artifacts for a small change. Do not create a new directory hierarchy or document suite just to
satisfy these rules. Use the project's chosen formats and languages.

Workflow: establish scope → identify design intent and acceptance criteria → implement → gather
execution evidence → review → repair within bounds → deliver with remaining limitations.
The same person or agent may perform sequential roles; this does not require spawning agents.

This package supplies instructions only. It does not install a parser, model, test runner, device
driver, CI gate, or autonomous agent service. Those capabilities must exist or be implemented as
part of an authorized task before claiming they were used.
