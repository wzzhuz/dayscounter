package com.wzzhuz.dayscounter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 一排可多选的标签。
 *
 * 用 FlowRow 而非 LazyRow：标签数量是个位数，
 * FlowRow 自动换行，视觉上比横向滚动更容易一览无余。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipMultiSelect(
    items: List<T>,
    selectedIds: Set<String>,
    labelOf: (T) -> String,
    idOf: (T) -> String,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            val id = idOf(item)
            FilterChip(
                selected = id in selectedIds,
                onClick = { onToggle(id) },
                label = { Text(labelOf(item)) },
                leadingIcon = if (id in selectedIds) {
                    {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "取消选择",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else null,
            )
        }
    }
}

/**
 * 一排单选的分类。
 *
 * 与标签的区别是**单值**：再点一次已选中的项 = 取消选择（回到「未分类」），
 * 而不是保持选中——单值选择器不该出现「点了没反应」。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipSingleSelect(
    items: List<T>,
    selectedId: String?,
    labelOf: (T) -> String,
    idOf: (T) -> String,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            val id = idOf(item)
            FilterChip(
                selected = id == selectedId,
                onClick = { onSelect(if (id == selectedId) null else id) },
                label = { Text(labelOf(item)) },
            )
        }
    }
}

/**
 * 带「+」的新建输入行。用于现场创建分类 / 标签。
 *
 * 为什么不做成弹窗：加一条事件要三步以内，
 * 为一个标签多开一层弹窗会让记录成本失控。
 */
@Composable
fun QuickCreateRow(
    placeholder: String,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(placeholder) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = {
                val name = text.trim()
                if (name.isNotBlank()) {
                    onCreate(name)
                    text = ""
                }
            },
            enabled = text.isNotBlank(),
        ) {
            Icon(Icons.Default.Add, contentDescription = "添加")
        }
    }
}

/** 已选标签的紧凑展示（列表卡片用） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagChips(
    names: List<String>,
    modifier: Modifier = Modifier,
) {
    if (names.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        names.take(3).forEach { name ->
            // 纯展示：不用 Chip（Chip 有选中态，点了却没反应会造成困惑）
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (names.size > 3) {
            Text(
                "+${names.size - 3}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
