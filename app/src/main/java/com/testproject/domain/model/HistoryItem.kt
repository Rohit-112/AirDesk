package com.testproject.domain.model

data class HistoryItem(
    val id: Int = 0,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFile: Boolean = false,
    val fileName: String? = null,
    val isReceived: Boolean = false,
    val isQueued: Boolean = false
)
