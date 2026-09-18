package com.wzzhuz.dayscounter.data

import com.wzzhuz.dayscounter.data.db.EventDao
import com.wzzhuz.dayscounter.data.db.EventEntity
import com.wzzhuz.dayscounter.data.db.EventWithTags
import com.wzzhuz.dayscounter.data.db.TagEntity
import com.wzzhuz.dayscounter.domain.CalendarType
import com.wzzhuz.dayscounter.domain.CountMode
import com.wzzhuz.dayscounter.domain.DayCountResult
import com.wzzhuz.dayscounter.domain.Event
import com.wzzhuz.dayscounter.domain.RepeatType
import com.wzzhuz.dayscounter.domain.Tag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

/**
 * 事件仓储。负责 Entity ↔ 领域模型映射，以及**排序缓存**。
 *
 * 排序不写进 SQL：按 `ABS(julianday(next) - julianday('now'))` 排序要求
 * nextOccurrence 落库，而重复事件的下一次发生日每天都在变——
 * 落库就回到「每天全表 UPDATE」的老问题。
 */
class EventRepository(private val dao: EventDao) {

    /**
     * 观察全部事件的显示行。
     *
     * **排序在 map 里做一次，结果直接给 UI**，
     * UI 层禁止在 items{} 内再排序 / 查找 / 过滤（O(n²) 陷阱）。
     */
    fun observeRows(today: LocalDate = LocalDate.now()): Flow<List<EventRow>> =
        dao.observeAll().map { list ->
            list.map { it.toRow(today) }
                .sortedWith(compareBy({ !it.event.pinned }, { it.sortKey() }))
        }

    fun observeByTag(tagId: String, today: LocalDate = LocalDate.now()): Flow<List<EventRow>> =
        dao.observeByTag(tagId).map { list ->
            list.map { it.toRow(today) }
                .sortedWith(compareBy({ !it.event.pinned }, { it.sortKey() }))
        }

    fun search(query: String, today: LocalDate = LocalDate.now()): Flow<List<EventRow>> =
        dao.search(query).map { list ->
            list.map { it.toRow(today) }
                .sortedWith(compareBy({ !it.event.pinned }, { it.sortKey() }))
        }

    fun observeTags(): Flow<List<Tag>> =
        dao.observeTags().map { list -> list.map { Tag(it.id, it.name, it.colorArgb) } }

    fun observeCategories(): Flow<List<Category>> =
        dao.observeCategories().map { list -> list.map { Category(it.id, it.name, it.builtIn) } }

    /**
     * 按名字取分类，不存在则创建。
     *
     * 同名直接复用而非新建，否则「家人」会被创建成两个不同 id，
     * 按分类筛选时看起来像漏了事件。
     */
    suspend fun findOrCreateCategory(name: String): String {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "分类名不能为空" }
        dao.findCategoryByName(trimmed)?.let { return it.id }
        val id = "cat_${java.util.UUID.randomUUID()}"
        dao.insertCategory(
            com.wzzhuz.dayscounter.data.db.CategoryEntity(
                id = id, name = trimmed, sortOrder = 100, builtIn = false
            )
        )
        return id
    }

    /**
     * 删除分类。**必须先把引用它的事件解绑**，
     * 否则那些事件的 categoryId 指向已删除的分类，变成脏引用。
     */
    fun deleteCategoryAsync(id: String) {
        CoroutineScope(Dispatchers.IO).launch {
            dao.clearCategoryOfEvents(id)
            dao.deleteCategory(id)
        }
    }

    suspend fun getById(id: String): Event? = dao.getById(id)?.toDomain()

    suspend fun save(event: Event) {
        dao.insertEvent(event.toEntity())
        dao.replaceTags(event.id, event.tags.map { it.id })
    }

    suspend fun insertAll(events: List<Event>) {
        dao.insertEvents(events.map { it.toEntity() })
        events.forEach { dao.replaceTags(it.id, it.tags.map { t -> t.id }) }
    }

    suspend fun delete(id: String) = dao.deleteEvent(id)

    /**
     * 同步删除（供 UI 层回调直接调用）。
     * 内部切到 IO 线程执行，调用方无需自己开协程。
     */
    fun deleteAsync(id: String) {
        CoroutineScope(Dispatchers.IO).launch {
            dao.deleteEvent(id)
        }
    }

    /** 同步保存（供 UI 层回调直接调用），保存完成后通过 onDone 回调通知 */
    fun saveAsync(
        event: com.wzzhuz.dayscounter.domain.Event,
        onDone: () -> Unit = {},
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            save(event)
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    suspend fun deleteAll() = dao.deleteAllEvents()

    suspend fun createTag(name: String, colorArgb: Int? = null): Tag {
        val tag = Tag(UUID.randomUUID().toString(), name, colorArgb)
        dao.insertTag(TagEntity(tag.id, tag.name, tag.colorArgb))
        return tag
    }

    suspend fun deleteTag(id: String) = dao.deleteTag(id)

    /** 同步创建标签（供 UI 回调直接调用）。创建后 tags Flow 自动刷新 */
    fun createTagAsync(name: String) {
        CoroutineScope(Dispatchers.IO).launch { createTag(name.trim()) }
    }

    /** 同步版 findOrCreateCategory（供 UI 回调直接调用） */
    fun findOrCreateCategoryAsync(name: String) {
        CoroutineScope(Dispatchers.IO).launch { findOrCreateCategory(name) }
    }

    fun newId(): String = UUID.randomUUID().toString()
}

