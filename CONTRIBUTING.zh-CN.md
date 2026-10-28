# 参与生长

[English](CONTRIBUTING.md) | **简体中文**

欢迎通过 GitHub Issues 报告问题或提议改进。请注明 Android 版本、应用版本、复现步骤和期望结果；先删除截图、备份与日志中的个人记录和凭据。

本项目使用 MIT 许可，第三方资源保留各自许可。提交改动前请阅读 AGENTS.md、docs/SCHEMA.md 与 docs/DEVELOPMENT_PLAN.md。

- 新组件先定义能力边界并核实开源许可，记录到 docs/OPEN_SOURCE_REVIEW.md。
- UI、记录、统计、草稿、转换与观察优先在手机执行，不为复用增加服务器。
- 保留稳定字段 ID 与独立业务记录；普通定义编辑不能绕过迁移、基线或范围检查。
- 不提交 Key、签名材料、私人记录或 .local 文件。普通测试不得调用付费 API。

Android 验证入口为 scripts/build.ps1，运行单元 / 框架测试、lint、主 APK 与设备测试 APK 编译。有真机才加 -DeviceTests，报告中区分编译与执行。

公开贡献围绕 Android 产品。中英文档同步更新，英文使用默认文件名，中文使用 `.zh-CN.md`；运行 `python scripts/check_i18n.py` 和 `python scripts/audit_artifact.py` 检查文档与公开产物。不要将运维配置、私人端点或内部发布记录加入提交或源码归档。
