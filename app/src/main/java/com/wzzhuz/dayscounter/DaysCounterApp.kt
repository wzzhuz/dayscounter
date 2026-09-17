package com.wzzhuz.dayscounter

import android.app.Application
import com.wzzhuz.dayscounter.data.EventRepository
import com.wzzhuz.dayscounter.data.db.AppDatabase

class DaysCounterApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val repository: EventRepository by lazy { EventRepository(database.eventDao()) }
}
