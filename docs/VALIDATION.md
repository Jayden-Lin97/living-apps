# Development preview validation

**English** | [简体中文](VALIDATION.zh-CN.md)

Version 0.3.1, checked 2026-10-29. This report distinguishes executed host tests from device tests that were only compiled.

## Executed checks

| Check | Result |
|---|---|
| Unit and Android framework tests | 115 passed; zero failures, errors or skips |
| Android lint | Passed; zero errors, three warnings (two version-gated attributes and the existing service reference) |
| Development APK | Built successfully, min SDK 26 / target 35 |
| Device test APK | Compiled successfully; not executed |
| Public source and APK audit | No matching credentials, private endpoint literals or operational configuration |
| Offline language catalog | 872 entries; 960 explicit copy lookups; no missing translations |

The app ships with an empty API endpoint and a configurable `gpt-6.1-sol` model. Remote requests require HTTPS; loopback and Android-emulator hosts may use HTTP for development. Tests cover local HTTP, configured HTTPS, rejected remote HTTP, URL credentials and empty addresses. No live paid API was called for this validation.

## Coverage

- AppSpec rejection, compatibility, stable IDs, defaults, required fields, options and version baselines.
- SQLite upgrades preserving apps/records/versions; persistent drafts, concurrent-write protection and isolated trials.
- Undo/redo, complete differences, scope checking, cancelled/late results, three-way merging and explicit conflict choices.
- Native field selection, rename, Activity recreation, trial CRUD, review and save; 320 dp / 1.3× font checks.
- Grouping/hiding constraints, preset record validation, tap-day dates, stale-action rejection and undo after later record edits.
- Type, numeric and option conversion, default/preset conversion, failed-preview blocking, record fingerprints and atomic commits.
- Verified conversion backups, corruption rejection, separate-copy restoration, retained later records and data_epoch checks.
- Local observation thresholds, allowlisted automatic changes, freezing and restoration.

Test classes: AndroidRuntimeTest 5, EditorRuntimeTest 14, MigrationRuntimeTest 8, CoreTest 26, EditingTest 10, ExtendedEditingTest 11, RebuildSpecTest 11, RebuildRuntimeTest 17, LanguageRuntimeTest 9 and MessageCatalogTest 4. Ordinary tests use offline fixtures.

## App language coverage

Executed host checks cover English first launch, Simplified Chinese, following system preferences, unsupported-language fallback, native dialog resources, saved choices and Activity recreation on Android 26/32, and the Android 33 per-app language service and one-time preference migration. Compact English home, settings, editor and reconstruction screens were rendered with native graphics.

Switching preserves existing definitions, stable IDs, records, versions and editor drafts. Fresh built-ins use the creation language; saved user content stays unchanged. Pending reconstruction steps stop on a language change and do not replay when reopened. The language catalog is bundled, and both languages stay available offline, including in future app-bundle packaging. No runtime translation API or server was introduced.

AppLanguageDeviceTest is compiled only. It checks English → Chinese → English with a synthetic app and record, then removes its own fixture and restores the prior language choice. Physical-device switching, upgrade installation and process-death persistence remain unverified.

## Interface renders

Robolectric 4.16.1 / Android 35 native graphics produced the images using synthetic records. They are not physical-device screenshots.

- [Editor](qa/host-draft-editor.png), [selection](qa/host-draft-selected.png)
- [Trial](qa/host-draft-trial.png), [review](qa/host-draft-review.png)
- [320 dp / larger text](qa/host-draft-compact-large-text.png)
- [Interface overview](UI_DESIGN.md)

## Artifact

Development-signed APK: 260135 bytes, SHA-256 `c27e402f5431ce643ff42737316c1fb8a2ed6d7bc89f357854cf0b3373c8380a`. Rebuilt checksums are written to `dist/artifact-audit.json`.

Public source packaging uses an explicit Android product allowlist. API keys are entered by users and protected with Android Keystore, not embedded in the APK or backups. No separate encryption of the business SQLite database file is claimed. Global backups omit history, drafts, trial data, events and conversion snapshots; export pre-conversion data by restoring a separate copy first.

## Remaining validation

No connected physical device was available. Device tests were compiled, not run. Still required: installation/upgrades, physical Keystore, keyboard/Back gestures, TalkBack, OEM fonts/insets, system file selection, on-device API failures/cancellation, and Doze/reboot/power scheduling. Host Activity recreation does not replace OS process-kill testing.

Nonprogrammer usability tasks have not been conducted. Free layouts, network/notification actions, conditional workflows, multiselect and property-level optimization locks remain future work.

Run `scripts/build.ps1 -DeviceTests` with a connected device. LiveApiDeviceTest additionally requires explicit `liveApiBase` and `liveApiKey` instrumentation arguments; missing either skips the paid test. Credentials must not be logged or committed.

## Installed-app reconstruction coverage

29 offline checks: 11 schema/model-boundary tests and 18 Android framework/SQLite tests. These cover unique selectors, source identity, bounded steps, protected content, anonymous API context, list-row count changes, live value refresh/no stale values, actual shadow-node action dispatch, explicit trial evidence and expiry, source-version invalidation, picker persistence, dynamic list selection, page-size limits, screen-off exit, cancellation of later steps, revision conflicts, restoration and language changes with no action replay.

RebuildEntryDeviceTest is compiled only. The service windows/packages are synthetic host fixtures with fresh framework window copies, not a physical QQ Music session. No live paid API was called. Real device acceptance must cover the entire source selection/authorization/picking/typing/keyboard/Back/search-results-playback loop, OEM accessibility restrictions, app updates and interruption while a workflow is running. See [mode boundaries](REBUILD_MODE.md).

## Runtime presentation refresh (2026-10-29)

The updated runtime passed 116 offline unit/framework tests, Android Lint and both APK builds. The additional navigation check verifies that opening options does not dispatch source-app actions and Back dismisses the panel before ending the session. Native captures cover English, Chinese, light/dark appearance and a 320 dp screen with 1.3× text. Device tests were compiled only: no device was connected. No paid API was called. Existing v0.3.1 release artifacts remain unchanged; these checks cover the current source revision.
