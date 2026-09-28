package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun fromChallengeType(value: ChallengeType): String = value.name

    @TypeConverter
    fun toChallengeType(value: String): ChallengeType = try {
        if (value.equals("PUZZLE", ignoreCase = true)) {
            ChallengeType.IMAGE_ARRANGEMENT
        } else {
            ChallengeType.valueOf(value)
        }
    } catch (e: Exception) {
        ChallengeType.MATH
    }
}

/**
 * Migration from Database Version 1 to 2:
 * Safely migrates existing alarms with legacy 'PUZZLE' challenge type to the new 'IMAGE_ARRANGEMENT' challenge type
 * ensuring zero data loss.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE alarms SET challengeType = 'IMAGE_ARRANGEMENT' WHERE challengeType = 'PUZZLE'")
    }
}

@Database(entities = [AlarmEntity::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "force_alarm_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
