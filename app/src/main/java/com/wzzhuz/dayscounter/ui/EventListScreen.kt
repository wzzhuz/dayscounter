package com.wzzhuz.dayscounter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.wzzhuz.dayscounter.data.EventRepository
import com.wzzhuz.dayscounter.data.EventRow
import com.wzzhuz.dayscounter.domain.DayCountResult

/**
 * 事件列表页。
 *
 * 数据来自 Room 的 Flow，变更自动刷新。
 * **排序由 Repository 完成**，此处只做渲染 ——
 * items{} 内禁止出现 indexOfFirst / find / sortedBy（O(n²) 陷阱）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListScreen(
    repository: EventRepository,
    onAddClick: () -> Unit,
    onEventClick: (EventRow) -> Unit,
    onSearchClick: () -> Unit = {},
) {
    val rows by repository.observeRows().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("倒数日") },
                actions = {
                    TextButton(onClick = onSearchClick) { Text("搜索") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "新增事件")
            }
        }
    ) { padding ->
        if (rows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Text(
                    text = "暂无事件。点击右下角 + 添加第一个重要日子。",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { it.event.id }) { row ->
                    EventRowCard(
                        row = row,
                        onClick = { onEventClick(row) },
                    )
                }
            }
        }
    }

}

/**
 * 单条事件卡片。
 *
 * **所有数据都在 [row] 里预先算好**，此处不做任何计算：
 * 在 items{} 内出现 indexOfFirst / find / sortedBy 会导致 O(n²)，
 * 5000 条时是 2500 万次比较，直接卡死 UI（lifelog 真实踩过）。
 */
@Composable
fun EventRowCard(
    row: EventRow,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (row.event.pinned) {
                        Text(
                            "置顶 · ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = row.event.title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    text = buildSubtitle(row),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = row.result.displayText(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun buildSubtitle(row: EventRow): String {
    val datePart = row.nextOccurrence.toString()
    val lunar = if (row.event.calendarType == com.wzzhuz.dayscounter.domain.CalendarType.LUNAR) " · 农历" else ""
    val repeat = if (row.event.repeatType == com.wzzhuz.dayscounter.domain.RepeatType.YEARLY) " · 每年" else ""
    return datePart + lunar + repeat
}

/** 天数的显示文案。 sealed class 保证 UI 不会漏处理某种情况 */
private fun DayCountResult.displayText(): String = when (this) {
    is DayCountResult.Future -> "还剩${days}天"
    DayCountResult.Today -> "就是今天"
    is DayCountResult.Past -> "已过${days}天"
    is DayCountResult.CountUp -> "第${days}天"
}
