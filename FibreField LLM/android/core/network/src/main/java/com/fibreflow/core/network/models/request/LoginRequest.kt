package com.fibreflow.core.network.models.request

import com.google.gson.annotations.SerializedName

/**
 * Login request model
 */
data class LoginRequest(
    @SerializedName("username")
    val username: String,

    @SerializedName("password")
    val password: String,

    @SerializedName("device_id")
    val deviceId: String? = null,

    @SerializedName("app_version")
    val appVersion: String? = null
)