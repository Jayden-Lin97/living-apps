# 开发预览验证

[English](VALIDATION.md) | **简体中文**

版本 0.3.1，验证日期 2026-10-29。明确区分已执行的宿主测试和仅编译的设备测试。

## 已执行检查

| 检查 | 结果 |
|---|---|
| 单元与 Android 框架测试 | 115 项通过，失败 / 错误 / 跳过均为零 |
| Android lint | 通过；零错误，三项警告（两项受系统版本限制的属性，以及已有服务引用） |
| 开发 APK | 构建成功，min SDK 26 / target 35 |
| 设备测试 APK | 编译成功，未执行 |
| 公开源码与 APK 检查 | 未匹配凭据、私人端点字面量或运维配置 |
| 离线语言目录 | 872 条翻译，960 处显式文案查找，无缺失翻译 |

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

测试分布：AndroidRuntimeTest 5、EditorRuntimeTest 14、MigrationRuntimeTest 8、CoreTest 26、EditingTest 10、ExtendedEditingTest 11、RebuildSpecTest 11、RebuildRuntimeTest 17、LanguageRuntimeTest 9、MessageCatalogTest 4。普通测试使用离线样例。

## 应用语言验证

已执行宿主检查，覆盖英文首次启动、简体中文、跟随系统、不支持语言回退、原生弹窗资源、Android 26/32 的选择持久化与 Activity 重建，以及 Android 33 应用语言服务和一次性偏好迁移。使用原生图形渲染了窄屏英文首页、设置、编辑器和重构界面。

切换保留已有定义、稳定 ID、记录、版本和编辑草稿。初始应用按创建时的语言生成，已保存用户内容保持原文。语言变化会停止待执行的重构步骤，重新打开也不会重放。语言目录随包提供，两种语言均可离线使用，包括未来的应用包发布；没有新增运行时翻译 API 或服务器。

AppLanguageDeviceTest 仅编译，未执行。该测试用合成应用和记录检查英文 → 中文 → 英文，结束后清理自己的样例并恢复先前语言选择。真机切换、升级安装和进程终止后的持久化仍待验证。

## 界面渲染

图片来自 Robolectric 4.16.1 / Android 35 原生图形，含合成记录，不是真机截图。

- [编辑器](qa/host-draft-editor.png)、[点选](qa/host-draft-selected.png)
- [试用](qa/host-draft-trial.png)、[检查](qa/host-draft-review.png)
- [320 dp / 大字号](qa/host-draft-compact-large-text.png)
- [界面概览](UI_DESIGN.zh-CN.md)

## 产物

开发签名 APK：260135 bytes，SHA-256 `c27e402f5431ce643ff42737316c1fb8a2ed6d7bc89f357854cf0b3373c8380a`。重建校验结果写入 `dist/artifact-audit.json`。

公开源码包采用显式 Android 产品允许列表。API Key 由用户输入并用 Android Keystore 保护，不进入 APK 或备份；不声称业务 SQLite 文件单独加密。全局备份不含历史、草稿、试用、事件或转换快照；需要导出转换前数据时，先恢复为独立副本。

## 待验证

本机没有已连接真机，设备测试只编译、未运行。仍需检查安装 / 升级、物理 Keystore、键盘 / 返回手势、TalkBack、OEM 字体 / 边距、系统文件选择、手机上的 API 失败 / 取消及 Doze / 重启 / 省电调度。宿主 Activity 重建不能替代系统杀进程测试。

尚未开展无编程经验用户的可用性任务；自由布局、联网 / 通知、条件工作流、多选与属性级优化锁仍属后续能力。

连接设备后运行 `scripts/build.ps1 -DeviceTests`。LiveApiDeviceTest 还须显式传入 `liveApiBase`、`liveApiKey`；缺少任一项时跳过付费测试。不得记录或提交凭据。

## 已安装应用重构验证

29 项离线检查：11 项描述/模型边界测试和 18 项 Android 框架/SQLite 测试。覆盖唯一定位、来源身份、有限步骤、受保护内容、匿名 API 上下文、列表行数变化、实时文字与旧值清空、模拟节点的真实动作派发、显式试用确认与过期、原应用更新失效、选取持久化、动态列表选择、页面大小限制、熄屏退出、取消后续步骤、版本冲突、恢复，以及切换语言不重放动作。

