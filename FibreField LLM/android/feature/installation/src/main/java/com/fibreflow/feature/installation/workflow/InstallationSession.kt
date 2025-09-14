package com.fibreflow.feature.installation.workflow

import com.fibreflow.core.common.result.Result
import com.fibreflow.domain.drops.entities.Drop
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installation session data class
 */
data class InstallationSession(
    val sessionId: String,
    val dropNumber: String,
    val technicianId: String,
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    val completedSteps: MutableSet<Int> = mutableSetOf(),
    val skippedSteps: MutableMap<Int, String> = mutableMapOf(), // stepId -> reason
    var currentStepId: Int = 1,
    var status: SessionStatus = SessionStatus.ACTIVE
) {

    /**
     * Get session duration in minutes
     */
    val durationMinutes: Long?
        get() = endTime?.let { (it - startTime) / (1000 * 60) }

    /**
     * Check if session is active
     */
    val isActive: Boolean
        get() = status == SessionStatus.ACTIVE

    /**
     * Check if session is completed
     */
    val isCompleted: Boolean
        get() = status == SessionStatus.COMPLETED

    /**
     * Get completion percentage
     */
    fun getCompletionPercentage(totalSteps: Int): Float {
        return if (totalSteps > 0) {
            completedSteps.size.toFloat() / totalSteps.toFloat()
        } else 0f
    }

    /**
     * Mark session as completed
     */
    fun complete() {
        endTime = System.currentTimeMillis()
        status = SessionStatus.COMPLETED
    }

    /**
     * Mark session as failed
     */
    fun fail() {
        endTime = System.currentTimeMillis()
        status = SessionStatus.FAILED
    }

    /**
     * Pause session
     */
    fun pause() {
        status = SessionStatus.PAUSED
    }

    /**
     * Resume session
     */
    fun resume() {
        if (status == SessionStatus.PAUSED) {
            status = SessionStatus.ACTIVE
        }
    }
}

/**
 * Session status enum
 */
enum class SessionStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Installation session manager
 * Handles creation, updates, and persistence of installation sessions
 */
@Singleton
class InstallationSessionManager @Inject constructor() {

    // In-memory storage for demo - in real app, this would use database
    private val sessions = mutableMapOf<String, InstallationSession>()

    /**
     * Create new installation session
     */
    suspend fun createSession(drop: Drop, technicianId: String): Result<InstallationSession> {
        return try {
            val sessionId = generateSessionId()
            val session = InstallationSession(
                sessionId = sessionId,
                dropNumber = drop.dropNumber,
                technicianId = technicianId
            )

            sessions[sessionId] = session
            Result.Success(session)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Get session by ID
     */
    suspend fun getSession(sessionId: String): Result<InstallationSession> {
        return sessions[sessionId]?.let { Result.Success(it) }
            ?: Result.Error(Exception("Session not found: $sessionId"))
    }

    /**
     * Update session
     */
    suspend fun updateSession(session: InstallationSession): Result<Unit> {
        return try {
            sessions[session.sessionId] = session
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Mark step as completed
     */
    suspend fun markStepCompleted(sessionId: String, stepId: Int): Result<Unit> {
        return try {
            val session = sessions[sessionId]
                ?: return Result.Error(Exception("Session not found: $sessionId"))

            session.completedSteps.add(stepId)
            session.currentStepId = stepId + 1

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Mark step as skipped
     */
    suspend fun markStepSkipped(sessionId: String, stepId: Int, reason: String): Result<Unit> {
        return try {
            val session = sessions[sessionId]
                ?: return Result.Error(Exception("Session not found: $sessionId"))

            session.skippedSteps[stepId] = reason
            session.currentStepId = stepId + 1

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Complete session
     */
    suspend fun completeSession(sessionId: String): Result<Unit> {
        return try {
            val session = sessions[sessionId]
                ?: return Result.Error(Exception("Session not found: $sessionId"))

            session.complete()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Get all active sessions for technician
     */
    suspend fun getActiveSessions(technicianId: String): Result<List<InstallationSession>> {
        return try {
            val activeSessions = sessions.values.filter {
                it.technicianId == technicianId && it.isActive
            }
            Result.Success(activeSessions)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Delete session
     */
    suspend fun deleteSession(sessionId: String): Result<Unit> {
        return try {
            sessions.remove(sessionId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Generate unique session ID
     */
    private fun generateSessionId(): String {
        return "session_${UUID.randomUUID().toString().substring(0, 8)}"
    }
}