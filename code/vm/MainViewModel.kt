package kr.com.chappiet.vm

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.github.pemistahl.lingua.api.Language
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.smartlook.android.core.api.Smartlook
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.com.chappiet.R
import kr.com.chappiet.data.model.ApiKey
import kr.com.chappiet.data.model.ApiKeyType
import kr.com.chappiet.data.model.DeviceProfile
import kr.com.chappiet.data.model.GooglePaymentProduct
import kr.com.chappiet.data.model.InitData
import kr.com.chappiet.data.model.PaymentProductType
import kr.com.chappiet.data.model.SignUpData
import kr.com.chappiet.data.model.User
import kr.com.chappiet.data.model.asAppSettings
import kr.com.chappiet.data.model.asChappieTAppTranslateSettings
import kr.com.chappiet.data.model.asGooglePaymentProduct
import kr.com.chappiet.data.model.asUser
import kr.com.chappiet.data.remote.onError
import kr.com.chappiet.data.remote.onException
import kr.com.chappiet.data.remote.onFailure
import kr.com.chappiet.data.remote.onSuccess
import kr.com.chappiet.data.remote.response.Promotion
import kr.com.chappiet.data.remote.request.asPurchaseDTO
import kr.com.chappiet.data.remote.state
import kr.com.chappiet.di.ApiKeyProvider
import kr.com.chappiet.domain.FireStoreRepository
import kr.com.chappiet.domain.FirebaseAuthRepository
import kr.com.chappiet.domain.LocalRepository
import kr.com.chappiet.domain.NetworkRepository
import kr.com.chappiet.domain.OpenAIRepository
import kr.com.chappiet.ui.activity.AppInfo
import kr.com.chappiet.ui.component.BottomSheetItemType
import kr.com.chappiet.util.TOOL_DEEPL
import kr.com.chappiet.util.TOOL_GPT
import kr.com.chappiet.util.listOfSourceLanguages
import kr.com.chappiet.util.listOfTargetLanguages
import kr.com.chappiet.util.listOfTranslateTools
import kr.com.chappiet.vm.RequestToActivity.RequestType
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val fireStoreRepository: FireStoreRepository,
    private val firebaseAuthRepository: FirebaseAuthRepository,
    private val networkRepository: NetworkRepository,
    private val localRepository: LocalRepository,
    private val openAIRepository: OpenAIRepository,
    private val apiKeyProvider: ApiKeyProvider
) : ViewModel() {

    private lateinit var deviceProfile: DeviceProfile

    private val _initData = MutableStateFlow(InitData())
    val initData: StateFlow<InitData> = _initData.asStateFlow()

    private val _uiState = MutableStateFlow(ChappieTHomeUIState())
    val uiState: StateFlow<ChappieTHomeUIState> = _uiState.asStateFlow()

    private val  _popUpUiState = MutableStateFlow(ChappieTPopUpUIState())
    val popUpUiState: StateFlow<ChappieTPopUpUIState> = _popUpUiState.asStateFlow()

    private val _requestToActivity:MutableStateFlow<RequestToActivity> = MutableStateFlow(
        RequestToActivity()
    )
    val requestToActivity = _requestToActivity.asStateFlow()

    private val _appTranslateSettings = MutableStateFlow(ChappieTAppTranslateSettings())
    val appTranslateSettings = _appTranslateSettings.asStateFlow()

    private val _googlePaymentProductList = MutableStateFlow<List<GooglePaymentProduct>?>(null)
    val googlePaymentProductList = _googlePaymentProductList.asStateFlow()

    private val updateAuthTrigger = MutableSharedFlow<Unit>()

    init {
        viewModelScope.launch {
            launch {
                localRepository.getProfile()
                    .catch { ex ->
                        suspendSetUiState(error = ex.message)
                    }.collect { profile ->
                        profile?.let {
                            deviceProfile = it
                            _appTranslateSettings.value =
                                it.appSettings.asChappieTAppTranslateSettings()
                        }
                    }
            }
            launch {
                fireStoreRepository.readInitData().retryWhen { _, attempt ->
                    if(attempt < 3) {
                        delay(5000)
                        true
                    } else {
                        false
                    }
                }.collect {result ->
                    result.onSuccess {
                        _initData.value = it
                    }
                    result.onFailure {
                        setRequest(RequestType.REQUEST_FAIL_INIT_DATA)
                    }
                }
            }
            launch {
                appTranslateSettings.debounce(300).collect {
                    if (!this@MainViewModel::deviceProfile.isInitialized) {
                        localRepository.saveDeviceProfile(DeviceProfile())
                    } else {
                        deviceProfile = deviceProfile.copy(
                            appSettings = it.asAppSettings()
                        )
                        localRepository.saveDeviceProfile(deviceProfile)
                    }
                }
            }
            launch {
                var firstTime = true
                updateAuthTrigger
                    .debounce(if(firstTime) 0L else 30000)
                    .collect {
                        firstTime = false
                        updateAuthInternal()
                    }
            }
        }
    }

    private fun updateAuthInternal() {
        ....
    }

    /**
     * debounce update auth
     */
    fun updateAuth() {
        viewModelScope.launch {
            updateAuthTrigger.emit(Unit)
        }
    }

    suspend fun suspendSetUiState(
        user: User? = this.uiState.value.user,
        addApiKey: ApiKey = this.uiState.value.addApiKey,
        addApiKeyStep: Int = this.uiState.value.addApiKeyStep,
        showNavigation: Boolean = this.uiState.value.showNavigation,
        sheetType: BottomSheetItemType? = this.uiState.value.sheetType,
        sheetItemList: List<Pair<Any, String>>? = this.uiState.value.sheetItemList,
        serviceAlive: Boolean = this.uiState.value.serviceAlive,
        toastMessage: Int? = this.uiState.value.toastMessage,
        toastParam: String? = this.uiState.value.toastParam,
        error: String? = this.uiState.value.error,
        loading: Boolean = this.uiState.value.loading
    ) {
        withContext(Dispatchers.Main) {
            setUiState(
                user = user,
                addApiKey = addApiKey,
                addApiKeyStep = addApiKeyStep,
                showNavigation = showNavigation,
                sheetType = sheetType,
                sheetItemList = sheetItemList,
                serviceAlive = serviceAlive,
                toastMessage = toastMessage,
                toastParam = toastParam,
                error = error,
                loading = loading
            )
        }
    }
    fun setUiState(
        user: User? = this.uiState.value.user,
        addApiKey: ApiKey = this.uiState.value.addApiKey,
        addApiKeyStep: Int = this.uiState.value.addApiKeyStep,
        showNavigation: Boolean = this.uiState.value.showNavigation,
        sheetType: BottomSheetItemType? = this.uiState.value.sheetType,
        sheetItemList: List<Pair<Any, String>>? = this.uiState.value.sheetItemList,
        serviceAlive: Boolean = this.uiState.value.serviceAlive,
        toastMessage: Int? = this.uiState.value.toastMessage,
        toastParam: String? = this.uiState.value.toastParam,
        error: String? = this.uiState.value.error,
        loading: Boolean = this.uiState.value.loading
    ) {
        _uiState.value = uiState.value.copy(
            user = user,
            addApiKey = addApiKey,
            addApiKeyStep = addApiKeyStep,
            showNavigation = showNavigation,
            sheetType = sheetType,
            sheetItemList = sheetItemList,
            serviceAlive = serviceAlive,
            toastMessage = toastMessage,
            toastParam = toastParam,
            error = error,
            loading = loading
        )
    }
    fun setPopUpUiState(
        showPopup: Boolean = false,
        popUpTitle: String? = null,
        popUpContent: String? = null,
        popUpAnnotatedString: AnnotatedString? = null,
        popUpOk: String? = null,
        popUpCancel: String? = null,
        cancelable: Boolean = false,
        properties: DialogProperties? = null,
        onClickOK: () -> Unit = { },
        onClickCancel: () -> Unit = { }
    ) {
        _popUpUiState.value = ChappieTPopUpUIState(
            showPopup,
            popUpTitle,
            popUpContent,
            popUpAnnotatedString,
            popUpOk,
            popUpCancel,
            properties,
            cancelable,
            onClickOK,
            onClickCancel
        )
    }

    private suspend fun suspendSetRequest(
        requestType: RequestType? = requestToActivity.value.requestType,
        requestParam: Any? = requestToActivity.value.requestParam,
        isHandled: Boolean = false
    ) {
        withContext(Dispatchers.Main) {
            setRequest(
                requestType = requestType,
                requestParam = requestParam,
                isHandled = isHandled
            )
        }
    }

    fun setRequest(
        requestType: RequestType? = requestToActivity.value.requestType,
        requestParam: Any? = requestToActivity.value.requestParam,
        isHandled: Boolean = false
    ) {
        _requestToActivity.value = requestToActivity.value.copy(
            requestType = requestType,
            requestParam = requestParam,
            isHandled = isHandled
        )
    }

    fun setSettings(
        isPermissionGuided: Boolean = appTranslateSettings.value.isPermissionGuided,
        maxVoiceActiveLength: Int = appTranslateSettings.value.maxVoiceActiveLength,
        minVoiceActiveLength: Int = appTranslateSettings.value.minVoiceActiveLength,
        rememberPreviousContext: Boolean = appTranslateSettings.value.rememberPreviousContext,
        voiceDetection: Boolean = appTranslateSettings.value.voiceDetection,
        noiseCancel: Boolean = appTranslateSettings.value.noiseCancel,
        noiseCancelDB:Int = appTranslateSettings.value.noiseCancelDB,
        voiceFilter: Boolean = appTranslateSettings.value.voiceFilter,
        gptTranslateOptionPrompt: String = appTranslateSettings.value.gptTranslateOptionPrompt
    ) {
        _appTranslateSettings.value = appTranslateSettings.value.copy(
            isPermissionGuided = isPermissionGuided,
            maxVoiceActiveLength = maxVoiceActiveLength,
            minVoiceActiveLength = minVoiceActiveLength,
            rememberPreviousContext = rememberPreviousContext,
            voiceDetection = voiceDetection,
            noiseCancel = noiseCancel,
            noiseCancelDB = noiseCancelDB,
            voiceFilter = voiceFilter,
            gptTranslateOptionPrompt = gptTranslateOptionPrompt
        )
    }

    fun setCreditProductList(list: List<ProductDetails>?, callBack:()->Unit) {
        _googlePaymentProductList.value =
            list?.map { it.asGooglePaymentProduct(PaymentProductType.CREDIT) }
                ?.sortedBy { it.productId?.replace("credit", "")?.toIntOrNull() }
        callBack()
    }

    fun switchLanguage() {
        if (appTranslateSettings.value.sourceLanguage != Language.UNKNOWN) {
            _appTranslateSettings.value = appTranslateSettings.value.copy(
                sourceLanguage = appTranslateSettings.value.targetLanguage,
                targetLanguage = appTranslateSettings.value.sourceLanguage
            )
        }
    }

    fun selectSheetItem(item: Pair<Any, String>) {
        when (uiState.value.sheetType) {
            BottomSheetItemType.SELECT_SOURCE_LANGUAGE -> {
                _appTranslateSettings.value =
                    appTranslateSettings.value.copy(
                        sourceLanguage = item.first as Language
                    )
            }

            BottomSheetItemType.SELECT_TARGET_LANGUAGE -> {
                _appTranslateSettings.value =
                    appTranslateSettings.value.copy(
                        targetLanguage = item.first as Language
                    )
            }

            BottomSheetItemType.SELECT_EXECUTE_APP -> {
                if (item.first.javaClass == AppInfo::class.java) {
                    val appInfo: AppInfo = item.first as AppInfo
                    _appTranslateSettings.value =
                        appTranslateSettings.value.copy(
                            selectedApp = appInfo.packageName,
                            selectedAppName = item.second
                        )
                }
            }

            BottomSheetItemType.SELECT_TRANSLATE_TOOL -> {
                if (item.first.javaClass == String::class.java) {
                    _appTranslateSettings.value =
                        appTranslateSettings.value.copy(translateTool = item.first as String)
                    // 선택한 번역 툴을 API Key 추가 화면의 기본값으로 적용.
                    ApiKeyType.values().find { it.tool == item.first }?.let { apiKeyType->
                        uiState.value.addApiKey.let {
                            setUiState(addApiKey = it.copy(
                                type = apiKeyType
                            ))
                        }
                    }
                }
            }

            BottomSheetItemType.SELECT_API_TYPE -> {
                if(item.first.javaClass == ApiKeyType::class.java) {
                    val apiKeyType: ApiKeyType = item.first as ApiKeyType
                    uiState.value.addApiKey.let {
                        setUiState(addApiKey = it.copy(
                            type = apiKeyType
                        ))
                    }
                }
            }

            BottomSheetItemType.SELECT_API_TYPE_LINK -> {
                if(item.first.javaClass == ApiKeyType::class.java) {
                    val apiKeyType: ApiKeyType = item.first as ApiKeyType
                    _requestToActivity.value = RequestToActivity(
                        RequestType.REQUEST_WEB,
                        apiKeyType.keyLink
                    )
                }
            }

            BottomSheetItemType.SELECT_API_TYPE_USAGE -> {
                if(item.first.javaClass == ApiKeyType::class.java) {
                    val apiKeyType: ApiKeyType = item.first as ApiKeyType
                    _requestToActivity.value = RequestToActivity(
                        RequestType.REQUEST_WEB,
                        apiKeyType.keyUsageLink
                    )
                }
            }

            BottomSheetItemType.SELECT_CALL_BACK -> {
                try{
                    @Suppress("UNCHECKED_CAST") val callback = item.first as ()-> Unit
                    callback()
                } catch (e:Exception) {
                    Timber.e(e)
                }
            }

            else -> {}
        }
    }

    fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        ....
    }

    fun signUpRequest(email:String?, displayName: String?, phone: String?, firebaseUser: FirebaseUser?,
                      signUpCompleteCallback:() -> Unit) {
        if(uiState.value.loading) { return }
        setUiState(loading = true)
        firebaseUser?.getIdToken(true)?.addOnCompleteListener { task ->
            task.result?.token?.let { it ->
                viewModelScope.launch(Dispatchers.IO) {
                    networkRepository.chappieTSignUpRequest(
                        it,
                        firebaseUser.asUser(email,displayName,phone)
                    ).collect { result ->
                        ....
                    }
                }
            } ?: run {
                setUiState(loading = false)
            }
        }
    }
    fun purchaseVerify(purchases: List<Purchase>, callBack: () -> Unit) {
        viewModelScope.launch (Dispatchers.IO) {
            firebaseAuthRepository.getIdToken()?.let {
                ....
            } ?: run {
                callBack()
            }
        }
    }
    fun onClickRegistPromotion(promotionId:String,
                               errorTitle:String) {
        if(uiState.value.loading) { return }
        viewModelScope.launch {
            suspendSetUiState(loading = true)
            firebaseAuthRepository.getIdToken()?.let {
                networkRepository.chappieTRegistPromotion(
                    it,
                    promotionId
                ).collect {result ->
                    result.onSuccess { res ->
                        updateAuth()
                        suspendSetUiState(loading = false)
                        if(res.success) {
                            setPopUpUiState(
                                showPopup = true,
                                popUpTitle = "[${res.promotion?.content}]",
                                popUpContent = res.message
                            )
                        } else {
                            setPopUpUiState(
                                showPopup = true,
                                popUpTitle = errorTitle,
                                popUpContent = res.message
                            )
                        }
                    }
                    result.onFailure { fail->
                        updateAuth()
                        suspendSetUiState(loading = false)
                        setPopUpUiState(
                            showPopup = true,
                            popUpTitle = errorTitle,
                            popUpContent = fail.message
                        )
                    }
                }
            } ?: kotlin.run {
                suspendSetUiState(loading = false)
                suspendSetRequest(RequestType.REQUEST_NEED_RE_LOGIN_POPUP)
            }
        }
    }
    fun onClickConsumePromotion(item: Promotion,
                                useMessage:String,
                                errorTitle:String) {
        if(uiState.value.loading) { return }
        viewModelScope.launch {
            suspendSetUiState(loading = true)
            firebaseAuthRepository.getIdToken()?.let {
                networkRepository.chappieTConsumePromotion(
                    it,
                    item.promotionId
                ).collect {result ->
                    result.onSuccess { res ->
                        updateAuth()
                        suspendSetUiState(loading = false)
                        if(res.success) {
                            setPopUpUiState(
                                showPopup = true,
                                popUpTitle = "[${item.content}]",
                                popUpContent = useMessage
                            )
                        } else {
                            setPopUpUiState(
                                showPopup = true,
                                popUpTitle = "[${item.content}]",
                                popUpContent = res.message
                            )
                        }
                    }
                    result.onFailure { fail->
                        updateAuth()
                        suspendSetUiState(loading = false)
                        setPopUpUiState(
                            showPopup = true,
                            popUpTitle = errorTitle,
                            popUpContent = fail.message
                        )
                    }
                }
            } ?: kotlin.run {
                suspendSetUiState(loading = false)
                suspendSetRequest(RequestType.REQUEST_NEED_RE_LOGIN_POPUP)
            }
        }
    }

    fun onEnableApiKey(i:Int, item:ApiKey, enable:Boolean) {
        updateApiKey(
            { list:List<ApiKey> ->
                list.mapIndexed { index, apiKey ->
                    if(index == i){
                        // 변경한 아이템의 값 변경
                        apiKey.copy(enabled = enable)
                    } else if (enable && apiKey.type == item.type && apiKey.enabled) {
                        // 활성화 했을 때 같은 타입 중 이미 활성화 된 타입이 있었다면 비활성화
                        apiKey.copy(enabled = false)
                    } else {
                        apiKey
                    }
                }.toList()
            }
        )
    }
    fun addApiKey(callBack: (Boolean) -> Unit) {
        if(uiState.value.loading){return}
        viewModelScope.launch (Dispatchers.IO) {
            suspendSetUiState(loading = true)
            when(uiState.value.addApiKey.type) {
                ApiKeyType.OPEN_AI -> {
                    openAIRepository.testApiKey(uiState.value.addApiKey.key).collect {result ->
                        result.onSuccess {
                            suspendSetUiState(loading = false)
                            addValidApiKey(callBack)
                        }
                        result.onFailure {
                            setRequest(RequestType.REQUEST_FAIL_VERIFY_API_KEY,result.state)
                            suspendSetUiState(loading = false)
                        }
                    }
                }
                ApiKeyType.DEEPL -> {
                    networkRepository.deepLCheckUsageRequest(uiState.value.addApiKey.key).collect {
                        result ->
                        result.onSuccess {
                            suspendSetUiState(loading = false)
                            addValidApiKey(callBack)
                        }
                        result.onFailure {
                            setRequest(RequestType.REQUEST_FAIL_VERIFY_API_KEY,result.state)
                            suspendSetUiState(loading = false)
                            Timber.e(it)
                        }
                    }
                }
                ApiKeyType.DEEPL_PRO -> {
                    networkRepository.deepLProCheckUsageRequest(uiState.value.addApiKey.key).collect {
                            result ->
                        result.onSuccess {
                            addValidApiKey(callBack)
                        }
                        result.onFailure {
                            setRequest(RequestType.REQUEST_FAIL_VERIFY_API_KEY,result.state)
                            suspendSetUiState(loading = false)
                            Timber.e(it)
                        }
                    }
                }
                else -> {
                    suspendSetUiState(loading = false, toastMessage = R.string.not_supported_api_type)
                }
            }
        }
    }

    private suspend fun addValidApiKey(callBack: (Boolean) -> Unit) {
        suspendSetUiState(addApiKey =
        uiState.value.addApiKey.copy(
            creationDate = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yy/MM/dd")).toString())
        )
        updateApiKey(
            { list:List<ApiKey> ->
                list.map { k ->
                    if (k.type == uiState.value.addApiKey.type) {
                        k.copy(enabled = false)
                    } else {
                        k
                    }
                }.toMutableList().apply { add(uiState.value.addApiKey) }.toList()
            },
            {
                callBack(true)
            }
        )
    }

    fun modifyApiKey(index:Int, name:String, key:String, callBack: () -> Unit) {
        updateApiKey(
            { list:List<ApiKey> ->
                list.toMutableList().apply {
                    this[index] = list[index].copy(name=name,key=key)
                }.toList()
            },
            callBack
        )
    }
    fun deleteApiKey(index:Int) {
        updateApiKey(
            { list:List<ApiKey> ->
                list.toMutableList().apply { removeAt(index) }.toList()
            }
        )
    }

    private fun updateApiKey(update:(List<ApiKey>) -> List<ApiKey>, callBack: () -> Unit = {}) {
        if(uiState.value.loading) { return }
        viewModelScope.launch (Dispatchers.IO) {
            suspendSetUiState(loading = true)
            firebaseAuthRepository.getAuth().currentUser?.let {
                fireStoreRepository.updateUserApiKeys(
                    it.uid,
                    update(uiState.value.user?.apiKeys ?: listOf())
                ).collect { result->
                    result.onSuccess {
                        updateAuth()
                        suspendSetUiState(loading = false)
                        withContext(Dispatchers.Main) {
                            callBack()
                        }
                    }
                    result.onFailure {
                        suspendSetUiState(loading = false,
                            toastMessage = R.string.unknown_error)
                    }
                }
            } ?: kotlin.run {
                suspendSetUiState(loading = false)
                suspendSetRequest(RequestType.REQUEST_NEED_RE_LOGIN_POPUP)
            }
        }
    }

    fun logOut() {
        firebaseAuthRepository.logout { setUiState(user = null) }
    }
}

