package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CoffeeOrder
import com.example.data.model.OrderStatus
import com.example.ui.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.OrderStatusChip
import com.example.ui.components.formatEgp
import com.example.ui.components.formatOrderTime
import com.example.ui.theme.StatusPendingColor
import com.example.ui.theme.StatusReadyColor

@Composable
fun OwnerOrdersTab(
    ordersUi: UiState<List<CoffeeOrder>>,
    onUpdateStatus: (orderId: String, status: OrderStatus, paid: Boolean) -> Unit,
) {
    var statusFilter by rememberSaveable { mutableStateOf<OrderStatus?>(null) }

    when (ordersUi) {
        UiState.Loading -> LoadingStateBox("جاري تحميل الطلبات الواردة...")
        is UiState.Error -> ErrorStateCard(ordersUi.message)
        is UiState.Success -> {
            val allOrders = ordersUi.data
            val pendingCount = allOrders.count { it.statusEnum == OrderStatus.PENDING }
            val filtered = allOrders.filter {
                statusFilter == null || it.statusEnum == statusFilter
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (pendingCount > 0) {
                    item {
                        Surface(
                            color = StatusPendingColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, StatusPendingColor.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    tint = StatusPendingColor,
                                )
                                Text(
                                    text = "لديك $pendingCount طلب جديد بانتظار التجهيز الآن!",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = StatusPendingColor,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = statusFilter == null,
                            onClick = { statusFilter = null },
                            label = { Text("الكل (${allOrders.size})") },
                        )
                        listOf(
                            OrderStatus.PENDING,
                            OrderStatus.PREPARING,
                            OrderStatus.READY,
                            OrderStatus.COMPLETED,
                            OrderStatus.CANCELLED,
                        ).forEach { st ->
                            val count = allOrders.count { it.statusEnum == st }
                            FilterChip(
                                selected = statusFilter == st,
                                onClick = { statusFilter = if (statusFilter == st) null else st },
                                label = { Text("${st.labelAr} ($count)") },
                            )
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "لا توجد طلبات في هذا القسم",
                            subtitle = "عند قيام أي عميل بالطلب من التطبيق سيصلك إشعار ويظهر الطلب هنا فوراً.",
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { order ->
                        OwnerOrderCard(
                            order = order,
                            onUpdateStatus = { newStatus, paid ->
                                onUpdateStatus(order.id, newStatus, paid)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OwnerOrderCard(
    order: CoffeeOrder,
    onUpdateStatus: (OrderStatus, Boolean) -> Unit,
) {
    val status = order.statusEnum
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = when (status) {
            OrderStatus.PENDING -> BorderStroke(1.5.dp, StatusPendingColor)
            OrderStatus.READY -> BorderStroke(1.5.dp, StatusReadyColor)
            else -> null
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("owner_order_card_${order.id}"),
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
                        text = "طلب ${order.orderNumber}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = formatOrderTime(order.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OrderStatusChip(status = status)
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = order.customerName,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = order.customerPhone,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (order.notes.isNotBlank()) {
                        Text(
                            text = "ملاحظة العميل: ${order.notes}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            order.items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${item.name} (${item.unitLabel}) × ${item.quantity}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatEgp(item.subtotal),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
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
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Surface(
                    color = if (order.paid) {
                        StatusReadyColor.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = if (order.paid) "تم التحصيل" else "غير مدفوع",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (order.paid) StatusReadyColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            // Owner Action Buttons
            when (status) {
                OrderStatus.PENDING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onUpdateStatus(OrderStatus.PREPARING, order.paid) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Coffee, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("بدء التجهيز")
                        }
                        OutlinedButton(
                            onClick = { onUpdateStatus(OrderStatus.CANCELLED, false) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("إلغاء")
                        }
                    }
                }
                OrderStatus.PREPARING -> {
                    Button(
                        onClick = { onUpdateStatus(OrderStatus.READY, order.paid) },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusReadyColor),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("الطلب جاهز للاستلام (إشعار العميل)")
                    }
                }
                OrderStatus.READY -> {
                    Button(
                        onClick = { onUpdateStatus(OrderStatus.COMPLETED, true) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تم التسليم وتحصيل ${formatEgp(order.totalAmount)}")
                    }
                }
                OrderStatus.COMPLETED -> {
                    if (!order.paid) {
                        Button(
                            onClick = { onUpdateStatus(OrderStatus.COMPLETED, true) },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusReadyColor),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تسجيل التحصيل بالكاشير")
                        }
                    }
                }
                OrderStatus.CANCELLED -> Unit
            }
        }
    }
}
