package com.fibreflow.core.location

import com.fibreflow.core.common.result.Result
import com.fibreflow.domain.drops.entities.Drop
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for detecting proximity to drop locations
 * Validates if technician is within acceptable distance of installation point
 */
@Singleton
class ProximityDetector @Inject constructor(
    private val locationService: LocationService
) {

    companion object {
        // Default proximity thresholds in meters
        const val DEFAULT_PROXIMITY_RADIUS = 50f  // 50 meters
        const val STRICT_PROXIMITY_RADIUS = 25f   // 25 meters for critical validations
        const val LOOSE_PROXIMITY_RADIUS = 100f   // 100 meters for initial checks
    }

    /**
     * Check if technician is within proximity of a drop location
     * @param drop The drop to check proximity for
     * @param radiusMeters Optional custom radius, defaults to DEFAULT_PROXIMITY_RADIUS
     * @return Result with proximity validation result
     */
    suspend fun validateProximity(
        drop: Drop,
        radiusMeters: Float = DEFAULT_PROXIMITY_RADIUS
    ): Result<ProximityResult> {
        return try {
            val currentLocationResult = locationService.getCurrentLocation()

            if (currentLocationResult is Result.Error) {
                return Result.Error(currentLocationResult.exception)
            }

            val currentLocation = (currentLocationResult as Result.Success).data

            val distance = locationService.calculateDistance(
                currentLocation.latitude,
                currentLocation.longitude,
                drop.latitude,
                drop.longitude
            )

            val isWithinRadius = distance <= radiusMeters

            val result = ProximityResult(
                isWithinProximity = isWithinRadius,
                distanceMeters = distance,
                requiredRadiusMeters = radiusMeters,
                currentLatitude = currentLocation.latitude,
                currentLongitude = currentLocation.longitude,
                targetLatitude = drop.latitude,
                targetLongitude = drop.longitude,
                accuracy = currentLocation.accuracy
            )

            Result.Success(result)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Check proximity for multiple drops
     * @param drops List of drops to check
     * @param radiusMeters Proximity radius
     * @return Map of drop numbers to proximity results
     */
    suspend fun validateMultipleProximities(
        drops: List<Drop>,
        radiusMeters: Float = DEFAULT_PROXIMITY_RADIUS
    ): Result<Map<String, ProximityResult>> {
        return try {
            val currentLocationResult = locationService.getCurrentLocation()

            if (currentLocationResult is Result.Error) {
                return Result.Error(currentLocationResult.exception)
            }

            val currentLocation = (currentLocationResult as Result.Success).data

            val results = drops.associate { drop ->
                val distance = locationService.calculateDistance(
                    currentLocation.latitude,
                    currentLocation.longitude,
                    drop.latitude,
                    drop.longitude
                )

                val isWithinRadius = distance <= radiusMeters

                drop.dropNumber to ProximityResult(
                    isWithinProximity = isWithinRadius,
                    distanceMeters = distance,
                    requiredRadiusMeters = radiusMeters,
                    currentLatitude = currentLocation.latitude,
                    currentLongitude = currentLocation.longitude,
                    targetLatitude = drop.latitude,
                    targetLongitude = drop.longitude,
                    accuracy = currentLocation.accuracy
                )
            }

            Result.Success(results)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Get the closest drop from current location
     * @param drops List of available drops
     * @return Result with closest drop and distance
     */
    suspend fun findClosestDrop(drops: List<Drop>): Result<ClosestDropResult> {
        return try {
            if (drops.isEmpty()) {
                return Result.Error(Exception("No drops provided"))
            }

            val currentLocationResult = locationService.getCurrentLocation()

            if (currentLocationResult is Result.Error) {
                return Result.Error(currentLocationResult.exception)
            }

            val currentLocation = (currentLocationResult as Result.Success).data

            var closestDrop: Drop? = null
            var closestDistance = Float.MAX_VALUE

            for (drop in drops) {
                val distance = locationService.calculateDistance(
                    currentLocation.latitude,
                    currentLocation.longitude,
                    drop.latitude,
                    drop.longitude
                )

                if (distance < closestDistance) {
                    closestDistance = distance
                    closestDrop = drop
                }
            }

            if (closestDrop == null) {
                return Result.Error(Exception("Unable to determine closest drop"))
            }

            val result = ClosestDropResult(
                drop = closestDrop,
                distanceMeters = closestDistance,
                currentLatitude = currentLocation.latitude,
                currentLongitude = currentLocation.longitude
            )

            Result.Success(result)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Check if current location is within a specific geographic boundary
     * @param boundaries List of boundary points (lat, lon pairs)
     * @return Result with boundary check result
     */
    suspend fun isWithinBoundary(boundaries: List<Pair<Double, Double>>): Result<Boolean> {
        return try {
            val currentLocationResult = locationService.getCurrentLocation()

            if (currentLocationResult is Result.Error) {
                return Result.Error(currentLocationResult.exception)
            }

            val currentLocation = (currentLocationResult as Result.Success).data

            // Simple point-in-polygon check using ray casting algorithm
            val result = isPointInPolygon(
                currentLocation.latitude,
                currentLocation.longitude,
                boundaries
            )

            Result.Success(result)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Ray casting algorithm to check if point is inside polygon
     */
    private fun isPointInPolygon(
        lat: Double,
        lon: Double,
        boundaries: List<Pair<Double, Double>>
    ): Boolean {
        if (boundaries.size < 3) return false

        var inside = false
        var j = boundaries.size - 1

        for (i in boundaries.indices) {
            val (latI, lonI) = boundaries[i]
            val (latJ, lonJ) = boundaries[j]

            if (((lonI > lon) != (lonJ > lon)) &&
                (lat < (latJ - latI) * (lon - lonI) / (lonJ - lonI) + latI)) {
                inside = !inside
            }
            j = i
        }

        return inside
    }
}

/**
 * Result of proximity validation
 */
data class ProximityResult(
    val isWithinProximity: Boolean,
    val distanceMeters: Float,
    val requiredRadiusMeters: Float,
    val currentLatitude: Double,
    val currentLongitude: Double,
    val targetLatitude: Double,
    val targetLongitude: Double,
    val accuracy: Float?
) {
    val distanceText: String
        get() = "%.1f meters".format(distanceMeters)

    val isAccurate: Boolean
        get() = accuracy != null && accuracy <= 20f  // Within 20 meters accuracy
}

/**
 * Result for finding closest drop
 */
data class ClosestDropResult(
    val drop: Drop,
    val distanceMeters: Float,
    val currentLatitude: Double,
    val currentLongitude: Double
) {
    val distanceText: String
        get() = "%.1f meters".format(distanceMeters)
}