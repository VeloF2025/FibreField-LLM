package com.fibreflow.core.network.models.response

import com.google.gson.annotations.SerializedName

/**
 * Authentication response model
 */
data class AuthResponse(
    @SerializedName("access_token")
    val accessToken: String,

    @SerializedName("refresh_token")
    val refreshToken: String,

    @SerializedName("token_type")
    val tokenType: String = "Bearer",

    @SerializedName("expires_in")
    val expiresIn: Long,

    @SerializedName("user")
    val user: UserInfo,

    @SerializedName("permissions")
    val permissions: List<String> = emptyList()
)

/**
 * User information in auth response
 */
data class UserInfo(
    @SerializedName("id")
    val id: String,

    @SerializedName("username")
    val username: String,

    @SerializedName("email")
    val email: String? = null,

    @SerializedName("full_name")
    val fullName: String,

    @SerializedName("role")
    val role: String,

    @SerializedName("is_active")
    val isActive: Boolean = true
)