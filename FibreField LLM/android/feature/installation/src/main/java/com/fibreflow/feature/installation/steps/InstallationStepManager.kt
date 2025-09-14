package com.fibreflow.feature.installation.steps

import com.fibreflow.core.common.result.Result
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installation step data class
 */
data class InstallationStep(
    val id: Int,
    val name: String,
    val description: String,
    val instructions: String,
    val photoRequirements: List<PhotoRequirement> = emptyList(),
    val validationRules: List<String> = emptyList(),
    val estimatedDurationMinutes: Int = 5,
    var isCompleted: Boolean = false,
    var isSkipped: Boolean = false,
    var skipReason: String? = null
) {

    /**
     * Check if step has photo requirements
     */
    val hasPhotoRequirements: Boolean
        get() = photoRequirements.isNotEmpty()

    /**
     * Get required photo types for this step
     */
    val requiredPhotoTypes: List<String>
        get() = photoRequirements.map { it.photoType }

    /**
     * Check if all photo requirements are satisfied
     */
    fun arePhotoRequirementsSatisfied(capturedPhotos: List<String>): Boolean {
        return photoRequirements.all { requirement ->
            capturedPhotos.contains(requirement.photoType)
        }
    }
}

/**
 * Photo requirement for installation step
 */
data class PhotoRequirement(
    val photoType: String,
    val description: String,
    val isRequired: Boolean = true,
    val qualityRequirements: List<String> = emptyList()
)

/**
 * Installation step manager
 * Defines and manages the 9-step installation workflow
 */
@Singleton
class InstallationStepManager @Inject constructor() {

    private val installationSteps = listOf(
        InstallationStep(
            id = 1,
            name = "Initial Assessment",
            description = "Assess the installation site and ONT equipment",
            instructions = "Take photos of the ONT, power meter, and installation area. Verify all equipment is present and undamaged.",
            photoRequirements = listOf(
                PhotoRequirement(
                    photoType = "ONT_POWER_LIGHT",
                    description = "Photo of ONT power light",
                    qualityRequirements = listOf("Well-lit", "Clear focus", "Light visible")
                ),
                PhotoRequirement(
                    photoType = "POWER_METER_READING",
                    description = "Photo of power meter reading",
                    qualityRequirements = listOf("Digits readable", "No glare")
                )
            ),
            validationRules = listOf(
                "ONT power light must be visible",
                "Power meter reading must be legible",
                "No visible equipment damage"
            ),
            estimatedDurationMinutes = 3
        ),

        InstallationStep(
            id = 2,
            name = "Fiber Connection",
            description = "Connect fiber optic cable to ONT",
            instructions = "Carefully connect the fiber cable to the ONT port. Ensure proper alignment and secure connection.",
            photoRequirements = listOf(
                PhotoRequirement(
                    photoType = "FIBER_CONNECTION",
                    description = "Photo of fiber cable connection",
                    qualityRequirements = listOf("Connection visible", "No damage to cable")
                )
            ),
            validationRules = listOf(
                "Fiber cable properly connected",
                "No visible cable damage",
                "ONT receives signal"
            ),
            estimatedDurationMinutes = 5
        ),

        InstallationStep(
            id = 3,
            name = "ONT Light Verification",
            description = "Verify all ONT indicator lights are functioning",
            instructions = "Check that all required lights on the ONT are illuminated correctly (Power, LOS, PON, LAN).",
            photoRequirements = listOf(
                PhotoRequirement(
                    photoType = "ONT_LOS_LIGHT",
                    description = "Photo of ONT LOS light",
                    qualityRequirements = listOf("Light clearly visible", "Proper color")
                ),
                PhotoRequirement(
                    photoType = "ONT_PON_LIGHT",
                    description = "Photo of ONT PON light",
                    qualityRequirements = listOf("Light clearly visible", "Proper color")
                ),
                PhotoRequirement(
                    photoType = "ONT_LAN_LIGHT",
                    description = "Photo of ONT LAN light",
                    qualityRequirements = listOf("Light clearly visible", "Proper color")
                )
            ),
            validationRules = listOf(
                "Power light: Green/On",
                "LOS light: Off (no signal loss)",
                "PON light: Green/On",
                "LAN light: Green/On (when connected)"
            ),
            estimatedDurationMinutes = 2
        ),

        InstallationStep(
            id = 4,
            name = "Cable Routing",
            description = "Route and secure fiber optic cable",
            instructions = "Properly route the fiber cable from ONT to demarcation point. Secure with appropriate fasteners.",
            photoRequirements = listOf(
                PhotoRequirement(
                    photoType = "CABLE_ROUTING",
                    description = "Photo of cable routing",
                    qualityRequirements = listOf("Cable path visible", "Proper securing")
                )
            ),
            validationRules = listOf(
                "Cable properly secured",
                "No sharp bends or kinks",
                "Adequate cable slack"
            ),
            estimatedDurationMinutes = 8
        ),

        InstallationStep(
            id = 5,
            name = "Splice Closure",
            description = "Install and seal fiber splice closure",
            instructions = "Install the fiber splice in appropriate enclosure and ensure proper sealing to prevent moisture ingress.",
            photoRequirements = listOf(
                PhotoRequirement(
                    photoType = "SPLICE_CLOSURE",
                    description = "Photo of splice closure",
                    qualityRequirements = listOf("Closure properly sealed", "Labels visible")
                )
            ),
            validationRules = listOf(
                "Splice closure properly sealed",
                "All connections secure",
                "Proper labeling applied"
            ),
            estimatedDurationMinutes = 10
        ),

        InstallationStep(
            id = 6,
            name = "ONT Mounting",
            description = "Securely mount ONT in designated location",
            instructions = "Mount the ONT securely using appropriate hardware. Ensure proper ventilation and accessibility.",
            photoRequirements = listOf(
                PhotoRequirement(
                    photoType = "ONT_INSTALLATION",
                    description = "Photo of mounted ONT",
                    qualityRequirements = listOf("Secure mounting", "Proper orientation")
                )
            ),
            validationRules = listOf(
                "ONT securely mounted",
                "Adequate ventilation",
                "Easy access for maintenance"
            ),
            estimatedDurationMinutes = 5
        ),

        InstallationStep(
            id = 7,
            name = "Signal Testing",
            description = "Test and verify signal quality",
            instructions = "Run speed test and verify signal strength. Document test results.",
            photoRequirements = listOf(
                PhotoRequirement(
                    photoType = "SPEED_TEST_RESULTS",
                    description = "Photo of speed test results",
                    qualityRequirements = listOf("Results clearly visible", "All metrics shown")
                )
            ),
            validationRules = listOf(
                "Download speed meets requirements",
                "Upload speed meets requirements",
                "Signal strength adequate",
                "No packet loss"
            ),
            estimatedDurationMinutes = 3
        ),

        InstallationStep(
            id = 8,
            name = "Final Documentation",
            description = "Complete installation documentation",
            instructions = "Document all installation details, serial numbers, and final configuration.",
            photoRequirements = listOf(
                PhotoRequirement(
                    photoType = "FINAL_INSTALLATION",
                    description = "Photo of completed installation",
                    qualityRequirements = listOf("All components visible", "Clean installation")
                )
            ),
            validationRules = listOf(
                "All documentation complete",
                "Serial numbers recorded",
                "Customer sign-off obtained"
            ),
            estimatedDurationMinutes = 5
        ),

        InstallationStep(
            id = 9,
            name = "Quality Assurance",
            description = "Final quality check and cleanup",
            instructions = "Perform final inspection, clean work area, and ensure customer satisfaction.",
            photoRequirements = emptyList(),
            validationRules = listOf(
                "Work area cleaned",
                "No installation debris",
                "Customer satisfied with installation",
                "All tools removed"
            ),
            estimatedDurationMinutes = 3
        )
    )

