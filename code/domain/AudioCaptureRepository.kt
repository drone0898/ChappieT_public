package kr.com.chappiet.domain

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioRecord.STATE_INITIALIZED
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.SystemClock
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.konovalov.vad.silero.Vad
import com.konovalov.vad.silero.VadSilero
import com.konovalov.vad.silero.config.FrameSize
import com.konovalov.vad.silero.config.Mode
import com.konovalov.vad.silero.config.SampleRate
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.close
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kr.com.chappiet.R
import kr.com.chappiet.data.model.AudioData
import kr.com.chappiet.service.AudioCaptureProvider
import kr.com.chappiet.service.AudioCaptureService
import kr.com.chappiet.vm.AudioCaptureSettings
import kr.com.chappiet.vm.ChappieTAppTranslateSettings.Companion.MAX_VOICE_ACTIVE_LENGTH_DEFAULT
import kr.com.chappiet.vm.ChappieTAppTranslateSettings.Companion.MIN_VOICE_ACTIVE_LENGTH_DEFAULT
import kr.com.chappiet.vm.ChappieTAppTranslateSettings.Companion.NOISE_CANCEL_DB_DEFAULT
import kr.com.chappiet.vm.ChappieTAppTranslateSettings.Companion.NOISE_CANCEL_DEFAULT
import kr.com.chappiet.vm.ChappieTAppTranslateSettings.Companion.VOICE_DETECTION_DEFAULT
import kr.com.chappiet.vm.ChappieTAppTranslateSettings.Companion.VOICE_FILTER_DEFAULT
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import javax.inject.Inject

class AudioCaptureRepository @Inject constructor() {

    private var _compressedAudioData: MutableStateFlow<AudioData?> = MutableStateFlow(null)
    var compressedAudioData: StateFlow<AudioData?> = _compressedAudioData.asStateFlow()
    private val _speakingState: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val speakingState = _speakingState.asStateFlow()

    private lateinit var audioCaptureProvider: AudioCaptureProvider

    private var recordStartTime:Long = 0L
    var lastSpeakingTime:Long = 0L

