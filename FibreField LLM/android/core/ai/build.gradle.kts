// 🟢 WORKING: Core AI module with computer vision and ML models
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "com.fibreflow.core.ai"

    buildFeatures {
        buildConfig = true
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

    // ML Kit for computer vision
    implementation(libs.mlkit.vision.common)
    implementation(libs.mlkit.vision.barcode.scanning)
    implementation(libs.mlkit.vision.text.recognition)

    // TensorFlow Lite for custom models
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support)

    // MLC LLM for Phi-3.5 Mini integration
    implementation(libs.mlc.llm.android)

    // Project dependencies
    implementation(project(":core:common"))

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    testImplementation(libs.mockito.kotlin)
}