# Living Apps project conventions

**English** | [简体中文](AGENTS.zh-CN.md)

- Product: an Android host app for personal sub-apps that can be created, edited, observed and restored.
- Public positioning emphasizes personal-app creation, installed-app interface reconstruction and continuous evolution. Keep computation and storage placement in technical/privacy explanations; do not promote "local-first" as a selling point.
- Keep the public homepage and repository Markdown bilingual. English uses default filenames; Chinese uses `.zh-CN` counterparts with manual switches. Update both editions and keep document links in the selected language.
- Before entering a new module or replacing a dependency, search for reusable open-source projects, verify the license of the exact version, and record adoption, reference or rejection reasons in `docs/OPEN_SOURCE_REVIEW.md`. Do not add a server merely to reuse a project.
- Prefer on-device execution for UI, data, observation statistics, rules, compatibility checks, versions and background scheduling. Update the computation deployment table and justify the need before introducing a server.
- All LLMs use configurable APIs. The default is `gpt-6.1-sol`; Responses requests must include `store:false`. Never silently switch models.
- Never commit API keys, development credentials, private records or `.local/` content. Keys must not be compiled into the APK; protect credentials with Android Keystore.
- Publish Android product code and necessary user/developer documentation only. Keep operational configuration, private endpoints, internal publication records and promotional production material outside the public source allowlist. Check archive contents as well as the working tree before publishing.
- Sub-apps are constrained AppSpec data. Never execute arbitrary generated code. Explicitly define the capability boundaries of every new component.
- Stable field IDs and business records are independent of layout versions. Never delete records to restore a UI. Check the baseline before applying a version so stale changes cannot overwrite a newer version.
- Automatic changes must pass the local allowlist and obey freezing, frequency and restoration mechanisms. Do not claim effective optimization without enough samples.
- Ordinary tests must not call paid APIs. Live API tests require explicit opt-in, read credentials from environment variables or process parameters, and must not log raw error response bodies.
- Run unit, Android framework and device tests appropriate to the change. If no device is available, distinguish that in the validation report; compiling a test is not executing it.

Build entry point: `scripts/build.ps1`. On Windows, place test output in a local ASCII-only directory while keeping source in the original workspace.
