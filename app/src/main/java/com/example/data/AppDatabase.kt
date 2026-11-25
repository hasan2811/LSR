package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ActiveAnswer::class,
        InspectionRecord::class,
        FindingItem::class,
        ActionHistoryLog::class,
        AppNotification::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lsrDao(): LsrDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE inspection_records ADD COLUMN projectId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE finding_items ADD COLUMN projectId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE finding_items ADD COLUMN evidencePhotoUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE finding_items ADD COLUMN evidencePhotoUrl TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE inspection_records ADD COLUMN company TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE inspection_records ADD COLUMN lsrCategories TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE finding_items ADD COLUMN company TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_notifications ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_notifications ADD COLUMN senderName TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE finding_items ADD COLUMN remoteDocId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE finding_items ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE finding_items ADD COLUMN cachedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE finding_items SET updatedAt = timestamp, cachedAt = timestamp WHERE updatedAt = 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_inspection_cursor ON inspection_records(timestamp, id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_inspection_proj_cursor ON inspection_records(projectId, timestamp, id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_inspection_sync ON inspection_records(isSynced, timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_finding_cursor_proj ON finding_items(projectId, timestamp, id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_finding_status_cursor ON finding_items(status, timestamp, id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_finding_sync_ttl ON finding_items(isSynced, cachedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_finding_updated_at ON finding_items(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_finding_remote_doc_id ON finding_items(remoteDocId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_notif_cursor ON app_notifications(timestamp, id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_notif_read_ttl ON app_notifications(isRead, timestamp)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lsr_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration(dropAllTables = false)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