/**
 * ui 상태 클래스
 */
data class ChappieTHomeUIState(
    val user: User? = null,
    val addApiKeyStep: Int = 1,
    val addApiKey: ApiKey = DEFAULT_ADD_API_KEY,
    val serviceAlive: Boolean = false,
    val showNavigation: Boolean = true,
    val sheetType: BottomSheetItemType? = null,
    val sheetItemList: List<Pair<Any, String>>? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val toastMessage: Int? = null,
    val toastParam: String? = null
) {
    companion object {
        val DEFAULT_ADD_API_KEY =
            ApiKey(TOOL_DEEPL,"",ApiKeyType.DEEPL,null,null,true)
    }
}

data class ChappieTPopUpUIState(
    val showPopup: Boolean = false,
    val popUpTitle: String? = null,
    val popUpContent: String? = null,
    val popUpAnnotatedString: AnnotatedString? = null,
    val popUpOk: String? = null,
    val popUpCancel: String? = null,
    val popupProperties: DialogProperties? = null,
    val cancelable: Boolean = false,
    val onClickOK: () -> Unit = {},
    val onClickCancel: () -> Unit = {}
)

/**
 * view에 특정 작업을 요청해야할 때
 * @see RequestType
 */
data class RequestToActivity(
    val requestType: RequestType? = null,
    val requestParam: Any? = null,
    val isHandled:Boolean = false
) {
    enum class RequestType {
        REQUEST_FAIL_VERIFY_API_KEY,
        REQUEST_FAIL_INIT_DATA,
        REQUEST_NEED_RE_LOGIN_POPUP,
        REQUEST_PURCHASE_ERROR_POPUP,
        REQUEST_SIGN_UP_FAIL_POPUP,
        REQUEST_SIGN_UP,
        REQUEST_WEB
    }
}

