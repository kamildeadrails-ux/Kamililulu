package com.example.network

import com.example.model.ChatMessage
import com.example.model.Order
import com.example.model.OrderStatus
import com.example.model.ProfileRole
import com.example.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.UUID

class P2PServer(
    private val port: Int = 8888,
    private val scope: CoroutineScope,
    private val stateProvider: () -> Triple<List<Order>, List<UserProfile>, List<ChatMessage>>,
    private val onOrderCreated: (Order) -> Unit,
    private val onPriceProposed: (String, Double, String) -> Unit,
    private val onDecisionMade: (String, Boolean) -> Unit,
    private val onStatusChanged: (String, OrderStatus, Boolean) -> Unit,
    private val onChatMessageSent: (ChatMessage) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    var isRunning: Boolean = false
        private set

    fun start(onStarted: (Boolean, String) -> Unit) {
        if (isRunning) {
            onStarted(true, "Сервер уже запущен на порту $port")
            return
        }

        try {
            serverSocket = ServerSocket(port)
            isRunning = true
            val ip = NetworkUtils.getLocalIpAddress()

            serverJob = scope.launch(Dispatchers.IO) {
                while (isActive && isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        launch(Dispatchers.IO) {
                            handleClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            }
            onStarted(true, "Сервер запущен на http://$ip:$port")
        } catch (e: Exception) {
            isRunning = false
            onStarted(false, "Не удалось запустить сервер: ${e.localizedMessage}")
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        serverJob?.cancel()
        serverSocket = null
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val output: OutputStream = socket.getOutputStream()

            val requestLine = reader.readLine() ?: return@withContext
            val parts = requestLine.split(" ")
            if (parts.size < 2) return@withContext

            val method = parts[0]
            val path = parts[1]

            // Read headers to determine Content-Length
            var contentLength = 0
            var line: String?
            while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                if (line!!.lowercase().startsWith("content-length:")) {
                    contentLength = line!!.substring(15).trim().toIntOrNull() ?: 0
                }
            }

            // Read body if POST
            val bodyBuilder = StringBuilder()
            if (method == "POST" && contentLength > 0) {
                val buffer = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val count = reader.read(buffer, read, contentLength - read)
                    if (count == -1) break
                    read += count
                }
                bodyBuilder.append(buffer, 0, read)
            }
            val body = bodyBuilder.toString()

            // Routing
            when {
                path == "/" || path == "/index.html" -> {
                    val html = generateWebDashboardHtml()
                    sendResponse(output, 200, "text/html; charset=utf-8", html)
                }
                path == "/api/sync" && method == "GET" -> {
                    val (orders, profiles, messages) = stateProvider()
                    val json = JSONObject().apply {
                        put("status", "ok")
                        val ordersArr = JSONArray()
                        orders.forEach { order ->
                            ordersArr.put(JSONObject().apply {
                                put("id", order.id)
                                put("userId", order.userId)
                                put("userName", order.userName)
                                put("userAvatarEmoji", order.userAvatarEmoji)
                                put("title", order.title)
                                put("description", order.description)
                                put("targetStoreOrPlace", order.targetStoreOrPlace)
                                put("deliveryAddress", order.deliveryAddress)
                                put("urgency", order.urgency)
                                put("status", order.status.name)
                                put("proposedPrice", order.proposedPrice ?: JSONObject.NULL)
                                put("adminNote", order.adminNote ?: "")
                                put("isPaidInReality", order.isPaidInReality)
                                put("paymentMethodNote", order.paymentMethodNote)
                                put("createdAt", order.createdAt)
                                put("updatedAt", order.updatedAt)
                            })
                        }
                        put("orders", ordersArr)

                        val profilesArr = JSONArray()
                        profiles.forEach { p ->
                            profilesArr.put(JSONObject().apply {
                                put("id", p.id)
                                put("name", p.name)
                                put("role", p.role.name)
                                put("avatarEmoji", p.avatarEmoji)
                                put("colorHex", p.colorHex)
                                val addrs = JSONArray()
                                p.savedAddresses.forEach { addrs.put(it) }
                                put("savedAddresses", addrs)
                                put("preferences", p.preferences)
                            })
                        }
                        put("profiles", profilesArr)

                        val msgsArr = JSONArray()
                        messages.forEach { m ->
                            msgsArr.put(JSONObject().apply {
                                put("id", m.id)
                                put("orderId", m.orderId)
                                put("senderId", m.senderId)
                                put("senderName", m.senderName)
                                put("senderRole", m.senderRole.name)
                                put("text", m.text)
                                put("timestamp", m.timestamp)
                                put("isSystemEvent", m.isSystemEvent)
                            })
                        }
                        put("messages", msgsArr)
                    }
                    sendResponse(output, 200, "application/json; charset=utf-8", json.toString())
                }
                path == "/api/order/create" && method == "POST" -> {
                    val obj = JSONObject(body)
                    val newOrder = Order(
                        id = obj.optString("id", "ORD-" + UUID.randomUUID().toString().take(6).uppercase()),
                        userId = obj.optString("userId", "guest"),
                        userName = obj.optString("userName", "Член семьи"),
                        userAvatarEmoji = obj.optString("userAvatarEmoji", "🛍️"),
                        title = obj.getString("title"),
                        description = obj.optString("description", ""),
                        targetStoreOrPlace = obj.optString("targetStoreOrPlace", ""),
                        deliveryAddress = obj.optString("deliveryAddress", "Дом"),
                        urgency = obj.optString("urgency", "Обычная"),
                        status = OrderStatus.PENDING_REVIEW,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    onOrderCreated(newOrder)
                    sendResponse(output, 200, "application/json", "{\"status\":\"created\",\"id\":\"${newOrder.id}\"}")
                }
                path == "/api/order/price" && method == "POST" -> {
                    val obj = JSONObject(body)
                    val orderId = obj.getString("orderId")
                    val price = obj.getDouble("price")
                    val note = obj.optString("note", "")
                    onPriceProposed(orderId, price, note)
                    sendResponse(output, 200, "application/json", "{\"status\":\"price_proposed\"}")
                }
                path == "/api/order/decision" && method == "POST" -> {
                    val obj = JSONObject(body)
                    val orderId = obj.getString("orderId")
                    val accept = obj.getBoolean("accept")
                    onDecisionMade(orderId, accept)
                    sendResponse(output, 200, "application/json", "{\"status\":\"decision_recorded\"}")
                }
                path == "/api/order/status" && method == "POST" -> {
                    val obj = JSONObject(body)
                    val orderId = obj.getString("orderId")
                    val statusStr = obj.getString("status")
                    val isPaid = obj.optBoolean("isPaidInReality", false)
                    val status = try { OrderStatus.valueOf(statusStr) } catch (e: Exception) { OrderStatus.PURCHASING }
                    onStatusChanged(orderId, status, isPaid)
                    sendResponse(output, 200, "application/json", "{\"status\":\"updated\"}")
                }
                path == "/api/chat" && method == "POST" -> {
                    val obj = JSONObject(body)
                    val msg = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        orderId = obj.getString("orderId"),
                        senderId = obj.optString("senderId", "pc_admin"),
                        senderName = obj.optString("senderName", "Компьютер / Админ"),
                        senderRole = try {
                            ProfileRole.valueOf(obj.optString("senderRole", "ADMIN"))
                        } catch (e: Exception) {
                            ProfileRole.ADMIN
                        },
                        text = obj.getString("text"),
                        timestamp = System.currentTimeMillis()
                    )
                    onChatMessageSent(msg)
                    sendResponse(output, 200, "application/json", "{\"status\":\"sent\"}")
                }
                else -> {
                    sendResponse(output, 404, "text/plain", "Not Found")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun sendResponse(output: OutputStream, statusCode: Int, contentType: String, content: String) {
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 $statusCode OK\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun generateWebDashboardHtml(): String {
        return """
        <!DOCTYPE html>
        <html lang="ru">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Семейные заказы — Панель управления (ПК)</title>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0f172a; color: #f8fafc; padding: 24px; }
                .container { max-width: 1100px; margin: 0 auto; }
                header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #334155; padding-bottom: 20px; margin-bottom: 24px; }
                h1 { font-size: 26px; color: #38bdf8; display: flex; align-items: center; gap: 10px; }
                .badge { background: #0284c7; padding: 6px 14px; border-radius: 9999px; font-size: 13px; font-weight: 600; }
                .grid { display: grid; grid-template-columns: 2fr 1fr; gap: 24px; }
                @media(max-width: 800px) { .grid { grid-template-columns: 1fr; } }
                .card { background: #1e293b; border: 1px solid #334155; border-radius: 16px; padding: 20px; margin-bottom: 16px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.2); }
                .order-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
                .order-title { font-size: 18px; font-weight: 700; color: #f1f5f9; }
                .order-meta { font-size: 13px; color: #94a3b8; margin-top: 4px; }
                .status-pill { display: inline-block; padding: 4px 10px; border-radius: 8px; font-size: 12px; font-weight: 600; text-transform: uppercase; }
                .status-pending { background: #ca8a04; color: #fef08a; }
                .status-proposed { background: #2563eb; color: #dbeafe; }
                .status-accepted { background: #059669; color: #a7f3d0; }
                .status-declined { background: #dc2626; color: #fee2e2; }
                .status-paid { background: #15803d; color: #bbf7d0; }
                .price-box { background: #0f172a; border-left: 4px solid #38bdf8; padding: 12px; border-radius: 6px; margin: 12px 0; font-size: 15px; }
                .actions { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 14px; }
                button { background: #2563eb; color: white; border: none; padding: 8px 16px; border-radius: 8px; cursor: pointer; font-size: 14px; font-weight: 600; transition: 0.2s; }
                button:hover { background: #1d4ed8; }
                button.success { background: #059669; }
                button.success:hover { background: #047857; }
                button.danger { background: #dc2626; }
                button.secondary { background: #475569; }
                input, select, textarea { width: 100%; background: #0f172a; border: 1px solid #475569; color: white; padding: 10px; border-radius: 8px; margin-bottom: 12px; font-size: 14px; }
                .chat-box { height: 320px; overflow-y: auto; background: #0f172a; border: 1px solid #334155; border-radius: 10px; padding: 12px; margin-bottom: 12px; }
                .chat-bubble { margin-bottom: 10px; padding: 8px 12px; border-radius: 10px; max-width: 85%; font-size: 13px; line-height: 1.4; }
                .chat-bubble.admin { background: #1e3a8a; margin-left: auto; color: #dbeafe; }
                .chat-bubble.buyer { background: #334155; margin-right: auto; color: #f8fafc; }
                .chat-bubble.system { background: #374151; font-style: italic; text-align: center; margin: 8px auto; max-width: 95%; font-size: 11px; color: #94a3b8; }
            </style>
        </head>
        <body>
            <div class="container">
                <header>
                    <div>
                        <h1>🏠 Семейные заказы & P2P Хаб</h1>
                        <p style="color: #94a3b8; margin-top: 4px;">Прямая связь ПК ↔ Android в домашней сети</p>
                    </div>
                    <div>
                        <span class="badge" id="hub-status">● Сервер активен</span>
                        <button onclick="loadData()" class="secondary" style="margin-left: 8px;">🔄 Обновить</button>
                    </div>
                </header>

                <div class="grid">
                    <div>
                        <h2 style="font-size: 20px; margin-bottom: 16px; color: #cbd5e1;">📋 Все семейные заказы</h2>
                        <div id="orders-list">Загрузка...</div>
                    </div>

                    <div>
                        <div class="card">
                            <h3 style="margin-bottom: 12px;">➕ Создать быстрый заказ</h3>
                            <input id="new-title" placeholder="Что нужно купить? (напр. Сыр и Хлеб)">
                            <input id="new-store" placeholder="Где купить? (Магнит, ВкусВилл, Аптека)">
                            <textarea id="new-desc" rows="2" placeholder="Комментарий или пожелания"></textarea>
                            <input id="new-address" placeholder="Куда доставить (Комната / Адрес)" value="Дом (Кухня)">
                            <button onclick="createOrder()" class="success" style="width: 100%;">Отправить админу</button>
                        </div>

                        <div class="card">
                            <h3 style="margin-bottom: 12px;">💬 Чат с семьей</h3>
                            <select id="chat-order-select" onchange="renderChat()">
                                <option value="FAMILY_GENERAL">Общий семейный чат</option>
                            </select>
                            <div class="chat-box" id="chat-messages"></div>
                            <div style="display: flex; gap: 8px;">
                                <input id="chat-input" placeholder="Напишите сообщение..." style="margin-bottom: 0;">
                                <button onclick="sendChatMessage()">Отправить</button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <script>
                let currentOrders = [];
                let currentMessages = [];

                async function loadData() {
                    try {
                        const res = await fetch('/api/sync');
                        const data = await res.json();
                        currentOrders = data.orders || [];
                        currentMessages = data.messages || [];
                        renderOrders();
                        updateChatSelect();
                        renderChat();
                    } catch (e) {
                        console.error(e);
                    }
                }

                function getStatusBadge(status, price, isPaid) {
                    if (isPaid) return '<span class="status-pill status-paid">Оплачено в реальности ✓</span>';
                    switch(status) {
                        case 'PENDING_REVIEW': return '<span class="status-pill status-pending">Ждёт оценки цены</span>';
                        case 'PRICE_PROPOSED': return '<span class="status-pill status-proposed">Оценка: ' + price + ' ₽</span>';
                        case 'ACCEPTED': return '<span class="status-pill status-accepted">Покупатель согласен</span>';
                        case 'DECLINED': return '<span class="status-pill status-declined">Отклонено</span>';
                        case 'PURCHASING': return '<span class="status-pill status-proposed">В закупке</span>';
                        case 'DELIVERED_UNPAID': return '<span class="status-pill status-pending">Доставлено (жду расчёта)</span>';
                        case 'COMPLETED_PAID': return '<span class="status-pill status-paid">Оплачено</span>';
                        default: return '<span class="status-pill status-secondary">' + status + '</span>';
                    }
                }

                function renderOrders() {
                    const el = document.getElementById('orders-list');
                    if (currentOrders.length === 0) {
                        el.innerHTML = '<div class="card" style="text-align: center; color: #94a3b8;">Пока нет заказов. Нажмите "Создать быстрый заказ".</div>';
                        return;
                    }
                    el.innerHTML = currentOrders.map(o => {
                        let actionsHtml = '';
                        if (o.status === 'PENDING_REVIEW') {
                            actionsHtml = `
                                <div style="display: flex; gap: 8px; width: 100%; margin-top: 8px;">
                                    <input id="price-input-${'$'}{o.id}" type="number" placeholder="Укажите цену в ₽" style="max-width: 160px; margin-bottom: 0;">
                                    <input id="note-input-${'$'}{o.id}" placeholder="Заметка (где нашел/замена)" style="margin-bottom: 0;">
                                    <button onclick="proposePrice('${'$'}{o.id}')" class="success">Назвать цену</button>
                                </div>
                            `;
                        } else if (o.status === 'PRICE_PROPOSED') {
                            actionsHtml = `
                                <div style="display: flex; gap: 8px;">
                                    <button onclick="makeDecision('${'$'}{o.id}', true)" class="success">✓ Согласиться ('${'$'}{o.proposedPrice} ₽')</button>
                                    <button onclick="makeDecision('${'$'}{o.id}', false)" class="danger">✕ Отклонить</button>
                                </div>
                            `;
                        } else if (o.status === 'ACCEPTED' || o.status === 'PURCHASING') {
                            actionsHtml = `
                                <div style="display: flex; gap: 8px;">
                                    <button onclick="changeStatus('${'$'}{o.id}', 'PURCHASING', false)" class="secondary">Закупаю</button>
                                    <button onclick="changeStatus('${'$'}{o.id}', 'DELIVERED_UNPAID', false)" class="secondary">Доставлено</button>
                                    <button onclick="changeStatus('${'$'}{o.id}', 'COMPLETED_PAID', true)" class="success">💵 Оплачено наличными/переводом</button>
                                </div>
                            `;
                        } else if (o.status === 'DELIVERED_UNPAID') {
                            actionsHtml = `
                                <button onclick="changeStatus('${'$'}{o.id}', 'COMPLETED_PAID', true)" class="success">💵 Подтвердить оплату в реальности (${'$'}{o.proposedPrice || 0} ₽)</button>
                            `;
                        }

                        return `
                            <div class="card">
                                <div class="order-header">
                                    <div>
                                        <div class="order-title">${'$'}{o.userAvatarEmoji || '👤'} ${'$'}{o.title}</div>
                                        <div class="order-meta">Заказал(а): <strong>${'$'}{o.userName}</strong> • ${'$'}{o.targetStoreOrPlace ? 'Место: ' + o.targetStoreOrPlace + ' • ' : ''}Адрес: ${'$'}{o.deliveryAddress}</div>
                                    </div>
                                    <div>${'$'}{getStatusBadge(o.status, o.proposedPrice, o.isPaidInReality)}</div>
                                </div>
                                ${'$'}{o.description ? '<p style="font-size: 14px; color: #cbd5e1; margin-bottom: 8px;">' + o.description + '</p>' : ''}
                                ${'$'}{o.proposedPrice ? '<div class="price-box">💰 Названная цена: <strong>' + o.proposedPrice + ' ₽</strong> ' + (o.adminNote ? '— <em>' + o.adminNote + '</em>' : '') + '</div>' : ''}
                                <div class="actions">${'$'}{actionsHtml}</div>
                            </div>
                        `;
                    }).join('');
                }

                function updateChatSelect() {
                    const sel = document.getElementById('chat-order-select');
                    const currentVal = sel.value;
                    let opts = '<option value="FAMILY_GENERAL">Общий семейный чат</option>';
                    currentOrders.forEach(o => {
                        opts += `<option value="${'$'}{o.id}">Заказ: ${'$'}{o.title} (${'$'}{o.userName})</option>`;
                    });
                    sel.innerHTML = opts;
                    if (Array.from(sel.options).some(o => o.value === currentVal)) {
                        sel.value = currentVal;
                    }
                }

                function renderChat() {
                    const sel = document.getElementById('chat-order-select').value;
                    const box = document.getElementById('chat-messages');
                    const filtered = currentMessages.filter(m => m.orderId === sel || (sel === 'FAMILY_GENERAL' && (!m.orderId || m.orderId === 'FAMILY_GENERAL')));
                    if (filtered.length === 0) {
                        box.innerHTML = '<div style="text-align: center; color: #64748b; padding-top: 50px;">Нет сообщений в этом чате. Начните диалог!</div>';
                        return;
                    }
                    box.innerHTML = filtered.map(m => {
                        if (m.isSystemEvent) {
                            return `<div class="chat-bubble system">📢 ${'$'}{m.text}</div>`;
                        }
                        const isAdm = m.senderRole === 'ADMIN';
                        return `
                            <div class="chat-bubble ${'$'}{isAdm ? 'admin' : 'buyer'}">
                                <div style="font-size: 11px; opacity: 0.8; margin-bottom: 2px;">${'$'}{m.senderName}</div>
                                <div>${'$'}{m.text}</div>
                            </div>
                        `;
                    }).join('');
                    box.scrollTop = box.scrollHeight;
                }

                async function createOrder() {
                    const title = document.getElementById('new-title').value.trim();
                    if (!title) return alert('Введите название!');
                    const store = document.getElementById('new-store').value.trim();
                    const desc = document.getElementById('new-desc').value.trim();
                    const address = document.getElementById('new-address').value.trim();
                    await fetch('/api/order/create', {
                        method: 'POST',
                        body: JSON.stringify({
                            title,
                            description: desc,
                            targetStoreOrPlace: store,
                            deliveryAddress: address,
                            userName: 'Компьютер / ПК',
                            userAvatarEmoji: '💻'
                        })
                    });
                    document.getElementById('new-title').value = '';
                    document.getElementById('new-desc').value = '';
                    loadData();
                }

                async function proposePrice(orderId) {
                    const price = parseFloat(document.getElementById('price-input-' + orderId).value);
                    if (!price || isNaN(price)) return alert('Введите корректную цену!');
                    const note = document.getElementById('note-input-' + orderId).value;
                    await fetch('/api/order/price', {
                        method: 'POST',
                        body: JSON.stringify({ orderId, price, note })
                    });
                    loadData();
                }

                async function makeDecision(orderId, accept) {
                    await fetch('/api/order/decision', {
                        method: 'POST',
                        body: JSON.stringify({ orderId, accept })
                    });
                    loadData();
                }

                async function changeStatus(orderId, status, isPaid) {
                    await fetch('/api/order/status', {
                        method: 'POST',
                        body: JSON.stringify({ orderId, status, isPaidInReality: isPaid })
                    });
                    loadData();
                }

                async function sendChatMessage() {
                    const input = document.getElementById('chat-input');
                    const text = input.value.trim();
                    if (!text) return;
                    const orderId = document.getElementById('chat-order-select').value;
                    await fetch('/api/chat', {
                        method: 'POST',
                        body: JSON.stringify({
                            orderId,
                            senderName: 'Админ (ПК)',
                            senderRole: 'ADMIN',
                            text
                        })
                    });
                    input.value = '';
                    loadData();
                }

                loadData();
                setInterval(loadData, 3000);
            </script>
        </body>
        </html>
        """.trimIndent()
    }
}
