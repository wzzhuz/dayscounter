package com.wzzhuz.dayscounter.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 分类表。
 *
 * **分类与标签是两套独立体系**：
 * - 分类：单值，粗粒度归档（纪念日 / 工作 / 生活 + 自定义）
 * - 标签：多值，交叉检索
 *
 * 用独立表而不是把分类名直接塞进 events 表，
 * 是为了能在选择列表里列出「历史用过的分类」，
 * 且改名时只需改一行（字符串直存则要 UPDATE 全表）。
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,

    /** 排序序号。三个默认分类固定为 0/1/2，用户自定义从 100 起 */
    val sortOrder: Int = 100,

    /** 默认分类不可删除 */
    val builtIn: Boolean = false,
)
