package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
fun AdminScreen(
    activeProfile: UserProfile,
    orders: List<Order>,
    familyMembers: List<UserProfile>,
    onOpenChat: (Order) -> Unit,
    onProposePriceClick: (Order) -> Unit,
    onDecisionClick: (Order, Boolean) -> Unit,
    onUpdateStatusClick: (Order, OrderStatus) -> Unit,
    onConfirmPaymentClick: (Order) -> Unit,
    onDeleteOrderClick: (Order) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedStatusFilter by remember { mutableStateOf("Требуют оценки") }
    var selectedMemberFilter by remember { mutableStateOf<String?>(null) }

    // Summary calculations
    val needPriceCount = orders.count { it.status == OrderStatus.PENDING_REVIEW }
    val readyToBuy = orders.filter { it.status == OrderStatus.ACCEPTED || it.status == OrderStatus.PURCHASING }
    val readyToBuySum = readyToBuy.sumOf { it.proposedPrice ?: 0.0 }
    val awaitingPayment = orders.filter { it.status == OrderStatus.DELIVERED_UNPAID }
    val awaitingPaymentSum = awaitingPayment.sumOf { it.proposedPrice ?: 0.0 }
    val totalPaidInReality = orders.filter { it.isPaidInReality }.sumOf { it.proposedPrice ?: 0.0 }

    val filteredOrders = orders.filter { order ->
        val statusMatches = when (selectedStatusFilter) {
            "Требуют оценки" -> order.status == OrderStatus.PENDING_REVIEW
            "Согласованы" -> order.status == OrderStatus.ACCEPTED
            "В закупке" -> order.status == OrderStatus.PURCHASING
            "Ждут расчета" -> order.status == OrderStatus.DELIVERED_UNPAID
            "Оплачены" -> order.status == OrderStatus.COMPLETED_PAID
            else -> true
        }
        val memberMatches = selectedMemberFilter == null || order.userId == selectedMemberFilter
        statusMatches && memberMatches
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Admin Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0F172A)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "👑 Панель управления закупками",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Администратор: ${activeProfile.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Finance & Task Summary
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AdminStatWidget(
                            title = "На оценку",
                            value = "$needPriceCount шт",
                            color = Color(0xFFFBBF24),
                            modifier = Modifier.weight(1f)
                        )
                        AdminStatWidget(
                            title = "К закупке",
                            value = "${readyToBuySum.toInt()} ₽",
                            color = Color(0xFF60A5FA),
                            modifier = Modifier.weight(1f)
                        )
                        AdminStatWidget(
                            title = "Жду денег",
                            value = "${awaitingPaymentSum.toInt()} ₽",
                            color = Color(0xFFF87171),
                            modifier = Modifier.weight(1f)
                        )
                        AdminStatWidget(
                            title = "Оплачено",
                            value = "${totalPaidInReality.toInt()} ₽",
                            color = Color(0xFF4ADE80),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Status Tabs / Filters
        item {
            Column {
                Text(
                    text = "Статус заказов:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val statuses = listOf(
                        "Требуют оценки" to needPriceCount,
                        "Согласованы" to orders.count { it.status == OrderStatus.ACCEPTED },
                        "В закупке" to orders.count { it.status == OrderStatus.PURCHASING },
                        "Ждут расчета" to awaitingPayment.size,
                        "Оплачены" to orders.count { it.status == OrderStatus.COMPLETED_PAID },
                        "Все" to orders.size
                    )
                    statuses.forEach { (label, count) ->
                        FilterChip(
                            selected = selectedStatusFilter == label,
                            onClick = { selectedStatusFilter = label },
                            label = { Text("$label ($count)") }
                        )
                    }
                }
            }
        }

        // Member Filter
        item {
            Column {
                Text(
                    text = "Фильтр по членам семьи:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = selectedMemberFilter == null,
                        onClick = { selectedMemberFilter = null },
                        label = { Text("Все") }
                    )
                    familyMembers.forEach { member ->
                        FilterChip(
                            selected = selectedMemberFilter == member.id,
                            onClick = { selectedMemberFilter = member.id },
                            label = { Text("${member.avatarEmoji} ${member.name}") }
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
                            Icons.Default.Assignment,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "В этом списке нет заказов",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Все новые заказы членов семьи появятся здесь для согласования цен",
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
}

@Composable
fun AdminStatWidget(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF1E293B),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontSize = 10.sp,
                color = Color(0xFF94A3B8),
                maxLines = 1
            )
        }
    }
}
