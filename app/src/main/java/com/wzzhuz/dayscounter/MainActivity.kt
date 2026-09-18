package com.wzzhuz.dayscounter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.wzzhuz.dayscounter.data.EventRepository
import com.wzzhuz.dayscounter.domain.CalendarType
import com.wzzhuz.dayscounter.domain.CountMode
import com.wzzhuz.dayscounter.domain.Event
import com.wzzhuz.dayscounter.domain.LunarCalendar
import com.wzzhuz.dayscounter.domain.RepeatType
import com.wzzhuz.dayscounter.ui.EventDraft
import com.wzzhuz.dayscounter.ui.EventEditScreen
import com.wzzhuz.dayscounter.ui.EventListScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = (application as DaysCounterApp).repository

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // 简单的两页状态机。首版不引入 navigation 依赖，
                    // 等页面变多再开 change 引入 navigation-compose。
                    var editingId by remember { mutableStateOf<String?>(null) }
                    var isAdding by remember { mutableStateOf(false) }

                    val categories by repository.observeCategories()
                        .collectAsState(initial = emptyList())
                    val tags by repository.observeTags()
                        .collectAsState(initial = emptyList())

                    if (isAdding || editingId != null) {
                        val draft = editingId?.let { id -> rememberDraft(repository, id) }

                        // 编辑模式下草稿异步加载，未到位时不显示表单，
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
                                            calendarType = if (form.lunar) {
                                                CalendarType.LUNAR
                                            } else {
                                                CalendarType.GREGORIAN
                                            },
                                            lunarMonth = if (form.lunar) lunarDate.month else null,
                                            lunarDay = if (form.lunar) lunarDate.day else null,
                                            lunarLeapMonth = if (form.lunar) {
                                                lunarDate.isLeapMonth
                                            } else false,
                                            countMode = if (form.countUp) {
                                                CountMode.COUNTUP
                                            } else {
                                                CountMode.COUNTDOWN
                                            },
                                            repeatType = if (form.repeat) {
                                                RepeatType.YEARLY
                                            } else {
                                                RepeatType.NONE
                                            },
                                            categoryId = form.categoryId,
                                            pinned = form.pinned,
                                            note = form.note,
                                            tags = tags.filter { it.id in form.tagIds },
                                            // 编辑时保留原创建时间
                                            createdAt = draft?.createdAt ?: now,
                                            updatedAt = now,
                                        )
                                    ) {
                                        isAdding = false
                                        editingId = null
                                    }
                                },
                                onCancel = { isAdding = false; editingId = null },
                                onCreateCategory = { name ->
                                    repository.findOrCreateCategoryAsync(name)
                                },
                                onCreateTag = { name -> repository.createTagAsync(name) },
                                onDelete = if (draft != null) {
                                    { repository.deleteAsync(draft.id); editingId = null }
                                } else null,
                            )
                        }
                    } else {
                        EventListScreen(
                            repository = repository,
                            onAddClick = { isAdding = true },
                            onEventClick = { row -> editingId = row.event.id },
                        )
                    }
                }
            }
        }
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
