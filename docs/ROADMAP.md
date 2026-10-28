# Roadmap and continuing open-source review

**English** | [简体中文](ROADMAP.zh-CN.md)

## 0.1 — Validate the local personal-app loop

App library, API creation and editing, field selection, offline forms and statistics, separate records and versions, local observation and low-risk automatic changes. No dedicated business backend is needed.

The initial runtime covers records, forms and statistics, with two local rules for autonomous improvement. Free-form layouts, more task types and independently proposing complex features remain future work, not completed capabilities.

## 0.2 — Native editing workspace (implemented)

Implemented selection, bottom panel, local properties, persistent drafts, undo/redo, ongoing conversation, scope and late-result checks, isolated trials, conflict merging, groups, hidden fields, preset actions and constrained data conversion. See APP_EDITING_INTERACTION.md and VALIDATION.md for exact scope and pending device acceptance.

## 0.3 — Extend composable components

First evaluate whether DivKit's local JSON rendering justifies replacing the small current runtime. Compare native RecyclerView/Compose and schema validation libraries for size, cold start, maintenance and licensing. Prioritize optional fields, groups, filters and charts before timers, calendars and notifications. Each component's execution capability must be explicitly describable, testable and migratable.

Acceptance: common layout and field changes work without an LLM; existing AppSpec v1 remains compatible; ordinary use remains available offline.

## 0.4 — More reliable personal adaptation

Continue referencing Möbius's issue and suggestion mechanisms and adaptive-UI research. Add local task events, explainable suggestions, evaluation against user-selected goals, confidence and cooldown after undo. An LLM receives minimized summaries only when local rules are insufficient, within user-configured budgets and trigger scope.

Acceptance: preregister a task metric and compare performance before and after a change; distinguish infrequent use from difficulty finding a feature; keep primary entry points stable; do not equate longer time spent with greater effectiveness.

## 0.5 — Local capabilities and app collaboration

Review open-source implementations for offline OCR, search, file import and charts individually. Verify licenses before integration; do not assume any particular project is already suitable. Cross-app reads require explicit capability permission and data ownership. Sharing a host does not imply sharing all records.

## Threshold for introducing a server

Design an optional server only for concrete requirements such as encrypted cross-device sync, multi-user sharing, centralized billing or enterprise policies. Runtime, statistics and version restoration do not move to a backend as a consequence. LLMs continue to use APIs, with no on-device large model.

## Work repeated at every stage

1. Search current open-source projects and official documentation for the specific module.
2. Record versions, licenses, maintenance and actual runnability; distinguish marketing, prototypes and implementations.
3. Run a minimal integration check, comparing local execution, power use, package size and introduced server dependencies.
4. Update OPEN_SOURCE_REVIEW.md, the computation deployment table and the validation report.
