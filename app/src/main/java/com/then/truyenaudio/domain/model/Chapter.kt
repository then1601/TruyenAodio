package com.then.truyenaudio.domain.model

data class Chapter(
    val id: Int,
    val novelId: Int,
    val chapterNumber: Int,
    val title: String,
    val content: String,
    val sourceUrl: String = ""
)