    /**
     * Get first installation step
     */
    fun getFirstStep(): Result<InstallationStep> {
        return installationSteps.firstOrNull()?.let { Result.Success(it) }
            ?: Result.Error(Exception("No installation steps defined"))
    }

    /**
     * Get next step after current step
     */
    fun getNextStep(currentStepId: Int): Result<InstallationStep> {
        val nextStepId = currentStepId + 1
        return installationSteps.find { it.id == nextStepId }?.let { Result.Success(it) }
            ?: Result.Error(Exception("No next step found"))
    }

    /**
     * Get previous step before current step
     */
    fun getPreviousStep(currentStepId: Int): Result<InstallationStep> {
        val previousStepId = currentStepId - 1
        return installationSteps.find { it.id == previousStepId }?.let { Result.Success(it) }
            ?: Result.Error(Exception("No previous step found"))
    }

    /**
     * Get step by ID
     */
    fun getStepById(stepId: Int): Result<InstallationStep> {
        return installationSteps.find { it.id == stepId }?.let { Result.Success(it) }
            ?: Result.Error(Exception("Step not found: $stepId"))
    }

    /**
     * Get all installation steps
     */
    fun getAllSteps(): List<InstallationStep> {
        return installationSteps
    }

    /**
     * Get total number of steps
     */
    fun getTotalSteps(): Int {
        return installationSteps.size
    }

    /**
     * Check if step ID is valid
     */
    fun isValidStepId(stepId: Int): Boolean {
        return stepId in 1..installationSteps.size
    }

    /**
     * Get steps that require photos
     */
    fun getStepsRequiringPhotos(): List<InstallationStep> {
        return installationSteps.filter { it.hasPhotoRequirements }
    }

    /**
     * Get estimated total duration for all steps
     */
    fun getEstimatedTotalDuration(): Int {
        return installationSteps.sumOf { it.estimatedDurationMinutes }
    }
}