package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class OrderStatus(val labelAr: String) {
    PENDING("طلب جديد"),
    PREPARING("جاري التجهيز"),
    READY("جاهز للاستلام"),
    COMPLETED("تم التسليم"),
    CANCELLED("ملغي");

    companion object {
        fun fromRaw(raw: String?): OrderStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

@Keep
@IgnoreExtraProperties
data class OrderLineItem(
    val menuItemId: String = "",
    val name: String = "",
    val category: String = MenuCategory.GROUND_COFFEE.name,
    val unitLabel: String = "",
    val unitPrice: Double = 0.0,
    val quantity: Long = 1L,
    val subtotal: Double = 0.0,
) {
    val categoryEnum: MenuCategory
        get() = MenuCategory.fromRaw(category)

    fun toMap(): Map<String, Any> = mapOf(
        "menuItemId" to menuItemId,
        "name" to name,
        "category" to category,
        "unitLabel" to unitLabel,
        "unitPrice" to unitPrice,
        "quantity" to quantity,
        "subtotal" to subtotal,
    )
}

@Keep
@IgnoreExtraProperties
data class CoffeeOrder(
    @DocumentId val id: String = "",
    val orderNumber: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val customerEmail: String = "",
    val notes: String = "",
    val items: List<OrderLineItem> = emptyList(),
    val totalAmount: Double = 0.0,
    val status: String = OrderStatus.PENDING.name,
    val paid: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val statusEnum: OrderStatus
        get() = OrderStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val phone: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class CartItem(
    val menuItem: MenuItem,
    val quantity: Int,
) {
    val lineTotal: Double
        get() = menuItem.price * quantity
}
