# 开发预览验证

[English](VALIDATION.md) | **简体中文**

版本 0.2.0，验证日期 2026-10-28。明确区分已执行的宿主测试和仅编译的设备测试。

## 已执行检查

| 检查 | 结果 |
|---|---|
| 单元与 Android 框架测试 | 74 项通过，失败 / 错误 / 跳过均为零 |
| Android lint | 通过 |
| 开发 APK | 构建成功，min SDK 26 / target 35 |
| 设备测试 APK | 编译成功，未执行 |
| 公开源码与 APK 检查 | 未匹配凭据、私人端点字面量或运维配置 |

应用初始 API 地址为空，模型可配置，默认为 `gpt-6.1-sol`。远端请求须使用 HTTPS；本机和安卓模拟器开发地址可使用 HTTP。测试覆盖本机 HTTP、自行配置的 HTTPS、拒绝远端 HTTP、URL 内凭据和空地址。本轮验证没有调用真实付费 API。

## 覆盖范围

- AppSpec 拒绝、兼容性、稳定 ID、默认值、必填项、选项与版本基线。
- SQLite 升级保留应用 / 记录 / 版本，持久草稿、并发保护与隔离试用。
- 撤销重做、完整差异、范围检查、取消 / 迟到结果、三方合并与显式冲突选择。
- 原生点选、改名、Activity 重建、试用 CRUD、检查和保存，320 dp / 1.3 倍字号。
- 分组 / 隐藏约束、预设记录校验、点击当天日期、陈旧动作拦截及后续改动后的撤销保护。
- 类型 / 数值 / 选项转换，默认值与预设同步转换、失败阻止、记录指纹与事务提交。
- 转换备份校验、损坏拒绝、独立副本恢复、保留后续记录与 data_epoch 校验。
- 本地观察阈值、允许列表自动修改、冻结与恢复。

测试分布：AndroidRuntimeTest 5、EditorRuntimeTest 14、MigrationRuntimeTest 8、CoreTest 26、EditingTest 10、ExtendedEditingTest 11。普通测试使用离线样例。

## 界面渲染

图片来自 Robolectric 4.16.1 / Android 35 原生图形，含合成记录，不是真机截图。

- [编辑器](qa/host-draft-editor.png)、[点选](qa/host-draft-selected.png)
- [试用](qa/host-draft-trial.png)、[检查](qa/host-draft-review.png)
- [320 dp / 大字号](qa/host-draft-compact-large-text.png)
- [界面概览](UI_DESIGN.zh-CN.md)

## 产物

开发签名 APK：208320 bytes，SHA-256 `afba24d1c962baf545dd3ba44191257857062f9dd9a1a5cc218ecb3c1f8b20a9`。重建校验结果写入 `dist/artifact-audit.json`。

公开源码包采用显式 Android 产品允许列表。API Key 由用户输入并用 Android Keystore 保护，不进入 APK 或备份；不声称业务 SQLite 文件单独加密。全局备份不含历史、草稿、试用、事件或转换快照；需要导出转换前数据时，先恢复为独立副本。

## 待验证

本机没有已连接真机，设备测试只编译、未运行。仍需检查安装 / 升级、物理 Keystore、键盘 / 返回手势、TalkBack、OEM 字体 / 边距、系统文件选择、手机上的 API 失败 / 取消及 Doze / 重启 / 省电调度。宿主 Activity 重建不能替代系统杀进程测试。

尚未开展无编程经验用户的可用性任务；自由布局、联网 / 通知、条件工作流、多选与属性级优化锁仍属后续能力。

连接设备后运行 `scripts/build.ps1 -DeviceTests`。LiveApiDeviceTest 还须显式传入 `liveApiBase`、`liveApiKey`；缺少任一项时跳过付费测试。不得记录或提交凭据。
