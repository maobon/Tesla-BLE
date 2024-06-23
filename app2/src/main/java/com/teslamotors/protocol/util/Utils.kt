package com.teslamotors.protocol.util

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Message
import android.os.Messenger
import android.util.Log
import android.util.TypedValue
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Determine whether the current [Context] has been granted the relevant [Manifest.permission].
 */
fun Context.hasPermission(permissionType: String): Boolean {
    return ContextCompat.checkSelfPermission(
        this, permissionType
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * Determine whether the current [Context] has been granted the relevant permissions to perform
 * Bluetooth operations depending on the mobile device's Android version.
 */
fun Context.hasRequiredBluetoothPermissions(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        hasPermission(Manifest.permission.BLUETOOTH_SCAN) && hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

fun Context.createToast(activity: Activity, text: String) = activity.runOnUiThread {
    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
}

fun BluetoothGatt.printGattTable() {
    if (services.isEmpty()) {
        Log.i(
            "printGattTable",
            "No service and characteristic available, call discoverServices() first?"
        )
        return
    }
    services.forEach { service ->
        val characteristicsTable = service.characteristics.joinToString(
            separator = "\n|--", prefix = "|--"
        ) { it.uuid.toString() }
        Log.i(
            "printGattTable", "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
        )
    }
}

// ....

fun BluetoothGattCharacteristic.isReadable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

fun BluetoothGattCharacteristic.isWritable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

fun BluetoothGattCharacteristic.isIndicatable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_INDICATE)

fun BluetoothGattCharacteristic.isNotifiable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)

fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
    return properties and property != 0
}

// ... somewhere outside BluetoothGattCallback
fun ByteArray.toHexString(): String =
    joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }

fun littleEndianConversion(bytes: ByteArray): Int {
    var result = 0
    for (i in bytes.indices) {
        result = result or (bytes[i].toInt() shl 8 * i)
    }
    return result
}

//

fun Int.to2ByteArray(): ByteArray = byteArrayOf(shr(8).toByte(), toByte())

fun Int.to4ByteArray(): ByteArray =
    ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(this).array()


// shared preference
private const val SP_TAG = "sharedPreference"

private const val sp_name = "count"
private const val count_name = "count"
fun Context.countAutoIncrement() = with(getSharedPreferences(sp_name, Context.MODE_PRIVATE)) {
    var curr = getInt(count_name, 100)
    Log.d(SP_TAG, "countAutoIncrement: curr count= $curr")
    edit().putInt(count_name, ++curr).apply()
    curr
}

// for shared key stash test
fun Context.useSharedKey(sharedKey: ByteArray? = null) =
    with(getSharedPreferences("key", Context.MODE_PRIVATE)) {
        if (sharedKey != null) {
            // save it
            Log.d(TAG, "useSharedKey: save sharedKey into sp")
            val keyHex = JUtils.bytesToHex(sharedKey)
            edit().putString("shared", keyHex).apply()
            return@with sharedKey
        } else {
            val hex = getString("shared", null)
            if (hex != null) {
                Log.d(TAG, "useSharedKey: get sharedKey from sp")
                return@with JUtils.hexToBytes(hex)
            } else {
                Log.d(TAG, "useSharedKey: sharedKey is null")
                return@with null
            }
        }
    }

// ....
fun Context.requestRelevantRuntimePermissions(belowS: () -> Unit, aboveS: () -> Unit) {
    if (hasRequiredBluetoothPermissions())
        return

    when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> belowS()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> aboveS()
    }
}

//
private const val TAG = "Utils"

fun sendMessage(
    messenger: Messenger?,
    action: Int,
    obj: Any? = null,
    statusCode: Int? = STATUS_CODE_ERR
) {
    if (messenger == null) {
        Log.d(TAG, "sendMessage: messenger is null")
        return
    }

    Message.obtain().apply {
        what = action
        if (obj != null) {
            this.obj = obj
        }
        if (statusCode != null) {
            arg1 = statusCode
        }
        messenger.send(this)
    }
}

// Foreground Service
const val CHANNEL_ID = "com.sample.drawoverlay.id"
const val CHANNEL_NAME = "com.sample.drawoverlay"

const val SERVICE_ACTION_STOP = "com.sample.drawoverlay.stop.service"

fun Context.toPx(dp: Int): Float = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    dp.toFloat(),
    resources.displayMetrics
)




