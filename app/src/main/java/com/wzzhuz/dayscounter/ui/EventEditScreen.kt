package com.wzzhuz.dayscounter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.wzzhuz.dayscounter.data.Category
import com.wzzhuz.dayscounter.domain.Tag
import com.wzzhuz.dayscounter.domain.LunarCalendar
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 选择日期的弹窗。
 *
 * ⚠️ DatePicker 用 UTC 毫秒，epochDay * 86_400_000 是官方示例的标准写法。
 * 用 LocalDate.ofEpochDay(millis / 86_400_000) 反解，避免时区偏移导致差一天。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = initialDate.toEpochDay() * 86_400_000L
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) {
                    onDateSelected(LocalDate.ofEpochDay(millis / 86_400_000L))
                } else {
                    onDismiss()
                }
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    ) {
        DatePicker(state = state)
    }
}

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日")

/**
 * 新增 / 编辑事件页。
 *
 * 记录成本必须低：标题 + 日期为必填，其余全部有默认值且有合理初始状态。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventEditScreen(
    initial: EventDraft?,
    categories: List<Category>,
    tags: List<Tag>,
    onSave: (EventFormResult) -> Unit,
    onCancel: () -> Unit,
    onCreateCategory: (String) -> Unit,
    onCreateTag: (String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var asLunar by remember { mutableStateOf(initial?.lunar ?: false) }
    var repeatYearly by remember { mutableStateOf(initial?.repeat ?: false) }
    var pinned by remember { mutableStateOf(initial?.pinned ?: false) }
    var countUp by remember {
        mutableStateOf(initial?.countUp ?: (initial?.date?.isBefore(LocalDate.now()) == true))
    }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }

    var categoryId by remember { mutableStateOf(initial?.categoryId) }
    var selectedTagIds by remember {
        mutableStateOf(initial?.tagIds?.toSet() ?: emptySet())
    }

    val lunarText: String? = if (asLunar) {
        val ld = LunarCalendar.gregorianToLunar(date)
        "农历${if (ld.isLeapMonth) "闰" else ""}${ld.month}月${ld.day}日"
    } else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "新增事件" else "编辑事件") },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text("取消") }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (title.isNotBlank()) {
                                onSave(
                                    EventFormResult(
                                        title = title.trim(),
                                        date = date,
                                        lunar = asLunar,
                                        repeat = repeatYearly,
                                        pinned = pinned,
                                        countUp = countUp,
                                        note = note.trim(),
                                        categoryId = categoryId,
                                        tagIds = selectedTagIds.toList(),
                                    )
                                )
                            }
                        },
                        enabled = title.isNotBlank(),
                    ) { Text("保存") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // 日期
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "日期",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(date.format(DATE_FMT), style = MaterialTheme.typography.titleMedium)
                    lunarText?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text("更改", color = MaterialTheme.colorScheme.primary)
            }

            SwitchRow(
                label = "按农历记录",
                sub = "勾选后按农历月日重复（用于家人生日、春节）",
                checked = asLunar,
                onCheckedChange = { asLunar = it },
            )

            SwitchRow(
                label = "每年重复",
                sub = "生日、纪念日",
                checked = repeatYearly,
                onCheckedChange = { repeatYearly = it },
            )

            SwitchRow(
                label = "置顶",
                sub = "置顶事件始终排在最前",
                checked = pinned,
                onCheckedChange = { pinned = it },
            )

            SwitchRow(
                label = "正数计数",
                sub = "出生当天算「第 1 天」；不勾选则为倒数（今天算 0）",
                checked = countUp,
                onCheckedChange = { countUp = it },
            )

            // ---- 分类（单值） ----
            SectionLabel("分类")
            ChipSingleSelect(
                items = categories,
                selectedId = categoryId,
                labelOf = { it.name },
                idOf = { it.id },
                onSelect = { categoryId = it },
            )
            QuickCreateRow(
                placeholder = "新建分类…",
                onCreate = onCreateCategory,
            )

            // ---- 标签（多值） ----
            SectionLabel("标签")
            if (tags.isEmpty()) {
                Text(
                    "还没有标签，可以在下面新建一个。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ChipMultiSelect(
                    items = tags,
                    selectedIds = selectedTagIds,
                    labelOf = { it.name },
                    idOf = { it.id },
                    onToggle = { id ->
                        selectedTagIds = if (id in selectedTagIds) {
                            selectedTagIds - id
                        } else {
                            selectedTagIds + id
                        }
                    },
                )
            }
            QuickCreateRow(
                placeholder = "新建标签…",
                onCreate = onCreateTag,
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注（可选）") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )

            if (onDelete != null) {
                var confirmDelete by remember { mutableStateOf(false) }
                TextButton(onClick = { confirmDelete = true }) {
                    Text("删除事件", color = MaterialTheme.colorScheme.error)
                }
                if (confirmDelete) {
                    AlertDialog(
                        onDismissRequest = { confirmDelete = false },
                        title = { Text("删除这个事件？") },
                        text = { Text("删除后无法恢复。") },
                        confirmButton = {
                            TextButton(onClick = { confirmDelete = false; onDelete() }) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmDelete = false }) { Text("取消") }
                        },
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerModal(
            initialDate = date,
            onDateSelected = { date = it; showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SwitchRow(
    label: String,
    sub: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 编辑页提交结果 */
data class EventFormResult(
    val title: String,
    val date: LocalDate,
    val lunar: Boolean,
    val repeat: Boolean,
    val pinned: Boolean,
    val countUp: Boolean,
    val note: String,
    val categoryId: String?,
    val tagIds: List<String>,
)

/** 编辑页的初始数据（从已有事件映射而来） */
data class EventDraft(
    val id: String,
    val title: String,
    val date: LocalDate,
    val lunar: Boolean,
    val repeat: Boolean,
    val pinned: Boolean,
    val countUp: Boolean,
    val note: String,
    val categoryId: String? = null,
    val tagIds: List<String> = emptyList(),
    val createdAt: Long = 0L,
)
