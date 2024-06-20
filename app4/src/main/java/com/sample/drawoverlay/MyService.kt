package com.sample.drawoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat


class MyService : Service() {

    private lateinit var window: Window

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onCreate() {
        super.onCreate()

        // create the custom or default notification based on the android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService()
        } else {
            startForeground(1, Notification())
        }

        val partClickListener = object : PartClickListener {
            override fun onTopClick() {
                Log.i(TAG, "onTopClick: ...")
            }

            override fun onBottomClick() {
                Log.i(TAG, "onBottomClick: ...")
            }
        }

        // create an instance of Window class and display the content on screen
        window = Window(this@MyService, partClickListener)
        window.open()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (SERVICE_ACTION_STOP == intent?.action) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    // for android version >=O
    // we need to create custom notification stating foreground service is running
    @RequiresApi(Build.VERSION_CODES.O)
    private fun startForegroundService() {

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        )

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val notificationBuilder = NotificationCompat.Builder(this@MyService, CHANNEL_ID)
        val notification = notificationBuilder
            .setOngoing(true)
            .setContentTitle("Service running")
            .setContentText("Displaying over other apps") // this is important, otherwise the notification will show the way
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        startForeground(2, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        window.close()
    }

    companion object {
        private const val TAG = "MyService"
    }
}