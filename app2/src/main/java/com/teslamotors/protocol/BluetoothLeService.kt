package com.teslamotors.protocol

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import com.teslamotors.protocol.ble.BluetoothLeUtil
import com.teslamotors.protocol.ble.GattCallback
import com.teslamotors.protocol.keystore.KeyStoreUtils
import com.teslamotors.protocol.msg.action.AuthRequest
import com.teslamotors.protocol.msg.action.ClosuresRequest
import com.teslamotors.protocol.msg.key.AddKeyToWhiteListRequest
import com.teslamotors.protocol.msg.key.AddKeyVehicleResponse
import com.teslamotors.protocol.msg.key.EphemeralKeyRequest
import com.teslamotors.protocol.msg.key.EphemeralKeyVehicleResponse
import com.teslamotors.protocol.util.JUtils
import com.teslamotors.protocol.util.Operations.AUTHENTICATING
import com.teslamotors.protocol.util.Operations.CLOSURES_REQUESTING
import com.teslamotors.protocol.util.Operations.EPHEMERAL_KEY_REQUESTING
import com.teslamotors.protocol.util.Operations.KEY_TO_WHITELIST_ADDING
import com.teslamotors.protocol.util.TESLA_BLUETOOTH_NAME
import com.teslamotors.protocol.util.TESLA_RX_CHARACTERISTIC_DESCRIPTOR_UUID
import com.teslamotors.protocol.util.countAutoIncrement

class BluetoothLeService : Service() {

    private var mScanning = false

    // real control instance
    lateinit var bluetoothLeUtil: BluetoothLeUtil
    lateinit var txCharacteristic: BluetoothGattCharacteristic
    lateinit var rxCharacteristic: BluetoothGattCharacteristic

    // bluetooth gatt callback result: onConnectionStateChange ...
    private lateinit var mGattCallback: GattCallback

    private val mBluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val mBleScanner by lazy {
        mBluetoothAdapter.bluetoothLeScanner
    }

    private val keyStoreUtils: KeyStoreUtils = KeyStoreUtils.getInstance()
    private lateinit var x963FormatPublicKey: ByteArray

    // -----------------------------------------

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate() {
        super.onCreate()

        initKeystore()

        mGattCallback = GattCallback(object : GattCallback.ConnectionStateListener {
            override fun onConnected(gatt: BluetoothGatt) {
                bluetoothLeUtil = BluetoothLeUtil(gatt)

                // auto next step discovery services
                bluetoothLeUtil.discoveryServices()
            }

            override fun onGetCharacteristics(
                tx: BluetoothGattCharacteristic,
                rx: BluetoothGattCharacteristic
            ) {
                txCharacteristic = tx
                rxCharacteristic = rx
                // enable notification
                bluetoothLeUtil.enableNotifications(rx, TESLA_RX_CHARACTERISTIC_DESCRIPTOR_UUID)
            }

            override fun onVehicleResponse(message: vcsec.FromVCSECMessage?) {
                when (bluetoothLeUtil.opera) {
                    KEY_TO_WHITELIST_ADDING -> {
                        val ret = with(message!!) {
                            AddKeyVehicleResponse().perform(this, keyStoreUtils.keyId)
                        }
                        Log.d(
                            TAG,
                            "onVehicleResponse: KEY_TO_WHITELIST_ADDING -> keystore process result: $ret"
                        )
                    }

                    EPHEMERAL_KEY_REQUESTING -> {
                        val sharedKey = with(message!!) {
                            EphemeralKeyVehicleResponse().perform(this@BluetoothLeService, this)
                        }
                        Log.d(
                            TAG,
                            "onVehicleResponse: EPHEMERAL_KEY_REQUESTING -> get shared key: $sharedKey"
                        )
                    }

                    AUTHENTICATING -> {

                    }

                    CLOSURES_REQUESTING -> {

                    }

                    else -> {}
                }
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun initKeystore() {
        x963FormatPublicKey = keyStoreUtils.getKeyPair(this)
        Log.d(TAG, "initKeystore: x963FormatPublicKey=${JUtils.bytesToHex(x963FormatPublicKey)}")
    }

    @Suppress("all")
    private val mScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (mScanning) stopBleScan()

            result.device.apply {
                Log.d(TAG, "connecting!!! ---> : ${this.name} + ${this.address}")

                // connect to vehicle .....
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
                .setDeviceName(TESLA_BLUETOOTH_NAME)
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

    // step2 ....................
    fun addKey() {
        if (x963FormatPublicKey.isEmpty()) {
            Log.e(TAG, "addKey: x963FormatPublicKey is null")
            return
        }

        val requestMsg = AddKeyToWhiteListRequest().perform(x963FormatPublicKey)
        bluetoothLeUtil.writeCharacteristic(
            txCharacteristic,
            requestMsg,
            KEY_TO_WHITELIST_ADDING
        )
    }

    // step3 ....................
    fun requestEphemeralKey() {
        val requestMsg = EphemeralKeyRequest().perform(keyStoreUtils.keyId)
        bluetoothLeUtil.writeCharacteristic(txCharacteristic, requestMsg, EPHEMERAL_KEY_REQUESTING)
    }

    // step 4 ...............................
    fun authenticate() {
        val sharedKey: ByteArray = keyStoreUtils.sharedKey
        val requestMsg = AuthRequest().perform(this, sharedKey, countAutoIncrement())
        bluetoothLeUtil.writeCharacteristic(txCharacteristic, requestMsg, AUTHENTICATING)
    }

    // real control
    fun openPassengerDoor() {
        val sharedKey: ByteArray = keyStoreUtils.sharedKey
        val requestMsg = ClosuresRequest().perform(this, sharedKey, countAutoIncrement())
        bluetoothLeUtil.writeCharacteristic(txCharacteristic, requestMsg, CLOSURES_REQUESTING)
    }

    companion object {
        private const val TAG = "BluetoothLeService"
    }

}