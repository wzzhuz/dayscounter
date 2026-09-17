package com.wzzhuz.dayscounter.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 事件表。
 *
 * **只存事实，不存推导值**：
 * - 不存 daysUntil —— 每天零点全部失效，需全表 UPDATE
 * - 不存 nextOccurrence —— 重复事件每年变化，落库即需每天全表刷新
 *
 * @property originEpochDay 原始目标日（LocalDate.toEpochDay）。
 *   重复事件存「第一次」的日期，**不存「今年那次」**——
 *   否则跨年不打开 App 时数据是脏的。
 * @property countMode COUNTDOWN / COUNTUP，决定「今天算 0 还是 1」
 * @property reminderDaysBefore 预留字段，供 add-event-reminder change 使用
 */
@Entity(
    tableName = "events",
    indices = [
        Index("pinned"),
        Index("categoryId"),
    ]
)
data class EventEntity(
    @PrimaryKey val id: String,

    val title: String,

    val originEpochDay: Long,

    /** GREGORIAN / LUNAR */
    val calendarType: String,

    // 农历字段，公历事件为 null
    val lunarMonth: Int? = null,
    val lunarDay: Int? = null,
    val lunarLeapMonth: Boolean = false,

    /** COUNTDOWN / COUNTUP */
    val countMode: String,

    /** NONE / YEARLY */
    val repeatType: String,

    val categoryId: String? = null,
    val pinned: Boolean = false,
    val note: String = "",

    /** 提前几天提醒；null = 不提醒。供 add-event-reminder change 使用 */
    val reminderDaysBefore: Int? = null,

    val createdAt: Long,
    val updatedAt: Long,
)
