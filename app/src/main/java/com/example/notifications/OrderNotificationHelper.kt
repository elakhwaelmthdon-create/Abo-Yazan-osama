package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.CoffeeOrder

class OrderNotificationHelper(private val context: Context) {

    private val channelId = context.getString(R.string.notification_channel_id)

    init {
        ensureChannel()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val existing = manager.getNotificationChannel(channelId)
            if (existing == null) {
                val channel = NotificationChannel(
                    channelId,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_desc)
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun notifyNewOrderForOwner(order: CoffeeOrder) {
        if (!hasNotificationPermission()) return
        val itemsSummary = order.items.joinToString("، ") { "${it.name} ×${it.quantity}" }
        postNotification(
            notificationId = order.id.hashCode(),
            title = "طلب جديد في بن يزن (${order.orderNumber})",
            body = "${order.customerName} • ${order.customerPhone}\n$itemsSummary — ${formatPrice(order.totalAmount)} ج.م"
        )
    }

    fun notifyOrderReadyForCustomer(order: CoffeeOrder) {
        if (!hasNotificationPermission()) return
        postNotification(
            notificationId = ("ready_${order.id}").hashCode(),
            title = "طلبك من بن يزن جاهز للاستلام!",
            body = "طلب رقم ${order.orderNumber} جاهز الآن — يمكنك التوجه للمحل للاستلام (${formatPrice(order.totalAmount)} ج.م)"
        )
    }

    private fun postNotification(notificationId: Int, title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission was revoked at runtime
        }
    }

    private fun formatPrice(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else String.format("%.2f", value)
}
