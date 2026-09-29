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
        when {
            value.equals("PUZZLE", ignoreCase = true) -> ChallengeType.IMAGE_ARRANGEMENT
            value.equals("WORD_SCRAMBLE", ignoreCase = true) -> ChallengeType.CARD_ARRANGEMENT
            else -> ChallengeType.valueOf(value)
        }
    } catch (e: Exception) {
        ChallengeType.MATH
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE alarms SET challengeType = 'IMAGE_ARRANGEMENT' WHERE challengeType = 'PUZZLE'")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE alarms SET challengeType = 'CARD_ARRANGEMENT' WHERE challengeType = 'WORD_SCRAMBLE'")
    }
}

@Database(entities = [AlarmEntity::class], version = 3, exportSchema = false)
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
