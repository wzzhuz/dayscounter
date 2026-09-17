# dayscounter

自用倒数日 App：记录重要日子，显示「还剩多少天」或「第 N 天」。

**为什么自己造**：Days Matter 免费版限 20 条事件、带广告，周期重复提醒与高级模板需订阅
（Pro 买断 ¥32 / 订阅 ¥3/月）。自用场景数据量只有几十条，不值得为这些付费。

## 架构速览

```
app/src/main/java/.../
├── domain/                 领域层（不依赖 Android / Room）
│   ├── Event.kt            领域模型 + CountMode 枚举
│   ├── DayCountCalculator.kt   ★ 天数计算 + 重复推导 + 计数口径
│   └── LunarCalendar.kt    ★ 农历 ↔ 公历（ICU 封装，零依赖）
├── data/
│   ├── db/                 Room 存储层
│   │   ├── AppDatabase.kt  Database + FTS 触发器
│   │   ├── EventEntity.kt  events 表（只存事实）
│   │   ├── TagEntity.kt    tags 表
│   │   ├── EventTagCrossRef.kt  多对多关联（外键 CASCADE）
│   │   ├── EventFts.kt     FTS4 虚拟表
│   │   └── EventDao.kt     查询 / 搜索 / 事务
│   ├── EventRepository.kt  Entity ↔ 领域模型 + 内存排序缓存
│   └── BackupManager.kt    JSON 导出导入
└── ui/                     Compose UI
```

## 三条铁律

1. **天数必须算对** —— 口径写进 spec，UI 美化不得改变计算逻辑
2. **数据是你的** —— 不申请任何网络权限，数据不出设备
3. **记录成本必须低** —— 加一条事件三步以内

## 天数口径（核心）

事件带 `countMode` 字段，两种模式**有意不对称**：

| 模式 | 今天 | 明天 | 昨天 |
|---|---|---|---|
| `COUNTDOWN` 倒数（不含起始日） | 就是今天（0） | 还剩 1 天 | 已过 1 天 |
| `COUNTUP` 正数（包含起始日） | 第 1 天 | 还剩 1 天 | 第 2 天 |

**不靠日期自动推断** —— 否则过期的春节会显示成「春节第 3 天」。

## 关键设计约束

- **库里只存事实**：剩余天数、下一次发生日均不落库，读取时推导
- **重复事件存规则**（原始日期 + YEARLY），不预生成每年实例
- **排序在内存做**（置顶优先 + |天数| 升序），不写进 SQL
- **JSON 仅作交换格式**，用于导出/导入换机
- 不声明 `android.permission.INTERNET`

## 出包

沙盒无 Android SDK，走 GitHub Actions 编译：

```
本地写 Kotlin 源码 → 推 GitHub → Actions 编译 → 下载 APK
```

## 规范

流程与规格见 `openspec/`：

- `project.md` —— 项目铁律与技术栈
- `specs/` —— 系统行为约定（真理源）
- `changes/` —— 进行中与已归档的变更

工作流规范本身见 [wzzhuz/my-app-workflow](https://github.com/wzzhuz/my-app-workflow)。
