package kr.com.chappiet.data.remote.response

import com.google.gson.annotations.SerializedName

data class DeepLResponseWrapper(
    val translations: List<DeepLTranslationsContent>?
)

data class DeepLTranslationsContent(
    @SerializedName("detected_source_language")
    val detectedSourceLanguage:String,
    val text:String
)