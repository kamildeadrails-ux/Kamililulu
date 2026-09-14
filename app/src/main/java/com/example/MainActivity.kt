package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.core.content.ContextCompat
import com.example.model.Order
import com.example.model.ProfileRole
import com.example.model.UserProfile
import com.example.ui.MainViewModel
import com.example.ui.components.NewOrderDialog
import com.example.ui.components.NotificationListSheet
import com.example.ui.components.OrderChatBottomSheet
import com.example.ui.components.P2PHubDialog
import com.example.ui.components.PaymentConfirmationDialog
import com.example.ui.components.PriceProposalDialog
import com.example.ui.components.ProfileEditDialog
import com.example.ui.components.ProfileSwitchBottomSheet
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.BrotherRegistrationScreen
import com.example.ui.screens.BuyerScreen
import com.example.ui.screens.FamilyChatScreen
import com.example.ui.screens.ProfilesScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    val activeProfile by viewModel.activeProfile.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val orders by viewModel.orders.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val unreadNotificationsCount by viewModel.unreadNotificationsCount.collectAsState()
    val activeChatMessages by viewModel.activeChatMessages.collectAsState()
    val isServerRunning by viewModel.isServerRunning.collectAsState()
    val serverMessage by viewModel.serverStatusMessage.collectAsState()
    val localIp by viewModel.localIp.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    val isRegistered by viewModel.isRegistered.collectAsState()

    var selectedNavIndex by remember { mutableIntStateOf(0) }

    // If user has not registered/selected which brother they are, show registration screen
    if (!isRegistered) {
        BrotherRegistrationScreen(
            profiles = profiles,
            onSelectBrother = { brotherId ->
                viewModel.registerBrother(brotherId)
                if (brotherId == "kamil_admin") {
                    selectedNavIndex = 1
                } else {
                    selectedNavIndex = 0
                }
            }
        )
        return
    }

    // Dialog & sheet states
    var showNewOrderDialog by remember { mutableStateOf(false) }
    var orderForPriceProposal by remember { mutableStateOf<Order?>(null) }
    var orderForPaymentConfirmation by remember { mutableStateOf<Order?>(null) }
    var orderForChat by remember { mutableStateOf<Order?>(null) }
    var showProfileSwitcher by remember { mutableStateOf(false) }
    var profileToEdit by remember { mutableStateOf<UserProfile?>(null) }
    var showNotificationsSheet by remember { mutableStateOf(false) }
    var showP2PHubDialog by remember { mutableStateOf(false) }
    var selectedOrderChatId by remember { mutableStateOf<String?>(null) }

    // Request POST_NOTIFICATIONS on Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Заказы братьев",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        // Active profile badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier
                                .clickable { showProfileSwitcher = true }
                                .testTag("active_profile_top_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(activeProfile.avatarEmoji, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${activeProfile.name} • ${if (activeProfile.role == ProfileRole.ADMIN) "Админ" else "Заказчик"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                },
                actions = {
                    // P2P Hub & Server Icon
                    IconButton(
                        onClick = { showP2PHubDialog = true },
                        modifier = Modifier.testTag("p2p_hub_button")
                    ) {
                        Box {
                            Icon(
                                Icons.Default.Dns,
                                contentDescription = "P2P Сервер & ПК",
                                tint = if (isServerRunning) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isServerRunning) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF16A34A), CircleShape)
                                        .align(Alignment.TopEnd)
                                )
                            }
                        }
                    }

                    // Notification Bell with Badge
                    IconButton(
                        onClick = { showNotificationsSheet = true },
                        modifier = Modifier.testTag("notifications_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadNotificationsCount > 0) {
                                    Badge { Text("$unreadNotificationsCount") }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "Уведомления",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(modifier = Modifier.testTag("bottom_nav")) {
                NavigationBarItem(
                    selected = selectedNavIndex == 0,
                    onClick = { selectedNavIndex = 0 },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                    label = { Text("Заказчик") },
                    modifier = Modifier.testTag("nav_buyer")
                )
                NavigationBarItem(
                    selected = selectedNavIndex == 1,
                    onClick = { selectedNavIndex = 1 },
                    icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                    label = { Text("Админ") },
                    modifier = Modifier.testTag("nav_admin")
                )
                NavigationBarItem(
                    selected = selectedNavIndex == 2,
                    onClick = { selectedNavIndex = 2 },
                    icon = { Icon(Icons.Default.Forum, contentDescription = null) },
                    label = { Text("Чат") },
                    modifier = Modifier.testTag("nav_chat")
                )
                NavigationBarItem(
                    selected = selectedNavIndex == 3,
                    onClick = { selectedNavIndex = 3 },
                    icon = { Icon(Icons.Default.People, contentDescription = null) },
                    label = { Text("Братья") },
                    modifier = Modifier.testTag("nav_profiles")
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedNavIndex) {
                0 -> BuyerScreen(
                    activeProfile = activeProfile,
                    orders = orders,
                    onOpenNewOrderDialog = { showNewOrderDialog = true },
                    onEditProfileClick = { profileToEdit = activeProfile },
                    onOpenChat = { order -> orderForChat = order },
                    onProposePriceClick = { order -> orderForPriceProposal = order },
                    onDecisionClick = { order, accept -> viewModel.respondToPrice(order.id, accept) },
                    onUpdateStatusClick = { order, status -> viewModel.updateOrderStatus(order.id, status) },
                    onConfirmPaymentClick = { order -> orderForPaymentConfirmation = order },
                    onDeleteOrderClick = { order -> viewModel.repository.apply { } /* handled via VM */ }
                )

                1 -> AdminScreen(
                    activeProfile = activeProfile,
                    orders = orders,
                    familyMembers = profiles,
                    onOpenChat = { order -> orderForChat = order },
                    onProposePriceClick = { order -> orderForPriceProposal = order },
                    onDecisionClick = { order, accept -> viewModel.respondToPrice(order.id, accept) },
                    onUpdateStatusClick = { order, status -> viewModel.updateOrderStatus(order.id, status) },
                    onConfirmPaymentClick = { order -> orderForPaymentConfirmation = order },
                    onDeleteOrderClick = { order -> /* handled */ }
                )

                2 -> FamilyChatScreen(
                    activeProfile = activeProfile,
                    orders = orders,
                    allMessages = activeChatMessages,
                    selectedOrderId = selectedOrderChatId,
                    onSelectOrderChat = { orderId ->
                        selectedOrderChatId = orderId
                        viewModel.openChatForOrder(orderId)
                    },
                    onSendMessage = { orderId, text ->
                        viewModel.sendChatMessage(orderId, text)
                    },
                    onAcceptPrice = { order -> viewModel.respondToPrice(order.id, true) },
                    onDeclinePrice = { order -> viewModel.respondToPrice(order.id, false) }
                )

                3 -> ProfilesScreen(
                    profiles = profiles,
                    activeProfile = activeProfile,
                    orders = orders,
                    onSelectProfile = { id -> viewModel.selectProfile(id) },
                    onEditProfile = { p -> profileToEdit = p },
                    onAddNewProfile = { p -> viewModel.updateProfile(p) }
                )
            }
        }
    }

    // New Order Dialog
    if (showNewOrderDialog) {
        NewOrderDialog(
            userProfile = activeProfile,
            onDismiss = { showNewOrderDialog = false },
            onSubmit = { title, desc, store, address, urgency ->
                viewModel.createOrder(title, desc, store, address, urgency)
                showNewOrderDialog = false
            }
        )
    }

    // Price Proposal Dialog (Admin)
    orderForPriceProposal?.let { order ->
        PriceProposalDialog(
            order = order,
            onDismiss = { orderForPriceProposal = null },
            onSubmit = { price, note ->
                viewModel.proposePrice(order.id, price, note)
                orderForPriceProposal = null
            }
        )
    }

    // Payment in Reality Confirmation Dialog
    orderForPaymentConfirmation?.let { order ->
        PaymentConfirmationDialog(
            order = order,
            onDismiss = { orderForPaymentConfirmation = null },
            onConfirm = { amount, methodNote ->
                viewModel.confirmPaymentInReality(order.id, amount, methodNote)
                orderForPaymentConfirmation = null
            }
        )
    }

    // Order Chat Bottom Sheet
    orderForChat?.let { order ->
        OrderChatBottomSheet(
            order = order,
            messages = activeChatMessages.filter { it.orderId == order.id },
            activeProfile = activeProfile,
            onDismiss = { orderForChat = null },
            onSendMessage = { text ->
                viewModel.sendChatMessage(order.id, text)
            }
        )
    }

    // Profile Switcher Sheet
    if (showProfileSwitcher) {
        ProfileSwitchBottomSheet(
            profiles = profiles,
            activeProfile = activeProfile,
            onDismiss = { showProfileSwitcher = false },
            onSelectProfile = { id ->
                viewModel.selectProfile(id)
                showProfileSwitcher = false
            },
            onEditProfile = { p ->
                showProfileSwitcher = false
                profileToEdit = p
            },
            onAddNewProfile = { p ->
                viewModel.updateProfile(p)
                showProfileSwitcher = false
            }
        )
    }

    // Profile Editor Dialog
    profileToEdit?.let { profile ->
        ProfileEditDialog(
            profile = profile,
            onDismiss = { profileToEdit = null },
            onSave = { updated ->
                viewModel.updateProfile(updated)
                profileToEdit = null
            }
        )
    }

    // Notifications List Sheet
    if (showNotificationsSheet) {
        NotificationListSheet(
            notifications = notifications,
            onDismiss = { showNotificationsSheet = false },
            onSelectOrder = { orderId ->
                val ord = orders.find { it.id == orderId }
                if (ord != null) {
                    orderForChat = ord
                    showNotificationsSheet = false
                }
            },
            onMarkAllRead = { viewModel.markNotificationsRead() },
            onClearAll = { viewModel.clearNotifications() }
        )
    }

    // P2P Hub & Computer Server Dialog
    if (showP2PHubDialog) {
        P2PHubDialog(
            isServerRunning = isServerRunning,
            serverMessage = serverMessage,
            localIp = localIp,
            isSyncing = isSyncing,
            onStartServer = { viewModel.startServer() },
            onStopServer = { viewModel.stopServer() },
            onSyncWithRemote = { host, port ->
                viewModel.syncWithRemoteHub(host, port) { success, msg -> }
            },
            onDismiss = { showP2PHubDialog = false }
        )
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Семейные заказы $name!",
        modifier = modifier
    )
}
