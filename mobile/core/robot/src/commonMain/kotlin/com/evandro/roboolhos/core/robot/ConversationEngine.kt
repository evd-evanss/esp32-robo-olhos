package com.evandro.roboolhos.core.robot

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

data class GeneratedReply(val text: String, val emotion: String)

interface ConversationEngine {
    val available: Boolean
    suspend fun respond(message: String, state: RobotState): GeneratedReply?
}

object EmbeddedFallbackEngine : ConversationEngine {
    override val available = false
    override suspend fun respond(message: String, state: RobotState): GeneratedReply? = null
}

class CallbackConversationEngine(
    override val available: Boolean,
    private val responder: (String, (String?, String?) -> Unit) -> Unit,
) : ConversationEngine {
    override suspend fun respond(message: String, state: RobotState): GeneratedReply? {
        if (!available) return null
        val prompt = buildString {
            appendLine("Mensagem do usuário: $message")
            appendLine("Nome conhecido: ${state.name.ifBlank { "não informado" }}")
            appendLine("Interesse conhecido: ${state.interest.ifBlank { "não informado" }}")
            appendLine("Humor atual do robô: ${state.mood}")
            append("Responda naturalmente em português brasileiro.")
        }
        return suspendCancellableCoroutine { continuation ->
            responder(prompt) { reply, emotion ->
                if (!continuation.isActive) return@responder
                val cleanReply = reply?.trim()?.take(320).orEmpty()
                val cleanEmotion = emotion?.trim()?.lowercase()
                    ?.takeIf { it in EXPRESSIONS } ?: "neutro"
                continuation.resume(cleanReply.takeIf { it.isNotBlank() }?.let { GeneratedReply(it, cleanEmotion) })
            }
        }
    }

    private companion object {
        val EXPRESSIONS = setOf("feliz", "triste", "raiva", "neutro", "surpresa", "curioso", "sono")
    }
}
