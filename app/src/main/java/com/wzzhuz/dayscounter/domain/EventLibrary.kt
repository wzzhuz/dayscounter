package com.wzzhuz.dayscounter.domain

import java.time.LocalDate

/**
 * 内置事件库：常见节日与热门倒数日，**一键添加**，不用手打标题。
 *
 * 两条设计约束：
 *
 * 1. **只存规则，不存具体年份的公历日期**。
 *    农历节日存农历月日，添加时现算。硬编码「2027 年春节 = 2 月 6 日」
 *    到 2028 年就错了，属于数据腐败。
 *
 * 2. **不收录周规则类节日**（母亲节「5 月第 2 个周日」等）。
 *    这类需要新的重复规则类型，要改 [DayCountCalculator.nextOccurrence] 核心推导，
 *    收益只是多三个节日，性价比不成立。
 */
object EventLibrary {

    /** 节日的日期类型 */
    enum class Kind { GREGORIAN, LUNAR }

    data class LibraryItem(
        val name: String,
        val kind: Kind,
        /** 公历月份或农历月份，1–12 */
        val month: Int,
        /** 公历日或农历日 */
        val day: Int,
        val category: String = "节日",
        /** 展示分组 */
        val group: String,
    )

    /** 公历节日 */
    private val GREGORIAN_ITEMS = listOf(
        LibraryItem("元旦", Kind.GREGORIAN, 1, 1, group = "节日"),
        LibraryItem("情人节", Kind.GREGORIAN, 2, 14, group = "节日"),
        LibraryItem("妇女节", Kind.GREGORIAN, 3, 8, group = "节日"),
        LibraryItem("植树节", Kind.GREGORIAN, 3, 12, group = "节日"),
        LibraryItem("愚人节", Kind.GREGORIAN, 4, 1, group = "节日"),
        LibraryItem("劳动节", Kind.GREGORIAN, 5, 1, group = "节日"),
        LibraryItem("青年节", Kind.GREGORIAN, 5, 4, group = "节日"),
        LibraryItem("儿童节", Kind.GREGORIAN, 6, 1, group = "节日"),
        LibraryItem("建党节", Kind.GREGORIAN, 7, 1, group = "节日"),
        LibraryItem("建军节", Kind.GREGORIAN, 8, 1, group = "节日"),
        LibraryItem("教师节", Kind.GREGORIAN, 9, 10, group = "节日"),
        LibraryItem("国庆节", Kind.GREGORIAN, 10, 1, group = "节日"),
        LibraryItem("万圣节", Kind.GREGORIAN, 10, 31, group = "节日"),
        LibraryItem("平安夜", Kind.GREGORIAN, 12, 24, group = "节日"),
        LibraryItem("圣诞节", Kind.GREGORIAN, 12, 25, group = "节日"),
        LibraryItem("跨年", Kind.GREGORIAN, 12, 31, group = "节日"),
    )

    /** 农历节日 */
    private val LUNAR_ITEMS = listOf(
        LibraryItem("春节", Kind.LUNAR, 1, 1, group = "农历节日"),
        LibraryItem("元宵节", Kind.LUNAR, 1, 15, group = "农历节日"),
        LibraryItem("端午节", Kind.LUNAR, 5, 5, group = "农历节日"),
        LibraryItem("七夕", Kind.LUNAR, 7, 7, group = "农历节日"),
        LibraryItem("中元节", Kind.LUNAR, 7, 15, group = "农历节日"),
        LibraryItem("中秋节", Kind.LUNAR, 8, 15, group = "农历节日"),
        LibraryItem("重阳节", Kind.LUNAR, 9, 9, group = "农历节日"),
        LibraryItem("腊八节", Kind.LUNAR, 12, 8, group = "农历节日"),
        LibraryItem("除夕", Kind.LUNAR, 12, 30, group = "农历节日"),
    )

    /** 热门倒数日 */
    private val COUNTDOWN_ITEMS = listOf(
        LibraryItem("高考", Kind.GREGORIAN, 6, 7, category = "工作", group = "倒数日"),
        LibraryItem("考研", Kind.GREGORIAN, 12, 21, category = "工作", group = "倒数日"),
        LibraryItem("双十一", Kind.GREGORIAN, 11, 11, category = "生活", group = "倒数日"),
        LibraryItem("双十二", Kind.GREGORIAN, 12, 12, category = "生活", group = "倒数日"),
    )

    val all: List<LibraryItem> = GREGORIAN_ITEMS + LUNAR_ITEMS + COUNTDOWN_ITEMS

    /** 按展示分组 */
    val groups: List<String> = all.map { it.group }.distinct()

    fun byGroup(group: String): List<LibraryItem> = all.filter { it.group == group }

    /**
     * 把库条目换算成具体日期。
     *
     * **已过期的取下一个**：当前是 9 月，添加「元旦」应得明年 1 月 1 日，
     * 而不是已经过去的今年 1 月 1 日——加一个已经过去的倒数日没有意义。
     *
     * @param today 用于判断是否已过期，默认今天
     */
    fun resolveDate(item: LibraryItem, today: LocalDate = LocalDate.now()): LocalDate {
        var year = today.year
        var date = dateInYear(item, year)
        if (date.isBefore(today)) {
            year += 1
            date = dateInYear(item, year)
        }
        return date
    }

    private fun dateInYear(item: LibraryItem, year: Int): LocalDate = when (item.kind) {
        Kind.GREGORIAN -> LocalDate.of(year, item.month, item.day)
        Kind.LUNAR -> LunarCalendar.lunarDateInYear(item.month, item.day, year)
    }
}
