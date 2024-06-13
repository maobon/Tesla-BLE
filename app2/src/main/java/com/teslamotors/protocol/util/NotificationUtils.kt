package com.teslamotors.protocol.util

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat.getString
import com.teslamotors.protocol.BluetoothLeService
import com.teslamotors.protocol.R

@RequiresApi(Build.VERSION_CODES.O)
class NotificationUtils(private val context: Context) {

    private val CHANNEL_ID = "CHANNEL_ID"
    private val NOTIFICATION_ID = 50

    private val openFrontAction = Intent(context, BluetoothLeService::class.java).apply {
        action = ACTION_OPEN_FRONT_PASSENGER_DOOR
    }

    private val openRearAction = Intent(context, BluetoothLeService::class.java).apply {
        action = ACTION_OPEN_REAR_PASSENGER_DOOR
    }

    private val actionOpenFrontPendingIntent: PendingIntent =
        PendingIntent.getService(context, 0, openFrontAction, PendingIntent.FLAG_IMMUTABLE)

    private val actionOpenRearPendingIntent: PendingIntent =
        PendingIntent.getService(context, 0, openRearAction, PendingIntent.FLAG_IMMUTABLE)

    @SuppressLint("MissingPermission")
    fun postNotification() {
        createNotificationChannel()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setContentTitle("Passenger Doors")
            .setContentText("Control with buttons below")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Set the intent that fires when the user taps the notification.
            .setAutoCancel(false)
            .addAction(
                R.drawable.ic_bluetooth,
                getString(context, R.string.action_front),
                actionOpenFrontPendingIntent
            )
            .addAction(
                R.drawable.ic_bluetooth,
                getString(context, R.string.action_rear),
                actionOpenRearPendingIntent
            )

        with(NotificationManagerCompat.from(context)) {
            // notificationId is a unique int for each notification that you must define.
            notify(NOTIFICATION_ID, builder.build())
        }
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is not in the Support Library.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(context, R.string.channel_name)
            val descriptionText = getString(context, R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system.
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}