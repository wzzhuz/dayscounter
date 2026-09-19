# 实施清单：bootstrap-day-count-core

> 状态图例：`[ ]` 未开始 · `[x]` 已完成 · `[-]` 主动跳过（需注明原因）

## 阶段一：规格落地（本 change 的文档部分）

- [x] 写 `proposal.md`
- [x] 写 `design.md`
- [x] 写 `specs/day-count/spec.md`
- [x] 写 `specs/event-management/spec.md`
- [x] 写 `specs/data-persistence/spec.md`
- [x] 写 `openspec/project.md`（三条铁律）
- [x] 写 `openspec/AGENTS.md`（指向 my-app-workflow）
- [x] 写 `README.md`（架构速览）

## 阶段二：推送到 GitHub

- [x] **先回显目标仓库名与本地源目录，获使用者确认**（踩坑清单 §4.7）
- [x] 创建仓库 `wzzhuz/dayscounter`
- [x] 推送 `openspec/` 全部内容
- [x] **推完立刻列远端根目录校验**（踩坑清单 §4.6）

## 阶段三：工程骨架

- [ ] 建 Android 工程（Kotlin + Compose + Material3 + Room + kotlinx.serialization）
- [ ] 引入 Room 依赖（runtime / ktx + **KSP**，注意 KSP 版本不与 Kotlin 绑定，踩坑清单 §1.2）
- [ ] **固定 debug.keystore 并引用**（防第二次编译装不上，踩坑清单 §2.4）
- [ ] 配阿里云 Maven 镜像（否则编译极慢，环境能力边界 §5.1）
- [ ] `AndroidManifest.xml`：确认**不声明** INTERNET 权限
- [x] 建 `.github/workflows/build.yml`
  - [ ] 触发规则用 `branches: ['**']`（**不要用逐个前缀**，会静默不跑，踩坑清单 §2.1）
- [x] 推一次空工程，确认 Actions 确实跑起来并出 APK

## 阶段四：领域层（核心）

- [ ] `Event.kt` —— 领域模型（**只存 LocalDate，不存时刻**；含 `CountMode` 枚举）
- [ ] `DayCountCalculator.kt`
  - [ ] `COUNTDOWN` 模式：纯日期差（今天=0 / 明天=1 / 昨天=已过 1）
  - [ ] `COUNTUP` 模式：**|日期差| + 1**（今天=第 1 天 / 昨天=第 2 天）
  - [ ] 按年重复：推导不早于今天的最近发生日
  - [ ] **2 月 29 日 → 平年回退到 2 月 28 日**
  - [ ] 默认模式：目标 ≥ 今天 → COUNTDOWN；否则 COUNTUP
- [ ] `LunarCalendar.kt`
  - [ ] 封装 `android.icu.util.ChineseCalendar`
  - [ ] 农历 ↔ 公历双向转换
  - [ ] 农历按年重复：每年重新转换，不做 +365 天近似

## 阶段五：数据层（Room）

- [ ] `db/AppDatabase.kt`
  - [ ] Room Database，注册 entities（Event / Tag / CrossRef / Fts）
  - [ ] ⚠️ **`onCreate` 里手动建 FTS 三条触发器**（Room 不自动生成，否则搜索永远为空且不报错）
- [ ] `db/EventEntity.kt`
  - [ ] `originEpochDay`（**存"第一次"，不存"今年那次"**）
  - [ ] `calendarType` + 农历字段（lunarMonth / lunarDay / lunarLeapMonth）
  - [ ] `countMode`、`repeatType`、`pinned`、`categoryId`、`note`
  - [ ] 预留 `reminderDaysBefore`（供 add-event-reminder change）
  - [ ] **不存** daysUntil / nextOccurrence
- [ ] `db/TagEntity.kt` + `db/EventTagCrossRef.kt`
  - [ ] 多对多关系表
  - [ ] **外键 `onDelete = CASCADE`**（防孤儿行）
  - [ ] `indices = [Index("tagId")]`
- [ ] `db/EventFts.kt` —— `@Fts4(contentEntity = EventEntity::class)`
- [ ] `db/EventDao.kt`
  - [ ] 查询返回 `Flow`（自动后台执行 + 变更通知）
  - [ ] `@Transaction` 的 `getEventsWithTags()`
  - [ ] FTS 搜索（标题 + 备注）
  - [x] 按标签筛选
- [ ] `EventRepository.kt`
  - [ ] Entity ↔ 领域模型映射
  - [ ] 排序结果缓存（置顶优先 + |天数| 升序）
  - [ ] **禁止**在 `items{}` 内做 `indexOfFirst` / `find` / `filter` / `sortedBy`
