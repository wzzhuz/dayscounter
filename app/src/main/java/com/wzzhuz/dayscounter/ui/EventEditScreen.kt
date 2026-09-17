package com.wzzhuz.dayscounter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wzzhuz.dayscounter.domain.CalendarType
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

/** 编辑 / 新增事件的表单状态 */
private class EventFormState(
    initialTitle: String,
    initialDate: LocalDate,
    initialLunar: Boolean,
    initialRepeat: Boolean,
    initialPinned: Boolean,
    initialCountUp: Boolean,
    initialNote: String,
) {
    var title by mutableStateOf(initialTitle)
    var date by mutableStateOf(initialDate)
    var asLunar by mutableStateOf(initialLunar)
    var repeatYearly by mutableStateOf(initialRepeat)
    var pinned by mutableStateOf(initialPinned)
    var countUp by mutableStateOf(initialCountUp)
    var note by mutableStateOf(initialNote)
    var showDatePicker by mutableStateOf(false)

    /** 农历模式下，把公历选择结果换算成农历月日后保存 */
    fun resolvedCalendarType(): CalendarType =
        if (asLunar) CalendarType.LUNAR else CalendarType.GREGORIAN

    fun lunarText(): String {
        val ld = LunarCalendar.gregorianToLunar(date)
        return "农历${if (ld.isLeapMonth) "闰" else ""}${ld.month}月${ld.day}日"
    }
}

/**
 * 新增 / 编辑事件页。
 *
 * 记录成本必须低：标题 + 日期为必填，其余全部有默认值且有合理初始状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(
    initial: EventDraft?,
    onSave: (title: String, date: LocalDate, lunar: Boolean, repeat: Boolean, pinned: Boolean, countUp: Boolean, note: String) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val state = remember {
        EventFormState(
            initialTitle = initial?.title ?: "",
            initialDate = initial?.date ?: LocalDate.now(),
            initialLunar = initial?.lunar ?: false,
            initialRepeat = initial?.repeat ?: false,
            initialPinned = initial?.pinned ?: false,
            initialCountUp = initial?.countUp ?: (initial?.date?.isBefore(LocalDate.now()) == true),
            initialNote = initial?.note ?: "",
        )
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(if (initial == null) "新增事件" else "编辑事件") },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text("取消") }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (state.title.isNotBlank()) {
                                onSave(
                                    state.title.trim(),
                                    state.date,
                                    state.asLunar,
                                    state.repeatYearly,
                                    state.pinned,
                                    state.countUp,
                                    state.note.trim(),
                                )
                            }
                        },
                        enabled = state.title.isNotBlank(),
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
                value = state.title,
                onValueChange = { state.title = it },
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // 日期
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { state.showDatePicker = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("日期", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(state.date.format(DATE_FMT), style = MaterialTheme.typography.titleMedium)
                    if (state.asLunar) {
                        Text(state.lunarText(), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text("更改", color = MaterialTheme.colorScheme.primary)
            }

            SwitchRow(
                label = "按农历记录",
                sub = "勾选后按农历月日重复（用于家人生日、春节）",
                checked = state.asLunar,
                onCheckedChange = { state.asLunar = it },
            )

            SwitchRow(
                label = "每年重复",
                sub = "生日、纪念日",
                checked = state.repeatYearly,
                onCheckedChange = { state.repeatYearly = it },
            )

            SwitchRow(
                label = "置顶",
                sub = "置顶事件始终排在最前",
                checked = state.pinned,
                onCheckedChange = { state.pinned = it },
            )

            SwitchRow(
                label = "正数计数",
                sub = "出生当天算「第 1 天」；不勾选则为倒数（今天算 0）",
                checked = state.countUp,
                onCheckedChange = { state.countUp = it },
            )

            OutlinedTextField(
                value = state.note,
                onValueChange = { state.note = it },
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

    if (state.showDatePicker) {
        DatePickerModal(
            initialDate = state.date,
            onDateSelected = { state.date = it; state.showDatePicker = false },
            onDismiss = { state.showDatePicker = false },
        )
    }
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
            Text(sub, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

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
    val createdAt: Long = 0L,
)
