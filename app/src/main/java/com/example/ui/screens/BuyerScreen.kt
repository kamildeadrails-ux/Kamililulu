package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Order
import com.example.model.OrderStatus
import com.example.model.UserProfile
import com.example.ui.components.OrderCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuyerScreen(
    activeProfile: UserProfile,
    orders: List<Order>,
    onOpenNewOrderDialog: () -> Unit,
    onEditProfileClick: () -> Unit,
    onOpenChat: (Order) -> Unit,
    onProposePriceClick: (Order) -> Unit,
    onDecisionClick: (Order, Boolean) -> Unit,
    onUpdateStatusClick: (Order, OrderStatus) -> Unit,
    onConfirmPaymentClick: (Order) -> Unit,
    onDeleteOrderClick: (Order) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("Все") }

    val myOrders = orders.filter { it.userId == activeProfile.id }
    val awaitingPrice = myOrders.count { it.status == OrderStatus.PENDING_REVIEW }
    val priceProposed = myOrders.count { it.status == OrderStatus.PRICE_PROPOSED }
    val awaitingPayment = myOrders.count { it.status == OrderStatus.DELIVERED_UNPAID }
    val totalPaid = myOrders.filter { it.isPaidInReality }.sumOf { it.proposedPrice ?: 0.0 }

    val filteredOrders = when (selectedFilter) {
        "Ждут решения" -> myOrders.filter { it.status == OrderStatus.PRICE_PROPOSED }
        "В закупке" -> myOrders.filter { it.status == OrderStatus.ACCEPTED || it.status == OrderStatus.PURCHASING }
        "Оплата в реальности" -> myOrders.filter { it.status == OrderStatus.DELIVERED_UNPAID }
        "Завершены" -> myOrders.filter { it.status == OrderStatus.COMPLETED_PAID }
        else -> myOrders
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile & Preferences banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(activeProfile.avatarEmoji, fontSize = 28.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = activeProfile.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Личный кабинет покупателя",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(onClick = onEditProfileClick) {
                                Icon(Icons.Default.Edit, contentDescription = "Редактировать предпочтения")
                            }
                        }

                        if (activeProfile.preferences.isNotBlank() || activeProfile.savedAddresses.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    if (activeProfile.preferences.isNotBlank()) {
                                        Text(
                                            text = "📌 Пожелания: ${activeProfile.preferences}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    if (activeProfile.savedAddresses.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Адреса: ${activeProfile.savedAddresses.joinToString(", ")}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Stats Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        title = "Ждут цену",
                        value = awaitingPrice.toString(),
                        color = Color(0xFFD97706),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Согласовать",
                        value = priceProposed.toString(),
                        color = Color(0xFF2563EB),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "К оплате",
                        value = awaitingPayment.toString(),
                        color = Color(0xFFDC2626),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Оплачено",
                        value = "${totalPaid.toInt()} ₽",
                        color = Color(0xFF16A34A),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // CTA Button "Хочу заказать"
            item {
                Button(
                    onClick = onOpenNewOrderDialog,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("create_new_order_cta")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Написать, что нужно купить (Хочу заказать)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Filters
            item {
                Column {
                    Text(
                        text = "Мои заказы (${filteredOrders.size}):",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Все", "Ждут решения", "В закупке", "Оплата в реальности", "Завершены").forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter) }
                            )
                        }
                    }
                }
            }

            // Orders list
            if (filteredOrders.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.ShoppingBag,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Нет заказов в этой категории",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Нажмите «Хочу заказать», чтобы отправить запрос админу семьи",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(filteredOrders, key = { it.id }) { order ->
                    OrderCard(
                        order = order,
                        activeProfile = activeProfile,
                        onOpenChat = { onOpenChat(order) },
                        onProposePriceClick = { onProposePriceClick(order) },
                        onDecisionClick = { accept -> onDecisionClick(order, accept) },
                        onUpdateStatusClick = { status -> onUpdateStatusClick(order, status) },
                        onConfirmPaymentClick = { onConfirmPaymentClick(order) },
                        onDeleteClick = { onDeleteOrderClick(order) }
                    )
                }
            }
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = onOpenNewOrderDialog,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("fab_new_order")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Новый заказ")
        }
    }
}

@Composable
fun StatCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
