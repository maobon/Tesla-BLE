package com.teslamotors.protocol.ui

import android.Manifest
import android.app.Activity
import android.content.DialogInterface.OnClickListener
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.teslamotors.protocol.R

object DialogUtil {

    const val PERMISSION_REQUEST_CODE = 100

    enum class PermissionType {
        Location, Bluetooth
    }

    private fun create(
        activity: Activity, title: String, message: String, onClickListener: OnClickListener
    ) = with(activity) {
        runOnUiThread {
            AlertDialog.Builder(activity).setTitle(title).setMessage(message).setCancelable(false)
                .setPositiveButton(android.R.string.ok, onClickListener).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun showReqPermissions(permissionType: PermissionType, activity: Activity) {
        when (permissionType) {
            PermissionType.Location -> {
                create(
                    activity,
                    activity.getString(R.string.title_location),
                    activity.getString(R.string.prompt_content_location)
                ) { _, _ ->
                    val permissions = arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    )
                    ActivityCompat.requestPermissions(
                        activity, permissions, PERMISSION_REQUEST_CODE
                    )
                }
            }

            // scan for iBeacon must need  ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION
            PermissionType.Bluetooth -> {
                create(
                    activity,
                    activity.getString(R.string.title_bluetooth),
                    activity.getString(R.string.prompt_content_bluetooth)
                ) { _, _ ->
                    val permissions = arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    )
                    ActivityCompat.requestPermissions(
                        activity, permissions, PERMISSION_REQUEST_CODE
                    )
                }
            }
        }
    }

}