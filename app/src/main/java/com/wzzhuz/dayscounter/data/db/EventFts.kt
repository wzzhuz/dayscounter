package com.wzzhuz.dayscounter.data.db

import androidx.room.Entity
import androidx.room.Fts4

/**
 * 全文搜索虚拟表（FTS4），覆盖标题与备注。
 *
 * ⚠️ 两个必须遵守的约束：
 *
 * 1. **FTS 实体不能带 @PrimaryKey** —— FTS 表用 rowid，
 *    声明主键会导致 Room 编译失败。
 * 2. **Room 不会自动生成同步触发器** ——
 *    必须在 [AppDatabase] 的 onCreate 里手动建 INSERT / DELETE / UPDATE 三条，
 *    否则症状是「搜索永远返回空，且不报任何错」。
 */
@Entity(tableName = "events_fts")
@Fts4(contentEntity = EventEntity::class)
data class EventFts(
    val title: String,
    val note: String,
)
