// 🟢 WORKING: Main application module with all dependencies and configuration
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.fibreflow.technician"
    compileSdk = 34
    
    defaultConfig {
        applicationId = "com.fibreflow.technician"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        
        testInstrumentationRunner = "com.fibreflow.technician.TestRunner"
        
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // Build configuration fields
        buildConfigField("String", "API_BASE_URL", "\"${findProperty("DEBUG_API_BASE_URL")}\"")
        buildConfigField("String", "CERTIFICATE_PIN", "\"${findProperty("CERTIFICATE_PIN_DEV")}\"")
        buildConfigField("boolean", "ENABLE_AI_VALIDATION", "${findProperty("ENABLE_AI_VALIDATION")}")
        buildConfigField("boolean", "ENABLE_OFFLINE_MAPS", "${findProperty("ENABLE_OFFLINE_MAPS")}")
        
        // Database configuration
        buildConfigField("String", "DATABASE_NAME", "\"${findProperty("DATABASE_NAME")}\"")
        buildConfigField("String", "LLM_MODEL_PATH", "\"${findProperty("LLM_MODEL_PATH")}\"")
        buildConfigField("String", "VISION_MODEL_PATH", "\"${findProperty("VISION_MODEL_PATH")}\"")
    }
    
    signingConfigs {
        create("release") {
            // Use environment variables or command line properties for production
            // These should be set in CI/CD environment or local.properties
            keyAlias = findProperty("KEY_ALIAS") as String?
            keyPassword = findProperty("KEY_PASSWORD") as String?
            storeFile = file("keystore/release.keystore")
            storePassword = findProperty("KEYSTORE_PASSWORD") as String?
            
            // Validate that required properties are set for release builds
            if (keyAlias == null || keyPassword == null || storePassword == null) {
                throw GradleException("Missing required signing properties. Set KEY_ALIAS, KEY_PASSWORD, and KEYSTORE_PASSWORD as environment variables or in local.properties")
            }
        }
        
        getByName("debug") {
            storeFile = file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    
    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
            applicationIdSuffix = ".debug"

            // Debug-specific configuration
            buildConfigField("String", "API_BASE_URL", "\"${findProperty("DEBUG_API_BASE_URL")}\"")
            buildConfigField("String", "CERTIFICATE_PIN", "\"${findProperty("CERTIFICATE_PIN_DEV")}\"")

            // Enable all debugging features
            buildConfigField("boolean", "ENABLE_ANALYTICS", "false")
            buildConfigField("boolean", "ENABLE_CRASH_REPORTING", "false")
        }
        
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            
            // Production configuration
            buildConfigField("String", "API_BASE_URL", "\"${findProperty("RELEASE_API_BASE_URL")}\"")
            buildConfigField("String", "CERTIFICATE_PIN", "\"${findProperty("CERTIFICATE_PIN_PROD")}\"")
            buildConfigField("boolean", "ENABLE_ANALYTICS", "true")
            buildConfigField("boolean", "ENABLE_CRASH_REPORTING", "true")
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        
        // Enable core library desugaring for modern Java features on older Android versions
        isCoreLibraryDesugaringEnabled = true
    }
    
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.camera.core.ExperimentalGetImage"
        )
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = false
        dataBinding = false
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
    
    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/gradle/incremental.annotation.processors"
            )
        }
    }
    
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Core modules
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:ai"))

    // Domain modules
    implementation(project(":domain:authentication"))
    implementation(project(":domain:drops"))

    // Feature modules
    implementation(project(":feature:installation"))

    // Infrastructure modules
    implementation(project(":infrastructure:sync"))
    implementation(project(":infrastructure:offline"))
    
    // Core Android libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    
    // Compose UI
    implementation(libs.bundles.compose)
    implementation(platform(libs.compose.bom))
    debugImplementation(libs.bundles.compose.debug)
    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    
    // Hilt Dependency Injection
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    
    // Room Database
    implementation(libs.bundles.room)
    kapt(libs.androidx.room.compiler)
    
    // Networking
    implementation(libs.bundles.retrofit)
    implementation(libs.gson)
    
    // WorkManager for background tasks
    implementation(libs.androidx.work.runtime.ktx)
    
    // DataStore for preferences
    implementation(libs.androidx.datastore.preferences)
    
    // CameraX for photo capture
    implementation(libs.bundles.camerax)
    
    // ML Kit for basic computer vision
    implementation(libs.bundles.mlkit)
    
    // TensorFlow Lite for custom models
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support)
    
    // MLC LLM for on-device language model
    implementation(libs.mlc.llm.android)
    
    // Maps and Location
    implementation(libs.osmdroid.android)
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    
    // Security
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
    implementation(libs.sqlcipher)
    
    // Firebase (optional for analytics and crash reporting)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    
    // Utilities
    implementation(libs.timber)
    
    // Core library desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    
    // Debug tools
    debugImplementation(libs.leakcanary.android)
    
    // Testing dependencies
    testImplementation(libs.bundles.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.robolectric)
    
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    
    // Instrumentation test runner
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.ext:truth:1.5.0")
}

// Kapt configuration for better build performance
kapt {
    correctErrorTypes = true
    
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
}

// Hilt configuration
hilt {
    enableAggregatingTask = true
    enableExperimentalClasspathAggregation = true
}