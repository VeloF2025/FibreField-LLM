package com.fibreflow.domain.authentication

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.fibreflow.core.common.result.Result
import com.fibreflow.domain.authentication.entities.BiometricCredentials
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Biometric Manager for fingerprint and face unlock authentication
 */
@Singleton
class BiometricManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "BiometricManager"
        private const val KEYSTORE_ALIAS = "fibrefield_biometric_key"
        private const val PREFS_NAME = "fibrefield_biometric_prefs"
        private const val KEY_TECHNICIAN_ID = "technician_id"
        private const val KEY_ENROLLED_AT = "enrolled_at"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    private val biometricManager = BiometricManager.from(context)
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /**
     * Check if biometric authentication is available on this device
     */
    fun isBiometricAvailable(): Boolean {
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> false
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> false
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> false
            else -> false
        }
    }

    /**
     * Get biometric availability status
     */
    fun getBiometricAvailabilityStatus(): BiometricAvailability {
        val status = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)

        return when (status) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Available
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NotSupported
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HardwareUnavailable
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NotEnrolled
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricAvailability.SecurityUpdateRequired
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> BiometricAvailability.NotSupported
            else -> BiometricAvailability.Unknown
        }
    }

    /**
     * Check if biometric credentials are stored for current user
     */
    fun hasStoredCredentials(): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val technicianId = prefs.getString(KEY_TECHNICIAN_ID, null)
            val enrolledAt = prefs.getLong(KEY_ENROLLED_AT, 0L)

            technicianId != null && enrolledAt > 0L

        } catch (e: Exception) {
            Log.e(TAG, "Failed to check stored credentials", e)
            false
        }
    }

    /**
     * Store biometric credentials for user
     */
    fun storeCredentials(credentials: BiometricCredentials) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_TECHNICIAN_ID, credentials.technicianId)
                .putLong(KEY_ENROLLED_AT, credentials.enrolledAt)
                .apply()

            Log.d(TAG, "Biometric credentials stored for technician: ${credentials.technicianId}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to store biometric credentials", e)
        }
    }

    /**
     * Get stored biometric credentials
     */
    fun getStoredCredentials(): BiometricCredentials? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val technicianId = prefs.getString(KEY_TECHNICIAN_ID, null)
            val enrolledAt = prefs.getLong(KEY_ENROLLED_AT, 0L)

            if (technicianId != null && enrolledAt > 0L) {
                BiometricCredentials(technicianId, enrolledAt)
            } else {
                null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get stored credentials", e)
            null
        }
    }

    /**
     * Clear stored biometric credentials
     */
    fun clearStoredCredentials() {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .remove(KEY_TECHNICIAN_ID)
                .remove(KEY_ENROLLED_AT)
                .apply()

            Log.d(TAG, "Biometric credentials cleared")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear biometric credentials", e)
        }
    }

    /**
     * Perform biometric authentication
     */
    suspend fun authenticate(activity: FragmentActivity): Result<Unit> = withContext(Dispatchers.Main) {
        try {
            if (!isBiometricAvailable()) {
                return@withContext Result.Error(RuntimeException("Biometric authentication not available"))
            }

            if (!hasStoredCredentials()) {
                return@withContext Result.Error(RuntimeException("No biometric credentials enrolled"))
            }

            val result = performBiometricPrompt(activity).first()

            when (result) {
                is BiometricResult.Success -> {
                    Log.i(TAG, "Biometric authentication successful")
                    Result.Success(Unit)
                }
                is BiometricResult.Error -> {
                    Log.e(TAG, "Biometric authentication failed: ${result.message}")
                    Result.Error(RuntimeException(result.message))
                }
                is BiometricResult.Cancelled -> {
                    Log.i(TAG, "Biometric authentication cancelled")
                    Result.Error(RuntimeException("Authentication cancelled"))
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Biometric authentication error", e)
            Result.Error(e)
        }
    }

    /**
     * Create biometric prompt for enrollment
     */
    fun createEnrollmentPrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit
    ): BiometricPrompt {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Enable Biometric Login")
            .setSubtitle("Use your fingerprint or face to login")
            .setDescription("This will allow you to login quickly without entering your password")
            .setNegativeButtonText("Use Password")
            .build()

        return BiometricPrompt(activity, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.i(TAG, "Biometric enrollment successful")
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.e(TAG, "Biometric enrollment error: $errString")
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.w(TAG, "Biometric enrollment failed")
                // Don't call error for failed attempts, just retry
            }

            override fun onAuthenticationHelp(helpCode: Int, helpString: CharSequence) {
                super.onAuthenticationHelp(helpCode, helpString)
                Log.d(TAG, "Biometric enrollment help: $helpString")
            }
        })
    }

    /**
     * Get biometric authentication prompt
     */
    private fun performBiometricPrompt(activity: FragmentActivity) = callbackFlow<BiometricResult> {
        val cipher = getCipherForBiometric()
        if (cipher == null) {
            trySend(BiometricResult.Error("Failed to create biometric cipher"))
            close()
            return@callbackFlow
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Login with Biometric")
            .setSubtitle("Use your fingerprint or face")
            .setNegativeButtonText("Use Password")
            .build()

        val biometricPrompt = BiometricPrompt(activity, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                trySend(BiometricResult.Success)
                close()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                trySend(BiometricResult.Error(errString.toString()))
                close()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Don't send error for failed attempts, biometric prompt handles retries
                Log.d(TAG, "Biometric authentication failed, retrying")
            }

            override fun onAuthenticationHelp(helpCode: Int, helpString: CharSequence) {
                super.onAuthenticationHelp(helpCode, helpString)
                Log.d(TAG, "Biometric authentication help: $helpString")
            }
        })

        biometricPrompt.authenticate(promptInfo)

        awaitClose {
            // Cleanup if needed
        }
    }

    /**
     * Create cipher for biometric authentication
     */
    private fun getCipherForBiometric(): Cipher? {
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            cipher
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create biometric cipher", e)
            null
        }
    }

    /**
     * Get or create secret key for biometric authentication
     */
    private fun getOrCreateSecretKey(): SecretKey {
        return if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey
        } else {
            createSecretKey()
        }
    }

    /**
     * Create new secret key for biometric authentication
     */
    private fun createSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Get biometric enrollment status
     */
    fun getBiometricStatus(): BiometricStatus {
        val availability = getBiometricAvailabilityStatus()
        val hasCredentials = hasStoredCredentials()

        return BiometricStatus(
            availability = availability,
            isEnrolled = hasCredentials,
            canAuthenticate = availability == BiometricAvailability.Available && hasCredentials
        )
    }
}

/**
 * Biometric availability status
 */
enum class BiometricAvailability {
    Available,
    NotSupported,
    HardwareUnavailable,
    NotEnrolled,
    SecurityUpdateRequired,
    Unknown
}

/**
 * Biometric authentication result
 */
sealed class BiometricResult {
    object Success : BiometricResult()
    data class Error(val message: String) : BiometricResult()
    object Cancelled : BiometricResult()
}

/**
 * Biometric status information
 */
data class BiometricStatus(
    val availability: BiometricAvailability,
    val isEnrolled: Boolean,
    val canAuthenticate: Boolean
)