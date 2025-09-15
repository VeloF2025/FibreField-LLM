package com.fibreflow.core.network.interceptors

import com.fibreflow.core.authentication.TokenManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AuthInterceptorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var authInterceptor: AuthInterceptor
    private lateinit var mockTokenManager: TokenManager
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        mockTokenManager = mock()
        authInterceptor = AuthInterceptor(mockTokenManager)

        client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `test auth interceptor adds authorization header`() = runTest {
        // Given
        val testToken = "test-auth-token-123"
        whenever(mockTokenManager.getValidAccessToken()).thenReturn(testToken)

        mockWebServer.enqueue(MockResponse().setBody("{\"message\": \"success\"}"))

        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()

        // When
        val response = client.newCall(request).execute()

        // Then
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("Bearer $testToken", recordedRequest.headers["Authorization"])
        assertEquals(200, response.code)
    }

    @Test
    fun `test auth interceptor handles missing token gracefully`() = runTest {
        // Given
        whenever(mockTokenManager.getValidAccessToken()).thenReturn(null)

        mockWebServer.enqueue(MockResponse().setBody("{\"message\": \"success\"}"))

        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()

        // When
        val response = client.newCall(request).execute()

        // Then
        val recordedRequest = mockWebServer.takeRequest()
        assertTrue(recordedRequest.headers["Authorization"] == null)
        assertEquals(200, response.code)
    }

    @Test
    fun `test auth interceptor preserves existing headers`() = runTest {
        // Given
        val testToken = "test-auth-token-123"
        whenever(mockTokenManager.getValidAccessToken()).thenReturn(testToken)

        mockWebServer.enqueue(MockResponse().setBody("{\"message\": \"success\"}"))

        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .header("Custom-Header", "custom-value")
            .build()

        // When
        val response = client.newCall(request).execute()

        // Then
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("Bearer $testToken", recordedRequest.headers["Authorization"])
        assertEquals("custom-value", recordedRequest.headers["Custom-Header"])
        assertEquals(200, response.code)
    }

    @Test
    fun `test auth interceptor handles network errors`() = runTest {
        // Given
        val testToken = "test-auth-token-123"
        whenever(mockTokenManager.getValidAccessToken()).thenReturn(testToken)

        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("{\"error\": \"server error\"}"))

        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()

        // When
        val response = client.newCall(request).execute()

        // Then
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("Bearer $testToken", recordedRequest.headers["Authorization"])
        assertEquals(500, response.code)
    }
}