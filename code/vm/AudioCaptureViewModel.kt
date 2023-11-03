package kr.com.chappiet.vm

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aallam.openai.api.exception.AuthenticationException
import com.aallam.openai.api.exception.OpenAIHttpException
import com.aallam.openai.api.exception.OpenAIIOException
import com.aallam.openai.api.exception.OpenAIServerException
import com.aallam.openai.api.exception.OpenAITimeoutException
import com.aallam.openai.api.exception.PermissionException
import com.aallam.openai.api.exception.RateLimitException
import com.github.pemistahl.lingua.api.IsoCode639_1
import com.github.pemistahl.lingua.api.Language
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.smartlook.android.core.api.Smartlook
import com.smartlook.android.core.api.model.Properties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.com.chappiet.R
import kr.com.chappiet.data.model.ApiKeyType
import kr.com.chappiet.data.model.DeviceProfile
import kr.com.chappiet.data.model.ScriptData
import kr.com.chappiet.data.model.TranscriptionWithAudioData
import kr.com.chappiet.data.model.User
import kr.com.chappiet.data.model.asAudioCaptureSettings
import kr.com.chappiet.data.model.asChappieTAppTranslateSettings
import kr.com.chappiet.data.remote.ApiResultState
import kr.com.chappiet.data.remote.onError
import kr.com.chappiet.data.remote.onException
import kr.com.chappiet.data.remote.onFailure
import kr.com.chappiet.data.remote.onLoading
import kr.com.chappiet.data.remote.onSuccess
import kr.com.chappiet.data.remote.request.ConsumeWhisperAPIDTO
import kr.com.chappiet.data.remote.state
import kr.com.chappiet.domain.AudioCaptureRepository
import kr.com.chappiet.domain.FireStoreRepository
import kr.com.chappiet.domain.FirebaseAuthRepository
import kr.com.chappiet.domain.LocalRepository
import kr.com.chappiet.domain.NetworkRepository
import kr.com.chappiet.domain.NullUserException
import kr.com.chappiet.domain.OpenAIRepository
import kr.com.chappiet.util.TOOL_DEEPL
import kr.com.chappiet.util.TOOL_DEEPL_PRO
import kr.com.chappiet.util.TOOL_GOOGLE_TRANSLATE
import kr.com.chappiet.util.TOOL_GPT
import kr.com.chappiet.util.TOOL_PAPAGO
import kr.com.chappiet.util.VIEW_INVISIBLE
import kr.com.chappiet.util.VIEW_VISIBLE
import kr.com.chappiet.util.listOfTargetLanguages
import kr.com.chappiet.vm.DisplayOption.ORIGINAL
import kr.com.chappiet.vm.DisplayOption.ORIGINAL_TRANSLATION
import kr.com.chappiet.vm.DisplayOption.TRANSLATION
import timber.log.Timber
import java.io.File

