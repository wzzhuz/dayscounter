package com.wzzhuz.dayscounter.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 天数口径的单元测试。
 *
 ** 这是本 App 最该被测试的部分**：
 * UI 错了改一行就行，天数错了会让使用者对一个重要日子记错时间，
 * 而且这种错误**不崩溃、不报错**，只能靠测试兜住。
 *
 * 只依赖 java.time，是纯 JVM 测试，无需 Android 环境。
 */
class DayCountCalculatorTest {

    private fun event(
        date: LocalDate,
        mode: CountMode = CountMode.COUNTDOWN,
        repeat: RepeatType = RepeatType.NONE,
        calendar: CalendarType = CalendarType.GREGORIAN,
    ) = Event(
        id = "t",
        title = "t",
        originDate = date,
        calendarType = calendar,
        countMode = mode,
        repeatType = repeat,
    )

    // ---------- 倒数口径 ----------

    @Test
    fun `倒数 今天算 0`() {
        val today = LocalDate.of(2026, 9, 28)
        val r = DayCountCalculator.calculate(event(today), today)
        assertEquals(DayCountResult.Today, r)
    }

    @Test
    fun `倒数 明天还剩 1 天`() {
        val today = LocalDate.of(2026, 9, 28)
        val r = DayCountCalculator.calculate(event(today.plusDays(1)), today)
        assertEquals(DayCountResult.Future(1), r)
    }

    @Test
    fun `倒数 昨天已过 1 天`() {
        val today = LocalDate.of(2026, 9, 28)
        val r = DayCountCalculator.calculate(event(today.minusDays(1)), today)
        assertEquals(DayCountResult.Past(1), r)
    }

    // ---------- 正数口径（使用者明确指定：出生当天 = 第 1 天）----------

    @Test
    fun `正数 今天是第 1 天`() {
        val today = LocalDate.of(2026, 9, 28)
        val r = DayCountCalculator.calculate(
            event(today, mode = CountMode.COUNTUP), today
        )
        assertEquals(DayCountResult.CountUp(1), r)
    }

    @Test
    fun `正数 昨天是第 2 天`() {
        val today = LocalDate.of(2026, 9, 28)
        val r = DayCountCalculator.calculate(
            event(today.minusDays(1), mode = CountMode.COUNTUP), today
        )
        assertEquals(DayCountResult.CountUp(2), r)
    }

    @Test
    fun `正数 前天是第 3 天`() {
        val today = LocalDate.of(2026, 9, 28)
        val r = DayCountCalculator.calculate(
            event(today.minusDays(2), mode = CountMode.COUNTUP), today
        )
        assertEquals(DayCountResult.CountUp(3), r)
    }

    /**
     * ⭐ 关键回归：倒数模式的事件过期后**不能**变成「第 N 天」。
     *
     * 如果按日期自动推断模式，过期的春节会显示成「春节第 3 天」——
     * 这是不可接受的。模式必须显式，不随日期漂移。
     */
    @Test
    fun `倒数模式过期后不变成正数`() {
        val today = LocalDate.of(2026, 9, 28)
        val past = event(today.minusDays(2), mode = CountMode.COUNTDOWN)
        val r = DayCountCalculator.calculate(past, today)
        assertEquals(DayCountResult.Past(2), r)
        // 明确断言「不是」CountUp，防止将来改坏
        assert(r !is DayCountResult.CountUp) { "倒数事件过期后不应切成正数口径" }
    }

    // ---------- 2 月 29 日 ----------

    @Test
    fun `2月29日 闰年正常`() {
        // 2028 是闰年
        val today = LocalDate.of(2028, 1, 1)
        val e = event(
            LocalDate.of(2000, 2, 29),
            repeat = RepeatType.YEARLY,
        )
        val next = DayCountCalculator.nextOccurrence(e, today)
        assertEquals(LocalDate.of(2028, 2, 29), next)
    }

    /**
     * ⭐ 平年回退：2 月 29 日的生日/纪念日在平年必须回退到 2 月 28 日。
     * 不处理会抛 DateTimeException 导致整页崩溃，且只在特定日期出现。
     */
    @Test
    fun `2月29日 平年回退到2月28日`() {
        // 2027 不是闰年
        val today = LocalDate.of(2027, 1, 1)
        val e = event(
            LocalDate.of(2000, 2, 29),
            repeat = RepeatType.YEARLY,
        )
        val next = DayCountCalculator.nextOccurrence(e, today)
        assertEquals(LocalDate.of(2027, 2, 28), next)
    }

    // ---------- 按年重复推导 ----------

    @Test
    fun `按年重复 今年还没到就取今年`() {
        val today = LocalDate.of(2026, 9, 28)
        val e = event(LocalDate.of(1995, 12, 25), repeat = RepeatType.YEARLY)
        assertEquals(LocalDate.of(2026, 12, 25), DayCountCalculator.nextOccurrence(e, today))
    }

    @Test
    fun `按年重复 今年已过就取明年`() {
        val today = LocalDate.of(2026, 9, 28)
        val e = event(LocalDate.of(1995, 3, 8), repeat = RepeatType.YEARLY)
        assertEquals(LocalDate.of(2027, 3, 8), DayCountCalculator.nextOccurrence(e, today))
    }

    @Test
    fun `按年重复 刚好是今天`() {
        val today = LocalDate.of(2026, 9, 28)
        val e = event(LocalDate.of(1995, 9, 28), repeat = RepeatType.YEARLY)
        assertEquals(today, DayCountCalculator.nextOccurrence(e, today))
    }

    @Test
    fun `非重复事件 不推导`() {
        val today = LocalDate.of(2026, 9, 28)
        val origin = LocalDate.of(2020, 1, 1)
        val e = event(origin, repeat = RepeatType.NONE)
        assertEquals(origin, DayCountCalculator.nextOccurrence(e, today))
    }

    // ---------- 默认模式 ----------

    @Test
    fun `默认模式 未来的日子用倒数`() {
        assertEquals(CountMode.COUNTDOWN, CountMode.defaultFor(5))
        assertEquals(CountMode.COUNTDOWN, CountMode.defaultFor(0))
    }

    @Test
    fun `默认模式 过去的日子用正数`() {
        assertEquals(CountMode.COUNTUP, CountMode.defaultFor(-1))
    }
}
