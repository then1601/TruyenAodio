package com.then.truyenaudio.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.then.truyenaudio.data.local.entities.ChapterEntity
import com.then.truyenaudio.data.local.entities.NovelEntity

@Database(
    entities = [
        NovelEntity::class,
        ChapterEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun novelDao(): NovelDao

    abstract fun chapterDao(): ChapterDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "truyen_audio.db"
                ).build()

                INSTANCE = instance

                instance
            }
        }
    }
}