@OptIn(FlowPreview::class)
class AudioCaptureViewModel constructor(
    private val firebaseAuthRepository: FirebaseAuthRepository,
    private val fireStoreRepository: FireStoreRepository,
    private val openAIRepository: OpenAIRepository,
    private val networkRepository: NetworkRepository,
    private val localRepository: LocalRepository,
    private val audioCaptureRepository: AudioCaptureRepository
) : ViewModel() {

    private lateinit var deviceProfile: DeviceProfile
    private val scriptDataList: MutableList<ScriptData?> = mutableListOf()

    private val detector =
        LanguageDetectorBuilder.fromLanguages(*listOfTargetLanguages.keys.toTypedArray()).build()
    private val scriptPrompt: StringBuilder = StringBuilder()

    private val _audioCaptureUIState: MutableStateFlow<AudioCaptureUIState> =
        MutableStateFlow(AudioCaptureUIState())
    val audioCaptureUIState = _audioCaptureUIState.asStateFlow()
    private val _audioCaptureSettings: MutableStateFlow<AudioCaptureSettings> =
        MutableStateFlow(AudioCaptureSettings())
    val audioCaptureSettings = _audioCaptureSettings.asStateFlow()

    private val _requestToService: MutableStateFlow<String?> = MutableStateFlow(null)
    val requestToService = _requestToService.asStateFlow()

    private val _translateRequestFlow: MutableStateFlow<Pair<TranscriptionWithAudioData, Int>?> =
        MutableStateFlow(null)
    private val translateRequestFlow = _translateRequestFlow.asStateFlow()

    private val _sttConsumeRequestFlow: MutableStateFlow<Long?> = MutableStateFlow(null)
    private val sttConsumeRequestFlow = _sttConsumeRequestFlow.asStateFlow()

    private val _saveCost: MutableStateFlow<Long> = MutableStateFlow(0L)
    private val saveCost = _saveCost.asStateFlow()

    private lateinit var speechToTextJob: Job // speech to text 작업 수행
    private lateinit var translateJob: Job // 번역 작업 수행
    private lateinit var translateProgressJob: Job // api 진행률 시각화 작업 수행
    private lateinit var sttConsumeJob: Job // stt credit 소모 작업 수행

    private lateinit var user:User

    init {
        viewModelScope.launch {
            launch {
                localRepository.getProfile()
                    .catch { ex ->
                        suspendSetUiState(error = ex.message)
                    }.collect { profile ->
                        profile?.let {
                            deviceProfile = it
                            _audioCaptureSettings.value =
                                it.appSettings.asAudioCaptureSettings()
                        }
                    }
            }
            launch {
                audioCaptureSettings.debounce(300).collect {
                    if (!this@AudioCaptureViewModel::deviceProfile.isInitialized) {
                        localRepository.saveDeviceProfile(DeviceProfile())
                    } else {
                        deviceProfile = deviceProfile.copy(
                            appSettings = deviceProfile.appSettings.copy(
                                textDarkMode = it.textDarkMode,
                                playActiveVoiceLottie = it.playActiveVoiceLottie,
                                displayOption = it.displayOption
                            )
                        )
                        localRepository.saveDeviceProfile(deviceProfile)
                    }
                }
            }
        }
    }

    /**
     * 서비스 실행시 설정한 값과 이전에 저장한 값을 가져온다.
     */
    fun getSettings() {
        viewModelScope.launch {
            localRepository.getProfile().filterNotNull().collect {
                _audioCaptureSettings.value = audioCaptureSettings.value.copy(
                    translateSettings = it.appSettings.asChappieTAppTranslateSettings()
                )
            }
        }
    }

    /**
     * credit이 충분한지 확인
     */
    fun initCheck(keepUse: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            firebaseAuthRepository.getAuth().currentUser?.let {
                fireStoreRepository.readUser(it.uid).collect { result->
                    result.onSuccess { user->
                        this@AudioCaptureViewModel.user = user
                        checkConsumeWhisperAPI(ConsumeWhisperAPIDTO(0), keepUse)
                    }
                    result.onFailure { e->
                        setErrorMessage(e.message)
                        _requestToService.value = STOP_SERVICE_REQ_AUTH
                        FirebaseCrashlytics.getInstance().log("init check consumeWhisper fail")
                        FirebaseCrashlytics.getInstance().recordException(e)
                    }
                }
            } ?: run {
                setToastMessage(R.string.stop_service_req_auth)
                _requestToService.value = STOP_SERVICE_REQ_AUTH
                FirebaseCrashlytics.getInstance().recordException(NullUserException())
            }
        }
    }

    /**
     * STT API와 Translate API가 정상 작동하는지 확인한다.
     */
    fun firstConnection(file: File, callback: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if(audioCaptureSettings.value.translateSettings.translateTool == TOOL_GPT) {
                // GPT 번역을 쓰는 경우 Whisper만 작동확인
                openAIRepository.transcriptionRequest(
                    file,
                    Language.ENGLISH.isoCode639_1.toString(),
                    null
                ).drop(1).collect { result ->
                    suspendSetUiState(
                        waitFirstConnection = false,
                        testSttState = result.state,
                        testTranslateState = result.state
                    )
                    result.onSuccess {
                        callback()
                    }
                    result.onFailure {
                        FirebaseCrashlytics.getInstance().recordException(Exception(result.state.name))
                        Smartlook.instance.trackEvent("AudioCaptureViewModel",
                            Properties().putString("whisper transcription fail", result.state.name))
                    }
                }
            } else {
                val translateFlow = when (audioCaptureSettings.value.translateSettings.translateTool) {
                    TOOL_DEEPL -> networkRepository.deepLTranslateRequest(
                        "hello",
                        Language.ENGLISH.isoCode639_1.toString(),
                        Language.KOREAN.isoCode639_1.toString()
                    )

                    TOOL_DEEPL_PRO -> networkRepository.deepLPROTranslateRequest(
                        "hello",
                        Language.ENGLISH.isoCode639_1.toString(),
                        Language.KOREAN.isoCode639_1.toString()
                    )

                    TOOL_PAPAGO -> networkRepository.papagoTranslateRequest(
                        "hello",
                        Language.ENGLISH.isoCode639_1.toString(),
                        Language.KOREAN.isoCode639_1.toString()
                    )

                    else -> networkRepository.deepLTranslateRequest(
                        "hello",
                        Language.ENGLISH.isoCode639_1.toString(),
                        Language.KOREAN.isoCode639_1.toString()
                    )
                }
                openAIRepository.transcriptionRequest(
                    file,
                    Language.ENGLISH.isoCode639_1.toString(),
                    null
                ).zip(translateFlow) { stt, translate ->
                    Pair(stt.state, translate.state)
                    // onLoading drop
                }.drop(1).collect { result ->
                    suspendSetUiState(
                        waitFirstConnection = false,
                        testSttState = result.first,
                        testTranslateState = result.second
                    )
                    if(result.first == ApiResultState.SUCCESS && result.second == ApiResultState.SUCCESS) {
                        callback()
                    } else {
                        Smartlook.instance.trackEvent("AudioCaptureViewModel",
                            Properties().putString("translate api","${result.first.name}, ${result.second.name}"))
                    }
                }
            }
        }
    }

    // 각 아래 작업들이 병렬로 수행된다.
    // speech to text job : 음성을 텍스트로 변환 요청
    // translate job : 번역 요청
    // sttConsume job : credit 소모 요청
    private fun collect() {
        speechToTextJob = viewModelScope.launch(Dispatchers.IO) {
            val parallel: MutableList<Deferred<Unit>> = mutableListOf()
            audioCaptureRepository.compressedAudioData.filterNotNull().buffer().collect {
                // 녹음된 음성이 emit 될때마다 1번의 transcription 요청과 translate 요청이 발생
                // 요청별 코루틴이 필요하며, 병렬로 실행
                parallel.add(async(Dispatchers.IO) {
                    val translateSettings = audioCaptureSettings.value.translateSettings
                    openAIRepository.transcriptionRequest(
                        it.file,
                        if (translateSettings.sourceLanguage == Language.UNKNOWN) {
                            null
                        } else {
                            translateSettings.sourceLanguage.isoCode639_1.toString()
                        },
                        scriptPrompt.let { p->if(p.isNotEmpty()) p.toString() else null }
                    ).buffer().collect { result ->
                        result.onSuccess { transcription ->
                            val index = addEmptyScript()
                            _sttConsumeRequestFlow.emit(it.duration)
                            if(transcription.text.isNotEmpty()) {
                                setOriginalScript(transcription.text,Script.ScriptState.COMPLETE,index)
                                if (audioCaptureSettings.value.displayOption == ORIGINAL) {
                                    addScriptData(transcription.text, null, it.duration, it.file)
                                    setTranslatedScript(null,Script.ScriptState.COMPLETE,index)
                                    suspendSetUiState(scriptIndex = index)
                                    return@onSuccess
                                }
                                _translateRequestFlow.emit(
                                    Pair(
                                        TranscriptionWithAudioData(
                                            transcription,
                                            it
                                        ), index
                                    )
                                )
                            }
                        }
                        result.onFailure { e ->
                            when(result.state) {
                                ApiResultState.WRONG_API_KEY -> {
                                    _requestToService.value = STOP_SERVICE_UNKNOWN
                                    setToastMessage(R.string.open_ai_wrong_api_key)
                                }
                                ApiResultState.LIMIT_USAGE -> {
                                    _requestToService.value = STOP_SERVICE_UNKNOWN
                                    setToastMessage(R.string.open_ai_limit_usage)
                                }
                                ApiResultState.TOO_MANY_REQUEST -> {
                                    setToastMessage(R.string.too_many_request)
                                    FirebaseCrashlytics.getInstance().recordException(e)
                                }
                                ApiResultState.TIME_OUT,
                                ApiResultState.NETWORK_ERROR -> {}
                                else -> {
                                    setToastMessage(result.state.message)
                                    FirebaseCrashlytics.getInstance().recordException(e)
                                    Timber.e(e)
                                }
                            }
                        }
                    }
                })
            }
            try{
                parallel.awaitAll()
            } catch (e: CancellationException) {
                // Deferred 객체는 취소될 때 CancellationException 발생
                // 사용자가 번역 정지를 눌렀을 때 Job이 cancel되고 이 블록이 실행됨.
            } catch (e: Exception) {
                Timber.e(e)
                FirebaseCrashlytics.getInstance().recordException(e)
                _requestToService.value = STOP_SERVICE_UNKNOWN
            }
        }
        translateJob = viewModelScope.launch(Dispatchers.IO) {
            val parallel: MutableList<Deferred<Unit>> = mutableListOf()
            translateRequestFlow.filterNotNull().buffer().collect { pair ->
                val translateSettings = audioCaptureSettings.value.translateSettings
                when (translateSettings.sourceLanguage) {
                    Language.UNKNOWN -> {
                        // 언어 감지일때
                        getComputeLanguageConfidenceValue(pair.first.transcription.text)?.let { ttsLanguage ->
                            if (ttsLanguage.toString() != translateSettings.targetLanguage.isoCode639_1.toString()) {
                                // 감지된 언어가 번역할 언어와 일치하지 않으면 번역 실행
                                parallel.add(async(Dispatchers.IO) {
                                    translateRequest(
                                        pair.first,
                                        Language.getByIsoCode639_1(ttsLanguage),
                                        translateSettings.targetLanguage,
                                        pair.second
                                    )
                                })
                            } else {
                                // 감지된 언어가 번역할 언어와 일치
                                addScriptData(pair.first, pair.first.transcription.text)
                                setTranslatedScript(pair.first.transcription.text, Script.ScriptState.COMPLETE, pair.second)
                            }
                        }
                    }

                    translateSettings.targetLanguage -> {
                        // 설정한 원어와 번역어가 같으면 stt 출력
                        addScriptData(pair.first, pair.first.transcription.text)
                        setTranslatedScript(pair.first.transcription.text, Script.ScriptState.COMPLETE, pair.second)
                    }

                    else -> {
                        // 언어 감지가 아니면 번역 요청
                        parallel.add(async(Dispatchers.IO) {
                            translateRequest(
                                pair.first,
                                translateSettings.sourceLanguage,
                                translateSettings.targetLanguage,
                                pair.second
                            )
                        })
                    }
                }
            }
            try{
                parallel.awaitAll()
            } catch (e: CancellationException) {

            } catch (e: Exception) {
                Timber.e(e)
                FirebaseCrashlytics.getInstance().recordException(e)
                _requestToService.value = STOP_SERVICE_UNKNOWN
            }
        }
        sttConsumeJob = viewModelScope.launch(Dispatchers.IO){
            var costDuration = 0L
            sttConsumeRequestFlow.filterNotNull().collect {
                .....
            }
        }
    }

    fun startAudioCapture() {
        _audioCaptureUIState.value = audioCaptureUIState.value.copy(isRecording = true)
        collect()
    }

    fun pauseAudioCapture() {
        _audioCaptureUIState.value = audioCaptureUIState.value.copy(isRecording = false, optionViewVisibility = VIEW_VISIBLE)
        if (this::speechToTextJob.isInitialized) {
            speechToTextJob.cancel()
        }
        if (this::translateJob.isInitialized) {
            translateJob.cancel()
        }
        if (this::translateProgressJob.isInitialized) {
            translateProgressJob.cancel()
        }
        scriptPrompt.clear() // 이전에 기억한 내용을 잊도록 합니다.
    }

    fun pauseAudioCaptureDisableResume() {
        setUiState(isRecording = false, disableResume = true, optionViewVisibility = VIEW_VISIBLE)
        if (this::speechToTextJob.isInitialized) {
            speechToTextJob.cancel()
        }
        if (this::translateJob.isInitialized) {
            translateJob.cancel()
        }
        if (this::translateProgressJob.isInitialized) {
            translateProgressJob.cancel()
        }
        scriptPrompt.clear() // 이전에 기억한 내용을 잊도록 합니다.
    }

    fun resumeAudioCapture() {
        startAudioCapture()
    }

    /**
     * Service에서 ViewModel을 정지시키는 함수.
     * ViewModel에서 호출이 필요한 경우 _requestToService 사용
     */
    fun stopAudioCapture(onStop: (Boolean) -> Unit = {}) {
        if (saveCost.value > 0) {
            viewModelScope.launch {
                sttConsumeJob.cancelAndJoin()
                checkConsumeWhisperAPI(ConsumeWhisperAPIDTO(saveCost.value), keepUse = { keepUse ->
                    onStop(keepUse)
                })
            }
        } else {
            onStop(true)
        }
    }

    suspend fun suspendSetUiState(
        isRecording: Boolean = this.audioCaptureUIState.value.isRecording,
        waitFirstConnection: Boolean = this.audioCaptureUIState.value.waitFirstConnection,
        disableResume: Boolean = this.audioCaptureUIState.value.disableResume,
        testSttState: ApiResultState? = this.audioCaptureUIState.value.testSttState,
        testTranslateState: ApiResultState? = this.audioCaptureUIState.value.testTranslateState,
        error: String? = this.audioCaptureUIState.value.error,
        scriptIndex: Int = this.audioCaptureUIState.value.scriptIndex,
        originScriptList: List<Script?> = this.audioCaptureUIState.value.originScriptList,
        translatedScriptList: List<Script?> = this.audioCaptureUIState.value.translatedScriptList,
        showOptionBottomSheet: Boolean = this.audioCaptureUIState.value.showOptionBottomSheet,
        optionViewVisibility: Int = this.audioCaptureUIState.value.optionViewVisibility
    ) {
        withContext(Dispatchers.Main) {
            setUiState(
                isRecording = isRecording,
                waitFirstConnection = waitFirstConnection,
                disableResume = disableResume,
                testSttState = testSttState,
                testTranslateState = testTranslateState,
                error = error,
                scriptIndex = scriptIndex,
                originScriptList = originScriptList,
                translatedScriptList = translatedScriptList,
                showOptionBottomSheet = showOptionBottomSheet,
                optionViewVisibility = optionViewVisibility
            )
        }
    }

    private fun setUiState(
        isRecording: Boolean = this.audioCaptureUIState.value.isRecording,
        waitFirstConnection: Boolean = this.audioCaptureUIState.value.waitFirstConnection,
        disableResume: Boolean = this.audioCaptureUIState.value.disableResume,
        testSttState: ApiResultState? = this.audioCaptureUIState.value.testSttState,
        testTranslateState: ApiResultState? = this.audioCaptureUIState.value.testTranslateState,
        error: String? = this.audioCaptureUIState.value.error,
        scriptIndex: Int = this.audioCaptureUIState.value.scriptIndex,
        originScriptList: List<Script?> = this.audioCaptureUIState.value.originScriptList,
        translatedScriptList: List<Script?> = this.audioCaptureUIState.value.translatedScriptList,
        showOptionBottomSheet: Boolean = this.audioCaptureUIState.value.showOptionBottomSheet,
        optionViewVisibility: Int = this.audioCaptureUIState.value.optionViewVisibility
    ) {
        _audioCaptureUIState.value = audioCaptureUIState.value.copy(
            isRecording = isRecording,
            waitFirstConnection = waitFirstConnection,
            disableResume = disableResume,
            testSttState = testSttState,
            testTranslateState = testTranslateState,
            error = error,
            scriptIndex = scriptIndex,
            originScriptList = originScriptList,
            translatedScriptList = translatedScriptList,
            showOptionBottomSheet = showOptionBottomSheet,
            optionViewVisibility = optionViewVisibility
        )
    }

    fun onTabShowOption() {
        val visibility = if (audioCaptureUIState.value.optionViewVisibility == VIEW_VISIBLE) {
            VIEW_INVISIBLE
        } else {
            VIEW_VISIBLE
        }
        setUiState(optionViewVisibility = visibility)
    }

    fun onClickPreviousButton() {
        if (audioCaptureUIState.value.scriptIndex == 0) {
            setScriptIndex(audioCaptureUIState.value.originScriptList.size - 1)
        } else {
            setScriptIndex(audioCaptureUIState.value.scriptIndex - 1)
        }
    }

    fun onClickNextButton() {
        if (audioCaptureUIState.value.scriptIndex == -1)
            return
        if (audioCaptureUIState.value.scriptIndex == audioCaptureUIState.value.originScriptList.size - 1) {
            setScriptIndex(0)
        } else {
            setScriptIndex(audioCaptureUIState.value.scriptIndex + 1)
        }
    }

    private fun setScriptIndex(index: Int) {
        if (index >= 0 && index <= audioCaptureUIState.value.originScriptList.size) {
            setUiState(scriptIndex = index)
        }
    }

    fun showOptionBottomSheet(show: Boolean) {
        setUiState(showOptionBottomSheet = show)
    }

    fun onSelectDisplayOption(selected: Pair<DisplayOption, String>) {
        _audioCaptureSettings.value = audioCaptureSettings.value.copy(
            displayOption = selected.first
        )
    }

    fun onCheckedTextDarkMode(textDarkMode: Boolean) {
        _audioCaptureSettings.value = audioCaptureSettings.value.copy(
            textDarkMode = textDarkMode
        )
    }

    fun onCheckedPlayActiveVoiceLottie(playActiveVoiceLottie: Boolean) {
        _audioCaptureSettings.value = audioCaptureSettings.value.copy(
            playActiveVoiceLottie = playActiveVoiceLottie
        )
    }

    private suspend fun checkConsumeWhisperAPI(
        consumeWhisperAPIDTO: ConsumeWhisperAPIDTO,
        keepUse: (Boolean) -> Unit,
    ) {
        if(!this::user.isInitialized) {
            setToastMessage(R.string.stop_service_req_auth)
            _requestToService.value = STOP_SERVICE_REQ_AUTH
            return
        }
        if(user.useUserApiKeys) {
            val apiKeys = user.apiKeys
            if(apiKeys != null && apiKeys.any { it.type == ApiKeyType.OPEN_AI && it.enabled }) {
                keepUse(true)
            } else {
                consumeWhisperAPIRequest(consumeWhisperAPIDTO, keepUse)
            }
        } else {
            consumeWhisperAPIRequest(consumeWhisperAPIDTO, keepUse)
        }
    }

    private suspend fun consumeWhisperAPIRequest(
        consumeWhisperAPIDTO: ConsumeWhisperAPIDTO,
        keepUse: (Boolean) -> Unit,
    ) {
        firebaseAuthRepository.getIdToken()?.let { token ->
            networkRepository.chappieTConsumeWhisperAPI(
                token,
                consumeWhisperAPIDTO
            ).collect { result ->
                result.onSuccess { res ->
                    _saveCost.value = 0
                    if (!res.keepUse) {
                        .....
                    } else {
                        keepUse(true)
                    }
                }
                result.onError { _, _ ->
                    setToastMessage(R.string.stop_service_network_error)
                    _requestToService.value = STOP_SERVICE_NETWORK_ERROR
                    keepUse(false)
                }
                result.onException {
                    setToastMessage(R.string.stop_service_network_error)
                    _requestToService.value = STOP_SERVICE_NETWORK_ERROR
                    keepUse(false)
                }
            }
        } ?: run {
            setToastMessage(R.string.stop_service_req_auth)
            _requestToService.value = STOP_SERVICE_REQ_AUTH
            keepUse(false)
        }
    }

    /**
     * text의 언어를 iso639_1 코드로 반환한다.
     * (가장 가능성이 높은 언어를 반환한다.)
     *
     * @see com.github.pemistahl.lingua.api.LanguageDetector.computeLanguageConfidenceValues
     */
    private fun getComputeLanguageConfidenceValue(transcriptionText: String): IsoCode639_1? {
        return detector.computeLanguageConfidenceValues(text = transcriptionText)
            .takeIf { m -> m.isNotEmpty() }?.firstKey()?.isoCode639_1
    }

    private suspend fun translateRequest(
        data: TranscriptionWithAudioData,
        sourceLanguage: Language,
        targetLanguage: Language,
        index: Int
    ) {
        when (audioCaptureSettings.value.translateSettings.translateTool) {
            TOOL_DEEPL -> deepLTranslateRequest(data, sourceLanguage, targetLanguage, index)
            TOOL_DEEPL_PRO -> deepLPROTranslateRequest(data, sourceLanguage, targetLanguage, index)
            TOOL_PAPAGO -> papagoTranslateRequest(data, sourceLanguage, targetLanguage, index)
            TOOL_GOOGLE_TRANSLATE -> googleTranslateRequest(data, sourceLanguage, targetLanguage, index)
            TOOL_GPT -> chatGPTTranslateRequest(data, sourceLanguage, targetLanguage, index)
        }
    }

    private suspend fun papagoTranslateRequest(
        data: TranscriptionWithAudioData,
        sourceLanguage: Language,
        targetLanguage: Language,
        index: Int
    ) {
        networkRepository.papagoTranslateRequest(
            data.transcription.text,
            sourceLanguage.isoCode639_1.toString(),
            targetLanguage.isoCode639_1.toString()
        )
            .collect { result ->
                result.onLoading {
                }
                result.onSuccess { res ->
                    addScriptData(data, res.message.result.translatedText)
                    setTranslatedScript(res.message.result.translatedText, Script.ScriptState.COMPLETE, index)
                }
                result.onFailure {e->
                    setToastMessage(result.state.message)
                    Timber.e(e)
                    FirebaseCrashlytics.getInstance().recordException(Throwable(e))
                }
            }
    }

    private suspend fun deepLTranslateRequest(
        data: TranscriptionWithAudioData,
        sourceLanguage: Language,
        targetLanguage: Language,
        index: Int
    ) {
        networkRepository.deepLTranslateRequest(data.transcription.text,
            sourceLanguage.isoCode639_1.toString(),
            targetLanguage.isoCode639_1.toString())
            .collect { result ->
                result.onSuccess { res ->
                    res.translations?.get(0)?.text.also {
                        addScriptData(data, it)
                        setTranslatedScript(it, Script.ScriptState.COMPLETE, index)
                    }
                }
                result.onFailure {e->
                    when (result.state) {
                        ApiResultState.WRONG_API_KEY -> {
                            setToastMessage(R.string.deepl_wrong_api_key)
                            _requestToService.value = STOP_SERVICE_UNKNOWN
                        }
                        ApiResultState.LIMIT_USAGE -> {
                            setToastMessage(R.string.deepl_limit_usage)
                            _requestToService.value = STOP_SERVICE_UNKNOWN
                        }
                        else -> {
                            setToastMessage(result.state.message)
                            Timber.e(e)
                            FirebaseCrashlytics.getInstance().recordException(Throwable(e))
                        }
                    }
                }
            }
    }

    private suspend fun deepLPROTranslateRequest(
        data: TranscriptionWithAudioData,
        sourceLanguage: Language,
        targetLanguage: Language,
        index: Int
    ) {
        networkRepository.deepLPROTranslateRequest(data.transcription.text,
            sourceLanguage.isoCode639_1.toString(),
            targetLanguage.isoCode639_1.toString())
            .collect { result ->
                result.onSuccess { res ->
                    res.translations?.get(0)?.text.also {
                        addScriptData(data, it)
                        setTranslatedScript(it, Script.ScriptState.COMPLETE, index)
                    }
                }
                result.onFailure {e->
                    when (result.state) {
                        ApiResultState.WRONG_API_KEY -> {
                            setToastMessage(R.string.deepl_wrong_api_key)
                            _requestToService.value = STOP_SERVICE_UNKNOWN
                        }
                        ApiResultState.LIMIT_USAGE -> {
                            setToastMessage(R.string.deepl_limit_usage)
                            _requestToService.value = STOP_SERVICE_UNKNOWN
                        }
                        else -> {
                            setToastMessage(result.state.message)
                            Timber.e(e)
                            FirebaseCrashlytics.getInstance().recordException(Throwable(e))
                        }
                    }
                }
            }
    }

    /**
     * 모든 응답에는 finish_reason. 가능한 값은 다음과 finish_reason같습니다.
     *
     * stop: API가 완전한 메시지를 반환했거나 stop 매개변수를 통해 제공된 중지 시퀀스 중 하나에 의해 종료된 메시지를 반환했습니다.
     * lengthmax_tokens: 매개변수 또는 토큰 제한 으로 인해 불완전한 모델 출력
     * function_call: 모델이 함수를 호출하기로 결정했습니다.
     * content_filter: 콘텐츠 필터의 플래그로 인해 콘텐츠가 누락되었습니다.
     * null: API 응답이 아직 진행 중이거나 완료되지 않았습니다.
     *
     * https://platform.openai.com/docs/guides/gpt/chat-completions-api
     */
    private suspend fun chatGPTTranslateRequest(
        data: TranscriptionWithAudioData,
        sourceLanguage: Language,
        targetLanguage: Language,
        index: Int
    ) {
        val builder = StringBuilder()
        openAIRepository.createTranslateRequest(
            data.transcription.text,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            systemMessage = audioCaptureSettings.value.translateSettings.gptTranslateOptionPrompt
            ).catch { e->
                val state = when(e) {
                    is AuthenticationException, is PermissionException -> ApiResultState.WRONG_API_KEY
                    is RateLimitException -> ApiResultState.LIMIT_USAGE
                    is OpenAITimeoutException -> ApiResultState.TIME_OUT
                    is OpenAIIOException -> ApiResultState.NETWORK_ERROR
                    is OpenAIHttpException -> ApiResultState.NETWORK_ERROR
                    is OpenAIServerException -> ApiResultState.NETWORK_ERROR
                    else -> ApiResultState.UNKNOWN_ERROR
                }
                when (state) {
                    ApiResultState.WRONG_API_KEY -> {
                        setToastMessage(R.string.open_ai_wrong_api_key)
                        _requestToService.value = STOP_SERVICE_UNKNOWN
                    }
                    ApiResultState.LIMIT_USAGE -> {
                        setToastMessage(R.string.open_ai_limit_usage)
                        _requestToService.value = STOP_SERVICE_UNKNOWN
                    }
                    else -> {
                        FirebaseCrashlytics.getInstance().recordException(e)
                        Timber.e(e)
                    }
                }
            }.collect { chunk->
            if(chunk.choices.last().finishReason != null) {
                builder.toString().let {translated->
                    addScriptData(data, translated)
                    setTranslatedScript(translated, Script.ScriptState.COMPLETE, index)
                }
            } else {
                chunk.choices.filter { c->c.delta.role==null }.forEach {chat ->
                    builder.append(chat.delta.content)
                    setTranslatedScript(builder.toString(), Script.ScriptState.WRITING, index)
                }
            }
        }
    }

    private suspend fun googleTranslateRequest(
        transcriptionWithAudioData: TranscriptionWithAudioData,
        sourceLanguage: Language,
        targetLanguage: Language,
        index: Int
    ) {
    }

    private fun addScriptData(data: TranscriptionWithAudioData, translated: String?) {
        addScriptData(
            data.transcription.text,
            translated,
            data.audioData.duration,
            data.audioData.file
        )
    }

    private fun addScriptData(origin: String?, translated: String?, duration: Long, file: File) {
        scriptDataList.add(ScriptData(origin, translated, duration, file.absolutePath))
    }

    private fun addEmptyScript():Int {
        return audioCaptureUIState.value.originScriptList.plus(Script()).also {
            setUiState(
                originScriptList = it,
                translatedScriptList = audioCaptureUIState.value.translatedScriptList.plus(Script())
            )
        }.lastIndex
    }
    private fun setOriginalScript(origin: String?, state: Script.ScriptState, index: Int) {
        if (audioCaptureSettings.value.translateSettings.rememberPreviousContext) {
            if (scriptPrompt.length > PROMPT_MAX_LENGTH) {
                // The model will only consider the final 224 tokens of the prompt and ignore anything earlier.
                // https://platform.openai.com/docs/guides/speech-to-text/prompting
                scriptPrompt.delete(0, scriptPrompt.length - PROMPT_MAX_LENGTH)
            }
            scriptPrompt.append(origin)
        }
        audioCaptureUIState.value.originScriptList.toMutableList().let {
            it[index] = Script(origin,state)
            if(audioCaptureUIState.value.optionViewVisibility == VIEW_VISIBLE) {
                setUiState(originScriptList = it)
            } else {
                setUiState(originScriptList = it, scriptIndex = index)
            }
        }
    }

    private fun setTranslatedScript(translated: String?, state: Script.ScriptState, index: Int) {
        audioCaptureUIState.value.translatedScriptList.toMutableList().apply {
            this[index] = Script(translated,state)
        }.toList().let {
            if(audioCaptureUIState.value.optionViewVisibility == VIEW_VISIBLE) {
                setUiState(translatedScriptList = it)
            }else {
                setUiState(translatedScriptList = it, scriptIndex = index)
            }
        }
    }

    private fun setErrorMessage(error: String?) {
        _audioCaptureUIState.value = audioCaptureUIState.value.copy(
            error = error
        )
    }

    private fun setToastMessage(@StringRes toastMessage: Int?, param: String? = null) {
        _audioCaptureUIState.value = audioCaptureUIState.value.copy(
            toastMessage = toastMessage,
            toastParam = param
        )
    }

    companion object {
        const val PROMPT_MAX_LENGTH = 60
        const val STOP_SERVICE_REQ_AUTH = "STOP_SERVICE_FOR_AUTH"
        const val STOP_SERVICE_NOT_ENOUGH_CREDIT = "STOP_SERVICE_NOT_ENOUGH_CREDIT"
        const val STOP_SERVICE_NETWORK_ERROR = "STOP_SERVICE_NETWORK_ERROR"
        const val STOP_SERVICE_UNKNOWN = "STOP_SERVICE_UNKNOWN"
    }
}

