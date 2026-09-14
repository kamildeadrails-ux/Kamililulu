package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.FamilyRepository
import com.example.model.ChatMessage
import com.example.model.NotificationItem
import com.example.model.Order
import com.example.model.OrderStatus
import com.example.model.ProfileRole
import com.example.model.UserProfile
import com.example.network.NetworkUtils
import com.example.network.P2PClient
import com.example.network.P2PServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    val repository = FamilyRepository(application, database, viewModelScope)

    val profiles: StateFlow<List<UserProfile>> = repository.getAllProfilesFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val orders: StateFlow<List<Order>> = repository.getAllOrdersFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val notifications: StateFlow<List<NotificationItem>> = repository.getNotificationsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val unreadNotificationsCount: StateFlow<Int> = notifications.combine(MutableStateFlow(0)) { list, _ ->
        list.count { !it.isRead }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val activeProfileId = repository.currentProfileId

    val isRegistered: StateFlow<Boolean> = activeProfileId.combine(profiles) { id, profs ->
        id != null && profs.any { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val activeProfile: StateFlow<UserProfile> = combine(profiles, activeProfileId) { profList, id ->
        profList.find { it.id == id } ?: profList.firstOrNull() ?: UserProfile(
            id = "kamil_admin",
            name = "Камиль (Админ)",
            role = ProfileRole.ADMIN,
            avatarEmoji = "👑"
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UserProfile("kamil_admin", "Камиль (Админ)", ProfileRole.ADMIN, "👑"))

    // Active order selected for chat dialog/sheet
    private val _selectedChatOrderId = MutableStateFlow<String?>(null)
    val selectedChatOrderId = _selectedChatOrderId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeChatMessages: StateFlow<List<ChatMessage>> = _selectedChatOrderId.flatMapLatest { orderId ->
        if (orderId != null) {
            repository.getMessagesForOrderFlow(orderId)
        } else {
            repository.getAllMessagesFlow()
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // P2P & Server State
    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning = _isServerRunning.asStateFlow()

    private val _serverStatusMessage = MutableStateFlow("Сервер остановлен")
    val serverStatusMessage = _serverStatusMessage.asStateFlow()

    private val _localIp = MutableStateFlow(NetworkUtils.getLocalIpAddress())
    val localIp = _localIp.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val p2pClient = P2PClient()
    private var p2pServer: P2PServer? = null

    init {
        // Auto-start local P2P server so PC / other devices can connect immediately
        startServer()
    }

    fun startServer() {
        if (_isServerRunning.value) return
        _localIp.value = NetworkUtils.getLocalIpAddress()

        p2pServer = P2PServer(
            port = 8888,
            scope = viewModelScope,
            stateProvider = {
                Triple(orders.value, profiles.value, activeChatMessages.value)
            },
            onOrderCreated = { order ->
                viewModelScope.launch {
                    val user = profiles.value.find { it.id == order.userId } ?: activeProfile.value
                    repository.createOrder(
                        title = order.title,
                        description = order.description,
                        storeOrPlace = order.targetStoreOrPlace,
                        deliveryAddress = order.deliveryAddress,
                        urgency = order.urgency,
                        user = user
                    )
                }
            },
            onPriceProposed = { orderId, price, note ->
                viewModelScope.launch {
                    repository.proposePrice(orderId, price, note, "Админ (Сеть)")
                }
            },
            onDecisionMade = { orderId, accept ->
                viewModelScope.launch {
                    repository.respondToPrice(orderId, accept, "Покупатель (Сеть)")
                }
            },
            onStatusChanged = { orderId, status, isPaid ->
                viewModelScope.launch {
                    repository.updateOrderStatus(orderId, status, isPaid)
                }
            },
            onChatMessageSent = { msg ->
                viewModelScope.launch {
                    val sender = profiles.value.find { it.id == msg.senderId } ?: UserProfile(
                        id = msg.senderId,
                        name = msg.senderName,
                        role = msg.senderRole
                    )
                    repository.sendChatMessage(msg.orderId, msg.text, sender)
                }
            }
        )

        p2pServer?.start { success, message ->
            _isServerRunning.value = success
            _serverStatusMessage.value = message
        }
    }

    fun stopServer() {
        p2pServer?.stop()
        _isServerRunning.value = false
        _serverStatusMessage.value = "Сервер остановлен"
    }

    fun syncWithRemoteHub(host: String, port: Int = 8888, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            p2pClient.setHubAddress(host, port)
            val result = p2pClient.syncWithHub()
            result.fold(
                onSuccess = { (remoteOrders, remoteProfiles, remoteMessages) ->
                    repository.saveImportedData(remoteOrders, remoteProfiles, remoteMessages)
                    _isSyncing.value = false
                    onResult(true, "Синхронизировано: ${remoteOrders.size} заказов, ${remoteMessages.size} сообщений")
                },
                onFailure = { err ->
                    _isSyncing.value = false
                    onResult(false, "Ошибка синхронизации: ${err.localizedMessage}")
                }
            )
        }
    }

    fun selectProfile(profileId: String) {
        repository.setCurrentProfile(profileId)
    }

    fun registerBrother(profileId: String) {
        repository.setCurrentProfile(profileId)
    }

    fun unregister() {
        repository.clearRegistration()
    }

    fun updateProfile(profile: UserProfile) {
        viewModelScope.launch {
            repository.updateProfile(profile)
        }
    }

    fun openChatForOrder(orderId: String?) {
        _selectedChatOrderId.value = orderId
    }

    fun createOrder(
        title: String,
        description: String,
        store: String,
        address: String,
        urgency: String
    ) {
        viewModelScope.launch {
            repository.createOrder(
                title = title,
                description = description,
                storeOrPlace = store,
                deliveryAddress = address,
                urgency = urgency,
                user = activeProfile.value
            )
        }
    }

    fun proposePrice(orderId: String, price: Double, note: String) {
        viewModelScope.launch {
            repository.proposePrice(orderId, price, note, activeProfile.value.name)
        }
    }

    fun respondToPrice(orderId: String, accept: Boolean) {
        viewModelScope.launch {
            repository.respondToPrice(orderId, accept, activeProfile.value.name)
        }
    }

    fun updateOrderStatus(orderId: String, status: OrderStatus) {
        viewModelScope.launch {
            repository.updateOrderStatus(orderId, status)
        }
    }

    fun confirmPaymentInReality(orderId: String, amount: Double, methodNote: String) {
        viewModelScope.launch {
            repository.confirmPaymentInReality(orderId, amount, methodNote)
        }
    }

    fun sendChatMessage(orderId: String, text: String) {
        viewModelScope.launch {
            repository.sendChatMessage(orderId, text, activeProfile.value)
        }
    }

    fun markNotificationsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsAsRead()
        }
    }

    fun clearNotifications() {
        viewModelScope.launch {
            repository.clearAllNotifications()
        }
    }

    override fun onCleared() {
        super.onCleared()
        p2pServer?.stop()
    }
}
