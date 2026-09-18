package com.wzzhuz.dayscounter.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    // ---------- 读 ----------

    /** 返回 Flow：Room 自动在后台线程执行，数据变更时自动刷新 UI */
    @Transaction
    @Query("SELECT * FROM events ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<EventWithTags>>

    @Transaction
    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: String): EventWithTags?

    /** 按标签筛选 */
    @Transaction
    @Query(
        """
        SELECT * FROM events
        INNER JOIN event_tag_cross_ref ON events.id = event_tag_cross_ref.eventId
        WHERE event_tag_cross_ref.tagId = :tagId
        ORDER BY pinned DESC, updatedAt DESC
        """
    )
    fun observeByTag(tagId: String): Flow<List<EventWithTags>>

    /** 全文搜索：FTS4 匹配标题与备注 */
    @Transaction
    @Query(
        """
        SELECT events.* FROM events
        INNER JOIN events_fts ON events.rowid = events_fts.rowid
        WHERE events_fts MATCH :query
        ORDER BY pinned DESC, updatedAt DESC
        """
    )
    fun search(query: String): Flow<List<EventWithTags>>

    /**
     * 查询未来 N 天内到期的事件。
     * 供 add-event-reminder change 使用。
     * 注意：nextOccurrence 不落库，故此处按 originEpochDay 粗筛，
     * 精确判断由领域层 [com.wzzhuz.dayscounter.domain.DayCountCalculator] 完成。
     */
    @Transaction
    @Query("SELECT * FROM events WHERE reminderDaysBefore IS NOT NULL")
    suspend fun getRemindableEvents(): List<EventWithTags>

    // ---------- 写 ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<EventEntity>)

    @Update
    suspend fun updateEvent(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteEvent(id: String)

    @Query("DELETE FROM events")
    suspend fun deleteAllEvents()

    // ---------- 标签 ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(refs: List<EventTagCrossRef>)

    /** 替换某事件的全部标签关联 */
    @Transaction
    suspend fun replaceTags(eventId: String, tagIds: List<String>) {
        deleteCrossRefsOf(eventId)
        insertCrossRefs(tagIds.map { EventTagCrossRef(eventId, it) })
    }

    @Query("DELETE FROM event_tag_cross_ref WHERE eventId = :eventId")
    suspend fun deleteCrossRefsOf(eventId: String)

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteTag(id: String)

    // ---------- 分类 ----------

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    /** 一次性查询（Flow 无法在 suspend 里直接取值） */
    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun findCategoryByName(name: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun findCategoryById(id: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: String)

    @Query("UPDATE events SET categoryId = NULL WHERE categoryId = :id")
    suspend fun clearCategoryOfEvents(id: String)

    // ---------- 导出用（BackupManager） ----------

    @Query("SELECT * FROM events ORDER BY createdAt ASC")
    suspend fun exportAll(): List<EventEntity>

    @Query("SELECT * FROM tags ORDER BY name ASC")
    suspend fun exportTags(): List<TagEntity>

    @Query("SELECT * FROM event_tag_cross_ref")
    suspend fun exportRefs(): List<EventTagCrossRef>
}
