package com.fibreflow.core.network.interceptors

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import javax.inject.Singleton

/**
 * Interceptor for logging network requests and responses
 * Only logs in debug builds for security
 */
@Singleton
class LoggingInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Log request
        Timber.d("🌐 REQUEST: ${request.method} ${request.url}")
        Timber.d("🌐 Headers: ${request.headers}")

        val startTime = System.nanoTime()
        val response = chain.proceed(request)
        val endTime = System.nanoTime()

        val duration = (endTime - startTime) / 1_000_000 // Convert to milliseconds

        // Log response
        Timber.d("🌐 RESPONSE: ${response.code} ${response.message} (${duration}ms)")
        Timber.d("🌐 URL: ${response.request.url}")

        return response
    }
}