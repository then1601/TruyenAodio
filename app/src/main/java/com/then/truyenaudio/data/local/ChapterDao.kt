package com.then.truyenaudio.data.local

import androidx.room.*
import com.then.truyenaudio.data.local.entities.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {

    @Query("""
        SELECT * FROM chapters
        WHERE novelId = :novelId
        ORDER BY chapterNumber ASC
    """)
    fun getChapters(novelId: Int): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getById(id: Int): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chapter: ChapterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<ChapterEntity>)
}