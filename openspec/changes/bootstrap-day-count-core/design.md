# 技术设计：存储选型、天数口径与 Room Schema

> **本文件记录决策与理由。写完即定型，不再更新。**
> 系统「现在应该做什么」以 `../specs/` 为准。

---

## 零、判断修正：为什么从 JSON 改成 Room

**这一节必须保留。** 首版曾判定「单文件 JSON 足够，不上 Room」，本轮推翻了该结论。
把改主意的过程写下来，是为了避免半年后又有人拿旧理由再翻回去一次。

### 上一轮的论证错在哪

上一轮只测了「全量解析」这一项，就得出「JSON 够用」。这个测量**选错了指标**。

真实场景不止解析，重测三个典型场景：

| 场景 | 100 条 | 1000 条 | 10000 条 |
|---|---|---|---|
| 冷启动全链路（解析+建索引+算天数+排序） | 0.29 ms | 2.81 ms | 39.8 ms |
| **改 1 条**（读 → 改 → 全量重写） | 0.60 ms | 4.72 ms | 54.6 ms |
| **小组件刷新**（读 → 过滤 → 排序 → 取前 3） | 0.21 ms | 2.07 ms | 29.2 ms |

（沙盒 Python 3.10 实测，Android 上 Moshi 通常快 2–5 倍，故为保守下限）

**小组件那一行是决定性的**：Glance 跑在**独立进程**，每次刷新都要把全量数据重新读一遍。
29ms 只是数据层，叠加 RemoteViews 渲染开销后很容易掉帧。而小组件是 `project.md` 里**早已列好的后续计划**。

### 真正的分水岭不是数据量

我上一轮把「数据量大不大」当成判断依据，这是误诊。真正的差异在这里，**与数据量无关**：

| 能力 | JSON | Room |
|---|---|---|
| 改 1 条 | 重写**整个文件** | `UPDATE` 一行 |
| 查未来 7 天到期 | 全表扫描 | `WHERE next < ?` 走索引 |
| 全文搜索 | 手写遍历 | FTS4 原生支持 |
| 多对多（标签） | 手写嵌套 + 手动维护引用完整性 | 关系表 + 外键级联 |
| Glance 小组件 | 独立进程读全量 JSON | 标准 DAO 查询 |
| SQL 写错 | 运行时才炸 | **编译期报错** |

**哪怕只有 50 条事件，小组件依然要每次全量读一遍 JSON。**

### 迁移成本此刻为零

| 成本项 | 现在上 Room |
|---|---|
| 数据迁移脚本 | **0**（零存量用户，无旧数据） |
| 依赖 | 3 行 gradle |
| 样板代码 | Entity + DAO + Database，约 80 行（一次性） |

**判断原则：迁移成本随数据量单调递增，现在是这条曲线的最低点。**
等做到小组件 + 提醒 + 搜索再迁移，要写 Entity + DAO + JSON→DB 迁移脚本 + 双写验证，
且那时手机上已有真实数据，**迁移出错就是数据丢失**。

---

## 一、天数口径：一个必须点破的冲突

使用者指定了「**出生当天算第 1 天**」。这条要落地，必须先解决一个冲突：

> 如果「今天」既可以是「还剩 0 天」（倒数语境），又可以是「第 1 天」（正数语境），
> 那么同一个日期距离会算出两个不同答案——**到底用哪个？**

**不能靠日期自动判定。** 反例：春节是倒数事件，过期之后若自动切成正数口径，
会显示「春节第 2 天」——完全不符合直觉。

### 解法：事件显式带「计数模式」

沿用 Days Matter 的「倒数日 / 正数日」区分，事件带一个 `countMode` 字段：

| 模式 | 语义 | 今天 | 明天 | 昨天 |
|---|---|---|---|---|
| `COUNTDOWN` 倒数 | **不含**起始日 | 就是今天（0） | 还剩 1 天 | 已过 1 天 |
| `COUNTUP` 正数 | **包含**起始日 | 第 1 天 | 还剩 1 天 | 第 2 天 |

- 倒数模式：`N = 日期差`（明天 = 1 天，符合「还有几天」的直觉）
- 正数模式：`N = |日期差| + 1`（出生当天 = 第 1 天 ✅）

**默认值**：目标日 ≥ 今天 → `COUNTDOWN`；目标日 < 今天 → `COUNTUP`。
用户可在编辑页手动切换。

**为什么不能两边都取「包含」**：若倒数也 +1，明天会变成「还剩 2 天」，
这与所有倒数日产品、与用户心智都冲突。这个不对称是**有意为之**，不是疏漏。

---

## 二、Entity 设计

### 核心原则：库里只存「事实」，不存「推导值」

```
存：originEpochDay, calendarType, repeatType, countMode, pinned, ...
不存：daysUntil（每天失效）❌
不存：nextOccurrence（重复事件每年变，落库 = 每天全表 UPDATE）❌
```

