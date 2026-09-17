# Data Persistence Specification

## Purpose

定义事件数据如何存、如何保证写不坏、如何换机、如何被查询。
核心取舍写在 `design.md`：**用 Room（SQLite）做存储，JSON 降级为导出/导入的交换格式。**

---

## ADDED Requirements

### Requirement: 使用 Room 作为存储层

系统 SHALL 使用 Room（SQLite）持久化事件数据，
SHALL NOT 使用单文件 JSON、DataStore 或网络存储作为主存储。

#### Scenario: 修改单条事件

- **GIVEN** 数据库中已有 N 条事件
- **WHEN** 用户修改其中 1 条的标题
- **THEN** 仅执行一条 `UPDATE` 语句
- **AND** **不重写**整个数据集

#### Scenario: 查询未来到期事件

- **GIVEN** 需要查询未来 7 天内到期的事件（`add-event-reminder` change 使用）
- **WHEN** 执行查询
- **THEN** 通过索引条件查询，而非全表扫描后内存过滤

**理由**：写入放大与全表扫描是 JSON 方案的固有短板，
且**与数据量无关**——Glance 小组件跑在独立进程，
即使只有 50 条事件，每次刷新也要把全量 JSON 读一遍。
迁移成本在零存量数据时为零，之后随数据量单调递增，故此刻切换。
完整论证见 `design.md` 第零节。

---

### Requirement: 库里只存事实，不存推导值

数据库 SHALL 只存储**事实字段**（原始日期、历法类型、重复规则、计数模式等）。

系统 SHALL NOT 持久化以下推导值：
- ❌ 剩余天数（每天零点全部失效）
- ❌ 下一次发生日（重复事件每年变化，落库即需每天全表刷新）

#### Scenario: 跨年首次打开

- **GIVEN** 存有按年重复的事件，用户上次打开是 2026 年
- **WHEN** 2027 年首次打开 App
- **THEN** 天数基于 2027 年推导，数据正确
- **AND** 数据库中不存在需要年度刷新的字段

**理由**：推导值落库意味着必须有个东西负责刷新它，
而那个东西（定时任务 / 启动检查）一定会漏掉某种情况。
存事实、算推导，数据永远自洽。

---

### Requirement: 写入原子性由事务保证

系统 SHALL 依赖 Room / SQLite 的事务保证写入原子性，
SHALL NOT 手写文件级原子写入（tmp → rename）。

批量操作（尤其导入）SHALL 在单个 `@Transaction` 内完成。

#### Scenario: 写入过程中进程被杀

- **GIVEN** 用户正在保存事件
- **WHEN** 写入过程中进程被杀或手机断电
- **THEN** 数据库回滚到上一个一致状态
- **AND** 不出现半截数据

#### Scenario: 导入中途失败

- **GIVEN** 用户导入一个含 100 条事件的 JSON，第 50 条数据格式错误
- **WHEN** 导入过程出错
- **THEN** **全部回滚**，一条都不写入
- **AND** 保留导入前的数据
- **AND** 提示明确错误

**理由**：SQLite 事务本身即原子（WAL + rollback journal），
无需再手写文件级保护。**部分成功是最糟的结果**——
用户会以为导入完成，实际少了一半数据。

---

### Requirement: 查询在 IO 线程执行

系统 SHALL 在 `Dispatchers.IO` 上执行数据库查询与写入，
SHALL NOT 在主线程执行数据库操作。

DAO 查询 SHALL 返回 `Flow` 以便数据变更时 UI 自动刷新。

#### Scenario: 冷启动

- **GIVEN** App 冷启动，需加载全部事件
- **WHEN** 数据加载中
- **THEN** UI 先显示加载态，主线程不被阻塞
- **AND** 加载完成后事件列表出现

#### Scenario: 数据变更后自动刷新

- **GIVEN** 用户正在事件列表页
- **WHEN** 新增一条事件
- **THEN** 列表自动包含新事件
- **AND** 无需手动调用刷新

**理由**：Room 天然支持返回 `Flow` 并自动在主线程之外执行，
这是它相对 JSON 方案的一项免费收益——手写 JSON 方案需自己做变更通知。

---

### Requirement: 外键级联删除

标签关联表 SHALL 声明外键并配置 `onDelete = CASCADE`。

#### Scenario: 删除带标签的事件

- **GIVEN** 事件 A 关联了标签「家人」
- **WHEN** 删除事件 A
- **THEN** 关联表中的对应行被自动清理
- **AND** 不存在指向已删除事件的孤儿行

**理由**：孤儿行不会立即报错，但会在「按标签统计/筛选」时
让数字莫名其妙多出几条，属于极难定位的脏数据。

---

### Requirement: 导出与导入使用 JSON 格式

系统 SHALL 支持将所有事件导出为 JSON 文件，
并 SHALL 支持从该文件完整还原。

导入 SHALL 还原：标题、原始日期、历法类型（含农历字段）、计数模式、
重复规则、分类、标签关联、置顶状态、备注。

#### Scenario: 换机迁移

- **GIVEN** 用户在旧手机导出 `dayscounter-backup.json`
- **WHEN** 在新手机导入该文件
- **THEN** 事件数量与全部字段完全一致
- **AND** 标签及其关联关系完整还原
- **AND** 天数按新设备的当前日期重新计算（**不沿用导出时的值**）

#### Scenario: 导入格式错误的文件

- **GIVEN** 用户选择了一个非本 App 导出的 JSON 文件
- **WHEN** 解析失败
- **THEN** 提示明确的错误信息
- **AND** **不清空**已有数据

**理由**：JSON 从「存储格式」降级为「交换格式」——
内部享受 Room 的查询能力，对外仍是那个人能看懂、能 `cat` 的文件。
没有云同步，导出导入是唯一的换机通道，必须可靠。
「导入失败却清空了原数据」是灾难性 bug，必须显式防住。

---

### Requirement: 不申请任何网络权限

系统 SHALL NOT 在 `AndroidManifest.xml` 中声明 `android.permission.INTERNET`。
系统 SHALL NOT 引入任何网络、广告、统计、崩溃上报类 SDK。

#### Scenario: 检查权限声明

- **GIVEN** 检查构建产物的 `AndroidManifest.xml`
- **WHEN** 查看 `<uses-permission>` 列表
- **THEN** 不存在 `android.permission.INTERNET`

**理由**：倒数日记录的是生日、纪念日这类最私人的信息，
没有任何理由让它联网。不声明权限 = 从系统层面保证数据出不了设备。

**连带约束**：
- 农历转换必须走本地 ICU，不得走网络 API（见 `day-count` spec）
- 全文搜索必须走本地 FTS，不得走云端（见 `event-management` spec）