RebuildEntryDeviceTest 仅编译，未执行。服务窗口和应用包是宿主合成测试数据，按框架行为返回新窗口副本，并非真实 QQ 音乐会话。未调用付费 API。真机仍需验收完整的选择/授权/选取/输入/键盘/返回/搜索结果播放流程，以及 OEM 无障碍限制、应用更新和运行中断。详见[模式边界](REBUILD_MODE.zh-CN.md)。

## 运行界面调整验证（2026-10-29）

新版运行界面通过 116 项离线单元/框架测试、Android Lint 及两类 APK 构建。新增导航检查验证：打开选项不派发原应用动作，返回键先收起面板再结束会话。原生渲染覆盖中英文、明暗外观，以及 320 dp / 1.3 倍字体。未连接设备，设备测试仅编译、未执行。未调用付费 API。现有 v0.3.1 Release 附件保持不变；这些检查针对当前源码修订。

## 试用前扩大验证（2026-11-09）

从 116 项通过的基线出发，追加容易漏掉的网络协议、中文备份容量、英文长名称和旧系统兼容场景。先运行复现用例，再修复，最后重跑整套验证。六个用例在对应修复前失败，修复后通过。

| 遇到的问题 | 根因 | 修复与复验 |
|---|---|---|
| 模型已返回候选，界面仍提示未完成。有部分文本的排队响应却可能进入后续解析 | 旧代码检查 `error` 字段是否存在，未区分 JSON `null`。状态只拦截部分已知失败值 | `error` 必须为空。明确给出状态时必须为 `completed`。兼容省略状态的网关。用本机 HTTP 夹具验证成功候选、请求 `store:false` 和指定模型，以及限流正文不外显、重定向不跟随。 |
| 超过 5 MB 的中文备份或含 51 个应用的备份可以导出，却无法导入 | 导入限制文件字节数和应用数，导出没有同样的校验。中文字的 UTF-8 字节数也可能明显多于字符串长度 | 导出与导入共享数量和记录结构限制。导出逐行检查容量，完整文件按 UTF-8 字节计数，并在打开输出流前完成校验。流关闭成功后才提示导出成功。使用 425 条长中文记录和 51 个应用验证超限拒绝，数据和当前版本仍保留。 |
| 在英文界面恢复长名称应用的转换前副本失败 | 固定保留 34 个字符，未预留英文后缀的长度，拼接后超过 AppSpec 的 40 个 UTF-16 代码单元限制 | 根据当前语言的后缀计算可保留的原名长度，避免截断表情字符的代理对。验证原应用仍保留转换后的记录，独立副本恢复转换前的记录。 |
| 约 2.4 MB 的中文转换备份在提交时触发 `SQLiteBlobTooBigException` | 校验通过 `CursorWindow` 读取包含整份备份的一行，其单行容量低于应用设定的备份上限 | 用 `SQLiteStatement.simpleQueryForString()` 读取大字段，继续在同一事务中校验摘要。用 201 条记录在 API 26 和 35 上验证可提交、可恢复，损坏备份仍被拒绝。新建转换备份按 UTF-8 字节校验，已有快照保留原有恢复入口。 |

接口契约参考 [OpenAI Docs 的 Responses 对象示例](https://developers.openai.com/api/reference/python/resources/beta/subresources/responses/methods/retrieve)。备份实现参考 [Android SQLite 事务文档](https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase#beginTransactionNonExclusive())、[AOSP Android 15 的 SQLiteStatement 实现](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/core/java/android/database/sqlite/SQLiteStatement.java) 和 [Android 系统文件选择文档](https://developer.android.com/training/data-storage/shared/documents-files)。继续使用现有 Android 平台接口与测试依赖。

本机 HTTP 夹具最初使用 `jdk.httpserver`，Android 单元测试编译时的 Java 系统模块镜像不包含该模块。改用只监听环回地址的标准 `ServerSocket`，仍通过实际 HTTP 请求验证传输行为。没有请求真实模型服务。

最终通过 **128 项单元、Android 框架和环回 HTTP 测试**，失败、错误和跳过均为零。框架覆盖 API 26、29、32、33、35，SQLite 的恢复、备份往返和导入回滚跨 API 26、29、35 验证。Android Lint 零错误、三项既有警告。开发 APK 与设备测试 APK 均构建成功。当前没有可用的 Android 设备，设备测试只完成编译。实际安装与覆盖升级、系统文件选择、真实原应用无障碍会话、设备密钥库和后台调度仍需设备验收。
