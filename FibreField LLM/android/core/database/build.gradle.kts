// 🟢 WORKING: Database module with Room, encryption, and migrations
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "com.fibreflow.core.database"
    
    buildFeatures {
        buildConfig = true
    }
    
    defaultConfig {
        // Database version and migration testing support
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.schemaLocation" to "$projectDir/schemas",
                    "room.incremental" to "true"
                )
            }
        }
    }
}

dependencies {
    // Core modules
    implementation(project(":core:common"))
    
    // Room Database
    implementation(libs.bundles.room)
    kapt(libs.androidx.room.compiler)
    
    // Database encryption
    implementation(libs.sqlcipher)
    
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    
    // Hilt Dependency Injection
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    
    // JSON handling
    implementation(libs.gson)
    
    // Utilities
    implementation(libs.timber)
    
    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.robolectric)
    
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}