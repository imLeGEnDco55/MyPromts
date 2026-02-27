package com.imlegendco.mypromts.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftPromptDao {
    @Query("SELECT * FROM draft_prompts ORDER BY timestamp DESC")
    fun getAllDrafts(): Flow<List<DraftPromptEntity>>

    @Insert
    suspend fun insertDraft(draft: DraftPromptEntity)

    @Delete
    suspend fun deleteDraft(draft: DraftPromptEntity)
}
