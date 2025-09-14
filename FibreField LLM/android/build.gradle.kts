// 🟢 WORKING: Root build configuration with all required plugins and versions
buildscript {
    extra.apply {
        set("sdk_version", 34)
        set("min_sdk_version", 24)
        set("target_sdk_version", 34)
        set("version_code", 1)
        set("version_name", "1.0.0")
        
        set("kotlin_version", "1.9.21")
        set("compose_version", "1.5.8")
        set("hilt_version", "2.50")
        set("room_version", "2.6.1")
        set("retrofit_version", "2.9.0")
        set("camerax_version", "1.3.1")
        set("work_version", "2.9.0")
    }
}

plugins {
    id("com.android.application") version "8.2.2" apply false
    id("com.android.library") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.21" apply false
    id("com.google.dagger.hilt.android") version "2.50" apply false
    id("com.google.gms.google-services") version "4.4.0" apply false
    id("com.google.firebase.crashlytics") version "2.9.9" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}

// Global configuration for all modules
subprojects {
    apply(plugin = "kotlin-android")
    
    // Configure all modules with common settings
    afterEvaluate {
        if (hasProperty("android")) {
            extensions.configure<com.android.build.gradle.BaseExtension> {
                compileSdkVersion(rootProject.extra["sdk_version"] as Int)
                
                defaultConfig {
                    minSdk = rootProject.extra["min_sdk_version"] as Int
                    targetSdk = rootProject.extra["target_sdk_version"] as Int
                    
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    consumerProguardFiles("consumer-rules.pro")
                }
                
                buildTypes {
                    getByName("release") {
                        isMinifyEnabled = false
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro"
                        )
                    }
                }
                
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }
        }
        
        // Configure Kotlin for all modules
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions {
                jvmTarget = "17"
                freeCompilerArgs = listOf(
                    "-opt-in=kotlin.RequiresOptIn",
                    "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                    "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                    "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
                )
            }
        }
    }
}