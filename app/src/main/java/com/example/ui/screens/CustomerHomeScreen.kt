package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.CartItem
import com.example.data.model.CoffeeOrder
import com.example.data.model.MenuCategory
import com.example.data.model.MenuItem
import com.example.data.model.OrderStatus
import com.example.ui.UiState
import com.example.ui.components.BrandTopHeader
import com.example.ui.components.CategoryBadge
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.OrderStatusChip
import com.example.ui.components.formatEgp
import com.example.ui.components.formatOrderTime
import com.example.ui.theme.StatusReadyColor
import com.example.ui.viewmodel.ShopViewModel

private enum class CustomerTab(val index: Int) {
    MENU(0),
    CART(1),
    ORDERS(2),
}

@Composable
fun CustomerHomeScreen(
    viewModel: ShopViewModel,
    snackbarHostState: SnackbarHostState,
    isOwnerPreview: Boolean,
    onExitOwnerPreview: () -> Unit,
    onSignOut: () -> Unit,
) {
    val menuUi by viewModel.menuState.collectAsStateWithLifecycle()
    val ordersUi by viewModel.ordersState.collectAsStateWithLifecycle()
    val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()
    val cartTotal by viewModel.cartTotal.collectAsStateWithLifecycle()
    val cartCount by viewModel.cartTotalCount.collectAsStateWithLifecycle()
    val customerName by viewModel.customerName.collectAsStateWithLifecycle()
    val customerPhone by viewModel.customerPhone.collectAsStateWithLifecycle()
    val orderNotes by viewModel.orderNotes.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(CustomerTab.MENU.index) }

    BackHandler(enabled = selectedTab != CustomerTab.MENU.index) {
        selectedTab = CustomerTab.MENU.index
    }

    val quantitiesMap = remember(cartItems) {
        cartItems.associate { it.menuItem.id to it.quantity }
    }

    val customerOrders = remember(ordersUi, viewModel.currentUserId) {
        when (val s = ordersUi) {
            is UiState.Success -> UiState.Success(
                s.data.filter { it.customerId == viewModel.currentUserId }
            )
            is UiState.Error -> s
            UiState.Loading -> UiState.Loading
        }
    }

    val readyOrdersCount = remember(customerOrders) {
        (customerOrders as? UiState.Success)?.data?.count { it.statusEnum == OrderStatus.READY } ?: 0
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                BrandTopHeader(
                    subtitle = if (isOwnerPreview) "معاينة واجهة العميل" else "مطحنة بن ومشاريب طازجة",
                    trailingAction = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (isOwnerPreview) {
                                TextButton(onClick = onExitOwnerPreview) {
                                    Text("لوحة المالك")
                                }
                            } else {
                                IconButton(
                                    onClick = onSignOut,
                                    modifier = Modifier.testTag("customer_sign_out_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                                        contentDescription = stringResource(id = R.string.sign_out),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    },
                )
            },
            bottomBar = {
                if (!useRail) {
                    Column {
                        AnimatedVisibility(
                            visible = selectedTab == CustomerTab.MENU.index && cartCount > 0,
                        ) {
                            CartStickyBanner(
                                count = cartCount,
                                total = cartTotal,
                                onOpenCart = { selectedTab = CustomerTab.CART.index },
                            )
                        }
                        CustomerNavigationBar(
                            selectedTab = selectedTab,
                            cartCount = cartCount,
                            readyOrdersCount = readyOrdersCount,
                            onSelectTab = { selectedTab = it },
                        )
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useRail) {
                    CustomerNavigationRail(
                        selectedTab = selectedTab,
                        cartCount = cartCount,
                        readyOrdersCount = readyOrdersCount,
                        onSelectTab = { selectedTab = it },
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 760.dp)
                            .fillMaxSize(),
                    ) {
                        when (selectedTab) {
                            CustomerTab.MENU.index -> CustomerMenuTab(
                                menuUi = menuUi,
                                quantitiesMap = quantitiesMap,
                                onAdd = viewModel::incrementCartItem,
                                onRemove = viewModel::decrementCartItem,
                                showBannerInline = useRail && cartCount > 0,
                                cartCount = cartCount,
                                cartTotal = cartTotal,
                                onOpenCart = { selectedTab = CustomerTab.CART.index },
                            )
                            CustomerTab.CART.index -> CustomerCartTab(
                                cartItems = cartItems,
                                cartTotal = cartTotal,
                                customerName = customerName,
                                customerPhone = customerPhone,
                                orderNotes = orderNotes,
                                isSubmitting = isSubmitting,
                                onNameChange = viewModel::updateCustomerName,
                                onPhoneChange = viewModel::updateCustomerPhone,
                                onNotesChange = viewModel::updateOrderNotes,
                                onAdd = viewModel::incrementCartItem,
                                onRemove = viewModel::decrementCartItem,
                                onClearCart = viewModel::clearCart,
                                onSubmitOrder = {
                                    viewModel.placeOrder {
                                        selectedTab = CustomerTab.ORDERS.index
                                    }
                                },
                                onBrowseMenu = { selectedTab = CustomerTab.MENU.index },
                            )
                            else -> CustomerOrdersTab(
                                ordersUi = customerOrders,
                                onCancelOrder = viewModel::cancelOrder,
                                onBrowseMenu = { selectedTab = CustomerTab.MENU.index },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerNavigationBar(
    selectedTab: Int,
    cartCount: Int,
    readyOrdersCount: Int,
    onSelectTab: (Int) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = selectedTab == CustomerTab.MENU.index,
            onClick = { onSelectTab(CustomerTab.MENU.index) },
            icon = { Icon(Icons.Rounded.Coffee, contentDescription = null) },
            label = { Text(stringResource(id = R.string.tab_menu)) },
        )
        NavigationBarItem(
            selected = selectedTab == CustomerTab.CART.index,
            onClick = { onSelectTab(CustomerTab.CART.index) },
            icon = {
                BadgedBox(
                    badge = {
                        if (cartCount > 0) {
                            Badge { Text(cartCount.toString()) }
                        }
                    },
                ) {
                    Icon(Icons.Rounded.ShoppingBag, contentDescription = null)
                }
            },
            label = { Text(stringResource(id = R.string.tab_cart)) },
        )
        NavigationBarItem(
            selected = selectedTab == CustomerTab.ORDERS.index,
            onClick = { onSelectTab(CustomerTab.ORDERS.index) },
            icon = {
                BadgedBox(
                    badge = {
                        if (readyOrdersCount > 0) {
                            Badge(containerColor = StatusReadyColor) {
                                Text(readyOrdersCount.toString())
                            }
                        }
                    },
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null)
                }
            },
            label = { Text(stringResource(id = R.string.tab_my_orders)) },
        )
    }
}

@Composable
private fun CustomerNavigationRail(
    selectedTab: Int,
    cartCount: Int,
    readyOrdersCount: Int,
    onSelectTab: (Int) -> Unit,
) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
        Spacer(modifier = Modifier.height(12.dp))
        NavigationRailItem(
            selected = selectedTab == CustomerTab.MENU.index,
            onClick = { onSelectTab(CustomerTab.MENU.index) },
            icon = { Icon(Icons.Rounded.Coffee, contentDescription = null) },
            label = { Text(stringResource(id = R.string.tab_menu)) },
        )
        NavigationRailItem(
            selected = selectedTab == CustomerTab.CART.index,
            onClick = { onSelectTab(CustomerTab.CART.index) },
            icon = {
                BadgedBox(
                    badge = {
                        if (cartCount > 0) Badge { Text(cartCount.toString()) }
                    },
                ) {
                    Icon(Icons.Rounded.ShoppingBag, contentDescription = null)
                }
            },
            label = { Text(stringResource(id = R.string.tab_cart)) },
        )
        NavigationRailItem(
            selected = selectedTab == CustomerTab.ORDERS.index,
            onClick = { onSelectTab(CustomerTab.ORDERS.index) },
            icon = {
                BadgedBox(
                    badge = {
                        if (readyOrdersCount > 0) {
                            Badge(containerColor = StatusReadyColor) { Text(readyOrdersCount.toString()) }
                        }
                    },
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null)
                }
            },
            label = { Text(stringResource(id = R.string.tab_my_orders)) },
        )
    }
}

@Composable
private fun CartStickyBanner(
    count: Int,
    total: Double,
    onOpenCart: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        onClick = onOpenCart,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiary,
                    shape = CircleShape,
                    modifier = Modifier.size(30.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiary,
                        )
                    }
                }
                Text(
                    text = "مراجعة السلة وتأكيد الطلب",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(
                text = formatEgp(total),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CustomerMenuTab(
    menuUi: UiState<List<MenuItem>>,
    quantitiesMap: Map<String, Int>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    showBannerInline: Boolean,
    cartCount: Int,
    cartTotal: Double,
    onOpenCart: () -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<MenuCategory?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary,
                                )
                            )
                        )
                        .padding(18.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "اطلب قهوتك الطازجة من بن يزن",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text(
                            text = "اختر البن المطحون أو المشروب الساخن/المثلج، وسيتم تحضيره وإشعارك فور جاهزيته للاستلام.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f),
                        )
                    }
                }
            }
        }

        if (showBannerInline) {
            item {
                CartStickyBanner(
                    count = cartCount,
                    total = cartTotal,
                    onOpenCart = onOpenCart,
                )
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("ابحث عن نوع البن أو المشروب...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("customer_menu_search"),
            )
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("الكل") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
                MenuCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                        label = { Text(cat.labelAr) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        }

        when (menuUi) {
            UiState.Loading -> {
                item { LoadingStateBox("جاري تحميل منيو بن يزن...") }
            }
            is UiState.Error -> {
                item { ErrorStateCard(menuUi.message) }
            }
            is UiState.Success -> {
                val filtered = menuUi.data.filter { item ->
                    item.available &&
                        (selectedCategory == null || item.categoryEnum == selectedCategory) &&
                        (searchQuery.isBlank() ||
                            item.name.contains(searchQuery, ignoreCase = true) ||
                            item.description.contains(searchQuery, ignoreCase = true))
                }

                if (filtered.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "لا توجد أصناف متاحة حالياً",
                            subtitle = "المنيو قيد التحديث الآن، يرجى المحاولة بعد قليل.",
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { item ->
                        CustomerMenuItemCard(
                            item = item,
                            quantity = quantitiesMap[item.id] ?: 0,
                            onAdd = { onAdd(item.id) },
                            onRemove = { onRemove(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerMenuItemCard(
    item: MenuItem,
    quantity: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        border = if (quantity > 0) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary)
        } else null,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("menu_item_card_${item.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryBadge(item.categoryEnum)
                    Text(
                        text = item.unitLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = formatEgp(item.price),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (quantity == 0) {
                Button(
                    onClick = onAdd,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("add_item_button_${item.id}"),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إضافة")
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(4.dp),
                    ) {
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Remove,
                                contentDescription = "إنقاص الكمية",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = quantity.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                        IconButton(
                            onClick = onAdd,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "زيادة الكمية",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerCartTab(
    cartItems: List<CartItem>,
    cartTotal: Double,
    customerName: String,
    customerPhone: String,
    orderNotes: String,
    isSubmitting: Boolean,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearCart: () -> Unit,
    onSubmitOrder: () -> Unit,
    onBrowseMenu: () -> Unit,
) {
    if (cartItems.isEmpty()) {
        EmptyStateCard(
            title = "سلتك فارغة حالياً",
            subtitle = "اختر البن المطحون أو المشروب المفضل لديك من المنيو لإرسال طلبك لمحل بن يزن.",
            actionLabel = "تصفح المنيو",
            onAction = onBrowseMenu,
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "مراجعة الطلب",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                TextButton(onClick = onClearCart) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إفراغ السلة")
                }
            }
        }

        items(cartItems, key = { it.menuItem.id }) { cartItem ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = cartItem.menuItem.name,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "${cartItem.menuItem.unitLabel} • ${formatEgp(cartItem.menuItem.price)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "المجموع: ${formatEgp(cartItem.lineTotal)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onRemove(cartItem.menuItem.id) }) {
                            Icon(Icons.Rounded.Remove, contentDescription = "إنقاص")
                        }
                        Text(
                            text = cartItem.quantity.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        IconButton(onClick = { onAdd(cartItem.menuItem.id) }) {
                            Icon(Icons.Rounded.Add, contentDescription = "زيادة")
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "بيانات الاستلام",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = onNameChange,
                        label = { Text("الاسم بالكامل") },
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("customer_name_input"),
                    )
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = onPhoneChange,
                        label = { Text("رقم الموبايل") },
                        leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("customer_phone_input"),
                    )
                    OutlinedTextField(
                        value = orderNotes,
                        onValueChange = onNotesChange,
                        label = { Text("ملاحظات (درجة التحميص، السكر، الحبهان...)") },
                        maxLines = 3,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("customer_notes_input"),
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "الإجمالي المطلوب عند الاستلام",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = formatEgp(cartTotal),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        item {
            Button(
                onClick = onSubmitOrder,
                enabled = !isSubmitting,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("submit_order_button"),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("جاري إرسال الطلب...")
                } else {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تأكيد وإرسال الطلب (${formatEgp(cartTotal)})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomerOrdersTab(
    ordersUi: UiState<List<CoffeeOrder>>,
    onCancelOrder: (String) -> Unit,
    onBrowseMenu: () -> Unit,
) {
    when (ordersUi) {
        UiState.Loading -> LoadingStateBox("جاري تحميل طلباتك...")
        is UiState.Error -> ErrorStateCard(ordersUi.message)
        is UiState.Success -> {
            if (ordersUi.data.isEmpty()) {
                EmptyStateCard(
                    title = "لا توجد طلبات سابقة",
                    subtitle = "اطلب قهوتك أو مشروبك الآن وتابع حالة تجهيزه من هنا خطوة بخطوة.",
                    actionLabel = "اطلب الآن",
                    onAction = onBrowseMenu,
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(ordersUi.data, key = { it.id }) { order ->
                        CustomerOrderCard(
                            order = order,
                            onCancelOrder = { onCancelOrder(order.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerOrderCard(
    order: CoffeeOrder,
    onCancelOrder: () -> Unit,
) {
    val status = order.statusEnum
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (status == OrderStatus.READY) {
            BorderStroke(2.dp, StatusReadyColor)
        } else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "طلب رقم ${order.orderNumber}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = formatOrderTime(order.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OrderStatusChip(status = status)
            }

            if (status == OrderStatus.READY) {
                Surface(
                    color = StatusReadyColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = StatusReadyColor,
                        )
                        Text(
                            text = "طلبك جاهز الآن! يمكنك التوجه لمحل بن يزن للاستلام.",
                            style = MaterialTheme.typography.labelLarge,
                            color = StatusReadyColor,
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            order.items.forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${line.name} (${line.unitLabel}) × ${line.quantity}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatEgp(line.subtotal),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (order.notes.isNotBlank()) {
                Text(
                    text = "ملاحظات: ${order.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "الإجمالي: ${formatEgp(order.totalAmount)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                if (order.paid) {
                    Surface(
                        color = StatusReadyColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = "تم الدفع",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusReadyColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (status == OrderStatus.PENDING) {
                OutlinedButton(
                    onClick = onCancelOrder,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("إلغاء الطلب")
                }
            }
        }
    }
}
