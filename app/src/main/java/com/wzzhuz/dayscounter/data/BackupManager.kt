package com.wzzhuz.dayscounter.data

import androidx.room.withTransaction
import com.wzzhuz.dayscounter.data.db.AppDatabase
import com.wzzhuz.dayscounter.data.db.EventEntity
import com.wzzhuz.dayscounter.data.db.EventTagCrossRef
import com.wzzhuz.dayscounter.data.db.TagEntity
import com.wzzhuz.dayscounter.domain.CalendarType
import com.wzzhuz.dayscounter.domain.CountMode
import com.wzzhuz.dayscounter.domain.Event
import com.wzzhuz.dayscounter.domain.RepeatType
import com.wzzhuz.dayscounter.domain.Tag
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * 导出 / 导入。
 *
 * Room 是存储格式，**JSON 降级为交换格式**：
 * 内部享受 Room 的查询能力，对外仍是那个人能看懂、能 cat 的文件。
 */
class BackupManager(private val db: AppDatabase) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = false }

    suspend fun export(): String {
        val events = db.eventDao().exportAll()
        val tags = db.eventDao().exportTags()
        val refs = db.eventDao().exportRefs()
        return json.encodeToString(
            BackupFile(
                version = 1,
                exportedAt = System.currentTimeMillis(),
                tags = tags.map { TagDto(it.id, it.name, it.colorArgb) },
                events = events.map { e ->
                    EventDto(
                        id = e.id,
                        title = e.title,
                        targetDate = LocalDate.ofEpochDay(e.originEpochDay).toString(),
                        calendarType = e.calendarType,
                        lunarMonth = e.lunarMonth,
                        lunarDay = e.lunarDay,
                        lunarLeapMonth = e.lunarLeapMonth,
                        countMode = e.countMode,
                        repeatType = e.repeatType,
                        categoryId = e.categoryId,
                        pinned = e.pinned,
                        note = e.note,
                        reminderDaysBefore = e.reminderDaysBefore,
                        tagIds = refs.filter { it.eventId == e.id }.map { it.tagId },
                        createdAt = e.createdAt,
                        updatedAt = e.updatedAt,
                    )
                },
            )
        )
    }

    /**
     * 导入。**必须在单事务内完成，失败整体回滚。**
     *
     * 「部分成功」是最糟的结果：用户会以为导入完成，实际少了一半数据。
     */
    suspend fun import(content: String): Int {
        val backup = json.decodeFromString<BackupFile>(content)

        // 先整体解析成功，才开始写库——避免解析到一半报错导致部分写入
        val events = backup.events.map { it.toEntity() }
        val tags = backup.tags.map { TagEntity(it.id, it.name, it.colorArgb) }
        val refs = backup.events.flatMap { e ->
            e.tagIds.map { EventTagCrossRef(e.id, it) }
        }

        db.withTransaction {
            db.eventDao().deleteAllEvents()
            tags.forEach { db.eventDao().insertTag(it) }
            db.eventDao().insertEvents(events)
            db.eventDao().insertCrossRefs(refs)
        }
        return events.size
    }
}

@Serializable
data class BackupFile(
    val version: Int,
    val exportedAt: Long,
    val tags: List<TagDto> = emptyList(),
    val events: List<EventDto>,
)

@Serializable
data class TagDto(val id: String, val name: String, val colorArgb: Int? = null)

@Serializable
data class EventDto(
    val id: String,
    val title: String,
    val targetDate: String,
    val calendarType: String = CalendarType.GREGORIAN.name,
    val lunarMonth: Int? = null,
    val lunarDay: Int? = null,
    val lunarLeapMonth: Boolean = false,
    val countMode: String = CountMode.COUNTDOWN.name,
    val repeatType: String = RepeatType.NONE.name,
    val categoryId: String? = null,
    val pinned: Boolean = false,
    val note: String = "",
    val reminderDaysBefore: Int? = null,
    val tagIds: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

private fun EventDto.toEntity(): EventEntity = EventEntity(
    id = id,
    title = title,
    originEpochDay = LocalDate.parse(targetDate).toEpochDay(),
    calendarType = calendarType,
    lunarMonth = lunarMonth,
    lunarDay = lunarDay,
    lunarLeapMonth = lunarLeapMonth,
    countMode = countMode,
    repeatType = repeatType,
    categoryId = categoryId,
    pinned = pinned,
    note = note,
    reminderDaysBefore = reminderDaysBefore,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
