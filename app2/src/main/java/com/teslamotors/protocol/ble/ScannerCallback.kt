package com.teslamotors.protocol.ble

import android.Manifest
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat

class ScannerCallback(
    private val context: Context,
    private val bluetoothLeScanner: BluetoothLeScanner,
    private val bluetoothGattCallback: BluetoothGattCallback,
    private val connectCallback: ConnectCallback
) : ScanCallback() {

    companion object {
        private const val TAG = "ScannerCallback"

        private const val BLUETOOTH_NAME_TESLA = "Saadbad01e89502c1C"
    }

    // 扫描结果
    override fun onScanResult(callbackType: Int, result: ScanResult?) {
        super.onScanResult(callbackType, result)

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {

            Log.w(TAG, "onScanResult: BLUETOOTH_CONNECT permission not granted")
            return
        }

        val device = result?.device
        Log.d(TAG, "onScanResult: deviceName=${device?.name}")

        if (device?.name.equals(BLUETOOTH_NAME_TESLA)) {
            bluetoothLeScanner.stopScan(this)

            // 连接
            Log.d(TAG, "onScanResult: 正在连接 Tesla")

            // 连接 tesla
            val bluetoothGatt = device?.connectGatt(context, true, bluetoothGattCallback)

            connectCallback.onConnected(bluetoothGatt!!)
        }
    }

    // 扫描失败
    override fun onScanFailed(errorCode: Int) {
        super.onScanFailed(errorCode)
    }
}