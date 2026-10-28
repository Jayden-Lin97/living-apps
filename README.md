# Living Apps

**English** | [简体中文](README.zh-CN.md)

Create your own apps and let them evolve with use. Living Apps is a local-first Android platform: one host app for creating, editing and running personal apps, with usage observations and restorable versions.

[Project homepage](https://www.ipsyonline.xyz/living-apps/) · [Download](https://github.com/Jayden-Lin97/living-apps/releases) · [Watch the film](https://www.ipsyonline.xyz/living-apps/#demo) · [Issues](https://github.com/Jayden-Lin97/living-apps/issues)

## What you can do

- Start from an offline template or describe an app to a configurable AI API.
- Select objects on the app screen, use offline controls or natural language, and keep a persistent draft with undo/redo.
- Try changes with isolated records, review differences, resolve conflicts and save a version.
- Run forms, records, lists, check-ins and basic statistics locally. Group/hide fields, configure preset record actions and use a separate verified data-conversion flow.
- Let local rules suggest improvements. Automatic adoption is off by default and currently limited to adding quick entry, with permission, freezing, cooldown and restoration.

Version 0.3.0 adds [Reconstruct an existing app](docs/REBUILD_MODE.md): select an installed app, bind current-page information, dynamic lists and explicit fixed operations, then design and locally try your own interface. This is a common engine, not universal compatibility; real QQ Music acceptance is still pending.

Version 0.3.0 is a development preview for Android 8.0+. Free-form layouts, arbitrary generated code, arbitrary network actions and general workflows are not supported. Installed-app reconstruction has a separate, bounded operation schema. The current Android UI is Chinese; public documentation is available in English and Chinese.

## Get started

Install the development-signed APK from [Releases](https://github.com/Jayden-Lin97/living-apps/releases). Spending, water and reading templates work offline with no fabricated records. Configure your own HTTPS API endpoint and key only when you need AI. The app includes no private gateway or developer credentials. Default model: `gpt-6.1-sol`; no silent model fallback.

Edits remain in a local draft until you review and save. Trial records stay separate. Restoring a UI version preserves business records. Data conversion has its own preview, verified backup and transaction.

## Local-first architecture

| On your phone | Configured remote API |
|---|---|
| Native UI, installed-app bindings/overlays/workflows, SQLite records, statistics, search, drafts, undo/merge, trials, preset actions, data conversion/backups, versions, compatibility, observation, rules and scheduling | Natural-language creation/editing and user-requested deeper analysis |

No dedicated business backend or on-device large model is required. API requests include `store:false` and do not automatically upload business records. Keys use Android Keystore and are excluded from APKs and backups. HTTP is permitted only for local development hosts; use a trusted HTTPS service for remote APIs.

## Documentation

- [User guide](docs/USER_GUIDE.md)
- [Native interface](docs/UI_DESIGN.md)
- [Editing interaction design](docs/APP_EDITING_INTERACTION.md)
- [Architecture and computation](docs/DEVELOPMENT_PLAN.md)
- [AppSpec and data compatibility](docs/SCHEMA.md)
- [Open-source licenses and references](docs/OPEN_SOURCE_REVIEW.md)
- [Validation and device-testing limits](docs/VALIDATION.md)
- [Roadmap](docs/ROADMAP.md)
- [Contributing](CONTRIBUTING.md) · [Security](SECURITY.md) · [MIT license](LICENSE)

The image below is an actual Android native View host render with synthetic data, not a physical-device screenshot:

![Native app library](docs/qa/host-home.png)

## Build and validate

Requires JDK 17, Android SDK Platform 35 and Gradle 8.9. On Windows:

```powershell
.\scripts\build.ps1 -JavaHome 'C:\path\to\jdk17' -AndroidSdk 'C:\path\to\android-sdk'
```

The Gradle Wrapper downloads dependencies on first use. `-GradleHome` can select a local Gradle 8.9. Output uses `%LOCALAPPDATA%\LivingAppsDevelopment\build` to avoid non-ASCII Windows class-loading issues; the APK is copied to `dist/`. The script runs unit/framework tests and lint, and builds app/device-test APKs. Add `-DeviceTests` only with a connected device. Compiling device tests does not mean they ran.

On macOS/Linux, configure `sdk.dir` in `local.properties`, then run `sh ./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`. That platform entry has not yet been exercised here.

```text
python scripts/check_i18n.py
python scripts/audit_artifact.py
python scripts/package_delivery.py
```

Source archives use an explicit product-file allowlist. Normal tests are offline and never call paid APIs. For an opt-in live probe, set `LIVING_APPS_BASE_URL` and `LIVING_APPS_API_KEY` in the process environment and run `python scripts/probe_api.py --generate`, `--edit` or `--editor`. Never place credentials in scripts or command history.

Project code is MIT licensed. Third-party code/assets retain their licenses under `licenses/`.
