# Open-source adoption and references

**English** | [简体中文](OPEN_SOURCE_REVIEW.zh-CN.md)

Initial review: 2026-10-27. This record evolves with modules and distinguishes distributed code, test dependencies and design references.

| Project | License | Decision | Reason / actual use |
|---|---|---|---|
| [DivKit](https://github.com/divkit/divkit) | Apache-2.0 | Reference; possible replacement for more complex layouts | Native JSON UI interpretation with local descriptions. The initial data forms need few components, so use a smaller constrained runtime |
| [JSON Forms](https://github.com/eclipsesource/jsonforms) | MIT | Reference schema/UI separation | Primarily web-based; avoid an extra WebView in the Android initial release |
| [Möbius Reflection](https://github.com/mobius-os/app-reflection) | MIT | Reference issue recording and periodic reflection | Server agent system; the reviewed module proposes changes periodically rather than fixing automatically. Use local Android events/rules without server dependencies |
| [Android Open Source Project](https://source.android.com/) | Component-specific, including Apache-2.0 | Use system APIs directly | SQLite, JobScheduler, Keystore, native UI and networking execute on the device |
| [JUnit 4](https://github.com/junit-team/junit4) | EPL-1.0 | Test dependency only | Protocol rejection, historical compatibility, suggestion thresholds and request/response handling |
| [JSON-java](https://github.com/stleary/JSON-java) | Public Domain, per the project's declaration in this review | JVM tests only | Android supplies org.json; JVM tests use a separate implementation |
| [Robolectric 4.16.1](https://github.com/robolectric/robolectric/tree/robolectric-4.16.1) | MIT; some Android-derived files Apache-2.0 | Reused for tests only | Android 35 framework, native SQLite and graphics validation without usable emulator acceleration; excluded from main APK |
| [AndroidX Test](https://github.com/android/android-test) | Apache-2.0 | Device tests only | Device test APK compiled; distinguish unexecuted device tests from executed host framework tests |
| [google-java-format 1.24.0](https://github.com/google/google-java-format) | Apache-2.0 | Development only, not distributed in APK | Consistent Java formatting for maintenance and review |
| [Gradle Wrapper 8.9](https://github.com/gradle/gradle/tree/v8.9.0/platforms/core-runtime/wrapper-main) | Apache-2.0 | Distributed with source | Fixed build entry and official distribution SHA-256; original LICENSE/NOTICE under licenses/ |
| [Lucide 0.468.0](https://github.com/lucide-icons/lucide/tree/0.468.0) | ISC; some Feather icons MIT | 27 vectors distributed in APK | Fixed commit `f12b0de177fbc2a6795e99be065887e72b237123`; SVG to Android VectorDrawable, with no JS/font/network dependency; full original license retained |

DivKit, JSON Forms and Möbius code/libraries are not incorporated and are not delivery runtime dependencies. Verify the exact version's LICENSE again before future code reuse.

## Changes from the second review

Storage/background work compared WorkManager with system JobScheduler. Only periodic local aggregation is needed, so use system APIs without a separate work-management library. Keystore uses standard Android AES-GCM. Robolectric supports Windows native runtimes from 4.12; version 4.16.1 adds framework and SQLite testing here. Passing these tests does not establish OEM-device or power-policy behavior.

Constraints reference [Android periodic work](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work) and [JobInfo.Builder](https://developer.android.com/reference/android/app/job/JobInfo.Builder#setPeriodic(long)). Wrapper verification uses the [official Gradle 8.9 checksum](https://services.gradle.org/distributions/gradle-8.9-bin.zip.sha256).

## UI reuse

After comparing Unicode symbols with a consistent icon set, the project incorporated the fixed Lucide vector geometry. `scripts/import_lucide.py` converts it; builds do not redownload icons. Source and inventory are in `lucide-icons.json`. Only stroke width and resource format changed. Full ISC/MIT licenses remain in `licenses/Lucide-LICENSE.txt` and APK `assets/lucide-license.txt`, accessible in Settings. Brand illustrations are project-native Canvas drawings, not Lucide assets.

## Editing interaction design

Designed 2026-10-27 and implemented 2026-10-28; see [editing design](APP_EDITING_INTERACTION.md). Version 0.2.0 has a native editor without new third-party production dependencies.

| Project | Verified version/license | Decision |
|---|---|---|
| [Puck](https://github.com/puckeditor/puck/tree/v0.21.0) | v0.21.0, commit `21470257d1f520c9b50f61196f1f02fc267c3912`; [MIT](https://github.com/puckeditor/puck/blob/v0.21.0/LICENSE) | Reference data-driven editing/history. React implementation not embedded in Android; no cloud AI dependency |
| [Material Components Android](https://github.com/material-components/material-components-android/releases/tag/1.13.0) | 1.13.0; [Apache 2.0](https://github.com/material-components/material-components-android/blob/1.13.0/LICENSE) | Evaluated, not added: platform Views handle a collapsible nonmodal panel without theme/AndroidX migration. Reevaluate for complex gestures later |

Verified the [ItemTouchHelper API](https://developer.android.com/reference/androidx/recyclerview/widget/ItemTouchHelper) for reordering, without adding RecyclerView or selecting its version. Initial native up/down controls remain. Lock and review a drag library's exact source license before adoption.

## Preset actions and data conversion

2026-10-28: reviewed declarative rules and local transactions before expansion.

| Project | Exact version/license | Decision and boundary |
|---|---|---|
| [JsonLogic](https://github.com/jwadhams/json-logic-js/tree/2.0.5) | 2.0.5, commit `c5c73601c90b11e98f6846609bac4dec203d1c18`; [MIT](https://github.com/jwadhams/json-logic-js/blob/2.0.5/LICENSE) | Reference constrained decisions as data; no JavaScript or general expression evaluator. ActionSpec only adds a validated preset record on a user tap, without network/background execution |
| AOSP SQLiteDatabase | [android-15.0.0_r1 source](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-15.0.0_r1/core/java/android/database/sqlite/SQLiteDatabase.java), Apache-2.0 | Use device transaction APIs; no source copying or separately bundled database library. Reviewed version is a reference; device SQLite varies by OS |
| SQLite transactions/backups | [Transactions](https://www.sqlite.org/lang_transaction.html), [Public Domain declaration](https://www.sqlite.org/copyright.html) | No extra SQLite distribution. Preview, SHA-256, pre-conversion JSON snapshot, updates and versioning stay local. Backup and writes share one transaction, with rollback on failure |

The project's MigrationPlan implements three fixed conversions: constrained types, explicit numeric multipliers and complete option mappings. Flyway/Liquibase-style DDL migration frameworks are not added: conversion affects business values under stable IDs; SQLiteOpenHelper still manages table upgrades. No servers were introduced.

## Public distribution boundary

An explicit allowlist limits source archives to Android product code, required build files, third-party licenses and user/developer documentation. Links and archive entries are checked offline before publication. Deployment configuration, internal publication records, private endpoints and credentials are outside the public source distribution. No Android dependency or business server is added.

## Reconstructing installed apps (2026-10-29)

| Project | Exact source / license verified | Decision and reason |
|---|---|---|
| [Mobilerun Portal](https://github.com/droidrun/mobilerun-portal/tree/94ecc89d02ac86f08076d7d354b612d3dd54541f) | Commit `94ecc89d02ac86f08076d7d354b612d3dd54541f`; [AGPL-3.0-or-later](https://github.com/droidrun/mobilerun-portal/blob/94ecc89d02ac86f08076d7d354b612d3dd54541f/LICENSE) | Reference only: accessibility-tree inspection demonstrates a common bridge across apps. No code incorporated; avoid the separate portal, remote-control endpoint and broader copyleft dependency in the current APK. |
| [Appium UiAutomator2 server](https://github.com/appium/appium-uiautomator2-server/tree/56bf27a222b9aa242e6a40cf87825b78b443b7be) | Commit `56bf27a222b9aa242e6a40cf87825b78b443b7be`; [Apache-2.0](https://github.com/appium/appium-uiautomator2-server/blob/56bf27a222b9aa242e6a40cf87825b78b443b7be/LICENSE) | Evaluated, not embedded: intended for instrumentation/test automation, with an external driver. A personal on-device interface must work without ADB, a test runner or a business server. |
| Android platform accessibility | Existing SDK API, component-specific AOSP licenses; no source copied | Use AccessibilityService, node actions and TYPE_ACCESSIBILITY_OVERLAY directly. No added production dependency or server. Only unique semantic selectors and explicit bounded workflows; no coordinate automation, screenshot upload or arbitrary generated code. |

The bridge is authored in this project. Source-app version/signature checks, local per-component trial confirmation, opt-in package access, definition revision checks and safe exit are independent of model output. Ordinary tests remain offline. The API can only rename/reorder existing anonymous component aliases and change appearance after preview; it cannot invent device operations. Google Play accessibility declarations and the distinction between deterministic automation and autonomous action must be reviewed before distribution to that store.
