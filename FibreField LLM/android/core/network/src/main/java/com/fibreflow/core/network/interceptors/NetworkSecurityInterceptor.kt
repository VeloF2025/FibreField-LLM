package com.fibreflow.core.network.interceptors

import android.content.Context
import android.provider.Settings
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import javax.inject.Singleton

/**
 * Interceptor for network security measures
 * Adds security headers and validates responses
 */
@Singleton
class NetworkSecurityInterceptor(
    private val context: Context
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Add security headers
        val secureRequest = request.newBuilder()
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("X-App-Version", getAppVersion())
            .addHeader("X-Device-ID", getDeviceId())
            .addHeader("X-Platform", "Android")
            .build()

        val response = chain.proceed(secureRequest)

        // Validate response security
        validateResponse(response)

        return response
    }

    private fun validateResponse(response: Response) {
        // Check for suspicious headers
        val serverHeader = response.header("Server")
        if (serverHeader != null && serverHeader.contains("nginx", ignoreCase = true)) {
            Timber.w("⚠️  Potential security concern: Server header detected")
        }

        // Check content type
        val contentType = response.header("Content-Type")
        if (contentType != null && !isValidContentType(contentType)) {
            Timber.w("⚠️  Unexpected content type: $contentType")
        }
    }

    private fun isValidContentType(contentType: String): Boolean {
        val validTypes = listOf(
            "application/json",
            "text/plain",
            "application/xml",
            "text/xml",
            "application/octet-stream"
        )
        return validTypes.any { contentType.startsWith(it) }
    }

    private fun getAppVersion(): String {
        return com.fibreflow.core.network.BuildConfig.APP_VERSION
    }

    private fun getDeviceId(): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: "unknown-device"
        } catch (e: Exception) {
            Timber.w("Failed to get device ID", e)
            "unknown-device"
        }
    }
}