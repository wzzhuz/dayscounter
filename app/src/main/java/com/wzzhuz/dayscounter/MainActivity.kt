package com.wzzhuz.dayscounter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.wzzhuz.dayscounter.data.BackupManager
import com.wzzhuz.dayscounter.data.EventRepository
import com.wzzhuz.dayscounter.domain.CalendarType
import com.wzzhuz.dayscounter.domain.CountMode
import com.wzzhuz.dayscounter.domain.Event
import com.wzzhuz.dayscounter.domain.EventLibrary
import com.wzzhuz.dayscounter.domain.LunarCalendar
import com.wzzhuz.dayscounter.domain.RepeatType
import com.wzzhuz.dayscounter.ui.ArchiveScreen
import com.wzzhuz.dayscounter.ui.DateCalculatorScreen
import com.wzzhuz.dayscounter.ui.EventDraft
import com.wzzhuz.dayscounter.ui.EventEditScreen
import com.wzzhuz.dayscounter.ui.EventLibraryScreen
import com.wzzhuz.dayscounter.ui.EventListScreen
import com.wzzhuz.dayscounter.ui.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as DaysCounterApp

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppContent(app.repository, app.backupManager)
                }
            }
        }
    }
}

private enum class Screen { LIST, LIBRARY, CALCULATOR, ARCHIVE, SETTINGS }

/**
 * 页面路由。
 *
 * 首版不引入 navigation-compose 依赖，用简单状态机。
 * 底部三个主 Tab + 归档页（从列表页进入）+ 编辑页（覆盖式）。
 *
 * ⚠️ 归档页不放底部 Tab：它是低频操作，占一个 Tab 位不划算，
 *    且误触会让人以为事件「没了」。
 */
@androidx.compose.runtime.Composable
private fun AppContent(repository: EventRepository, backupManager: BackupManager) {
    var screen by remember { mutableStateOf(Screen.LIST) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var isAdding by remember { mutableStateOf(false) }

    val categories by repository.observeCategories().collectAsState(initial = emptyList())
    val tags by repository.observeTags().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    // 编辑页覆盖在最上层：编辑/新增时底部导航不该还能点
    if (isAdding || editingId != null) {
        val draft = editingId?.let { id -> rememberDraft(repository, id) }

        // 编辑模式草稿异步加载，未到位时不渲染表单，
        // 否则会用空值覆盖原数据
        if (editingId == null || draft != null) {
            EventEditScreen(
                initial = draft,
                categories = categories,
                tags = tags,
                onSave = { form ->
                    val now = System.currentTimeMillis()
                    val lunarDate = LunarCalendar.gregorianToLunar(form.date)
                    repository.saveAsync(
                        Event(
                            id = editingId ?: repository.newId(),
                            title = form.title,
                            originDate = form.date,
                            calendarType = if (form.lunar) CalendarType.LUNAR else CalendarType.GREGORIAN,
                            lunarMonth = if (form.lunar) lunarDate.month else null,
                            lunarDay = if (form.lunar) lunarDate.day else null,
                            lunarLeapMonth = if (form.lunar) lunarDate.isLeapMonth else false,
                            countMode = if (form.countUp) CountMode.COUNTUP else CountMode.COUNTDOWN,
                            repeatType = if (form.repeat) RepeatType.YEARLY else RepeatType.NONE,
                            categoryId = form.categoryId,
                            pinned = form.pinned,
                            note = form.note,
                            tags = tags.filter { it.id in form.tagIds },
                            createdAt = draft?.createdAt ?: now,
                            updatedAt = now,
                        )
                    ) {
                        isAdding = false
                        editingId = null
                    }
                },
                onCancel = { isAdding = false; editingId = null },
                onCreateCategory = { name -> repository.findOrCreateCategoryAsync(name) },
                onCreateTag = { name -> repository.createTagAsync(name) },
                onArchive = if (draft != null) {
                    {
                        repository.archiveAsync(draft.id)
                        editingId = null
                    }
                } else null,
                onDelete = if (draft != null) {
                    { repository.deleteAsync(draft.id); editingId = null }
                } else null,
            )
            return
        }
    }

    when (screen) {
        Screen.LIST -> {
            androidx.compose.material3.Scaffold(
                bottomBar = { BottomTabs(current = screen, onSelect = { screen = it }) }
            ) { padding ->
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    EventListScreen(
                        repository = repository,
                        categories = categories,
                        onAddClick = { isAdding = true },
                        onEventClick = { row -> editingId = row.event.id },
                        onOpenArchive = { screen = Screen.ARCHIVE },
                    )
                }
            }
        }

        Screen.LIBRARY -> {
            androidx.compose.material3.Scaffold(
                bottomBar = { BottomTabs(current = screen, onSelect = { screen = it }) }
            ) { padding ->
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // 从库里选一条 → 在 IO 线程落库（含自动建分类），完成后回列表
                    EventLibraryScreen(
                        onPick = { item ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    addFromLibrary(repository, item)
                                }
                                screen = Screen.LIST
                            }
                        },
                        onBack = { screen = Screen.LIST },
                    )
                }
            }
        }

        Screen.CALCULATOR -> {
            androidx.compose.material3.Scaffold(
                bottomBar = { BottomTabs(current = screen, onSelect = { screen = it }) }
            ) { padding ->
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    DateCalculatorScreen(onBack = { screen = Screen.LIST })
                }
            }
        }

        Screen.ARCHIVE -> {
            ArchiveScreen(
                repository = repository,
                onBack = { screen = Screen.LIST },
            )
        }

        Screen.SETTINGS -> {
            SettingsScreen(
                repository = repository,
                backupManager = backupManager,
                onBack = { screen = Screen.LIST },
            )
        }
    }
}

