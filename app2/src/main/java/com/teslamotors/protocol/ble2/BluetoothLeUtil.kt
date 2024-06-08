package com.teslamotors.protocol.ble2

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import android.util.Log
import com.teslamotors.protocol.util.XIAOMI_ENV_SENSOR_CCC_DESCRIPTOR_UUID
import com.teslamotors.protocol.util.isIndicatable
import com.teslamotors.protocol.util.isNotifiable
import com.teslamotors.protocol.util.isWritable
import com.teslamotors.protocol.util.isWritableWithoutResponse
import java.util.*

@SuppressLint("MissingPermission")
class BluetoothLeUtil(
    private val bluetoothGatt: BluetoothGatt?
) {
    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, payload: ByteArray) {
        val writeType = when {
            characteristic.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.isWritableWithoutResponse() -> {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }

            else -> error("Characteristic ${characteristic.uuid} cannot be written to")
        }

        bluetoothGatt?.let { gatt ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, payload, writeType)
            } else {
                // Fall back to deprecated version of writeCharacteristic for Android <13
                gatt.legacyCharacteristicWrite(characteristic, payload, writeType)
            }
        } ?: error("Not connected to a BLE device!")
    }

    @TargetApi(Build.VERSION_CODES.S)
    @Suppress("DEPRECATION")
    fun BluetoothGatt.legacyCharacteristicWrite(
        characteristic: BluetoothGattCharacteristic, value: ByteArray, writeType: Int
    ) {
        characteristic.writeType = writeType
        characteristic.value = value
        writeCharacteristic(characteristic)
    }

    fun writeDescriptor(descriptor: BluetoothGattDescriptor, payload: ByteArray) {
        bluetoothGatt?.let { gatt ->

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, payload)
            } else {
                // Fall back to deprecated version of writeDescriptor for Android <13
                gatt.legacyDescriptorWrite(descriptor, payload)
            }

        } ?: error("Not connected to a BLE device!")
    }

    @TargetApi(Build.VERSION_CODES.S)
    @Suppress("DEPRECATION")
    fun BluetoothGatt.legacyDescriptorWrite(
        descriptor: BluetoothGattDescriptor, value: ByteArray
    ): Boolean {
        descriptor.value = value
        return writeDescriptor(descriptor)
    }

    fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
        val cccdUuid = UUID.fromString(XIAOMI_ENV_SENSOR_CCC_DESCRIPTOR_UUID)

        val payload = when {
            characteristic.isIndicatable() -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            characteristic.isNotifiable() -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> {
                Log.e(
                    "ConnectionManager",
                    "${characteristic.uuid} doesn't support notifications/indications"
                )
                return
            }
        }

        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (bluetoothGatt?.setCharacteristicNotification(characteristic, true) == false) {
                Log.e(
                    "ConnectionManager",
                    "setCharacteristicNotification failed for ${characteristic.uuid}"
                )
                return
            }

            writeDescriptor(cccDescriptor, payload)
        } ?: Log.e(
            "ConnectionManager", "${characteristic.uuid} doesn't contain the CCC descriptor!"
        )
    }

    fun disableNotifications(characteristic: BluetoothGattCharacteristic) {
        if (!characteristic.isNotifiable() && !characteristic.isIndicatable()) {
            Log.e(
                "ConnectionManager",
                "${characteristic.uuid} doesn't support indications/notifications"
            )
            return
        }

        val cccdUuid = UUID.fromString(XIAOMI_ENV_SENSOR_CCC_DESCRIPTOR_UUID)
        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (bluetoothGatt?.setCharacteristicNotification(characteristic, false) == false) {
                Log.e(
                    "ConnectionManager",
                    "setCharacteristicNotification failed for ${characteristic.uuid}"
                )
                return
            }
            writeDescriptor(cccDescriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
        } ?: Log.e(
            "ConnectionManager", "${characteristic.uuid} doesn't contain the CCC descriptor!"
        )
    }

    companion object {
        private const val TAG = "BluetoothLEUtils"
    }
}