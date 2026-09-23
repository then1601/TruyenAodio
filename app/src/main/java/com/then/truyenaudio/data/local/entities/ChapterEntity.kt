package com.then.truyenaudio.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chapters")
data class ChapterEntity(

    @PrimaryKey
    val id: Int,

    val novelId: Int,

    val chapterNumber: Int,

    val title: String,

    val content: String
)