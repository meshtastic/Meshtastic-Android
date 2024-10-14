package com.geeksville.mesh.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmapOrNull
import com.geeksville.mesh.MainActivity
import com.geeksville.mesh.R
import com.geeksville.mesh.TelemetryProtos.LocalStats
import com.geeksville.mesh.android.notificationManager
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.util.PendingIntentCompat
import java.io.Closeable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("TooManyFunctions")
class MeshServiceNotifications(
    private val context: Context
) : Closeable {

    companion object {
        private const val FIFTEEN_MINUTES_IN_MILLIS = 15L * 60 * 1000
    }

    private val notificationManager: NotificationManager get() = context.notificationManager

    // We have two notification channels: one for general service status and another one for messages
    val notifyId = 101
    private val messageNotifyId = 102

    private var largeIcon: Bitmap? = null

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = "my_service"
        val channelName = context.getString(R.string.meshtastic_service_notifications)
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            lightColor = Color.BLUE
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        notificationManager.createNotificationChannel(channel)
        return channelId
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createMessageNotificationChannel(): String {
        val channelId = "my_messages"
        val channelName = context.getString(R.string.meshtastic_messages_notifications)
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            lightColor = Color.BLUE
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
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

    private val messageChannelId: String by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createMessageNotificationChannel()
        } else {
            // If earlier version channel ID is not used
            // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
            ""
        }
    }

    private fun formatStatsString(stats: LocalStats?, currentStatsUpdatedAtMillis: Long?): String {
        val updatedAt = "Next update at: ${
            currentStatsUpdatedAtMillis?.let {
                val date = Date(it + FIFTEEN_MINUTES_IN_MILLIS) // Add 15 minutes in milliseconds
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                dateFormat.format(date)
            } ?: "???"
        }"
        val statsJoined = stats?.allFields?.mapNotNull { (k, v) ->
            if (k.name == "num_online_nodes" || k.name == "num_total_nodes") {
                return@mapNotNull null
            }
            "${
                k.name.replace('_', ' ').split(" ")
                    .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
            }=$v"
        }?.joinToString("\n") ?: "No Local Stats"
        return "$updatedAt\n$statsJoined"
    }

    fun updateServiceStateNotification(
        summaryString: String? = null,
        localStats: LocalStats? = null,
        currentStatsUpdatedAtMillis: Long? = null,
    ) {
        val statsString = formatStatsString(localStats, currentStatsUpdatedAtMillis)
        notificationManager.notify(
            notifyId,
            createServiceStateNotification(summaryString.orEmpty(), statsString)
        )
    }

    fun updateMessageNotification(name: String, message: String) =
        notificationManager.notify(
            messageNotifyId,
            createMessageNotification(name, message)
        )

    fun showNewNodeSeenNotification(node: NodeEntity) {
        notificationManager.notify(
            node.num, // show unique notifications
            createNewNodeSeenNotification(node.user.shortName, node.user.longName)
        )
    }

    private val openAppIntent: PendingIntent by lazy {
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntentCompat.FLAG_IMMUTABLE
        )
    }

    /**
     * Generate a bitmap from a vector drawable (even on old builds)
     * https://stackoverflow.com/questions/33696488/getting-bitmap-from-vector-drawable/#51742167
     */
    private fun getBitmapFromVectorDrawable(drawableId: Int): Bitmap? =
        AppCompatResources.getDrawable(context, drawableId)?.toBitmapOrNull()

    private fun commonBuilder(channel: String): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(context, channel)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent)

        // Set the notification icon
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            // If running on really old versions of android (<= 5.1.1) (possibly only cyanogen) we might encounter a bug with setting application specific icons
            // so punt and stay with just the bluetooth icon - see https://meshtastic.discourse.group/t/android-1-1-42-ready-for-alpha-testing/2399/3?u=geeksville
            builder.setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
        } else {
            // Newer androids also support a 'large' icon

            // We delay making this bitmap until we know we need it
            largeIcon = largeIcon ?: getBitmapFromVectorDrawable(R.mipmap.ic_launcher2)

            builder.setSmallIcon(
                // vector form icons don't work reliably on older androids
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    R.drawable.app_icon_novect
                } else {
                    R.drawable.app_icon
                }
            )
                .setLargeIcon(largeIcon)
        }
        return builder
    }

    lateinit var serviceNotificationBuilder: NotificationCompat.Builder
    fun createServiceStateNotification(name: String, message: String? = null): Notification {
        if (!::serviceNotificationBuilder.isInitialized) {
            serviceNotificationBuilder = commonBuilder(channelId)
        }
        with(serviceNotificationBuilder) {
            priority = NotificationCompat.PRIORITY_MIN
            setCategory(Notification.CATEGORY_SERVICE)
            setOngoing(true)
            setContentTitle(name)
            message?.let {
                setContentText(it)
                setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(message),
                )
            }
        }
        return serviceNotificationBuilder.build()
    }

    lateinit var messageNotificationBuilder: NotificationCompat.Builder
    private fun createMessageNotification(name: String, message: String): Notification {
        if (!::messageNotificationBuilder.isInitialized) {
            messageNotificationBuilder = commonBuilder(messageChannelId)
        }
        with(messageNotificationBuilder) {
            priority = NotificationCompat.PRIORITY_DEFAULT
            setCategory(Notification.CATEGORY_MESSAGE)
            setAutoCancel(true)
            setContentTitle(name)
            setContentText(message)
            setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(message),
            )
        }
        return messageNotificationBuilder.build()
    }

    lateinit var newNodeSeenNotificationBuilder: NotificationCompat.Builder
    private fun createNewNodeSeenNotification(name: String, message: String? = null): Notification {
        if (!::newNodeSeenNotificationBuilder.isInitialized) {
            newNodeSeenNotificationBuilder = commonBuilder(messageChannelId)
        }
        with(newNodeSeenNotificationBuilder) {
            priority = NotificationCompat.PRIORITY_DEFAULT
            setCategory(Notification.CATEGORY_STATUS)
            setAutoCancel(true)
            setContentTitle("New Node Seen: $name")
            message?.let {
                setContentText(it)
                setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(message),
                )
            }
        }
        return newNodeSeenNotificationBuilder.build()
    }

    override fun close() {
        largeIcon?.recycle()
        largeIcon = null
    }
}
