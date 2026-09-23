package com.then.truyenaudio.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "novels")
data class NovelEntity(

    @PrimaryKey
    val id: Int,

    val title: String,

    val author: String,

    val description: String,

    val coverUrl: String?,

    val status: String
)