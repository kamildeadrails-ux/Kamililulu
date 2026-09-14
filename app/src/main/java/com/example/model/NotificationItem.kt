package com.example.model

enum class NotificationType {
    NEW_ORDER,
    PRICE_PROPOSAL,
    PRICE_ACCEPTED,
    PRICE_DECLINED,
    CHAT_MESSAGE,
    ORDER_STATUS_CHANGE,
    PAYMENT_CONFIRMED
}

data class NotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val type: NotificationType,
    val orderId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
