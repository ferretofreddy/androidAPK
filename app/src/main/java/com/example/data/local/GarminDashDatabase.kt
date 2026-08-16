package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RouteEntity::class, TrackPointEntity::class, DownloadedMapEntity::class],
    version = 3,
    exportSchema = false
)
abstract class GarminDashDatabase : RoomDatabase() {

    abstract fun routeDao(): RouteDao
    abstract fun downloadedMapDao(): DownloadedMapDao

    companion object {
        @Volatile
        private var INSTANCE: GarminDashDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `downloaded_maps` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `minLat` REAL NOT NULL,
                        `maxLat` REAL NOT NULL,
                        `minLon` REAL NOT NULL,
                        `maxLon` REAL NOT NULL,
                        `zoomMin` INTEGER NOT NULL,
                        `zoomMax` INTEGER NOT NULL,
                        `downloadDateTimestamp` INTEGER NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `filePath` TEXT NOT NULL,
                        `totalTiles` INTEGER NOT NULL,
                        `downloadedTiles` INTEGER NOT NULL,
                        `isCompleted` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): GarminDashDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GarminDashDatabase::class.java,
                    "garmindash_database.db"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

