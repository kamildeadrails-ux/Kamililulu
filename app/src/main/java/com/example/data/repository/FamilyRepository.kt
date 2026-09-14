package com.example.data.repository

import android.content.Context
import com.example.data.db.AppDatabase
import com.example.data.model.ChatMessageEntity
import com.example.data.model.NotificationEntity
import com.example.data.model.OrderEntity
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.p2p.FamilyP2pServer
import com.example.service.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID

class FamilyRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val notificationHelper: NotificationHelper
) {
    val p2pServer = FamilyP2pServer(context, database, notificationHelper)

    val profiles: Flow<List<UserProfile>> = database.userProfileDao().getAllProfiles()
    val currentProfile: Flow<UserProfile?> = database.userProfileDao().getCurrentProfile()
    val allOrders: Flow<List<OrderEntity>> = database.orderDao().getAllOrders()

    suspend fun selectProfile(profileId: String) {
        database.userProfileDao().selectProfile(profileId)
    }

    suspend fun createProfile(
        name: String,
        role: UserRole,
        avatarEmoji: String,
        accentColorHex: String,
        savedAddresses: List<String>,
        preferences: String
    ): UserProfile {
        val newProfile = UserProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            role = role,
            avatarEmoji = avatarEmoji,
            accentColorHex = accentColorHex,
            savedAddresses = savedAddresses,
            preferences = preferences,
            isCurrent = false
        )
        database.userProfileDao().insertProfile(newProfile)
        return newProfile
    }

    suspend fun updateProfile(profile: UserProfile) {
        database.userProfileDao().updateProfile(profile)
    }

    suspend fun addSavedAddress(profileId: String, newAddress: String) {
        val profile = database.userProfileDao().getProfileById(profileId) ?: return
        if (!profile.savedAddresses.contains(newAddress.trim())) {
            val updated = profile.copy(savedAddresses = profile.savedAddresses + newAddress.trim())
            database.userProfileDao().updateProfile(updated)
        }
    }

    suspend fun removeSavedAddress(profileId: String, addressToRemove: String) {
        val profile = database.userProfileDao().getProfileById(profileId) ?: return
        val updated = profile.copy(savedAddresses = profile.savedAddresses.filter { it != addressToRemove })
        database.userProfileDao().updateProfile(updated)
    }

    suspend fun createOrder(
        title: String,
        description: String,
        targetStore: String,
        deliveryAddress: String,
        urgency: String,
        budgetEstimate: Double?
    ): OrderEntity {
        val current = database.userProfileDao().getCurrentProfile().firstOrNull()
        val userId = current?.id ?: "user_default"
        val userName = current?.name ?: "Член семьи"
        val userAvatar = current?.avatarEmoji ?: "👤"

        val order = OrderEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            userName = userName,
            userAvatar = userAvatar,
            itemTitle = title,
            itemDescription = description,
            targetStore = targetStore,
            deliveryAddress = deliveryAddress,
            urgency = urgency,
            budgetEstimate = budgetEstimate,
            status = OrderStatus.PENDING_REVIEW,
            paymentStatus = PaymentStatus.UNPAID
        )

        database.orderDao().insertOrder(order)

        // Trigger notification to Administrator
        val alertTitle = "🔔 Новый семейный заказ!"
        val alertMessage = "$userName хочет: '$title'. Назначьте цену в админ-панели."
        notificationHelper.triggerNotification(
            title = alertTitle,
            message = alertMessage,
            channelId = NotificationHelper.CHANNEL_ADMIN,
            orderId = order.id,
            type = "ORDER_NEW"
        )

        database.notificationDao().insertNotification(
            NotificationEntity(
                targetUserId = "ADMIN",
                title = alertTitle,
                message = alertMessage,
                type = "ORDER_NEW",
                orderId = order.id
            )
        )

        // System chat message in order thread
        database.chatMessageDao().insertMessage(
            ChatMessageEntity(
                orderId = order.id,
                senderId = "system",
                senderName = "Система",
                senderRole = UserRole.ADMIN,
                senderAvatar = "⚙️",
                text = "Заказ создан: $title ($deliveryAddress). Ожидает оценки цены админом.",
                isSystemEvent = true
            )
        )

        return order
    }

    suspend fun quotePrice(orderId: String, price: Double, adminNote: String) {
        val order = database.orderDao().getOrderByIdSync(orderId) ?: return
        val current = database.userProfileDao().getCurrentProfile().firstOrNull()
        val adminName = current?.name ?: "Администратор"

        val updated = order.copy(
            status = OrderStatus.PRICE_QUOTED,
            quotedPrice = price,
            adminNote = adminNote,
            updatedAt = System.currentTimeMillis()
        )
        database.orderDao().updateOrder(updated)

        // Notify Buyer
        val alertTitle = "💰 Цена назначена: ${price.toInt()} ₽"
        val alertMessage = "$adminName оценил '$order.itemTitle' в ${price.toInt()} ₽. Вы согласны на покупку?"
        notificationHelper.triggerNotification(
            title = alertTitle,
            message = alertMessage,
            channelId = NotificationHelper.CHANNEL_ORDERS,
            orderId = order.id,
            type = "PRICE_QUOTED"
        )

        database.notificationDao().insertNotification(
            NotificationEntity(
                targetUserId = order.userId,
                title = alertTitle,
                message = alertMessage,
                type = "PRICE_QUOTED",
                orderId = order.id
            )
        )

        // Add to chat thread
        database.chatMessageDao().insertMessage(
            ChatMessageEntity(
                orderId = order.id,
                senderId = current?.id ?: "admin",
                senderName = adminName,
                senderRole = UserRole.ADMIN,
                senderAvatar = current?.avatarEmoji ?: "👩‍💼",
                text = "Назначил(а) цену: ${price.toInt()} ₽.${if (adminNote.isNotBlank()) " Заметка: $adminNote" else ""}"
            )
        )
    }

    suspend fun respondToQuote(orderId: String, accept: Boolean) {
        val order = database.orderDao().getOrderByIdSync(orderId) ?: return
        val current = database.userProfileDao().getCurrentProfile().firstOrNull()
        val buyerName = current?.name ?: order.userName

        val newStatus = if (accept) OrderStatus.ACCEPTED else OrderStatus.REJECTED
        val updated = order.copy(
            status = newStatus,
            updatedAt = System.currentTimeMillis()
        )
        database.orderDao().updateOrder(updated)

        val title = if (accept) "✅ Покупатель согласился с ценой!" else "❌ Покупатель отклонил предложение"
        val message = if (accept) {
            "$buyerName готов(а) купить '${order.itemTitle}' за ${order.quotedPrice?.toInt() ?: 0} ₽! Можно покупать."
        } else {
            "$buyerName отклонил(а) покупку '${order.itemTitle}' по предложенной цене."
        }

        notificationHelper.triggerNotification(
            title = title,
            message = message,
            channelId = NotificationHelper.CHANNEL_ADMIN,
            orderId = order.id,
            type = if (accept) "ORDER_ACCEPTED" else "ORDER_REJECTED"
        )

        database.notificationDao().insertNotification(
            NotificationEntity(
                targetUserId = "ADMIN",
                title = title,
                message = message,
                type = if (accept) "ORDER_ACCEPTED" else "ORDER_REJECTED",
                orderId = order.id
            )
        )

        database.chatMessageDao().insertMessage(
            ChatMessageEntity(
                orderId = order.id,
                senderId = order.userId,
                senderName = buyerName,
                senderRole = UserRole.MEMBER,
                senderAvatar = order.userAvatar,
                text = if (accept) "✅ Я согласен(на) на покупку за ${order.quotedPrice?.toInt() ?: 0} ₽!" else "❌ Отказываюсь от покупки."
            )
        )
    }

    suspend fun markPurchased(orderId: String) {
        val order = database.orderDao().getOrderByIdSync(orderId) ?: return
        val updated = order.copy(
            status = OrderStatus.PURCHASED,
            updatedAt = System.currentTimeMillis()
        )
        database.orderDao().updateOrder(updated)

        val alertTitle = "🛍️ Товар куплен админом!"
        val alertMessage = "'${order.itemTitle}' куплен и скоро будет передан вам."
        notificationHelper.triggerNotification(
            title = alertTitle,
            message = alertMessage,
            channelId = NotificationHelper.CHANNEL_ORDERS,
            orderId = order.id,
            type = "STATUS_CHANGE"
        )

        database.notificationDao().insertNotification(
            NotificationEntity(
                targetUserId = order.userId,
                title = alertTitle,
                message = alertMessage,
                type = "STATUS_CHANGE",
                orderId = order.id
            )
        )
    }

    suspend fun recordRealityPayment(orderId: String, paymentMethod: String) {
        val order = database.orderDao().getOrderByIdSync(orderId) ?: return
        val updated = order.copy(
            paymentStatus = PaymentStatus.PAID_CONFIRMED,
            paymentMethod = paymentMethod,
            updatedAt = System.currentTimeMillis()
        )
        database.orderDao().updateOrder(updated)

        val alertTitle = "💵 Оплата в реальности подтверждена!"
        val alertMessage = "Оплата ${order.quotedPrice?.toInt() ?: 0} ₽ за '${order.itemTitle}' зафиксирована ($paymentMethod)."
        notificationHelper.triggerNotification(
            title = alertTitle,
            message = alertMessage,
            channelId = NotificationHelper.CHANNEL_PAYMENT,
            orderId = order.id,
            type = "PAYMENT_CONFIRMED"
        )

        database.notificationDao().insertNotification(
            NotificationEntity(
                targetUserId = "ALL",
                title = alertTitle,
                message = alertMessage,
                type = "PAYMENT_CONFIRMED",
                orderId = order.id
            )
        )

        database.chatMessageDao().insertMessage(
            ChatMessageEntity(
                orderId = order.id,
                senderId = "system",
                senderName = "Оплата",
                senderRole = UserRole.ADMIN,
                senderAvatar = "💵",
                text = "Оплата в реальности подтверждена: $paymentMethod (${order.quotedPrice?.toInt() ?: 0} ₽).",
                isSystemEvent = true
            )
        )
    }

    suspend fun completeOrder(orderId: String) {
        val order = database.orderDao().getOrderByIdSync(orderId) ?: return
        val updated = order.copy(
            status = OrderStatus.COMPLETED,
            updatedAt = System.currentTimeMillis()
        )
        database.orderDao().updateOrder(updated)

        val alertTitle = "🎉 Заказ успешно завершён!"
        val alertMessage = "'${order.itemTitle}' передан в руки, заказ закрыт!"
        notificationHelper.triggerNotification(
            title = alertTitle,
            message = alertMessage,
            channelId = NotificationHelper.CHANNEL_ORDERS,
            orderId = order.id,
            type = "STATUS_CHANGE"
        )

        database.notificationDao().insertNotification(
            NotificationEntity(
                targetUserId = "ALL",
                title = alertTitle,
                message = alertMessage,
                type = "STATUS_CHANGE",
                orderId = order.id
            )
        )
    }

    suspend fun deleteOrder(order: OrderEntity) {
        database.orderDao().deleteOrder(order)
    }

    fun getOrderChat(orderId: String): Flow<List<ChatMessageEntity>> =
        database.chatMessageDao().getMessagesForOrder(orderId)

    fun getAllChat(): Flow<List<ChatMessageEntity>> =
        database.chatMessageDao().getAllMessages()

    suspend fun sendChatMessage(orderId: String, text: String) {
        val current = database.userProfileDao().getCurrentProfile().firstOrNull()
        val senderId = current?.id ?: "anonymous"
        val senderName = current?.name ?: "Член семьи"
        val senderRole = current?.role ?: UserRole.MEMBER
        val senderAvatar = current?.avatarEmoji ?: "👤"

        val chatMessage = ChatMessageEntity(
            orderId = orderId,
            senderId = senderId,
            senderName = senderName,
            senderRole = senderRole,
            senderAvatar = senderAvatar,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        database.chatMessageDao().insertMessage(chatMessage)

        // Find recipient
        val targetUser = if (senderRole == UserRole.ADMIN) {
            // Find order owner if orderId is specified
            if (orderId != "general") {
                val order = database.orderDao().getOrderByIdSync(orderId)
                order?.userId ?: "ALL"
            } else "ALL"
        } else {
            "ADMIN"
        }

        notificationHelper.triggerNotification(
            title = "💬 Сообщение от $senderName",
            message = text,
            channelId = NotificationHelper.CHANNEL_CHAT,
            orderId = orderId,
            type = "CHAT_MESSAGE"
        )

        database.notificationDao().insertNotification(
            NotificationEntity(
                targetUserId = targetUser,
                title = "💬 Сообщение от $senderName",
                message = text,
                type = "CHAT_MESSAGE",
                orderId = orderId
            )
        )
    }

    fun getNotificationsForUser(userId: String, isAdmin: Boolean): Flow<List<NotificationEntity>> =
        database.notificationDao().getNotificationsForUser(userId, isAdmin)

    fun getUnreadNotificationsCount(userId: String, isAdmin: Boolean): Flow<Int> =
        database.notificationDao().getUnreadCount(userId, isAdmin)

    suspend fun markNotificationRead(id: String) {
        database.notificationDao().markAsRead(id)
    }

    suspend fun markAllNotificationsRead() {
        database.notificationDao().markAllAsRead()
    }
}
