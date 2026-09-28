package com.wzzhuz.dayscounter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wzzhuz.dayscounter.domain.EventLibrary
import com.wzzhuz.dayscounter.domain.LunarCalendar
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 事件库：内置节日与热门倒数日，点击即添加。
 *
 * 库里存的是**规则**（农历正月初一 / 公历 12 月 25 日），
 * 这里只做展示与换算，具体日期在添加的那一刻才落到具体年份上。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventLibraryScreen(
    onPick: (EventLibrary.LibraryItem) -> Unit,
    onBack: () -> Unit,
) {
    var group by remember { mutableStateOf(EventLibrary.groups.first()) }
    val items = remember(group) { EventLibrary.byGroup(group) }
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("事件库") },
                navigationIcon = {
                    Text("返回", modifier = Modifier
                        .clickable(onClick = onBack)
                        .padding(horizontal = 12.dp))
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 分组切换
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EventLibrary.groups.forEach { g ->
                    FilterChip(
                        selected = g == group,
                        onClick = { group = g },
                        label = { Text(g) },
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.name }) { item ->
                    LibraryItemRow(item = item, today = today, onClick = { onPick(item) })
                }
            }
        }
    }
}

@Composable
private fun LibraryItemRow(
    item: EventLibrary.LibraryItem,
    today: LocalDate,
    onClick: () -> Unit,
) {
    // 换算展示日期：已过期则取下一个（与添加时同一套逻辑）
    val date = remember(item, today) { EventLibrary.resolveDate(item, today) }
    val days = ChronoUnit.DAYS.between(today, date)

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = buildString {
                        append("${date.year} 年 ${date.monthValue} 月 ${date.dayOfMonth} 日")
                        if (item.kind == EventLibrary.Kind.LUNAR) {
                            val ld = LunarCalendar.gregorianToLunar(date)
                            append("（农历${if (ld.isLeapMonth) "闰" else ""}${ld.month}月${ld.day}日）")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        item.category,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = "还剩\n${days}天",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
