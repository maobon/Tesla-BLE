package com.teslamotors.protocol.ble

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.teslamotors.protocol.util.JUtils
import java.util.UUID


class GattCallback(
    private val context: Context, private val connectCallback: ConnectCallback
) : BluetoothGattCallback() {


    companion object {
        private const val TAG = "GattCallback"

        // 211
        private val UUID_GATT_SERVICE = UUID.fromString("00000211-b2d1-43f0-9b88-960cebf8b91e")

        // 212
        private val UUID_GATT_CHARACTERISTIC_TX =
            UUID.fromString("00000212-b2d1-43f0-9b88-960cebf8b91e")

        // 213
        private val UUID_GATT_CHARACTERISTIC_RX =
            UUID.fromString("00000213-b2d1-43f0-9b88-960cebf8b91e")
        private val UUID_GATT_CHARACTERISTIC_RX_DESCRIPTOR =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)

        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Log.i(TAG, "Connected to GATT server....")
            // Attempts to discover services after successful connection.
            // Log.i(TAG, "Attempting to start service discovery:" + mBluetoothGatt.discoverServices())

            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val startDiscoveryServices = gatt?.discoverServices()

        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            Log.i(TAG, "Disconnected from GATT server.")
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        super.onServicesDiscovered(gatt, status)

        when (status) {
            BluetoothGatt.GATT_SUCCESS -> {
                Log.w(TAG, "onServicesDiscovered received: GATT_SUCCESS HHHHHHHHHHH ...");

                gatt?.services?.forEach {
                    val uuid = it.uuid.toString()
                    Log.d(TAG, "onServicesDiscovered: uuid= $uuid")
                }

                // GATT service
                val service: BluetoothGattService? = gatt?.getService(UUID_GATT_SERVICE)

                // check
                service?.characteristics?.forEach {
                    Log.d(
                        TAG,
                        "onServicesDiscovered: characteristic = ${it.uuid} permission: ${it.permissions}"
                    )
                }

                // 212
                // GATT characteristic Tx
                val characteristicTx: BluetoothGattCharacteristic? =
                    service?.getCharacteristic(UUID_GATT_CHARACTERISTIC_TX)

                // 213
                // GATT characteristic Rx
                val characteristicRx: BluetoothGattCharacteristic? =
                    service?.getCharacteristic(UUID_GATT_CHARACTERISTIC_RX)

                // ... enable indication ...
                val descriptor: BluetoothGattDescriptor? =
                    characteristicRx?.getDescriptor(UUID_GATT_CHARACTERISTIC_RX_DESCRIPTOR)
                descriptor?.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)

                if (ActivityCompat.checkSelfPermission(
                        context, Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }

                gatt?.writeDescriptor(descriptor)
                gatt?.setCharacteristicNotification(characteristicRx, true)
                // ....

                // get characteristics
                connectCallback.onGetCharacteristics(characteristicTx!!, characteristicRx!!)
            }
        }
    }

    // 接受 Vehicle 响应
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray
    ) {
        super.onCharacteristicChanged(gatt, characteristic, value)
        Log.d(TAG, "RX 全量打印: ${JUtils.bytesToHex(value)}")

        if (characteristic.uuid == UUID_GATT_CHARACTERISTIC_RX) {
            connectCallback.onResponseFromVehicle(value)
        }
    }

}