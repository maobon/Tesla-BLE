package com.sample.drawoverlay

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkOverlayPermission()
        startService()
    }

    // method for starting the service 
    private fun startService() {
        // check if the user has already granted the Draw over other apps permission
        if (Settings.canDrawOverlays(this)) {
            // start the service based on the android version 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(Intent(this, MyService::class.java))
            } else {
                startService(Intent(this, MyService::class.java))
            }
        }
    }

    // method to ask user to grant the Overlay permission  
    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            // send user to the device settings 
            val myIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            startActivity(myIntent)
        }
    }

    // check for permission again when user grants it from  
    // the device settings, and start the service 
    override fun onResume() {
        super.onResume()
        startService()
    }
}