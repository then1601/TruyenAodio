package com.then.truyenaudio.data.local

import androidx.room.*
import com.then.truyenaudio.data.local.entities.NovelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {

    @Query("SELECT * FROM novels ORDER BY id DESC")
    fun getAll(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novels WHERE id = :id")
    suspend fun getById(id: Int): NovelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(novel: NovelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(novels: List<NovelEntity>)

    @Delete
    suspend fun delete(novel: NovelEntity)

    @Query("DELETE FROM novels")
    suspend fun deleteAll()
}