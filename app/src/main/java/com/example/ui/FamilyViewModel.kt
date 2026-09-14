package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.ChatMessageEntity
import com.example.data.model.NotificationEntity
import com.example.data.model.OrderEntity
import com.example.data.model.OrderStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.repository.FamilyRepository
import com.example.service.InAppAlert
import com.example.service.NotificationHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class OrderFilter(val label: String) {
    ALL("Все"),
    NEEDS_ACTION("Требуют решения"),
    ACTIVE("В работе"),
    COMPLETED("Завершены"),
    REJECTED("Отклонены")
}

class FamilyViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val notificationHelper = NotificationHelper(application)
    val repository = FamilyRepository(application, database, notificationHelper)

    val inAppAlerts = NotificationHelper.inAppAlerts

    val profiles: StateFlow<List<UserProfile>> = repository.profiles
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentProfile: StateFlow<UserProfile?> = repository.currentProfile
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _orderFilter = MutableStateFlow(OrderFilter.ALL)
    val orderFilter: StateFlow<OrderFilter> = _orderFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val allOrders: StateFlow<List<OrderEntity>> = repository.allOrders
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filteredOrders: StateFlow<List<OrderEntity>> = combine(
        allOrders,
        orderFilter,
        searchQuery,
        currentProfile
    ) { orders, filter, query, current ->
        var list = orders
        // Filter by tab
        list = when (filter) {
            OrderFilter.ALL -> list
            OrderFilter.NEEDS_ACTION -> list.filter {
                if (current?.role == UserRole.ADMIN) {
                    it.status == OrderStatus.PENDING_REVIEW || it.status == OrderStatus.ACCEPTED
                } else {
                    it.status == OrderStatus.PRICE_QUOTED && it.userId == current?.id
                }
            }
            OrderFilter.ACTIVE -> list.filter {
                it.status == OrderStatus.ACCEPTED || it.status == OrderStatus.PURCHASED
            }
            OrderFilter.COMPLETED -> list.filter { it.status == OrderStatus.COMPLETED }
            OrderFilter.REJECTED -> list.filter { it.status == OrderStatus.REJECTED }
        }
        // Filter by search query
        if (query.isNotBlank()) {
            list = list.filter {
                it.itemTitle.contains(query, ignoreCase = true) ||
                it.userName.contains(query, ignoreCase = true) ||
                it.targetStore.contains(query, ignoreCase = true) ||
                it.deliveryAddress.contains(query, ignoreCase = true)
            }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedOrderId = MutableStateFlow<String?>(null)
    val selectedOrderId: StateFlow<String?> = _selectedOrderId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedOrder: StateFlow<OrderEntity?> = _selectedOrderId.flatMapLatest { id ->
        if (id != null) database.orderDao().getOrderById(id) else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeChatMessages: StateFlow<List<ChatMessageEntity>> = _selectedOrderId.flatMapLatest { id ->
        repository.getOrderChat(id ?: "general")
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val generalChatMessages: StateFlow<List<ChatMessageEntity>> = repository.getAllChat()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val notifications: StateFlow<List<NotificationEntity>> = currentProfile.flatMapLatest { profile ->
        val userId = profile?.id ?: "user_default"
        val isAdmin = profile?.role == UserRole.ADMIN
        repository.getNotificationsForUser(userId, isAdmin)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val unreadNotificationsCount: StateFlow<Int> = currentProfile.flatMapLatest { profile ->
        val userId = profile?.id ?: "user_default"
        val isAdmin = profile?.role == UserRole.ADMIN
        repository.getUnreadNotificationsCount(userId, isAdmin)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _isP2pRunning = MutableStateFlow(false)
    val isP2pRunning: StateFlow<Boolean> = _isP2pRunning.asStateFlow()

    private val _p2pUrl = MutableStateFlow("")
    val p2pUrl: StateFlow<String> = _p2pUrl.asStateFlow()

    init {
        // Automatically start the P2P Local Web Server for PC connection out of the box
        startP2pServer()
    }

    fun setFilter(filter: OrderFilter) {
        _orderFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectOrder(orderId: String?) {
        _selectedOrderId.value = orderId
    }

    fun switchProfile(profileId: String) {
        viewModelScope.launch {
            repository.selectProfile(profileId)
        }
    }

    fun createProfile(
        name: String,
        role: UserRole,
        emoji: String,
        colorHex: String,
        addresses: List<String>,
        preferences: String
    ) {
        viewModelScope.launch {
            val newProfile = repository.createProfile(name, role, emoji, colorHex, addresses, preferences)
            repository.selectProfile(newProfile.id)
        }
    }

    fun updateProfile(profile: UserProfile) {
        viewModelScope.launch {
            repository.updateProfile(profile)
        }
    }

    fun addSavedAddress(profileId: String, address: String) {
        viewModelScope.launch {
            repository.addSavedAddress(profileId, address)
        }
    }

    fun removeSavedAddress(profileId: String, address: String) {
        viewModelScope.launch {
            repository.removeSavedAddress(profileId, address)
        }
    }

    fun createOrder(
        title: String,
        description: String,
        store: String,
        address: String,
        urgency: String,
        budget: Double?
    ) {
        viewModelScope.launch {
            val order = repository.createOrder(title, description, store, address, urgency, budget)
            _selectedOrderId.value = order.id
        }
    }

    fun quotePrice(orderId: String, price: Double, adminNote: String) {
        viewModelScope.launch {
            repository.quotePrice(orderId, price, adminNote)
        }
    }

    fun respondToQuote(orderId: String, accept: Boolean) {
        viewModelScope.launch {
            repository.respondToQuote(orderId, accept)
        }
    }

    fun markPurchased(orderId: String) {
        viewModelScope.launch {
            repository.markPurchased(orderId)
        }
    }

    fun recordRealityPayment(orderId: String, method: String) {
        viewModelScope.launch {
            repository.recordRealityPayment(orderId, method)
        }
    }

    fun completeOrder(orderId: String) {
        viewModelScope.launch {
            repository.completeOrder(orderId)
        }
    }

    fun deleteOrder(order: OrderEntity) {
        viewModelScope.launch {
            repository.deleteOrder(order)
            if (_selectedOrderId.value == order.id) {
                _selectedOrderId.value = null
            }
        }
    }

    fun sendChatMessage(orderId: String, text: String) {
        viewModelScope.launch {
            repository.sendChatMessage(orderId, text)
        }
    }

    fun markNotificationAsRead(id: String) {
        viewModelScope.launch {
            repository.markNotificationRead(id)
        }
    }

    fun markAllNotificationsAsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsRead()
        }
    }

    fun startP2pServer() {
        val success = repository.p2pServer.start()
        _isP2pRunning.value = success
        _p2pUrl.value = repository.p2pServer.getWebUrl()
    }

    fun stopP2pServer() {
        repository.p2pServer.stop()
        _isP2pRunning.value = false
        _p2pUrl.value = ""
    }

    fun toggleP2pServer() {
        if (_isP2pRunning.value) {
            stopP2pServer()
        } else {
            startP2pServer()
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.p2pServer.stop()
    }
}
