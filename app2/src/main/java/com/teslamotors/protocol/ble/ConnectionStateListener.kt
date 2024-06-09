package com.teslamotors.protocol.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import com.teslamotors.protocol.vcsec

interface ConnectionStateListener {

    fun onConnected(gatt: BluetoothGatt)

    fun onGetCharacteristics(tx: BluetoothGattCharacteristic, rx: BluetoothGattCharacteristic)

    fun onVehicleResponse(message: vcsec.FromVCSECMessage?)
}