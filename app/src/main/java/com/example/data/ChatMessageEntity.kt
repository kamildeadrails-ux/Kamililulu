package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.ChatMessage
import com.example.model.ProfileRole

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val senderId: String,
    val senderName: String,
    val senderRole: String,
    val text: String,
    val timestamp: Long,
    val isSystemEvent: Boolean
) {
    fun toDomain(): ChatMessage {
        val parsedRole = try {
            ProfileRole.valueOf(senderRole)
        } catch (e: Exception) {
            ProfileRole.BUYER
        }
        return ChatMessage(
            id = id,
            orderId = orderId,
            senderId = senderId,
            senderName = senderName,
            senderRole = parsedRole,
            text = text,
            timestamp = timestamp,
            isSystemEvent = isSystemEvent
        )
    }

    companion object {
        fun fromDomain(msg: ChatMessage): ChatMessageEntity {
            return ChatMessageEntity(
                id = msg.id,
                orderId = msg.orderId,
                senderId = msg.senderId,
                senderName = msg.senderName,
                senderRole = msg.senderRole.name,
                text = msg.text,
                timestamp = msg.timestamp,
                isSystemEvent = msg.isSystemEvent
            )
        }
    }
}
