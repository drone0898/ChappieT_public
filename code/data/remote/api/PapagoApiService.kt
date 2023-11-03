package kr.com.chappiet.data.remote.api

import kr.com.chappiet.data.remote.response.PapagoResponseWrapper
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Headers
import retrofit2.http.POST

interface PapagoApiService {

    @FormUrlEncoded
    @Headers("Content-Type: application/x-www-form-urlencoded; charset=UTF-8")
    @POST("v1/papago/n2mt")
    suspend fun papagoTranslateRequest(
        @Field("source") source: String,
        @Field("target") target: String,
        @Field("text") text: String
    ): PapagoResponseWrapper

//    @Headers("Content-Type: application/x-www-form-urlencoded; charset=UTF-8")
//    @POST("v1/papago/n2mt")
//    suspend fun papagoTranslateRequest(
//        @Body request: HashMap<String, Any>
//    ): PapagoResponseWrapper

}