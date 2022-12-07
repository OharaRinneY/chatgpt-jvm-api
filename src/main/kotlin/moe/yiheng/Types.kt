package moe.yiheng

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

data class SessionResult(
    var user: User,
    var expires: String,
    var accessToken: String,
    var error: String?,
)

data class User(
    var id: String,
    var name: String,
    var email: String,
    var image: String,
    var picture: String,
    var groups: List<String>,
    var features: List<String>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ConversationJSONBody(
    var action: String,
    @JsonProperty("conversation_id") var conversationId: String? = null,
    var messages: List<Prompt>,
    var model: String,
    @JsonProperty("parent_message_id") var parentMessageId: String,
)

data class Prompt(
    val content: PromptContent,
    val id: String,
    val role: String,
)

data class PromptContent(
    @JsonProperty("content_type") val contentType: String,
    val parts: List<String>,
)

data class ConversationResponseEvent(
    val message: Message?,
    @JsonProperty("conversation_id") val conversationId: String?,
    val error: String?,
)

data class Message(
    val id: String,
    val content: MessageContent,
    val role: String,
    val user: String?,
    @JsonProperty("create_time") val createTime: String?,
    @JsonProperty("update_time") val updateTime: String?,
    @JsonProperty("end_turn") val endTurn: String?,
    val weight: Int,
    val recipient: String,
    val metadata: MessageMetadata,
)

data class MessageContent(
    @JsonProperty("content_type") val contentType: String,
    val parts: List<String>,
)


class MessageMetadata