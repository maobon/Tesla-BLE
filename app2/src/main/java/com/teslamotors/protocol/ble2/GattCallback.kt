package com.teslamotors.protocol.ble2

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.util.Log
import com.teslamotors.protocol.util.printGattTable
import com.teslamotors.protocol.util.toHexString

class GattCallback(
    private val mStatusListener: ConnectionStateListener
) : BluetoothGattCallback() {

    companion object {
        private const val TAG = "GattCallback"
    }

    var mBluetoothGatt: BluetoothGatt? = null

    interface ConnectionStateListener {
        fun onConnected()

        fun onServiceDiscovered()
    }

    @SuppressLint("MissingPermission")
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        val deviceAddress = gatt.device.address

        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.w("BluetoothGattCallback", "Successfully connected to $deviceAddress")

                // stash BluetoothGatt instance ...
                mBluetoothGatt = gatt

                // createToast(this@MainActivity, "Connected OK")
                mStatusListener.onConnected()

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w("BluetoothGattCallback", "Successfully disconnected from $deviceAddress")
                gatt.close()
            }

        } else {
            Log.w(
                "BluetoothGattCallback",
                "Error $status encountered for $deviceAddress! Disconnecting..."
            )
            gatt.close()
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        with(gatt) {
            Log.w(
                "BluetoothGattCallback",
                "Discovered ${services.size} services for ${device.address}"
            )

            // See implementation just above this section
            printGattTable()
            // Consider connection setup as complete here

            mStatusListener.onServiceDiscovered()

        }
    }

    @Deprecated("Deprecated for Android 13+")
    @Suppress("DEPRECATION")
    override fun onCharacteristicRead(
        gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
    ) {

        with(characteristic) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.i(
                        "BluetoothGattCallback",
                        "Read characteristic --> $uuid:\n${value.toHexString()}"
                    )
                }

                BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                    Log.e("BluetoothGattCallback", "Read not permitted for $uuid!")
                }

                else -> {
                    Log.e(
                        "BluetoothGattCallback",
                        "Characteristic read failed for $uuid, error: $status"
                    )
                }
            }
        }
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        val uuid = characteristic.uuid
        when (status) {
            BluetoothGatt.GATT_SUCCESS -> {

                val hexString = value.toHexString()
                Log.i(
                    "BluetoothGattCallback", "Read characteristic $uuid:\n $hexString"
                )

                // todo ...............
                // Log.d(
                //     TAG, "onCharacteristicRead: ppp=${
                //         littleEndianConversion(value)
                //     }"
                // )
            }

            BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                Log.e("BluetoothGattCallback", "Read not permitted for $uuid!")
            }

            else -> {
                Log.e(
                    "BluetoothGattCallback", "Characteristic read failed for $uuid, error: $status"
                )
            }
        }
    }

    @Deprecated("Deprecated for Android 13+")
    @Suppress("DEPRECATION")
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic
    ) {

        with(characteristic) {
            Log.i(
                "BluetoothGattCallback",
                "Characteristic $uuid changed | value: ${value.toHexString()}"
            )
        }
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray
    ) {

        val newValueHex = value.toHexString()
        with(characteristic) {
            Log.i("BluetoothGattCallback", "Characteristic $uuid changed | value: $newValueHex")
        }

        // ...
        val str = String(value)
        Log.d(TAG, "onCharacteristicChanged: str: $str")

        // temp
        val kk = with(str) {
            substring(indexOf("T="), lastIndexOf(" "))
        }
        Log.d(TAG, "onCharacteristicChanged: $kk")

        // temp
        val k = with(str) {
            substring(indexOf("H="), length - 1)
        }
        Log.d(TAG, "onCharacteristicChanged: $k")
    }

}