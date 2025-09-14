package com.fibreflow.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.*

/**
 * Entity representing AI conversations for LLM guidance
 */
@Entity(
    tableName = "ai_conversations",
    indices = [
        Index(value = ["installation_id"]),
        Index(value = ["created_at"])
    ]
)
data class AIConversationEntity(
    @PrimaryKey
    @ColumnInfo(name = "conversation_id")
    val conversationId: Long,

    @ColumnInfo(name = "installation_id")
    val installationId: Long?,

    @ColumnInfo(name = "user_message")
    val userMessage: String,

    @ColumnInfo(name = "ai_response")
    val aiResponse: String,

    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Float?,

    @ColumnInfo(name = "created_at")
    val createdAt: Date
)