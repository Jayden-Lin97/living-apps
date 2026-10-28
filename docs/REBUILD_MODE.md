# Reconstruct an installed app

**English** | [简体中文](REBUILD_MODE.zh-CN.md)

The Android development build adds **Reconstruct an existing app** alongside creating a standalone personal app. Living draws the interface; the installed source app performs the business operations. This is a common engine with user-selected bindings, not a promise that every application or feature is compatible. The Android interface remains Chinese.

## Use the mode

1. On the home screen choose **重构已有应用**. Select a launcher app that Android permits Living to discover. The list does not grant access or establish compatibility.
2. Read the accessibility disclosure, enable the service in system settings, then separately authorize the selected source app. Permissions are never enabled automatically.
3. Choose **到原应用选取信息 / 动作**. A small dock lets you navigate the source app yourself. On the intended page choose **选取当前页面**.
4. Add a text binding, click, input or scroll action. Repeated resource IDs may offer a read-only list or an explicitly interactive list. Ambiguous individual selectors cannot be adopted. Source pages without usable semantic nodes are unsupported.
5. Rename/reorder components and choose light/dark and comfortable/compact appearance. An action may append steps from another demonstrated page, up to eight. A workflow has at most one runtime input; typed values are not stored in its definition.
6. Start the custom interface. The source app must be on the appropriate page for the first step. Displayed values and lists come from its currently loaded page; unavailable bindings display a status instead of retaining a previous value. Lists show at most 40 items; off-screen content is not automatically fetched.
7. Tap an action. Every step checks the local baseline, source identity, page shape and a unique selector. An accepted Android click is not evidence of business success. Inspect the result, including through **查看原应用 / 手动操作**, then explicitly mark the last attempt as tried on this device within one minute. This is user confirmation for that definition/source version, not global compatibility certification.
8. Stop and return to Living at any time. Leaving the source app or turning the screen off ends the runtime session. In the source-app dock, Back operates the source app; the full custom interface handles Back as an exit.

## Local capability boundary

`RebuildSpec` is separate from record-oriented AppSpec v1/v2. It contains a source package, installed version code, signing-certificate fingerprint, name/appearance and up to 24 components. Components are `read`, `list`, `action` or `input`. Selectors contain only resource ID, class, text and content description. A SHA-256 page-shape baseline uses unique resource/class/action tuples, tolerating repeated list-row counts but detecting structural differences. This fingerprint is a compatibility check, not a complete description of business semantics.

The only workflow operations are node click, text entry and forward scrolling. There are no coordinates, scripts, arbitrary intents, URLs, shell commands, screenshot APIs, network-control listeners or remotely executable actions. Interactive lists resolve the selected visible text again and use its nearest available clickable ancestor, within five levels. Repeated matches are disabled. A click cannot reach unrelated packages.

The first implementation uses the platform accessibility bridge. It does not yet implement MediaSession adapters, OCR, automatic whole-app exploration, conditional workflows, arbitrary graphics or hidden-background execution. Login/password nodes, marked-sensitive data and recognized sensitive operations are blocked or handed back to the source app. The keyword heuristic is not a comprehensive financial/sensitive-action classifier: use the original app for those operations. Android/system/app protection is not bypassed.

Page traversal is bounded to 300 visible nodes, 1,200 visited nodes and depth 40. Truncation disables actions. Each step waits a bounded time for its expected page; it never retries a click already accepted. Stopping cancels later steps but cannot undo operations already performed by the source app.

## Persistence, restoration and updates

Definitions and append-only version history are in the private local `living-rebuild.db`, separate from business records and the source app's database. Saves compare the current revision. Edits/restoration create a new version and clear trial confirmations. Deleting a reconstructed interface removes only these Living definitions/history. The existing record-app global backup does **not** include reconstructed interfaces; the settings screen states this limitation. Separate definition export/import remains future work. Database-file encryption is not claimed.

A changed source version code blocks runtime use. After inspecting the update, the user may accept the new version and re-test; selectors/page baselines must still match. A signing-certificate change requires creating a new association. Same-version, server-driven UI changes are checked by selectors and page shape; these checks cannot guarantee unchanged semantics. Restoring a historical interface does not downgrade or modify the source app.

## Optional natural-language editing

The existing configurable Responses API is reused, with default model `gpt-6.1-sol`, `store:false` and no fallback. Only the user's entered requirement and anonymous component aliases/types are sent. Source package, selectors, captured text, component labels, screenshots, account data and trial inputs are not automatically included. Users should avoid placing private source content in the request themselves.

The model can rename/reorder every existing alias exactly once and change name, dark mode or density. Unknown keys, lost/duplicate components and attempted capability additions are rejected. A preview requires adoption; a stale result cannot overwrite a newer definition. Ordinary tests never call the paid API.

## Validation and distribution

Read [validation](VALIDATION.md) for executed checks and device limits, and [open-source review](OPEN_SOURCE_REVIEW.md) for pinned references. Host tests use synthetic packages/windows; they are not QQ Music compatibility evidence. Real QQ Music search → results → playback remains a physical-device acceptance task.

Google Play requires accessibility disclosure/declarations and distinguishes deterministic automation from prohibited autonomous planning/execution for general apps. The API here edits appearance; runtime steps are fixed and explicitly user-triggered. Store acceptance has not been established. See the [official policy](https://support.google.com/googleplay/android-developer/answer/10964491).
