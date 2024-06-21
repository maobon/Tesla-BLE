package com.teslamotors.protocol.ble

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.teslamotors.protocol.msg.Operations
import com.teslamotors.protocol.util.isIndicatable
import com.teslamotors.protocol.util.isNotifiable
import com.teslamotors.protocol.util.isWritable
import com.teslamotors.protocol.util.isWritableWithoutResponse
import java.util.*

@SuppressLint("MissingPermission")
class GattUtil(
    private val bluetoothGatt: BluetoothGatt? = null,
    private val stateListener: ConnectionStateListener? = null
) {

    var opera: Operations? = null

    fun discoveryServices() {
        val discoveryRun = java.lang.Runnable {
            bluetoothGatt?.discoverServices()
        }
        Handler(Looper.getMainLooper()).postDelayed(discoveryRun, 100)
    }

    fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray,
        operations: Operations? = null
    ) {
        opera = operations

        val writeType = when {
            characteristic.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.isWritableWithoutResponse() -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else -> {
                responseErr(
                    GattErrorType.ERR_INNER_CHARACTERISTIC_WRITE,
                    "Characteristic ${characteristic.uuid} cannot be written to"
                )
                return
            }
        }

        bluetoothGatt?.let { gatt ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, payload, writeType)
            } else {
                // Fall back to deprecated version of writeCharacteristic for Android <13
                gatt.legacyCharacteristicWrite(characteristic, payload, writeType, operations)
            }
        } ?: responseErr(
            GattErrorType.ERR_INNER_CHARACTERISTIC_WRITE, "Not connected to a BLE device!"
        )
    }

    @TargetApi(Build.VERSION_CODES.S)
    @Suppress("DEPRECATION")
    fun BluetoothGatt.legacyCharacteristicWrite(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
        operations: Operations? = null
    ) {
        opera = operations

        characteristic.writeType = writeType
        characteristic.value = value
        writeCharacteristic(characteristic)
    }

    private fun writeDescriptor(descriptor: BluetoothGattDescriptor, payload: ByteArray) {
        bluetoothGatt?.let { gatt ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, payload)
            } else {
                // Fall back to deprecated version of writeDescriptor for Android <13
                gatt.legacyDescriptorWrite(descriptor, payload)
            }
        } ?: responseErr(GattErrorType.ERR_INNER_DESCRIPTOR_WRITE, "Not connected to a BLE device!")
    }

    @TargetApi(Build.VERSION_CODES.S)
    @Suppress("DEPRECATION")
    fun BluetoothGatt.legacyDescriptorWrite(
        descriptor: BluetoothGattDescriptor, value: ByteArray
    ): Boolean {
        descriptor.value = value
        return writeDescriptor(descriptor)
    }

    fun enableNotifications(characteristic: BluetoothGattCharacteristic, cccdUuid: UUID) {
        val payload = when {
            characteristic.isIndicatable() -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            characteristic.isNotifiable() -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> {
                responseErr(
                    GattErrorType.ERR_INNER_NOTIFICATIONS_ENABLE,
                    "${characteristic.uuid} doesn't support notifications/indications"
                )
                return
            }
        }

        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (bluetoothGatt?.setCharacteristicNotification(characteristic, true) == false) {
                responseErr(
                    GattErrorType.ERR_INNER_NOTIFICATIONS_ENABLE,
                    "setCharacteristicNotification failed for ${characteristic.uuid}"
                )
                return
            }
            writeDescriptor(cccDescriptor, payload)
        } ?: responseErr(
            GattErrorType.ERR_INNER_NOTIFICATIONS_ENABLE,
            "${characteristic.uuid} doesn't contain the CCC descriptor!"
        )
    }

    fun disableNotifications(characteristic: BluetoothGattCharacteristic, cccdUuid: UUID) {
        if (!characteristic.isNotifiable() && !characteristic.isIndicatable()) {
            responseErr(
                GattErrorType.ERR_INNER_NOTIFICATIONS_DISABLE,
                "${characteristic.uuid} doesn't support indications/notifications"
            )
            return
        }
        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (bluetoothGatt?.setCharacteristicNotification(characteristic, false) == false) {
                responseErr(
                    GattErrorType.ERR_INNER_NOTIFICATIONS_DISABLE,
                    "setCharacteristicNotification failed for ${characteristic.uuid}"
                )
                return
            }
            writeDescriptor(cccDescriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
        } ?: responseErr(
            GattErrorType.ERR_INNER_NOTIFICATIONS_DISABLE,
            "${characteristic.uuid} doesn't contain the CCC descriptor!"
        )
    }

    companion object {
        private const val TAG = "GattUtil"
    }

    private fun responseErr(type: GattErrorType, desc: String? = null) {
        Log.e(TAG, "errorType: $type desc:$desc")
        stateListener?.onError(type, desc = desc)
    }
}