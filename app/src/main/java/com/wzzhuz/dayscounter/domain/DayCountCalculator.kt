package com.wzzhuz.dayscounter.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 天数计算。本 App 的核心——UI 可以丑，天数不能错。
 *
 * 三条铁律：
 * 1. **只比较日期，不比较时刻** —— 事件只存 LocalDate
 * 2. **重复事件存规则，动态推导** —— 不预生成每年实例
 * 3. **天数实时计算，绝不落库** —— 落库意味着每天零点全部失效
 */
object DayCountCalculator {

    /**
     * 计算事件在当前日期下的下一次发生日。
     *
     * - 非重复事件：直接返回原始日期
     * - 按年重复（公历）：取第一个 >= today 的年份对应日期
     * - 按年重复（农历）：每年重新做一次农历→公历转换，
     *   **不使用「去年的公历日期 + 365 天」这类近似推算**
     *
     * @param today 设备本地时区的今天（本 App 不做跨时区处理）
     */
    fun nextOccurrence(event: Event, today: LocalDate): LocalDate {
        if (event.repeatType != RepeatType.YEARLY) {
            return event.originDate
        }

        // 农历按年重复：逐年转换，找到第一个不早于今天的年份
        if (event.calendarType == CalendarType.LUNAR) {
            val month = event.lunarMonth ?: return event.originDate
            val day = event.lunarDay ?: return event.originDate
            for (year in today.year..(today.year + 1)) {
                val candidate = LunarCalendar.lunarDateInYear(month, day, year)
                if (!candidate.isBefore(today)) return candidate
            }
            // 理论上不可达（明年一定有），兜底返回明年的
            return LunarCalendar.lunarDateInYear(month, day, today.year + 1)
        }

        // 公历按年重复
        val thisYear = safeDateInYear(event.originDate, today.year)
        return if (!thisYear.isBefore(today)) {
            thisYear
        } else {
            safeDateInYear(event.originDate, today.year + 1)
        }
    }

    /**
     * 把 [origin] 的月日搬到 [year] 年。
     *
     * **2 月 29 日 → 平年回退到 2 月 28 日**。
     * 不处理会在平年抛 DateTimeException 导致整页崩溃，
     * 且只在特定日期出现，测试期很难碰到。
     */
    private fun safeDateInYear(origin: LocalDate, year: Int): LocalDate {
        if (origin.monthValue == 2 && origin.dayOfMonth == 29 && !isLeapYear(year)) {
            return LocalDate.of(year, 2, 28)
        }
        return LocalDate.of(year, origin.monthValue, origin.dayOfMonth)
    }

    private fun isLeapYear(year: Int): Boolean = java.time.Year.isLeap(year.toLong())

    /**
     * 计算显示用的天数。
     *
     * | 模式       | 今天      | 明天      | 昨天      |
     * |-----------|----------|----------|----------|
     * | COUNTDOWN | 0（就是今天）| 还剩 1 天 | 已过 1 天 |
     * | COUNTUP   | 第 1 天   | 还剩 1 天 | 第 2 天   |
     *
     * @return [DayCountResult]，已区分倒数/已过/正数三种显示态
     */
    fun calculate(event: Event, today: LocalDate): DayCountResult {
        val next = nextOccurrence(event, today)
        val diff = ChronoUnit.DAYS.between(today, next)  // >0 未来，0 今天，<0 过去

        return when {
            diff > 0 -> DayCountResult.Future(diff.toInt())
            diff == 0L -> {
                if (event.countMode == CountMode.COUNTUP) {
                    DayCountResult.CountUp(1)   // 出生当天 = 第 1 天
                } else {
                    DayCountResult.Today        // 就是今天
                }
            }
            else -> {
                val absDiff = (-diff).toInt()
                if (event.countMode == CountMode.COUNTUP) {
                    // 正数模式：绝对值 + 1（昨天 = 第 2 天）
                    DayCountResult.CountUp(absDiff + 1)
                } else {
                    // 倒数模式：过期后显示「已过 N 天」，不自动切换口径
                    DayCountResult.Past(absDiff)
                }
            }
        }
    }
}

/** 天数的显示态。用 sealed class 是为了让 UI 无法漏处理某种情况 */
sealed interface DayCountResult {
    /** 还剩 N 天（未来） */
    data class Future(val days: Int) : DayCountResult

    /** 就是今天（倒数模式下 diff == 0） */
    data object Today : DayCountResult

    /** 已过 N 天（倒数模式下 diff < 0）。**不显示为负数** */
    data class Past(val days: Int) : DayCountResult

    /** 第 N 天（正数模式）。出生当天 = 第 1 天 */
    data class CountUp(val days: Int) : DayCountResult
}
