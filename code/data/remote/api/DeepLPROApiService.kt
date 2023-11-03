package kr.com.chappiet.data.remote.api

import kr.com.chappiet.data.remote.response.DeepLCheckUsageAndLimitsResponse
import kr.com.chappiet.data.remote.response.DeepLResponseWrapper
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface DeepLPROApiService {
    @FormUrlEncoded
    @Headers("Content-Type: application/x-www-form-urlencoded; charset=UTF-8")
    @POST("/v2/translate")
    suspend fun deepLPROTranslateRequest(
        @Field("target_lang") targetLang: String = "KO",
        @Field("text") text: String,
        @Header("Authorization") auth: String
    ): DeepLResponseWrapper

    // https://www.deepl.com/ko/docs-api/general/get-usage
    // Check Usage and Limits
    // Retrieve usage information within the current billing period together with the corresponding account limits.
    @POST("/v2/usage")
    suspend fun deepLCheckUsageAndLimits(
        @Header("Authorization") auth: String
    ): DeepLCheckUsageAndLimitsResponse
}