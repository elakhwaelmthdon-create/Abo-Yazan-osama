package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MenuCategory(val labelAr: String) {
    GROUND_COFFEE("بن مطحون"),
    BEVERAGE("مشاريب");

    companion object {
        fun fromRaw(raw: String?): MenuCategory =
            entries.firstOrNull { it.name == raw } ?: GROUND_COFFEE
    }
}

@Keep
@IgnoreExtraProperties
data class MenuItem(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = MenuCategory.GROUND_COFFEE.name,
    val unitLabel: String = "",
    val price: Double = 0.0,
    val available: Boolean = true,
    val sortOrder: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val categoryEnum: MenuCategory
        get() = MenuCategory.fromRaw(category)
}
