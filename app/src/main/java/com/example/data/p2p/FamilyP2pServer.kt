package com.example.data.p2p

import android.content.Context
import com.example.data.db.AppDatabase
import com.example.data.model.ChatMessageEntity
import com.example.data.model.NotificationEntity
import com.example.data.model.OrderEntity
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentStatus
import com.example.data.model.UserRole
import com.example.service.NotificationHelper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.nio.charset.StandardCharsets
import java.util.UUID

class FamilyP2pServer(
    private val context: Context,
    private val database: AppDatabase,
    private val notificationHelper: NotificationHelper,
    private val port: Int = 8080
) {
    private var server: HttpServer? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    var isRunning: Boolean = false
        private set

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            // fallback
        }
        return "127.0.0.1"
    }

    fun getWebUrl(): String = "http://${getLocalIpAddress()}:$port"

    fun start(): Boolean {
        if (isRunning) return true
        return try {
            server = HttpServer.create(InetSocketAddress(port), 0).apply {
                createContext("/", RootWebHandler())
                createContext("/api/status", ApiStatusHandler())
                createContext("/api/orders", ApiOrdersHandler())
                createContext("/api/quote", ApiQuoteHandler())
                createContext("/api/decide", ApiDecideHandler())
                createContext("/api/pay", ApiPayHandler())
                createContext("/api/chat", ApiChatHandler())
                createContext("/api/profiles", ApiProfilesHandler())
                executor = java.util.concurrent.Executors.newFixedThreadPool(4)
                start()
            }
            isRunning = true
            true
        } catch (e: Exception) {
            isRunning = false
            false
        }
    }

    fun stop() {
        try {
            server?.stop(0)
            server = null
            isRunning = false
        } catch (e: Exception) {
            // Ignored
        }
    }

    private inner class RootWebHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val html = generateWebDashboardHtml()
            val bytes = html.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "text/html; charset=UTF-8")
            exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private inner class ApiStatusHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val json = JSONObject().apply {
                put("status", "online")
                put("device", android.os.Build.MODEL)
                put("serverPort", port)
                put("time", System.currentTimeMillis())
            }.toString()
            sendJsonResponse(exchange, 200, json)
        }
    }

    private inner class ApiOrdersHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            if (exchange.requestMethod.equals("GET", ignoreCase = true)) {
                scope.launch {
                    val orders = database.orderDao().getAllOrders().first()
                    val array = JSONArray()
                    orders.forEach { ord ->
                        array.put(JSONObject().apply {
                            put("id", ord.id)
                            put("userId", ord.userId)
                            put("userName", ord.userName)
                            put("userAvatar", ord.userAvatar)
                            put("itemTitle", ord.itemTitle)
                            put("itemDescription", ord.itemDescription)
                            put("targetStore", ord.targetStore)
                            put("deliveryAddress", ord.deliveryAddress)
                            put("urgency", ord.urgency)
                            put("budgetEstimate", ord.budgetEstimate ?: JSONObject.NULL)
                            put("status", ord.status.name)
                            put("statusTitle", ord.status.title())
                            put("quotedPrice", ord.quotedPrice ?: JSONObject.NULL)
                            put("adminNote", ord.adminNote ?: "")
                            put("paymentStatus", ord.paymentStatus.name)
                            put("paymentMethod", ord.paymentMethod ?: "")
                            put("createdAt", ord.createdAt)
                        })
                    }
                    sendJsonResponse(exchange, 200, array.toString())
                }
            } else if (exchange.requestMethod.equals("POST", ignoreCase = true)) {
                val body = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).readText()
                try {
                    val json = JSONObject(body)
                    val title = json.getString("itemTitle")
                    val desc = json.optString("itemDescription", "")
                    val store = json.optString("targetStore", "")
                    val address = json.optString("deliveryAddress", "Дом")
                    val urgency = json.optString("urgency", "Обычная")
                    val budget = if (json.has("budgetEstimate")) json.optDouble("budgetEstimate") else null
                    val senderName = json.optString("userName", "Член семьи (с ПК)")

                    val newOrder = OrderEntity(
                        id = UUID.randomUUID().toString(),
                        userId = "web_user",
                        userName = senderName,
                        userAvatar = "💻",
                        itemTitle = title,
                        itemDescription = desc,
                        targetStore = store,
                        deliveryAddress = address,
                        urgency = urgency,
                        budgetEstimate = budget,
                        status = OrderStatus.PENDING_REVIEW,
                        paymentStatus = PaymentStatus.UNPAID
                    )

                    scope.launch {
                        database.orderDao().insertOrder(newOrder)
                        // Trigger alert for admin
                        notificationHelper.triggerNotification(
                            title = "🔔 Новый заказ с ПК от $senderName",
                            message = "Хочет заказать: '$title'. Назначьте цену в админке!",
                            channelId = NotificationHelper.CHANNEL_ADMIN,
                            orderId = newOrder.id,
                            type = "ORDER_NEW"
                        )
                        database.notificationDao().insertNotification(
                            NotificationEntity(
                                targetUserId = "ADMIN",
                                title = "Новый заказ с ПК: $title",
                                message = "$senderName ожидает оценки заказа '$title'",
                                type = "ORDER_NEW",
                                orderId = newOrder.id
                            )
                        )
                    }
                    sendJsonResponse(exchange, 200, JSONObject().put("success", true).put("orderId", newOrder.id).toString())
                } catch (e: Exception) {
                    sendJsonResponse(exchange, 400, JSONObject().put("error", e.message).toString())
                }
            }
        }
    }

    private inner class ApiQuoteHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val body = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).readText()
            try {
                val json = JSONObject(body)
                val orderId = json.getString("orderId")
                val price = json.getDouble("price")
                val note = json.optString("note", "")

                scope.launch {
                    val existing = database.orderDao().getOrderByIdSync(orderId)
                    if (existing != null) {
                        val updated = existing.copy(
                            status = OrderStatus.PRICE_QUOTED,
                            quotedPrice = price,
                            adminNote = note,
                            updatedAt = System.currentTimeMillis()
                        )
                        database.orderDao().updateOrder(updated)

                        // Notification to buyer
                        notificationHelper.triggerNotification(
                            title = "💰 Цена назначена: ${price.toInt()} ₽",
                            message = "По заказу '${existing.itemTitle}'. Подтвердите или отклоните покупку!",
                            channelId = NotificationHelper.CHANNEL_ORDERS,
                            orderId = existing.id,
                            type = "PRICE_QUOTED"
                        )

                        database.notificationDao().insertNotification(
                            NotificationEntity(
                                targetUserId = existing.userId,
                                title = "Цена назначена: ${price.toInt()} ₽",
                                message = "Заказ '${existing.itemTitle}' оценен в ${price.toInt()} ₽",
                                type = "PRICE_QUOTED",
                                orderId = existing.id
                            )
                        )
                        sendJsonResponse(exchange, 200, JSONObject().put("success", true).toString())
                    } else {
                        sendJsonResponse(exchange, 404, JSONObject().put("error", "Order not found").toString())
                    }
                }
            } catch (e: Exception) {
                sendJsonResponse(exchange, 400, JSONObject().put("error", e.message).toString())
            }
        }
    }

    private inner class ApiDecideHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val body = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).readText()
            try {
                val json = JSONObject(body)
                val orderId = json.getString("orderId")
                val accepted = json.getBoolean("accepted")

                scope.launch {
                    val existing = database.orderDao().getOrderByIdSync(orderId)
                    if (existing != null) {
                        val newStatus = if (accepted) OrderStatus.ACCEPTED else OrderStatus.REJECTED
                        val updated = existing.copy(
                            status = newStatus,
                            updatedAt = System.currentTimeMillis()
                        )
                        database.orderDao().updateOrder(updated)

                        val title = if (accepted) "✅ Заказ принят к покупке!" else "❌ Покупатель отклонил цену"
                        val msg = if (accepted) {
                            "${existing.userName} согласился на цену ${existing.quotedPrice?.toInt() ?: 0} ₽ за '${existing.itemTitle}'"
                        } else {
                            "${existing.userName} отказался от покупки '${existing.itemTitle}'"
                        }

                        notificationHelper.triggerNotification(
                            title = title,
                            message = msg,
                            channelId = NotificationHelper.CHANNEL_ADMIN,
                            orderId = existing.id,
                            type = if (accepted) "ORDER_ACCEPTED" else "ORDER_REJECTED"
                        )

                        sendJsonResponse(exchange, 200, JSONObject().put("success", true).toString())
                    } else {
                        sendJsonResponse(exchange, 404, JSONObject().put("error", "Order not found").toString())
                    }
                }
            } catch (e: Exception) {
                sendJsonResponse(exchange, 400, JSONObject().put("error", e.message).toString())
            }
        }
    }

    private inner class ApiPayHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val body = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).readText()
            try {
                val json = JSONObject(body)
                val orderId = json.getString("orderId")
                val method = json.optString("method", "Наличными при передаче")

                scope.launch {
                    val existing = database.orderDao().getOrderByIdSync(orderId)
                    if (existing != null) {
                        val updated = existing.copy(
                            paymentStatus = PaymentStatus.PAID_CONFIRMED,
                            paymentMethod = method,
                            updatedAt = System.currentTimeMillis()
                        )
                        database.orderDao().updateOrder(updated)

                        notificationHelper.triggerNotification(
                            title = "💵 Оплата подтверждена в реальности!",
                            message = "Оплата за '${existing.itemTitle}' (${existing.quotedPrice?.toInt() ?: 0} ₽) подтверждена ($method).",
                            channelId = NotificationHelper.CHANNEL_PAYMENT,
                            orderId = existing.id,
                            type = "PAYMENT_CONFIRMED"
                        )

                        sendJsonResponse(exchange, 200, JSONObject().put("success", true).toString())
                    } else {
                        sendJsonResponse(exchange, 404, JSONObject().put("error", "Order not found").toString())
                    }
                }
            } catch (e: Exception) {
                sendJsonResponse(exchange, 400, JSONObject().put("error", e.message).toString())
            }
        }
    }

    private inner class ApiChatHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            if (exchange.requestMethod.equals("GET", ignoreCase = true)) {
                val query = exchange.requestURI.query ?: ""
                val orderIdParam = query.split("&").find { it.startsWith("orderId=") }?.substringAfter("orderId=") ?: "general"
                scope.launch {
                    val messages = database.chatMessageDao().getMessagesForOrder(orderIdParam).first()
                    val array = JSONArray()
                    messages.forEach { msg ->
                        array.put(JSONObject().apply {
                            put("id", msg.id)
                            put("orderId", msg.orderId)
                            put("senderId", msg.senderId)
                            put("senderName", msg.senderName)
                            put("senderRole", msg.senderRole.name)
                            put("senderAvatar", msg.senderAvatar)
                            put("text", msg.text)
                            put("timestamp", msg.timestamp)
                        })
                    }
                    sendJsonResponse(exchange, 200, array.toString())
                }
            } else if (exchange.requestMethod.equals("POST", ignoreCase = true)) {
                val body = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).readText()
                try {
                    val json = JSONObject(body)
                    val orderId = json.optString("orderId", "general")
                    val senderName = json.optString("senderName", "Компьютер (ПК)")
                    val text = json.getString("text")

                    val chatMsg = ChatMessageEntity(
                        id = UUID.randomUUID().toString(),
                        orderId = orderId,
                        senderId = "pc_client",
                        senderName = senderName,
                        senderRole = UserRole.MEMBER,
                        senderAvatar = "💻",
                        text = text,
                        timestamp = System.currentTimeMillis()
                    )

                    scope.launch {
                        database.chatMessageDao().insertMessage(chatMsg)
                        notificationHelper.triggerNotification(
                            title = "💬 Сообщение с ПК ($senderName)",
                            message = text,
                            channelId = NotificationHelper.CHANNEL_CHAT,
                            orderId = orderId,
                            type = "CHAT_MESSAGE"
                        )
                    }
                    sendJsonResponse(exchange, 200, JSONObject().put("success", true).toString())
                } catch (e: Exception) {
                    sendJsonResponse(exchange, 400, JSONObject().put("error", e.message).toString())
                }
            }
        }
    }

    private inner class ApiProfilesHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            scope.launch {
                val profiles = database.userProfileDao().getAllProfiles().first()
                val array = JSONArray()
                profiles.forEach { p ->
                    array.put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("role", p.role.name)
                        put("avatarEmoji", p.avatarEmoji)
                        put("savedAddresses", JSONArray(p.savedAddresses))
                        put("preferences", p.preferences)
                        put("isCurrent", p.isCurrent)
                    })
                }
                sendJsonResponse(exchange, 200, array.toString())
            }
        }
    }

    private fun sendJsonResponse(exchange: HttpExchange, code: Int, json: String) {
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json; charset=UTF-8")
        exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
        exchange.responseHeaders.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        exchange.responseHeaders.set("Access-Control-Allow-Headers", "Content-Type")
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun generateWebDashboardHtml(): String {
        return """
<!DOCTYPE html>
<html lang="ru">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Семейные Заказы - Компьютерный веб-хаб</title>
  <style>
    :root {
      --primary: #4f46e5;
      --primary-hover: #4338ca;
      --bg: #0f172a;
      --card-bg: #1e293b;
      --card-border: #334155;
      --text: #f8fafc;
      --text-muted: #94a3b8;
      --accent-green: #10b981;
      --accent-red: #ef4444;
      --accent-amber: #f59e0b;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
    body { background: var(--bg); color: var(--text); padding: 24px; }
    .container { max-width: 1100px; margin: 0 auto; }
    header { display: flex; justify-content: space-between; align-items: center; padding-bottom: 20px; border-bottom: 1px solid var(--card-border); margin-bottom: 24px; }
    .logo { display: flex; align-items: center; gap: 12px; font-size: 22px; font-weight: 700; }
    .badge-online { background: #064e3b; color: #6ee7b7; padding: 4px 12px; border-radius: 9999px; font-size: 13px; font-weight: 600; display: flex; align-items: center; gap: 6px; }
    .dot { width: 8px; height: 8px; border-radius: 50%; background: #10b981; }
    
    .grid { display: grid; grid-template-columns: 2fr 1fr; gap: 24px; }
    @media (max-width: 800px) { .grid { grid-template-columns: 1fr; } }

    .card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 16px; padding: 20px; margin-bottom: 20px; }
    h2 { font-size: 18px; font-weight: 600; margin-bottom: 16px; display: flex; align-items: center; gap: 8px; }
    
    .order-card { background: #0f172a; border: 1px solid var(--card-border); border-radius: 12px; padding: 16px; margin-bottom: 12px; }
    .order-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
    .order-user { display: flex; align-items: center; gap: 8px; font-weight: 600; }
    .status-badge { font-size: 12px; padding: 3px 10px; border-radius: 12px; font-weight: 600; }
    .status-PENDING_REVIEW { background: #451a03; color: #fcd34d; }
    .status-PRICE_QUOTED { background: #172554; color: #93c5fd; }
    .status-ACCEPTED { background: #064e3b; color: #6ee7b7; }
    .status-REJECTED { background: #450a0a; color: #fca5a5; }
    .status-COMPLETED { background: #2e1065; color: #d8b4fe; }
    
    .order-title { font-size: 16px; font-weight: 700; margin-bottom: 4px; }
    .order-meta { font-size: 13px; color: var(--text-muted); margin-bottom: 12px; }
    .price-tag { font-size: 18px; font-weight: 700; color: #38bdf8; margin: 8px 0; }

    .actions { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 10px; }
    button { background: var(--primary); color: white; border: none; padding: 8px 16px; border-radius: 8px; font-weight: 600; cursor: pointer; transition: 0.2s; }
    button:hover { background: var(--primary-hover); }
    button.success { background: var(--accent-green); }
    button.danger { background: var(--accent-red); }
    button.secondary { background: #334155; }

    input, textarea, select { width: 100%; background: #0f172a; border: 1px solid var(--card-border); color: white; padding: 10px; border-radius: 8px; margin-bottom: 12px; }
    input:focus, textarea:focus { outline: 1px solid var(--primary); }

    .chat-box { height: 260px; overflow-y: auto; display: flex; flex-direction: column; gap: 8px; padding: 8px; background: #0f172a; border-radius: 8px; margin-bottom: 12px; }
    .chat-bubble { padding: 8px 12px; border-radius: 10px; max-width: 85%; font-size: 14px; }
    .chat-bubble.mine { align-self: flex-end; background: var(--primary); color: white; }
    .chat-bubble.other { align-self: flex-start; background: #334155; color: white; }
    .chat-sender { font-size: 11px; opacity: 0.7; margin-bottom: 2px; }
  </style>
</head>
<body>
  <div class="container">
    <header>
      <div class="logo">
        <span>🛒</span>
        <span>Семейные Заказы (Family P2P Hub)</span>
      </div>
      <div class="badge-online">
        <span class="dot"></span>
        <span>P2P Сервер активен</span>
      </div>
    </header>

    <div class="grid">
      <!-- Left Column: Orders & Admin Flow -->
      <div>
        <div class="card">
          <h2>📦 Список семейных заказов</h2>
          <div id="ordersContainer">Загрузка заказов...</div>
        </div>

        <div class="card">
          <h2>➕ Сделать новый заказ с компьютера</h2>
          <form id="newOrderForm">
            <input type="text" id="orderTitle" placeholder="Что нужно купить? (например: Пицца, Хлеб, Новые наушники)" required>
            <textarea id="orderDesc" rows="2" placeholder="Уточнения, ссылка, предпочтения (например: без лука, из магазина Магнит)"></textarea>
            <div style="display:grid; grid-template-columns: 1fr 1fr; gap:12px;">
              <input type="text" id="orderStore" placeholder="Магазин (ВкусВилл, Озон...)">
              <input type="number" id="orderBudget" placeholder="Примерный бюджет (₽)">
            </div>
            <button type="submit" style="width: 100%;">🚀 Отправить заказ в семью</button>
          </form>
        </div>
      </div>

      <!-- Right Column: Family Chat & Live Feed -->
      <div>
        <div class="card">
          <h2>💬 Семейный чат по заказам</h2>
          <div class="chat-box" id="chatMessages">
            <!-- Messages load here -->
          </div>
          <form id="chatForm" style="display:flex; gap:8px;">
            <input type="text" id="chatInput" placeholder="Написать семье..." style="margin-bottom:0;" required>
            <button type="submit" style="flex-shrink:0;">💬</button>
          </form>
        </div>

        <div class="card" style="background:#1e1b4b; border-color:#4338ca;">
          <h2 style="color:#c7d2fe;">📱 Как это работает?</h2>
          <p style="font-size:13px; color:#e0e7ff; line-height:1.5;">
            1. Член семьи пишет заказ.<br>
            2. Админ (родитель) получает оповещение и указывает реальную цену.<br>
            3. Заказчик смотрит цену и принимает или отклоняет.<br>
            4. Оплата подтверждается в реальности (наличными или переводом).
          </p>
        </div>
      </div>
    </div>
  </div>

  <script>
    async function fetchOrders() {
      try {
        const res = await fetch('/api/orders');
        const data = await res.json();
        const container = document.getElementById('ordersContainer');
        if (data.length === 0) {
          container.innerHTML = '<p style="color:var(--text-muted);">Пока нет активных заказов.</p>';
          return;
        }
        container.innerHTML = data.map(o => `
          <div class="order-card">
            <div class="order-header">
              <div class="order-user">
                <span>` + o.userAvatar + `</span>
                <span>` + o.userName + `</span>
              </div>
              <span class="status-badge status-` + o.status + `">` + o.statusTitle + `</span>
            </div>
            <div class="order-title">` + o.itemTitle + `</div>
            <div class="order-meta">
              ` + (o.itemDescription ? '📝 ' + o.itemDescription + '<br>' : '') + `
              ` + (o.targetStore ? '🏬 Магазин: ' + o.targetStore + ' | ' : '') + `
              📍 Доставка: ` + o.deliveryAddress + `
            </div>

            ` + (o.quotedPrice ? `
              <div class="price-tag">💰 Назначенная цена: ` + Math.round(o.quotedPrice) + ` ₽</div>
              ` + (o.adminNote ? '<div style="font-size:13px; color:#93c5fd; margin-bottom:8px;">💬 Заметка админа: ' + o.adminNote + '</div>' : '') + `
            ` : '') + `

            <div class="actions">
              ` + (o.status === 'PENDING_REVIEW' ? `
                <input type="number" id="quote_` + o.id + `" placeholder="Цена ₽" style="width:100px; margin-bottom:0;">
                <button onclick="quotePrice('` + o.id + `')">Оценить (Админ)</button>
              ` : '') + `

              ` + (o.status === 'PRICE_QUOTED' ? `
                <button class="success" onclick="decideOrder('` + o.id + `', true)">✅ Купить за ` + (o.quotedPrice || 0) + ` ₽</button>
                <button class="danger" onclick="decideOrder('` + o.id + `', false)">❌ Отклонить</button>
              ` : '') + `

              ` + (o.status === 'ACCEPTED' ? `
                <button class="secondary" onclick="confirmPayment('` + o.id + `')">💵 Подтвердить оплату в реальности</button>
              ` : '') + `
            </div>
          </div>
        `).join('');
      } catch (err) {
        console.error(err);
      }
    }

    async function quotePrice(orderId) {
      const price = document.getElementById('quote_' + orderId).value;
      if (!price) return alert('Введите цену!');
      await fetch('/api/quote', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ orderId, price: parseFloat(price), note: 'Оценено с компьютера' })
      });
      fetchOrders();
    }

    async function decideOrder(orderId, accepted) {
      await fetch('/api/decide', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ orderId, accepted })
      });
      fetchOrders();
    }

    async function confirmPayment(orderId) {
      await fetch('/api/pay', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ orderId, method: 'Наличными при встрече' })
      });
      fetchOrders();
    }

    document.getElementById('newOrderForm').onsubmit = async (e) => {
      e.preventDefault();
      const title = document.getElementById('orderTitle').value;
      const desc = document.getElementById('orderDesc').value;
      const store = document.getElementById('orderStore').value;
      const budget = parseFloat(document.getElementById('orderBudget').value) || null;

      await fetch('/api/orders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ itemTitle: title, itemDescription: desc, targetStore: store, budgetEstimate: budget })
      });

      document.getElementById('newOrderForm').reset();
      fetchOrders();
    };

    async function fetchChat() {
      try {
        const res = await fetch('/api/chat');
        const msgs = await res.json();
        const box = document.getElementById('chatMessages');
        box.innerHTML = msgs.map(m => `
          <div class="chat-bubble ` + (m.senderId === 'pc_client' ? 'mine' : 'other') + `">
            <div class="chat-sender">` + m.senderAvatar + ` ` + m.senderName + `</div>
            <div>` + m.text + `</div>
          </div>
        `).join('');
        box.scrollTop = box.scrollHeight;
      } catch (err) {}
    }

    document.getElementById('chatForm').onsubmit = async (e) => {
      e.preventDefault();
      const input = document.getElementById('chatInput');
      const text = input.value;
      if (!text) return;
      await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text })
      });
      input.value = '';
      fetchChat();
    };

    fetchOrders();
    fetchChat();
    setInterval(fetchOrders, 3000);
    setInterval(fetchChat, 2000);
  </script>
</body>
</html>
        """.trimIndent()
    }
}
