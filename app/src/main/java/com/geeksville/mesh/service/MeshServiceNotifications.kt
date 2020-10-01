package com.geeksville.mesh.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MainActivity
import com.geeksville.mesh.R
import com.geeksville.mesh.android.notificationManager
import com.geeksville.mesh.utf8

class MeshServiceNotifications(
    private val context: Context
) {
    private val notificationManager: NotificationManager get() = context.notificationManager
    val notifyId = 101

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = "my_service"
        val channelName = context.getString(R.string.meshtastic_service_notifications)
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
            lightColor = Color.BLUE
            importance = NotificationManager.IMPORTANCE_NONE
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        notificationManager.createNotificationChannel(channel)
        return channelId
    }

    private val channelId: String by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        } else {
            // If earlier version channel ID is not used
            // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
            ""
        }
    }

    /**
     * Update our notification with latest data
     */
    fun updateNotification(
        recentReceivedText: DataPacket?,
        summaryString: String,
        senderName: String
    ) {
        val notification = createNotification(recentReceivedText, summaryString, senderName)
        notificationManager.notify(notifyId, notification)
    }

    private val openAppIntent: PendingIntent by lazy {
        PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), 0)
    }

    /**
     * Generate a new version of our notification - reflecting current app state
     */
    fun createNotification(
        recentReceivedText: DataPacket?,
        summaryString: String,
        senderName: String
    ): Notification {
        val category = if (recentReceivedText != null) Notification.CATEGORY_SERVICE else Notification.CATEGORY_MESSAGE
        val builder = NotificationCompat.Builder(context, channelId).setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(category)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(summaryString) // leave this off for now so our notification looks smaller
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent)

        // FIXME, show information about the nearest node
        // if(shortContent != null) builder.setContentText(shortContent)

        // If a text message arrived include it with our notification
        recentReceivedText?.let { packet ->
            // Try to show the human name of the sender if possible
            builder.setContentText("Message from $senderName")
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(packet.bytes!!.toString(utf8))
            )
        }

        return builder.build()
    }
}
