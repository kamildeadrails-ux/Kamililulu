package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Notes
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.Order

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PriceProposalDialog(
    order: Order,
    onDismiss: () -> Unit,
    onSubmit: (price: Double, note: String) -> Unit
) {
    var priceText by remember { mutableStateOf(order.proposedPrice?.toInt()?.toString() ?: "") }
    var noteText by remember { mutableStateOf(order.adminNote ?: "") }
    var isError by remember { mutableStateOf(false) }

    val presetChips = listOf(150, 300, 500, 750, 1000, 1500)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Назвать цену заказа",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "«${order.title}» для ${order.userName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = priceText,
                    onValueChange = {
                        priceText = it.filter { char -> char.isDigit() || char == '.' }
                        isError = false
                    },
                    label = { Text("Цена в рублях (₽)") },
                    leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("Пожалуйста, введите корректную сумму") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("price_input_field")
                )

                // Quick preset increments
                Text(
                    text = "Быстрый выбор:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presetChips.forEach { preset ->
                        FilterChip(
                            selected = priceText == preset.toString(),
                            onClick = { priceText = preset.toString() },
                            label = { Text("$preset ₽") }
                        )
                    }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Заметка / Где нашли / Замена (опционально)") },
                    placeholder = { Text("Например: В Пятёрочке только по 1л со скидкой") },
                    leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("price_note_field")
                )

                Text(
                    text = "💡 После отправки покупатель получит пуш-уведомление с кнопками «Купить» или «Отклонить». Оплата производится в реальности при получении.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = priceText.toDoubleOrNull()
                    if (price != null && price > 0) {
                        onSubmit(price, noteText)
                    } else {
                        isError = true
                    }
                },
                modifier = Modifier.testTag("submit_price_button")
            ) {
                Text("Отправить цену")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_price_button")
            ) {
                Text("Отмена")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
