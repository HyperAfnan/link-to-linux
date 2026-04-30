package com.linktolinux.wifidirect.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.linktolinux.wifidirect.R

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_DISCOVERY_ID = "discovery_channel_v2"
        const val CONNECTION_NOTIFICATION_ID = 1002

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = context.getString(R.string.notification_channel_discovery_name)
                val descriptionText = context.getString(R.string.notification_channel_discovery_description)
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_DISCOVERY_ID, name, importance).apply {
                    description = descriptionText
                }
                val notificationManager: NotificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
    fun showConnectedNotification() {
        showNotification(
            CONNECTION_NOTIFICATION_ID,
            context.getString(R.string.notification_connected_title),
            context.getString(R.string.notification_connected_content)
        )
    }

    fun showDisconnectedNotification() {
        showNotification(
            CONNECTION_NOTIFICATION_ID,
            context.getString(R.string.notification_disconnected_title),
            context.getString(R.string.notification_disconnected_content)
        )
    }

    fun showConnectionFailedNotification(error: String? = null) {
        showNotification(
            CONNECTION_NOTIFICATION_ID,
            context.getString(R.string.notification_failed_title),
            error ?: context.getString(R.string.notification_failed_content)
        )
    }

    private fun showNotification(id: Int, title: String, content: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_DISCOVERY_ID)
            .setSmallIcon(R.drawable.ic_discovery_notification) // Reuse the discovery icon for now
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(id, builder.build())
            } catch (e: SecurityException) {
                // Handle missing permission
            }
        }
    }
}
