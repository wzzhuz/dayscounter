package com.wzzhuz.dayscounter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wzzhuz.dayscounter.domain.DateCalculator
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 日期计算器：独立于事件列表的工具。
 *
 * 两个能力，两个 Tab：
 * 1. **间隔**：两日期相差多少天（取绝对值——间隔是距离，不是位移）
 * 2. **推算**：某日期前/后 N 天是哪天
 *
 * **只做日期运算，不引入时刻与时区**，与事件天数计算同源（铁律一）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateCalculatorScreen(onBack: () -> Unit) {
    var mode by remember { mutableStateOf(Mode.INTERVAL) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日期计算器") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == Mode.INTERVAL,
                    onClick = { mode = Mode.INTERVAL },
                    label = { Text("间隔") },
                )
                FilterChip(
                    selected = mode == Mode.OFFSET,
                    onClick = { mode = Mode.OFFSET },
                    label = { Text("推算") },
                )
            }

            when (mode) {
                Mode.INTERVAL -> IntervalPanel()
                Mode.OFFSET -> OffsetPanel()
            }
        }
    }
}

private enum class Mode { INTERVAL, OFFSET }

private val FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

private fun parseOrToday(text: String): LocalDate =
    runCatching { LocalDate.parse(text.trim(), FMT) }.getOrDefault(LocalDate.now())

@Composable
private fun IntervalPanel() {
    val today = LocalDate.now()
    var fromText by remember { mutableStateOf(today.format(FMT)) }
    var toText by remember { mutableStateOf(today.plusDays(100).format(FMT)) }

    val from = parseOrToday(fromText)
    val to = parseOrToday(toText)
    val days = DateCalculator.daysBetween(from, to)
    val period = DateCalculator.periodBetween(from, to)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = fromText,
            onValueChange = { fromText = it },
            label = { Text("开始日期（yyyy-MM-dd）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = toText,
            onValueChange = { toText = it },
            label = { Text("结束日期（yyyy-MM-dd）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "$days 天",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    // Period 按日历单位逐字段算，是近似值，必须标「约」
                    text = "约 ${DateCalculator.formatPeriod(period)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "间隔取绝对值：起止顺序不影响结果。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OffsetPanel() {
    val today = LocalDate.now()
    var baseText by remember { mutableStateOf(today.format(FMT)) }
    var offsetText by remember { mutableStateOf("100") }

    val base = parseOrToday(baseText)
    val offset = offsetText.trim().toLongOrNull() ?: 0L
    val result = DateCalculator.addDays(base, offset)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = baseText,
            onValueChange = { baseText = it },
            label = { Text("基准日期（yyyy-MM-dd）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = offsetText,
            onValueChange = { offsetText = it },
            label = { Text("天数（负数表示往前）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = DateCalculator.formatWithWeekday(result),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${DateCalculator.weekdayName(result)} · 距今 ${DateCalculator.daysBetween(LocalDate.now(), result)} 天",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "跨闰年自动处理：2024-02-28 往后 1 天是 02-29。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
