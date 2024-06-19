package com.sample.drawoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat


class MyService : Service() {

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onCreate() {
        super.onCreate()

        // create the custom or default notification
        // based on the android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startMyOwnForeground()
        else
            startForeground(1, Notification())

        // create an instance of Window class
        // and display the content on screen
        val window = Window(this)
        window.open()
    }


    // for android version >=O we need to create custom notification stating
    // foreground service is running
    @RequiresApi(Build.VERSION_CODES.O)
    private fun startMyOwnForeground() {

        val NOTIFICATION_CHANNEL_ID = "example.permanence"
        val channelName = "Background Service"

        val chan = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            channelName,
            NotificationManager.IMPORTANCE_MIN
        )

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(chan)

        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        val notification: Notification = notificationBuilder.setOngoing(true)
            .setContentTitle("Service running")
            .setContentText("Displaying over other apps") // this is important, otherwise the notification will show the way
            // you want i.e. it will show some default notification

            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        startForeground(2, notification)
    }
}