/**
 * 把库条目落库。
 *
 * 关键：日期用 [EventLibrary.resolveDate] 现场换算（已过期取下一个），
 * 分类用 findOrCreateCategory 按名字复用——否则「节日」会被建成多个不同 id。
 */
private suspend fun addFromLibrary(repository: EventRepository, item: EventLibrary.LibraryItem) {
    val now = System.currentTimeMillis()
    val date = EventLibrary.resolveDate(item)
    val lunarDate = LunarCalendar.gregorianToLunar(date)
    val isLunar = item.kind == EventLibrary.Kind.LUNAR
    val categoryId = runCatching { repository.findOrCreateCategory(item.category) }.getOrNull()

    repository.save(
        Event(
            id = repository.newId(),
            title = item.name,
            originDate = date,
            calendarType = if (isLunar) CalendarType.LUNAR else CalendarType.GREGORIAN,
            lunarMonth = if (isLunar) lunarDate.month else null,
            lunarDay = if (isLunar) lunarDate.day else null,
            lunarLeapMonth = if (isLunar) lunarDate.isLeapMonth else false,
            countMode = CountMode.COUNTDOWN,
            repeatType = RepeatType.YEARLY,
            categoryId = categoryId,
            note = "",
            createdAt = now,
            updatedAt = now,
        )
    )
}

@androidx.compose.runtime.Composable
private fun BottomTabs(current: Screen, onSelect: (Screen) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = current == Screen.LIST,
            onClick = { onSelect(Screen.LIST) },
            icon = { Icon(Icons.Default.Home, contentDescription = "事件") },
            label = { Text("事件") },
        )
        NavigationBarItem(
            selected = current == Screen.LIBRARY,
            onClick = { onSelect(Screen.LIBRARY) },
            icon = { Icon(Icons.Default.List, contentDescription = "事件库") },
            label = { Text("事件库") },
        )
        NavigationBarItem(
            selected = current == Screen.CALCULATOR,
            onClick = { onSelect(Screen.CALCULATOR) },
            icon = { Icon(Icons.Default.DateRange, contentDescription = "计算器") },
            label = { Text("计算器") },
        )
        NavigationBarItem(
            selected = current == Screen.SETTINGS,
            onClick = { onSelect(Screen.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
            label = { Text("设置") },
        )
    }
}

/**
 * 把已有事件读成编辑页的草稿。
 * 在 IO 线程查询，避免阻塞主线程。
 */
@androidx.compose.runtime.Composable
private fun rememberDraft(repository: EventRepository, id: String): EventDraft? {
    var draft by remember(id) { mutableStateOf<EventDraft?>(null) }
    LaunchedEffect(id) {
        val event = withContext(Dispatchers.IO) { repository.getById(id) }
        draft = event?.let {
            EventDraft(
                id = it.id,
                title = it.title,
                date = it.originDate,
                lunar = it.calendarType == CalendarType.LUNAR,
                repeat = it.repeatType == RepeatType.YEARLY,
                pinned = it.pinned,
                countUp = it.countMode == CountMode.COUNTUP,
                note = it.note,
                categoryId = it.categoryId,
                tagIds = it.tags.map { t -> t.id },
                createdAt = it.createdAt,
            )
        }
    }
    return draft
}
