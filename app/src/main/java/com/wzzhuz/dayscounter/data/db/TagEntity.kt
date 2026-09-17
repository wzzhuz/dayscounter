package com.wzzhuz.dayscounter.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 标签表。与事件是多对多关系 */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorArgb: Int? = null,
)
