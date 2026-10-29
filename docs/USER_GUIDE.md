# Using Living Apps 0.3.1

**English** | [简体中文](USER_GUIDE.zh-CN.md)

## Install and start

Install `dist/living-apps-0.3.1-debug.apk`, requiring Android 8.0 or later. This development-signed validation build is not in an app store. Installing over an older version with the same signature preserves apps and records; the database upgrades automatically.

First launch offers offline spending, water and reading templates with no fabricated records. Open one to add records; tap a record to edit or delete it. Statistics run on the phone. The pencil opens editing, and the history icon opens versions. The Android interface, website and documentation support English and Simplified Chinese.

## Choose the app language

Open **Settings → Language / 语言** and choose **Follow system**, **简体中文** or **English**. The preference survives app restarts. System-default mode chooses a supported system language and falls back to English. Android 13+ also exposes the choice in the system app-language settings; Android 8–12 stores it locally.

The switch changes menus, editor controls, validation messages and reconstruction controls. Existing app names, field labels/options, records and saved AI conversations remain in their original language. Built-in templates use the selected language only when first created. Saved drafts and versions are retained; previously saved free-text status/history notes may retain their original wording. An active reconstruction session ends and pending steps are cancelled; reopen the interface to continue. Language selection works offline and does not change your API model, key or automatic-improvement preference.

## Creation and AI configuration

In Settings, enter your API gateway, model and key. The default model is `gpt-6.1-sol`; discovery reads the actual model catalog, without silent switching. Enter your own key initially. No development key is included in the installer, and saved keys are protected with Android Keystore.

The endpoint starts empty. Configure an HTTPS service you trust. HTTP is restricted to loopback and Android-emulator development hosts.

Tap Create an app, for example: “Create a running app with date, distance, duration, mood and notes; open the records screen by default.” Review, try the form and adopt it. The runtime supports records, forms, lists, check-ins and basic statistics, not generated webpages or arbitrary code.

## Edit on the app screen

1. Tap the pencil to enter a local draft. The live app retains its current version; an existing draft resumes.
2. Tap an input in the form, or use Fields to select hidden fields, cards, statistics, the app or preset actions. The outline and panel identify the selection.
3. Direct adjustments work offline: rename, move up/down, add fields/options, set defaults, group and hide. The app panel controls theme, icon, density, start screen, title and statistics. Text becomes a draft change only after Apply.
4. Use AI editing for a request. A field or preset action can be scoped to that object. Conversation retains relevant context and each successful change is undoable. This requires a network connection and sends the request, definition and relevant conversation, not records or trial input automatically.
5. Collapse, half-open or expand the panel. Undo/redo is local. Back first collapses the panel, then saves the draft and exits. Returning to use preserves search and scroll position.
6. Trial uses a temporary copy. Add, edit and delete only trial data. Copy up to 20 local records to check layout without uploading them to the model.
7. Done reviews differences, live record counts and compatibility before Save new version. Multiple draft edits produce one live version. Trial records do not enter the live database.

Cancellation, failure or unsupported model output preserves input and the draft. If local edits happen during generation, the result becomes a candidate for review instead of overwriting the draft. Out-of-scope candidates require explicit permission for that request before merging. Same-property conflicts let you keep the local value or accept the candidate.

If the live baseline changes, first rebase by merging against the latest live version. A pre-merge draft snapshot is retained and can be restored through More. Ordinary changes support undo/redo. Discard draft deletes the draft, conversation and trial copy while preserving live records.

## Preset actions

In the whole-app panel, choose the preset action that adds a record on tap. Set a button name and a complete preset record; dates may resolve to the day of the tap. At most six buttons are allowed. Configuration itself does not execute them.

Check results in Trial first. After saving a version, only tapping the action in the live app adds a record; an undo entry is then available. If that record has since been edited, undo is blocked and you should inspect it in the list. Actions do not automatically access the network or execute in the background.

## Data conversion and restoration

Field type changes, numeric conversions and replacement of old options are separate from ordinary AI edits. Save or discard the ordinary draft, then open More → Data conversion (separate flow) in the editor.

- Text can become number, date or boolean, and these can become text. Dates must be real ISO dates; booleans accept true / false / 是 / 否. Converting a number to text disables its former aggregate reference and unit.
- An aggregate numeric field accepts an explicit formula, new value = old value × multiplier, and a new unit. Ordinary unit settings under Home and statistics change display text only.
- Choice conversion requires a target for every old option. Identical targets merge; unmapped historical values block conversion.

Preview successful, failed, null and missing values first. Failures cannot be partially committed. Confirmation rechecks the baseline and records, saves and verifies a local backup, then commits records and a new version in one transaction. Defaults and preset action values convert too; stable IDs, other fields and record timestamps remain. Limits are 5,000 records and a 5 MB conversion backup per app.

Pre-conversion UI versions cannot be restored directly, to prevent old types misreading new data. More → Pre-conversion backups verifies and creates an independent app copy while preserving the current app and records added after conversion.

## Local observation and autonomous improvement

Growth observes opens, saves, statistics visits, validation errors and timing, not typed content. Rule suggestions require activity across at least two days and four opens. Six saves may trigger a quick-entry suggestion; six statistics visits significantly exceeding saves may suggest a statistics start screen.

Suggestions are the default. After enabling automatic adoption, only adding quick entry is allowlisted, at most once per app per 24 hours. More can freeze a version. Active drafts pause automatic adoption while observation continues. JobScheduler targets a six-hour interval, may be delayed by the system, and does not call a model API.

AI deeper analysis is initiated by the user and includes only the definition and a seven-day aggregate summary. An observation comparison needs at least five valid timing samples on each side; it is not proof of causal improvement.

## Global backups

Settings exports plaintext JSON containing current app definitions, records and frozen status, excluding keys, events, historical versions, drafts, trial data and pre-conversion backups. To export pre-conversion data, first create its app copy, then export a global backup. Choose a trusted save location.

Import creates independent app copies rather than overwriting existing apps. Limits are 5 MB, 50 apps, 5,000 records per app and 20,000 total records. Invalid input rolls back the entire import.

## Reconstruct an existing app

Use the new **重构已有应用** home entry. The source app keeps its account/business data; Living stores its own UI bindings and bounded workflows locally. See the [full setup, authorization and trial guide](REBUILD_MODE.md). The record-app global backup described above excludes reconstructed interfaces.
