package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.model.CartItem
import com.example.data.model.CoffeeOrder
import com.example.data.model.MenuCategory
import com.example.data.model.MenuItem
import com.example.data.model.OrderLineItem
import com.example.data.model.OrderStatus
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class BonYazanRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        const val OWNER_EMAIL = "elakhwaelmthdon@gmail.com"
        private const val COLLECTION_MENU = "menu_items"
        private const val COLLECTION_ORDERS = "orders"
        private const val COLLECTION_USERS = "users"

        fun isShopOwner(email: String?, isEmailVerified: Boolean): Boolean =
            isEmailVerified && email?.equals(OWNER_EMAIL, ignoreCase = true) == true
    }

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("يجب تسجيل الدخول بحساب Google أولاً")

    fun observeAvailableMenu(): Flow<List<MenuItem>> = flow {
        requireUserId()
        emitAll(
            db.collection(COLLECTION_MENU)
                .whereEqualTo("available", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MenuItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(compareBy<MenuItem> { it.sortOrder }.thenBy { it.name })
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser != null) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, COLLECTION_MENU)
                    }
                    throw error
                }
        )
    }

    fun observeAllMenuForOwner(): Flow<List<MenuItem>> = flow {
        requireUserId()
        emitAll(
            db.collection(COLLECTION_MENU)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MenuItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(compareBy<MenuItem> { it.sortOrder }.thenBy { it.name })
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser != null) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, COLLECTION_MENU)
                    }
                    throw error
                }
        )
    }

    fun observeCustomerOrders(customerId: String): Flow<List<CoffeeOrder>> = flow {
        val uid = requireUserId()
        check(uid == customerId) { "User ID mismatch while observing customer orders" }
        emitAll(
            db.collection(COLLECTION_ORDERS)
                .whereEqualTo("customerId", customerId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CoffeeOrder::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, COLLECTION_ORDERS)
                    }
                    throw error
                }
        )
    }

    fun observeAllOrdersForOwner(): Flow<List<CoffeeOrder>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection(COLLECTION_ORDERS)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CoffeeOrder::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, COLLECTION_ORDERS)
                    }
                    throw error
                }
        )
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "User ID mismatch while observing user profile" }
        val docRef = db.collection(COLLECTION_USERS).document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveMenuItem(
        itemId: String?,
        name: String,
        description: String,
        category: MenuCategory,
        unitLabel: String,
        price: Double,
        available: Boolean,
        sortOrder: Int,
    ): Result<Unit> {
        val cleanName = name.trim()
        val cleanDesc = description.trim().take(250)
        val cleanUnit = unitLabel.trim()
        val path = if (itemId.isNullOrBlank()) COLLECTION_MENU else "$COLLECTION_MENU/$itemId"
        return try {
            requireUserId()
            require(cleanName.length in 1..80) { "اسم الصنف يجب أن يكون بين 1 و 80 حرف" }
            require(cleanUnit.length in 1..40) { "الوحدة أو الحجم مطلوب (مثل: ربع كيلو أو كوب)" }
            require(price > 0.0 && price <= 100000.0) { "السعر يجب أن يكون أكبر من 0" }
            val safeSort = sortOrder.coerceIn(0, 10000)

            if (itemId.isNullOrBlank()) {
                val docRef = db.collection(COLLECTION_MENU).document()
                val payload = mapOf(
                    "name" to cleanName,
                    "description" to cleanDesc,
                    "category" to category.name,
                    "unitLabel" to cleanUnit,
                    "price" to price,
                    "available" to available,
                    "sortOrder" to safeSort,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(payload).await()
            } else {
                val docRef = db.collection(COLLECTION_MENU).document(itemId)
                docRef.update(
                    mapOf(
                        "name" to cleanName,
                        "description" to cleanDesc,
                        "category" to category.name,
                        "unitLabel" to cleanUnit,
                        "price" to price,
                        "available" to available,
                        "sortOrder" to safeSort,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (itemId.isNullOrBlank()) OperationType.CREATE else OperationType.UPDATE,
                path
            )
            Result.failure(e)
        }
    }

    suspend fun toggleMenuAvailability(itemId: String, available: Boolean): Result<Unit> {
        val path = "$COLLECTION_MENU/$itemId"
        return try {
            requireUserId()
            require(itemId.isNotBlank()) { "معرف الصنف غير صالح" }
            db.collection(COLLECTION_MENU).document(itemId).update(
                mapOf(
                    "available" to available,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMenuItem(itemId: String): Result<Unit> {
        val path = "$COLLECTION_MENU/$itemId"
        return try {
            requireUserId()
            require(itemId.isNotBlank()) { "معرف الصنف غير صالح" }
            db.collection(COLLECTION_MENU).document(itemId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun seedStarterMenu(): Result<Unit> {
        return try {
            requireUserId()
            val starterItems = listOf(
                Triple("بن تركي سادة (تحميص وسط)", "بن برازيلي فاخر مطحون طازج يومياً", 140.0) to ("ربع كيلو (250 جم)" to MenuCategory.GROUND_COFFEE),
                Triple("بن تركي محوج (هيل وجوزة الطيب)", "خلطة بن يزن الخاصة المحوجة الفاخرة", 175.0) to ("ربع كيلو (250 جم)" to MenuCategory.GROUND_COFFEE),
                Triple("بن فرنساوي بالبندق", "قهوة فرنسية ناعمة بنكهة البندق المحمص", 160.0) to ("ربع كيلو (250 جم)" to MenuCategory.GROUND_COFFEE),
                Triple("بن كولومبي اسبريسو", "حبوب كولومبية مختصة طحنة اسبريسو", 210.0) to ("ربع كيلو (250 جم)" to MenuCategory.GROUND_COFFEE),
                Triple("قهوة تركي سادة", "تحضير على الرمل الساخن بوش غني", 25.0) to ("فنجان" to MenuCategory.BEVERAGE),
                Triple("قهوة تركي محوج", "مضبوطة بالهيل الطازج", 30.0) to ("فنجان" to MenuCategory.BEVERAGE),
                Triple("اسبريسو دبل شوت", "استخلاص طازج من محمصة بن يزن", 45.0) to ("كوب" to MenuCategory.BEVERAGE),
                Triple("كافيه لاتيه مثلج / ساخن", "اسبريسو مع حليب مبخر أو مثلج", 65.0) to ("كوب كبير" to MenuCategory.BEVERAGE),
            )
            val batch = db.batch()
            starterItems.forEachIndexed { index, (info, meta) ->
                val docRef = db.collection(COLLECTION_MENU).document()
                val payload = mapOf(
                    "name" to info.first,
                    "description" to info.second,
                    "category" to meta.second.name,
                    "unitLabel" to meta.first,
                    "price" to info.third,
                    "available" to true,
                    "sortOrder" to (index + 1),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                batch.set(docRef, payload)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, COLLECTION_MENU)
            Result.failure(e)
        }
    }

    suspend fun placeOrder(
        customerName: String,
        customerPhone: String,
        notes: String,
        cartItems: List<CartItem>,
    ): Result<String> {
        val cleanName = customerName.trim()
        val cleanPhone = customerPhone.trim()
        val cleanNotes = notes.trim().take(250)
        var docPath = COLLECTION_ORDERS
        return try {
            val user = auth.currentUser
                ?: throw IllegalStateException("يجب تسجيل الدخول أولاً")
            val uid = user.uid
            require(cleanName.length in 2..60) { "يرجى إدخال الاسم (حرفين على الأقل)" }
            require(cleanPhone.length in 7..20) { "يرجى إدخال رقم هاتف صحيح" }
            require(cartItems.isNotEmpty() && cartItems.size <= 30) { "السلة فارغة أو تجاوزت الحد الأقصى" }

            val lineItems = cartItems.map { cartItem ->
                OrderLineItem(
                    menuItemId = cartItem.menuItem.id,
                    name = cartItem.menuItem.name,
                    category = cartItem.menuItem.category,
                    unitLabel = cartItem.menuItem.unitLabel,
                    unitPrice = cartItem.menuItem.price,
                    quantity = cartItem.quantity.toLong(),
                    subtotal = cartItem.lineTotal,
                )
            }
            val total = lineItems.sumOf { it.subtotal }
            require(total > 0.0 && total <= 500000.0) { "قيمة الطلب غير صالحة" }

            val orderNumber = "YZ-${Random.nextInt(1000, 9999)}"
            val docRef = db.collection(COLLECTION_ORDERS).document()
            docPath = docRef.path

            val orderPayload = mapOf(
                "orderNumber" to orderNumber,
                "customerId" to uid,
                "customerName" to cleanName,
                "customerPhone" to cleanPhone,
                "customerEmail" to (user.email ?: "").take(100),
                "notes" to cleanNotes,
                "items" to lineItems.map { it.toMap() },
                "totalAmount" to total,
                "status" to OrderStatus.PENDING.name,
                "paid" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            docRef.set(orderPayload).await()

            // Save/update customer profile for quick future checkout
            saveUserProfileBestEffort(uid, cleanName, cleanPhone)

            Result.success(orderNumber)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun saveUserProfileBestEffort(uid: String, displayName: String, phone: String) {
        val docRef = db.collection(COLLECTION_USERS).document(uid)
        try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "displayName" to displayName,
                        "phone" to phone,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to displayName,
                        "phone" to phone,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
        }
    }

    suspend fun updateOrderStatusAndPayment(
        orderId: String,
        status: OrderStatus,
        paid: Boolean,
    ): Result<Unit> {
        val path = "$COLLECTION_ORDERS/$orderId"
        return try {
            requireUserId()
            require(orderId.isNotBlank()) { "معرف الطلب غير صالح" }
            db.collection(COLLECTION_ORDERS).document(orderId).update(
                mapOf(
                    "status" to status.name,
                    "paid" to paid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun cancelPendingOrder(orderId: String): Result<Unit> {
        val path = "$COLLECTION_ORDERS/$orderId"
        return try {
            requireUserId()
            require(orderId.isNotBlank()) { "معرف الطلب غير صالح" }
            db.collection(COLLECTION_ORDERS).document(orderId).update(
                mapOf(
                    "status" to OrderStatus.CANCELLED.name,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
