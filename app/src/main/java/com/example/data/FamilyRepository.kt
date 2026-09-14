package com.example.data

import android.content.Context
import com.example.model.ChatMessage
import com.example.model.NotificationItem
import com.example.model.NotificationType
import com.example.model.Order
import com.example.model.OrderStatus
import com.example.model.ProfileRole
import com.example.model.UserProfile
import com.example.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

class FamilyRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val scope: CoroutineScope
) {
    private val dao = database.familyDao()
    private val prefs = context.getSharedPreferences("family_brother_prefs", Context.MODE_PRIVATE)

    private val _currentProfileId = MutableStateFlow<String?>(prefs.getString("registered_brother_id", null))
    val currentProfileId = _currentProfileId.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            seedInitialDataIfNeeded()
        }
    }

    fun setCurrentProfile(profileId: String) {
        prefs.edit().putString("registered_brother_id", profileId).apply()
        _currentProfileId.value = profileId
    }

    fun clearRegistration() {
        prefs.edit().remove("registered_brother_id").apply()
        _currentProfileId.value = null
    }

    fun getAllProfilesFlow(): Flow<List<UserProfile>> {
        return dao.getAllProfilesFlow().map { list -> list.map { it.toDomain() } }
    }

    fun getAllOrdersFlow(): Flow<List<Order>> {
        return dao.getAllOrdersFlow().map { list -> list.map { it.toDomain() } }
    }

    fun getMessagesForOrderFlow(orderId: String): Flow<List<ChatMessage>> {
        return dao.getMessagesForOrderFlow(orderId).map { list -> list.map { it.toDomain() } }
    }

    fun getAllMessagesFlow(): Flow<List<ChatMessage>> {
        return dao.getAllMessagesFlow().map { list -> list.map { it.toDomain() } }
    }

    fun getNotificationsFlow(): Flow<List<NotificationItem>> {
        return dao.getNotificationsFlow().map { list -> list.map { it.toDomain() } }
    }

    suspend fun createOrder(
        title: String,
        description: String,
        storeOrPlace: String,
        deliveryAddress: String,
        urgency: String,
        user: UserProfile
    ): String {
        val id = "ORD-${System.currentTimeMillis().toString().takeLast(4)}"
        val order = Order(
            id = id,
            userId = user.id,
            userName = user.name,
            userAvatarEmoji = user.avatarEmoji,
            title = title.trim(),
            description = description.trim(),
            targetStoreOrPlace = storeOrPlace.trim(),
            deliveryAddress = deliveryAddress.trim().ifEmpty { "Дом (Кухня)" },
            urgency = urgency,
            status = OrderStatus.PENDING_REVIEW,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        dao.insertOrder(OrderEntity.fromDomain(order))

        // System notification to Admin
        val notif = NotificationItem(
            id = UUID.randomUUID().toString(),
            title = "🆕 Новый заказ от ${user.name}",
            message = "«$title» в $deliveryAddress. Нажмите, чтобы оценить цену.",
            type = NotificationType.NEW_ORDER,
            orderId = id,
            timestamp = System.currentTimeMillis()
        )
        dao.insertNotification(NotificationEntity.fromDomain(notif))
        NotificationHelper.showNotification(
            context,
            notif.title,
            notif.message,
            orderId = id
        )

        // System chat welcome message
        val welcomeMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            orderId = id,
            senderId = "system",
            senderName = "Система",
            senderRole = ProfileRole.ADMIN,
            text = "Заказ создан. Ожидайте, пока администратор проверит наличие и назовет цену.",
            isSystemEvent = true
        )
        dao.insertMessage(ChatMessageEntity.fromDomain(welcomeMsg))

        return id
    }

    suspend fun proposePrice(orderId: String, price: Double, note: String, adminName: String) {
        val existingEntity = dao.getOrderById(orderId) ?: return
        val currentOrder = existingEntity.toDomain()
        val updated = currentOrder.copy(
            proposedPrice = price,
            adminNote = note.trim().ifEmpty { null },
            status = OrderStatus.PRICE_PROPOSED,
            updatedAt = System.currentTimeMillis()
        )
        dao.insertOrder(OrderEntity.fromDomain(updated))

        // System message in chat
        val noteText = if (note.isNotBlank()) " ($note)" else ""
        val chatMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            orderId = orderId,
            senderId = "admin",
            senderName = adminName,
            senderRole = ProfileRole.ADMIN,
            text = "💰 Админ предложил цену: ${price.toInt()} ₽$noteText. Ждём подтверждения покупателя.",
            isSystemEvent = true
        )
        dao.insertMessage(ChatMessageEntity.fromDomain(chatMsg))

        // Notification for the buyer
        val notif = NotificationItem(
            id = UUID.randomUUID().toString(),
            title = "💰 Названа цена для «${currentOrder.title}»",
            message = "Цена: ${price.toInt()} ₽. Вы можете купить или отклонить заказ.",
            type = NotificationType.PRICE_PROPOSAL,
            orderId = orderId,
            timestamp = System.currentTimeMillis()
        )
        dao.insertNotification(NotificationEntity.fromDomain(notif))
        NotificationHelper.showNotification(
            context,
            notif.title,
            notif.message,
            orderId = orderId
        )
    }

    suspend fun respondToPrice(orderId: String, accept: Boolean, userName: String) {
        val existingEntity = dao.getOrderById(orderId) ?: return
        val currentOrder = existingEntity.toDomain()

        val newStatus = if (accept) OrderStatus.ACCEPTED else OrderStatus.DECLINED
        val updated = currentOrder.copy(
            status = newStatus,
            updatedAt = System.currentTimeMillis()
        )
        dao.insertOrder(OrderEntity.fromDomain(updated))

        val text = if (accept) {
            "✅ $userName согласился(лась) с ценой ${currentOrder.proposedPrice?.toInt() ?: 0} ₽. Заказ согласован к покупке!"
        } else {
            "❌ $userName отклонил(а) предложенную цену."
        }

        val chatMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            orderId = orderId,
            senderId = currentOrder.userId,
            senderName = userName,
            senderRole = ProfileRole.BUYER,
            text = text,
            isSystemEvent = true
        )
        dao.insertMessage(ChatMessageEntity.fromDomain(chatMsg))

        val notif = NotificationItem(
            id = UUID.randomUUID().toString(),
            title = if (accept) "✅ Заказ согласован!" else "❌ Цена отклонена покупателем",
            message = "$userName для «${currentOrder.title}»: ${if (accept) "Согласен на покупку!" else "Отклонил предложение."}",
            type = if (accept) NotificationType.PRICE_ACCEPTED else NotificationType.PRICE_DECLINED,
            orderId = orderId,
            timestamp = System.currentTimeMillis()
        )
        dao.insertNotification(NotificationEntity.fromDomain(notif))
        NotificationHelper.showNotification(
            context,
            notif.title,
            notif.message,
            orderId = orderId
        )
    }

    suspend fun updateOrderStatus(orderId: String, status: OrderStatus, isPaid: Boolean = false) {
        val existingEntity = dao.getOrderById(orderId) ?: return
        val currentOrder = existingEntity.toDomain()
        val updated = currentOrder.copy(
            status = status,
            isPaidInReality = if (isPaid) true else currentOrder.isPaidInReality,
            updatedAt = System.currentTimeMillis()
        )
        dao.insertOrder(OrderEntity.fromDomain(updated))

        val statusLabel = when (status) {
            OrderStatus.PURCHASING -> "🛍️ Товар закупается в магазине"
            OrderStatus.DELIVERED_UNPAID -> "📦 Товар доставлен! Ожидает оплаты в реальности"
            OrderStatus.COMPLETED_PAID -> "💵 Оплата подтверждена в реальности! Заказ выполнен"
            OrderStatus.CANCELLED -> "🚫 Заказ отменён"
            else -> status.name
        }

        val chatMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            orderId = orderId,
            senderId = "system",
            senderName = "Система",
            senderRole = ProfileRole.ADMIN,
            text = statusLabel,
            isSystemEvent = true
        )
        dao.insertMessage(ChatMessageEntity.fromDomain(chatMsg))

        val notif = NotificationItem(
            id = UUID.randomUUID().toString(),
            title = "Статус заказа изменен",
            message = "«${currentOrder.title}»: $statusLabel",
            type = if (isPaid) NotificationType.PAYMENT_CONFIRMED else NotificationType.ORDER_STATUS_CHANGE,
            orderId = orderId,
            timestamp = System.currentTimeMillis()
        )
        dao.insertNotification(NotificationEntity.fromDomain(notif))
        NotificationHelper.showNotification(
            context,
            notif.title,
            notif.message,
            orderId = orderId
        )
    }

    suspend fun confirmPaymentInReality(orderId: String, amount: Double, methodNote: String) {
        val existingEntity = dao.getOrderById(orderId) ?: return
        val currentOrder = existingEntity.toDomain()
        val updated = currentOrder.copy(
            status = OrderStatus.COMPLETED_PAID,
            isPaidInReality = true,
            paymentMethodNote = methodNote,
            updatedAt = System.currentTimeMillis()
        )
        dao.insertOrder(OrderEntity.fromDomain(updated))

        val chatMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            orderId = orderId,
            senderId = "system",
            senderName = "Касса",
            senderRole = ProfileRole.ADMIN,
            text = "💵 Оплата в реальной жизни подтверждена: ${amount.toInt()} ₽ ($methodNote). Спасибо!",
            isSystemEvent = true
        )
        dao.insertMessage(ChatMessageEntity.fromDomain(chatMsg))

        val notif = NotificationItem(
            id = UUID.randomUUID().toString(),
            title = "💵 Оплата подтверждена",
            message = "Заказ «${currentOrder.title}» оплачен в реальности (${amount.toInt()} ₽)",
            type = NotificationType.PAYMENT_CONFIRMED,
            orderId = orderId,
            timestamp = System.currentTimeMillis()
        )
        dao.insertNotification(NotificationEntity.fromDomain(notif))
        NotificationHelper.showNotification(
            context,
            notif.title,
            notif.message,
            orderId = orderId
        )
    }

    suspend fun sendChatMessage(orderId: String, text: String, sender: UserProfile) {
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            orderId = orderId,
            senderId = sender.id,
            senderName = sender.name,
            senderRole = sender.role,
            text = text.trim(),
            timestamp = System.currentTimeMillis(),
            isSystemEvent = false
        )
        dao.insertMessage(ChatMessageEntity.fromDomain(msg))

        // Trigger notification
        val notif = NotificationItem(
            id = UUID.randomUUID().toString(),
            title = "💬 Сообщение от ${sender.name}",
            message = text.trim(),
            type = NotificationType.CHAT_MESSAGE,
            orderId = orderId,
            timestamp = System.currentTimeMillis()
        )
        dao.insertNotification(NotificationEntity.fromDomain(notif))
        NotificationHelper.showNotification(
            context,
            notif.title,
            notif.message,
            orderId = orderId
        )
    }

    suspend fun updateProfile(profile: UserProfile) {
        dao.insertProfile(UserProfileEntity.fromDomain(profile))
    }

    suspend fun deleteOrder(orderId: String) {
        dao.deleteOrder(orderId)
    }

    suspend fun markAllNotificationsAsRead() {
        dao.markAllNotificationsAsRead()
    }

    suspend fun clearAllNotifications() {
        dao.clearAllNotifications()
    }

    suspend fun saveImportedData(orders: List<Order>, profiles: List<UserProfile>, messages: List<ChatMessage>) {
        if (orders.isNotEmpty()) {
            dao.insertOrders(orders.map { OrderEntity.fromDomain(it) })
        }
        if (profiles.isNotEmpty()) {
            dao.insertProfiles(profiles.map { UserProfileEntity.fromDomain(it) })
        }
        if (messages.isNotEmpty()) {
            dao.insertMessages(messages.map { ChatMessageEntity.fromDomain(it) })
        }
    }

    private suspend fun seedInitialDataIfNeeded() {
        val brothersCount = dao.getBrothersCount()
        val defaultProfiles = listOf(
            UserProfile(
                id = "kamil_admin",
                name = "Камиль (Админ)",
                role = ProfileRole.ADMIN,
                avatarEmoji = "👑",
                colorHex = 0xFF6366F1,
                savedAddresses = listOf("Дом: Зал", "Дом: Кухня", "Комната Камиля"),
                preferences = "Главный администратор. Проверяет цены, заказывает и подтверждает оплату в реальности.",
                phone = "+7 900 111-22-33"
            ),
            UserProfile(
                id = "aykhan_brother",
                name = "Айхан",
                role = ProfileRole.BUYER,
                avatarEmoji = "😎",
                colorHex = 0xFF2563EB,
                savedAddresses = listOf("Комната Айхана", "Дом: Кухня"),
                preferences = "Любит вкусную еду, напитки и быструю доставку",
                phone = "+7 900 222-33-44"
            ),
            UserProfile(
                id = "timur_brother",
                name = "Тимур",
                role = ProfileRole.BUYER,
                avatarEmoji = "⚡",
                colorHex = 0xFFF59E0B,
                savedAddresses = listOf("Комната Тимура", "Дом: Гостиная"),
                preferences = "Гаджеты, игры, снеки и аксессуары",
                phone = "+7 900 333-44-55"
            )
        )

        if (brothersCount < 3) {
            dao.deleteAllProfiles()
            dao.insertProfiles(defaultProfiles.map { UserProfileEntity.fromDomain(it) })

            // Seed sample orders specifically for the 3 brothers
            val sampleOrder1 = Order(
                id = "ORD-1001",
                userId = "aykhan_brother",
                userName = "Айхан",
                userAvatarEmoji = "😎",
                title = "Пицца Пепперони и Кола",
                description = "Большую пиццу на тонком тесте и 1л напиток к вечернему просмотру",
                targetStoreOrPlace = "Додо пицца или Доминос",
                deliveryAddress = "Комната Айхана",
                urgency = "К вечеру",
                status = OrderStatus.PRICE_PROPOSED,
                proposedPrice = 690.0,
                adminNote = "Камиль: Нашёл комбо за 690 ₽ вместо 850 ₽. Берём?",
                createdAt = System.currentTimeMillis() - 3600_000,
                updatedAt = System.currentTimeMillis() - 1800_000
            )

            val sampleOrder2 = Order(
                id = "ORD-1002",
                userId = "timur_brother",
                userName = "Тимур",
                userAvatarEmoji = "⚡",
                title = "Механическая клавиатура / Кабель Type-C",
                description = "Плетёный провод 2м для зарядки и геймпада",
                targetStoreOrPlace = "DNS / Ozon экспресс",
                deliveryAddress = "Комната Тимура",
                urgency = "Обычная",
                status = OrderStatus.PENDING_REVIEW,
                proposedPrice = null,
                createdAt = System.currentTimeMillis() - 1200_000,
                updatedAt = System.currentTimeMillis() - 1200_000
            )

            val sampleOrder3 = Order(
                id = "ORD-1003",
                userId = "aykhan_brother",
                userName = "Айхан",
                userAvatarEmoji = "😎",
                title = "Энергетик и чипсы с сыром",
                description = "Перекус на вечер",
                targetStoreOrPlace = "Пятёрочка у дома",
                deliveryAddress = "Дом: Кухня",
                urgency = "Срочно",
                status = OrderStatus.DELIVERED_UNPAID,
                proposedPrice = 280.0,
                adminNote = "Камиль: Купил в магазине, лежит на кухне! Жду 280 ₽ в реальности",
                isPaidInReality = false,
                createdAt = System.currentTimeMillis() - 7200_000,
                updatedAt = System.currentTimeMillis() - 600_000
            )

            dao.insertOrders(listOf(
                OrderEntity.fromDomain(sampleOrder1),
                OrderEntity.fromDomain(sampleOrder2),
                OrderEntity.fromDomain(sampleOrder3)
            ))

            // Seed sample chat messages between brothers
            val msg1 = ChatMessage(
                id = "MSG-1",
                orderId = "ORD-1001",
                senderId = "aykhan_brother",
                senderName = "Айхан",
                senderRole = ProfileRole.BUYER,
                text = "Камиль, закажи пиццу пожалуйста, очень хочется перекусить!",
                timestamp = System.currentTimeMillis() - 3500_000
            )
            val msg2 = ChatMessage(
                id = "MSG-2",
                orderId = "ORD-1001",
                senderId = "kamil_admin",
                senderName = "Камиль (Админ)",
                senderRole = ProfileRole.ADMIN,
                text = "Посмотрел в приложении: есть комбо за 690 ₽. Будешь брать?",
                timestamp = System.currentTimeMillis() - 1800_000
            )
            val msgSys = ChatMessage(
                id = "MSG-SYS",
                orderId = "ORD-1001",
                senderId = "system",
                senderName = "Система",
                senderRole = ProfileRole.ADMIN,
                text = "💰 Камиль предложил цену: 690 ₽ (Нашёл комбо за 690 ₽ вместо 850 ₽. Берём?)",
                timestamp = System.currentTimeMillis() - 1790_000,
                isSystemEvent = true
            )

            dao.insertMessages(listOf(
                ChatMessageEntity.fromDomain(msg1),
                ChatMessageEntity.fromDomain(msg2),
                ChatMessageEntity.fromDomain(msgSys)
            ))

            // Initial notification
            val initialNotif = NotificationItem(
                id = "NOTIF-1",
                title = "💰 Предложена цена для «Пицца Пепперони»",
                message = "Камиль предложил цену 690 ₽. Нажмите, чтобы согласовать!",
                type = NotificationType.PRICE_PROPOSAL,
                orderId = "ORD-1001",
                timestamp = System.currentTimeMillis() - 1790_000
            )
            dao.insertNotification(NotificationEntity.fromDomain(initialNotif))
        }
    }
}
