package com.fibreflow.domain.authentication

import com.fibreflow.core.authentication.TokenManager
import com.fibreflow.core.network.api.AuthenticationAPI
import com.fibreflow.core.network.models.request.LoginRequest
import com.fibreflow.core.network.models.response.AuthResponse
import com.fibreflow.domain.authentication.entities.AuthToken
import com.fibreflow.domain.authentication.repositories.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticationServiceTest {

    private lateinit var authenticationService: AuthenticationService
    private lateinit var mockAuthRepository: AuthRepository
    private lateinit var mockTokenManager: TokenManager
    private lateinit var mockAuthApi: AuthenticationAPI

    @Before
    fun setUp() {
        mockAuthRepository = mock()
        mockTokenManager = mock()
        mockAuthApi = mock()

        authenticationService = AuthenticationService(
            authRepository = mockAuthRepository,
            tokenManager = mockTokenManager,
            authApi = mockAuthApi
        )
    }

    @Test
    fun `test login successfully`() = runTest {
        // Given
        val username = "testuser"
        val password = "testpass"
        val authResponse = AuthResponse(
            accessToken = "access-token-123",
            refreshToken = "refresh-token-456",
            expiresIn = 3600,
            tokenType = "Bearer"
        )
        
        whenever(mockAuthApi.login(LoginRequest(username, password)))
            .thenReturn(Response.success(authResponse))

        // When
        val result = authenticationService.login(username, password)

        // Then
        assertTrue(result is com.fibreflow.core.common.result.Result.Success)
        verify(mockTokenManager).saveTokens(
            AuthToken(
                accessToken = "access-token-123",
                refreshToken = "refresh-token-456",
                expiresAt = any()
            )
        )
    }

    @Test
    fun `test login with invalid credentials`() = runTest {
        // Given
        val username = "testuser"
        val password = "wrongpass"
        
        whenever(mockAuthApi.login(LoginRequest(username, password)))
            .thenReturn(Response.error(401, okhttp3.ResponseBody.create(null, "")))

        // When
        val result = authenticationService.login(username, password)

        // Then
        assertTrue(result is com.fibreflow.core.common.result.Result.Error)
        assertEquals("Authentication failed", (result as com.fibreflow.core.common.result.Result.Error).exception.message)
    }

    @Test
    fun `test logout clears tokens`() = runTest {
        // Given - service is initialized

        // When
        authenticationService.logout()

        // Then
        verify(mockTokenManager).clearTokens()
        verify(mockAuthRepository).clearSession()
    }

    @Test
    fun `test isLoggedIn returns true when token valid`() = runTest {
        // Given
        whenever(mockTokenManager.isTokenValid()).thenReturn(true)

        // When
        val isLoggedIn = authenticationService.isLoggedIn()

        // Then
        assertTrue(isLoggedIn)
    }

    @Test
    fun `test isLoggedIn returns false when token invalid`() = runTest {
        // Given
        whenever(mockTokenManager.isTokenValid()).thenReturn(false)

        // When
        val isLoggedIn = authenticationService.isLoggedIn()

        // Then
        assertTrue(!isLoggedIn)
    }

    @Test
    fun `test refresh token successfully`() = runTest {
        // Given
        val authResponse = AuthResponse(
            accessToken = "new-access-token-789",
            refreshToken = "new-refresh-token-012",
            expiresIn = 3600,
            tokenType = "Bearer"
        )
        
        whenever(mockAuthApi.refreshToken("refresh-token-456"))
            .thenReturn(Response.success(authResponse))

        whenever(mockTokenManager.getRefreshToken()).thenReturn("refresh-token-456")

        // When
        val result = authenticationService.refreshToken()

        // Then
        assertTrue(result is com.fibreflow.core.common.result.Result.Success)
        verify(mockTokenManager).saveTokens(
            AuthToken(
                accessToken = "new-access-token-789",
                refreshToken = "new-refresh-token-012",
                expiresAt = any()
            )
        )
    }

    @Test
    fun `test refresh token fails when no refresh token`() = runTest {
        // Given
        whenever(mockTokenManager.getRefreshToken()).thenReturn(null)

        // When
        val result = authenticationService.refreshToken()

        // Then
        assertTrue(result is com.fibreflow.core.common.result.Result.Error)
        assertEquals("No refresh token available", (result as com.fibreflow.core.common.result.Result.Error).exception.message)
    }
}