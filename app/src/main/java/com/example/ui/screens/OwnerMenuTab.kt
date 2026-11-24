package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.MenuCategory
import com.example.data.model.MenuItem
import com.example.ui.UiState
import com.example.ui.components.CategoryBadge
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.formatEgp

@Composable
fun OwnerMenuTab(
    menuUi: UiState<List<MenuItem>>,
    isSubmitting: Boolean,
    onSaveItem: (
        itemId: String?,
        name: String,
        description: String,
        category: MenuCategory,
        unitLabel: String,
        priceText: String,
        available: Boolean,
        sortOrder: Int,
        onDone: () -> Unit,
    ) -> Unit,
    onToggleAvailability: (itemId: String, available: Boolean) -> Unit,
    onDeleteItem: (itemId: String) -> Unit,
    onSeedStarterMenu: () -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var categoryFilter by rememberSaveable { mutableStateOf<MenuCategory?>(null) }
    var editingItem by remember { mutableStateOf<MenuItem?>(null) }
    var showEditorDialog by remember { mutableStateOf(false) }
    var deletingItem by remember { mutableStateOf<MenuItem?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("ابحث في المنيو لتعديل السعر أو التوفر...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilterChip(
                        selected = categoryFilter == null,
                        onClick = { categoryFilter = null },
                        label = { Text("الكل") },
                    )
                    MenuCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = categoryFilter == cat,
                            onClick = { categoryFilter = if (categoryFilter == cat) null else cat },
                            label = { Text(cat.labelAr) },
                        )
                    }
                }
            }

            when (menuUi) {
                UiState.Loading -> item { LoadingStateBox("جاري تحميل المنيو...") }
                is UiState.Error -> item { ErrorStateCard(menuUi.message) }
                is UiState.Success -> {
                    val allItems = menuUi.data
                    val filtered = allItems.filter { item ->
                        (categoryFilter == null || item.categoryEnum == categoryFilter) &&
                            (searchQuery.isBlank() ||
                                item.name.contains(searchQuery, ignoreCase = true) ||
                                item.description.contains(searchQuery, ignoreCase = true))
                    }

                    if (allItems.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "المنيو فارغ حالياً",
                                subtitle = "أضف أصناف البن المطحون والمشاريب بالأسعار التي تحددها، أو أضف منيو بن يزن المبدئي لتعديله بسهولة.",
                                actionLabel = "إضافة صنف جديد",
                                onAction = {
                                    editingItem = null
                                    showEditorDialog = true
                                },
                            )
                        }
                        item {
                            OutlinedButton(
                                onClick = onSeedStarterMenu,
                                enabled = !isSubmitting,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("إضافة أصناف بن ومشاريب مقترحة جاهزة")
                            }
                        }
                    } else if (filtered.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "لا توجد نتائج مطابقة",
                                subtitle = "جرب البحث باسم مختلف أو اختر تصنيفاً آخر.",
                            )
                        }
                    } else {
                        items(filtered, key = { it.id }) { item ->
                            OwnerMenuItemCard(
                                item = item,
                                onToggleAvailable = { available ->
                                    onToggleAvailability(item.id, available)
                                },
                                onEdit = {
                                    editingItem = item
                                    showEditorDialog = true
                                },
                                onDelete = { deletingItem = item },
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                editingItem = null
                showEditorDialog = true
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_menu_item_fab"),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "إضافة صنف جديد")
        }
    }

    if (showEditorDialog) {
        val nextSort = ((menuUi as? UiState.Success)?.data?.size ?: 0) + 1
        MenuItemEditorDialog(
            initialItem = editingItem,
            defaultSortOrder = nextSort,
            isSubmitting = isSubmitting,
            onDismiss = { showEditorDialog = false },
            onSave = { name, desc, cat, unit, price, available, sort ->
                onSaveItem(
                    editingItem?.id,
                    name,
                    desc,
                    cat,
                    unit,
                    price,
                    available,
                    sort,
                ) {
                    showEditorDialog = false
                }
            },
        )
    }

    deletingItem?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text("حذف الصنف") },
            text = { Text("هل تريد حذف \"${target.name}\" من المنيو نهائياً؟") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteItem(target.id)
                        deletingItem = null
                    },
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingItem = null }) {
                    Text("إلغاء")
                }
            },
        )
    }
}

@Composable
private fun OwnerMenuItemCard(
    item: MenuItem,
    onToggleAvailable: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.available) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryBadge(item.categoryEnum)
                    Text(
                        text = item.unitLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatEgp(item.price),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }

            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Switch(
                        checked = item.available,
                        onCheckedChange = onToggleAvailable,
                    )
                    Text(
                        text = if (item.available) "متاح للعملاء" else "مخفي مؤقتاً",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "تعديل الصنف",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "حذف الصنف",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItemEditorDialog(
    initialItem: MenuItem?,
    defaultSortOrder: Int,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        description: String,
        category: MenuCategory,
        unitLabel: String,
        priceText: String,
        available: Boolean,
        sortOrder: Int,
    ) -> Unit,
) {
    var name by remember { mutableStateOf(initialItem?.name.orEmpty()) }
    var description by remember { mutableStateOf(initialItem?.description.orEmpty()) }
    var category by remember { mutableStateOf(initialItem?.categoryEnum ?: MenuCategory.GROUND_COFFEE) }
    var unitLabel by remember { mutableStateOf(initialItem?.unitLabel ?: "ربع كيلو (250 جم)") }
    var priceText by remember {
        mutableStateOf(
            if (initialItem != null) {
                if (initialItem.price % 1.0 == 0.0) initialItem.price.toInt().toString()
                else initialItem.price.toString()
            } else ""
        )
    }
    var available by remember { mutableStateOf(initialItem?.available ?: true) }
    val sortOrder = initialItem?.sortOrder ?: defaultSortOrder

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialItem == null) "إضافة صنف جديد" else "تعديل الصنف",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MenuCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = {
                                category = cat
                                if (initialItem == null) {
                                    unitLabel = if (cat == MenuCategory.GROUND_COFFEE) {
                                        "ربع كيلو (250 جم)"
                                    } else {
                                        "كوب"
                                    }
                                }
                            },
                            label = { Text(cat.labelAr) },
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الصنف") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("menu_item_name_input"),
                )

                OutlinedTextField(
                    value = unitLabel,
                    onValueChange = { unitLabel = it },
                    label = { Text("الوزن / الحجم (مثلاً: ربع كيلو، ثمن، كوب)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("السعر (ج.م)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("menu_item_price_input"),
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("وصف مختصر (اختياري)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("متاح للطلب في منيو العميل")
                    Switch(checked = available, onCheckedChange = { available = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(name, description, category, unitLabel, priceText, available, sortOrder)
                },
                enabled = !isSubmitting,
                modifier = Modifier.testTag("save_menu_item_button"),
            ) {
                Text(if (isSubmitting) "جاري الحفظ..." else "حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        },
    )
}