/**
 * @property ORIGINAL : 원어만 표시
 * @property ORIGINAL_TRANSLATION : 번역 + 원어 표시
 * @property TRANSLATION : 번역만 표시
 */
enum class DisplayOption {
    ORIGINAL,
    ORIGINAL_TRANSLATION,
    TRANSLATION
}

/**
 * @param optionViewVisibility
 * @param waitApiVisibility 개발자 모드일때 표시 용도
 * @param originScriptList -> 원어 저장용
 * @param translatedScriptList -> 번역 저장용
 */
data class AudioCaptureUIState(
    val waitFirstConnection: Boolean = true,
    val disableResume: Boolean = false,
    val testSttState: ApiResultState? = null,
    val testTranslateState: ApiResultState? = null,
    val isRecording: Boolean = false,
    val scriptIndex: Int = -1,
    val originScriptList: List<Script?> = listOf(),
    val translatedScriptList: List<Script?> = listOf(),
    val showOptionBottomSheet: Boolean = false,
    val optionViewVisibility: Int = VIEW_INVISIBLE,
    val waitApiVisibility: Int = VIEW_INVISIBLE,
    val error: String? = null,
    val toastMessage: Int? = null,
    val toastParam: String? = null
)

/**
 * @param devOptionVisibility 개발자 모드
 */
data class AudioCaptureSettings(
    val displayOption: DisplayOption = TRANSLATION,
    val devOptionVisibility: Boolean = false,
    val translateSettings: ChappieTAppTranslateSettings = ChappieTAppTranslateSettings(),
    val textDarkMode: Boolean = true,
    val playActiveVoiceLottie: Boolean = true
)

data class Script(
    val script:String? = null,
    val state:ScriptState = ScriptState.NULL
) {
    enum class ScriptState {
        NULL,
        WRITING,
        COMPLETE,
        FAIL
    }
}
