# 任务清单

> 按顺序执行。推送前必须回显目标仓库（流程规范第五节）。

## 一、数据层

- [ ] `EventEntity` 加 `archivedAt: Long?` 与 `colorArgb: Int?`
- [ ] `CategoryEntity` 加 `colorStartArgb` / `colorEndArgb`
- [ ] DB 升到 **v3**，写 `MIGRATION_2_3`（**禁止** fallbackToDestructiveMigration）
  - [ ] ALTER TABLE events ADD COLUMN archivedAt INTEGER
  - [ ] ALTER TABLE events ADD COLUMN colorArgb INTEGER
  - [ ] ALTER TABLE categories ADD COLUMN colorStartArgb INTEGER
  - [ ] ALTER TABLE categories ADD COLUMN colorEndArgb INTEGER
  - [ ] 为已有三个默认分类回填配色
  - [ ] **验证**：升级后原事件不丢（真机或至少 SQL 层面确认）
- [ ] DAO：主列表查询加 `WHERE archivedAt IS NULL`
- [ ] DAO：`observeArchived()` 查 `archivedAt IS NOT NULL`
- [ ] DAO：`archive(id, time)` / `unarchive(id)`
- [ ] DAO：分类色查询

## 二、事件库

- [ ] 建 `domain/EventLibrary.kt`，存规则不存日期
  - [ ] 公历节日：元旦、情人节、妇女节、植树节、愚人节、劳动节、青年节、儿童节、教师节、国庆节、万圣节、双十一、平安夜、圣诞、跨年
  - [ ] 农历节日：春节、元宵、端午、七夕、中元、中秋、重阳、腊八、除夕
  - [ ] 热门倒数日：高考(6/7)、考研(12/21)、双十二(12/12)
  - [ ] **不收录**母亲节/父亲节/感恩节（周规则，见 design.md）
- [ ] `addFromLibrary(item, year)`：农历走 `lunarDateInYear`，公历走 `LocalDate.of(year, m, d)`
- [ ] **已过期则取下一年**（Spec Scenario: 库中的条目已过期）
- [ ] 库条目带建议分类「节日」，自动设每年重复
- [ ] 回归校验：春节/中秋换算结果与已知日期比对

## 三、日期计算器

- [ ] 建 `domain/DateCalculator.kt`
  - [ ] `daysBetween(a, b)`：绝对值，`ChronoUnit.DAYS.between`
  - [ ] `periodBetween(a, b)`：`Period.between`，标注「约」
  - [ ] `addDays(date, n)`：`date.plusDays(n)`，含星期
  - [ ] 跨闰年验证（2024-02-28 +1 = 02-29）
- [ ] 建 `ui/DateCalculatorScreen.kt`：两个 Tab（间隔 / 推算）

## 四、归档

- [ ] Repository：`archiveAsync` / `unarchiveAsync` / `deleteForeverAsync`
- [ ] 归档页 `ui/ArchiveScreen.kt`：列表 + 恢复 + 彻底删除（二次确认）
- [ ] 列表页/编辑页入口：归档按钮
- [ ] **导出导入含 archivedAt**（换机后仍在归档页，不回主列表）

## 五、视觉

- [ ] 建 `ui/theme/ColorPalette.kt`：五组渐变 + 哈希分配
- [ ] 默认分类回填配色（纪念日粉 / 工作蓝 / 生活绿 / 节日橙）
- [ ] 卡片改渐变背景，文字转深色保对比度
- [ ] **天数数字放大为第一视觉焦点**
- [ ] 卡片显示分类名（色块 + 文字）
- [ ] 编辑页加「自定义颜色」入口（可选，默认跟分类）

## 六、串联与出包

- [ ] `MainActivity` 加页面路由：列表 / 事件库 / 计算器 / 归档
- [ ] 顶部菜单或底部导航放入口
- [ ] 推送 → Actions 出包 → **校验远端文件内容**（踩坑清单 §4.6）

## 七、收尾

- [ ] 代码与 spec 逐条比对（尤其「已过期取下一年的节日」）
- [ ] 更新 `openspec/specs/`（归档后主列表口径变化）
- [ ] 回填新坑到 `my-app-workflow/踩坑清单.md`
- [ ] 等使用者确认后归档本 change

## 主动跳过

- 桌面小组件（单开 change）
- 到期提醒（单开 change，`reminderDaysBefore` 字段已预留）
- 云同步（违反铁律二）
