package com.then.truyenaudio.data.remote

import com.then.truyenaudio.domain.model.Chapter
import com.then.truyenaudio.domain.model.Novel
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @GET("novels")
    suspend fun getNovels(): List<Novel>

    @GET("novels/{id}")
    suspend fun getNovel(
        @Path("id") id: Int
    ): Novel

    @GET("novels/{id}/chapters")
    suspend fun getChapters(
        @Path("id") id: Int
    ): List<Chapter>

    @GET("search")
    suspend fun search(
        @Query("q") keyword: String
    ): List<Novel>
}