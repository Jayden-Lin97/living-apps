# Contributing to Living Apps

**English** | [简体中文](CONTRIBUTING.zh-CN.md)

We welcome bug reports and suggestions for improvements through GitHub Issues. Please include your Android version, app version, steps to reproduce, and expected results. First remove personal records and credentials from screenshots, backups, and logs.

This project is licensed under the MIT License; third-party resources retain their respective licenses. Before submitting changes, please read AGENTS.md, docs/SCHEMA.md, and docs/DEVELOPMENT_PLAN.md.

- For new components, first define their capability boundaries and verify their open-source licenses, then document them in docs/OPEN_SOURCE_REVIEW.md.
- Prioritize running UI, records, statistics, drafts, conversions, and observations on the phone; do not add a server merely for reuse.
- Preserve stable field IDs and independent business records; ordinary definition edits must not bypass migration, baseline, or scope checks.
- Do not commit keys, signing materials, private records, or .local files. Ordinary tests must not call paid APIs.

The Android validation entry point is scripts/build.ps1. It runs unit / framework tests and lint, and compiles the main APK and device-test APK. Add -DeviceTests only when a physical device is available, and distinguish compilation from execution in reports.

Public contributions focus on the Android product. Update both documentation languages: English default filenames and Chinese `.zh-CN.md` counterparts. Run `python scripts/check_i18n.py` and `python scripts/audit_artifact.py` to check documents and public artifacts. Do not add operational configuration, private endpoints or internal publication records to commits or source archives.
