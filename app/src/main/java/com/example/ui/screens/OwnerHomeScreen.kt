package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.OrderStatus
import com.example.ui.UiState
import com.example.ui.components.BrandTopHeader
import com.example.ui.components.QrCodePosterDialog
import com.example.ui.theme.StatusPendingColor
import com.example.ui.viewmodel.ShopViewModel
import kotlinx.coroutines.launch

private enum class OwnerTab(val index: Int) {
    ORDERS(0),
    MENU(1),
    CASHIER(2),
}

@Composable
fun OwnerHomeScreen(
    viewModel: ShopViewModel,
    snackbarHostState: SnackbarHostState,
    onPreviewCustomer: () -> Unit,
    onSignOut: () -> Unit,
) {
    val menuUi by viewModel.menuState.collectAsStateWithLifecycle()
    val ordersUi by viewModel.ordersState.collectAsStateWithLifecycle()
    val cashierSummary by viewModel.cashierSummary.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(OwnerTab.ORDERS.index) }
    var showQrDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = selectedTab != OwnerTab.ORDERS.index) {
        selectedTab = OwnerTab.ORDERS.index
    }

    val pendingCount = (ordersUi as? UiState.Success)?.data?.count {
        it.statusEnum == OrderStatus.PENDING
    } ?: 0

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                BrandTopHeader(
                    subtitle = "لوحة تحكم المالك والكاشير",
                    trailingAction = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            IconButton(
                                onClick = { showQrDialog = true },
                                modifier = Modifier.testTag("owner_qr_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.QrCode2,
                                    contentDescription = "باركود تحميل التطبيق",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(
                                onClick = onPreviewCustomer,
                                modifier = Modifier.testTag("preview_customer_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Visibility,
                                    contentDescription = "معاينة وضع العميل",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(
                                onClick = onSignOut,
                                modifier = Modifier.testTag("owner_sign_out_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                                    contentDescription = stringResource(id = R.string.sign_out),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                )
            },
            bottomBar = {
                if (!useRail) {
                    OwnerNavigationBar(
                        selectedTab = selectedTab,
                        pendingCount = pendingCount,
                        onSelectTab = { selectedTab = it },
                    )
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useRail) {
                    OwnerNavigationRail(
                        selectedTab = selectedTab,
                        pendingCount = pendingCount,
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
                            .widthIn(max = 820.dp)
                            .fillMaxSize(),
                    ) {
                        when (selectedTab) {
                            OwnerTab.ORDERS.index -> OwnerOrdersTab(
                                ordersUi = ordersUi,
                                onUpdateStatus = viewModel::updateOrderStatus,
                            )
                            OwnerTab.MENU.index -> OwnerMenuTab(
                                menuUi = menuUi,
                                isSubmitting = isSubmitting,
                                onSaveItem = viewModel::saveMenuItem,
                                onToggleAvailability = viewModel::toggleMenuAvailability,
                                onDeleteItem = viewModel::deleteMenuItem,
                                onSeedStarterMenu = viewModel::seedStarterMenu,
                            )
                            else -> OwnerCashierTab(
                                summary = cashierSummary,
                                ordersUi = ordersUi,
                                onMarkPaidAndCompleted = { order ->
                                    val nextStatus = if (order.statusEnum == OrderStatus.READY) {
                                        OrderStatus.COMPLETED
                                    } else {
                                        order.statusEnum
                                    }
                                    viewModel.updateOrderStatus(order.id, nextStatus, true)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showQrDialog) {
        QrCodePosterDialog(
            onDismiss = { showQrDialog = false },
            onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
        )
    }
}

@Composable
private fun OwnerNavigationBar(
    selectedTab: Int,
    pendingCount: Int,
    onSelectTab: (Int) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = selectedTab == OwnerTab.ORDERS.index,
            onClick = { onSelectTab(OwnerTab.ORDERS.index) },
            icon = {
                BadgedBox(
                    badge = {
                        if (pendingCount > 0) {
                            Badge(containerColor = StatusPendingColor) {
                                Text(pendingCount.toString())
                            }
                        }
                    },
                ) {
                    Icon(Icons.Rounded.NotificationsActive, contentDescription = null)
                }
            },
            label = { Text(stringResource(id = R.string.tab_owner_orders)) },
        )
        NavigationBarItem(
            selected = selectedTab == OwnerTab.MENU.index,
            onClick = { onSelectTab(OwnerTab.MENU.index) },
            icon = { Icon(Icons.Rounded.RestaurantMenu, contentDescription = null) },
            label = { Text(stringResource(id = R.string.tab_owner_menu)) },
        )
        NavigationBarItem(
            selected = selectedTab == OwnerTab.CASHIER.index,
            onClick = { onSelectTab(OwnerTab.CASHIER.index) },
            icon = { Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null) },
            label = { Text(stringResource(id = R.string.tab_owner_cashier)) },
        )
    }
}

@Composable
private fun OwnerNavigationRail(
    selectedTab: Int,
    pendingCount: Int,
    onSelectTab: (Int) -> Unit,
) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
        Spacer(modifier = Modifier.height(12.dp))
        NavigationRailItem(
            selected = selectedTab == OwnerTab.ORDERS.index,
            onClick = { onSelectTab(OwnerTab.ORDERS.index) },
            icon = {
                BadgedBox(
                    badge = {
                        if (pendingCount > 0) {
                            Badge(containerColor = StatusPendingColor) {
                                Text(pendingCount.toString())
                            }
                        }
                    },
                ) {
                    Icon(Icons.Rounded.NotificationsActive, contentDescription = null)
                }
            },
            label = { Text(stringResource(id = R.string.tab_owner_orders)) },
        )
        NavigationRailItem(
            selected = selectedTab == OwnerTab.MENU.index,
            onClick = { onSelectTab(OwnerTab.MENU.index) },
            icon = { Icon(Icons.Rounded.RestaurantMenu, contentDescription = null) },
            label = { Text(stringResource(id = R.string.tab_owner_menu)) },
        )
        NavigationRailItem(
            selected = selectedTab == OwnerTab.CASHIER.index,
            onClick = { onSelectTab(OwnerTab.CASHIER.index) },
            icon = { Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null) },
            label = { Text(stringResource(id = R.string.tab_owner_cashier)) },
        )
    }
}
