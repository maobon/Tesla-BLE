package com.teslamotors.protocol

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.teslamotors.protocol.ble2.BluetoothLeUtil
import com.teslamotors.protocol.ble2.GattCallback
import com.teslamotors.protocol.util.XIAOMI_ENV_SENSOR_CHARACTERISTIC
import com.teslamotors.protocol.util.XIAOMI_ENV_SENSOR_SERVICE
import com.teslamotors.protocol.util.XIAOMI_MIJIA_SENSOR_NAME
import com.teslamotors.protocol.util.isReadable
import java.util.*

class BluetoothLeService : Service() {

    private var mScanning = false
    private lateinit var mGattCallback: GattCallback

    private val mBluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val mBleScanner by lazy {
        mBluetoothAdapter.bluetoothLeScanner
    }

    override fun onCreate() {
        super.onCreate()

        mGattCallback = GattCallback(object : GattCallback.ConnectionStateListener {
            override fun onConnected() {
                discoveryServices()
            }

            override fun onServiceDiscovered() {
                enableNotify()
            }
        })
    }

    @Suppress("all")
    private val mScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (mScanning) stopBleScan()

            // todo core connect to the target ...
            result.device.apply {
                Log.d(TAG, "connecting!!! ---> : ${this.name} + ${this.address}")

                connectGatt(
                    this@BluetoothLeService,
                    false,
                    mGattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {

        }
    }

    inner class InnerBinder : Binder() {
        fun getService() = this@BluetoothLeService
    }

    override fun onBind(intent: Intent): IBinder {
        return InnerBinder()
    }

    fun isBluetoothEnable() = mBluetoothAdapter.isEnabled

    @Suppress("all")
    fun startBleScan() {
        if (mScanning) stopBleScan() else {
            mScanning = true

            val scanFilter = ScanFilter.Builder()
                .setDeviceName(XIAOMI_MIJIA_SENSOR_NAME)
                .build()

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            val filters = mutableListOf<ScanFilter>().apply {
                add(scanFilter)
            }

            mBleScanner.startScan(filters, scanSettings, mScanCallback)
        }
    }

    @Suppress("all")
    fun stopBleScan() {
        mBleScanner.stopScan(mScanCallback)
        mScanning = false
    }

    @SuppressLint("MissingPermission")
    fun discoveryServices() {
        Handler(Looper.getMainLooper()).postDelayed({
            mGattCallback.mBluetoothGatt?.discoverServices()
        }, 100)
    }

    fun enableNotify() {
        val serviceUuid = UUID.fromString(XIAOMI_ENV_SENSOR_SERVICE)
        val charUuid = UUID.fromString(XIAOMI_ENV_SENSOR_CHARACTERISTIC)

        mGattCallback.mBluetoothGatt?.getService(serviceUuid)?.let {
            Log.d(TAG, "enableNotify: getService successfully: ${it.uuid}")
            val characteristic = it.getCharacteristic(charUuid)
            Log.d(TAG, "enableNotify: characteristic => $characteristic")

            BluetoothLeUtil(mGattCallback.mBluetoothGatt).enableNotifications(characteristic)
        }

        // val characteristic = mBluetoothGatt?.getService(serviceUuid)?.getCharacteristic(
        //     charUuid
        // )


        // characteristic?.descriptors?.forEach {
        //     Log.w(TAG, "*** descriptors uuid: ${it.uuid}")
        // }

    }

    companion object {
        private const val TAG = "BluetoothLeService"
    }

    // ---------------------------------------------------------------------------------------------
    @SuppressLint("MissingPermission")
    fun readBatteryLevel(gatt: BluetoothGatt) {

        val batteryServiceUuid = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val batteryLevelCharUuid = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

        val batteryLevelChar =
            gatt.getService(batteryServiceUuid)?.getCharacteristic(batteryLevelCharUuid)

        if (batteryLevelChar?.isReadable() == true) {
            gatt.readCharacteristic(batteryLevelChar)
        }
    }


}