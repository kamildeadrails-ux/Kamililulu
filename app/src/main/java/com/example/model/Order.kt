package com.example.model

enum class OrderStatus {
    PENDING_REVIEW,     // Ожидает оценки админом
    PRICE_PROPOSED,     // Админ предложил цену -> ждет согласия покупателя
    ACCEPTED,           // Покупатель согласился с ценой -> согласовано
    DECLINED,           // Покупатель отклонил предложенную цену
    PURCHASING,         // Закупщик покупает товар
    DELIVERED_UNPAID,   // Доставлено, ожидает оплаты в реальности
    COMPLETED_PAID,     // Оплачено в реальной жизни и завершено
    CANCELLED           // Отменено
}

data class Order(
    val id: String,
    val userId: String,
    val userName: String,
    val userAvatarEmoji: String = "👤",
    val title: String,
    val description: String,
    val targetStoreOrPlace: String = "",
    val deliveryAddress: String = "",
    val urgency: String = "Обычная",
    val status: OrderStatus = OrderStatus.PENDING_REVIEW,
    val proposedPrice: Double? = null,
    val adminNote: String? = null,
    val isPaidInReality: Boolean = false,
    val paymentMethodNote: String = "Наличные или перевод при встрече",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
