package com.wzzhuz.dayscounter.domain

import java.time.LocalDate

/**
 * 事件的领域模型。
 *
 * **只存事实，不存推导值** —— 剩余天数与下一次发生日均不在此模型中，
 * 一律由 [DayCountCalculator] 在读取时推导。
 *
 * @property originDate 原始目标日。重复事件存「第一次」的日期，
 *                      不存「今年那次」——否则跨年不打开 App 时数据是脏的。
 * @property countMode  计数模式，决定「今天算 0 还是 1」
 */
data class Event(
    val id: String,
    val title: String,
    val originDate: LocalDate,
    val calendarType: CalendarType = CalendarType.GREGORIAN,

    // 农历字段，公历事件为 null
    val lunarMonth: Int? = null,
    val lunarDay: Int? = null,
    val lunarLeapMonth: Boolean = false,

    val countMode: CountMode = CountMode.COUNTDOWN,
    val repeatType: RepeatType = RepeatType.NONE,

    val categoryId: String? = null,
    val pinned: Boolean = false,
    val note: String = "",

    /** 提前几天提醒；null = 不提醒。供 add-event-reminder change 使用 */
    val reminderDaysBefore: Int? = null,

    val tags: List<Tag> = emptyList(),

    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class Tag(
    val id: String,
    val name: String,
    val colorArgb: Int? = null,
)
