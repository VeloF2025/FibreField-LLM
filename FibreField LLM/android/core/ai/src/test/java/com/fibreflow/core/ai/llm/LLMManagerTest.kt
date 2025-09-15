package com.fibreflow.core.ai.llm

import com.fibreflow.core.ai.performance.AIPerformanceMonitor
import com.fibreflow.core.common.result.Result
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LLMManagerTest {

    private lateinit var llmManager: LLMManager
    private lateinit var mockPhi35MiniLLM: Phi35MiniLLM
    private lateinit var mockPerformanceMonitor: AIPerformanceMonitor

    @Before
    fun setUp() {
        mockPhi35MiniLLM = mock()
        mockPerformanceMonitor = mock()

        llmManager = LLMManager(
            phi35MiniLLM = mockPhi35MiniLLM,
            performanceMonitor = mockPerformanceMonitor
        )
    }

    @Test
    fun `test LLM manager initialization`() = runTest {
        // Given - manager is initialized in setup

        // When - check if manager is ready
        val isReady = llmManager.isReady()

        // Then - should be ready
        assertTrue(isReady)
    }

    @Test
    fun `test generate response successfully`() = runTest {
        // Given
        val prompt = "Explain fibre optic installation"
        val expectedResponse = "Fibre optic installation involves..."
        
        whenever(mockPhi35MiniLLM.generateResponse(prompt))
            .thenReturn(Result.Success(expectedResponse))

        // When
        val result = llmManager.generateResponse(prompt)

        // Then
        assertTrue(result is Result.Success)
        assertEquals(expectedResponse, (result as Result.Success).data)
        verify(mockPerformanceMonitor).trackInference("text_generation", true, any())
    }

    @Test
    fun `test generate response with error`() = runTest {
        // Given
        val prompt = "Explain fibre optic installation"
        val errorMessage = "Model not loaded"
        
        whenever(mockPhi35MiniLLM.generateResponse(prompt))
            .thenReturn(Result.Error(Exception(errorMessage)))

        // When
        val result = llmManager.generateResponse(prompt)

        // Then
        assertTrue(result is Result.Error)
        assertEquals(errorMessage, (result as Result.Error).exception.message)
        verify(mockPerformanceMonitor).trackInference("text_generation", false, any())
    }

    @Test
    fun `test get model info`() = runTest {
        // Given
        val expectedInfo = mapOf(
            "model_name" to "Phi-3.5 Mini",
            "model_version" to "1.0.0",
            "context_length" to "4096"
        )
        
        whenever(mockPhi35MiniLLM.getModelInfo())
            .thenReturn(expectedInfo)

        // When
        val info = llmManager.getModelInfo()

        // Then
        assertEquals(expectedInfo, info)
    }

    @Test
    fun `test shutdown cleans up resources`() = runTest {
        // Given - manager is initialized

        // When
        llmManager.shutdown()

        // Then - should call phi35MiniLLM shutdown
        verify(mockPhi35MiniLLM).shutdown()
    }

    @Test
    fun `test get performance metrics`() = runTest {
        // Given
        val expectedMetrics = mapOf(
            "total_inferences" to 100,
            "success_rate" to 0.95,
            "average_latency_ms" to 150.0
        )
        
        whenever(mockPerformanceMonitor.getPerformanceMetrics())
            .thenReturn(expectedMetrics)

        // When
        val metrics = llmManager.getPerformanceMetrics()

        // Then
        assertEquals(expectedMetrics, metrics)
    }
}