    private val bufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        ENCODING_CHANNEL,
        ENCODING_FORMAT
    )

    private lateinit var vad: VadSilero

    private var audioRecord: AudioRecord? = null
    private var vadAudioRecord: AudioRecord? = null
    private var vadBuffer:ShortArray = ShortArray(bufferSize/2)
    private var audioBuffer:ShortArray = ShortArray(bufferSize/2)
    private val audioOutputFiles = ConcurrentLinkedQueue<File>()
    private val audioRecordJobs = LinkedList<Job>()
    private val audioConvertJobs = ConcurrentLinkedQueue<Job>() // 동시성 문제 방지
    private var switchingAudioRecordJob: Job? = null
    private var vadJob: Job? = null
    private var awaitSwitching: Deferred<Unit>? = null
    private var switchingChannel: Boolean = false
    private var currentChannel:ByteWriteChannel? = null
    private var nextChannel:ByteWriteChannel? = null

    private var maxVoiceActiveLength: Int = (MAX_VOICE_ACTIVE_LENGTH_DEFAULT * 1000)
    private var minVoiceActiveLength: Int = (MIN_VOICE_ACTIVE_LENGTH_DEFAULT * 1000)
    private var voiceDetection: Boolean = VOICE_DETECTION_DEFAULT
    private var voiceFilter: Boolean = VOICE_FILTER_DEFAULT
    private var noiseCancel: Boolean = NOISE_CANCEL_DEFAULT
    private var noiseCancelDB: Int = NOISE_CANCEL_DB_DEFAULT


    fun setProvider(audioCaptureProvider: AudioCaptureProvider) {
        this.audioCaptureProvider = audioCaptureProvider
        buildVAD()
    }
    fun setSettings(settings: AudioCaptureSettings) {
        maxVoiceActiveLength = settings.translateSettings.maxVoiceActiveLength
        minVoiceActiveLength = settings.translateSettings.minVoiceActiveLength
        voiceDetection = settings.translateSettings.voiceDetection
        noiseCancel = settings.translateSettings.noiseCancel
        voiceFilter = settings.translateSettings.voiceFilter
    }

    private fun buildVAD() {
        vad = Vad.builder()
            .setContext(audioCaptureProvider.getServiceContext())
            .setSampleRate(SampleRate.SAMPLE_RATE_16K)
            .setFrameSize(VAD_FRAME_SIZE)
            .setMode(Mode.AGGRESSIVE)
            .setSilenceDurationMs(VAD_SILENCE_DURATION)
            .setSpeechDurationMs(VAD_SPEECH_DURATION)
            .build()
    }

    fun startAudioCapture() {
        audioRecord = startAudioRecord().also {
            audioRecordJobs += startAudioRecordJob(it)
        }
        if(voiceDetection) {
            vadAudioRecord = startAudioRecord(vad = true)
            startVAD()
        } else {
            vadJob = CoroutineScope(Dispatchers.IO).launch {
                while (currentCoroutineContext().isActive && this.isActive) {
                    delay(VAD_CHECK_DELAY.toLong())
                    val currentTime = SystemClock.elapsedRealtime()
                    // 조건 1: 녹음 시작 후 MAX_VOICE_ACTIVE_LENGTH초 이상 경과했을 때
                    if (currentTime - recordStartTime >= maxVoiceActiveLength) {
                        splitRecordAndCompress()
                        lastSpeakingTime = currentTime
                        recordStartTime = currentTime // 녹음 시작 시간을 리셋합니다.
                        _speakingState.value = false
                        continue
                    }
                }
            }
        }
    }

    fun pauseAudioCapture() {
        _compressedAudioData = MutableStateFlow(null)
        compressedAudioData = _compressedAudioData.asStateFlow()
        switchingAudioRecordJob?.cancel()
        audioRecordJobs.forEach{it.cancel()}
        audioConvertJobs.forEach{it.cancel()}
        audioRecordJobs.clear()
        audioConvertJobs.clear()
        vadJob?.cancel()
    }

    fun resumeAudioCapture(){
        buildVAD()
        startAudioCapture()
    }

    fun closeRepository() {
        _compressedAudioData = MutableStateFlow(null)
        compressedAudioData = _compressedAudioData.asStateFlow()
        switchingAudioRecordJob?.cancel()
        audioRecordJobs.forEach{it.cancel()}
        audioConvertJobs.forEach{it.cancel()}
        audioRecordJobs.clear()
        audioConvertJobs.clear()
        vadJob?.cancel()
    }

    /**
     * 감지한 음성활동을 바탕으로 녹음 파일을 분할하고 인코딩을 시작하도록 합니다.
     * 음성활동이 없는 상태가 지속되면 녹음을 종료하도록 합니다.
     */
    private fun startVAD() {
        // voice activity detection
        vadJob = CoroutineScope(Dispatchers.IO).launch {
            _speakingState.value = false
            var silenceCount = 0
            lastSpeakingTime = SystemClock.elapsedRealtime()
            recordStartTime = lastSpeakingTime
            while (currentCoroutineContext().isActive && this.isActive) {
                delay(VAD_CHECK_DELAY.toLong())
                val currentTime = SystemClock.elapsedRealtime()
                // 조건 1: 녹음 시작 후 MAX_VOICE_ACTIVE_LENGTH초 이상 경과했을 때
                if (currentTime - recordStartTime >= maxVoiceActiveLength) {
                    if(speakingState.value){
                        splitRecordAndCompress()
                    }else{
                        splitRecordAndDelete()
                    }
                    lastSpeakingTime = currentTime
                    recordStartTime = currentTime // 녹음 시작 시간을 리셋합니다.
                    _speakingState.value = false
                    continue
                }
                if(currentCoroutineContext().isActive && this.isActive) {
                    vadAudioRecord?.read(vadBuffer,0,vadBuffer.size)
                    val sliceBuffer = if(vadBuffer.size > VAD_FRAME_SIZE.value) {
                        vadBuffer.sliceArray(0 until VAD_FRAME_SIZE.value)
                    } else {
                        val padding = ShortArray(VAD_FRAME_SIZE.value - vadBuffer.size)
                        vadBuffer + padding
                    }
                    val speech:Boolean = vad.isSpeech(sliceBuffer)

                    if (!speech) {
                        // 조건 2: 문장이 끝났고, MIN_VOICE_ACTIVE_LENGTH 초 이상 경과했을 때
                        if (speakingState.value && currentTime - lastSpeakingTime >= minVoiceActiveLength) {
                            splitRecordAndCompress()
                            lastSpeakingTime = currentTime
                            recordStartTime = currentTime // 녹음 시작 시간을 리셋합니다.
                            _speakingState.value = false
                            silenceCount = 0
                            continue
                        }
                        if (!speakingState.value) {
                            silenceCount++
                        }
                        // 조건 3: 침묵이 STOP_VAD_LIMIT 초 이상일 경우
                        if (silenceCount > STOP_VAD_LIMIT / VAD_CHECK_DELAY) {
                            audioCaptureProvider.showToastMessage(R.string.stop_service_silence)
                            audioCaptureProvider.executeAction(AudioCaptureService.ACTION_PAUSE_RECORD)
                            break
                        }
                    } else if (!speakingState.value) {
                        _speakingState.value = true
                        lastSpeakingTime = SystemClock.elapsedRealtime()
                        silenceCount = 0
                    }
                }
            }
        }.apply { invokeOnCompletion { vad.close() } }
    }

    @SuppressLint("NewApi")
    private fun startAudioRecord(vad:Boolean = false): AudioRecord{
        val config =
            AudioPlaybackCaptureConfiguration.Builder(audioCaptureProvider.getMediaProjection())
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(ENCODING_FORMAT)
            .setSampleRate(if(vad){ VAD_SAMPLE_RATE }else{ SAMPLE_RATE })
            .setChannelMask(ENCODING_CHANNEL)
            .build()

        val audioRecord = AudioRecord.Builder()
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .setAudioPlaybackCaptureConfig(config)
            .build()

        audioRecord.startRecording()
        return audioRecord
    }

    private fun startAudioRecordJob(audioRecord: AudioRecord): Job =
        CoroutineScope(Dispatchers.IO).launch {
            currentChannel = createAudioFileByTimeStamp(AudioOriginDIR, "pcm").also {
                audioOutputFiles.add(it)
            }.writeChannel()

            while (isActive && audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.read(audioBuffer,0,audioBuffer.size)
                val byteBuffer = ByteBuffer.allocate(audioBuffer.size * 2)
                byteBuffer.order(ByteOrder.nativeOrder())
                for (value in audioBuffer) {
                    byteBuffer.putShort(value)
                }
                val byteArray = byteBuffer.array()
                if(switchingChannel) {
                    awaitSwitching = async {
                        currentChannel?.close()
                        currentChannel = nextChannel
                    }
                    awaitSwitching?.await()
                    switchingChannel = false
                }
                currentChannel?.apply {
                    writeFully(byteArray, 0, byteArray.size)
                }
            }

            currentChannel?.close()
        }.apply {
            invokeOnCompletion { audioRecord.takeIf { it.state == STATE_INITIALIZED }?.apply { stop();release(); } }
        }

    /**
     * 녹음 파일을 변경하고, 이전 파일은 리스트에서 제거합니다.
     */
    private fun splitRecordAndDelete() {
        CoroutineScope(Dispatchers.IO).launch {
            nextChannel = createAudioFileByTimeStamp(AudioOriginDIR, "pcm").also {
                audioOutputFiles.add(it)
            }.writeChannel()
            switchingChannel = true
            awaitSwitching?.await()
            audioOutputFiles.poll()
        }
    }

    /**
     * 녹음 파일 채널 종료전에 압축하는것을 방지하기위해 코드를 동기화합니다.
     */
    private suspend fun splitRecordAndCompress() {
        CoroutineScope(Dispatchers.IO).launch {
            nextChannel = createAudioFileByTimeStamp(AudioOriginDIR, "pcm").also {
                audioOutputFiles.add(it)
            }.writeChannel()
            switchingChannel = true
            awaitSwitching?.await()
            audioConvertJobs += CoroutineScope(Dispatchers.IO).launch {
                audioOutputFiles.poll()?.let {
                    val result: Pair<File,FFmpegSession> = convertToM4aFFmpeg(it)
                    if(ReturnCode.isSuccess(result.second.returnCode)) {
                        getAudioDuration(result.first).let {duration->
                            if(duration >= MINIMUM_AUDIO_LENGTH) {
                                // Whisper Request require minimum length 0.1 seconds.
                                _compressedAudioData.emit(AudioData(result.first,duration))
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main){
                            audioCaptureProvider.executeAction(AudioCaptureService.ACTION_PAUSE_RECORD)
                            audioCaptureProvider.showToastMessage(R.string.exception_pause_record)
                        }
                        FirebaseCrashlytics.getInstance().recordException(Throwable(result.second.failStackTrace))
                        Timber.e(result.second.failStackTrace)
                    }
                }
            }.apply {
                invokeOnCompletion { audioConvertJobs.remove(this) }
            }
        }
    }

    private fun createAudioFile(dir: String = AudioOriginDIR, filename: String): File {
        val audioCapturesDirectory = File(audioCaptureProvider.getCacheDir(), dir)
        if (!audioCapturesDirectory.exists()) {
            audioCapturesDirectory.mkdirs()
        }
        return File(audioCapturesDirectory.absolutePath + "/" + filename)
    }

    private suspend fun convertToM4aFFmpeg(origin: File): Pair<File,FFmpegSession> =
        withContext(Dispatchers.IO) {
        val compressedFile: File = createAudioFile(AudioCompressDIR, "${origin.nameWithoutExtension}.m4a")
        val command = if(noiseCancel && voiceFilter) {
            "-y -f s16le -ar $SAMPLE_RATE -ac 1 -i ${origin.absolutePath} -af \"highpass=f=200, lowpass=f=3000, afftdn=nf=${-noiseCancelDB}\" -c:a aac -b:a 64k ${compressedFile.absolutePath}"
        } else if(!voiceFilter && !noiseCancel) {
            "-y -f s16le -ar $SAMPLE_RATE -ac 1 -i ${origin.absolutePath} -c:a aac -b:a 64k ${compressedFile.absolutePath}"
        } else if(!voiceFilter) {
            "-y -f s16le -ar $SAMPLE_RATE -ac 1 -i ${origin.absolutePath} -af \"afftdn=nf=${-noiseCancelDB}\" -c:a aac -b:a 64k ${compressedFile.absolutePath}"
        } else {
            "-y -f s16le -ar $SAMPLE_RATE -ac 1 -i ${origin.absolutePath} -af \"highpass=f=200, lowpass=f=3000\" -c:a aac -b:a 64k ${compressedFile.absolutePath}"
        }

        val deferredResult = CompletableDeferred<Pair<File,FFmpegSession>>()
        FFmpegKit.executeAsync(command) { session ->
            deferredResult.complete(Pair(compressedFile,session))
        }
        // 세션 완료를 기다립니다.
        return@withContext deferredResult.await()
    }

    private fun getAudioDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            return time?.toLong() ?: 0L
        } finally {
            retriever.release()
        }
    }


    companion object {
        private const val SAMPLE_RATE = 16600
        private const val VAD_SAMPLE_RATE = 16000 // Silero VAD Valid Sample Rate : 16000Hz, 8000Hz
        private const val CODEC_BIT_RATE = 64000

        private const val ENCODING_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val ENCODING_CHANNEL = AudioFormat.CHANNEL_IN_MONO

        const val MINIMUM_AUDIO_LENGTH = 500

        const val VAD_SILENCE_DURATION = 300
        const val VAD_SPEECH_DURATION = 50
        val VAD_FRAME_SIZE = FrameSize.FRAME_SIZE_512 // Valid Frame Size : 512, 1024, 1536

        private const val VAD_CHECK_DELAY = 200
        private const val STOP_VAD_LIMIT = 120000

        const val AudioOriginDIR = "/AudioCaptures"
        const val AudioCompressDIR = "/AudioCompress"
    }
}