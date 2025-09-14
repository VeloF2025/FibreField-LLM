package com.fibreflow.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.fibreflow.core.common.result.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Key Manager for secure cryptographic key management
 * Provides secure storage and retrieval of encryption keys using Android Keystore
 */
@Singleton
class KeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "KeyManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DATABASE_KEY_ALIAS = "fibrefield_db_key"
        private const val API_KEY_ALIAS = "fibrefield_api_key"
        private const val TOKEN_KEY_ALIAS = "fibrefield_token_key"
        private const val KEY_SIZE = 256
    }

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /**
     * Generate database encryption key
     */
    suspend fun generateDatabaseKey(): Result<SecretKey> = withContext(Dispatchers.IO) {
        try {
            if (keyStore.containsAlias(DATABASE_KEY_ALIAS)) {
                // Key already exists, retrieve it
                val existingKey = keyStore.getKey(DATABASE_KEY_ALIAS, null) as SecretKey
                return@withContext Result.Success(existingKey)
            }

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                DATABASE_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setKeySize(KEY_SIZE)
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(keyGenParameterSpec)
            val secretKey = keyGenerator.generateKey()

            Log.i(TAG, "Database encryption key generated successfully")
            Result.Success(secretKey)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate database key", e)
            Result.Error(e)
        }
    }

    /**
     * Generate API communication key
     */
    suspend fun generateApiKey(): Result<SecretKey> = withContext(Dispatchers.IO) {
        try {
            if (keyStore.containsAlias(API_KEY_ALIAS)) {
                val existingKey = keyStore.getKey(API_KEY_ALIAS, null) as SecretKey
                return@withContext Result.Success(existingKey)
            }

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                API_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setKeySize(KEY_SIZE)
                .setUserAuthenticationRequired(false)
                .build()

            keyGenerator.init(keyGenParameterSpec)
            val secretKey = keyGenerator.generateKey()

            Log.i(TAG, "API communication key generated successfully")
            Result.Success(secretKey)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate API key", e)
            Result.Error(e)
        }
    }

    /**
     * Generate token encryption key
     */
    suspend fun generateTokenKey(): Result<SecretKey> = withContext(Dispatchers.IO) {
        try {
            if (keyStore.containsAlias(TOKEN_KEY_ALIAS)) {
                val existingKey = keyStore.getKey(TOKEN_KEY_ALIAS, null) as SecretKey
                return@withContext Result.Success(existingKey)
            }

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                TOKEN_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setKeySize(KEY_SIZE)
                .setUserAuthenticationRequired(false)
                .build()

            keyGenerator.init(keyGenParameterSpec)
            val secretKey = keyGenerator.generateKey()

            Log.i(TAG, "Token encryption key generated successfully")
            Result.Success(secretKey)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate token key", e)
            Result.Error(e)
        }
    }

    /**
     * Get database key
     */
    fun getDatabaseKey(): Result<SecretKey> {
        return try {
            if (!keyStore.containsAlias(DATABASE_KEY_ALIAS)) {
                return Result.Error(IllegalStateException("Database key not found"))
            }

            val key = keyStore.getKey(DATABASE_KEY_ALIAS, null) as SecretKey
            Result.Success(key)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get database key", e)
            Result.Error(e)
        }
    }

    /**
     * Get API key
     */
    fun getApiKey(): Result<SecretKey> {
        return try {
            if (!keyStore.containsAlias(API_KEY_ALIAS)) {
                return Result.Error(IllegalStateException("API key not found"))
            }

            val key = keyStore.getKey(API_KEY_ALIAS, null) as SecretKey
            Result.Success(key)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get API key", e)
            Result.Error(e)
        }
    }

    /**
     * Get token key
     */
    fun getTokenKey(): Result<SecretKey> {
        return try {
            if (!keyStore.containsAlias(TOKEN_KEY_ALIAS)) {
                return Result.Error(IllegalStateException("Token key not found"))
            }

            val key = keyStore.getKey(TOKEN_KEY_ALIAS, null) as SecretKey
            Result.Success(key)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get token key", e)
            Result.Error(e)
        }
    }

    /**
     * Check if all required keys exist
     */
    fun hasAllKeys(): Boolean {
        return keyStore.containsAlias(DATABASE_KEY_ALIAS) &&
               keyStore.containsAlias(API_KEY_ALIAS) &&
               keyStore.containsAlias(TOKEN_KEY_ALIAS)
    }

    /**
     * Clear all keys (use with caution - requires re-initialization)
     */
    suspend fun clearAllKeys(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val aliases = listOf(DATABASE_KEY_ALIAS, API_KEY_ALIAS, TOKEN_KEY_ALIAS)
            aliases.forEach { alias ->
                if (keyStore.containsAlias(alias)) {
                    keyStore.deleteEntry(alias)
                }
            }

            Log.i(TAG, "All keys cleared successfully")
            Result.Success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear keys", e)
            Result.Error(e)
        }
    }

    /**
     * Get key information for diagnostics
     */
    fun getKeyInfo(): KeyInfo {
        val dbKeyExists = keyStore.containsAlias(DATABASE_KEY_ALIAS)
        val apiKeyExists = keyStore.containsAlias(API_KEY_ALIAS)
        val tokenKeyExists = keyStore.containsAlias(TOKEN_KEY_ALIAS)

        return KeyInfo(
            databaseKeyExists = dbKeyExists,
            apiKeyExists = apiKeyExists,
            tokenKeyExists = tokenKeyExists,
            allKeysPresent = dbKeyExists && apiKeyExists && tokenKeyExists
        )
    }
}

/**
 * Key information for diagnostics
 */
data class KeyInfo(
    val databaseKeyExists: Boolean,
    val apiKeyExists: Boolean,
    val tokenKeyExists: Boolean,
    val allKeysPresent: Boolean
)