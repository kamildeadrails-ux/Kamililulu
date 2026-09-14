package com.example.data.db

import androidx.room.TypeConverter
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentStatus
import com.example.data.model.UserRole

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return value?.joinToString(";;;") ?: ""
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(";;;").filter { it.isNotBlank() }
    }

    @TypeConverter
    fun fromUserRole(role: UserRole?): String = role?.name ?: UserRole.MEMBER.name

    @TypeConverter
    fun toUserRole(value: String?): UserRole = try {
        UserRole.valueOf(value ?: UserRole.MEMBER.name)
    } catch (e: Exception) {
        UserRole.MEMBER
    }

    @TypeConverter
    fun fromOrderStatus(status: OrderStatus?): String = status?.name ?: OrderStatus.PENDING_REVIEW.name

    @TypeConverter
    fun toOrderStatus(value: String?): OrderStatus = try {
        OrderStatus.valueOf(value ?: OrderStatus.PENDING_REVIEW.name)
    } catch (e: Exception) {
        OrderStatus.PENDING_REVIEW
    }

    @TypeConverter
    fun fromPaymentStatus(status: PaymentStatus?): String = status?.name ?: PaymentStatus.UNPAID.name

    @TypeConverter
    fun toPaymentStatus(value: String?): PaymentStatus = try {
        PaymentStatus.valueOf(value ?: PaymentStatus.UNPAID.name)
    } catch (e: Exception) {
        PaymentStatus.UNPAID
    }
}