**天数与下一次发生日一律读取时算。** 换 Room 不改变这条铁律。

### events 表

```kotlin
@Entity(
    tableName = "events",
    indices = [Index("pinned"), Index("categoryId")]
)
data class EventEntity(
    @PrimaryKey val id: String,

    val title: String,

    /** 原始目标日（LocalDate.toEpochDay）。重复事件存"第一次"，非"今年那次" */
    val originEpochDay: Long,

    /** GREGORIAN | LUNAR */
    val calendarType: String,

    // 农历字段，公历事件为 null
    val lunarMonth: Int? = null,
    val lunarDay: Int? = null,
    val lunarLeapMonth: Boolean? = null,

    /** COUNTDOWN | COUNTUP —— 决定"今天算 0 还是 1"，见第一节 */
    val countMode: String,

    /** NONE | YEARLY */
    val repeatType: String,

    val categoryId: String? = null,
    val pinned: Boolean = false,
    val note: String = "",

    // —— 为后续 change 预留，首版 UI 不暴露 ——
    /** 提前几天提醒；null = 不提醒（add-event-reminder change 使用） */
    val reminderDaysBefore: Int? = null,

    val createdAt: Long,
    val updatedAt: Long,
)
```

**为什么不存 `targetEpochDay` 而是 `originEpochDay`**：
按年重复的事件，每年对应的公历日期都不同。若存「今年那次」，
每年 1 月 1 日就要全表刷新一次，且跨年未打开 App 时数据是脏的。
**存原始日期 + 重复规则，读取时推导**——数据永远自洽。

### tags + 多对多（标签功能）

```kotlin
@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorArgb: Int? = null,
)

@Entity(
    tableName = "event_tag_cross_ref",
    primaryKeys = ["eventId", "tagId"],
    foreignKeys = [
        ForeignKey(EventEntity::class, ["id"], ["eventId"], onDelete = CASCADE),
        ForeignKey(TagEntity::class,  ["id"], ["tagId"],  onDelete = CASCADE),
    ],
    indices = [Index("tagId")]
)
data class EventTagCrossRef(val eventId: String, val tagId: String)

data class EventWithTags(
    @Embedded val event: EventEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(EventTagCrossRef::class, "eventId", "tagId")
    )
    val tags: List<TagEntity>,
)
```

**外键用 `onDelete = CASCADE`**：删事件时自动清交叉表，
否则会留下孤儿行，未来做「按标签筛选」时统计莫名其妙多出几条。

### FTS 全文搜索（搜索功能）

```kotlin
@Entity(tableName = "events_fts")
@Fts4(contentEntity = EventEntity::class)
data class EventFts(val title: String, val note: String)
```

⚠️ **已知陷阱（实施时必须验证）**：
Room 的 `@Fts4(contentEntity = ...)` **不会自动生成同步触发器**。
只建表不建触发器的话，搜索**永远返回空结果，且不报任何错**——
属于「编译过、跑起来、结果全错」的那类最难查的 bug。

需在 `RoomDatabase.Callback.onCreate()` 里手动执行三条触发器：

```sql
CREATE TRIGGER events_ai AFTER INSERT ON events BEGIN
  INSERT INTO events_fts(rowid, title, note) VALUES (new.rowid, new.title, new.note);
END;
-- 同样需要 AFTER DELETE 与 AFTER UPDATE 各一条
```

> 📌 实施时若发现 Room 版本行为有变，以实测为准并回填本节。
> 这条应同时补进 `my-app-workflow/踩坑清单.md`（属「症状指不到根因」型）。

**为什么 FTS 而不是 `LIKE '%关键词%'`**：
`LIKE` 无法走索引，且中文分词与大小写处理都得手写；
FTS4 是 SQLite 内置能力，Room 原生支持，代价只是上面那三条触发器。

---

## 三、为什么排序仍在内存里做，不写进 SQL

有诱惑写成：

```sql
SELECT * FROM events ORDER BY ABS(julianday(next_occurrence) - julianday('now'))
```

**不这么做。** 这条 SQL 要求把「下一次发生日」落库，
而重复事件的下一次发生日**每天都在变**——落库就回到「每天全表 UPDATE」的老问题。

正确做法：

```
DAO 查全量（几十条，微秒级）
  → 内存中推导 nextOccurrence + 计算天数
  → 排序（置顶优先 + |天数| 升序）
  → 缓存结果，UI 直接消费
```

几十条数据在内存排序是微秒级，**不值得为此引入缓存失效风险**。
这也延续了「存事实、算推导」的一致性。

---

## 四、导出导入：JSON 降级为「交换格式」

换 Room 不影响换机路径：

- **导出**：查全量 → 序列化为 JSON 文件（格式与首版设计完全一致）
- **导入**：解析 JSON → **单个事务内**批量 insert（失败整体回滚）

**JSON 从「存储格式」降级为「交换格式」**——内部享受 Room 的查询能力，
对外仍然是那个人能看懂、能 `cat` 的文件。两全其美。

