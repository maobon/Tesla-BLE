package com.teslamotors.protocol.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import com.teslamotors.protocol.vcsec
import com.teslamotors.protocol.vcsec.FromVCSECMessage

interface ConnectionStateListener {

    fun onConnected(gatt: BluetoothGatt)

    fun onGetCharacteristics(tx: BluetoothGattCharacteristic, rx: BluetoothGattCharacteristic)

    fun onVehicleResponse(vcsecMsg: FromVCSECMessage?)
}