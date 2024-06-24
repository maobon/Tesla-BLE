package com.teslamotors.protocol.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import androidx.core.app.ActivityCompat.startActivityForResult

/**
 * Check location switch status and enable it.
 */
const val LOCATION_RUNTIME_PERMISSIONS_REQUEST_CODE = 101

fun Context.isLocationEnable(): Boolean =
    with(getSystemService(Context.LOCATION_SERVICE) as LocationManager) {
        isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

fun enableLocation(activity: Activity) {
    activity.isLocationEnable().apply {
        if (!this) {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivityForResult(
                activity,
                intent,
                LOCATION_RUNTIME_PERMISSIONS_REQUEST_CODE,
                null
            )
        }
    }
}