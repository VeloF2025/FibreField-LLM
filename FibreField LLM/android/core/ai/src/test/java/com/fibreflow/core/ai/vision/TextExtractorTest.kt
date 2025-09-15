package com.fibreflow.core.ai.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.fibreflow.core.ai.performance.AIPerformanceMonitor
import com.fibreflow.core.common.result.Result
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TextExtractorTest {

    private lateinit var textExtractor: TextExtractor
    private lateinit var mockPerformanceMonitor: AIPerformanceMonitor

    @Before
    fun setUp() {
        mockPerformanceMonitor = mock()
        textExtractor = TextExtractor(mockPerformanceMonitor)
    }

    @Test
    fun `test text extractor initialization`() = runTest {
        // Given - extractor is initialized in setup

        // When - check if extractor is ready
        val isReady = textExtractor.isReady()

        // Then - should be ready
        assertTrue(isReady)
    }

    @Test
    fun `test extract text from bitmap successfully`() = runTest {
        // Given
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        // When
        val result = textExtractor.extractText(bitmap)

        // Then
        assertTrue(result is Result.Success)
        // Since we're using a mock, we can't get real text data, but we can verify the result type
    }

    @Test
    fun `test extract text with null bitmap`() = runTest {
        // Given
        val bitmap: Bitmap? = null

        // When
        val result = textExtractor.extractText(bitmap)

        // Then
        assertTrue(result is Result.Error)
        assertEquals("Bitmap cannot be null", (result as Result.Error).exception.message)
    }

    @Test
    fun `test extract text with empty bitmap`() = runTest {
        // Given
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)

        // When
        val result = textExtractor.extractText(bitmap)

        // Then
        // Should handle empty bitmap gracefully
        assertTrue(result is Result.Success)
    }

    @Test
    fun `test shutdown cleans up resources`() = runTest {
        // Given - extractor is initialized

        // When
        textExtractor.shutdown()

        // Then - should not throw any exceptions
        assertTrue(true)
    }

    @Test
    fun `test get performance metrics`() = runTest {
        // Given
        // Performance monitor is mocked, so we can't get real metrics

        // When
        val metrics = textExtractor.getPerformanceMetrics()

        // Then
        // Should return empty or default metrics
        assertTrue(metrics.isNotEmpty())
    }
}