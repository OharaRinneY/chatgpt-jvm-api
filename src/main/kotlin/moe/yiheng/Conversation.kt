package moe.yiheng

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import java.util.*

class Conversation (val api: ChatGPTAPI) {
    private var conversationId : String? = null
    private var parentMessageId: String = UUID.randomUUID().toString()

    /**
     * send message to chat bot
     * @param message message to send
     * @param processor a callback function (real-time process the response)
     * @return response from chat bot after api sends DONE. Sometimes the api may be stuck, to avoid it,
     * You can use callback function.
     */
    suspend fun sendMessage(
        message: String,
        processor: ((String) -> Unit)? = null,
    ): String {
        val client = api.client
        val accessToken = api.refreshAccessToken()
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
        val url = "${api.backendApiBaseUrl}/conversation"
        var returnMessage = ""
        val mapper = ObjectMapper().apply {
            propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
            registerKotlinModule()
        }
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
                val parseData = mapper.readValue(data, ConversationResponseEvent::class.java)
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


    /**
     * reset the conversation
     */
    fun resetConversation() {
        this.conversationId = null
        this.parentMessageId = UUID.randomUUID().toString()
    }
}