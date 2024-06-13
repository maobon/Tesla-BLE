package com.sample.notification

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.util.Log

class MyService : Service() {

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: ")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ")

        if (intent?.action == NotificationUtils.ACTION_ONE) {
            Log.d(TAG, "onStartCommand: ACTION_ONE")
            sendData("action_one_data")
        }

        if (intent?.action == NotificationUtils.ACTION_TWO) {
            Log.d(TAG, "onStartCommand: ACTION_TWO")
            sendData("action_two_data")
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun sendData(data: String) {

        Intent().apply {
            action = "com.sample.notification"
            setPackage(packageName)
            putExtra("data", data)
            sendBroadcast(this)
        }
    }

    companion object {
        private const val TAG = "MyService"
    }
}