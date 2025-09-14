package com.fibreflow.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.fibreflow.core.common.result.Result
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Service for handling device location operations
 * Provides GPS location retrieval with proper permissions handling
 */
@Singleton
class LocationService @Inject constructor(
    private val context: Context
) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /**
     * Get current device location with high accuracy
     * @param timeoutMs Timeout in milliseconds (default 30 seconds)
     * @return Result with Location or error
     */
    suspend fun getCurrentLocation(timeoutMs: Long = 30000): Result<Location> {
        return try {
            // Check permissions
            if (!hasLocationPermissions()) {
                return Result.Error(SecurityException("Location permissions not granted"))
            }

            // Check if location services are enabled
            if (!isLocationEnabled()) {
                return Result.Error(Exception("Location services are disabled"))
            }

            // Get last known location first
            val lastLocation = getLastKnownLocation()
            if (lastLocation != null && isLocationFresh(lastLocation)) {
                return Result.Success(lastLocation)
            }

            // Request fresh location
            getFreshLocation(timeoutMs)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Get last known location from any provider
     */
    private suspend fun getLastKnownLocation(): Location? {
        return try {
            val location = withTimeoutOrNull(5000) {
                suspendCancellableCoroutine { continuation ->
                    try {
                        fusedLocationClient.lastLocation
                            .addOnSuccessListener { location ->
                                continuation.resume(location)
                            }
                            .addOnFailureListener { exception ->
                                continuation.resume(null)
                            }
                    } catch (e: Exception) {
                        continuation.resume(null)
                    }
                }
            }
            location
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Request fresh location with high priority
     */
    private suspend fun getFreshLocation(timeoutMs: Long): Result<Location> {
        return try {
            val location = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    try {
                        fusedLocationClient.getCurrentLocation(
                            Priority.PRIORITY_HIGH_ACCURACY,
                            null
                        ).addOnSuccessListener { location ->
                            if (location != null) {
                                continuation.resume(location)
                            } else {
                                continuation.resume(null)
                            }
                        }.addOnFailureListener { exception ->
                            continuation.resume(null)
                        }
                    } catch (e: Exception) {
                        continuation.resume(null)
                    }
                }
            }

            if (location != null) {
                Result.Success(location)
            } else {
                Result.Error(Exception("Unable to get location within timeout"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Check if location permissions are granted
     */
    private fun hasLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if location services are enabled
     */
    private fun isLocationEnabled(): Boolean {
        return try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if location is fresh (within last 5 minutes)
     */
    private fun isLocationFresh(location: Location): Boolean {
        val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)
        return location.time > fiveMinutesAgo
    }

    /**
     * Calculate distance between two locations in meters
     */
    fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    /**
     * Check if device is within specified radius of target location
     */
    fun isWithinRadius(
        currentLat: Double,
        currentLon: Double,
        targetLat: Double,
        targetLon: Double,
        radiusMeters: Float
    ): Boolean {
        val distance = calculateDistance(currentLat, currentLon, targetLat, targetLon)
        return distance <= radiusMeters
    }
}