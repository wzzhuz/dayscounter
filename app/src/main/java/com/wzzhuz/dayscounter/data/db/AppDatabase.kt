package com.wzzhuz.dayscounter.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        EventEntity::class,
        TagEntity::class,
        EventTagCrossRef::class,
        EventFts::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao

    companion object {
        private const val DB_NAME = "dayscounter.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addCallback(FtsTriggerCallback())
                .build()

        /**
         * FTS 同步触发器。
         *
         * **Room 的 @Fts4(contentEntity=...) 不会自动生成触发器。**
         * 不建这三条的症状是：编译过、跑起来、搜索永远返回空结果、且不报任何错。
         */
        private class FtsTriggerCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS events_fts_ai
                    AFTER INSERT ON events BEGIN
                        INSERT INTO events_fts(rowid, title, note)
                        VALUES (new.rowid, new.title, new.note);
                    END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS events_fts_ad
                    AFTER DELETE ON events BEGIN
                        INSERT INTO events_fts(events_fts, rowid, title, note)
                        VALUES ('delete', old.rowid, old.title, old.note);
                    END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS events_fts_au
                    AFTER UPDATE ON events BEGIN
                        INSERT INTO events_fts(events_fts, rowid, title, note)
                        VALUES ('delete', old.rowid, old.title, old.note);
                        INSERT INTO events_fts(rowid, title, note)
                        VALUES (new.rowid, new.title, new.note);
                    END
                    """.trimIndent()
                )
            }
        }
    }
}
