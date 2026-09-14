package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OrderEntity
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.OrderStatusBadge
import com.example.ui.components.RoleBadge
import com.example.ui.components.UserAvatarView

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    currentProfile: UserProfile?,
    allProfiles: List<UserProfile>,
    userOrders: List<OrderEntity>,
    isP2pRunning: Boolean,
    p2pUrl: String,
    onSwitchProfile: (String) -> Unit,
    onCreateProfile: (name: String, role: UserRole, emoji: String, colorHex: String, addresses: List<String>, preferences: String) -> Unit,
    onAddAddress: (String, String) -> Unit,
    onRemoveAddress: (String, String) -> Unit,
    onUpdatePreferences: (UserProfile) -> Unit,
    onToggleP2p: () -> Unit,
    onOrderClick: (OrderEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var newAddressInput by remember { mutableStateOf("") }
    var preferencesInput by remember(currentProfile?.preferences) {
        mutableStateOf(currentProfile?.preferences ?: "")
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize().testTag("profile_screen")
    ) {
        // Active Profile Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatarView(
                            emoji = currentProfile?.avatarEmoji ?: "👤",
                            accentColorHex = currentProfile?.accentColorHex ?: "#4F46E5",
                            size = 56
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentProfile?.name ?: "Профиль не выбран",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RoleBadge(role = currentProfile?.role ?: UserRole.MEMBER)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Активный профиль",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Family Switcher Header & Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "👨‍👩‍👧‍👦 Члены семьи (переключение)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = { showAddMemberDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Добавить")
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(allProfiles, key = { it.id }) { prof ->
                    val isSelected = prof.id == currentProfile?.id
                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSwitchProfile(prof.id) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            UserAvatarView(emoji = prof.avatarEmoji, size = 32)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = prof.name,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = prof.role.displayName(),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Выбран",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Saved Addresses Section
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📍 Сохранённые адреса и комнаты",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Куда обычно доставлять заказы этого члена семьи:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val addresses = currentProfile?.savedAddresses ?: emptyList()
                    if (addresses.isEmpty()) {
                        Text(
                            text = "Пока нет сохранённых мест. Добавьте ниже (например: 'Моя комната', 'Кухня').",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            addresses.forEach { addr ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(text = "🏠 $addr", fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Удалить адрес",
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable {
                                                    currentProfile?.let { onRemoveAddress(it.id, addr) }
                                                },
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Add new address input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newAddressInput,
                            onValueChange = { newAddressInput = it },
                            placeholder = { Text("Новый адрес / комната", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newAddressInput.isNotBlank() && currentProfile != null) {
                                    onAddAddress(currentProfile.id, newAddressInput.trim())
                                    newAddressInput = ""
                                }
                            },
                            enabled = newAddressInput.isNotBlank(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Добавить")
                        }
                    }
                }
            }
        }

        // Specific Preferences Section
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⭐ Личные предпочтения и пожелания",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Вкусовые предпочтения, диета, любимые магазины, лимиты бюджета:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = preferencesInput,
                        onValueChange = { preferencesInput = it },
                        placeholder = { Text("Например: Без лука и сахара, любимый магазин ВкусВилл, брать со скидкой по карте") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (currentProfile != null) {
                                onUpdatePreferences(currentProfile.copy(preferences = preferencesInput.trim()))
                                Toast.makeText(context, "Предпочтения сохранены!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Сохранить предпочтения")
                    }
                }
            }
        }

        // Past Orders by This Profile
        item {
            Text(
                text = "📦 История заказов профиля (${userOrders.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (userOrders.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "У этого профиля пока нет оформленных заказов",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(userOrders, key = { it.id }) { ord ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().clickable { onOrderClick(ord) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ord.itemTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("📍 ${ord.deliveryAddress}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (ord.quotedPrice != null) {
                                Text("💰 ${ord.quotedPrice.toInt()} ₽", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                            }
                        }
                        OrderStatusBadge(status = ord.status)
                    }
                }
            }
        }

        // P2P Web Server Control Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Computer, contentDescription = null, tint = Color(0xFF38BDF8))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("P2P Семейный Хаб для ПК", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(checked = isP2pRunning, onCheckedChange = { onToggleP2p() })
                    }
                    if (isP2pRunning && p2pUrl.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Компьютер в Wi-Fi сети может подключиться по адресу:\n$p2pUrl",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(60.dp)) }
    }

    // Add Family Member Dialog
    if (showAddMemberDialog) {
        var memberName by remember { mutableStateOf("") }
        var memberRole by remember { mutableStateOf(UserRole.MEMBER) }
        var memberEmoji by remember { mutableStateOf("🧑") }
        var memberAddress by remember { mutableStateOf("") }
        var memberPrefs by remember { mutableStateOf("") }

        val emojiOptions = listOf("🧑", "👩", "👨", "👧", "👦", "👵", "👴", "🐱")

        AlertDialog(
            onDismissRequest = { showAddMemberDialog = false },
            title = { Text("Новый член семьи") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = memberName,
                        onValueChange = { memberName = it },
                        label = { Text("Имя") },
                        placeholder = { Text("Например: Бабушка, Брат Тимур") },
                        singleLine = true
                    )

                    Text("Роль:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = memberRole == UserRole.MEMBER, onClick = { memberRole = UserRole.MEMBER })
                        Text("Член семьи", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(selected = memberRole == UserRole.ADMIN, onClick = { memberRole = UserRole.ADMIN })
                        Text("Администратор", fontSize = 13.sp)
                    }

                    Text("Аватар:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        emojiOptions.forEach { em ->
                            Surface(
                                color = if (memberEmoji == em) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape,
                                modifier = Modifier.size(36.dp).clickable { memberEmoji = em }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(em, fontSize = 18.sp)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = memberAddress,
                        onValueChange = { memberAddress = it },
                        label = { Text("Комната / Адрес по умолчанию") },
                        placeholder = { Text("Например: Гостиная") },
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = memberPrefs,
                        onValueChange = { memberPrefs = it },
                        label = { Text("Предпочтения (диета, пожелания)") },
                        placeholder = { Text("Например: Без глютена") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (memberName.isNotBlank()) {
                            val addrs = if (memberAddress.isNotBlank()) listOf(memberAddress.trim()) else listOf("Дом")
                            onCreateProfile(
                                memberName.trim(),
                                memberRole,
                                memberEmoji,
                                if (memberRole == UserRole.ADMIN) "#EC4899" else "#4F46E5",
                                addrs,
                                memberPrefs.trim()
                            )
                            showAddMemberDialog = false
                        }
                    },
                    enabled = memberName.isNotBlank()
                ) {
                    Text("Создать профиль")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMemberDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}
