package com.wzzhuz.dayscounter.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 固定调色板。
 *
 * **为什么不用 Material You 动态取色（Android 12+ 取壁纸色）**：
 * 取色结果不可控，用户壁纸若为深色会破坏文字对比度。
 * 固定调色板在任何设备上观感一致——**好看的前提是能看清**。
 *
 * 每组是「低饱和背景起始色 → 稍深结束色」的渐变：
 * 低饱和保证卡片里的数字不会糊掉，深色文字始终可读。
 */
data class GradientPair(
    val start: Color,
    val end: Color,
    /** 卡片上的强调色（天数数字用），比背景深以保证对比度 */
    val accent: Color,
    val name: String,
)

object ColorPalette {

    /** 纪念日：粉 */
    private val ANNIVERSARY = GradientPair(
        start = Color(0xFFFCE4EC),
        end = Color(0xFFF8BBD0),
        accent = Color(0xFFC2185B),
        name = "粉",
    )

    /** 工作：蓝 */
    private val WORK = GradientPair(
        start = Color(0xFFE3F2FD),
        end = Color(0xFF90CAF9),
        accent = Color(0xFF1565C0),
        name = "蓝",
    )

    /** 生活：绿 */
    private val LIFE = GradientPair(
        start = Color(0xFFE8F5E9),
        end = Color(0xFFA5D6A7),
        accent = Color(0xFF2E7D32),
        name = "绿",
    )

    /** 节日：橙 */
    private val FESTIVAL = GradientPair(
        start = Color(0xFFFFF3E0),
        end = Color(0xFFFFCC80),
        accent = Color(0xFFEF6C00),
        name = "橙",
    )

    /** 其他候选色，供自定义分类轮转 */
    private val EXTRAS = listOf(
        GradientPair(Color(0xFFF3E5F5), Color(0xFFCE93D8), Color(0xFF6A1B9A), "紫"),
        GradientPair(Color(0xFFE0F7FA), Color(0xFF80DEEA), Color(0xFF00838F), "青"),
        GradientPair(Color(0xFFFFFDE7), Color(0xFFFFF59D), Color(0xFFF9A825), "黄"),
        GradientPair(Color(0xFFEDE7F6), Color(0xFFB39DDB), Color(0xFF4527A0), "靛"),
        GradientPair(Color(0xFFFBE9E7), Color(0xFFFFAB91), Color(0xFFD84315), "珊瑚"),
        GradientPair(Color(0xFFE8EAF6), Color(0xFF9FA8DA), Color(0xFF283593), "蓝紫"),
    )

    /** 三个默认分类 + 节日的固定配色，按分类名匹配 */
    private val BUILT_IN: Map<String, GradientPair> = mapOf(
        "纪念日" to ANNIVERSARY,
        "工作" to WORK,
        "生活" to LIFE,
        "节日" to FESTIVAL,
    )

    /** 轮转池：自定义分类从这里取 */
    private val ROTATION: List<GradientPair> = EXTRAS

    /**
     * 按分类名取配色。
     *
     * **同名分类永远得到同一颜色**：用 `hashCode` 而非随机，
     * 否则重启 App 后颜色会变，让用户以为 App 不稳定。
     *
     * 内置分类用固定配色；自定义分类从轮转池按哈希取。
     */
    fun forCategory(name: String): GradientPair {
        BUILT_IN[name]?.let { return it }
        val idx = (name.hashCode() and Int.MAX_VALUE) % ROTATION.size
        return ROTATION[idx]
    }

    /** 未分类事件的兜底色 */
    val UNCATEGORIZED = GradientPair(
        start = Color(0xFFF5F5F5),
        end = Color(0xFFE0E0E0),
        accent = Color(0xFF424242),
        name = "灰",
    )

    /**
     * 供编辑页「自定义颜色」选择的候选列表。
     *
     * ⚠️ **顺序即契约**：事件把选中的**索引**存进 `colorArgb` 列，
     * 重排本列表会让已有事件的配色错乱。**只能追加，不能重排或插入**。
     *
     * 只暴露这几组而不开放任意取色器：
     * 任意取色可能选出高饱和背景，把天数数字糊掉——
     * 违反「好看的前提是能看清」。
     */
    val PICKABLE: List<GradientPair> = listOf(
        ANNIVERSARY, WORK, LIFE, FESTIVAL
    ) + EXTRAS

    /**
     * 按索引取配色，供事件自定义色反查。
     * 索引越界（如调色板调整后）时返回 null，调用方回退到分类色。
     */
    fun byIndex(index: Int?): GradientPair? =
        if (index == null || index < 0 || index >= PICKABLE.size) null else PICKABLE[index]

    /** 归档事件的降饱和色：视觉上明确「这不是活跃数据」 */
    val ARCHIVED = GradientPair(
        start = Color(0xFFEFEFEF),
        end = Color(0xFFDCDCDC),
        accent = Color(0xFF757575),
        name = "灰",
    )
}
