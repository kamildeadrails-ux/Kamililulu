package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.NotificationItem
import com.example.model.NotificationType

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val message: String,
    val type: String,
    val orderId: String?,
    val timestamp: Long,
    val isRead: Boolean
) {
    fun toDomain(): NotificationItem {
        val parsedType = try {
            NotificationType.valueOf(type)
        } catch (e: Exception) {
            NotificationType.ORDER_STATUS_CHANGE
        }
        return NotificationItem(
            id = id,
            title = title,
            message = message,
            type = parsedType,
            orderId = orderId,
            timestamp = timestamp,
            isRead = isRead
        )
    }

    companion object {
        fun fromDomain(item: NotificationItem): NotificationEntity {
            return NotificationEntity(
                id = item.id,
                title = item.title,
                message = item.message,
                type = item.type.name,
                orderId = item.orderId,
                timestamp = item.timestamp,
                isRead = item.isRead
            )
        }
    }
}