---

## 五、原子性：从「文件 rename」改为「数据库事务」

首版设计的 tmp → fsync → rename 是为防「写一半被杀导致文件截断」。
Room 下 SQLite 的事务本身就是原子的（WAL + rollback journal），**不再需要手写文件级原子写入**。

导入时必须显式用 `@Transaction` 包裹全部 insert：
**部分成功是最糟的结果**——用户会以为导入完成，实际少了一半数据。

---

## 六、工程结构

```
dayscounter/
├── openspec/                          规格（真理源）
├── app/src/main/java/.../
│   ├── domain/
│   │   ├── Event.kt                   领域模型（含 CountMode 枚举）
│   │   ├── DayCountCalculator.kt      ★ 天数计算 + 重复推导 + 模式口径
│   │   └── LunarCalendar.kt           ★ 农历转换（ICU 封装，零依赖）
│   ├── data/
│   │   ├── db/
│   │   │   ├── AppDatabase.kt         Room Database + FTS 触发器
│   │   │   ├── EventEntity.kt
│   │   │   ├── TagEntity.kt
│   │   │   ├── EventTagCrossRef.kt
│   │   │   ├── EventFts.kt
│   │   │   └── EventDao.kt            ★ 查询 + 搜索 + 事务
│   │   ├── EventRepository.kt         领域模型 ↔ Entity 映射 + 内存排序缓存
│   │   └── BackupManager.kt           JSON 导出导入
│   └── ui/
│       ├── EventListScreen.kt         列表 + 搜索 + 标签筛选
│       ├── EventEditScreen.kt         编辑 + 标签选择 + 模式切换
│       └── TagManageScreen.kt         标签管理
└── .github/workflows/build.yml
```

**依赖**：Compose BOM + Material3 + Room（runtime/ktx + KSP）+ kotlinx.serialization。
**不引入**：任何网络库、广告 SDK、统计 SDK。

---

## 七、考虑过的其他方案（最终版）

| # | 方案 | 结论 |
|---|---|---|
| 1 | **单文件 JSON 存储** | ❌ **否决（原为采纳，本轮推翻）**。写入放大、全表扫描、无查询能力、无 FTS，短板全在已列入计划的功能上。降级为导出/导入的**交换格式** |
| 2 | **Room + SQL 内排序** | ❌ 否决。要求 nextOccurrence 落库，等于每天全表 UPDATE。排序放内存 |
| 3 | ProtoBuf / DataStore | ❌ 否决。不可读，备份需转换；DataStore 是为 key-value 设计的 |
| 4 | 持久化「剩余天数」 | ❌ 否决。每天零点全部失效需全表 UPDATE，且引入脏值 bug |
| 5 | 预生成重复事件每年实例 | ❌ 否决。数据量随年份线性膨胀，改原始日期要批量改 |
| 6 | 引入第三方农历库 | ❌ 否决。ICU 内置零依赖 |
| 7 | 网络 API 查农历 | ❌ 否决。违反「不申请网络权限」铁律 |
| 8 | 搜索用 `LIKE '%x%'` | ❌ 否决。不走索引、无分词，FTS4 代价只多三条触发器 |
| 9 | **首版就做小组件** | ❌ 否决。Glance 有自己的能力边界，用错会运行时崩溃（编译期发现不了）。**但 Schema 已就绪**，开 change 2 即可 |
| 10 | **首版就做通知提醒** | ❌ 否决。需通知权限、渠道适配、WorkManager 时机。**但 `reminderDaysBefore` 字段已预留**，开 change 3 即可 |
| 11 | 按月 / 按周重复 | ❌ 否决。真实场景只有生日、纪念日（按年） |
| 12 | 存时刻（时分秒） | ❌ 否决。引入「今天下午 3 点上午算几天」的无解问题 |
| 13 | 多设备云同步 | ❌ 否决。需账号 + 后端 + 冲突合并，违反铁律 |
| 14 | **计数模式靠日期自动判定** | ❌ 否决。春节过期会变成「春节第 2 天」。必须显式 `countMode` + 可手动切换 |

---

## 八、与 lifelog 的复用 / 新增差异

| 项 | 关系 |
|---|---|
| Kotlin + Compose + Actions 出包链路 | ✅ 复用 lifelog |
| debug.keystore 固定签名 | ✅ 复用（踩坑清单 §2.4） |
| workflow `branches: ['**']` | ✅ 复用（踩坑清单 §2.1） |
| 早跑一次 release（防 lintVital 埋雷） | ✅ 复用（踩坑清单 §2.3） |
| 不用脚本拼接含转义的 Kotlin 字符串 | ✅ 复用（踩坑清单 §1.1 ⭐犯过两次） |
| **存储层** | 🔶 **差异**：lifelog 用 JSON 直读，**本项目用 Room**（数据量小但查询需求多） |
| **FTS 触发器** | 🆕 新增风险点，实施后回填 `踩坑清单.md` |
