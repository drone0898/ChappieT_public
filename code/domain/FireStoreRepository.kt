package kr.com.chappiet.domain

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kr.com.chappiet.data.model.ApiKey
import kr.com.chappiet.data.model.InitData
import kr.com.chappiet.data.model.IntroCheck
import kr.com.chappiet.data.model.Notice
import kr.com.chappiet.data.model.NoticeWrapper
import kr.com.chappiet.data.model.User
import kr.com.chappiet.data.model.User.Companion.USER_API_KEYS
import kr.com.chappiet.data.remote.ApiResult
import kr.com.chappiet.util.COMMON_TIMEOUT
import kr.com.chappiet.util.classToMapGson
import kr.com.chappiet.util.mapToClassGson
import retrofit2.HttpException
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * firebase store의
 * addOnSuccessListener와 addOnFailureListener를 suspend function에서 사용할 경우 메모리 누수 가능성이 존재합니다.
 * Firebase의 Callback이 Coroutine의 생명 주기를 알지 못하기 때문입니다.
 *
 * Coroutine과 Callback을 함께 사용하려면 suspendCancellableCoroutine나 suspendCoroutine를 사용해야 합니다.
 * 이 함수들은 Coroutine과 Callback 간의 생명 주기를 동기화해 메모리 누수를 방지합니다.
 */
class FireStoreRepository @Inject constructor(
    val firebaseAuthRepository: FirebaseAuthRepository
) {

    private val db = Firebase.firestore

    fun readNotice(idList: List<String>): Flow<Result<List<Notice>>> = handleFlowWithTimeout {
        suspendCancellableCoroutine { continuation ->
            db.collection(...)
                .document(...)
                .get()
                .addOnSuccessListener {
                    it.data?.let {
                            it1 -> mapToClassGson(it1, NoticeWrapper::class.java)
                    }?. let { array ->
                        continuation.resume(
                            ...
                        { index ->
                            ...
                        })
                    } ?: run {
                        throw NullDataException()
                    }
                }.addOnFailureListener { e->
                    FirebaseCrashlytics.getInstance().recordException(e)
                    Timber.e(e)
                    continuation.resumeWithException(e)
                }
        }
    }
    fun readUser(uid: String): Flow<Result<User>> = handleFlowWithTimeout {
            suspendCancellableCoroutine { continuation ->
                db.collection(FIRE_STORE_COLLECTION_USERS)
                    .document(uid)
                    .get()
                    .addOnSuccessListener {
                        ...
                    }.addOnFailureListener {e->
                        FirebaseCrashlytics.getInstance().recordException(e)
                        Timber.e(e)
                        continuation.resumeWithException(e)
                    }
            }
        }

    fun readIntroCheck(): Flow<Result<IntroCheck>> = handleFlowWithTimeout {
        suspendCancellableCoroutine { continuation ->
            db.collection(...)
                .document(...)
                .get()
                .addOnSuccessListener {
                    it.data?.let { it1 ->
                        mapToClassGson(it1, IntroCheck::class.java)
                    }?.let { result ->
                        continuation.resume(result)
                    } ?: kotlin.run {
                        continuation.resumeWithException(NullDataException())
                    }
                }.addOnFailureListener { e ->
                    FirebaseCrashlytics.getInstance().recordException(e)
                    Timber.e(e)
                    continuation.resumeWithException(e)
                }
        }
    }

    fun readInitData(): Flow<Result<InitData>> = handleFlowWithTimeout {
        suspendCancellableCoroutine { continuation ->
            db.collection(...)
                .document(...)
                .get()
                .addOnSuccessListener {
                    it.data?.let { it1 ->
                        mapToClassGson(it1, InitData::class.java)
                    }?.let { result ->
                        continuation.resume(result)
                    } ?: kotlin.run {
                        continuation.resumeWithException(NullDataException())
                    }
                }.addOnFailureListener { e ->
                    FirebaseCrashlytics.getInstance().recordException(e)
                    Timber.e(e)
                    continuation.resumeWithException(e)
                }
        }
    }


    fun updateUserApiKeys(uid:String, apiKeys: List<ApiKey>): Flow<Result<Unit>> = handleFlowWithTimeout {
        suspendCancellableCoroutine { continuation->
            db.collection(FIRE_STORE_COLLECTION_USERS).document(uid).apply {
                ...
            }
        }
    }

    companion object {
        ...
    }
}
fun <T : Any> handleFlowWithTimeout(execute: suspend () -> T): Flow<Result<T>> = flow {
    withTimeoutOrNull(COMMON_TIMEOUT.toLong()) {
        val result = try {
            Result.success(execute())
        } catch (e: Exception) {
            Result.failure(e)
        }
        emit(result)
    } ?: emit(Result.failure(CancellationException("Operation timed out")))
}


class NullUserException : Exception("Null User Exception")
class NullDataException : Exception("Null Data Exception")