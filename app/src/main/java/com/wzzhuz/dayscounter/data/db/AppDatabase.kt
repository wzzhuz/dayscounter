package com.wzzhuz.dayscounter.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        EventEntity::class,
        TagEntity::class,
        EventTagCrossRef::class,
        CategoryEntity::class,
        EventFts::class,
    ],
    version = 3,
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
                .addCallback(SeedCallback())
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        /**
         * 2 → 3：归档字段 + 配色字段。
         *
         * **必须写显式迁移**：使用者手机上已有真实事件，
         * `fallbackToDestructiveMigration` = 数据全丢。
         * 这里只加列，不动任何已有数据。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN archivedAt INTEGER")
                db.execSQL("ALTER TABLE events ADD COLUMN colorArgb INTEGER")
                db.execSQL("ALTER TABLE categories ADD COLUMN colorStartArgb INTEGER")
                db.execSQL("ALTER TABLE categories ADD COLUMN colorEndArgb INTEGER")
                // 建索引：主列表恒查 archivedAt IS NULL
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_archivedAt` ON `events` (`archivedAt`)")

                // 为已有默认分类回填配色（与 ColorPalette 保持一致）
                backfillCategoryColor(db, "纪念日", 0xFFFCE4EC.toInt(), 0xFFF8BBD0.toInt())
                backfillCategoryColor(db, "工作", 0xFFE3F2FD.toInt(), 0xFF90CAF9.toInt())
                backfillCategoryColor(db, "生活", 0xFFE8F5E9.toInt(), 0xFFA5D6A7.toInt())
            }

            private fun backfillCategoryColor(
                db: SupportSQLiteDatabase,
                name: String,
                start: Int,
                end: Int,
            ) {
                db.execSQL(
                    "UPDATE categories SET colorStartArgb = ?, colorEndArgb = ? WHERE name = ?",
                    arrayOf<Any>(start, end, name)
                )
            }
        }

        /**
         * 1 → 2：新增 categories 表（分类功能）。
         *
         * **必须写显式迁移，不能用 fallbackToDestructiveMigration**：
         * 使用者手机上已有真实事件数据，破坏性迁移 = 数据全丢。
         * 这里只建新表 + seed 默认分类，完全不触碰 events / tags。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `categories` (" +
                        "`id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, " +
                        "`builtIn` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` " +
                        "ON `categories` (`name`)"
                )
                // onCreate 的 seed 只对全新库生效，升级场景要在这里补
                db.execSQL(
                    "INSERT OR IGNORE INTO categories (id, name, sortOrder, builtIn) " +
                        "VALUES ('cat_anniversary', '纪念日', 0, 1)"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO categories (id, name, sortOrder, builtIn) " +
                        "VALUES ('cat_work', '工作', 1, 1)"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO categories (id, name, sortOrder, builtIn) " +
                        "VALUES ('cat_life', '生活', 2, 1)"
                )
            }
        }

        /**
         * 预置三个默认分类。
         *
         * **必须在事务里 seed**：否则建表后首次查询可能读到空列表，
         * 让用户以为「分类功能坏了」。
         */
        private class SeedCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.beginTransaction()
                try {
                    listOf(
                        Triple("cat_anniversary", "纪念日", 0),
                        Triple("cat_work", "工作", 1),
                        Triple("cat_life", "生活", 2),
                    ).forEach { (id, name, order) ->
                        db.execSQL(
                            "INSERT OR IGNORE INTO categories (id, name, sortOrder, builtIn) " +
                                "VALUES (?, ?, ?, 1)",
                            arrayOf<Any>(id, name, order)
                        )
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }

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
