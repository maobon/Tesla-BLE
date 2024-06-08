package com.teslamotors.protocol.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic

open class ConnectCallback {

    open fun onConnected(bluetoothGatt: BluetoothGatt) {}

    open fun onGetCharacteristics(characteristicTx: BluetoothGattCharacteristic,
                                  characteristicRx: BluetoothGattCharacteristic) {}

    // From Vehicle
    open fun onResponseFromVehicle(data: ByteArray) {}
}