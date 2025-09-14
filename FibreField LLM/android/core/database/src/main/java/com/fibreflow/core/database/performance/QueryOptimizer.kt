package com.fibreflow.core.database.performance

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Database Query Optimizer for optimizing database query performance
 * Analyzes and optimizes query execution plans
 */
@Singleton
class QueryOptimizer @Inject constructor() {

    companion object {
        private const val TAG = "QueryOptimizer"
        private const val SLOW_QUERY_THRESHOLD_MS = 1000L // 1 second
        private const val VERY_SLOW_QUERY_THRESHOLD_MS = 5000L // 5 seconds
    }

    private val queryMetrics = mutableMapOf<String, QueryPerformanceMetrics>()

    /**
     * Analyze query performance
     */
    fun analyzeQueryPerformance(
        queryId: String,
        query: String,
        executionTimeMs: Long,
        resultCount: Int,
        parameters: Map<String, Any> = emptyMap()
    ): QueryAnalysisResult {
        val metrics = QueryPerformanceMetrics(
            queryId = queryId,
            query = query,
            executionTimeMs = executionTimeMs,
            resultCount = resultCount,
            parameters = parameters,
            timestamp = System.currentTimeMillis()
        )

        queryMetrics[queryId] = metrics

        // Analyze performance
        val analysis = when {
            executionTimeMs > VERY_SLOW_QUERY_THRESHOLD_MS -> QueryPerformance.VERY_SLOW
            executionTimeMs > SLOW_QUERY_THRESHOLD_MS -> QueryPerformance.SLOW
            executionTimeMs < 100 -> QueryPerformance.FAST
            else -> QueryPerformance.NORMAL
        }

        // Log performance issues
        when (analysis) {
            QueryPerformance.SLOW -> Timber.w("Slow query detected: $queryId took ${executionTimeMs}ms")
            QueryPerformance.VERY_SLOW -> Timber.e("Very slow query detected: $queryId took ${executionTimeMs}ms")
            else -> Timber.v("Query $queryId executed in ${executionTimeMs}ms")
        }

        return QueryAnalysisResult(
            queryId = queryId,
            performance = analysis,
            executionTimeMs = executionTimeMs,
            recommendations = generateRecommendations(query, executionTimeMs, resultCount)
        )
    }

    /**
     * Optimize query based on analysis
     */
    fun optimizeQuery(query: String, analysis: QueryAnalysisResult): QueryOptimizationResult {
        val optimizations = mutableListOf<QueryOptimization>()

        // Analyze query structure
        if (query.contains("SELECT *")) {
            optimizations.add(
                QueryOptimization(
                    type = OptimizationType.SELECT_FIELDS,
                    description = "Replace SELECT * with specific field names",
                    expectedImprovement = "20-40% performance improvement"
                )
            )
        }

        if (query.contains("WHERE") && !query.contains("INDEX")) {
            optimizations.add(
                QueryOptimization(
                    type = OptimizationType.ADD_INDEX,
                    description = "Consider adding indexes on WHERE clause columns",
                    expectedImprovement = "50-90% performance improvement"
                )
            )
        }

        if (query.contains("JOIN") && query.contains("WHERE")) {
            optimizations.add(
                QueryOptimization(
                    type = OptimizationType.OPTIMIZE_JOINS,
                    description = "Review JOIN order and add appropriate indexes",
                    expectedImprovement = "30-70% performance improvement"
                )
            )
        }

        if (analysis.executionTimeMs > SLOW_QUERY_THRESHOLD_MS) {
            optimizations.add(
                QueryOptimization(
                    type = OptimizationType.QUERY_REWRITE,
                    description = "Consider rewriting query or using alternative data access patterns",
                    expectedImprovement = "Variable - depends on rewrite approach"
                )
            )
        }

        return QueryOptimizationResult(
            originalQuery = query,
            optimizations = optimizations,
            estimatedTotalImprovement = calculateEstimatedImprovement(optimizations)
        )
    }

