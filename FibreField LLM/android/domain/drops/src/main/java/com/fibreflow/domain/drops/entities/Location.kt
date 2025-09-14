package com.fibreflow.domain.drops.entities

/**
 * Domain entity representing GPS location
 */
data class Location(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    val altitude: Double? = null,
    val bearing: Float? = null,
    val speed: Float? = null,
    val timestamp: Long = System.currentTimeMillis()
) {

    /**
     * Calculate distance to another location using Haversine formula
     */
    fun distanceTo(other: Location): Double {
        val earthRadius = 6371000.0 // meters

        val lat1Rad = Math.toRadians(latitude)
        val lat2Rad = Math.toRadians(other.latitude)
        val deltaLatRad = Math.toRadians(other.latitude - latitude)
        val deltaLonRad = Math.toRadians(other.longitude - longitude)

        val a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }

    /**
     * Check if location is within specified radius of another location
     */
    fun isWithinRadius(other: Location, radiusMeters: Double): Boolean {
        return distanceTo(other) <= radiusMeters
    }

    /**
     * Get formatted coordinates string
     */
    val coordinates: String
        get() = "%.6f, %.6f".format(latitude, longitude)

    /**
     * Check if location has good accuracy (< 10 meters)
     */
    val hasGoodAccuracy: Boolean
        get() = accuracy != null && accuracy < 10.0f

    /**
     * Check if location is recent (< 5 minutes old)
     */
    val isRecent: Boolean
        get() = (System.currentTimeMillis() - timestamp) < (5 * 60 * 1000)
}