package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class InAppAlert(
    val id: String,
    val title: String,
    val message: String,
    val orderId: String? = null,
    val type: String
)

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ORDERS = "family_orders_channel"
        const val CHANNEL_CHAT = "family_chat_channel"
        const val CHANNEL_ADMIN = "family_admin_channel"
        const val CHANNEL_PAYMENT = "family_payment_channel"

        private val _inAppAlerts = MutableSharedFlow<InAppAlert>(extraBufferCapacity = 10)
        val inAppAlerts = _inAppAlerts.asSharedFlow()
    }

    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val systemNotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

            val ordersChannel = NotificationChannel(
                CHANNEL_ORDERS,
                "Заказы и согласование цен",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о назначении цены, принятии или отклонении заказа"
                enableVibration(true)
            }

            val chatChannel = NotificationChannel(
                CHANNEL_CHAT,
                "Семейный чат",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Сообщения от членов семьи и админа"
                enableVibration(true)
            }

            val adminChannel = NotificationChannel(
                CHANNEL_ADMIN,
                "Уведомления администратора",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Новые заказы от членов семьи для оценки"
                enableVibration(true)
            }

            val paymentChannel = NotificationChannel(
                CHANNEL_PAYMENT,
                "Оплата в реальности",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Подтверждения физической оплаты наличными или переводом"
                enableVibration(true)
            }

            systemNotificationManager?.createNotificationChannels(
                listOf(ordersChannel, chatChannel, adminChannel, paymentChannel)
            )
        }
    }

    fun triggerNotification(
        title: String,
        message: String,
        channelId: String = CHANNEL_ORDERS,
        orderId: String? = null,
        type: String = "INFO"
    ) {
        // Emit in-app banner for active foreground experience
        _inAppAlerts.tryEmit(
            InAppAlert(
                id = System.currentTimeMillis().toString(),
                title = title,
                message = message,
                orderId = orderId,
                type = type
            )
        )

        // Trigger gentle haptic vibration
        vibratePhone()

        // Post system notification if permission granted
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                if (orderId != null) {
                    putExtra("orderId", orderId)
                }
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                (System.currentTimeMillis() % 10000).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setSound(soundUri)
                .setContentIntent(pendingIntent)

            try {
                notificationManager.notify((System.currentTimeMillis() % 100000).toInt(), builder.build())
            } catch (e: SecurityException) {
                // Handled gracefully if permission revoked
            }
        }
    }

    private fun vibratePhone() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(120)
            }
        } catch (e: Exception) {
            // Ignored if device lacks vibrator
        }
    }
}
