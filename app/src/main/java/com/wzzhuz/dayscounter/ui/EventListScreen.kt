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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.wzzhuz.dayscounter.data.EventRow
import com.wzzhuz.dayscounter.domain.DayCountResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListScreen() {
    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("倒数日") },
                actions = {
                    IconButton(onClick = { /* TODO: 搜索入口，change 内实现 */ }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* TODO: 新增事件 */ }) {
                Icon(Icons.Default.Add, contentDescription = "新增事件")
            }
        }
    ) { padding ->
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
fun EventRowCard(row: EventRow, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.event.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = row.nextOccurrence.toString(),
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

/** 天数的显示文案。 sealed class 保证 UI 不会漏处理某种情况 */
private fun DayCountResult.displayText(): String = when (this) {
    is DayCountResult.Future -> "还剩 ${days}天"
    DayCountResult.Today -> "就是今天"
    is DayCountResult.Past -> "已过 ${days}天"
    is DayCountResult.CountUp -> "第 ${days}天"
}

@Composable
fun EventList(rows: List<EventRow>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(rows, key = { it.event.id }) { row ->
            EventRowCard(row)
        }
    }
}
