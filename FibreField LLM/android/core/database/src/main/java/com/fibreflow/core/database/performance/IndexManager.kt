package com.fibreflow.core.database.performance

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Database Index Manager for managing and optimizing database indexes
 * Analyzes query patterns and suggests optimal indexing strategies
 */
@Singleton
class IndexManager @Inject constructor() {

    companion object {
        private const val TAG = "IndexManager"
        private const val INDEX_ANALYSIS_THRESHOLD = 100 // Minimum queries to analyze
        private const val SLOW_QUERY_THRESHOLD_MS = 500L
    }

    private val indexUsageMetrics = mutableMapOf<String, IndexUsageMetrics>()
    private val suggestedIndexes = mutableMapOf<String, SuggestedIndex>()

    /**
     * Analyze query for index optimization opportunities
     */
    fun analyzeQueryForIndexes(
        queryId: String,
        query: String,
        executionTimeMs: Long,
        tableName: String,
        whereClause: String? = null,
        joinClause: String? = null,
        orderByClause: String? = null
    ): IndexAnalysisResult {
        val analysis = IndexAnalysisResult(
            queryId = queryId,
            tableName = tableName,
            executionTimeMs = executionTimeMs,
            recommendations = mutableListOf()
        )

        // Analyze WHERE clause for potential indexes
        if (whereClause != null) {
            val whereRecommendations = analyzeWhereClause(whereClause, tableName, executionTimeMs)
            analysis.recommendations.addAll(whereRecommendations)
        }

        // Analyze JOIN clause for potential indexes
        if (joinClause != null) {
            val joinRecommendations = analyzeJoinClause(joinClause, tableName, executionTimeMs)
            analysis.recommendations.addAll(joinRecommendations)
        }

        // Analyze ORDER BY clause for potential indexes
        if (orderByClause != null) {
            val orderByRecommendations = analyzeOrderByClause(orderByClause, tableName, executionTimeMs)
            analysis.recommendations.addAll(orderByRecommendations)
        }

        // Store analysis for future reference
        if (analysis.recommendations.isNotEmpty()) {
            Timber.d("Found ${analysis.recommendations.size} index recommendations for query $queryId")
        }

        return analysis
    }

