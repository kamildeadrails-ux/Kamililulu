package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.Order
import com.example.model.OrderStatus

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val userName: String,
    val userAvatarEmoji: String,
    val title: String,
    val description: String,
    val targetStoreOrPlace: String,
    val deliveryAddress: String,
    val urgency: String,
    val status: String,
    val proposedPrice: Double?,
    val adminNote: String?,
    val isPaidInReality: Boolean,
    val paymentMethodNote: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomain(): Order {
        val parsedStatus = try {
            OrderStatus.valueOf(status)
        } catch (e: Exception) {
            OrderStatus.PENDING_REVIEW
        }
        return Order(
            id = id,
            userId = userId,
            userName = userName,
            userAvatarEmoji = userAvatarEmoji,
            title = title,
            description = description,
            targetStoreOrPlace = targetStoreOrPlace,
            deliveryAddress = deliveryAddress,
            urgency = urgency,
            status = parsedStatus,
            proposedPrice = proposedPrice,
            adminNote = adminNote,
            isPaidInReality = isPaidInReality,
            paymentMethodNote = paymentMethodNote,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(order: Order): OrderEntity {
            return OrderEntity(
                id = order.id,
                userId = order.userId,
                userName = order.userName,
                userAvatarEmoji = order.userAvatarEmoji,
                title = order.title,
                description = order.description,
                targetStoreOrPlace = order.targetStoreOrPlace,
                deliveryAddress = order.deliveryAddress,
                urgency = order.urgency,
                status = order.status.name,
                proposedPrice = order.proposedPrice,
                adminNote = order.adminNote,
                isPaidInReality = order.isPaidInReality,
                paymentMethodNote = order.paymentMethodNote,
                createdAt = order.createdAt,
                updatedAt = order.updatedAt
            )
        }
    }
}
