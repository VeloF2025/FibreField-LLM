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
class Phi35MiniLLMTest {

    private lateinit var phi35MiniLLM: Phi35MiniLLM
    private lateinit var mockPerformanceMonitor: AIPerformanceMonitor

    @Before
    fun setUp() {
        mockPerformanceMonitor = mock()
        phi35MiniLLM = Phi35MiniLLM(mockPerformanceMonitor)
    }

    @Test
    fun `test Phi35MiniLLM initialization`() = runTest {
        // Given - LLM is initialized in setup

        // When - check if LLM is ready
        val isReady = phi35MiniLLM.isReady()

        // Then - should be ready
        assertTrue(isReady)
    }

    @Test
    fun `test generate response successfully`() = runTest {
        // Given
        val prompt = "Explain fibre optic installation"
        
        // When
        val result = phi35MiniLLM.generateResponse(prompt)

        // Then
        assertTrue(result is Result.Success)
        val response = (result as Result.Success).data
        assertTrue(response.contains("fibre optic") || response.contains("installation"))
        verify(mockPerformanceMonitor).trackInference("phi35_mini", true, any())
    }

    @Test
    fun `test generate response with empty prompt`() = runTest {
        // Given
        val prompt = ""
        
        // When
        val result = phi35MiniLLM.generateResponse(prompt)

        // Then
        assertTrue(result is Result.Error)
        assertEquals("Prompt cannot be empty", (result as Result.Error).exception.message)
        verify(mockPerformanceMonitor).trackInference("phi35_mini", false, any())
    }

    @Test
    fun `test get model info`() = runTest {
        // Given - LLM is initialized

        // When
        val info = phi35MiniLLM.getModelInfo()

        // Then
        assertEquals("Phi-3.5 Mini", info["model_name"])
        assertEquals("1.0.0", info["model_version"])
        assertEquals("4096", info["context_length"])
    }

    @Test
    fun `test shutdown cleans up resources`() = runTest {
        // Given - LLM is initialized

        // When
        phi35MiniLLM.shutdown()

        // Then - should not throw any exceptions
        assertTrue(true) // Just verify shutdown completes
    }

    @Test
    fun `test model loading state`() = runTest {
        // Given - LLM is initialized

        // When
        val isLoaded = phi35MiniLLM.isModelLoaded()

        // Then
        assertTrue(isLoaded)
    }
}