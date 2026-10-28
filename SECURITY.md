# Security Issues

**English** | [简体中文](SECURITY.zh-CN.md)

Do not include API keys, cloud accounts, private keys, or personal records in public Issues, Pull Requests, demo data, screenshots, or logs.

If you discover an issue that could lead to arbitrary code execution, unauthorized access to records, credential leakage, or bypassing data transformation checks, please report it privately through this repository’s GitHub Security Advisories. If private reporting is unavailable, you may first open an Issue without exploitation details to contact the maintainers. Do not attach real credentials or personal databases to public discussions.

This is currently a developer preview, with no commitment to long-term maintenance of a stable release. Android Keystore is used to protect API keys; this does not mean that the application’s SQLite files are encrypted. Users configure their own model gateways; please make sure you understand the relevant service’s data and transport policies.
