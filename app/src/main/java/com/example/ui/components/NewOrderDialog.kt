package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.UserProfile

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewOrderDialog(
    userProfile: UserProfile,
    onDismiss: () -> Unit,
    onSubmit: (title: String, desc: String, store: String, address: String, urgency: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var store by remember { mutableStateOf("") }
    var customAddress by remember {
        mutableStateOf(userProfile.savedAddresses.firstOrNull() ?: "Дом (Кухня)")
    }
    var urgency by remember { mutableStateOf("Обычная") }
    var isTitleError by remember { mutableStateOf(false) }

    val urgencyOptions = listOf("Обычная", "Срочно к столу", "Как будет время")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Новый заказ в семью",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Заказывает: ${userProfile.avatarEmoji} ${userProfile.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        isTitleError = false
                    },
                    label = { Text("Что нужно купить / заказать? *") },
                    placeholder = { Text("Например: Пицца 4 сыра, Молоко, Батарейки") },
                    leadingIcon = { Icon(Icons.Default.ShoppingBag, contentDescription = null) },
                    isError = isTitleError,
                    supportingText = if (isTitleError) {
                        { Text("Обязательное поле") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("order_title_input")
                )

                OutlinedTextField(
                    value = store,
                    onValueChange = { store = it },
                    label = { Text("Где купить / Магазин / Ссылка") },
                    placeholder = { Text("Например: Додо пицца, ВкусВилл или Аптека") },
                    leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("order_store_input")
                )

                // Saved Addresses chips + custom
                Text(
                    text = "Куда доставить / Кому передать:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    userProfile.savedAddresses.forEach { addr ->
                        FilterChip(
                            selected = customAddress == addr,
                            onClick = { customAddress = addr },
                            label = { Text(addr) }
                        )
                    }
                }

                OutlinedTextField(
                    value = customAddress,
                    onValueChange = { customAddress = it },
                    label = { Text("Уточнить место передачи") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Urgency chips
                Text(
                    text = "Срочность:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    urgencyOptions.forEach { opt ->
                        FilterChip(
                            selected = urgency == opt,
                            onClick = { urgency = opt },
                            label = { Text(opt) }
                        )
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Комментарий и пожелания (опционально)") },
                    placeholder = { Text("Бренд, объем, без лука, до 600 рублей...") },
                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("order_desc_input")
                )

                if (userProfile.preferences.isNotBlank()) {
                    Text(
                        text = "📌 Ваши сохранённые предпочтения: ${userProfile.preferences}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.trim().isEmpty()) {
                        isTitleError = true
                    } else {
                        onSubmit(title, description, store, customAddress, urgency)
                    }
                },
                modifier = Modifier.testTag("submit_order_button")
            ) {
                Text("Отправить админу")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_order_button")
            ) {
                Text("Отмена")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
