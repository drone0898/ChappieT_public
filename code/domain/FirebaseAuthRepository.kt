package kr.com.chappiet.domain

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.auth.ktx.auth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor(
) {
    private lateinit var auth: FirebaseAuth
    private lateinit var result: GetTokenResult
    private var token: String? = null


    fun startAuth():FirebaseAuth {
        auth = Firebase.auth
        return auth
    }

    fun getAuth():FirebaseAuth {
        return auth
    }

    private fun tokenIsExpired(): Boolean {
        return !(token!=null && this::result.isInitialized &&
                result.expirationTimestamp > System.currentTimeMillis())
    }

    suspend fun getIdToken(): String? {
        if (!tokenIsExpired()) {
            return token
        }
        return withContext(Dispatchers.IO) {
            try {
                auth.currentUser?.getIdToken(true)?.await()?.let {
                    this@FirebaseAuthRepository.result = it
                    token = result.token
                }
                token
            } catch (e: Exception) {
                null
            }
        }
    }

    fun logout(callback: (Result<Nothing?>) -> Unit) {
        auth.signOut()
        FirebaseCrashlytics.getInstance().setUserId("")
        callback(Result.success(null))
    }
}