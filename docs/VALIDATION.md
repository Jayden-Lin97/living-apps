# Development preview validation

**English** | [简体中文](VALIDATION.zh-CN.md)

Version 0.2.0, checked 2026-10-28. This report distinguishes executed host tests from device tests that were only compiled.

## Executed checks

| Check | Result |
|---|---|
| Unit and Android framework tests | 74 passed; zero failures, errors or skips |
| Android lint | Passed |
| Development APK | Built successfully, min SDK 26 / target 35 |
| Device test APK | Compiled successfully; not executed |
| Public source and APK audit | No matching credentials, private endpoint literals or operational configuration |

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

Test classes: AndroidRuntimeTest 5, EditorRuntimeTest 14, MigrationRuntimeTest 8, CoreTest 26, EditingTest 10 and ExtendedEditingTest 11. Ordinary tests use offline fixtures.

## Interface renders

Robolectric 4.16.1 / Android 35 native graphics produced the images using synthetic records. They are not physical-device screenshots.

- [Editor](qa/host-draft-editor.png), [selection](qa/host-draft-selected.png)
- [Trial](qa/host-draft-trial.png), [review](qa/host-draft-review.png)
- [320 dp / larger text](qa/host-draft-compact-large-text.png)
- [Interface overview](UI_DESIGN.md)

## Artifact

Development-signed APK: 208320 bytes, SHA-256 `afba24d1c962baf545dd3ba44191257857062f9dd9a1a5cc218ecb3c1f8b20a9`. Rebuilt checksums are written to `dist/artifact-audit.json`.

Public source packaging uses an explicit Android product allowlist. API keys are entered by users and protected with Android Keystore, not embedded in the APK or backups. No separate encryption of the business SQLite database file is claimed. Global backups omit history, drafts, trial data, events and conversion snapshots; export pre-conversion data by restoring a separate copy first.

## Remaining validation

No connected physical device was available. Device tests were compiled, not run. Still required: installation/upgrades, physical Keystore, keyboard/Back gestures, TalkBack, OEM fonts/insets, system file selection, on-device API failures/cancellation, and Doze/reboot/power scheduling. Host Activity recreation does not replace OS process-kill testing.

Nonprogrammer usability tasks have not been conducted. Free layouts, network/notification actions, conditional workflows, multiselect and property-level optimization locks remain future work.

Run `scripts/build.ps1 -DeviceTests` with a connected device. LiveApiDeviceTest additionally requires explicit `liveApiBase` and `liveApiKey` instrumentation arguments; missing either skips the paid test. Credentials must not be logged or committed.
