package kr.com.chappiet.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kr.com.chappiet.data.model.User
import kr.com.chappiet.data.remote.ApiResult
import kr.com.chappiet.data.remote.response.ChappieTCommonResponseWrapper
import kr.com.chappiet.data.remote.api.ChappieTFunctionService
import kr.com.chappiet.data.remote.api.DeepLApiService
import kr.com.chappiet.data.remote.api.DeepLPROApiService
import kr.com.chappiet.data.remote.response.DeepLResponseWrapper
import kr.com.chappiet.data.remote.api.PapagoApiService
import kr.com.chappiet.data.remote.request.ConsumePromotionDTO
import kr.com.chappiet.data.remote.response.ConsumeWhisperAPIResponseWrapper
import kr.com.chappiet.data.remote.response.DeepLCheckUsageAndLimitsResponse
import kr.com.chappiet.data.remote.response.PapagoResponseWrapper
import kr.com.chappiet.data.remote.response.RegistPromotionResponse
import kr.com.chappiet.data.remote.response.SignUpResponseWrapper
import kr.com.chappiet.data.remote.response.ConsumePromotionResponse
import kr.com.chappiet.data.remote.request.ConsumeWhisperAPIDTO
import kr.com.chappiet.data.remote.request.PurchaseDTO
import kr.com.chappiet.data.remote.request.RegistPromotionDTO
import kr.com.chappiet.di.ApiKeyProvider
import kr.com.chappiet.di.NetworkModule.Companion.DEEPL_REQUEST_AUTH_KEY
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkRepository @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider,
    private val papagoApiService: PapagoApiService,
    private val deepLApiService: DeepLApiService,
    private val deepLPROApiService: DeepLPROApiService,
    private val chappieTFunctionService: ChappieTFunctionService
) {
    fun papagoTranslateRequest(text:String, source:String, target:String): Flow<ApiResult<PapagoResponseWrapper>> =
        handleFlowApi {
            papagoApiService.papagoTranslateRequest(source,target,text)
        }

    fun deepLCheckUsageRequest(authKey:String): Flow<ApiResult<DeepLCheckUsageAndLimitsResponse>> = handleFlowApi {
        deepLApiService.deepLCheckUsageAndLimits("$DEEPL_REQUEST_AUTH_KEY $authKey")
    }

    fun deepLProCheckUsageRequest(authKey:String): Flow<ApiResult<DeepLCheckUsageAndLimitsResponse>> = handleFlowApi {
        deepLPROApiService.deepLCheckUsageAndLimits("$DEEPL_REQUEST_AUTH_KEY $authKey")
    }

    fun deepLTranslateRequest(text:String, source:String, target:String): Flow<ApiResult<DeepLResponseWrapper>> =
        handleFlowApi {
            // DeepL은 target_lang을 UpperCase로 받는다.
            // https://www.deepl.com/ko/docs-api/translate-text/translate-text
            deepLApiService.deepLTranslateRequest(target.uppercase(),text,"$DEEPL_REQUEST_AUTH_KEY ${apiKeyProvider.userDeeplAuthKey}")
        }

    fun deepLPROTranslateRequest(text:String, source:String, target:String): Flow<ApiResult<DeepLResponseWrapper>> =
        handleFlowApi {
            deepLPROApiService.deepLPROTranslateRequest(target.uppercase(),text,"$DEEPL_REQUEST_AUTH_KEY ${apiKeyProvider.userDeeplProAuthKey}")
        }

    fun chappieTSignUpRequest(idToken:String,user: User): Flow<ApiResult<SignUpResponseWrapper>> = handleFlowApi {
        chappieTFunctionService.signUpRequest("Bearer $idToken",user)
    }

    fun chappieTPurchaseVerifyRequest(idToken:String, purchaseDTO: PurchaseDTO): Flow<ApiResult<ChappieTCommonResponseWrapper>> = handleFlowApi {
        chappieTFunctionService.verifyPurchase("Bearer $idToken", purchaseDTO)
    }

    fun chappieTConsumeWhisperAPI(idToken:String, consumeWhisperAPIDTO: ConsumeWhisperAPIDTO): Flow<ApiResult<ConsumeWhisperAPIResponseWrapper>> = handleFlowApi {
        chappieTFunctionService.consumeWhisperAPI("Bearer $idToken",consumeWhisperAPIDTO)
    }

    fun chappieTRegistPromotion(idToken:String, promotionId: String): Flow<ApiResult<RegistPromotionResponse>> = handleFlowApi {
        chappieTFunctionService.registPromotion("Bearer $idToken",RegistPromotionDTO(promotionId))
    }

    fun chappieTConsumePromotion(idToken:String, promotionId: String): Flow<ApiResult<ConsumePromotionResponse>> = handleFlowApi {
        chappieTFunctionService.consumePromotion("Bearer $idToken", ConsumePromotionDTO(promotionId))
    }
}

fun <T : Any> handleFlowApi(execute: suspend () -> T): Flow<ApiResult<T>> = flow {
    emit(ApiResult.Loading)
    val result = try {
        ApiResult.Success(execute())
    } catch (e: HttpException) {
        ApiResult.Fail.Error(e=e, code = e.code(), message = e.message())
    } catch (e: Exception) {
        ApiResult.Fail.Exception(e = e, message = e.message)
    }
    emit(result)
}
