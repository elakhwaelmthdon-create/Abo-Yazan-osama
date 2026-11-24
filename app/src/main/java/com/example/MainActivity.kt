package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthScreen
import com.example.auth.signOutUser
import com.example.data.remote.BonYazanRepository
import com.example.notifications.OrderNotificationHelper
import com.example.ui.screens.CustomerHomeScreen
import com.example.ui.screens.OwnerHomeScreen
import com.example.ui.theme.BonYazanTheme
import com.example.ui.viewmodel.NotificationEvent
import com.example.ui.viewmodel.ShopViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BonYazanTheme {
                BonYazanAppRoot()
            }
        }
    }
}

@Composable
private fun BonYazanAppRoot() {
    var currentUser by remember { mutableStateOf<FirebaseUser?>(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            }
        )
    } else {
        key(user.uid) {
            AuthenticatedShopContent(user = user)
        }
    }
}

@Composable
private fun AuthenticatedShopContent(user: FirebaseUser) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val notificationHelper = remember(context) { OrderNotificationHelper(context.applicationContext) }
    val snackbarHostState = remember { SnackbarHostState() }

    val shopViewModel: ShopViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                ShopViewModel(
                    repository = BonYazanRepository(db),
                    currentUserId = user.uid,
                    userEmail = user.email,
                    userDisplayName = user.displayName,
                    isEmailVerified = user.isEmailVerified,
                )
            }
        },
    )

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* Permission handled gracefully */ },
    )

    LaunchedEffect(user.uid) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationHelper.hasNotificationPermission()
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(shopViewModel) {
        shopViewModel.actionMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(shopViewModel) {
        shopViewModel.notificationEvents.collect { event ->
            when (event) {
                is NotificationEvent.NewOrderForOwner -> {
                    notificationHelper.notifyNewOrderForOwner(event.order)
                    snackbarHostState.showSnackbar(
                        "طلب جديد (${event.order.orderNumber}) من ${event.order.customerName}"
                    )
                }
                is NotificationEvent.OrderReadyForCustomer -> {
                    notificationHelper.notifyOrderReadyForCustomer(event.order)
                    snackbarHostState.showSnackbar(
                        "طلبك رقم ${event.order.orderNumber} جاهز للاستلام الآن!"
                    )
                }
            }
        }
    }

    val previewCustomerMode by shopViewModel.previewCustomerMode.collectAsStateWithLifecycle()

    val onSignOut = {
        signOutUser(
            credentialManager = credentialManager,
            scope = scope,
        )
    }

    if (shopViewModel.isOwner && !previewCustomerMode) {
        OwnerHomeScreen(
            viewModel = shopViewModel,
            snackbarHostState = snackbarHostState,
            onPreviewCustomer = { shopViewModel.setPreviewCustomerMode(true) },
            onSignOut = onSignOut,
        )
    } else {
        CustomerHomeScreen(
            viewModel = shopViewModel,
            snackbarHostState = snackbarHostState,
            isOwnerPreview = shopViewModel.isOwner && previewCustomerMode,
            onExitOwnerPreview = { shopViewModel.setPreviewCustomerMode(false) },
            onSignOut = onSignOut,
        )
    }
}
