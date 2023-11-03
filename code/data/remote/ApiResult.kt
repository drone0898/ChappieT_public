package kr.com.chappiet.data.remote

import com.aallam.openai.api.exception.AuthenticationException
import com.aallam.openai.api.exception.OpenAIHttpException
import com.aallam.openai.api.exception.OpenAIIOException
import com.aallam.openai.api.exception.OpenAIServerException
import com.aallam.openai.api.exception.OpenAITimeoutException
import com.aallam.openai.api.exception.PermissionException
import com.aallam.openai.api.exception.RateLimitException
import kr.com.chappiet.R
import java.io.IOException

sealed class ApiResult<out T> {
    object Loading : ApiResult<Nothing>()
    data class Success<out T>(val data: T) : ApiResult<T>()
    sealed class Fail : ApiResult<Nothing>() {
        data class Error(val e:Throwable, val  code: Int,val message: String?) : Fail()
        data class Exception(val e:Throwable, val code:Int? = null, val message: String? = null) : Fail()
    }
}

// reified : 인라인(inline) 함수와 reified 키워드를 함께 사용하면 T type에 대해서 런타임에 접근할 수 있게 해줌.
inline fun <reified T : Any> ApiResult<T>.onLoading(action: () -> Unit) {
    if (this is ApiResult.Loading) action()
}

inline fun <reified T : Any> ApiResult<T>.onSuccess(action: (data: T) -> Unit) {
    if (this is ApiResult.Success) action(data)
}

inline fun <reified T: Any> ApiResult<T>.onFailure(action: (e: Throwable) -> Unit) {
    if(this is ApiResult.Fail.Error) action(e)
    else if(this is ApiResult.Fail.Exception) action(e)
}

inline fun <reified T : Any> ApiResult<T>.onError(action: (code: Int, message: String?) -> Unit) {
    if (this is ApiResult.Fail.Error) action(code, message)
}

inline fun <reified T : Any> ApiResult<T>.onException(action: (e: Throwable) -> Unit) {
    if (this is ApiResult.Fail.Exception) action(e)
}

val <T> ApiResult<T>.isSuccess: Boolean
    get() = this is ApiResult.Success

val <T> ApiResult<T>.isFailure: Boolean
    get() = this is ApiResult.Fail

val <T> ApiResult<T>.state: ApiResultState
    get() = when (this) {
        is ApiResult.Loading -> ApiResultState.LOADING
        is ApiResult.Success -> ApiResultState.SUCCESS
        is ApiResult.Fail -> when (this) {
            is ApiResult.Fail.Error -> {
                when (code) {
                    401, 403 -> ApiResultState.WRONG_API_KEY
                    429 -> ApiResultState.TOO_MANY_REQUEST
                    456 -> ApiResultState.LIMIT_USAGE
                    500 -> ApiResultState.INTERNAL_SERVER_ERROR
                    else -> ApiResultState.NETWORK_ERROR
                }
            }
            is ApiResult.Fail.Exception -> when(this.e) {
                is AuthenticationException, is PermissionException -> ApiResultState.WRONG_API_KEY
                is RateLimitException -> ApiResultState.LIMIT_USAGE
                is OpenAITimeoutException -> ApiResultState.TIME_OUT
                is OpenAIIOException -> ApiResultState.TOO_MANY_REQUEST
                is OpenAIHttpException -> ApiResultState.NETWORK_ERROR
                is OpenAIServerException -> ApiResultState.NETWORK_ERROR
                else -> ApiResultState.UNKNOWN_ERROR
            }
        }
    }

enum class ApiResultState (
    val message:Int
) {
    SUCCESS(R.string.success),
    LOADING(R.string.loading),
    WRONG_API_KEY(R.string.wrong_api_key),
    TOO_MANY_REQUEST(R.string.too_many_request),
    LIMIT_USAGE(R.string.limit_usage),
    TIME_OUT(R.string.time_out),
    INTERNAL_SERVER_ERROR(R.string.internal_server_error),
    NETWORK_ERROR(R.string.network_error),
    UNKNOWN_ERROR(R.string.unknown_error)
}