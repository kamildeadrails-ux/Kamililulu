package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.ChatMessageEntity
import com.example.data.model.NotificationEntity
import com.example.data.model.OrderEntity
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@Database(
    entities = [
        UserProfile::class,
        OrderEntity::class,
        ChatMessageEntity::class,
        NotificationEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun orderDao(): OrderDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "family_orders.db"
                ).addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed initial family members and welcome data
                        CoroutineScope(Dispatchers.IO).launch {
                            val database = getInstance(context)
                            seedInitialData(database)
                        }
                    }
                }).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun seedInitialData(database: AppDatabase) {
            val sonId = "user_son"
            val momId = "user_mom"
            val dadId = "user_dad"
            val daughterId = "user_daughter"

            val profiles = listOf(
                UserProfile(
                    id = sonId,
                    name = "Камиль (Сын)",
                    role = UserRole.MEMBER,
                    avatarEmoji = "🧑‍💻",
                    accentColorHex = "#4F46E5",
                    savedAddresses = listOf("Моя комната", "Письменный стол", "Школа №4"),
                    preferences = "Вкус: без лука, напитки без сахара. Заказ желательно из WB или ВкусВилл",
                    isCurrent = true
                ),
                UserProfile(
                    id = momId,
                    name = "Мама (Администратор)",
                    role = UserRole.ADMIN,
                    avatarEmoji = "👩‍💼",
                    accentColorHex = "#EC4899",
                    savedAddresses = listOf("Кухня", "Гостиная", "Дом"),
                    preferences = "Смотреть срок годности, проверять акции в Пятёрочке",
                    isCurrent = false
                ),
                UserProfile(
                    id = dadId,
                    name = "Папа (Администратор)",
                    role = UserRole.ADMIN,
                    avatarEmoji = "👨‍🔧",
                    accentColorHex = "#2563EB",
                    savedAddresses = listOf("Гараж", "Дом", "Мастерская"),
                    preferences = "Чек обязательно, инструменты и хозтовары только оригинал",
                    isCurrent = false
                ),
                UserProfile(
                    id = daughterId,
                    name = "Алиса (Дочь)",
                    role = UserRole.MEMBER,
                    avatarEmoji = "👧",
                    accentColorHex = "#10B981",
                    savedAddresses = listOf("Детская", "Дом"),
                    preferences = "Фломастеры, раскраски, мармеладные мишки",
                    isCurrent = false
                )
            )
            database.userProfileDao().insertProfiles(profiles)

            // Initial demo orders showcasing the exact workflow
            val order1Id = "ord_sample_1"
            val order2Id = "ord_sample_2"
            val now = System.currentTimeMillis()

            val order1 = OrderEntity(
                id = order1Id,
                userId = sonId,
                userName = "Камиль (Сын)",
                userAvatar = "🧑‍💻",
                itemTitle = "Пицца Пепперони 30см и Кола Зеро",
                itemDescription = "Заказать по дороге домой из Додо Пиццы, с острым соусом",
                targetStore = "Додо Пицца",
                deliveryAddress = "Моя комната",
                urgency = "Срочно к ужину",
                budgetEstimate = 700.0,
                status = OrderStatus.PRICE_QUOTED,
                quotedPrice = 649.0,
                adminNote = "Нашла промокод на скидку 15%! Выйдет за 649₽ вместо 750₽. Берём?",
                paymentStatus = PaymentStatus.UNPAID,
                createdAt = now - 3600000,
                updatedAt = now - 1800000
            )

            val order2 = OrderEntity(
                id = order2Id,
                userId = daughterId,
                userName = "Алиса (Дочь)",
                userAvatar = "👧",
                itemTitle = "Набор маркеров для скетчинга 24 цвета",
                itemDescription = "Для школьного проекта по рисованию",
                targetStore = "Комус или WB",
                deliveryAddress = "Детская",
                urgency = "Обычная",
                budgetEstimate = 400.0,
                status = OrderStatus.ACCEPTED,
                quotedPrice = 380.0,
                adminNote = "Заказал на Wildberries, прибудет завтра после обеда",
                paymentStatus = PaymentStatus.PAID_CASH,
                paymentMethod = "Наличными из копилки при передаче",
                createdAt = now - 86400000,
                updatedAt = now - 40000000
            )

            database.orderDao().insertOrder(order1)
            database.orderDao().insertOrder(order2)

            // Initial chat messages
            database.chatMessageDao().insertMessage(
                ChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    orderId = order1Id,
                    senderId = sonId,
                    senderName = "Камиль (Сын)",
                    senderRole = UserRole.MEMBER,
                    senderAvatar = "🧑‍💻",
                    text = "Привет! Закажешь пиццу после работы? Очень проголодался!",
                    timestamp = now - 3500000
                )
            )
            database.chatMessageDao().insertMessage(
                ChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    orderId = order1Id,
                    senderId = momId,
                    senderName = "Мама (Администратор)",
                    senderRole = UserRole.ADMIN,
                    senderAvatar = "👩‍💼",
                    text = "Привет! Посмотрела в приложении, с купоном выйдет 649₽. Назначила цену в заказе, подтверди покупку в приложении!",
                    timestamp = now - 1800000
                )
            )

            // Initial notifications
            database.notificationDao().insertNotification(
                NotificationEntity(
                    id = UUID.randomUUID().toString(),
                    targetUserId = sonId,
                    title = "💰 Цена назначена админом!",
                    message = "Мама оценила заказ 'Пицца Пепперони 30см' в 649 ₽. Подтвердите покупку!",
                    type = "PRICE_QUOTED",
                    orderId = order1Id,
                    timestamp = now - 1800000,
                    isRead = false
                )
            )
        }
    }
}
