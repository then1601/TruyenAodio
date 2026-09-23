package com.then.truyenaudio.data.repository

import com.then.truyenaudio.data.local.NovelDao
import com.then.truyenaudio.data.remote.ApiService

class NovelRepository(
    private val api: ApiService,
    private val novelDao: NovelDao
) {

    fun getLocalNovels() =
        novelDao.getAll()

    suspend fun refreshNovels() {

        val novels = api.getNovels()

        novelDao.insertAll(
            novels.map {
                com.then.truyenaudio.data.local.entities.NovelEntity(
                    id = it.id,
                    title = it.title,
                    author = it.author,
                    description = it.description,
                    coverUrl = it.coverUrl,
                    status = it.status
                )
            }
        )
    }
}