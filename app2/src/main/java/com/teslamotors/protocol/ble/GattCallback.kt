package com.teslamotors.protocol.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.util.Log
import com.teslamotors.protocol.util.MessageUtil
import com.teslamotors.protocol.util.TESLA_RX_CHARACTERISTIC_UUID
import com.teslamotors.protocol.util.TESLA_SERVICE_UUID
import com.teslamotors.protocol.util.TESLA_TX_CHARACTERISTIC_UUID
import com.teslamotors.protocol.util.printGattTable
import com.teslamotors.protocol.util.toHexString
import com.teslamotors.protocol.vcsec

@SuppressLint("MissingPermission")
class GattCallback(
    private val mStatusListener: ConnectionStateListener
) : BluetoothGattCallback() {

    /**
     * connection state change
     *
     * @param gatt
     * @param status
     * @param newState
     */
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        val deviceAddress = gatt.device.address

        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.w(TAG, "Successfully connected to $deviceAddress")

                // stash BluetoothGatt instance ...
                mStatusListener.onConnected(gatt)

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                gatt.close()
                Log.w(TAG, "Successfully disconnected from $deviceAddress")
            }

        } else {
            gatt.close()
            Log.w(TAG, "Error $status encountered for $deviceAddress! Disconnecting...")
            mStatusListener.onError(
                GattErrorType.ERR_CONNECTION_STATUS_CHANGE, status, "connection failed"
            )
        }
    }

    /**
     * service's discovered
     *
     * @param gatt
     * @param status
     */
    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        with(gatt) {
            Log.w(TAG, "Discovered ${services.size} services for ${device.address}")

            // See implementation just above this section
            printGattTable()
            // Consider connection setup as complete here

            // tesla
            val teslaService = getService(TESLA_SERVICE_UUID)
            val characteristicTx = teslaService.run {
                getCharacteristic(TESLA_TX_CHARACTERISTIC_UUID)
            }
            val characteristicRx = teslaService.run {
                getCharacteristic(TESLA_RX_CHARACTERISTIC_UUID)
            }

            if (teslaService == null || characteristicTx == null || characteristicRx == null) {
                Log.e(TAG, "onServicesDiscovered: Tesla BLE Service or Characteristic is null")
                mStatusListener.onError(
                    GattErrorType.ERR_SERVICES_DISCOVERY,
                    desc = "Tesla BLE Service or Characteristic is null"
                )
                return
            }

            mStatusListener.onGetCharacteristics(characteristicTx, characteristicRx)
        }
    }

    /**
     * service's Characteristic read
     *
     * @param gatt
     * @param characteristic
     * @param status
     */
    @Deprecated("Deprecated for Android 13+")
    @Suppress("DEPRECATION")
    override fun onCharacteristicRead(
        gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
    ) {

        with(characteristic) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.i(TAG, "Read characteristic --> $uuid:\n${value.toHexString()}")
                }

                BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                    Log.e(TAG, "Read not permitted for $uuid!")
                }

                else -> {
                    Log.e(TAG, "Characteristic read failed for $uuid, error: $status")
                    mStatusListener.onError(
                        GattErrorType.ERR_CHARACTERISTIC_READ,
                        status,
                        "characteristic read error (deprecation)"
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
                Log.i(TAG, "Read characteristic $uuid:\n $hexString")
            }

            BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                Log.e(TAG, "Read not permitted for $uuid!")
                mStatusListener.onError(
                    GattErrorType.ERR_CHARACTERISTIC_READ, status, "Read not permitted for $uuid!"
                )
            }

            else -> {
                Log.e(TAG, "Characteristic read failed for $uuid, error: $status")
                mStatusListener.onError(
                    GattErrorType.ERR_CHARACTERISTIC_READ, status, "characteristic read error"
                )
            }
        }
    }

    /**
     * service's Characteristic changed
     *
     * @param gatt
     * @param characteristic
     */
    @Deprecated("Deprecated for Android 13+")
    @Suppress("DEPRECATION")
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic
    ) {
        with(characteristic) {
            if (value == null || value.isEmpty()) {
                mStatusListener.onError(
                    GattErrorType.ERR_CHARACTERISTIC_CHANGE, desc = "received value is null"
                )
                return
            }

            val hex = value.toHexString()
            Log.i(TAG, "Characteristic $uuid changed | value: $hex")
            processReceiveMsg(characteristic, value)
        }
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray
    ) {
        if (value.isEmpty()) {
            mStatusListener.onError(
                GattErrorType.ERR_CHARACTERISTIC_CHANGE, desc = "received value is null"
            )
            return
        }

        val hex = value.toHexString()
        with(characteristic) {
            Log.i(TAG, "Characteristic $uuid changed | value: $hex")
            processReceiveMsg(characteristic, value)
        }
    }

    /**
     * core function
     * analysis received messages from vehicle
     *
     * @param characteristic BluetoothGattCharacteristic
     * @param value ByteArray
     */
    private fun processReceiveMsg(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (characteristic.uuid == TESLA_RX_CHARACTERISTIC_UUID) {
            val fromVCSECMessage: vcsec.FromVCSECMessage? = MessageUtil.autoChaCha(value)
            if (fromVCSECMessage != null) {
                Log.d(TAG, "received content from vehicle:\n$fromVCSECMessage")
                mStatusListener.onVehicleResponse(fromVCSECMessage)
            }
        } else {
            mStatusListener.onError(
                GattErrorType.ERR_CHARACTERISTIC_CHANGE, desc = "not Tesla rx characteristic"
            )
        }
    }

    companion object {
        private const val TAG = "GattCallback"
    }
}