/**
 * 저장 되어야 하는 상태
 *
 * @see kr.com.chappiet.data.model.AppSettings
 *
 * @param translateTool See in Constants.kt for possible values.
 */
data class ChappieTAppTranslateSettings(
    val isPermissionGuided: Boolean = false,
    val translateTool: String = TOOL_GPT,
    val sourceLanguage: Language = Language.UNKNOWN,
    val targetLanguage: Language = Language.KOREAN,
    val selectedApp: String? = null,
    val selectedAppName: String? = null,
    val maxVoiceActiveLength: Int = MAX_VOICE_ACTIVE_LENGTH_DEFAULT,
    val minVoiceActiveLength: Int = MIN_VOICE_ACTIVE_LENGTH_DEFAULT,
    val rememberPreviousContext: Boolean = REMEMBER_PREVIOUS_CONTEXT_DEFAULT,
    val voiceDetection: Boolean = VOICE_DETECTION_DEFAULT,
    val noiseCancel: Boolean = NOISE_CANCEL_DEFAULT,
    val noiseCancelDB: Int = NOISE_CANCEL_DB_DEFAULT,
    val voiceFilter: Boolean = VOICE_FILTER_DEFAULT,
    val gptTranslateOptionPrompt: String = GPT_TRANSLATE_OPTION_PROMPT_DEFAULT
) {
    init {
        require(listOfTranslateTools.keys.contains(translateTool)) {
            "Invalid translateTool: $translateTool"
        }
        require(listOfSourceLanguages.keys.contains(sourceLanguage)) {
            "Invalid sourceLanguage: $sourceLanguage"
        }
        require(listOfTargetLanguages.keys.contains(targetLanguage)) {
            "Invalid targetLanguage: $targetLanguage"
        }
    }

    companion object {
        const val MAX_VOICE_ACTIVE_LENGTH_DEFAULT = 6000
        const val MIN_VOICE_ACTIVE_LENGTH_DEFAULT = 3000
        const val REMEMBER_PREVIOUS_CONTEXT_DEFAULT = false
        const val VOICE_DETECTION_DEFAULT = true
        // afftdn range [-80 ~ -20]
        const val NOISE_CANCEL_DB_DEFAULT = 20
        const val NOISE_CANCEL_DEFAULT = false
        const val VOICE_FILTER_DEFAULT = false
        const val GPT_TRANSLATE_OPTION_PROMPT_DEFAULT = "Your only tasks are to naturally translate to @{targetLanguage} and to correct any transcription errors"
        // Your tasks are to naturally translate to @{targetLanguage} and to correct any transcription errors 18tokens
        // Naturally translate to @{targetLanguage} and correct any transcription errors 13tokens
    }
}