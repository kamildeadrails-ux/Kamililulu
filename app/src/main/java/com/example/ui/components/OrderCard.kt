package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PriceChange
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.model.ProfileRole
import com.example.model.UserProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrderCard(
    order: Order,
    activeProfile: UserProfile,
    onOpenChat: () -> Unit,
    onProposePriceClick: () -> Unit,
    onDecisionClick: (accept: Boolean) -> Unit,
    onUpdateStatusClick: (OrderStatus) -> Unit,
    onConfirmPaymentClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAdmin = activeProfile.role == ProfileRole.ADMIN
    val isOwner = activeProfile.id == order.userId
    val timeFormat = SimpleDateFormat("dd MMM, HH:mm", Locale("ru"))
    val formattedTime = timeFormat.format(Date(order.createdAt))

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("order_card_${order.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: User avatar + name + status pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = order.userAvatarEmoji,
                            fontSize = 20.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = order.userName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                StatusBadge(status = order.status, isPaid = order.isPaidInReality)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Order Title
            Text(
                text = order.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Description if present
            if (order.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = order.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Meta tags: Store, Address, Urgency
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (order.targetStoreOrPlace.isNotBlank()) {
                    MetaTag(
                        icon = { Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = order.targetStoreOrPlace
                    )
                }

                if (order.deliveryAddress.isNotBlank()) {
                    MetaTag(
                        icon = { Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = order.deliveryAddress
                    )
                }

                MetaTag(
                    icon = null,
                    label = "⚡ ${order.urgency}",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                )
            }

            // Proposed Price Box
            if (order.proposedPrice != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when (order.status) {
                        OrderStatus.ACCEPTED, OrderStatus.COMPLETED_PAID -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        OrderStatus.DECLINED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.surface
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Названная цена:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${order.proposedPrice.toInt()} ₽",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (!order.adminNote.isNullOrBlank()) {
                                Text(
                                    text = "Заметка админа: ${order.adminNote}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (order.isPaidInReality) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF16A34A),
                                contentColor = Color.White
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Оплачено в реальности", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Row / Flow
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // BUYER ACTIONS:
                if (order.status == OrderStatus.PRICE_PROPOSED) {
                    Button(
                        onClick = { onDecisionClick(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        modifier = Modifier.testTag("accept_order_${order.id}")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Купить за ${order.proposedPrice?.toInt()} ₽")
                    }

                    OutlinedButton(
                        onClick = { onDecisionClick(false) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("decline_order_${order.id}")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Отклонить")
                    }
                }

                // ADMIN ACTIONS:
                if (isAdmin) {
                    if (order.status == OrderStatus.PENDING_REVIEW) {
                        Button(
                            onClick = onProposePriceClick,
                            modifier = Modifier.testTag("propose_price_${order.id}")
                        ) {
                            Icon(Icons.Default.PriceChange, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Назвать цену")
                        }
                    } else if (order.status == OrderStatus.PRICE_PROPOSED) {
                        FilledTonalButton(onClick = onProposePriceClick) {
                            Icon(Icons.Default.PriceChange, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Изменить цену")
                        }
                    } else if (order.status == OrderStatus.ACCEPTED) {
                        Button(
                            onClick = { onUpdateStatusClick(OrderStatus.PURCHASING) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Взять в закупку")
                        }
                    } else if (order.status == OrderStatus.PURCHASING) {
                        Button(
                            onClick = { onUpdateStatusClick(OrderStatus.DELIVERED_UNPAID) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                        ) {
                            Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Товар доставлен")
                        }
                    }
                }

                // REAL-LIFE PAYMENT CONFIRMATION:
                if (order.status == OrderStatus.DELIVERED_UNPAID || (order.status == OrderStatus.ACCEPTED && !order.isPaidInReality)) {
                    Button(
                        onClick = onConfirmPaymentClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        modifier = Modifier.testTag("confirm_payment_${order.id}")
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Оплачено в реальности (${order.proposedPrice?.toInt() ?: 0} ₽)")
                    }
                }

                // CHAT BUTTON (Always accessible)
                FilledTonalButton(
                    onClick = onOpenChat,
                    modifier = Modifier.testTag("open_chat_${order.id}")
                ) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Чат по заказу")
                }

                // DELETE BUTTON (for owner or admin)
                if (isAdmin || isOwner) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.testTag("delete_order_${order.id}")
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Удалить заказ",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: OrderStatus, isPaid: Boolean) {
    val (label, bgColor, textColor) = when {
        isPaid -> Triple("Оплачено ✓", Color(0xFFDCFCE7), Color(0xFF15803D))
        status == OrderStatus.PENDING_REVIEW -> Triple("Ждёт оценки", Color(0xFFFEF9C3), Color(0xFF854D0E))
        status == OrderStatus.PRICE_PROPOSED -> Triple("Названа цена", Color(0xFFDBEAFE), Color(0xFF1D4ED8))
        status == OrderStatus.ACCEPTED -> Triple("Согласовано", Color(0xFFD1FAE5), Color(0xFF065F46))
        status == OrderStatus.DECLINED -> Triple("Отклонено", Color(0xFFFEE2E2), Color(0xFF991B1B))
        status == OrderStatus.PURCHASING -> Triple("В закупке", Color(0xFFEDE9FE), Color(0xFF5B21B6))
        status == OrderStatus.DELIVERED_UNPAID -> Triple("Доставлено (жду расчёта)", Color(0xFFFFEDD5), Color(0xFFC2410C))
        status == OrderStatus.COMPLETED_PAID -> Triple("Завершено ✓", Color(0xFFDCFCE7), Color(0xFF15803D))
        status == OrderStatus.CANCELLED -> Triple("Отменено", Color(0xFFF1F5F9), Color(0xFF475569))
        else -> Triple(status.name, Color(0xFFF1F5F9), Color(0xFF475569))
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        contentColor = textColor
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun MetaTag(
    icon: (@Composable () -> Unit)?,
    label: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
