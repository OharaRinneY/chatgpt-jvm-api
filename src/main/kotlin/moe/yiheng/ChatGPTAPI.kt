package moe.yiheng

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.jackson.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import java.io.Closeable
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ChatGPTAPI(
    private var sessionToken: String,
    private var apiBaseUrl: String = "https://chat.openai.com/api",
    private var backendApiBaseUrl: String = "https://chat.openai.com/backend-api",
    private var userAgent: String = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36"
) : Closeable {

    // access token cache
    private var cachedAccessToken: AtomicReference<String?> = AtomicReference(null)

    // kotlin coroutine scope
    private val scope = CoroutineScope(Dispatchers.IO)

    // ktor http client
    private val client = HttpClient(OkHttp) {
        install(UserAgent) {
            agent = userAgent
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 5.minutes.inWholeMilliseconds
            socketTimeoutMillis = 60.seconds.inWholeMilliseconds
        }
        install(ContentNegotiation) {
            jackson() {
                registerKotlinModule()
            }
        }
    }

    // parent message id
    private var parentMessageId: String = UUID.randomUUID().toString()
    private var conversationId: String? = null

    init {
        // reset session token cache each 30 seconds
        scope.launch {
            cachedAccessToken.set(null)
            delay(30.seconds)
        }
    }

    suspend fun sendMessage(
        message: String,
        processor: ((String) -> Unit)? = null,
    ): String {
        val accessToken = this.refreshAccessToken()
        val body = ConversationJSONBody(
            conversationId = conversationId,
            action = "next",
            messages = listOf(
                Prompt(
                    id = UUID.randomUUID().toString(),
                    role = "user",
                    content = PromptContent(
                        contentType = "text",
                        parts = listOf(message)
                    )
                )
            ),
            model = "text-davinci-002-render",
            parentMessageId = parentMessageId
        )
        val url = "$backendApiBaseUrl/conversation"
        var returnMessage = ""
        val channel = client.preparePost(url) {
            headers {
                append("Authorization", "Bearer $accessToken")
                append("Content-Type", "application/json")
            }
            setBody(body)
        }.execute { response: HttpResponse ->
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                var data = channel.readUTF8Line() ?: continue
                if (!data.startsWith("data:")) {
                    continue
                }
                data = data.substring(6)
                if (data == "[DONE]") {
                    break
                }
                val parseData = ObjectMapper().apply {
                    propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
                    registerKotlinModule()
                }.readValue(data, ConversationResponseEvent::class.java)
                this.conversationId = parseData.conversationId
                this.parentMessageId = parseData.message?.id ?: this.parentMessageId
                returnMessage = parseData.message?.content?.parts?.getOrNull(0) ?: continue
                if (processor != null) {
                    processor(returnMessage)
                }
            }
        }
        return returnMessage
    }

    @Throws(IllegalStateException::class)
    suspend fun refreshAccessToken(): String {
        cachedAccessToken.get()?.let { return it }
        val result = client.get("$apiBaseUrl/auth/session") {
            headers {
                append("cookie", "__Secure-next-auth.session-token=${sessionToken}")
            }
        }.body<SessionResult>()
        if (result.error != null) {
            throw IllegalStateException("Failed to refresh access token: ${result.error}")
        }
        val accessToken = result.accessToken
        cachedAccessToken.set(accessToken)
        return accessToken
    }

    override fun close() {
        scope.cancel()
        client.close()
    }
}
