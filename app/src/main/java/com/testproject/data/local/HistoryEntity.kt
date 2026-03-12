package com.testproject.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history_table")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isReceived: Boolean,
    val isFile: Boolean = false,
    val fileName: String? = null,
    val isQueued: Boolean = false
)
