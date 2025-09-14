package com.fibreflow.core.network.models.response

import com.google.gson.annotations.SerializedName

/**
 * Token refresh response model
 */
data class RefreshTokenResponse(
    @SerializedName("access_token")
    val accessToken: String,

    @SerializedName("token_type")
    val tokenType: String = "Bearer",

    @SerializedName("expires_in")
    val expiresIn: Long
)