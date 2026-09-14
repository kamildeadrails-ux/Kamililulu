package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class UserRole {
    ADMIN,
    MEMBER;

    fun displayName(): String = when (this) {
        ADMIN -> "Администратор"
        MEMBER -> "Член семьи"
    }
}

enum class OrderStatus {
    PENDING_REVIEW, // Заказ создан, ожидает назначения цены админом
    PRICE_QUOTED,   // Админ назвал цену, ожидает решения покупателя
    ACCEPTED,       // Покупатель согласился с ценой, заказ принят в работу
    REJECTED,       // Покупатель отклонил цену или отменил заказ
    PURCHASED,      // Админ купил товар
    COMPLETED;      // Доставлен, передача и оплата завершены

    fun title(): String = when (this) {
        PENDING_REVIEW -> "Ожидает оценки"
        PRICE_QUOTED -> "Цена назначена"
        ACCEPTED -> "Принят к покупке"
        REJECTED -> "Отклонён"
        PURCHASED -> "Куплено"
        COMPLETED -> "Завершён"
    }
}

enum class PaymentStatus {
    UNPAID,
    PAID_CASH,
    PAID_TRANSFER,
    PAID_CONFIRMED;

    fun title(): String = when (this) {
        UNPAID -> "Не оплачен"
        PAID_CASH -> "Оплата наличными (в реальности)"
        PAID_TRANSFER -> "Перевод на карту"
        PAID_CONFIRMED -> "Оплата подтверждена"
    }
}

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: UserRole = UserRole.MEMBER,
    val avatarEmoji: String = "👤",
    val accentColorHex: String = "#4F46E5",
    val savedAddresses: List<String> = emptyList(),
    val preferences: String = "",
    val isCurrent: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val userName: String,
    val userAvatar: String,
    val itemTitle: String,
    val itemDescription: String = "",
    val targetStore: String = "",
    val deliveryAddress: String = "",
    val urgency: String = "Обычная", // "Срочно", "Обычная", "Не горит"
    val budgetEstimate: Double? = null,
    val status: OrderStatus = OrderStatus.PENDING_REVIEW,
    val quotedPrice: Double? = null,
    val adminNote: String? = null,
    val paymentStatus: PaymentStatus = PaymentStatus.UNPAID,
    val paymentMethod: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val orderId: String = "general", // "general" or specific orderId
    val senderId: String,
    val senderName: String,
    val senderRole: UserRole,
    val senderAvatar: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSystemEvent: Boolean = false
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val targetUserId: String, // userId or "ALL" or "ADMIN"
    val title: String,
    val message: String,
    val type: String,
    val orderId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
