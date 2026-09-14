package com.example.model

data class ChatMessage(
    val id: String,
    val orderId: String,
    val senderId: String,
    val senderName: String,
    val senderRole: ProfileRole,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSystemEvent: Boolean = false
)
