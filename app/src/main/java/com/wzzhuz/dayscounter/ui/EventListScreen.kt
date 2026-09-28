package com.wzzhuz.dayscounter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wzzhuz.dayscounter.data.Category
import com.wzzhuz.dayscounter.data.EventRepository
import com.wzzhuz.dayscounter.data.EventRow
import com.wzzhuz.dayscounter.domain.CalendarType
import com.wzzhuz.dayscounter.domain.DayCountResult
import com.wzzhuz.dayscounter.domain.RepeatType
import com.wzzhuz.dayscounter.ui.theme.ColorPalette
import com.wzzhuz.dayscounter.ui.theme.GradientPair

/**
 * 事件列表页。
 *
 * 数据来自 Room 的 Flow，变更自动刷新。
 * **排序由 Repository 完成**，此处只做渲染 ——
 * items{} 内禁止出现 indexOfFirst / find / sortedBy（O(n²) 陷阱）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventListScreen(
    repository: EventRepository,
    categories: List<Category>,
    onAddClick: () -> Unit,
    onEventClick: (EventRow) -> Unit,
    onOpenArchive: () -> Unit,
) {
    val tags by repository.observeTags().collectAsState(initial = emptyList())
    var selectedTagId by remember { mutableStateOf<String?>(null) }

    val rows by remember(selectedTagId) {
        if (selectedTagId == null) {
            repository.observeRows()
        } else {
            repository.observeByTag(selectedTagId!!)
        }
    }.collectAsState(initial = emptyList())

    // categoryId → Category，避免在 items{} 里做查找（O(n²)）
    val categoryById = remember(categories) { categories.associateBy { it.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("倒数日") },
                actions = {
                    Text("归档", modifier = Modifier
                        .clickable(onClick = onOpenArchive)
                        .padding(horizontal = 12.dp))
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "新增事件")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedTagId == null,
                        onClick = { selectedTagId = null },
                        label = { Text("全部") },
                    )
                    tags.forEach { tag ->
                        FilterChip(
                            selected = selectedTagId == tag.id,
                            onClick = {
                                selectedTagId = if (selectedTagId == tag.id) null else tag.id
                            },
                            label = { Text(tag.name) },
                        )
                    }
                }
            }

            if (rows.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = if (selectedTagId == null) {
                            "暂无事件。点击右下角 + 添加，\n或到「事件库」一键添加节日。"
                        } else {
                            "该标签下没有事件。"
                        },
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(rows, key = { it.event.id }) { row ->
                        EventRowCard(
                            row = row,
                            category = row.event.categoryId?.let { categoryById[it] },
                            onClick = { onEventClick(row) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单条事件卡片。
 *
 * **渐变背景 + 天数放大**：倒数日 App 的核心信息就是天数，
 * 标题抢了天数的视觉权重本末倒置（spec: 排版层级）。
 *
 * **所有数据都在 [row] 里预先算好**，此处不做任何计算：
 * 在 items{} 内出现 indexOfFirst / find / sortedBy 会导致 O(n²)，
 * 5000 条时是 2500 万次比较，直接卡死 UI（lifelog 真实踩过）。
 */
@Composable
fun EventRowCard(
    row: EventRow,
    category: Category?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val gradient = resolveGradient(category, row.event.colorArgb)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(gradient.start, gradient.end)))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // 分类色块：列表页一眼看出分类归属
                if (category != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = gradient.accent.copy(alpha = 0.15f),
                    ) {
                        Text(
                            category.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = gradient.accent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (row.event.pinned) {
                        Text(
                            "置顶 · ",
                            style = MaterialTheme.typography.bodySmall,
                            color = gradient.accent,
                        )
                    }
                    Text(
                        text = row.event.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = androidx.compose.ui.graphics.Color(0xFF212121),
                    )
                }
                Text(
                    text = buildSubtitle(row),
                    style = MaterialTheme.typography.bodySmall,
                    color = androidx.compose.ui.graphics.Color(0xFF616161),
                )
                if (row.event.tags.isNotEmpty()) {
                    TagChips(names = row.event.tags.map { it.name })
                }
            }
            // 天数是第一视觉焦点
            Text(
                text = row.result.displayText(),
                style = MaterialTheme.typography.headlineMedium,
                color = gradient.accent,
            )
        }
    }
}

/**
 * 决定卡片配色，优先级：事件自定义色 > 分类存储色 > 按分类名分配 > 未分类兜底。
 *
 * ⚠️ 必须用单一返回类型：[GradientPair]。
 * 写成 `let { if (...) A else B } ?: C` 且分支类型不同，Kotlin 会推断成 Any，编译失败。
 */
private fun resolveGradient(category: Category?, eventColorArgb: Int?): GradientPair {
    // colorArgb 存的是 ColorPalette.PICKABLE 的**索引**，不是颜色数值
    eventColorArgb?.let { idx ->
        ColorPalette.byIndex(idx)?.let { return it }
    }
    val cat = category ?: return ColorPalette.UNCATEGORIZED
    if (cat.colorStartArgb != null && cat.colorEndArgb != null) {
        // 存储色只含渐变，accent 仍按名字取，保证文字对比度
        return GradientPair(
            Color(cat.colorStartArgb),
            Color(cat.colorEndArgb),
            ColorPalette.forCategory(cat.name).accent,
            cat.name,
        )
    }
    return ColorPalette.forCategory(cat.name)
}

private fun buildSubtitle(row: EventRow): String {
    val datePart = row.nextOccurrence.toString()
    val lunar = if (row.event.calendarType == CalendarType.LUNAR) " · 农历" else ""
    val repeat = if (row.event.repeatType == RepeatType.YEARLY) " · 每年" else ""
    return datePart + lunar + repeat
}

/** 天数的显示文案。 sealed class 保证 UI 不会漏处理某种情况 */
fun DayCountResult.displayText(): String = when (this) {
    is DayCountResult.Future -> "还剩\n${days}天"
    DayCountResult.Today -> "就是\n今天"
    is DayCountResult.Past -> "已过\n${days}天"
    is DayCountResult.CountUp -> "第\n${days}天"
}
