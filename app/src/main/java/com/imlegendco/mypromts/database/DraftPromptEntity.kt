package com.imlegendco.mypromts.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "draft_prompts")
data class DraftPromptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
