package kr.com.chappiet.data.remote.response

import com.google.gson.annotations.SerializedName

data class DeepLCheckUsageAndLimitsResponse(
    @SerializedName("character_count")
    val characterCount: Int,
    @SerializedName("character_limit")
    val characterLimit: Int
)
