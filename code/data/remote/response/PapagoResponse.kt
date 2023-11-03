package kr.com.chappiet.data.remote.response

import com.google.gson.annotations.SerializedName

data class PapagoResponseWrapper(
    val message: MessageContent
)

data class MessageContent(
    @SerializedName("@type") val type: String,
    @SerializedName("@service") val service: String,
    @SerializedName("@version") val version: String,
    val result: ResultContent
)

data class ResultContent (
    val srcLangType: String,
    val tarLangType: String,
    val translatedText: String
)
