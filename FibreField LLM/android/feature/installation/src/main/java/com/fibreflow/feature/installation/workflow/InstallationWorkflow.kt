package com.fibreflow.feature.installation.workflow

import com.fibreflow.core.common.result.Result
import com.fibreflow.domain.drops.entities.Drop
import com.fibreflow.feature.installation.steps.InstallationStep
import com.fibreflow.feature.installation.steps.InstallationStepManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Main installation workflow orchestrator
 * Manages the complete 9-step installation process
 */
@Singleton
class InstallationWorkflow @Inject constructor(
    private val stepManager: InstallationStepManager,
    private val sessionManager: InstallationSessionManager
) {

    private val _currentStep = MutableStateFlow<InstallationStep?>(null)
    val currentStep: StateFlow<InstallationStep?> = _currentStep.asStateFlow()

    private val _workflowState = MutableStateFlow(WorkflowState.NOT_STARTED)
    val workflowState: StateFlow<WorkflowState> = _workflowState.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private var currentSession: InstallationSession? = null

    /**
     * Start installation workflow for a drop
     */
    suspend fun startInstallation(drop: Drop, technicianId: String): Result<Unit> {
        return try {
            Timber.d("Starting installation workflow for drop: ${drop.dropNumber}")

            // Create installation session
            val sessionResult = sessionManager.createSession(drop, technicianId)
            if (sessionResult is Result.Error) {
                return sessionResult
            }

            currentSession = (sessionResult as Result.Success).data
            _workflowState.value = WorkflowState.IN_PROGRESS

            // Get first step
            val firstStepResult = stepManager.getFirstStep()
            if (firstStepResult is Result.Success) {
                _currentStep.value = firstStepResult.data
                updateProgress()
                Timber.d("Installation workflow started, first step: ${firstStepResult.data.name}")
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error starting installation workflow")
            _workflowState.value = WorkflowState.ERROR
            Result.Error(e)
        }
    }

    /**
     * Complete current step and move to next
     */
    suspend fun completeCurrentStep(): Result<Unit> {
        return try {
            val currentStepValue = _currentStep.value
            if (currentStepValue == null) {
                return Result.Error(Exception("No current step"))
            }

            // Mark current step as completed in session
            currentSession?.let { session ->
                sessionManager.markStepCompleted(session.sessionId, currentStepValue.id)
            }

            // Get next step
            val nextStepResult = stepManager.getNextStep(currentStepValue.id)
            if (nextStepResult is Result.Success) {
                val nextStep = nextStepResult.data
                _currentStep.value = nextStep
                updateProgress()
                Timber.d("Completed step ${currentStepValue.name}, moving to ${nextStep.name}")
            } else {
                // No more steps, workflow complete
                _workflowState.value = WorkflowState.COMPLETED
                _currentStep.value = null
                _progress.value = 1.0f
                Timber.d("Installation workflow completed")
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error completing current step")
            Result.Error(e)
        }
    }

    /**
     * Skip current step (with reason)
     */
    suspend fun skipCurrentStep(reason: String): Result<Unit> {
        return try {
            val currentStepValue = _currentStep.value
            if (currentStepValue == null) {
                return Result.Error(Exception("No current step"))
            }

            Timber.d("Skipping step ${currentStepValue.name}, reason: $reason")

            // Mark step as skipped in session
            currentSession?.let { session ->
                sessionManager.markStepSkipped(session.sessionId, currentStepValue.id, reason)
            }

            // Move to next step
            return completeCurrentStep()
        } catch (e: Exception) {
            Timber.e(e, "Error skipping current step")
            Result.Error(e)
        }
    }

    /**
     * Go back to previous step
     */
    suspend fun goToPreviousStep(): Result<Unit> {
        return try {
            val currentStepValue = _currentStep.value
            if (currentStepValue == null) {
                return Result.Error(Exception("No current step"))
            }

            val previousStepResult = stepManager.getPreviousStep(currentStepValue.id)
            if (previousStepResult is Result.Success) {
                _currentStep.value = previousStepResult.data
                updateProgress()
                Timber.d("Went back to step ${previousStepResult.data.name}")
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error going to previous step")
            Result.Error(e)
        }
    }

    /**
     * Pause current workflow
     */
    suspend fun pauseWorkflow(): Result<Unit> {
        return try {
            _workflowState.value = WorkflowState.PAUSED
            Timber.d("Installation workflow paused")
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error pausing workflow")
            Result.Error(e)
        }
    }

    /**
     * Resume paused workflow
     */
    suspend fun resumeWorkflow(): Result<Unit> {
        return try {
            if (_workflowState.value == WorkflowState.PAUSED) {
                _workflowState.value = WorkflowState.IN_PROGRESS
                Timber.d("Installation workflow resumed")
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error resuming workflow")
            Result.Error(e)
        }
    }

    /**
     * Cancel current workflow
     */
    suspend fun cancelWorkflow(): Result<Unit> {
        return try {
            _workflowState.value = WorkflowState.CANCELLED
            _currentStep.value = null
            _progress.value = 0f
            currentSession = null
            Timber.d("Installation workflow cancelled")
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error cancelling workflow")
            Result.Error(e)
        }
    }

    /**
     * Get current session information
     */
    fun getCurrentSession(): InstallationSession? {
        return currentSession
    }

    /**
     * Check if workflow can proceed to next step
     */
    fun canProceedToNextStep(): Boolean {
        val currentStepValue = _currentStep.value
        return currentStepValue != null && currentStepValue.isCompleted
    }

    /**
     * Update workflow progress
     */
    private fun updateProgress() {
        val currentStepValue = _currentStep.value
        if (currentStepValue != null) {
            _progress.value = (currentStepValue.id - 1).toFloat() / stepManager.getTotalSteps().toFloat()
        }
    }
}

/**
 * Workflow state enum
 */
enum class WorkflowState {
    NOT_STARTED,
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
    CANCELLED,
    ERROR
}