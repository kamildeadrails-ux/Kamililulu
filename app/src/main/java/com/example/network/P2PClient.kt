package com.example.network

import com.example.model.ChatMessage
import com.example.model.Order
import com.example.model.OrderStatus
import com.example.model.ProfileRole
import com.example.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class P2PClient(
    private var hubHost: String = "192.168.1.100",
    private var hubPort: Int = 8888
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun setHubAddress(host: String, port: Int) {
        this.hubHost = host.trim()
        this.hubPort = port
    }

    fun getHubUrl(): String = "http://$hubHost:$hubPort"

    suspend fun syncWithHub(): Result<Triple<List<Order>, List<UserProfile>, List<ChatMessage>>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${getHubUrl()}/api/sync")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Код ответа: ${response.code}"))
                }
                val bodyStr = response.body?.string() ?: return@withContext Result.failure(Exception("Пустой ответ"))
                val root = JSONObject(bodyStr)

                val orders = mutableListOf<Order>()
                val ordersArr = root.optJSONArray("orders") ?: JSONArray()
                for (i in 0 until ordersArr.length()) {
                    val o = ordersArr.getJSONObject(i)
                    val status = try { OrderStatus.valueOf(o.getString("status")) } catch (e: Exception) { OrderStatus.PENDING_REVIEW }
                    orders.add(
                        Order(
                            id = o.getString("id"),
                            userId = o.optString("userId", "remote"),
                            userName = o.optString("userName", "Пользователь"),
                            userAvatarEmoji = o.optString("userAvatarEmoji", "👤"),
                            title = o.getString("title"),
                            description = o.optString("description", ""),
                            targetStoreOrPlace = o.optString("targetStoreOrPlace", ""),
                            deliveryAddress = o.optString("deliveryAddress", "Дом"),
                            urgency = o.optString("urgency", "Обычная"),
                            status = status,
                            proposedPrice = if (o.isNull("proposedPrice")) null else o.optDouble("proposedPrice"),
                            adminNote = o.optString("adminNote", null),
                            isPaidInReality = o.optBoolean("isPaidInReality", false),
                            paymentMethodNote = o.optString("paymentMethodNote", "Наличные/перевод"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }

                val profiles = mutableListOf<UserProfile>()
                val profArr = root.optJSONArray("profiles") ?: JSONArray()
                for (i in 0 until profArr.length()) {
                    val p = profArr.getJSONObject(i)
                    val addrs = mutableListOf<String>()
                    val addrsArr = p.optJSONArray("savedAddresses") ?: JSONArray()
                    for (j in 0 until addrsArr.length()) {
                        addrs.add(addrsArr.getString(j))
                    }
                    val role = try { ProfileRole.valueOf(p.getString("role")) } catch (e: Exception) { ProfileRole.BUYER }
                    profiles.add(
                        UserProfile(
                            id = p.getString("id"),
                            name = p.getString("name"),
                            role = role,
                            avatarEmoji = p.optString("avatarEmoji", "👤"),
                            colorHex = p.optLong("colorHex", 0xFF2563EB),
                            savedAddresses = addrs,
                            preferences = p.optString("preferences", ""),
                            phone = p.optString("phone", "")
                        )
                    )
                }

                val messages = mutableListOf<ChatMessage>()
                val msgsArr = root.optJSONArray("messages") ?: JSONArray()
                for (i in 0 until msgsArr.length()) {
                    val m = msgsArr.getJSONObject(i)
                    val role = try { ProfileRole.valueOf(m.getString("senderRole")) } catch (e: Exception) { ProfileRole.BUYER }
                    messages.add(
                        ChatMessage(
                            id = m.getString("id"),
                            orderId = m.getString("orderId"),
                            senderId = m.getString("senderId"),
                            senderName = m.getString("senderName"),
                            senderRole = role,
                            text = m.getString("text"),
                            timestamp = m.optLong("timestamp", System.currentTimeMillis()),
                            isSystemEvent = m.optBoolean("isSystemEvent", false)
                        )
                    )
                }

                Result.success(Triple(orders, profiles, messages))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendOrder(order: Order): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("id", order.id)
                put("userId", order.userId)
                put("userName", order.userName)
                put("userAvatarEmoji", order.userAvatarEmoji)
                put("title", order.title)
                put("description", order.description)
                put("targetStoreOrPlace", order.targetStoreOrPlace)
                put("deliveryAddress", order.deliveryAddress)
                put("urgency", order.urgency)
            }
            val request = Request.Builder()
                .url("${getHubUrl()}/api/order/create")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun proposePrice(orderId: String, price: Double, note: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("orderId", orderId)
                put("price", price)
                put("note", note)
            }
            val request = Request.Builder()
                .url("${getHubUrl()}/api/order/price")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendDecision(orderId: String, accept: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("orderId", orderId)
                put("accept", accept)
            }
            val request = Request.Builder()
                .url("${getHubUrl()}/api/order/decision")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendChatMessage(msg: ChatMessage): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("orderId", msg.orderId)
                put("senderId", msg.senderId)
                put("senderName", msg.senderName)
                put("senderRole", msg.senderRole.name)
                put("text", msg.text)
            }
            val request = Request.Builder()
                .url("${getHubUrl()}/api/chat")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
