package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessageEntity
import com.example.data.model.OrderEntity
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.OrderStatusBadge
import com.example.ui.components.PaymentStatusBadge
import com.example.ui.components.UserAvatarView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailSheet(
    order: OrderEntity,
    currentProfile: UserProfile?,
    chatMessages: List<ChatMessageEntity>,
    onDismiss: () -> Unit,
    onQuotePrice: (Double, String) -> Unit,
    onRespondToQuote: (Boolean) -> Unit,
    onMarkPurchased: () -> Unit,
    onRecordPayment: (String) -> Unit,
    onCompleteOrder: () -> Unit,
    onDeleteOrder: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isBuyer = currentProfile?.id == order.userId
    val isAdmin = currentProfile?.role == UserRole.ADMIN

    var priceInput by remember { mutableStateOf(order.quotedPrice?.toInt()?.toString() ?: "") }
    var adminNoteInput by remember { mutableStateOf(order.adminNote ?: "") }
    var chatTextInput by remember { mutableStateOf("") }
    var showPaymentDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        modifier = Modifier.testTag("order_detail_sheet")
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Детали заказа",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ID: ${order.id.take(8)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    actions = {
                        if (isAdmin || isBuyer) {
                            IconButton(onClick = onDeleteOrder) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Удалить заказ",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { paddingValues ->
            val listState = rememberLazyListState()

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Order Status & Buyer Info Card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                UserAvatarView(emoji = order.userAvatar, size = 44)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = order.userName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    val df = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale("ru"))
                                    Text(
                                        text = df.format(Date(order.createdAt)),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OrderStatusBadge(status = order.status)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = order.itemTitle,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (order.itemDescription.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = order.itemDescription,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Grid specs
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("🏬 Магазин:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(if (order.targetStore.isNotBlank()) order.targetStore else "Любой", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                }
                                Column {
                                    Text("📍 Куда доставить:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(if (order.deliveryAddress.isNotBlank()) order.deliveryAddress else "Домой", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                }
                                Column {
                                    Text("⚡ Срочность:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(order.urgency, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                // 2. Price Negotiation / Decision Section
                item {
                    when (order.status) {
                        OrderStatus.PENDING_REVIEW -> {
                            // Admin can quote price
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFFEF3C7)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "⏳ Ожидает назначения цены админом",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color(0xFF92400E)
                                    )
                                    Text(
                                        text = "Заказчик указал примерный бюджет: ${order.budgetEstimate?.toInt()?.let { "$it ₽" } ?: "Не указан"}",
                                        fontSize = 13.sp,
                                        color = Color(0xFFB45309)
                                    )

                                    if (isAdmin) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedTextField(
                                            value = priceInput,
                                            onValueChange = { priceInput = it.filter { ch -> ch.isDigit() } },
                                            label = { Text("Назначить реальную цену (₽)") },
                                            placeholder = { Text("Например: 450") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = adminNoteInput,
                                            onValueChange = { adminNoteInput = it },
                                            label = { Text("Комментарий по цене или скидке") },
                                            placeholder = { Text("Например: Нашел со скидкой 20% в Магните") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = {
                                                val price = priceInput.toDoubleOrNull()
                                                if (price != null && price > 0) {
                                                    onQuotePrice(price, adminNoteInput)
                                                }
                                            },
                                            enabled = priceInput.isNotBlank(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFD97706)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("💰 Назначить цену и уведомить покупателя", fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Администратор семьи сейчас проверяет стоимость товара и скоро назовет цену.",
                                            fontSize = 12.sp,
                                            color = Color(0xFF92400E)
                                        )
                                    }
                                }
                            }
                        }

                        OrderStatus.PRICE_QUOTED -> {
                            // Price was quoted by admin! Buyer decides to accept or reject
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFEFF6FF)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "💰 Назначенная цена:",
                                                fontSize = 12.sp,
                                                color = Color(0xFF1E40AF)
                                            )
                                            Text(
                                                text = "${order.quotedPrice?.toInt() ?: 0} ₽",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 26.sp,
                                                color = Color(0xFF1E3A8A)
                                            )
                                        }

                                        Surface(
                                            color = Color(0xFFDBEAFE),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "Ожидает решения",
                                                color = Color(0xFF1D4ED8),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    if (order.adminNote?.isNotBlank() == true) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Заметка админа: ${order.adminNote}",
                                            fontSize = 13.sp,
                                            color = Color(0xFF1E40AF)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Buyer decisions
                                    Text(
                                        text = "Покупатель решает: купить или отказаться?",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1E3A8A)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = { onRespondToQuote(true) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF10B981)
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Согласен! Купить", fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = { onRespondToQuote(false) },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = Color(0xFFEF4444)
                                            ),
                                            modifier = Modifier.weight(0.8f)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Отказаться")
                                        }
                                    }
                                }
                            }
                        }

                        OrderStatus.ACCEPTED -> {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFECFDF5)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "✅ Покупатель согласился с ценой ${order.quotedPrice?.toInt() ?: 0} ₽!",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color(0xFF065F46)
                                    )
                                    Text(
                                        text = "Заказ утвержден в семье. Админ может отправляться за покупкой.",
                                        fontSize = 13.sp,
                                        color = Color(0xFF047857)
                                    )

                                    if (isAdmin) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = onMarkPurchased,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF059669)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.ShoppingBag, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("🛍️ Отметить: Товар куплен!")
                                        }
                                    }
                                }
                            }
                        }

                        OrderStatus.PURCHASED -> {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFF5F3FF)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "🛍️ Товар уже куплен в магазине!",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color(0xFF5B21B6)
                                    )
                                    Text(
                                        text = "Осталось передать товар заказчику и подтвердить оплату в реальности.",
                                        fontSize = 13.sp,
                                        color = Color(0xFF6D28D9)
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = onCompleteOrder,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF7C3AED)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("🎉 Завершить заказ (Передан заказчику)", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        OrderStatus.REJECTED -> {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFFEF2F2)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "❌ Заказ отклонен",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color(0xFF991B1B)
                                    )
                                    Text(
                                        text = "Покупатель или админ отказались от покупки по предложенной цене.",
                                        fontSize = 13.sp,
                                        color = Color(0xFFB91C1C)
                                    )
                                }
                            }
                        }

                        OrderStatus.COMPLETED -> {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFEEF2FF)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "🎉 Заказ полностью выполнен!",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color(0xFF3730A3)
                                    )
                                    Text(
                                        text = "Товар передан, цена согласована и оплачена в реальности.",
                                        fontSize = 13.sp,
                                        color = Color(0xFF4338CA)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Reality Payment Section ("оплата происходит в реальности")
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "💵 Оплата в реальности",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                PaymentStatusBadge(paymentStatus = order.paymentStatus)
                            }

                            if (order.paymentMethod?.isNotBlank() == true) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Способ: ${order.paymentMethod}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (order.paymentStatus != PaymentStatus.PAID_CONFIRMED) {
                                Text(
                                    text = "Деньги передаются лично в руки или переводом на карту:",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onRecordPayment("Наличными лично в руки") },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("💵 Наличные", fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { onRecordPayment("Перевод на карту Сбер/Т-Банк") },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("💳 Перевод", fontSize = 12.sp)
                                    }
                                }
                            } else {
                                Text(
                                    text = "✅ Оплата полностью подтверждена обеими сторонами.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF059669),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // 4. Real-time Order Chat
                item {
                    Text(
                        text = "💬 Чат по этому заказу",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (chatMessages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Сообщений пока нет. Напишите, чтобы уточнить детали или поторговаться!",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(chatMessages, key = { it.id }) { msg ->
                        val isMine = msg.senderId == currentProfile?.id
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                        ) {
                            if (!isMine) {
                                UserAvatarView(emoji = msg.senderAvatar, size = 30)
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
                                if (!isMine) {
                                    Text(
                                        text = msg.senderName,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Surface(
                                    color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = msg.text,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Chat Input box
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = chatTextInput,
                            onValueChange = { chatTextInput = it },
                            placeholder = { Text("Написать по заказу...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                if (chatTextInput.isNotBlank()) {
                                    onSendMessage(chatTextInput.trim())
                                    chatTextInput = ""
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Отправить",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
