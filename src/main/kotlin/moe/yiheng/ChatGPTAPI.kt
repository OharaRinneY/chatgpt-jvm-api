package moe.yiheng

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * ChatGPT API
 * remember close after use.
 * @param sessionToken session token from cookie.
 * @param timeout timeout for each conversation. default is 5 minutes.
 * @param apiBaseUrl optional, default is https://chat.openai.com/api
 * @param backendApiBaseUrl optional, default is https://chat.openai.com/backend-api
 * @param userAgent optional
 */
class ChatGPTAPI(
    private var sessionToken: String,
    private var timeout: Duration = 5.minutes,
    private var apiBaseUrl: String = "https://chat.openai.com/api",
    internal var backendApiBaseUrl: String = "https://chat.openai.com/backend-api",
    private var userAgent: String = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36"
) : Closeable {

    // thread-safe access token cache
    private var cachedAccessToken: AtomicReference<String?> = AtomicReference(null)

    // kotlin coroutine scope
    private val scope = CoroutineScope(Dispatchers.IO)

    // ktor http client
    internal val client = HttpClient(OkHttp) {
        install(UserAgent) {
            agent = userAgent
        }
        install(HttpTimeout) {
            requestTimeoutMillis = timeout.inWholeMilliseconds
            socketTimeoutMillis = 60.seconds.inWholeMilliseconds
        }
        install(ContentNegotiation) {
            jackson() {
                registerKotlinModule()
            }
        }
    }

    private var parentMessageId: String = UUID.randomUUID().toString()
    private var conversationId: String? = null

    init {
        // reset session token cache each 30 seconds
        scope.launch {
            cachedAccessToken.set(null)
            delay(30.seconds)
        }
    }

    fun newConversation(): Conversation {
        return Conversation(this)
    }

    @Throws(IllegalStateException::class)
    internal suspend fun refreshAccessToken(): String {
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