    /**
     * Get query performance statistics
     */
    fun getQueryPerformanceStatistics(): QueryPerformanceStatistics {
        val totalQueries = queryMetrics.size
        val slowQueries = queryMetrics.values.count { it.executionTimeMs > SLOW_QUERY_THRESHOLD_MS }
        val verySlowQueries = queryMetrics.values.count { it.executionTimeMs > VERY_SLOW_QUERY_THRESHOLD_MS }

        val averageExecutionTime = if (totalQueries > 0) {
            queryMetrics.values.map { it.executionTimeMs.toDouble() }.average()
        } else 0.0

        return QueryPerformanceStatistics(
            totalQueries = totalQueries,
            slowQueries = slowQueries,
            verySlowQueries = verySlowQueries,
            slowQueryPercentage = if (totalQueries > 0) (slowQueries.toDouble() / totalQueries) * 100 else 0.0,
            averageExecutionTimeMs = averageExecutionTime,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Clear old query metrics
     */
    fun clearOldMetrics(olderThanHours: Int = 24) {
        val cutoffTime = System.currentTimeMillis() - (olderThanHours * 60 * 60 * 1000L)
        queryMetrics.entries.removeIf { it.value.timestamp < cutoffTime }
        Timber.d("Cleared query metrics older than $olderThanHours hours")
    }

    // Private helper methods

    private fun generateRecommendations(
        query: String,
        executionTimeMs: Long,
        resultCount: Int
    ): List<String> {
        val recommendations = mutableListOf<String>()

        if (executionTimeMs > VERY_SLOW_QUERY_THRESHOLD_MS) {
            recommendations.add("Query is executing very slowly. Consider database optimization or query rewrite.")
        }

        if (resultCount > 10000) {
            recommendations.add("Large result set detected. Consider pagination or filtering.")
        }

        if (query.contains("LIKE") && query.contains("%") && !query.contains("INDEX")) {
            recommendations.add("LIKE queries with leading wildcards perform poorly without indexes.")
        }

        if (query.contains("ORDER BY") && !query.contains("INDEX")) {
            recommendations.add("ORDER BY without appropriate indexes can be slow.")
        }

        return recommendations
    }

    private fun calculateEstimatedImprovement(optimizations: List<QueryOptimization>): String {
        val totalImprovement = optimizations.sumOf { improvement ->
            when (improvement.expectedImprovement) {
                "20-40% performance improvement" -> 30
                "50-90% performance improvement" -> 70
                "30-70% performance improvement" -> 50
                "Variable - depends on rewrite approach" -> 25
                else -> 0
            }
        }

        return when {
            totalImprovement > 100 -> "Significant improvement expected"
            totalImprovement > 50 -> "Moderate to significant improvement expected"
            totalImprovement > 20 -> "Moderate improvement expected"
            else -> "Minor improvement expected"
        }
    }
}

/**
 * Data classes for query optimization
 */

data class QueryAnalysisResult(
    val queryId: String,
    val performance: QueryPerformance,
    val executionTimeMs: Long,
    val recommendations: List<String>
)

data class QueryOptimizationResult(
    val originalQuery: String,
    val optimizations: List<QueryOptimization>,
    val estimatedTotalImprovement: String
)

data class QueryOptimization(
    val type: OptimizationType,
    val description: String,
    val expectedImprovement: String
)

data class QueryPerformanceStatistics(
    val totalQueries: Int,
    val slowQueries: Int,
    val verySlowQueries: Int,
    val slowQueryPercentage: Double,
    val averageExecutionTimeMs: Double,
    val timestamp: Long
)

data class QueryPerformanceMetrics(
    val queryId: String,
    val query: String,
    val executionTimeMs: Long,
    val resultCount: Int,
    val parameters: Map<String, Any>,
    val timestamp: Long
)

enum class QueryPerformance {
    FAST,
    NORMAL,
    SLOW,
    VERY_SLOW
}

enum class OptimizationType {
    SELECT_FIELDS,
    ADD_INDEX,
    OPTIMIZE_JOINS,
    QUERY_REWRITE,
    ADD_PAGINATION,
    CACHE_RESULTS
}