# Security Issues

**English** | [简体中文](SECURITY.zh-CN.md)

Do not include API keys, cloud accounts, private keys, or personal records in public Issues, Pull Requests, demo data, screenshots, or logs.

If you discover an issue that could lead to arbitrary code execution, unauthorized access to records, credential leakage, or bypassing data transformation checks, please report it privately through this repository’s GitHub Security Advisories. If private reporting is unavailable, you may first open an Issue without exploitation details to contact the maintainers. Do not attach real credentials or personal databases to public discussions.

This is currently a developer preview, with no commitment to long-term maintenance of a stable release. Android Keystore is used to protect API keys; this does not mean that the application’s SQLite files are encrypted. Users configure their own model gateways; please make sure you understand the relevant service’s data and transport policies.

## Installed-app reconstruction

The accessibility service requires system enablement plus per-source consent in Living. Runtime access is bound to the selected package, signing certificate, installed version and definition revision. It runs explicit fixed node operations; it has no remote-control server, screenshot upload or arbitrary generated code. Source-page text is not automatically included in model context; optional appearance generation sends the user requirement and anonymous component aliases/types only.

Definitions can contain selected control text and are stored in the private local database without a separate database-encryption claim. Password/marked-sensitive nodes and recognized sensitive actions are blocked, but keyword checks are not a comprehensive sensitive-action classifier. Use the source app for authentication, payment and other sensitive operations. A local trial confirmation is not evidence of universal compatibility or business success. Report incorrect targeting, protected-content handling and stale-state behavior privately through the reporting guidance above. See [capability boundaries](docs/REBUILD_MODE.md).
