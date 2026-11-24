package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CartItem
import com.example.data.model.CoffeeOrder
import com.example.data.model.MenuCategory
import com.example.data.model.MenuItem
import com.example.data.model.OrderStatus
import com.example.data.model.UserProfile
import com.example.data.remote.BonYazanRepository
import com.example.ui.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class CashierSummary(
    val totalRequestedRevenue: Double = 0.0,
    val collectedCash: Double = 0.0,
    val unpaidActiveAmount: Double = 0.0,
    val todayRevenue: Double = 0.0,
    val todayOrdersCount: Int = 0,
    val groundCoffeeRevenue: Double = 0.0,
    val beveragesRevenue: Double = 0.0,
    val pendingCount: Int = 0,
    val preparingCount: Int = 0,
    val readyCount: Int = 0,
    val completedCount: Int = 0,
)

sealed interface NotificationEvent {
    data class NewOrderForOwner(val order: CoffeeOrder) : NotificationEvent
    data class OrderReadyForCustomer(val order: CoffeeOrder) : NotificationEvent
}

class ShopViewModel(
    private val repository: BonYazanRepository,
    val currentUserId: String,
    val userEmail: String?,
    val userDisplayName: String?,
    isEmailVerified: Boolean,
) : ViewModel() {

    val isOwner: Boolean = BonYazanRepository.isShopOwner(userEmail, isEmailVerified)

    private val _previewCustomerMode = MutableStateFlow(false)
    val previewCustomerMode: StateFlow<Boolean> = _previewCustomerMode.asStateFlow()

    private val _cartQuantities = MutableStateFlow<Map<String, Int>>(emptyMap())

    private val _customerName = MutableStateFlow(userDisplayName.orEmpty())
    val customerName: StateFlow<String> = _customerName.asStateFlow()

    private val _customerPhone = MutableStateFlow("")
    val customerPhone: StateFlow<String> = _customerPhone.asStateFlow()

    private val _orderNotes = MutableStateFlow("")
    val orderNotes: StateFlow<String> = _orderNotes.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _actionMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val actionMessage = _actionMessage.asSharedFlow()

    private val _notificationEvents = MutableSharedFlow<NotificationEvent>(extraBufferCapacity = 8)
    val notificationEvents = _notificationEvents.asSharedFlow()

    // Track seen order states to fire real-time notifications without spamming on initial load
    private var ownerOrdersInitialized = false
    private val seenPendingOrderIds = mutableSetOf<String>()
    private var customerOrdersInitialized = false
    private val seenReadyOrderIds = mutableSetOf<String>()

    val menuState: StateFlow<UiState<List<MenuItem>>> =
        (if (isOwner) repository.observeAllMenuForOwner() else repository.observeAvailableMenu())
            .map<List<MenuItem>, UiState<List<MenuItem>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing menu", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "تعذر تحميل المنيو"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    val ordersState: StateFlow<UiState<List<CoffeeOrder>>> =
        (if (isOwner) repository.observeAllOrdersForOwner() else repository.observeCustomerOrders(currentUserId))
            .map<List<CoffeeOrder>, UiState<List<CoffeeOrder>>> { orders ->
                detectNotificationTransitions(orders)
                UiState.Success(orders)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing orders", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "تعذر تحميل الطلبات"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing profile", firestoreError ?: error)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null,
        )

    val cartItems: StateFlow<List<CartItem>> = combine(menuState, _cartQuantities) { menuUi, qtyMap ->
        val items = (menuUi as? UiState.Success)?.data ?: emptyList()
        items.filter { it.available }
            .mapNotNull { item ->
                val qty = qtyMap[item.id] ?: 0
                if (qty > 0) CartItem(item, qty) else null
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList(),
    )

    val cartTotal: StateFlow<Double> = cartItems
        .map { list -> list.sumOf { it.lineTotal } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = 0.0,
        )

    val cartTotalCount: StateFlow<Int> = cartItems
        .map { list -> list.sumOf { it.quantity } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = 0,
        )

    val cashierSummary: StateFlow<CashierSummary> = ordersState
        .map { state ->
            val orders = (state as? UiState.Success)?.data ?: return@map CashierSummary()
            computeCashierSummary(orders)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CashierSummary(),
        )

    init {
        viewModelScope.launch {
            userProfile.collect { profile ->
                if (profile != null) {
                    if (_customerName.value.isBlank() && profile.displayName.isNotBlank()) {
                        _customerName.value = profile.displayName
                    }
                    if (_customerPhone.value.isBlank() && profile.phone.isNotBlank()) {
                        _customerPhone.value = profile.phone
                    }
                }
            }
        }
    }

    private fun detectNotificationTransitions(orders: List<CoffeeOrder>) {
        if (isOwner) {
            val pending = orders.filter { it.statusEnum == OrderStatus.PENDING }
            if (!ownerOrdersInitialized) {
                ownerOrdersInitialized = true
                pending.forEach { seenPendingOrderIds.add(it.id) }
            } else {
                pending.forEach { order ->
                    if (seenPendingOrderIds.add(order.id)) {
                        _notificationEvents.tryEmit(NotificationEvent.NewOrderForOwner(order))
                    }
                }
            }
        }
        val myReadyOrders = orders.filter {
            it.customerId == currentUserId && it.statusEnum == OrderStatus.READY
        }
        if (!customerOrdersInitialized) {
            customerOrdersInitialized = true
            myReadyOrders.forEach { seenReadyOrderIds.add(it.id) }
        } else {
            myReadyOrders.forEach { order ->
                if (seenReadyOrderIds.add(order.id)) {
                    _notificationEvents.tryEmit(NotificationEvent.OrderReadyForCustomer(order))
                }
            }
        }
    }

    fun setPreviewCustomerMode(enabled: Boolean) {
        if (isOwner) {
            _previewCustomerMode.value = enabled
        }
    }

    fun updateCustomerName(value: String) {
        _customerName.value = value
    }

    fun updateCustomerPhone(value: String) {
        _customerPhone.value = value
    }

    fun updateOrderNotes(value: String) {
        _orderNotes.value = value
    }

    fun incrementCartItem(itemId: String) {
        _cartQuantities.update { current ->
            val next = (current[itemId] ?: 0) + 1
            current + (itemId to next.coerceAtMost(50))
        }
    }

    fun decrementCartItem(itemId: String) {
        _cartQuantities.update { current ->
            val currentQty = current[itemId] ?: 0
            if (currentQty <= 1) current - itemId else current + (itemId to (currentQty - 1))
        }
    }

    fun clearCart() {
        _cartQuantities.value = emptyMap()
    }

    fun placeOrder(onSuccess: (String) -> Unit) {
        val currentCart = cartItems.value
        val name = _customerName.value.trim()
        val phone = _customerPhone.value.trim()
        val notes = _orderNotes.value.trim()

        if (currentCart.isEmpty()) {
            _actionMessage.tryEmit("أضف أصنافاً للسلة أولاً")
            return
        }
        if (name.length < 2) {
            _actionMessage.tryEmit("يرجى كتابة اسمك بالكامل")
            return
        }
        if (phone.length < 7) {
            _actionMessage.tryEmit("يرجى كتابة رقم هاتف صحيح للتواصل")
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.placeOrder(
                customerName = name,
                customerPhone = phone,
                notes = notes,
                cartItems = currentCart,
            )
            _isSubmitting.value = false
            result.onSuccess { orderNumber ->
                _cartQuantities.value = emptyMap()
                _orderNotes.value = ""
                _actionMessage.tryEmit("تم إرسال طلبك ($orderNumber) بنجاح!")
                onSuccess(orderNumber)
            }.onFailure { error ->
                _actionMessage.tryEmit(error.localizedMessage ?: "تعذر إرسال الطلب")
            }
        }
    }

    fun cancelOrder(orderId: String) {
        viewModelScope.launch {
            val result = repository.cancelPendingOrder(orderId)
            result.onSuccess {
                _actionMessage.tryEmit("تم إلغاء الطلب")
            }.onFailure { error ->
                _actionMessage.tryEmit(error.localizedMessage ?: "تعذر إلغاء الطلب")
            }
        }
    }

    fun updateOrderStatus(orderId: String, status: OrderStatus, paid: Boolean) {
        viewModelScope.launch {
            val result = repository.updateOrderStatusAndPayment(orderId, status, paid)
            result.onSuccess {
                val msg = when (status) {
                    OrderStatus.PREPARING -> "الطلب قيد التجهيز الآن"
                    OrderStatus.READY -> "تم تسجيل الطلب جاهز للاستلام وإرسال تنبيه للعميل"
                    OrderStatus.COMPLETED -> if (paid) "تم التسليم وتحصيل المبلغ بالكاشير" else "تم تسجيل التسليم"
                    OrderStatus.CANCELLED -> "تم إلغاء الطلب"
                    OrderStatus.PENDING -> "تمت إعادة الطلب لقائمة الانتظار"
                }
                _actionMessage.tryEmit(msg)
            }.onFailure { error ->
                _actionMessage.tryEmit(error.localizedMessage ?: "فشل تحديث حالة الطلب")
            }
        }
    }

    fun saveMenuItem(
        itemId: String?,
        name: String,
        description: String,
        category: MenuCategory,
        unitLabel: String,
        priceText: String,
        available: Boolean,
        sortOrder: Int,
        onSaved: () -> Unit,
    ) {
        val price = priceText.trim().toDoubleOrNull()
        if (name.isBlank()) {
            _actionMessage.tryEmit("أدخل اسم الصنف")
            return
        }
        if (unitLabel.isBlank()) {
            _actionMessage.tryEmit("أدخل الحجم أو الوزن (مثل: ربع كيلو أو كوب)")
            return
        }
        if (price == null || price <= 0.0) {
            _actionMessage.tryEmit("أدخل سعراً صحيحاً أكبر من 0")
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.saveMenuItem(
                itemId = itemId,
                name = name,
                description = description,
                category = category,
                unitLabel = unitLabel,
                price = price,
                available = available,
                sortOrder = sortOrder,
            )
            _isSubmitting.value = false
            result.onSuccess {
                _actionMessage.tryEmit(if (itemId == null) "تمت إضافة الصنف للمنيو" else "تم تحديث الصنف")
                onSaved()
            }.onFailure { error ->
                _actionMessage.tryEmit(error.localizedMessage ?: "تعذر حفظ الصنف")
            }
        }
    }

    fun toggleMenuAvailability(itemId: String, available: Boolean) {
        viewModelScope.launch {
            repository.toggleMenuAvailability(itemId, available)
                .onFailure { error ->
                    _actionMessage.tryEmit(error.localizedMessage ?: "تعذر تحديث التوفر")
                }
        }
    }

    fun deleteMenuItem(itemId: String) {
        viewModelScope.launch {
            repository.deleteMenuItem(itemId)
                .onSuccess { _actionMessage.tryEmit("تم حذف الصنف من المنيو") }
                .onFailure { error ->
                    _actionMessage.tryEmit(error.localizedMessage ?: "تعذر حذف الصنف")
                }
        }
    }

    fun seedStarterMenu() {
        viewModelScope.launch {
            _isSubmitting.value = true
            repository.seedStarterMenu()
                .onSuccess { _actionMessage.tryEmit("تمت إضافة منيو بن يزن المبدئي بنجاح") }
                .onFailure { error ->
                    _actionMessage.tryEmit(error.localizedMessage ?: "تعذر إضافة الأصناف")
                }
            _isSubmitting.value = false
        }
    }

    private fun computeCashierSummary(orders: List<CoffeeOrder>): CashierSummary {
        val activeOrders = orders.filter { it.statusEnum != OrderStatus.CANCELLED }
        val startOfTodayMillis = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todayOrders = activeOrders.filter { order ->
            val orderMillis = (order.createdAt?.seconds ?: 0L) * 1000L
            orderMillis >= startOfTodayMillis
        }

        var groundTotal = 0.0
        var beverageTotal = 0.0
        activeOrders.forEach { order ->
            order.items.forEach { item ->
                when (item.categoryEnum) {
                    MenuCategory.GROUND_COFFEE -> groundTotal += item.subtotal
                    MenuCategory.BEVERAGE -> beverageTotal += item.subtotal
                }
            }
        }

        return CashierSummary(
            totalRequestedRevenue = activeOrders.sumOf { it.totalAmount },
            collectedCash = activeOrders.filter { it.paid }.sumOf { it.totalAmount },
            unpaidActiveAmount = activeOrders.filter { !it.paid }.sumOf { it.totalAmount },
            todayRevenue = todayOrders.sumOf { it.totalAmount },
            todayOrdersCount = todayOrders.size,
            groundCoffeeRevenue = groundTotal,
            beveragesRevenue = beverageTotal,
            pendingCount = orders.count { it.statusEnum == OrderStatus.PENDING },
            preparingCount = orders.count { it.statusEnum == OrderStatus.PREPARING },
            readyCount = orders.count { it.statusEnum == OrderStatus.READY },
            completedCount = orders.count { it.statusEnum == OrderStatus.COMPLETED },
        )
    }

    private companion object {
        const val TAG = "ShopViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
