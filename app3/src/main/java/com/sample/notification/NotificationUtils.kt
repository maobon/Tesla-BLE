package com.sample.notification

import android.Manifest
import android.app.Notification.EXTRA_NOTIFICATION_ID
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat.getString

class NotificationUtils(private val context: Context) {

    companion object{
        val ACTION_ONE = "ACTION_ONE"
        val ACTION_TWO = "ACTION_TWO"
    }

    private val CHANNEL_ID = "CHANNEL_ID"
    private val NOTIFICATION_ID = 10

    @RequiresApi(Build.VERSION_CODES.O)
    private val actionOneIntent = Intent(context, MyService::class.java).apply {
        action = ACTION_ONE
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private val actionTwoIntent = Intent(context, MyService::class.java).apply {
        action = ACTION_TWO
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private val actionOnePendingIntent: PendingIntent =
        PendingIntent.getService(context, 0, actionOneIntent, PendingIntent.FLAG_IMMUTABLE)

    @RequiresApi(Build.VERSION_CODES.O)
    private val actionTwoPendingIntent: PendingIntent =
        PendingIntent.getService(context, 0, actionTwoIntent, PendingIntent.FLAG_IMMUTABLE)

    @RequiresApi(Build.VERSION_CODES.O)
    fun test() {

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setContentTitle("My notification")
            .setContentText("Hello World!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Set the intent that fires when the user taps the notification.
            // .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .addAction(
                R.drawable.ic_bluetooth,
                getString(context, R.string.action_one),
                actionOnePendingIntent
            )
            .addAction(
                R.drawable.ic_bluetooth,
                getString(context, R.string.action_two),
                actionTwoPendingIntent
            )

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                // ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                // public fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                //                                        grantResults: IntArray)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.

                return@with
            }

            // notificationId is a unique int for each notification that you must define.
            notify(NOTIFICATION_ID, builder.build())
        }
    }

    fun createNotificationChannel() {
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