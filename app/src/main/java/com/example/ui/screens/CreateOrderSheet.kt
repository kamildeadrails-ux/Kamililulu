package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserProfile
import com.example.ui.components.UserAvatarView

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateOrderSheet(
    currentProfile: UserProfile?,
    onDismiss: () -> Unit,
    onSubmitOrder: (title: String, description: String, store: String, address: String, urgency: String, budget: Double?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var itemTitle by remember { mutableStateOf("") }
    var itemDescription by remember { mutableStateOf(currentProfile?.preferences ?: "") }
    var store by remember { mutableStateOf("") }
    var address by remember { mutableStateOf(currentProfile?.savedAddresses?.firstOrNull() ?: "Дом") }
    var urgency by remember { mutableStateOf("Обычная") }
    var budgetText by remember { mutableStateOf("") }

    val savedAddresses = currentProfile?.savedAddresses ?: emptyList()
    val urgencyOptions = listOf("Обычная", "Срочно к ужину ⚡", "Не горит / Когда будешь в магазине")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        modifier = Modifier.testTag("create_order_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = "Что ты хочешь заказать?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Sender profile banner
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatarView(
                            emoji = currentProfile?.avatarEmoji ?: "👤",
                            size = 36
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Заказчик: ${currentProfile?.name ?: "Член семьи"}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Админ получит оповещение и назначит цену",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Item Title (Required)
                OutlinedTextField(
                    value = itemTitle,
                    onValueChange = { itemTitle = it },
                    label = { Text("Что купить? (Название)*") },
                    placeholder = { Text("Например: Пицца Пепперони, Молоко 3.2%, Наушники") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_order_title_input")
                )

                // Item Details / Link
                OutlinedTextField(
                    value = itemDescription,
                    onValueChange = { itemDescription = it },
                    label = { Text("Уточнения, ссылка или пожелания") },
                    placeholder = { Text("Например: Вкус сырный, упаковка 500г, если нет - взять аналог") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Target Store
                OutlinedTextField(
                    value = store,
                    onValueChange = { store = it },
                    label = { Text("Где купить? (Магазин / Сервис)") },
                    placeholder = { Text("Например: ВкусВилл, Озон, Аптека, Любой ближайший") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Delivery address selection
                Column {
                    Text(
                        text = "Куда доставить / передать:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (savedAddresses.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            savedAddresses.forEach { addr ->
                                FilterChip(
                                    selected = address == addr,
                                    onClick = { address = addr },
                                    label = { Text(addr, fontSize = 12.sp) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        placeholder = { Text("Или введите другое место (Комната, Кухня, Адрес)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Urgency
                Column {
                    Text(
                        text = "Срочность:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        urgencyOptions.forEach { opt ->
                            FilterChip(
                                selected = urgency == opt,
                                onClick = { urgency = opt },
                                label = { Text(opt, fontSize = 12.sp) }
                            )
                        }
                    }
                }

                // Expected budget estimate
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { budgetText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Примерный бюджет (₽) - необязательно") },
                    placeholder = { Text("Например: 500") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (itemTitle.isNotBlank()) {
                            val budget = budgetText.toDoubleOrNull()
                            onSubmitOrder(itemTitle.trim(), itemDescription.trim(), store.trim(), address.trim(), urgency, budget)
                            onDismiss()
                        }
                    },
                    enabled = itemTitle.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("submit_order_button")
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🚀 Отправить заказ админу", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}
