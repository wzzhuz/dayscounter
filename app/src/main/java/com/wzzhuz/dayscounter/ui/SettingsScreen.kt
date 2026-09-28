package com.wzzhuz.dayscounter.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wzzhuz.dayscounter.data.BackupManager
import com.wzzhuz.dayscounter.data.EventRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页：导出 / 导入 + 标签管理。
 *
 * **用 Storage Access Framework（SAF），不申请任何存储权限**：
 * - `CreateDocument` 让用户自己选保存位置，系统授予临时写权限
 * - `OpenDocument` 同理
 * 这样既不需要 READ/WRITE_EXTERNAL_STORAGE，
 * 也不会像写死公共目录那样在 Android 11+ 被分区存储拦掉。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: EventRepository,
    backupManager: BackupManager,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }

    val tags by repository.observeTags().collectAsState(initial = emptyList())

    // 导出：先算出 JSON，再交给用户选位置
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            runCatching {
                val json = withContext(Dispatchers.IO) { backupManager.export() }
                writeText(context, uri, json)
            }.onSuccess {
                snackbar.showSnackbar("已导出到所选位置")
            }.onFailure { e ->
                snackbar.showSnackbar("导出失败：${e.message ?: "未知错误"}")
            }
            busy = false
        }
    }

    // 导入：读文件 → 单事务写库，失败整体回滚
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            runCatching {
                val text = withContext(Dispatchers.IO) { readText(context, uri) }
                withContext(Dispatchers.IO) { backupManager.import(text) }
            }.onSuccess { count ->
                snackbar.showSnackbar("已导入 $count 条事件")
            }.onFailure { e ->
                // 部分成功是最糟结果，但 import 是单事务，失败即全回滚
                snackbar.showSnackbar("导入失败，已回滚：${e.message ?: "未知错误"}")
            }
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        // 用 Close 而不是返回箭头：此页从底部 Tab 进入，语义是「关闭」
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- 备份 ----
            SectionTitle("备份")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "导出为一个 JSON 文件，可在另一台设备上导入恢复。" +
                            "文件完全由你保管，本 App 不联网、不上传。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            // 初始文件名带日期，避免多次导出互相覆盖
                            exportLauncher.launch(defaultFileName(context))
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("导出为 JSON 文件") }

                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("从 JSON 文件导入") }

                    Text(
                        "导入会先整体解析成功再写入；中途出错则整批回滚，" +
                            "不会出现「导入一半」的情况。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---- 标签管理 ----
            SectionTitle("标签")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (tags.isEmpty()) {
                        Text(
                            "还没有标签。标签可在编辑事件时新建，也可以在这里预先建好。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        tags.forEach { tag ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(tag.name, style = MaterialTheme.typography.bodyLarge)
                                IconButton(onClick = {
                                    scope.launch {
                                        // 删除标签：关联表有 onDelete CASCADE，不会留孤儿行
                                        withContext(Dispatchers.IO) {
                                            repository.deleteTag(tag.id)
                                        }
                                        snackbar.showSnackbar("已删除标签「${tag.name}」")
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "删除标签 ${tag.name}",
                                    )
                                }
                            }
                        }
                    }
                    QuickCreateRow(
                        placeholder = "新建标签…",
                        onCreate = { name -> repository.createTagAsync(name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

private fun defaultFileName(context: Context): String {
    val stamp = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.CHINA)
        .format(java.util.Date())
    return "dayscounter-$stamp.json"
}

private fun writeText(context: Context, uri: android.net.Uri, text: String) {
    context.contentResolver.openOutputStream(uri)?.use { out ->
        out.write(text.toByteArray(Charsets.UTF_8))
        out.flush()
    } ?: error("无法打开所选位置")
}

private fun readText(context: Context, uri: android.net.Uri): String {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("无法读取所选文件")
    return String(bytes, Charsets.UTF_8)
}
