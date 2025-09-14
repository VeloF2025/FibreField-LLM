package com.fibreflow.core.database.dao

import androidx.room.*
import com.fibreflow.core.database.entities.AIConversationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for AI Conversation operations
 * Handles all database operations related to AI chat conversations
 */
@Dao
interface AIConversationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: AIConversationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(conversations: List<AIConversationEntity>): List<Long>

    @Update
    suspend fun updateConversation(conversation: AIConversationEntity)

    @Delete
    suspend fun deleteConversation(conversation: AIConversationEntity)

    @Query("SELECT * FROM ai_conversations WHERE id = :conversationId")
    suspend fun getConversationById(conversationId: String): AIConversationEntity?

    @Query("SELECT * FROM ai_conversations WHERE installationId = :installationId ORDER BY timestamp ASC")
    suspend fun getConversationsByInstallation(installationId: String): List<AIConversationEntity>

    @Query("SELECT * FROM ai_conversations WHERE installationId = :installationId ORDER BY timestamp ASC")
    fun getConversationsByInstallationFlow(installationId: String): Flow<List<AIConversationEntity>>

    @Query("SELECT * FROM ai_conversations WHERE technicianId = :technicianId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentConversationsByTechnician(technicianId: String, limit: Int = 50): List<AIConversationEntity>

    @Query("DELETE FROM ai_conversations WHERE timestamp < :cutoffDate")
    suspend fun deleteOldConversations(cutoffDate: Long): Int

    @Query("SELECT COUNT(*) FROM ai_conversations")
    suspend fun getTotalConversationCount(): Int
}