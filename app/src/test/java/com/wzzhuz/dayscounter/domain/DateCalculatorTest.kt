package com.wzzhuz.dayscounter.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 日期计算器的单元测试。
 *
 * 与 [DayCountCalculatorTest] 一样是纯 java.time 的 JVM 测试，
 * 不需要 Android 环境。
 */
class DateCalculatorTest {

    // ---------- 间隔 ----------

    @Test
    fun `2026-01-01 到 2026-12-31 是 364 天`() {
        assertEquals(
            364L,
            DateCalculator.daysBetween(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
            )
        )
    }

    @Test
    fun `同一天是 0 天`() {
        val d = LocalDate.of(2026, 9, 28)
        assertEquals(0L, DateCalculator.daysBetween(d, d))
    }

    /**
     * 逆序输入取绝对值。
     * 间隔是距离，没有方向；显示负数会让用户困惑。
     */
    @Test
    fun `逆序输入取绝对值不为负`() {
        val r = DateCalculator.daysBetween(
            LocalDate.of(2026, 12, 31),
            LocalDate.of(2026, 1, 1)
        )
        assertEquals(364L, r)
        assert(r >= 0) { "间隔不得为负" }
    }

    @Test
    fun `年月日分解 2026-01-01 到 2028-03-15`() {
        val p = DateCalculator.periodBetween(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2028, 3, 15)
        )
        assertEquals(2, p.years)
        assertEquals(2, p.months)
        assertEquals(14, p.days)
        assertEquals(804L, DateCalculator.daysBetween(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2028, 3, 15)
        ))
    }

    // ---------- 推算 ----------

    @Test
    fun `2026-09-28 往后 100 天`() {
        assertEquals(
            LocalDate.of(2027, 1, 6),
            DateCalculator.addDays(LocalDate.of(2026, 9, 28), 100)
        )
    }

    @Test
    fun `2026-09-28 往前 100 天`() {
        assertEquals(
            LocalDate.of(2026, 6, 20),
            DateCalculator.addDays(LocalDate.of(2026, 9, 28), -100)
        )
    }

    /** 跨闰年：2024 是闰年 */
    @Test
    fun `2024-02-28 往后 1 天是 02-29`() {
        assertEquals(
            LocalDate.of(2024, 2, 29),
            DateCalculator.addDays(LocalDate.of(2024, 2, 28), 1)
        )
    }

    /** 平年：2025-02-28 往后 1 天直接到 3 月 */
    @Test
    fun `2025-02-28 往后 1 天是 03-01`() {
        assertEquals(
            LocalDate.of(2025, 3, 1),
            DateCalculator.addDays(LocalDate.of(2025, 2, 28), 1)
        )
    }

    @Test
    fun `推算 0 天是自身`() {
        val d = LocalDate.of(2026, 9, 28)
        assertEquals(d, DateCalculator.addDays(d, 0))
    }

    // ---------- 格式化 ----------

    @Test
    fun `星期名正确`() {
        // 2026-09-28 是周一
        assertEquals("周一", DateCalculator.weekdayName(LocalDate.of(2026, 9, 28)))
        assertEquals("周日", DateCalculator.weekdayName(LocalDate.of(2026, 10, 4)))
    }

    @Test
    fun `零天数的分解不显示空串`() {
        val p = DateCalculator.periodBetween(
            LocalDate.of(2026, 9, 28),
            LocalDate.of(2026, 9, 28)
        )
        assertEquals("0 天", DateCalculator.formatPeriod(p))
    }
}
