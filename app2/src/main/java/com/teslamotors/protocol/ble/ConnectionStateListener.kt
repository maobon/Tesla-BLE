package com.teslamotors.protocol.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import com.teslamotors.protocol.vcsec.FromVCSECMessage

interface ConnectionStateListener {

    /**
     * connect to vehicle successful
     *
     * @param gatt
     */
    fun onConnected(gatt: BluetoothGatt)

    /**
     * discovery BLE services successful
     *
     * @param tx phone -> vehicle
     * @param rx vehicle -> phone
     */
    fun onGetCharacteristics(tx: BluetoothGattCharacteristic, rx: BluetoothGattCharacteristic)

    /**
     * received messages from vehicle
     *
     * @param vcsecMsg Tesla response
     */
    fun onVehicleResponse(vcsecMsg: FromVCSECMessage?)

    /**
     * Error
     *
     * @param type ErrorType
     * @param statusCode
     * @param desc description
     */
    fun onError(type: GattErrorType, statusCode: Int?=-1, desc: String? = null)
}