    /**
     * Get index usage statistics
     */
    fun getIndexUsageStatistics(): IndexUsageStatistics {
        val totalIndexes = indexUsageMetrics.size
        val usedIndexes = indexUsageMetrics.values.count { it.usageCount > 0 }
        val unusedIndexes = totalIndexes - usedIndexes

        val averageUsage = if (totalIndexes > 0) {
            indexUsageMetrics.values.map { it.usageCount.toDouble() }.average()
        } else 0.0

        return IndexUsageStatistics(
            totalIndexes = totalIndexes,
            usedIndexes = usedIndexes,
            unusedIndexes = unusedIndexes,
            averageUsagePerIndex = averageUsage,
            mostUsedIndex = indexUsageMetrics.maxByOrNull { it.value.usageCount }?.key ?: "None",
            leastUsedIndex = indexUsageMetrics.minByOrNull { it.value.usageCount }?.key ?: "None",
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Suggest indexes based on query analysis
     */
    fun getIndexSuggestions(tableName: String? = null): List<IndexSuggestion> {
        val suggestions = mutableListOf<IndexSuggestion>()

        suggestedIndexes.values.forEach { suggested ->
            if (tableName == null || suggested.tableName == tableName) {
                suggestions.add(
                    IndexSuggestion(
                        tableName = suggested.tableName,
                        columns = suggested.columns,
                        indexType = suggested.indexType,
                        estimatedImprovement = suggested.estimatedImprovement,
                        usageFrequency = suggested.usageFrequency
                    )
                )
            }
        }

        return suggestions.sortedByDescending { it.usageFrequency }
    }

    /**
     * Record index usage
     */
    fun recordIndexUsage(indexName: String, tableName: String, usageCount: Int = 1) {
        val metrics = indexUsageMetrics.getOrPut(indexName) {
            IndexUsageMetrics(
                indexName = indexName,
                tableName = tableName,
                usageCount = 0,
                lastUsed = 0L
            )
        }

        metrics.usageCount += usageCount
        metrics.lastUsed = System.currentTimeMillis()

        Timber.v("Recorded index usage: $indexName used $usageCount times")
    }

    /**
     * Analyze slow queries and suggest indexes
     */
    fun analyzeSlowQueries(queries: List<SlowQuery>): List<IndexSuggestion> {
        val suggestions = mutableListOf<IndexSuggestion>()

        queries.forEach { slowQuery ->
            if (slowQuery.executionTimeMs > SLOW_QUERY_THRESHOLD_MS) {
                val analysis = analyzeQueryForIndexes(
                    queryId = slowQuery.queryId,
                    query = slowQuery.query,
                    executionTimeMs = slowQuery.executionTimeMs,
                    tableName = slowQuery.tableName,
                    whereClause = slowQuery.whereClause,
                    joinClause = slowQuery.joinClause,
                    orderByClause = slowQuery.orderByClause
                )

                analysis.recommendations.forEach { recommendation ->
                    suggestions.add(
                        IndexSuggestion(
                            tableName = analysis.tableName,
                            columns = recommendation.columns,
                            indexType = recommendation.indexType,
                            estimatedImprovement = recommendation.estimatedImprovement,
                            usageFrequency = 1 // Each slow query counts as usage
                        )
                    )
                }
            }
        }

        return suggestions.distinctBy { "${it.tableName}_${it.columns.joinToString("_")}" }
    }

    /**
     * Generate index creation SQL
     */
    fun generateIndexCreationSQL(suggestion: IndexSuggestion): String {
        val indexName = "idx_${suggestion.tableName}_${suggestion.columns.joinToString("_")}"
        val columnsList = suggestion.columns.joinToString(", ")

        return when (suggestion.indexType) {
            IndexType.BTREE -> "CREATE INDEX $indexName ON ${suggestion.tableName} ($columnsList);"
            IndexType.HASH -> "CREATE INDEX $indexName ON ${suggestion.tableName} USING HASH ($columnsList);"
            IndexType.GIN -> "CREATE INDEX $indexName ON ${suggestion.tableName} USING GIN ($columnsList);"
            IndexType.GIST -> "CREATE INDEX $indexName ON ${suggestion.tableName} USING GIST ($columnsList);"
            IndexType.UNIQUE -> "CREATE UNIQUE INDEX $indexName ON ${suggestion.tableName} ($columnsList);"
        }
    }

    /**
     * Clear old metrics data
     */
    fun clearOldMetrics(olderThanHours: Int = 168) { // 7 days
        val cutoffTime = System.currentTimeMillis() - (olderThanHours * 60 * 60 * 1000L)

        indexUsageMetrics.entries.removeIf { it.value.lastUsed < cutoffTime }
        suggestedIndexes.entries.removeIf { it.value.lastSuggested < cutoffTime }

        Timber.d("Cleared index metrics older than $olderThanHours hours")
    }

    // Private helper methods

    private fun analyzeWhereClause(
        whereClause: String,
        tableName: String,
        executionTimeMs: Long
    ): List<IndexRecommendation> {
        val recommendations = mutableListOf<IndexRecommendation>()

        // Simple analysis - look for common patterns
        val conditions = whereClause.split(" AND ", " OR ").map { it.trim() }

        conditions.forEach { condition ->
            if (condition.contains("=") && !condition.contains("IS NULL")) {
                val column = extractColumnName(condition)
                if (column != null) {
                    recommendations.add(
                        IndexRecommendation(
                            columns = listOf(column),
                            indexType = IndexType.BTREE,
                            reason = "Equality condition in WHERE clause",
                            estimatedImprovement = calculateEstimatedImprovement(executionTimeMs)
                        )
                    )
                }
            }

            if (condition.contains("LIKE") && condition.contains("%") && !condition.endsWith("%")) {
                val column = extractColumnName(condition)
                if (column != null) {
                    recommendations.add(
                        IndexRecommendation(
                            columns = listOf(column),
                            indexType = IndexType.BTREE,
                            reason = "Prefix LIKE query",
                            estimatedImprovement = "60-80% improvement"
                        )
                    )
                }
            }

            if (condition.contains(">") || condition.contains("<") ||
                condition.contains(">=") || condition.contains("<=")) {
                val column = extractColumnName(condition)
                if (column != null) {
                    recommendations.add(
                        IndexRecommendation(
                            columns = listOf(column),
                            indexType = IndexType.BTREE,
                            reason = "Range condition in WHERE clause",
                            estimatedImprovement = calculateEstimatedImprovement(executionTimeMs)
                        )
                    )
                }
            }
        }

        return recommendations
    }

    private fun analyzeJoinClause(
        joinClause: String,
        tableName: String,
        executionTimeMs: Long
    ): List<IndexRecommendation> {
        val recommendations = mutableListOf<IndexRecommendation>()

        // Look for JOIN conditions
        val joinPattern = "(?i)JOIN\\s+\\w+\\s+ON\\s+([^=]+)=([^=]+)".toRegex()
        val matches = joinPattern.findAll(joinClause)

        matches.forEach { match ->
            val leftSide = match.groupValues[1].trim()
            val rightSide = match.groupValues[2].trim()

            val leftColumn = extractColumnName(leftSide)
            val rightColumn = extractColumnName(rightSide)

            if (leftColumn != null) {
                recommendations.add(
                    IndexRecommendation(
                        columns = listOf(leftColumn),
                        indexType = IndexType.BTREE,
                        reason = "JOIN condition",
                        estimatedImprovement = calculateEstimatedImprovement(executionTimeMs)
                    )
                )
            }

            if (rightColumn != null) {
                recommendations.add(
                    IndexRecommendation(
                        columns = listOf(rightColumn),
                        indexType = IndexType.BTREE,
                        reason = "JOIN condition",
                        estimatedImprovement = calculateEstimatedImprovement(executionTimeMs)
                    )
                )
            }
        }

        return recommendations
    }

    private fun analyzeOrderByClause(
        orderByClause: String,
        tableName: String,
        executionTimeMs: Long
    ): List<IndexRecommendation> {
        val recommendations = mutableListOf<IndexRecommendation>()

        val columns = orderByClause.split(",").map { it.trim() }
            .mapNotNull { extractColumnName(it) }

        if (columns.isNotEmpty()) {
            recommendations.add(
                IndexRecommendation(
                    columns = columns,
                    indexType = IndexType.BTREE,
                    reason = "ORDER BY clause",
                    estimatedImprovement = calculateEstimatedImprovement(executionTimeMs)
                )
            )
        }

        return recommendations
    }

    private fun extractColumnName(condition: String): String? {
        // Simple column extraction - in a real implementation, this would be more sophisticated
        val parts = condition.split(" ", ".", "=").filter { it.isNotBlank() }
        return parts.find { it.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")) }
    }

    private fun calculateEstimatedImprovement(executionTimeMs: Long): String {
        return when {
            executionTimeMs > 5000 -> "70-90% improvement"
            executionTimeMs > 1000 -> "50-70% improvement"
            executionTimeMs > 500 -> "30-50% improvement"
            else -> "20-40% improvement"
        }
    }
}

/**
 * Data classes for index management
 */

data class IndexAnalysisResult(
    val queryId: String,
    val tableName: String,
    val executionTimeMs: Long,
    val recommendations: MutableList<IndexRecommendation>
)

data class IndexRecommendation(
    val columns: List<String>,
    val indexType: IndexType,
    val reason: String,
    val estimatedImprovement: String
)

data class IndexUsageStatistics(
    val totalIndexes: Int,
    val usedIndexes: Int,
    val unusedIndexes: Int,
    val averageUsagePerIndex: Double,
    val mostUsedIndex: String,
    val leastUsedIndex: String,
    val timestamp: Long
)

data class IndexUsageMetrics(
    val indexName: String,
    val tableName: String,
    var usageCount: Int,
    var lastUsed: Long
)

data class SuggestedIndex(
    val tableName: String,
    val columns: List<String>,
    val indexType: IndexType,
    val estimatedImprovement: String,
    var usageFrequency: Int = 0,
    val lastSuggested: Long = System.currentTimeMillis()
)

data class IndexSuggestion(
    val tableName: String,
    val columns: List<String>,
    val indexType: IndexType,
    val estimatedImprovement: String,
    val usageFrequency: Int
)

data class SlowQuery(
    val queryId: String,
    val query: String,
    val tableName: String,
    val executionTimeMs: Long,
    val whereClause: String? = null,
    val joinClause: String? = null,
    val orderByClause: String? = null
)

enum class IndexType {
    BTREE,    // Default B-tree index
    HASH,     // Hash index for equality
    GIN,      // Generalized Inverted Index
    GIST,     // Generalized Search Tree
    UNIQUE    // Unique index
}