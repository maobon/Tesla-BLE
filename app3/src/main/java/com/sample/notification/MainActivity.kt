package com.sample.notification

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import com.sample.notification.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var rootView: ActivityMainBinding
    private lateinit var receiver: BroadcastReceiver

    class InnerBroadcastReceiver(
        private val activity: MainActivity
    ) : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "onReceive: hhhhhhhhhhhhhhhhhhhhhhhhhh")
            val data = intent?.getStringExtra("data")
            activity.rootView.tvTextView.text = data

        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rootView = ActivityMainBinding.inflate(layoutInflater)
        setContentView(rootView.main)

        receiver = InnerBroadcastReceiver(this@MainActivity)

        rootView.btnTest.setOnClickListener {
            val permissions = mutableListOf<String>()
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions(permissions.toTypedArray(), 100)
        }

        rootView.btnTest2.setOnClickListener {
            val notificationUtils = NotificationUtils(this@MainActivity)
            notificationUtils.createNotificationChannel()
            notificationUtils.test()
        }
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onResume() {
        super.onResume()
        val intentFilter = IntentFilter().apply {
            addAction("com.sample.notification")
        }
        registerReceiver(receiver, intentFilter, RECEIVER_NOT_EXPORTED)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100 && permissions[0] == Manifest.permission.POST_NOTIFICATIONS) {
            if (grantResults[0] == PERMISSION_GRANTED) {
                Toast.makeText(this@MainActivity, "OK", Toast.LENGTH_SHORT).show()

            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}