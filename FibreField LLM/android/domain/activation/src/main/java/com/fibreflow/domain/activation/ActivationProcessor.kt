package com.fibreflow.domain.activation

import com.fibreflow.core.common.result.Result
import com.fibreflow.core.network.api.ActivationAPI
import com.fibreflow.core.network.models.response.ActivationResponse
import com.fibreflow.domain.drops.entities.Drop
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain service for processing activation operations
 * Handles activation scheduling, execution, and monitoring
 */
@Singleton
class ActivationProcessor @Inject constructor(
    private val activationApi: ActivationAPI
) {

    /**
     * Schedule activation for a completed drop
     */
    suspend fun scheduleActivation(
        drop: Drop,
        technicianId: String,
        scheduledDate: Long,
        serviceType: String,
        priority: String = "NORMAL",
        notes: String? = null
    ): Result<ActivationResult> {
        return try {
            Timber.d("Scheduling activation for drop: ${drop.dropNumber}")

            val scheduleRequest = com.fibreflow.core.network.api.ActivationScheduleRequest(
                dropId = drop.dropNumber,
                technicianId = technicianId,
                scheduledDate = scheduledDate,
                priority = priority,
                serviceType = serviceType,
                notes = notes
            )

            val response = activationApi.scheduleActivation(scheduleRequest)

            if (response.isSuccessful) {
                val activationResponse = response.body()
                if (activationResponse != null) {
                    Timber.d("Activation scheduled successfully: ${activationResponse.activationId}")
                    Result.Success(
                        ActivationResult(
                            activationId = activationResponse.activationId,
                            status = activationResponse.status,
                            scheduledDate = activationResponse.scheduledDate
                        )
                    )
                } else {
                    Result.Error(Exception("Schedule activation response was null"))
                }
            } else {
                val errorMsg = "Failed to schedule activation: ${response.code()} ${response.message()}"
                Timber.e(errorMsg)
                Result.Error(Exception(errorMsg))
            }

        } catch (e: Exception) {
            Timber.e(e, "Error scheduling activation")
            Result.Error(e)
        }
    }

    /**
     * Start activation process
     */
    suspend fun startActivation(
        activationId: String,
        technicianId: String,
        equipmentUsed: List<String>? = null
    ): Result<ActivationResult> {
        return try {
            Timber.d("Starting activation: $activationId")

            val startRequest = com.fibreflow.core.network.api.ActivationStartRequest(
                technicianId = technicianId,
                equipmentUsed = equipmentUsed
            )

            val response = activationApi.startActivation(activationId, startRequest)

            if (response.isSuccessful) {
                val activationResponse = response.body()
                if (activationResponse != null) {
                    Timber.d("Activation started successfully: $activationId")
                    Result.Success(
                        ActivationResult(
                            activationId = activationResponse.activationId,
                            status = activationResponse.status,
                            startedAt = activationResponse.startedAt
                        )
                    )
                } else {
                    Result.Error(Exception("Start activation response was null"))
                }
            } else {
                val errorMsg = "Failed to start activation: ${response.code()} ${response.message()}"
                Timber.e(errorMsg)
                Result.Error(Exception(errorMsg))
            }

        } catch (e: Exception) {
            Timber.e(e, "Error starting activation: $activationId")
            Result.Error(e)
        }
    }

    /**
     * Run activation tests
     */
    suspend fun runActivationTests(
        activationId: String,
        testTypes: List<String>,
        priority: String = "normal"
    ): Result<TestExecutionResult> {
        return try {
            Timber.d("Running activation tests for: $activationId")

            val testRequest = com.fibreflow.core.network.api.ActivationTestRequest(
                testTypes = testTypes,
                priority = priority
            )

            val response = activationApi.runActivationTests(activationId, testRequest)

            if (response.isSuccessful) {
                val testResponse = response.body()
                if (testResponse != null) {
                    Timber.d("Tests executed for activation: $activationId")
                    Result.Success(
                        TestExecutionResult(
                            activationId = testResponse.activationId,
                            overallStatus = testResponse.overallStatus,
                            testResults = testResponse.testResults,
                            summary = testResponse.summary
                        )
                    )
                } else {
                    Result.Error(Exception("Test execution response was null"))
                }
            } else {
                val errorMsg = "Failed to run tests: ${response.code()} ${response.message()}"
                Timber.e(errorMsg)
                Result.Error(Exception(errorMsg))
            }

        } catch (e: Exception) {
            Timber.e(e, "Error running activation tests: $activationId")
            Result.Error(e)
        }
    }

    /**
     * Complete activation
     */
    suspend fun completeActivation(
        activationId: String,
        success: Boolean,
        completionNotes: String? = null,
        testResults: Map<String, Boolean>? = null,
        issues: List<String>? = null
    ): Result<ActivationResult> {
        return try {
            Timber.d("Completing activation: $activationId, success: $success")

            val completion = com.fibreflow.core.network.api.ActivationCompletion(
                success = success,
                completionNotes = completionNotes,
                testResults = testResults,
                issues = issues
            )

            val response = activationApi.completeActivation(activationId, completion)

            if (response.isSuccessful) {
                val activationResponse = response.body()
                if (activationResponse != null) {
                    Timber.d("Activation completed successfully: $activationId")
                    Result.Success(
                        ActivationResult(
                            activationId = activationResponse.activationId,
                            status = activationResponse.status,
                            completedAt = activationResponse.completedAt
                        )
                    )
                } else {
                    Result.Error(Exception("Complete activation response was null"))
                }
            } else {
                val errorMsg = "Failed to complete activation: ${response.code()} ${response.message()}"
                Timber.e(errorMsg)
                Result.Error(Exception(errorMsg))
            }

        } catch (e: Exception) {
            Timber.e(e, "Error completing activation: $activationId")
            Result.Error(e)
        }
    }

    /**
     * Get activation status
     */
    suspend fun getActivationStatus(activationId: String): Result<ActivationStatus> {
        return try {
            val response = activationApi.getActivation(activationId)

            if (response.isSuccessful) {
                val activationResponse = response.body()
                if (activationResponse != null) {
                    Result.Success(
                        ActivationStatus(
                            activationId = activationResponse.activationId,
                            status = activationResponse.status,
                            progress = activationResponse.progress,
                            currentStep = determineCurrentStep(activationResponse.status),
                            estimatedCompletion = calculateEstimatedCompletion(activationResponse)
                        )
                    )
                } else {
                    Result.Error(Exception("Get activation response was null"))
                }
            } else {
                val errorMsg = "Failed to get activation status: ${response.code()} ${response.message()}"
                Timber.e(errorMsg)
                Result.Error(Exception(errorMsg))
            }

        } catch (e: Exception) {
            Timber.e(e, "Error getting activation status: $activationId")
            Result.Error(e)
        }
    }

    /**
     * Report activation issue
     */
    suspend fun reportIssue(
        activationId: String,
        issueType: String,
        severity: String,
        description: String,
        testResults: Map<String, Any>? = null
    ): Result<IssueReportResult> {
        return try {
            Timber.d("Reporting issue for activation: $activationId")

            val issueReport = com.fibreflow.core.network.api.ActivationIssueReport(
                issueType = issueType,
                severity = severity,
                description = description,
                testResults = testResults
            )

            val response = activationApi.reportActivationIssue(activationId, issueReport)

            if (response.isSuccessful) {
                val issueResponse = response.body()
                if (issueResponse != null) {
                    Timber.d("Issue reported successfully: ${issueResponse.issueId}")
                    Result.Success(
                        IssueReportResult(
                            issueId = issueResponse.issueId,
                            status = issueResponse.status
                        )
                    )
                } else {
                    Result.Error(Exception("Issue report response was null"))
                }
            } else {
                val errorMsg = "Failed to report issue: ${response.code()} ${response.message()}"
                Timber.e(errorMsg)
                Result.Error(Exception(errorMsg))
            }

        } catch (e: Exception) {
            Timber.e(e, "Error reporting activation issue: $activationId")
            Result.Error(e)
        }
    }

    /**
     * Validate activation prerequisites
     */
    fun validateActivationPrerequisites(drop: Drop): Result<ValidationResult> {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Check if drop is completed
        if (drop.status != com.fibreflow.domain.drops.entities.DropStatus.COMPLETED) {
            errors.add("Drop must be completed before activation")
        }

        // Check if drop has required data
        if (drop.address.isNullOrBlank()) {
            errors.add("Drop address is required for activation")
        }

        // Check coordinates
        if (drop.latitude == 0.0 && drop.longitude == 0.0) {
            warnings.add("Drop coordinates appear to be default values")
        }

        val result = ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )

        return if (result.isValid) Result.Success(result) else Result.Error(Exception("Validation failed: ${errors.joinToString(", ")}"))
    }

    /**
     * Determine current step based on status
     */
    private fun determineCurrentStep(status: String): String {
        return when (status.lowercase()) {
            "scheduled" -> "Scheduled"
            "in_progress", "running" -> "Running Tests"
            "testing" -> "Executing Tests"
            "completed" -> "Completed"
            "failed" -> "Failed"
            else -> "Unknown"
        }
    }

    /**
     * Calculate estimated completion time
     */
    private fun calculateEstimatedCompletion(activation: ActivationResponse): Long? {
        return activation.startedAt?.let { startTime ->
            val elapsed = System.currentTimeMillis() - startTime
            val progress = activation.progress.coerceIn(0.0f, 1.0f)

            if (progress > 0.0f) {
                val totalEstimated = elapsed / progress
                startTime + totalEstimated.toLong()
            } else {
                null
            }
        }
    }
}

/**
 * Result of activation operation
 */
data class ActivationResult(
    val activationId: String,
    val status: String,
    val scheduledDate: Long? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null
)

/**
 * Result of test execution
 */
data class TestExecutionResult(
    val activationId: String,
    val overallStatus: String,
    val testResults: Map<String, com.fibreflow.core.network.api.TestResult>,
    val summary: com.fibreflow.core.network.api.TestSummary
)

/**
 * Activation status information
 */
data class ActivationStatus(
    val activationId: String,
    val status: String,
    val progress: Float,
    val currentStep: String,
    val estimatedCompletion: Long?
)

/**
 * Result of issue reporting
 */
data class IssueReportResult(
    val issueId: String,
    val status: String
)

/**
 * Validation result
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)