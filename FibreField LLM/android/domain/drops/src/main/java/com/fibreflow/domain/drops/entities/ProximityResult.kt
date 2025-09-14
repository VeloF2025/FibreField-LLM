package com.fibreflow.domain.drops.entities

/**
 * Domain entity representing proximity validation result
 */
data class ProximityResult(
    val dropNumber: String,
    val userLocation: Location,
    val dropLocation: Location,
    val distance: Double,
    val requiredRadius: Double = 50.0, // 50 meters default
    val isWithinRange: Boolean = distance <= requiredRadius,
    val accuracy: Float? = userLocation.accuracy,
    val timestamp: Long = System.currentTimeMillis()
) {

    /**
     * Get distance in human readable format
     */
    val distanceText: String
        get() = when {
            distance < 1000 -> "%.0f meters".format(distance)
            else -> "%.1f km".format(distance / 1000)
        }

    /**
     * Check if location accuracy is sufficient
     */
    val hasSufficientAccuracy: Boolean
        get() = accuracy == null || accuracy <= requiredRadius.toFloat()

    /**
     * Get validation status
     */
    val validationStatus: ProximityValidationStatus
        get() = when {
            !isWithinRange -> ProximityValidationStatus.TOO_FAR
            !hasSufficientAccuracy -> ProximityValidationStatus.INSUFFICIENT_ACCURACY
            else -> ProximityValidationStatus.VALID
        }

    /**
     * Check if validation passed
     */
    val isValid: Boolean
        get() = validationStatus == ProximityValidationStatus.VALID

    /**
     * Get validation message
     */
    val validationMessage: String
        get() = when (validationStatus) {
            ProximityValidationStatus.VALID -> "Location validated successfully"
            ProximityValidationStatus.TOO_FAR -> "Too far from drop location (${distanceText})"
            ProximityValidationStatus.INSUFFICIENT_ACCURACY -> "Location accuracy insufficient (${accuracy?.toInt()}m)"
        }
}

/**
 * Proximity validation status enum
 */
enum class ProximityValidationStatus {
    VALID,
    TOO_FAR,
    INSUFFICIENT_ACCURACY
}