/** 列表的一行：领域模型 + 已算好的天数 + 已推导的下一次发生日 */
data class EventRow(
    val event: Event,
    val result: DayCountResult,
    val nextOccurrence: LocalDate,
) {
    /** 排序键：置顶组内 / 非置顶组内，均按 |天数| 升序 */
    fun sortKey(): Int = when (result) {
        is DayCountResult.Future -> result.days
        is DayCountResult.Past -> result.days
        is DayCountResult.CountUp -> 0
        DayCountResult.Today -> 0
    }
}

// ---------- 映射 ----------

private fun EventWithTags.toDomain(): Event = event.toDomain(tags)

private fun EventEntity.toDomain(tags: List<TagEntity> = emptyList()): Event = Event(
    id = id,
    title = title,
    originDate = LocalDate.ofEpochDay(originEpochDay),
    calendarType = CalendarType.valueOf(calendarType),
    lunarMonth = lunarMonth,
    lunarDay = lunarDay,
    lunarLeapMonth = lunarLeapMonth,
    countMode = CountMode.valueOf(countMode),
    repeatType = RepeatType.valueOf(repeatType),
    categoryId = categoryId,
    pinned = pinned,
    note = note,
    reminderDaysBefore = reminderDaysBefore,
    tags = tags.map { Tag(it.id, it.name, it.colorArgb) },
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun Event.toEntity(): EventEntity = EventEntity(
    id = id,
    title = title,
    originEpochDay = originDate.toEpochDay(),
    calendarType = calendarType.name,
    lunarMonth = lunarMonth,
    lunarDay = lunarDay,
    lunarLeapMonth = lunarLeapMonth,
    countMode = countMode.name,
    repeatType = repeatType.name,
    categoryId = categoryId,
    pinned = pinned,
    note = note,
    reminderDaysBefore = reminderDaysBefore,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun EventWithTags.toRow(today: LocalDate): EventRow {
    val domain = toDomain()
    val next = com.wzzhuz.dayscounter.domain.DayCountCalculator.nextOccurrence(domain, today)
    return EventRow(
        event = domain,
        result = com.wzzhuz.dayscounter.domain.DayCountCalculator.calculate(domain, today),
        nextOccurrence = next,
    )
}

internal fun Event.toRow(today: LocalDate = LocalDate.now()): EventRow {
    val next = com.wzzhuz.dayscounter.domain.DayCountCalculator.nextOccurrence(this, today)
    return EventRow(
        event = this,
        result = com.wzzhuz.dayscounter.domain.DayCountCalculator.calculate(this, today),
        nextOccurrence = next,
    )
}
