package com.teslamotors.protocol.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi

object BluetoothUtil {

    @RequiresApi(Build.VERSION_CODES.S)
    val permissions = arrayListOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    fun checkSupport(context: Context): BluetoothLeScanner {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
            throw RuntimeException("device not support bluetooth LE")

        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        return bluetoothManager.adapter.bluetoothLeScanner
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun isPermissionsGranted(context: Context): Boolean {
        var granted = true

        for (permission in permissions) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) granted =
                false
        }

        return granted
    }

}