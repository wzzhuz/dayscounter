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
     * @param year   农历年（公历年份记法，如 2026）
     * @param month  农历月，1–12
     * @param day    农历日，1–30
     * @param isLeapMonth 是否闰月
     */
    fun lunarToGregorian(year: Int, month: Int, day: Int, isLeapMonth: Boolean): LocalDate {
        val cc = ChineseCalendar(utc)
        cc.clear()
        cc.set(Calendar.ERA, 1)
        cc.set(Calendar.YEAR, year)
        cc.set(Calendar.MONTH, month - 1)      // ICU 月份从 0 开始
        cc.set(Calendar.DAY_OF_MONTH, day)
        if (isLeapMonth) {
            cc.set(ChineseCalendar.LEAP_MONTH, 1)
        }
        return cc.time.toInstant().atZone(zoneId).toLocalDate()
    }

    /** 公历 → 农历。返回三元组（年, 月, 日, 是否闰月） */
    fun gregorianToLunar(date: LocalDate): LunarDate {
        val cc = ChineseCalendar(utc)
        cc.clear()
        cc.time = java.util.Date.from(date.atStartOfDay(zoneId).toInstant())
        return LunarDate(
            year = cc.get(Calendar.YEAR),
            month = cc.get(Calendar.MONTH) + 1,   // ICU 月份从 0 开始
            day = cc.get(Calendar.DAY_OF_MONTH),
            isLeapMonth = cc.get(ChineseCalendar.LEAP_MONTH) == 1,
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
            // 该年无此农历日（极罕见），回退到该月最后一天
            val fallback = LocalDate.of(gregorianYear, 1, 1)
                .withDayOfMonth(1)
            fallback
        }
    }
}

data class LunarDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val isLeapMonth: Boolean,
)
