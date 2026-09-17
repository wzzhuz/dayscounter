package com.wzzhuz.dayscounter.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 事件 ↔ 标签 的多对多关联表。
 *
 * **外键必须 CASCADE**：否则删事件后留下孤儿行，
 * 未来做「按标签统计/筛选」时数字会莫名其妙多出几条——极难定位。
 */
@Entity(
    tableName = "event_tag_cross_ref",
    primaryKeys = ["eventId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")]
)
data class EventTagCrossRef(
    val eventId: String,
    val tagId: String,
)
