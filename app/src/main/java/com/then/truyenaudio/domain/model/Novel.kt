package com.then.truyenaudio.domain.model

data class Novel(
    val id: Int,
    val title: String,
    val author: String,
    val description: String,
    val coverUrl: String?,
    val status: String,
    val sourceUrl: String = ""
)