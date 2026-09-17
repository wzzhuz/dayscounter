package com.wzzhuz.dayscounter.domain

import android.icu.util.Calendar
import android.icu.util.ChineseCalendar
import android.icu.util.TimeZone
import java.time.LocalDate
import java.time.ZoneId

/**
 * 农历 ↔ 公历转换。
 *
 * 使用 `android.icu.util.ChineseCalendar`：
 * - **零依赖**，Android 系统内置（API 24+）
 * - 覆盖 1900–2100 年
 * - 结果以 ICU 为准，**不做自行修正**——自行修正等于维护一张可能抄错的补丁表
 *
 * 不通过网络查询：本 App 不申请任何网络权限。
 */
object LunarCalendar {

    private val utc = TimeZone.getTimeZone("UTC")
    private val zoneId: ZoneId = ZoneId.of("UTC")

    /**
     * 农历 → 公历。
     *
     * ⚠️ 两个必须注意的点：
     * 1. 年份用 `EXTENDED_YEAR`（= 公历年份），**不能用 ERA + YEAR**。
     *    ChineseCalendar 的 ERA + YEAR 表示「60 年周期内的第几年」（1–60），
     *    用它设置 2026 会得到完全错误的日期，且不报错。
     * 2. 闰月字段是 `Calendar.IS_LEAP_MONTH`（定义在基类 Calendar 上），
     *    **不是** `ChineseCalendar.LEAP_MONTH`（不存在，会编译失败）。
     *
     * @param year   农历年（公历年份记法，如 2026）
     * @param month  农历月，1–12
     * @param day    农历日，1–30
     * @param isLeapMonth 是否闰月
     */
    fun lunarToGregorian(year: Int, month: Int, day: Int, isLeapMonth: Boolean = false): LocalDate {
        val cc = ChineseCalendar(utc)
        cc.clear()
        cc.set(Calendar.EXTENDED_YEAR, year)
        cc.set(Calendar.MONTH, month - 1)      // ICU 月份从 0 开始
        cc.set(Calendar.DAY_OF_MONTH, day)
        cc.set(Calendar.IS_LEAP_MONTH, if (isLeapMonth) 1 else 0)
        return LocalDate.ofEpochDay(cc.timeInMillis / 86_400_000L)
    }

    /** 公历 → 农历 */
    fun gregorianToLunar(date: LocalDate): LunarDate {
        val cc = ChineseCalendar(utc)
        cc.clear()
        cc.timeInMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        return LunarDate(
            year = cc.get(Calendar.EXTENDED_YEAR),
            month = cc.get(Calendar.MONTH) + 1,   // ICU 月份从 0 开始
            day = cc.get(Calendar.DAY_OF_MONTH),
            isLeapMonth = cc.get(Calendar.IS_LEAP_MONTH) == 1,
        )
    }

    /**
     * 给定农历月日，求其在指定公历年份对应的公历日期。
     * 用于农历事件的按年重复推导——**每年重新转换，不做 +365 天近似**。
     */
    fun lunarDateInYear(lunarMonth: Int, lunarDay: Int, gregorianYear: Int): LocalDate {
        return try {
            lunarToGregorian(gregorianYear, lunarMonth, lunarDay, isLeapMonth = false)
        } catch (e: IllegalArgumentException) {
            // 该年无此农历日（极罕见），回退到该年 12 月 31 日，保证推导不中断
            LocalDate.of(gregorianYear, 12, 31)
        }
    }
}

data class LunarDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val isLeapMonth: Boolean,
)
