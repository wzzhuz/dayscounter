package com.wzzhuz.dayscounter.data.db

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/** 事件 + 其关联的标签列表 */
data class EventWithTags(
    @Embedded val event: EventEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = EventTagCrossRef::class,
            parentColumn = "eventId",
            entityColumn = "tagId",
        )
    )
    val tags: List<TagEntity>,
)
