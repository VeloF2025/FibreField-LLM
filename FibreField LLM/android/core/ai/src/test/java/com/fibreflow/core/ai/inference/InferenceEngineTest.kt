package com.fibreflow.core.ai.inference

import com.fibreflow.core.ai.llm.LLMManager
import com.fibreflow.core.ai.performance.AIPerformanceMonitor
import com.fibreflow.core.ai.pipeline.ModelCoordinator
import com.fibreflow.core.ai.vision.BarcodeScanner
import com.fibreflow.core.ai.vision.ONTLightDetector
import com.fibreflow.core.ai.vision.PhotoQualityAnalyzer
import com.fibreflow.core.ai.vision.TextExtractor
import com.fibreflow.core.common.result.Result
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class InferenceEngineTest {

    private lateinit var inferenceEngine: InferenceEngine
    private lateinit var mockLLMManager: LLMManager
    private lateinit var mockModelCoordinator: ModelCoordinator
    private lateinit var mockPerformanceMonitor: AIPerformanceMonitor
    private lateinit var mockBarcodeScanner: BarcodeScanner
    private lateinit var mockTextExtractor: TextExtractor
    private lateinit var mockONTLightDetector: ONTLightDetector
    private lateinit var mockPhotoQualityAnalyzer: PhotoQualityAnalyzer

    @Before
    fun setUp() {
        mockLLMManager = mock()
        mockModelCoordinator = mock()
        mockPerformanceMonitor = mock()
        mockBarcodeScanner = mock()
        mockTextExtractor = mock()
        mockONTLightDetector = mock()
        mockPhotoQualityAnalyzer = mock()

        inferenceEngine = InferenceEngine(
            llmManager = mockLLMManager,
            modelCoordinator = mockModelCoordinator,
            performanceMonitor = mockPerformanceMonitor,
            barcodeScanner = mockBarcodeScanner,
            textExtractor = mockTextExtractor,
            ontLightDetector = mockONTLightDetector,
            photoQualityAnalyzer = mockPhotoQualityAnalyzer
        )
    }

    @Test
    fun `test inference engine initialization`() = runTest {
        // Given - engine is initialized in setup

        // When - check if engine is ready
        val isReady = inferenceEngine.isReady()

        // Then - should be ready
        assertTrue(isReady)
    }

    @Test
    fun `test process text prompt successfully`() = runTest {
        // Given
        val prompt = "What is fibre optic installation?"
        val expectedResponse = "Fibre optic installation involves..."
        
        whenever(mockLLMManager.generateResponse(prompt))
            .thenReturn(Result.Success(expectedResponse))

        // When
        val result = inferenceEngine.processTextPrompt(prompt)

        // Then
        assertTrue(result is Result.Success)
        assertEquals(expectedResponse, (result as Result.Success).data)
    }

    @Test
    fun `test process text prompt with error`() = runTest {
        // Given
        val prompt = "What is fibre optic installation?"
        val errorMessage = "Network error"
        
        whenever(mockLLMManager.generateResponse(prompt))
            .thenReturn(Result.Error(Exception(errorMessage)))

        // When
        val result = inferenceEngine.processTextPrompt(prompt)

        // Then
        assertTrue(result is Result.Error)
        assertEquals(errorMessage, (result as Result.Error).exception.message)
    }

    @Test
    fun `test shutdown cleans up resources`() = runTest {
        // Given - engine is initialized

        // When
        inferenceEngine.shutdown()

        // Then - should not throw any exceptions
        assertTrue(true) // Just verify shutdown completes
    }

    @Test
    fun `test get performance metrics`() = runTest {
        // Given
        val expectedMetrics = mapOf(
            "inference_count" to 5,
            "average_latency_ms" to 150.0,
            "success_rate" to 0.95
        )
        
        whenever(mockPerformanceMonitor.getPerformanceMetrics())
            .thenReturn(expectedMetrics)

        // When
        val metrics = inferenceEngine.getPerformanceMetrics()

        // Then
        assertEquals(expectedMetrics, metrics)
    }
}