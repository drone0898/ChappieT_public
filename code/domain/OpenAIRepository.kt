package kr.com.chappiet.domain

import com.aallam.openai.api.audio.Transcription
import com.aallam.openai.api.audio.TranscriptionRequest
import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionChunk
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.file.FileSource
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.api.moderation.ModerationModel
import com.aallam.openai.api.moderation.ModerationRequest
import com.aallam.openai.api.moderation.TextModeration
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.RetryStrategy
import com.github.pemistahl.lingua.api.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.single
import kr.com.chappiet.data.remote.ApiResult
import kr.com.chappiet.di.ApiKeyProvider
import kr.com.chappiet.vm.ChappieTAppTranslateSettings
import okio.source
import java.io.File
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

class OpenAIRepository @Inject constructor(
....
) {
    private var openAI: OpenAI = createOpenAI()
    private fun createOpenAI():OpenAI{
        ....
    }

    suspend fun testApiKey(key:String): Flow<ApiResult<TextModeration>> = handleFlowApi {
        OpenAI(
            token = key,
            organization = null,
            timeout = Timeout(socket = 15.seconds)
        ).moderations(request = ModerationRequest(
            input = listOf("Hi Open AI This is an test for Api Key"),
            model = ModerationModel.Stable
        ))
    }

    suspend fun createChatCompletion(userMessage: String, modelId: String = "gpt-3.5-turbo"): ChatCompletion {
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId(modelId),
            messages = listOf(ChatMessage(role = ChatRole.User, content = userMessage))
        )
        return openAI.chatCompletion(chatCompletionRequest)
    }

    suspend fun createChatCompletions(userMessage: String, modelId: String = "gpt-3.5-turbo"): Flow<ChatCompletionChunk> {
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId(modelId),
            messages = listOf(ChatMessage(role = ChatRole.User, content = userMessage))
        )
        return openAI.chatCompletions(chatCompletionRequest)
    }

    fun createTranslateRequest(
    originText: String,
    sourceLanguage: Language,
    targetLanguage: Language,
    systemMessage: String = ChappieTAppTranslateSettings.GPT_TRANSLATE_OPTION_PROMPT_DEFAULT,
    temperature: Double? = 0.5): Flow<ChatCompletionChunk> {
        val replacedSystemMessage =
            systemMessage.replace("@{targetLanguage}", targetLanguage.name)
                .replace("@{sourceLanguage}", sourceLanguage.name)

        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId("gpt-3.5-turbo"),
            messages = listOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = replacedSystemMessage
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = originText
                )
            ),
            temperature = temperature,
            maxTokens = 100
        )
        return openAI.chatCompletions(chatCompletionRequest)
    }

    /**
     * transcripton은 input language를 결정할 수 있다.
     */
    suspend fun transcriptionRequest(file:File,
                                     language:String? = null,
                                     prompt: String?,
                                     temperature: Double? = null): Flow<ApiResult<Transcription>>
    = handleFlowApi {
        val request = TranscriptionRequest(
            audio = FileSource(name = file.name, source = file.source()),
            model = ModelId("whisper-1"),
            language = language,
            prompt = prompt,
            temperature = temperature
        )
        openAI.transcription(request)
    }
}