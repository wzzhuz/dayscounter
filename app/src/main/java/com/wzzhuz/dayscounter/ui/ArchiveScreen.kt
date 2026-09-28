package com.wzzhuz.dayscounter.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wzzhuz.dayscounter.data.EventRepository
import com.wzzhuz.dayscounter.data.EventRow

/**
 * 归档页：查看已归档事件，可恢复或彻底删除。
 *
 * **归档不是删除**——数据完整保留，只是从主列表隐藏。
 * 过期事件不该「只能删」，删了就真没了。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    repository: EventRepository,
    onBack: () -> Unit,
) {
    val rows by repository.observeArchived().collectAsState(initial = emptyList())
    var pendingDelete by remember { mutableStateOf<EventRow?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("已归档") },
                navigationIcon = {
                    Text("返回", modifier = Modifier
                        .clickable(onClick = onBack)
                        .padding(horizontal = 12.dp))
                }
            )
        }
    ) { padding ->
        if (rows.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Text(
                    "还没有归档的事件。\n过期的日子可以归档，比删掉更安心。",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { it.event.id }) { row ->
                    ArchiveRow(
                        row = row,
                        onRestore = { repository.unarchiveAsync(row.event.id) },
                        onDelete = { pendingDelete = row },
                    )
                }
            }
        }
    }

    // 彻底删除必须二次确认：这是唯一不可恢复的操作
    pendingDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("彻底删除「${row.event.title}」？") },
            text = { Text("删除后无法恢复。如果只是想从主列表隐藏，用「恢复」即可。") },
            confirmButton = {
                TextButton(onClick = {
                    repository.deleteForeverAsync(row.event.id)
                    pendingDelete = null
                }) { Text("彻底删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ArchiveRow(
    row: EventRow,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.event.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    row.nextOccurrence.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    row.result.displayText().replace("\n", ""),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    TextButton(onClick = onRestore) { Text("恢复") }
                    TextButton(onClick = onDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
