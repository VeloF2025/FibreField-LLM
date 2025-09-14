// 🟢 WORKING: Core network module with API interfaces and interceptors
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "com.fibreflow.core.network"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "BASE_URL", "\"https://api.fibreflow.com/\"")
        buildConfigField("String", "CERTIFICATE_PIN", "\"sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=\"")
        buildConfigField("String", "APP_VERSION", "\"1.0.0\"")
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // Security
    implementation(libs.okhttp.certificate.pinner)

    // Utilities
    implementation(libs.timber)
    implementation(libs.gson)

    // Project dependencies
    implementation(project(":core:common"))

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.okhttp.mockwebserver)
}