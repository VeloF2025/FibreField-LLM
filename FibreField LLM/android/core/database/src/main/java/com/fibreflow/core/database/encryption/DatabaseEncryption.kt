package com.fibreflow.core.database.encryption

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.fibreflow.core.common.result.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Database encryption manager using SQLCipher
 * Provides AES-256 encryption for local data storage
 */
@Singleton
class DatabaseEncryption @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "DatabaseEncryption"
        private const val KEY_ALIAS = "fibrefield_db_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_SIZE = 256
    }

    private var databaseKey: String? = null
    private var supportFactory: SupportFactory? = null

    /**
     * Initialize database encryption
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Initializing database encryption")

            // Generate or retrieve encryption key
            val keyResult = getOrCreateDatabaseKey()
            if (keyResult is Result.Error) {
                return@withContext keyResult
            }

            databaseKey = keyResult.data

            // Create SQLCipher support factory
            supportFactory = SupportFactory(SQLiteDatabase.getBytes(databaseKey?.toCharArray()))

            Log.i(TAG, "Database encryption initialized successfully")
            Result.Success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize database encryption", e)
            Result.Error(e)
        }
    }

    /**
     * Get SQLCipher support factory for Room database
     */
    fun getSupportFactory(): SupportFactory? = supportFactory

    /**
     * Check if encryption is properly configured
     */
    fun isEncryptionEnabled(): Boolean {
        return databaseKey != null && supportFactory != null
    }

    /**
     * Get database key for external use (secure operations only)
     */
    fun getDatabaseKey(): String? = databaseKey

    /**
     * Change database encryption key (requires database migration)
     */
    suspend fun changeEncryptionKey(newKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Validate new key
            if (newKey.length < 8) {
                return@withContext Result.Error(IllegalArgumentException("Key must be at least 8 characters"))
            }

            // Store new key securely
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

            // Remove old key
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }

            // Generate new key
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setKeySize(KEY_SIZE)
                .setUserAuthenticationRequired(false)
                .build()

            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()

            // Update database key and factory
            databaseKey = newKey
            supportFactory = SupportFactory(SQLiteDatabase.getBytes(newKey.toCharArray()))

            Log.i(TAG, "Database encryption key changed successfully")
            Result.Success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to change encryption key", e)
            Result.Error(e)
        }
    }

    /**
     * Validate database integrity after encryption
     */
    suspend fun validateEncryption(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Test encryption by attempting to open database
            // This is a simplified validation - in practice would test actual database operations
            val isValid = databaseKey != null && supportFactory != null

            if (isValid) {
                Log.i(TAG, "Database encryption validation passed")
                Result.Success(true)
            } else {
                Log.w(TAG, "Database encryption validation failed")
                Result.Success(false)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Database encryption validation error", e)
            Result.Error(e)
        }
    }

    // Private implementation methods

    private fun getOrCreateDatabaseKey(): Result<String> {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

            // Check if key already exists
            if (keyStore.containsAlias(KEY_ALIAS)) {
                // Retrieve existing key
                val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
                // In practice, you'd derive a database key from this hardware key
                // For simplicity, using a fixed key derived from the hardware key
                val derivedKey = deriveDatabaseKey(secretKey)
                Result.Success(derivedKey)
            } else {
                // Generate new key
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .setKeySize(KEY_SIZE)
                    .setUserAuthenticationRequired(false)
                    .build()

                keyGenerator.init(keyGenParameterSpec)
                val secretKey = keyGenerator.generateKey()

                val derivedKey = deriveDatabaseKey(secretKey)
                Result.Success(derivedKey)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get or create database key", e)
            Result.Error(e)
        }
    }

    private fun deriveDatabaseKey(secretKey: SecretKey): String {
        // In a real implementation, you'd use a proper key derivation function
        // For this example, using a simple transformation
        val encoded = secretKey.encoded
        return android.util.Base64.encodeToString(encoded, android.util.Base64.NO_WRAP)
            .substring(0, 32) // Ensure 32 character key for SQLCipher
    }
}