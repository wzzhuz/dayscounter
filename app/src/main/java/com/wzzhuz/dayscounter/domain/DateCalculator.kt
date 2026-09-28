package com.wzzhuz.dayscounter.domain

import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 日期计算器：独立于事件列表的工具。
 *
 * **纯日期运算，不引入时刻与时区**——与事件天数计算同源（铁律一）。
 * 引入时区会让「相差几天」在不同时区得到不同答案。
 *
 * 用 [ChronoUnit.DAYS.between] 而不是自己算：
 * `LocalDate` 的 `toEpochDay()` 相减本就是纯日期差，不含时区与夏令时，
 * JDK 已经实现了，没有理由自己写。
 */
object DateCalculator {

    private val WEEKDAY_FMT = DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日 EEEE")
    private val WEEKDAY_SHORT = DateTimeFormatter.ofPattern("M月d日 E")

    /**
     * 两日期相隔天数。
     *
     * **取绝对值**：间隔是距离，不是位移。
     * 显示负数会让用户困惑——他只是想知道「还有多久」。
     */
    fun daysBetween(a: LocalDate, b: LocalDate): Long =
        kotlin.math.abs(ChronoUnit.DAYS.between(a, b))

    /**
     * 年月日分解。
     *
     * ⚠️ `Period.between(2026-01-31, 2026-03-01)` 得「1 月 1 天」而非 29 天——
     * 这是 `Period` 的语义（按日历单位逐字段算），**属正常不是 bug**。
     * 所以 UI 上必须标注「约」。
     */
    fun periodBetween(a: LocalDate, b: LocalDate): Period {
        val (from, to) = if (a.isAfter(b)) b to a else a to b
        return Period.between(from, to)
    }

    /** 格式化年月日分解，如「2 年 2 月 14 天」。为零的部分省略 */
    fun formatPeriod(period: Period): String {
        val parts = mutableListOf<String>()
        if (period.years != 0) parts += "${period.years} 年"
        if (period.months != 0) parts += "${period.months} 月"
        if (period.days != 0 || parts.isEmpty()) parts += "${period.days} 天"
        return parts.joinToString(" ")
    }

    /** 某日期前/后 N 天是哪天。n 为负表示往前 */
    fun addDays(date: LocalDate, n: Long): LocalDate = date.plusDays(n)

    /** 带星期的可读格式 */
    fun formatWithWeekday(date: LocalDate): String = date.format(WEEKDAY_FMT)

    /** 简短格式，用于结果副标题 */
    fun formatShort(date: LocalDate): String = date.format(WEEKDAY_SHORT)

    /**
     * 一周中的第几天，中文。
     * 用 ICU 无关的硬编码：星期名不随历法变化，不值得为此引入依赖。
     */
    fun weekdayName(date: LocalDate): String =
        listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[
            date.dayOfWeek.value - 1
        ]
}
