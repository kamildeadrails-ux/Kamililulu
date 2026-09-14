package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChatMessage
import com.example.model.Order
import com.example.model.OrderStatus
import com.example.model.ProfileRole
import com.example.model.UserProfile
import com.example.ui.components.ChatBubble

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FamilyChatScreen(
    activeProfile: UserProfile,
    orders: List<Order>,
    allMessages: List<ChatMessage>,
    selectedOrderId: String?,
    onSelectOrderChat: (String?) -> Unit,
    onSendMessage: (orderId: String, text: String) -> Unit,
    onAcceptPrice: (Order) -> Unit,
    onDeclinePrice: (Order) -> Unit,
    modifier: Modifier = Modifier
) {
    var messageInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Determine current order context
    val currentOrder = orders.find { it.id == selectedOrderId }
    val currentOrderId = selectedOrderId ?: "FAMILY_GENERAL"

    // Filter messages for current order channel
    val filteredMessages = allMessages.filter { msg ->
        if (currentOrderId == "FAMILY_GENERAL") {
            msg.orderId == "FAMILY_GENERAL" || msg.orderId.isEmpty()
        } else {
            msg.orderId == currentOrderId
        }
    }

    val quickReplies = if (activeProfile.role == ProfileRole.ADMIN) {
        listOf("В магазине по скидке!", "Есть только замена", "Буду дома через 15 мин", "Я уже на кассе")
    } else {
        listOf("Да, согласен на цену!", "Слишком дорого, отмени", "Сфоткай, пожалуйста", "Спасибо!")
    }

    LaunchedEffect(filteredMessages.size) {
        if (filteredMessages.isNotEmpty()) {
            listState.animateScrollToItem(filteredMessages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 80.dp)
    ) {
        // Channel Tabs: General Family Chat + Orders
        val channels = listOf("FAMILY_GENERAL" to "💬 Общий семейный") + orders.map { it.id to "${it.userAvatarEmoji} ${it.title.take(16)}" }

        ScrollableTabRow(
            selectedTabIndex = channels.indexOfFirst { it.first == currentOrderId }.coerceAtLeast(0),
            edgePadding = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            channels.forEach { (chId, label) ->
                val isSelected = chId == currentOrderId
                Tab(
                    selected = isSelected,
                    onClick = {
                        onSelectOrderChat(if (chId == "FAMILY_GENERAL") null else chId)
                    },
                    text = {
                        Text(
                            text = label,
                            maxLines = 1,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        // Active Order Info Banner if discussing specific order
        if (currentOrder != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Обсуждение: ${currentOrder.title}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Заказчик: ${currentOrder.userName} • Статус: ${currentOrder.status.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (currentOrder.proposedPrice != null) {
                            Text(
                                text = "💰 Цена: ${currentOrder.proposedPrice.toInt()} ₽",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Buyer can accept/decline right here in chat
                    if (currentOrder.status == OrderStatus.PRICE_PROPOSED && activeProfile.id == currentOrder.userId) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { onAcceptPrice(currentOrder) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFF16A34A), CircleShape)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Согласиться", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { onDeclinePrice(currentOrder) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFDC2626), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Отклонить", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // Message Thread
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (filteredMessages.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (currentOrder != null) "Начните обсуждение деталей заказа" else "Общий чат семьи пуст",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Задайте вопрос о цене, наличии или сроках доставки",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredMessages, key = { it.id }) { msg ->
                        ChatBubble(
                            message = msg,
                            isCurrentUser = msg.senderId == activeProfile.id
                        )
                    }
                }
            }
        }

        // Quick replies chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            quickReplies.forEach { reply ->
                FilterChip(
                    selected = false,
                    onClick = { onSendMessage(currentOrderId, reply) },
                    label = { Text(reply, fontSize = 12.sp) }
                )
            }
        }

        // Bottom Input Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageInput,
                onValueChange = { messageInput = it },
                placeholder = { Text("Сообщение для семьи...") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("family_chat_input"),
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (messageInput.trim().isNotEmpty()) {
                        onSendMessage(currentOrderId, messageInput.trim())
                        messageInput = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .testTag("family_chat_send_btn")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Отправить",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