- [ ] `BackupManager.kt`
  - [ ] 导出 JSON（含标签关联关系）
  - [ ] 导入 JSON：**单事务，失败全回滚，不清空已有数据**

## 阶段六：UI 层

- [ ] `EventListScreen.kt`
  - [x] 列表：天数 / 标题 / 分类 / 标签
  - [x] 置顶事件视觉区分
  - [x] 「就是今天」（倒数）与「第 N 天」（正数）分别标记
  - [x] 搜索入口 + 结果空状态提示
  - [x] 按标签筛选
- [ ] `EventEditScreen.kt`
  - [x] 标题 + 日期为必填，其余有默认值
  - [x] 公历 / 农历切换
  - [x] **倒数 / 正数模式切换**（默认按日期给值）
  - [x] 分类选择（三个默认 + 自定义）
  - [x] 标签多选
  - [x] 按年重复开关
  - [x] 置顶开关
  - [x] 删除前确认弹窗
- [ ] `TagManageScreen.kt` —— 标签增删改
- [ ] 导出 / 导入入口（设置页）

## 阶段七：验证

- [ ] 单元测试：倒数口径（今天=0 / 明天=1 / 昨天=已过 1）
- [ ] 单元测试：**正数口径（今天=第 1 天 / 昨天=第 2 天）** ⭐ 使用者明确指定
- [ ] 单元测试：倒数模式事件过期后**不**变成「第 N 天」
- [ ] 单元测试：**2 月 29 日平年回退**
- [ ] 单元测试：农历对照基线（春节、中秋、端午若干年份）
- [ ] 手动验证：搜索备注中的关键词能命中 ⚠️ **重点验 FTS 触发器**
- [ ] 手动验证：一个事件打两个标签，两个标签都能筛出它
- [ ] 手动验证：删除带标签事件后无孤儿行
- [ ] 手动验证：加满 25 条事件，确认无数量限制提示
- [ ] 手动验证：导出 → 清数据 → 导入，字段与标签完整还原
- [x] 检查产物 Manifest 无 INTERNET 权限
- [ ] **手动跑一次 release 构建**（debug 不跑 lintVital，问题会潜伏，踩坑清单 §2.3）

## 阶段八：收尾

- [ ] 代码中的天数计算与 spec 逐条比对
- [ ] 更新 `openspec/specs/`（若有实施中发现的口径调整）
- [ ] 清理临时诊断产物（如 `ci-logs/`，踩坑清单 §2.5）
- [ ] 等使用者确认后归档

## 实施中发现的新坑（待回填 my-app-workflow/踩坑清单.md）

- AGP 9.0 起内置 Kotlin，**不能再声明 `org.jetbrains.kotlin.android` 插件**，否则构建直接失败
- AGP 已内置名为 `debug` 的 SigningConfig，用 `create("debug")` 会报 already exists，必须 `getByName("debug")`
- FTS 实体**不能带 `@PrimaryKey`**（FTS 表用 rowid）
- `ChineseCalendar` 闰月字段是 `Calendar.IS_LEAP_MONTH`（在基类上），不是 `ChineseCalendar.LEAP_MONTH`
- `ChineseCalendar` 设年份必须用 `Calendar.EXTENDED_YEAR`，用 ERA+YEAR 是 1–60 的周期年，会静默算错
- GitHub 新仓库 GITHUB_TOKEN 默认只读，workflow 里要 push 必须显式声明 `permissions: contents: write`
- 声明了 `permissions:` 块会覆盖默认值，只写 contents 会让 actions 权限变 none，artifact 上传失败
- `actions/upload-artifact@v4` 同名 artifact 已存在时会失败，需 `overwrite: true`
- workflow 里 `git add` 被 `.gitignore` 忽略的目录会静默无内容、commit 失败、push 不执行，且步骤仍显示 success
- `by` 委托（`var x by remember { mutableStateOf(...) }`）依赖 `import androidx.compose.runtime.getValue/setValue`。
  按「未使用 import」自动清理会误删这两个 —— 它们在代码里不出现标识符名，
  症状是 `Type 'MutableState<String>' has no method 'setValue'`，**报错信息完全指不到根因**

## 主动跳过

（暂无。若有，必须写明原因，不把先斩后奏粉饰成按部就班）

## 检查项（每次改动后跑）

| 检查 | 方法 |
|---|---|
| Kotlin 字符串跨行 | 不用脚本拼接含转义的字符串（**已犯两次**，踩坑清单 §1.1） |
| 推送目标正确 | echo 出仓库 + 路径，肉眼确认 |
| 推送后校验 | 列**远端**文件列表比对（不查本地） |
| CI 确实跑了 | Actions 列表里**有没有这条分支** |
