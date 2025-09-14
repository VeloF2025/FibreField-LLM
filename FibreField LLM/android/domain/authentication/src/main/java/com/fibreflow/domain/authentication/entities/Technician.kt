package com.fibreflow.domain.authentication.entities

/**
 * Domain entity representing a technician user
 */
data class Technician(
    val id: String,
    val username: String,
    val email: String? = null,
    val fullName: String,
    val role: String,
    val isActive: Boolean = true,
    val permissions: List<String> = emptyList()
)