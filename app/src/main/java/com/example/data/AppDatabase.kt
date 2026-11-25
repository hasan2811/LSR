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
    version = 5,
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lsr_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration(dropAllTables = false)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
