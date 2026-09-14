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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ProfileRole
import com.example.model.UserProfile

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileEditDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var selectedRole by remember { mutableStateOf(profile.role) }
    var selectedEmoji by remember { mutableStateOf(profile.avatarEmoji) }
    var preferences by remember { mutableStateOf(profile.preferences) }
    var phone by remember { mutableStateOf(profile.phone) }
    val addresses = remember { mutableStateListOf<String>().apply { addAll(profile.savedAddresses) } }
    var newAddressInput by remember { mutableStateOf("") }

    val emojiChoices = listOf("👩", "👨", "👦", "👧", "🧑‍🦰", "👵", "👴", "🐱", "🐶", "⭐")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Настройки профиля",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Emoji picker
                Text(
                    text = "Выберите аватар:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    emojiChoices.forEach { emoji ->
                        FilterChip(
                            selected = selectedEmoji == emoji,
                            onClick = { selectedEmoji = emoji },
                            label = { Text(emoji, fontSize = 20.sp) }
                        )
                    }
                }

                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя члена семьи") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profile_name_input")
                )

                // Role selector
                Text(
                    text = "Роль в семье:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedRole == ProfileRole.BUYER,
                        onClick = { selectedRole = ProfileRole.BUYER },
                        label = { Text("🛒 Покупатель / Заказчик") }
                    )
                    FilterChip(
                        selected = selectedRole == ProfileRole.ADMIN,
                        onClick = { selectedRole = ProfileRole.ADMIN },
                        label = { Text("👑 Админ / Закупщик") }
                    )
                }

                // Saved Addresses
                Text(
                    text = "Сохранённые адреса и комнаты:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    addresses.forEach { addr ->
                        InputChip(
                            selected = true,
                            onClick = { addresses.remove(addr) },
                            label = { Text(addr) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = "Удалить")
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newAddressInput,
                        onValueChange = { newAddressInput = it },
                        placeholder = { Text("Новая комната/адрес") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (newAddressInput.trim().isNotEmpty()) {
                                addresses.add(newAddressInput.trim())
                                newAddressInput = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить адрес")
                    }
                }

                // Personal preferences
                OutlinedTextField(
                    value = preferences,
                    onValueChange = { preferences = it },
                    label = { Text("Пожелания и предпочтения") },
                    placeholder = { Text("Например: Без лука, только свежее, бюджет до 500₽") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("profile_prefs_input")
                )

                // Phone
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Телефон для связи (опционально)") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = profile.copy(
                        name = name.trim().ifEmpty { profile.name },
                        role = selectedRole,
                        avatarEmoji = selectedEmoji,
                        savedAddresses = addresses.toList(),
                        preferences = preferences.trim(),
                        phone = phone.trim()
                    )
                    onSave(updated)
                },
                modifier = Modifier.testTag("save_profile_button")
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
