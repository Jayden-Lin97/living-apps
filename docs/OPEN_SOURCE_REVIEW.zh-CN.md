# 开源复用与参考记录

[English](OPEN_SOURCE_REVIEW.md) | **简体中文**

检索日期：2026-10-27。本表随模块开发更新，并区分实际分发、测试依赖和设计参考。

| 项目 | 许可 | 结论 | 原因 / 实际采用 |
|---|---|---|---|
| [DivKit](https://github.com/divkit/divkit) | Apache-2.0 | 参考，后续复杂布局的可替换候选 | 原生解释 JSON UI；支持本地描述文件。首版数据表单所需组件较少，采用更小的受约束运行时 |
| [JSON Forms](https://github.com/eclipsesource/jsonforms) | MIT | 参考 schema 与 UI 分离 | 主实现面向 Web；安卓首版避免额外引入 WebView |
| [Möbius Reflection](https://github.com/mobius-os/app-reflection) | MIT | 参考问题记录与定期反思 | 是服务端 Agent 体系；当前模块定时提出建议而不自动修复。安卓使用本地事件与规则，不迁入服务器依赖 |
| [Android Open Source Project](https://source.android.com/) | 各组件以 Apache-2.0 等许可发布 | 直接使用系统 API | SQLite、JobScheduler、Keystore、原生 UI 和网络，设备内执行 |
| [JUnit 4](https://github.com/junit-team/junit4) | EPL-1.0 | 仅测试依赖 | 验证协议拒绝、历史兼容、建议阈值与请求/响应处理 |
| [JSON-java](https://github.com/stleary/JSON-java) | Public Domain（以项目声明为准） | 仅 JVM 测试依赖 | Android 已提供 org.json，JVM 单测使用独立实现 |
| [Robolectric 4.16.1](https://github.com/robolectric/robolectric/tree/robolectric-4.16.1) | MIT；部分 Android 移植文件为 Apache-2.0 | 实际复用，仅测试依赖 | 本机没有可用模拟器加速，使用 Android 35 框架、原生 SQLite 与原生图形验证；不会加入主 APK |
| [AndroidX Test](https://github.com/android/android-test) | Apache-2.0 | 实际复用，仅设备测试依赖 | 已编译设备测试包，区分未运行的设备测试和已运行的宿主框架测试 |
| [google-java-format 1.24.0](https://github.com/google/google-java-format) | Apache-2.0 | 开发期工具，不随 APK 分发 | 规范 Java 源码格式，提升后续维护和审阅体验 |
| [Gradle Wrapper 8.9](https://github.com/gradle/gradle/tree/v8.9.0/platforms/core-runtime/wrapper-main) | Apache-2.0 | 实际随源码分发 | 固定构建入口，已配置官方发行包 SHA-256；原发行 LICENSE / NOTICE 位于 licenses/ |
| [Lucide 0.468.0](https://github.com/lucide-icons/lucide/tree/0.468.0) | ISC；部分 Feather 图标为 MIT | 实际复用 27 个矢量图标，随 APK 分发 | 固定 commit `f12b0de177fbc2a6795e99be065887e72b237123`，SVG 转 Android VectorDrawable，不加入 JS / 字体 / 网络依赖；保留完整原许可 |

未引入 DivKit、JSON Forms、Möbius 的库或代码，不在交付物中将其列为运行时依赖。后续实际复用代码须再次核实具体版本的 LICENSE。

## 第二轮检索的具体改变

存储与后台阶段对比了 WorkManager 与系统 JobScheduler，当前后台任务只有定期本地聚合，选择系统 API；没有增加独立工作管理库。Keystore 使用 Android 标准 AES-GCM 能力。验证阶段确认 Robolectric 4.12 起支持 Windows 原生运行时，实际采用 4.16.1 补充框架与 SQLite 测试；测试通过不能等同于 OEM 真机与后台省电策略验证。

背景约束参考 [Android 周期工作说明](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work) 和 [JobInfo.Builder](https://developer.android.com/reference/android/app/job/JobInfo.Builder#setPeriodic(long))。Wrapper 发行包校验值取自 [Gradle 8.9 官方 checksum](https://services.gradle.org/distributions/gradle-8.9-bin.zip.sha256)。

## UI 阶段的复用

对比继续使用 Unicode 字符和采用统一图标资源后，实际引入 Lucide 的固定版本矢量几何。转换脚本为 `scripts/import_lucide.py`，构建不需要再次下载图标；来源和资源清单见 `lucide-icons.json`。仅调整描边宽度和资源格式，完整 ISC / MIT 许可保存在 `licenses/Lucide-LICENSE.txt` 和 APK 的 `assets/lucide-license.txt`，设置页提供查看入口。品牌插画由本项目的原生 Canvas 绘制，不取自 Lucide。

## 应用编辑交互方案阶段

2026-10-27 设计、2026-10-28 实现：新增评估见 [应用编辑交互方案](APP_EDITING_INTERACTION.zh-CN.md)。0.2.0 已实现原生编辑器，没有新增第三方生产依赖。

| 项目 | 核实版本及许可 | 采用或参考决定 |
|---|---|---|
| [Puck](https://github.com/puckeditor/puck/tree/v0.21.0) | v0.21.0，commit `21470257d1f520c9b50f61196f1f02fc267c3912`；[MIT](https://github.com/puckeditor/puck/blob/v0.21.0/LICENSE) | 参考数据驱动的组件编辑与历史机制；主实现为 React，不嵌入安卓，不增加云 AI 依赖 |
| [Material Components Android](https://github.com/material-components/material-components-android/releases/tag/1.13.0) | 1.13.0；[Apache 2.0](https://github.com/material-components/material-components-android/blob/1.13.0/LICENSE) | 评估后未引入：现有平台 View 可实现非模态可收展面板；避免为单个面板迁移整个主题和 AndroidX 依赖。后续需要复杂手势时再评估 |

列表排序核实了 [ItemTouchHelper 官方 API](https://developer.android.com/reference/androidx/recyclerview/widget/ItemTouchHelper)，但未新增 AndroidX RecyclerView 依赖或锁定其版本。首轮保留原生上移下移控件；若引入拖拽库，会先锁版本并核对该版本源文件许可。

## 固定动作与数据转换模块

2026-10-28：在扩展前检索声明式规则与本地事务方案。

| 项目 | 具体版本、许可 | 决定与能力边界 |
|---|---|---|
| [JsonLogic](https://github.com/jwadhams/json-logic-js/tree/2.0.5) | 2.0.5，commit `c5c73601c90b11e98f6846609bac4dec203d1c18`；[MIT](https://github.com/jwadhams/json-logic-js/blob/2.0.5/LICENSE) | 参考用数据表示受约束决策的原则；不嵌入 JavaScript，不采用通用表达式求值器。当前 ActionSpec 只允许用户点击后添加一条经过校验的预设记录，无联网或后台执行 |
| AOSP SQLiteDatabase | [android-15.0.0_r1 源码](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-15.0.0_r1/core/java/android/database/sqlite/SQLiteDatabase.java)，Apache-2.0 | 复用设备系统的事务 API；没有拷贝该类源码或另打包数据库库。参考版本用于审阅，设备 SQLite 实际版本随系统而定 |
| SQLite 事务与备份思路 | [事务官方说明](https://www.sqlite.org/lang_transaction.html)、[Public Domain 声明](https://www.sqlite.org/copyright.html) | 不新增 SQLite 发行依赖。转换演算、SHA-256 校验、转换前 JSON 快照、记录更新和新版本在本机完成；备份与写入放在同一 SQLite 事务内，失败回滚 |

自写的 MigrationPlan 是三类固定转换：受限类型转换、显式数值倍率、完整选项映射。未接入 Flyway/Liquibase 等面向数据库 DDL 的迁移框架：本产品转换的是稳定字段 ID 下的业务值，数据库表升级仍由 SQLiteOpenHelper 管理。没有为这些模块引入服务器。

## 公开交付边界

公开源码包使用显式允许列表，仅包含 Android 产品源码、构建所需文件、第三方许可及用户 / 开发文档。文档链接和归档文件在发布前离线检查；部署配置、内部发布记录、私人地址与凭据不属于公开源码。没有新增 Android 依赖或业务服务器。
