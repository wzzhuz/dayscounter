# AGENTS.md

本项目的 AI 协作指引。

## 流程规范不在本仓库

**完整流程规范见 [wzzhuz/my-app-workflow](https://github.com/wzzhuz/my-app-workflow)**，
核心文件是它的 `元宝分组指令.md`。本仓库不复制一份规范，避免规范分叉。

新会话开始时，AI 助手应当：

1. 读本文件（定位项目）
2. 读 `openspec/project.md`（项目铁律与技术栈）
3. 列出 `openspec/specs/` 下的模块与 `openspec/changes/` 下的进行中改动
4. 用 3-5 句话向使用者复述当前状态，**然后停下来等待确认**

## 本项目的关键背景

| 项 | 内容 |
|---|---|
| 应用 | 倒数日（记录重要日子，显示还剩/已过多少天） |
| 为什么自建 | Days Matter 免费版限 20 条 + 带广告，重复提醒要订阅 |
| 平台 | Android 原生，Kotlin + Jetpack Compose |
| 出包 | 沙盒不能编译 APK → 推源码到 GitHub → Actions 编译 |
| 铁律 | 天数算对 / 数据不出设备 / 记录成本低 |

## 动手前必读的踩坑清单

`my-app-workflow/踩坑清单.md` 里的高频坑，与本项目的对应关系：

| 坑 | 本项目在哪里会遇到 |
|---|---|
| 脚本生成 Kotlin 时 `\n` 被转义（**犯过两次**） | 写含字符串字面量的 Kotlin 文件时 |
| workflow 触发规则不匹配，Actions **静默不跑** | 建 `.github/workflows/build.yml` 时 |
| debug 签名每次重新生成，APK 装不上 | 配置 `build.gradle.kts` 时 |
| release 才跑 lintVital，问题潜伏 | 首次出正式包时 |
| 列表渲染里 O(n²)（`indexOfFirst` / `find`） | 事件列表排序时 |
| 推送到错误的仓库 | 沙盒里同时有 lifelog 和 dayscounter 时 |
| 本地副本落后远端，推送时整体覆盖 | 跨会话改同一批文件时 |

## 本项目的特有约束

- **天数必须实时计算，绝不持久化**（`day-count` spec）
- **重复事件存规则，不预生成每年实例**（`day-count` spec）
- **排序结果缓存，不在 `items{}` 内排序**（`event-management` spec）
- **不声明 `android.permission.INTERNET`**（`data-persistence` spec）
- **不引入 Room** —— 数据量离瓶颈差三个数量级，见 change 的 `design.md`
